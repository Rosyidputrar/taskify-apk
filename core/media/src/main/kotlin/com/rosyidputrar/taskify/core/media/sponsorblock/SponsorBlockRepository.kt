/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

// Diadaptasi dari ArchiveTune `sponsorblock/SponsorBlockRepository.kt`
// (v15.1.0, c7f0e24) — bentuk permintaan (hash prefix SHA-256, param
// `categories` JSON + `actionTypes` + `service`), cache LRU, dan aturan
// status dipertahankan. Yang DIUBAH untuk Taskify
// (lihat `licenses/ARCHIVETUNE.md`):
//   - Tanpa DataStore/Hilt/`Context`: `apiUrl` parameter konstruktor,
//     kategori parameter [segments]; tanpa `observeSettings`/`setEnabled`/
//     `setCategories`/`setApiUrl` (pengaturan tinggal di `MusicSettings`,
//     Tahap 6-7).
//   - Validasi kategori di sisi klien (aturan Worker
//     `resolveSponsorBlockCategories`): kategori tak dikenal ->
//     [IllegalArgumentException], bukan diam-diam dibuang — hulu menjawab
//     404 yang sama untuk kategori tak dikenal dan untuk video yang memang
//     tidak punya segmen.
//   - Hasil dipetakan ke [SponsorSegment] domain (`:core:domain`), bukan
//     `SponsorBlockRawSegment`.

package com.rosyidputrar.taskify.core.media.sponsorblock

import com.rosyidputrar.taskify.core.domain.media.SponsorBlock
import com.rosyidputrar.taskify.core.domain.media.SponsorSegment
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/** Hulu SponsorBlock menolak / mati — pemanggil harus menampilkannya, bukan `[]`. */
class SponsorBlockApiException(
    val statusCode: Int,
) : Exception("SponsorBlock request failed with HTTP $statusCode")

/**
 * Segmen SponsorBlock langsung dari `sponsor.ajay.app` (tanpa Worker).
 *
 * `GET {apiUrl}/api/skipSegments/<sha256-prefix>?categories=[...]` —
 * hanya 4 karakter hex pertama hash SHA-256 videoId yang dikirim
 * (privasi: videoId tidak dikirim utuh, seperti ArchiveTune).
 *
 * - 200 -> segmen dinormalisasi + diurutkan menurut `start`.
 * - 404 -> `[]`: video ini tidak punya segmen (jawaban normal).
 * - 429/5xx/jaringan gagal/JSON rusak -> lempar (perilaku Worker:
 *   502, bukan `[]` — mengembalikan `[]` saat sebenarnya gagal akan
 *   membuat pemutar memutar sponsor sambil mengira tidak ada yang
 *   perlu dilewati).
 *
 * Hasil di-cache per videoId di memori (LRU, maks 64 entri) — termasuk
 * hasil 404, supaya video tanpa segmen tidak ditanya berulang kali.
 */
class SponsorBlockRepository(
    private val http: HttpClient? = null,
    apiUrl: String = DEFAULT_SPONSORBLOCK_API_URL,
) {
    private val apiUrl: String = normalizeSponsorBlockApiUrl(apiUrl)
        ?: throw IllegalArgumentException("URL SponsorBlock tidak sah: $apiUrl")

    private val client: HttpClient = http ?: HttpClient(OkHttp) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
            connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
            socketTimeoutMillis = REQUEST_TIMEOUT_MILLIS
        }
        engine {
            config {
                retryOnConnectionFailure(true)
            }
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val responseCache =
        object : LinkedHashMap<RequestKey, List<SponsorSegment>>(MAX_CACHE_ENTRIES + 1, 0.75f, true) {
            override fun removeEldestEntry(
                eldest: MutableMap.MutableEntry<RequestKey, List<SponsorSegment>>,
            ): Boolean = size > MAX_CACHE_ENTRIES
        }

    /**
     * Segmen untuk satu video. [categories] bawaan =
     * [SponsorBlock.DEFAULT_CATEGORIES] (sama dengan Worker dan UI
     * pengaturan sekarang). Kategori tak dikenal ->
     * [IllegalArgumentException].
     */
    suspend fun segments(
        videoId: String,
        categories: List<String> = SponsorBlock.DEFAULT_CATEGORIES,
    ): List<SponsorSegment> {
        val categoryValues = categories.map { it.lowercase() }.distinct().sorted()
        val unknown = categoryValues.firstOrNull { SponsorBlockCategory.fromApiValue(it) == null }
        if (unknown != null) {
            throw IllegalArgumentException(
                "Kategori \"$unknown\" tidak dikenal. Kategori yang didukung: " +
                    SponsorBlockCategory.entries.joinToString(", ") { it.apiValue } + ".",
            )
        }
        val key = RequestKey(apiUrl, videoId, categoryValues)
        getCached(key)?.let { return it }

        val hashPrefix = videoId.sha256Prefix()
        val response = client.get("$apiUrl/api/skipSegments/$hashPrefix") {
            parameter("categories", json.encodeToString(categoryValues))
            parameter("actionTypes", SKIP_ACTION_TYPES_JSON)
            parameter("service", YOUTUBE_SERVICE)
            header(HttpHeaders.UserAgent, USER_AGENT)
        }
        val segments = when {
            response.status == HttpStatusCode.NotFound -> emptyList()
            response.status.value !in 200..299 -> throw SponsorBlockApiException(response.status.value)
            else -> {
                val decoded = runCatching {
                    json.decodeFromString<List<SponsorBlockVideoResponse>>(response.bodyAsText())
                }.getOrElse { throw SponsorBlockApiException(response.status.value) }
                decoded.firstOrNull { it.videoId == videoId }
                    ?.segments.orEmpty().toDomainSegments()
            }
        }
        putCached(key, segments)
        return segments
    }

    @Synchronized
    private fun getCached(key: RequestKey): List<SponsorSegment>? = responseCache[key]

    @Synchronized
    private fun putCached(key: RequestKey, segments: List<SponsorSegment>) {
        responseCache[key] = segments
    }

    private fun String.sha256Prefix(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
        return buildString(HASH_PREFIX_LENGTH) {
            for (index in 0 until HASH_PREFIX_LENGTH / 2) {
                val value = digest[index].toInt() and 0xFF
                append(HEX_DIGITS[value ushr 4])
                append(HEX_DIGITS[value and 0x0F])
            }
        }
    }

    private data class RequestKey(
        val apiUrl: String,
        val videoId: String,
        val categories: List<String>,
    )

    companion object {
        const val REQUEST_TIMEOUT_MILLIS = 10_000L
        const val CONNECT_TIMEOUT_MILLIS = 8_000L
        const val MAX_CACHE_ENTRIES = 64

        /** Panjang prefix hash (hex) yang dikirim — 4 karakter = 2 byte. */
        const val HASH_PREFIX_LENGTH = 4

        /**
         * ArchiveTune hanya meminta `["skip"]`; Taskify meminta juga
         * `"mute"` karena `SponsorBlock.muted()` (`:core:domain`) butuh
         * segmen bisu — jalur Worker lama juga mengembalikannya.
         */
        const val SKIP_ACTION_TYPES_JSON = "[\"skip\",\"mute\"]"
        const val YOUTUBE_SERVICE = "YouTube"
        const val USER_AGENT = "Taskify/1.0"
        const val HEX_DIGITS = "0123456789abcdef"
    }
}
