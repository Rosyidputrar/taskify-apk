package com.rosyidputrar.taskify.core.media.mytube

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.rosyidputrar.taskify.core.domain.media.AccountInfo
import com.rosyidputrar.taskify.core.innertube.PlaybackAuthState

/**
 * Penyimpanan konteks auth musik (Tahap 5 rewrite direct + Tahap 3/4 login akun).
 *
 * C M-2: rahasia sesi (cookie Google, `dataSyncId`, PoToken) disimpan di
 * [EncryptedSharedPreferences] (`"youtube_auth_secure"`, AES-256-GCM,
 * kunci di AndroidKeyStore) — BUKAN lagi plaintext di `"youtube_auth"`.
 * `visitorData` (anonim, bukan rahasia) + identitas akun (nama/email/
 * handle/foto) tetap di prefs biasa `"youtube_auth"`.
 *
 * Migrasi satu kali: bila prefs lama masih berisi rahasia, nilainya
 * dipindah ke prefs terenkripsi lalu kunci lamanya dihapus — sekali jalan,
 * idempotent (instans kedua tidak menemukan sisa).
 *
 * Bila prefs terenkripsi tidak bisa dibuat (KeyStore rusak / perangkat
 * tanpa dukungan), fallback ke prefs biasa dengan log warning — login
 * tetap jalan, bukan crash. [securePrefs]/[plainPrefs] hanya seam injeksi
 * untuk tes (Robolectric + KeyStore tidak selalu bisa membuat prefs
 * terenkripsi — lihat `EncryptedPrefsProbeTest`).
 *
 * Semua nilai dinormalisasi lewat [PlaybackAuthState.normalized] saat
 * disimpan dan dibaca: spasi di ujung dibuang, string `"null"` dianggap
 * kosong. Nilai kosong tidak disimpan (kuncinya dihapus). Cookie dan
 * identitas akun TIDAK PERNAH di-log.
 */
class MyTubeAuthStore(
    context: Context,
    securePrefs: SharedPreferences? = null,
    plainPrefs: SharedPreferences? = null,
) {
    private val appContext = context.applicationContext
    private val plain: SharedPreferences =
        plainPrefs ?: appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Prefs terenkripsi, dibuat malas (KeyStore I/O tidak di konstruktor).
     * [securePrefs] yang diinjeksikan dipakai apa adanya (tes).
     */
    private val secure: SharedPreferences by lazy {
        val prefs = securePrefs ?: createSecurePrefs(appContext)
        migrateLegacySecrets(prefs, plain)
        prefs
    }

    /** State auth tersimpan, sudah dinormalisasi; [PlaybackAuthState.EMPTY] bila belum ada. */
    fun loadAuthState(): PlaybackAuthState =
        PlaybackAuthState(
            cookie = secure.getString(KEY_COOKIE, null),
            visitorData = plain.getString(KEY_VISITOR_DATA, null),
            dataSyncId = secure.getString(KEY_DATA_SYNC_ID, null),
            poToken = secure.getString(KEY_PO_TOKEN, null),
        ).normalized()

    /** Simpan state auth (dinormalisasi dulu); nilai kosong menghapus kuncinya. */
    fun saveAuthState(state: PlaybackAuthState) {
        val s = state.normalized()
        secure.edit()
            .putOrRemove(KEY_COOKIE, s.cookie)
            .putOrRemove(KEY_DATA_SYNC_ID, s.dataSyncId)
            .putOrRemove(KEY_PO_TOKEN, s.poToken)
            .apply()
        plain.edit()
            .putOrRemove(KEY_VISITOR_DATA, s.visitorData)
            .apply()
    }

    /** `visitorData` tersimpan, atau null bila belum pernah di-bootstrap. */
    var visitorData: String?
        get() = loadAuthState().visitorData
        set(value) = saveVisitorData(value)

    /** Simpan `visitorData` saja (dinormalisasi); null/blank menghapusnya. */
    fun saveVisitorData(value: String?) {
        val normalized = PlaybackAuthState(visitorData = value).normalized().visitorData
        plain.edit().putOrRemove(KEY_VISITOR_DATA, normalized).apply()
    }

    /**
     * Simpan identitas akun yang login (Tahap 3/4); nilai kosong menghapus
     * kuncinya. Nama kosong = tidak ada akun yang disimpan ([loadAccount]
     * mengembalikan null).
     */
    fun saveAccount(account: AccountInfo) {
        plain.edit()
            .putOrRemove(KEY_ACCOUNT_NAME, account.name.trim())
            .putOrRemove(KEY_ACCOUNT_EMAIL, account.email?.trim())
            .putOrRemove(KEY_ACCOUNT_CHANNEL_HANDLE, account.channelHandle?.trim())
            .putOrRemove(KEY_ACCOUNT_THUMBNAIL_URL, account.thumbnailUrl?.trim())
            .apply()
    }

    /** Identitas akun yang login, atau null bila belum login / nama kosong. */
    fun loadAccount(): AccountInfo? {
        val name = plain.getString(KEY_ACCOUNT_NAME, null)?.trim().orEmpty()
        if (name.isEmpty()) return null
        return AccountInfo(
            name = name,
            email = plain.getString(KEY_ACCOUNT_EMAIL, null)?.trim()?.takeIf { it.isNotEmpty() },
            channelHandle = plain.getString(KEY_ACCOUNT_CHANNEL_HANDLE, null)?.trim()?.takeIf { it.isNotEmpty() },
            thumbnailUrl = plain.getString(KEY_ACCOUNT_THUMBNAIL_URL, null)?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    /** Hapus identitas akun saja (state auth cookie/visitorData tetap). */
    fun clearAccount() {
        plain.edit()
            .remove(KEY_ACCOUNT_NAME)
            .remove(KEY_ACCOUNT_EMAIL)
            .remove(KEY_ACCOUNT_CHANNEL_HANDLE)
            .remove(KEY_ACCOUNT_THUMBNAIL_URL)
            .apply()
    }

    /** Lupakan seluruh konteks auth musik (mis. saat reset/logout musik) — kedua prefs dibersihkan. */
    fun clear() {
        secure.edit().clear().commit()
        plain.edit().clear().commit()
    }

    companion object {
        /** Nama SharedPreferences auth musik — sengaja berbeda dari `"music"`. */
        const val PREFS_NAME = "youtube_auth"

        /** Nama prefs terenkripsi (C M-2) — hanya rahasia sesi di sini. */
        const val SECURE_PREFS_NAME = "youtube_auth_secure"

        private const val TAG = "MyTubeAuthStore"
        private const val KEY_VISITOR_DATA = "visitorData"
        private const val KEY_COOKIE = "cookie"
        private const val KEY_DATA_SYNC_ID = "dataSyncId"
        private const val KEY_PO_TOKEN = "poToken"
        private const val KEY_ACCOUNT_NAME = "accountName"
        private const val KEY_ACCOUNT_EMAIL = "accountEmail"
        private const val KEY_ACCOUNT_CHANNEL_HANDLE = "accountChannelHandle"
        private const val KEY_ACCOUNT_THUMBNAIL_URL = "accountThumbnailUrl"

        /** Kunci rahasia yang dimigrasi dari prefs lama ke prefs terenkripsi. */
        private val SECRET_KEYS = listOf(KEY_COOKIE, KEY_DATA_SYNC_ID, KEY_PO_TOKEN)

        private fun createSecurePrefs(context: Context): SharedPreferences {
            return try {
                EncryptedSharedPreferences.create(
                    context,
                    SECURE_PREFS_NAME,
                    MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            } catch (e: Exception) {
                Log.w(TAG, "EncryptedSharedPreferences tidak bisa dibuat, memakai prefs biasa", e)
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            }
        }

        /**
         * Pindah rahasia dari prefs lama ke [secure] bila masih ada, lalu
         * hapus kunci lamanya. Dilewati bila [secure] adalah prefs biasa
         * (fallback — tidak ada yang bisa dimigrasi).
         */
        private fun migrateLegacySecrets(secure: SharedPreferences, plain: SharedPreferences) {
            if (secure === plain) return
            val legacy = SECRET_KEYS.mapNotNull { key -> plain.getString(key, null)?.let { key to it } }
            if (legacy.isEmpty()) return
            val secureEditor = secure.edit()
            legacy.forEach { (key, value) -> secureEditor.putString(key, value) }
            secureEditor.apply()
            val plainEditor = plain.edit()
            legacy.forEach { (key, _) -> plainEditor.remove(key) }
            plainEditor.apply()
            Log.i(TAG, "Migrasi ${legacy.size} rahasia auth ke prefs terenkripsi")
        }
    }
}

private fun SharedPreferences.Editor.putOrRemove(key: String, value: String?): SharedPreferences.Editor =
    if (value.isNullOrBlank()) remove(key) else putString(key, value)
