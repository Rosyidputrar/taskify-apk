package com.rosyidputrar.taskify.core.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.Json
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * D-05 — VERIFIKASI: sesi Supabase bawaan supabase-kt 3.9.0 memang plaintext. `SettingsSessionManager`
 * menyimpan JSON sesi polos (`Json.encodeToString(UserSession)`) lewat multiplatform-settings ke
 * SharedPreferences `<package>_preferences`, kunci `sb-<url>-session` (terverifikasi dari bytecode
 * artefak `auth-kt-android-3.9.0.aar` + `multiplatform-settings-no-arg-android-1.3.0.aar`:
 * `SettingsUtilKt.createDefaultSessionManager` + `createDefaultSettingsKey`, `NoArgKt.Settings()`).
 * Siapa pun yang membaca berkas shared_prefs mendapat access token + refresh token.
 *
 * Penggantinya: sesi disimpan terenkripsi AES-256-GCM (kunci di AndroidKeyStore, alias
 * `taskify_supabase_session`) di berkas prefs sendiri. Sesi lama plaintext dimigrasi sekali jalan
 * saat pertama dibaca, lalu kunci lamanya dihapus. `SettingsCodeVerifierCache` (PKCE, juga
 * plaintext di prefs yang sama) di luar lingkup D-05 — dicatat sebagai residu, bukan diperbaiki
 * diam-diam.
 */
class EncryptedSessionManager(
    private val store: SessionStore,
    private val crypto: SessionCrypto,
    private val legacy: LegacySessionReader?,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val onSaveError: (Throwable) -> Unit = {},
) : SessionManager {

    /**
     * Gagal menyimpan TIDAK boleh menggagalkan login: supabase-kt memanggil ini di dalam
     * `signInWith`, jadi exception di sini (Keystore rusak setelah pulihkan cadangan/ganti
     * kunci layar, serialisasi) dulu membuat tombol "Masuk" berputar tanpa akhir. Kini sesi tetap
     * dipakai di memori; hanya belum tersimpan (masuk lagi setelah aplikasi ditutup).
     */
    override suspend fun saveSession(session: UserSession) {
        val result = runCatching {
            val plain = json.encodeToString(UserSession.serializer(), session)
            store.put(crypto.encrypt(plain))
        }
        result.exceptionOrNull()?.let { e ->
            if (e is kotlin.coroutines.cancellation.CancellationException) throw e
            onSaveError(e)
            // Jangan tinggalkan sesi lama terenkripsi yang tidak cocok lagi dengan sesi aktif.
            runCatching { store.remove() }
        }
        legacy?.clear()
    }

    override suspend fun loadSession(): UserSession {
        val cipher = store.get() ?: migrateLegacy() ?: throw NoSessionFoundException()
        val plain = runCatching { crypto.decrypt(cipher) }.getOrNull() ?: throw NoSessionFoundException()
        return runCatching { json.decodeFromString(UserSession.serializer(), plain) }.getOrElse { throw NoSessionFoundException() }
    }

    override suspend fun deleteSession() {
        store.remove()
        legacy?.clear()
    }

    /** Migrasi sekali jalan: baca sesi lama plaintext, simpan terenkripsi, hapus kunci lama. */
    private suspend fun migrateLegacy(): String? {
        val plain = legacy?.read() ?: return null
        if (plain.isBlank()) return null
        val cipher = runCatching { crypto.encrypt(plain) }.getOrNull() ?: return null
        store.put(cipher)
        legacy.clear()
        return cipher
    }
}

/** Penyimpanan string sesi terenkripsi — antarmuka supaya bisa dites di JVM tanpa Android. */
interface SessionStore {
    fun get(): String?
    fun put(value: String)
    fun remove()
}

/** [SessionStore] di atas SharedPreferences (berkas sendiri, bukan `<package>_preferences`). */
class SharedPreferencesSessionStore(
    private val prefs: SharedPreferences,
    private val key: String = SESSION_KEY,
) : SessionStore {
    override fun get(): String? = prefs.getString(key, null)
    override fun put(value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun remove() {
        prefs.edit().remove(key).apply()
    }
}

/** Enkripsi string sesi — antarmuka supaya bisa dites di JVM tanpa Keystore. */
interface SessionCrypto {
    fun encrypt(plain: String): String
    fun decrypt(cipher: String): String
}

/**
 * AES-256-GCM murni (javax.crypto). Format: `v1:` + Base64(iv 12 byte || ciphertext+tag).
 * Menerima [SecretKey] apa pun — di produksi dari AndroidKeyStore, di tes dari SecretKeySpec.
 */
class AesGcmSessionCrypto(
    private val key: SecretKey,
    private val cipherFactory: () -> Cipher = { Cipher.getInstance(TRANSFORMATION) },
) : SessionCrypto {
    override fun encrypt(plain: String): String {
        // Jangan kirim IV sendiri (GCMParameterSpec) saat ENCRYPT_MODE: kunci AndroidKeyStore
        // memakai randomizedEncryptionRequired = true secara bawaan dan selalu menolak
        // caller-provided IV dengan InvalidAlgorithmParameterException. Biarkan Cipher
        // menghasilkan IV acak 12 byte sendiri lalu baca cipher.iv.
        val cipher = cipherFactory().apply {
            init(Cipher.ENCRYPT_MODE, key)
        }
        val iv = cipher.iv
        require(iv != null && iv.size == IV_BYTES) { "IV AES-GCM tidak valid." }
        val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.getEncoder().encodeToString(iv + ciphertext)
    }

    override fun decrypt(cipher: String): String {
        require(cipher.startsWith(PREFIX)) { "Sesi terenkripsi rusak." }
        val combined = Base64.getDecoder().decode(cipher.removePrefix(PREFIX))
        require(combined.size > IV_BYTES) { "Sesi terenkripsi rusak." }
        val c = cipherFactory().apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, combined, 0, IV_BYTES))
        }
        return c.doFinal(combined, IV_BYTES, combined.size - IV_BYTES).toString(Charsets.UTF_8)
    }

    companion object {
        const val PREFIX = "v1:"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }
}

/** Pembaca sesi lama plaintext — antarmuka supaya migrasi bisa dites di JVM tanpa Android. */
interface LegacySessionReader {
    fun read(): String?
    fun clear()
}

/** Sesi lama bawaan supabase-kt: berkas `<package>_preferences`, kunci [legacySessionKey]. */
class DefaultLegacySessionReader(context: Context, supabaseUrl: String) : LegacySessionReader {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
    private val key: String = legacySessionKey(supabaseUrl)

    override fun read(): String? = prefs.getString(key, null)
    override fun clear() {
        prefs.edit().remove(key).apply()
    }
}

/**
 * Kunci persis yang dipakai `SettingsSessionManager` bawaan supabase-kt 3.9.0
 * (`SettingsUtilKt.createDefaultSettingsKey(url) + "-session"` — terverifikasi dari bytecode).
 */
fun legacySessionKey(supabaseUrl: String): String =
    "sb-" + supabaseUrl.removeSuffix("/").replace('/', '-').replace('.', '-') + "-session"

/** SessionManager terenkripsi siap pakai untuk [createTaskifySupabase]. */
fun createEncryptedSessionManager(context: Context, supabaseUrl: String): SessionManager =
    EncryptedSessionManager(
        store = SharedPreferencesSessionStore(
            context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE),
        ),
        crypto = RecoveringSessionCrypto(keyProvider = ::sessionKey, resetKey = ::deleteSessionKey),
        legacy = DefaultLegacySessionReader(context, supabaseUrl),
        onSaveError = { android.util.Log.w("Taskify", "Sesi tidak bisa disimpan terenkripsi; tetap masuk tanpa menyimpan", it) },
    )

/**
 * Kunci Keystore bisa menjadi tidak valid (mis. setelah ganti kunci layar, pulihkan cadangan ke HP
 * lain, atau bug vendor). Enkripsi yang gagal → kunci lama dibuang, kunci baru dibuat, dicoba sekali
 * lagi. Sesi lama yang dienkripsi kunci lama memang tidak terbaca lagi (= masuk ulang), bukan macet.
 */
class RecoveringSessionCrypto(
    private val keyProvider: () -> SecretKey,
    private val resetKey: () -> Unit,
) : SessionCrypto {
    @Volatile private var current: AesGcmSessionCrypto? = null

    private fun crypto(): AesGcmSessionCrypto = current ?: AesGcmSessionCrypto(keyProvider()).also { current = it }

    override fun encrypt(plain: String): String = try {
        crypto().encrypt(plain)
    } catch (first: Exception) {
        current = null
        runCatching { resetKey() }
        try {
            crypto().encrypt(plain)
        } catch (second: Exception) {
            current = null
            second.addSuppressed(first)
            throw second
        }
    }

    override fun decrypt(cipher: String): String = crypto().decrypt(cipher)
}

private fun deleteSessionKey() {
    KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }.deleteEntry(SESSION_KEY_ALIAS)
}

private fun sessionKey(): SecretKey {
    val store = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    (store.getKey(SESSION_KEY_ALIAS, null) as? SecretKey)?.let { return it }
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).apply {
        init(
            KeyGenParameterSpec.Builder(SESSION_KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
    }.generateKey()
}

private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val SESSION_KEY_ALIAS = "taskify_supabase_session"
private const val SESSION_PREFS = "taskify_supabase_session"
private const val SESSION_KEY = "session"
