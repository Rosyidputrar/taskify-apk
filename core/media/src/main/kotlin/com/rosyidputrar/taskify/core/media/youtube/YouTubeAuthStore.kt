package com.rosyidputrar.taskify.core.media.youtube

import android.content.Context
import android.content.SharedPreferences
import com.rosyidputrar.taskify.core.domain.media.AccountInfo
import com.rosyidputrar.taskify.core.innertube.PlaybackAuthState

/**
 * Penyimpanan konteks auth musik (Tahap 5 rewrite direct + Tahap 3/4 login akun).
 *
 * `visitorData` (dan cookie/dataSyncId/poToken) disimpan di
 * SharedPreferences terpisah `"youtube_auth"` — BUKAN `"music"`
 * ([com.rosyidputrar.taskify.core.media.MusicPrefs]): konteks auth bukan
 * pengaturan antrean, dan harus bertahan walau antrean dihapus saat logout.
 *
 * Sejak login akun (Tahap 3/4): identitas akun (nama/email/handle/foto) juga
 * disimpan di prefs yang sama lewat [saveAccount]/[loadAccount]/[clearAccount].
 * Cookie dan identitas akun TIDAK PERNAH di-log.
 *
 * Semua nilai dinormalisasi lewat [PlaybackAuthState.normalized] saat
 * disimpan dan dibaca: spasi di ujung dibuang, string `"null"` dianggap
 * kosong. Nilai kosong tidak disimpan (kuncinya dihapus).
 */
class YouTubeAuthStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** State auth tersimpan, sudah dinormalisasi; [PlaybackAuthState.EMPTY] bila belum ada. */
    fun loadAuthState(): PlaybackAuthState =
        PlaybackAuthState(
            cookie = prefs.getString(KEY_COOKIE, null),
            visitorData = prefs.getString(KEY_VISITOR_DATA, null),
            dataSyncId = prefs.getString(KEY_DATA_SYNC_ID, null),
            poToken = prefs.getString(KEY_PO_TOKEN, null),
        ).normalized()

    /** Simpan state auth (dinormalisasi dulu); nilai kosong menghapus kuncinya. */
    fun saveAuthState(state: PlaybackAuthState) {
        val s = state.normalized()
        prefs.edit()
            .putOrRemove(KEY_COOKIE, s.cookie)
            .putOrRemove(KEY_VISITOR_DATA, s.visitorData)
            .putOrRemove(KEY_DATA_SYNC_ID, s.dataSyncId)
            .putOrRemove(KEY_PO_TOKEN, s.poToken)
            .apply()
    }

    /** `visitorData` tersimpan, atau null bila belum pernah di-bootstrap. */
    var visitorData: String?
        get() = loadAuthState().visitorData
        set(value) = saveVisitorData(value)

    /** Simpan `visitorData` saja (dinormalisasi); null/blank menghapusnya. */
    fun saveVisitorData(value: String?) {
        val normalized = PlaybackAuthState(visitorData = value).normalized().visitorData
        prefs.edit().putOrRemove(KEY_VISITOR_DATA, normalized).apply()
    }

    /**
     * Simpan identitas akun yang login (Tahap 3/4); nilai kosong menghapus
     * kuncinya. Nama kosong = tidak ada akun yang disimpan ([loadAccount]
     * mengembalikan null).
     */
    fun saveAccount(account: AccountInfo) {
        prefs.edit()
            .putOrRemove(KEY_ACCOUNT_NAME, account.name.trim())
            .putOrRemove(KEY_ACCOUNT_EMAIL, account.email?.trim())
            .putOrRemove(KEY_ACCOUNT_CHANNEL_HANDLE, account.channelHandle?.trim())
            .putOrRemove(KEY_ACCOUNT_THUMBNAIL_URL, account.thumbnailUrl?.trim())
            .apply()
    }

    /** Identitas akun yang login, atau null bila belum login / nama kosong. */
    fun loadAccount(): AccountInfo? {
        val name = prefs.getString(KEY_ACCOUNT_NAME, null)?.trim().orEmpty()
        if (name.isEmpty()) return null
        return AccountInfo(
            name = name,
            email = prefs.getString(KEY_ACCOUNT_EMAIL, null)?.trim()?.takeIf { it.isNotEmpty() },
            channelHandle = prefs.getString(KEY_ACCOUNT_CHANNEL_HANDLE, null)?.trim()?.takeIf { it.isNotEmpty() },
            thumbnailUrl = prefs.getString(KEY_ACCOUNT_THUMBNAIL_URL, null)?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    /** Hapus identitas akun saja (state auth cookie/visitorData tetap). */
    fun clearAccount() {
        prefs.edit()
            .remove(KEY_ACCOUNT_NAME)
            .remove(KEY_ACCOUNT_EMAIL)
            .remove(KEY_ACCOUNT_CHANNEL_HANDLE)
            .remove(KEY_ACCOUNT_THUMBNAIL_URL)
            .apply()
    }

    /** Lupakan seluruh konteks auth musik (mis. saat reset/logout musik). */
    fun clear() {
        prefs.edit().clear().commit()
    }

    companion object {
        /** Nama SharedPreferences auth musik — sengaja berbeda dari `"music"`. */
        const val PREFS_NAME = "youtube_auth"

        private const val KEY_VISITOR_DATA = "visitorData"
        private const val KEY_COOKIE = "cookie"
        private const val KEY_DATA_SYNC_ID = "dataSyncId"
        private const val KEY_PO_TOKEN = "poToken"
        private const val KEY_ACCOUNT_NAME = "accountName"
        private const val KEY_ACCOUNT_EMAIL = "accountEmail"
        private const val KEY_ACCOUNT_CHANNEL_HANDLE = "accountChannelHandle"
        private const val KEY_ACCOUNT_THUMBNAIL_URL = "accountThumbnailUrl"
    }
}

private fun SharedPreferences.Editor.putOrRemove(key: String, value: String?): SharedPreferences.Editor =
    if (value.isNullOrBlank()) remove(key) else putString(key, value)
