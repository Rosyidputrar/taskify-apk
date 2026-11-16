package com.rosyidputrar.taskify.core.media

import com.rosyidputrar.taskify.core.domain.media.MusicQueue
import com.rosyidputrar.taskify.core.domain.media.SearchFilter
import com.rosyidputrar.taskify.core.domain.media.Song
import com.rosyidputrar.taskify.core.domain.media.Thumb
import com.rosyidputrar.taskify.core.domain.media.Track
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** Galat pemutar musik; pesannya sudah siap tampil (Worker memang berbahasa Indonesia). */
class MusicException(message: String, val status: Int = 0) : Exception(message)

data class SearchPage(val songs: List<Song>, val cursor: String?)

/**
 * Klien REST Worker music-taskify (port `api-client.ts`). Token Supabase diambil SEGAR setiap
 * permintaan; penanda audio siap = ADANYA `url` (bukan progress 100); progres tidak pernah mundur.
 */
class MusicClient(private val baseUrl: String, private val token: suspend () -> String?) {
    private val http = HttpClient(OkHttp) {
        install(HttpTimeout) { requestTimeoutMillis = 30_000; connectTimeoutMillis = 15_000 }
        expectSuccess = false
    }
    private val json = Json { ignoreUnknownKeys = true }
    private val base = baseUrl.trimEnd('/')

    val configured: Boolean get() = base.isNotBlank()

    private suspend fun post(path: String, body: JsonObject): JsonObject {
        val t = runCatching { token() }.getOrNull()
        if (t.isNullOrBlank()) throw MusicException("Sesi berakhir. Masuk ulang.", 401)
        val res = try {
            http.post("$base$path") {
                header("Authorization", "Bearer $t")
                setBody(TextContent(body.toString(), ContentType.Application.Json))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw MusicException("Tidak bisa menghubungi server musik. Periksa koneksi.")
        }
        val text = res.bodyAsText()
        if (!res.status.isSuccess()) {
            val msg = runCatching { (json.parseToJsonElement(text).jsonObject["error"] as? JsonPrimitive)?.content }.getOrNull()
            throw MusicException(
                msg?.takeIf { it.isNotBlank() } ?: when {
                    res.status.value == 401 -> "Sesi berakhir. Masuk ulang."
                    res.status.value == 403 -> "Akun ini tidak diizinkan memakai pemutar musik."
                    res.status.value >= 500 -> "Server musik sedang bermasalah. Coba lagi sebentar."
                    else -> "Server musik balas HTTP ${res.status.value}."
                },
                res.status.value,
            )
        }
        return json.parseToJsonElement(text).jsonObject
    }

    suspend fun search(query: String, filter: SearchFilter): SearchPage =
        page(post("/v1/search", buildJsonObject { put("q", query); filter.value?.let { put("filter", it) } }))

    /** Halaman lanjutan dikirim HANYA dengan cursor (menyertakan `q` ditolak 400). */
    suspend fun searchMore(cursor: String): SearchPage = page(post("/v1/search", buildJsonObject { put("cursor", cursor) }))

    suspend fun track(videoId: String): Track {
        val o = post("/v1/track", buildJsonObject { put("videoId", videoId) })
        return Track(o.str("videoId") ?: videoId, o.str("lyricsId").orEmpty(), (o["upNext"] as? JsonArray).songs())
    }

    suspend fun lyrics(lyricsId: String): String = post("/v1/lyrics", buildJsonObject { put("lyricsId", lyricsId) }).str("text").orEmpty()

    /** URL audio siap putar (mp3: m4a dari konverter tidak bisa diputar — lihat catatan webapp). */
    suspend fun resolveStream(videoId: String, onProgress: (Int, String) -> Unit = { _, _ -> }): String {
        onProgress(0, MusicQueue.progressLabel(0))
        val init = post("/v1/stream/init", buildJsonObject { put("videoId", videoId); put("format", "mp3") })
        init.str("url")?.takeIf { it.isNotBlank() }?.let { onProgress(100, ""); return it }
        val job = init.str("job") ?: throw MusicException("Server musik tidak memberi tugas konversi.")
        val deadline = System.currentTimeMillis() + CONVERT_TIMEOUT_MS
        var highest = 0
        while (System.currentTimeMillis() < deadline) {
            delay(POLL_INTERVAL_MS)
            val step = post("/v1/stream/poll", buildJsonObject { put("job", job) })
            step.str("url")?.takeIf { it.isNotBlank() }?.let { onProgress(100, ""); return it }
            val p = (step["progress"] as? JsonPrimitive)?.intOrNull ?: 0
            if (p > highest) { highest = p; onProgress(p, MusicQueue.progressLabel(p)) }
        }
        throw MusicException("Menyiapkan audio terlalu lama. Coba lagu lain.")
    }

    private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun page(o: JsonObject) = SearchPage((o["songs"] as? JsonArray).songs(), o.str("cursor"))

    private fun JsonArray?.songs(): List<Song> = this.orEmpty().mapNotNull { e ->
        val s = e as? JsonObject ?: return@mapNotNull null
        val id = s.str("videoId") ?: return@mapNotNull null
        Song(
            id, s.str("title").orEmpty(), s.str("artist").orEmpty(), s.str("duration").orEmpty(),
            (s["thumbnails"] as? JsonArray).orEmpty().mapNotNull { t ->
                val o = t as? JsonObject ?: return@mapNotNull null
                Thumb(o.str("url") ?: return@mapNotNull null, (o["width"] as? JsonPrimitive)?.intOrNull ?: 0, (o["height"] as? JsonPrimitive)?.intOrNull ?: 0)
            },
            s.str("url").orEmpty(),
        )
    }

    companion object {
        const val POLL_INTERVAL_MS = 1_200L
        const val CONVERT_TIMEOUT_MS = 90_000L
    }
}
