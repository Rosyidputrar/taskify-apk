package com.rosyidputrar.taskify.core.sandi

import android.app.Application
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import com.rosyidputrar.taskify.core.domain.sandi.Dek
import com.rosyidputrar.taskify.core.domain.sandi.TrustMeta
import com.rosyidputrar.taskify.core.domain.sandi.TrustState
import com.rosyidputrar.taskify.core.domain.sandi.trustExpiresAt
import com.rosyidputrar.taskify.core.domain.sandi.trustState
import kotlinx.coroutines.CancellationException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * "Ingat perangkat ini" (padanan `device-trust.ts`): DEK dibungkus kunci AES-GCM di Android
 * Keystore (tidak bisa diekspor, sebisanya di perangkat keras aman) lalu disimpan di
 * SharedPreferences. Master password TIDAK pernah disimpan. Berlaku 30 hari; batal bila kunci
 * brankas diganti (stamp beda), kedaluwarsa, atau akun keluar.
 *
 * Kunci Keystore WAJIB dibuka dengan verifikasi pemilik HP (sidik jari/wajah atau PIN/pola layar),
 * jadi HP yang tertinggal dalam keadaan terbuka tidak otomatis membuka brankas. Kunci juga gugur
 * bila kunci layar dimatikan atau sidik jari baru didaftarkan.
 */
interface TrustStore {
    /** HP punya kunci layar sehingga kunci bisa dilindungi verifikasi. */
    fun available(): Boolean

    /**
     * Simpan DEK setelah pemilik HP terverifikasi. false = dibatalkan pengguna.
     * @throws DeviceAuthException bila verifikasi/Keystore gagal.
     */
    suspend fun remember(dek: Dek, stamp: String, auth: DeviceAuthenticator, nowMs: Long = System.currentTimeMillis()): Boolean

    /**
     * Buka DEK setelah pemilik HP terverifikasi. null = tidak ada/kedaluwarsa/dibatalkan.
     * @throws DeviceAuthException bila verifikasi gagal atau kunci sudah gugur (lalu dilupakan).
     */
    suspend fun load(currentStamp: String, auth: DeviceAuthenticator, nowMs: Long = System.currentTimeMillis()): Dek?

    fun meta(): TrustMeta?
    fun forget()
}

class KeystoreTrustStore(private val app: Application) : TrustStore {
    private val sdk = Build.VERSION.SDK_INT

    private val prefs = app.getSharedPreferences("taskify_sandi_trust", android.content.Context.MODE_PRIVATE)

    override fun available(): Boolean = ActivityDeviceAuthenticator.available(app)

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun existingKey(): SecretKey? = keyStore().getKey(ALIAS, null) as? SecretKey

    private fun newKey(): SecretKey {
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            spec.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
        } else {
            @Suppress("DEPRECATION")
            spec.setUserAuthenticationValidityDurationSeconds(DeviceTrustPolicy.VALIDITY_SECONDS)
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply { init(spec.build()) }.generateKey()
    }

    /**
     * Jalankan [use] dengan cipher yang sudah diizinkan pemilik HP. API 30+: cipher di-init dulu lalu
     * diikat ke dialog (CryptoObject). API < 30: verifikasi dulu, lalu init dalam jendela waktu kunci.
     * null = dibatalkan.
     */
    private suspend fun <T> authorized(auth: DeviceAuthenticator, prompt: AuthPrompt, init: (Cipher) -> Unit, use: (Cipher) -> T): T? {
        val cipher = Cipher.getInstance(TRANSFORM)
        if (DeviceTrustPolicy.usesCryptoObject(sdk)) init(cipher)
        when (val r = auth.authenticate(prompt, if (DeviceTrustPolicy.usesCryptoObject(sdk)) cipher else null)) {
            AuthResult.Ok -> Unit
            AuthResult.Cancelled -> return null
            is AuthResult.Failed -> throw DeviceAuthException(r.message)
        }
        if (!DeviceTrustPolicy.usesCryptoObject(sdk)) init(cipher)
        return use(cipher)
    }

    override suspend fun remember(dek: Dek, stamp: String, auth: DeviceAuthenticator, nowMs: Long): Boolean {
        if (!available()) throw DeviceAuthException("Pasang kunci layar (PIN/pola/sandi) di HP dulu untuk memakai fitur ini.")
        forget() // kunci baru setiap kali: tidak ada sisa kunci lama
        val raw = dek.export()
        try {
            val key = newKey()
            val sealed = authorized(auth, REMEMBER_PROMPT, init = { it.init(Cipher.ENCRYPT_MODE, key) }) { cipher ->
                cipher.updateAAD(stamp.toByteArray())
                cipher.iv + cipher.doFinal(raw)
            } ?: return false.also { forget() }
            prefs.edit()
                .putString(K_DEK, Base64.getEncoder().encodeToString(sealed))
                .putString(K_STAMP, stamp)
                .putLong(K_EXPIRES, trustExpiresAt(nowMs))
                .putInt(K_VERSION, VERSION)
                .apply()
            return true
        } catch (e: CancellationException) {
            forget()
            throw e
        } catch (e: DeviceAuthException) {
            forget()
            throw e
        } catch (e: Exception) {
            forget()
            throw DeviceAuthException(keystoreMessage(e))
        } finally {
            raw.fill(0)
        }
    }

    override fun meta(): TrustMeta? {
        // Versi lama (kunci tanpa verifikasi pengguna) tidak dipakai lagi.
        if (prefs.contains(K_DEK) && prefs.getInt(K_VERSION, 1) != VERSION) {
            forget()
            return null
        }
        val stamp = prefs.getString(K_STAMP, null) ?: return null
        val expires = prefs.getLong(K_EXPIRES, 0)
        return if (prefs.contains(K_DEK)) TrustMeta(stamp, expires) else null
    }

    override suspend fun load(currentStamp: String, auth: DeviceAuthenticator, nowMs: Long): Dek? {
        val meta = meta() ?: return null
        if (trustState(meta, currentStamp, nowMs) != TrustState.Ok) {
            forget()
            return null
        }
        val sealed = Base64.getDecoder().decode(prefs.getString(K_DEK, null) ?: return null)
        return try {
            val key = existingKey() ?: return null.also { forget() }
            authorized(auth, UNLOCK_PROMPT, init = { it.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, sealed, 0, 12)) }) { cipher ->
                cipher.updateAAD(meta.stamp.toByteArray())
                Dek(cipher.doFinal(sealed, 12, sealed.size - 12))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: DeviceAuthException) {
            throw e
        } catch (e: KeyPermanentlyInvalidatedException) {
            forget()
            throw DeviceAuthException("Kunci layar atau sidik jari HP berubah, jadi perangkat ini perlu diingat ulang. Buka dengan master password.")
        } catch (e: UserNotAuthenticatedException) {
            // Verifikasi tidak diterima kunci (mis. biometrik lemah di Android lama): kunci tetap disimpan,
            // pengguna bisa mencoba lagi dengan PIN/pola layar.
            throw DeviceAuthException(keystoreMessage(e))
        } catch (e: Exception) {
            forget()
            throw DeviceAuthException(keystoreMessage(e))
        }
    }

    private fun keystoreMessage(e: Exception): String = when (e) {
        is UserNotAuthenticatedException -> "Verifikasi tidak diterima kunci perangkat. Coba lagi dengan PIN/pola layar."
        is KeyPermanentlyInvalidatedException -> "Kunci layar atau sidik jari HP berubah. Buka dengan master password."
        else -> "Kunci perangkat tidak bisa dipakai di HP ini. Buka dengan master password."
    }

    override fun forget() {
        prefs.edit().clear().apply()
        runCatching {
            val ks = keyStore()
            ks.deleteEntry(ALIAS)
            ks.deleteEntry(LEGACY_ALIAS)
        }
    }

    private companion object {
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val ALIAS = "taskify_sandi_trust_v2"
        const val LEGACY_ALIAS = "taskify_sandi_trust_v1"
        const val VERSION = 2
        const val K_DEK = "dek"
        const val K_STAMP = "stamp"
        const val K_EXPIRES = "expires_at"
        const val K_VERSION = "version"
        val REMEMBER_PROMPT = AuthPrompt("Ingat perangkat ini", "Verifikasi dengan sidik jari atau kunci layar HP")
        val UNLOCK_PROMPT = AuthPrompt("Buka brankas Sandi", "Verifikasi dengan sidik jari atau kunci layar HP")
    }
}
