package com.rosyidputrar.taskify.core.sandi

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.rosyidputrar.taskify.core.domain.sandi.Dek
import com.rosyidputrar.taskify.core.domain.sandi.TrustMeta
import com.rosyidputrar.taskify.core.domain.sandi.TrustState
import com.rosyidputrar.taskify.core.domain.sandi.trustExpiresAt
import com.rosyidputrar.taskify.core.domain.sandi.trustState
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
 */
interface TrustStore {
    fun remember(dek: Dek, stamp: String, nowMs: Long = System.currentTimeMillis()): Boolean
    fun load(currentStamp: String, nowMs: Long = System.currentTimeMillis()): Dek?
    fun meta(): TrustMeta?
    fun forget()
}

class KeystoreTrustStore(context: Context) : TrustStore {
    private val prefs = context.getSharedPreferences("taskify_sandi_trust", Context.MODE_PRIVATE)

    private fun key(create: Boolean): SecretKey? {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        if (!create) return null
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    override fun remember(dek: Dek, stamp: String, nowMs: Long): Boolean = try {
        val raw = dek.export()
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key(create = true))
            cipher.updateAAD(stamp.toByteArray())
            val sealed = cipher.iv + cipher.doFinal(raw)
            prefs.edit().putString(K_DEK, Base64.getEncoder().encodeToString(sealed)).putString(K_STAMP, stamp).putLong(K_EXPIRES, trustExpiresAt(nowMs)).apply()
            true
        } finally {
            raw.fill(0)
        }
    } catch (_: Exception) {
        false
    }

    override fun meta(): TrustMeta? {
        val stamp = prefs.getString(K_STAMP, null) ?: return null
        val expires = prefs.getLong(K_EXPIRES, 0)
        return if (prefs.contains(K_DEK)) TrustMeta(stamp, expires) else null
    }

    override fun load(currentStamp: String, nowMs: Long): Dek? {
        val meta = meta() ?: return null
        if (trustState(meta, currentStamp, nowMs) != TrustState.Ok) {
            forget()
            return null
        }
        return try {
            val sealed = Base64.getDecoder().decode(prefs.getString(K_DEK, null))
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(create = false) ?: return null.also { forget() }, GCMParameterSpec(128, sealed, 0, 12))
            cipher.updateAAD(meta.stamp.toByteArray())
            Dek(cipher.doFinal(sealed, 12, sealed.size - 12))
        } catch (_: Exception) {
            forget()
            null
        }
    }

    override fun forget() {
        prefs.edit().clear().apply()
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS) }
    }

    private companion object {
        const val ALIAS = "taskify_sandi_trust_v1"
        const val K_DEK = "dek"
        const val K_STAMP = "stamp"
        const val K_EXPIRES = "expires_at"
    }
}
