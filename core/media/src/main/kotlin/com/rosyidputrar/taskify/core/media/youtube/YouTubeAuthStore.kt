package com.rosyidputrar.taskify.core.media.youtube

import android.content.Context
import android.content.SharedPreferences
import com.rosyidputrar.taskify.core.innertube.PlaybackAuthState

/**
 * Penyimpanan konteks auth musik anonim (Tahap 5 rewrite direct).
 *
 * `visitorData` (dan kelak cookie/dataSyncId/poToken) disimpan di
 * SharedPreferences terpisah `"youtube_auth"` — BUKAN `"music"`
 * ([com.rosyidputrar.taskify.core.media.MusicPrefs]): konteks auth bukan
 * pengaturan antrean, dan harus bertahan walau antrean dihapus saat logout.
 *
 * Tahap ini HANYA anonim: tidak ada login WebView, tidak ada cookie login,
 * tidak ada PoToken/BotGuard, tidak ada `dataSyncId` (lihat PLAN G.10).
 * Field cookie/dataSyncId/poToken sudah disiapkan di sini (dan di
 * [PlaybackAuthState]) supaya tahap lanjut tinggal mengisi, tapi nilainya
 * kosong sampai saat itu — jangan pernah menulis kredensial/akun/sesi
 * apa pun lewat store ini di tahap ini.
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
    }
}

private fun SharedPreferences.Editor.putOrRemove(key: String, value: String?): SharedPreferences.Editor =
    if (value.isNullOrBlank()) remove(key) else putString(key, value)
