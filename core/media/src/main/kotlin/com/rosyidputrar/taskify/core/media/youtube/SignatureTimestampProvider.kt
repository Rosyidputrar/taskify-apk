package com.rosyidputrar.taskify.core.media.youtube

import com.rosyidputrar.taskify.core.innertube.SharedHttpClient
import com.rosyidputrar.taskify.core.innertube.models.YouTubeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * `signatureTimestamp` dari `base.js` YouTube (C1-8, ArchiveTune
 * `morideobfuscator/javascript/src/index.js:283-322` — `getCurrentPlayerId`
 * + `downloadPlayerSource` + `extractSignatureTimestamp` — ditulis ulang
 * di Kotlin tanpa QuickJS, karena yang dibutuhkan di sini hanya SATU
 * angka dari `base.js`, bukan mesin JS penuh).
 *
 * Alur: GET `https://www.youtube.com/iframe_api` → ekstrak id player
 * (`player/<id>/`) → GET `/s/player/<id>/player_es6.vflset/en_US/base.js`
 * → regex `signatureTimestamp: <angka>` — hasilnya HARUS tepat 1 nilai
 * unik, kalau tidak `null`. Nilai di-cache 6 jam di memori.
 *
 * Best-effort total: gagal apa pun (jaringan mati, pola berubah, id
 * tidak ketemu) → `null`, dan `player` tetap dipanggil tanpa
 * `signatureTimestamp` (perilaku lama). Tidak pernah melempar, kecuali
 * pembatalan coroutine. Hanya dipanggil untuk klien dengan
 * `useSignatureTimestamp = true`.
 */
class SignatureTimestampProvider(
    private val okHttpClient: OkHttpClient = SharedHttpClient.client,
    private val now: () -> Long = System::currentTimeMillis,
) {
    @Volatile
    private var cached: CachedTimestamp? = null

    suspend fun signatureTimestamp(client: YouTubeClient, videoId: String): Int? {
        if (!client.useSignatureTimestamp) return null
        cached?.takeIf { now() - it.fetchedAtMs < CACHE_TTL_MS }?.let { return it.timestamp }
        val fetched = runCatching { fetchTimestamp() }.getOrNull() ?: return null
        cached = CachedTimestamp(fetched, now())
        return fetched
    }

    private suspend fun fetchTimestamp(): Int? = withContext(Dispatchers.IO) {
        val iframeApi = get(IFRAME_API_URL) ?: return@withContext null
        val playerId = extractPlayerId(iframeApi) ?: return@withContext null
        val baseJs = get(BASE_JS_URL.format(playerId)) ?: return@withContext null
        extractSignatureTimestamp(baseJs)
    }

    private fun get(url: String): String? {
        val request = Request.Builder().url(url).header("User-Agent", DESKTOP_USER_AGENT).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()?.takeIf { it.isNotBlank() }
        }
    }

    private data class CachedTimestamp(val timestamp: Int, val fetchedAtMs: Long)

    companion object {
        const val IFRAME_API_URL = "https://www.youtube.com/iframe_api"
        const val BASE_JS_URL = "https://www.youtube.com/s/player/%s/player_es6.vflset/en_US/base.js"

        /** Umur cache timestamp: 6 jam (sama seperti umur URL stream). */
        const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L

        const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"

        private val PLAYER_ID_REGEX = Regex("""player/([A-Za-z0-9_-]+)/""")
        private val SIGNATURE_TIMESTAMP_REGEX = Regex("""signatureTimestamp\s*:\s*(\d{5,})""")

        /** Id player dari badan `iframe_api`, atau `null` bila pola tidak ketemu. */
        fun extractPlayerId(iframeApiBody: String): String? =
            PLAYER_ID_REGEX.find(iframeApiBody)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }

        /**
         * `signatureTimestamp` dari `base.js`: HARUS tepat 1 nilai unik
         * (ArchiveTune `extractSignatureTimestamp`); 0 atau >1 nilai
         * berbeda → `null`.
         */
        fun extractSignatureTimestamp(baseJs: String): Int? {
            val values = SIGNATURE_TIMESTAMP_REGEX.findAll(baseJs)
                .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
                .toSet()
            return values.singleOrNull()
        }
    }
}
