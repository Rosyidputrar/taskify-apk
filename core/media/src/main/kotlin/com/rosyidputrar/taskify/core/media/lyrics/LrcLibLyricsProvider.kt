package com.rosyidputrar.taskify.core.media.lyrics

import com.rosyidputrar.taskify.core.domain.media.LyricLine
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.abs

/**
 * Lirik sinkron dari LRCLib (`https://lrclib.net/api`) — ditulis dari
 * perilaku Worker music-taskify (`worker/synced-lyrics.ts`), BUKAN salinan
 * `LrcLib.kt` ArchiveTune (aturan toleransi dan urutan `/api/get` lalu
 * `/api/search` mengikuti Worker yang terverifikasi live 2026-11-16):
 *
 * 1. `GET /api/get` (judul + artis + album + durasi) — kecocokan terbaik
 *    menurut LRCLib sendiri; bila 404, durasi meleset >10 dtk, atau hasil
 *    tanpa lirik, lanjut ke `/api/search`.
 * 2. `GET /api/search` (judul + artis) — kandidat yang punya LRC,
 *    diurutkan selisih durasi; yang terdekat pun harus dalam 15 dtk.
 *
 * Tanpa kunci, tanpa cookie. LRCLib meminta User-Agent yang
 * mengidentifikasi aplikasi (tanpa itu sebagian permintaan ditolak 403).
 *
 * [fetchLyrics] TIDAK PERNAH melempar untuk kegagalan hulu (down, timeout,
 * 429, 5xx, JSON rusak -> `null`); pemanggil jatuh ke sumber lirik
 * berikutnya. Hanya pembatalan coroutine yang diteruskan.
 */
class LrcLibLyricsProvider(
    private val http: HttpClient? = null,
) : LyricsProvider {

    override val name: String = "LrcLib"

    override fun isEnabled(): Boolean = true

    /**
     * Hasil LRCLib yang bisa dipakai — `null` = tidak ada yang berguna
     * (bukan error). `synced` `null` = LRCLib tidak punya LRC (tapi mungkin
     * punya teks); `synced` `[]` = LRCLib memastikan instrumental.
     */
    data class LrcLibResult(
        val synced: List<LyricLine>?,
        val plain: String,
        val instrumental: Boolean,
    )

    suspend fun fetchLyrics(
        title: String,
        artist: String,
        album: String? = null,
        duration: Int = -1,
    ): LrcLibResult? {
        if (title.isBlank() || artist.isBlank()) return null
        val target = duration.takeIf { it > 0 }
        val getParams = buildMap<String, String> {
            put("artist_name", artist)
            put("track_name", title)
            if (!album.isNullOrBlank()) put("album_name", album)
            if (target != null) put("duration", target.toString())
        }
        when (val got = fetch("/get", getParams)) {
            is LrclibFetch.Ok -> {
                val track = got.data as? JsonObject
                if (track != null) {
                    val trackDuration = (track["duration"] as? JsonPrimitive)?.doubleOrNull
                    val mismatch = target != null && trackDuration != null &&
                        abs(trackDuration - target) > GET_DURATION_TOLERANCE_S
                    if (!mismatch) {
                        trackToResult(track)?.let { return it }
                    }
                    // `/api/get` menjawab tapi tanpa lirik / lagu lain yang
                    // kebetulan bernama sama -> coba `/api/search`.
                    return searchLyrics(title, artist, target)
                }
            }
            is LrclibFetch.NotFound -> return searchLyrics(title, artist, target)
            is LrclibFetch.Unavailable -> return null
        }
        return searchLyrics(title, artist, target)
    }

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> = runCatching {
        val result = fetchLyrics(title, artist, album, duration)
            ?: throw IllegalStateException("Lyrics unavailable")
        if (result.instrumental && result.synced.isNullOrEmpty() && result.plain.isBlank()) {
            throw IllegalStateException("Instrumental")
        }
        if (!result.synced.isNullOrEmpty()) return@runCatching LrcParser.formatLrc(result.synced)
        if (result.plain.isNotBlank()) return@runCatching result.plain
        throw IllegalStateException("Lyrics unavailable")
    }

    private suspend fun searchLyrics(title: String, artist: String, target: Int?): LrcLibResult? {
        val fetched = fetch(
            "/search",
            mapOf("artist_name" to artist, "track_name" to title),
        )
        if (fetched !is LrclibFetch.Ok) return null
        val data = fetched.data as? JsonArray ?: return null
        val withLrc = data.mapNotNull { it as? JsonObject }
            .filter { it.strOrNull("syncedLyrics").isNullOrBlank().not() }
            .sortedBy { candidateScore(it, target) }
        val best = withLrc.firstOrNull() ?: return null
        if (target != null && candidateScore(best, target) > DURATION_TOLERANCE_S) return null
        return trackToResult(best)
    }

    private fun candidateScore(track: JsonObject, target: Int?): Double {
        val d = (track["duration"] as? JsonPrimitive)?.doubleOrNull
        if (target == null || d == null) return 1_000_000.0
        return abs(d - target)
    }

    private fun trackToResult(track: JsonObject): LrcLibResult? {
        val plain = track.strOrNull("plainLyrics").orEmpty()
        val syncedRaw = track.strOrNull("syncedLyrics").orEmpty()
        val instrumental = (track["instrumental"] as? JsonPrimitive)?.booleanOrNull == true
        if (instrumental && syncedRaw.isBlank() && plain.isBlank()) {
            return LrcLibResult(emptyList(), "", true)
        }
        if (syncedRaw.isNotBlank()) {
            val lines = LrcParser.parseLrc(syncedRaw)
            if (lines.isNotEmpty()) return LrcLibResult(lines, plain, instrumental)
            if (plain.isNotBlank()) return LrcLibResult(null, plain, instrumental)
            return null
        }
        if (plain.isNotBlank()) return LrcLibResult(null, plain, instrumental)
        return null
    }

    /** Nilai string satu field JSON, atau null bila bukan string. */
    private fun JsonObject.strOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private sealed interface LrclibFetch {
        data class Ok(val data: JsonElement) : LrclibFetch
        data object NotFound : LrclibFetch
        data object Unavailable : LrclibFetch
    }

    private suspend fun fetch(path: String, params: Map<String, String>): LrclibFetch {
        val client = http ?: defaultClient()
        return try {
            val response = client.get("$BASE_URL$path") {
                params.forEach { (k, v) -> parameter(k, v) }
                header("Accept", "application/json")
                header("User-Agent", USER_AGENT)
            }
            when {
                response.status.value == 404 -> LrclibFetch.NotFound
                !response.status.isSuccess() -> LrclibFetch.Unavailable
                else -> {
                    val data = runCatching { json.parseToJsonElement(response.bodyAsText()) }.getOrNull()
                        ?: return LrclibFetch.Unavailable
                    LrclibFetch.Ok(data)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            LrclibFetch.Unavailable
        }
    }

    private fun defaultClient(): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = TIMEOUT_MS
            connectTimeoutMillis = TIMEOUT_MS
            socketTimeoutMillis = TIMEOUT_MS
        }
    }

    companion object {
        const val BASE_URL = "https://lrclib.net/api"

        /** Batas tunggu satu panggilan LRCLib — sama dengan Worker. */
        const val TIMEOUT_MS = 8_000L

        /** Beda durasi maksimum (detik) supaya kandidat dianggap lagu yang sama. */
        const val DURATION_TOLERANCE_S = 15.0

        /** Beda durasi maksimum untuk hasil `/api/get` sebelum dianggap lagu lain. */
        const val GET_DURATION_TOLERANCE_S = 10.0

        const val USER_AGENT = "Taskify/1.0 (https://github.com/Rosyidputrar/taskify-apk)"

        private val json = Json { ignoreUnknownKeys = true }
    }
}
