package com.rosyidputrar.taskify.core.media

import android.util.Log
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
import io.ktor.http.Headers
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Galat pemutar musik; pesannya sudah siap tampil (Worker memang berbahasa Indonesia).
 * [code]/[retryable]/[retryAfterSeconds] disalin dari amplop error Worker
 * `{error:{message, code, retryable, retryAfterSeconds?}}` (item 4) supaya ViewModel bisa
 * menampilkan pesan spesifik, mis. pesan rate-limit "Coba lagi dalam 12 detik".
 */
class MusicException(
    message: String,
    val status: Int = 0,
    val code: String? = null,
    val retryable: Boolean = false,
    val retryAfterSeconds: Long? = null,
) : Exception(message)

/** Hasil resolve audio: URL siap putar + asal audionya (item 10-klien). */
data class StreamInfo(
    val url: String?,
    val source: String?,
    val mimeType: String?,
    val itag: Int?,
    val bitrate: Int? = null,
) {
    /** Jalur direct yang memilih opus (itag 251) — perlu dilacak, bisa gagal di perangkat tertentu. */
    val isOpus: Boolean get() = mimeType?.contains("opus", ignoreCase = true) == true

    companion object {
        fun fromInit(init: JsonObject): StreamInfo {
            val str = { k: String -> (init[k] as? JsonPrimitive)?.takeIf { it.isString }?.content }
            return StreamInfo(
                url = str("url")?.takeIf { it.isNotBlank() },
                source = str("source"),
                mimeType = str("mimeType"),
                itag = (init["itag"] as? JsonPrimitive)?.intOrNull,
                bitrate = (init["bitrate"] as? JsonPrimitive)?.intOrNull,
            )
        }
    }
}

data class SearchPage(val songs: List<Song>, val cursor: String?)

/**
 * Klien REST Worker music-taskify (port `api-client.ts`). Token Supabase diambil SEGAR setiap
 * permintaan; penanda audio siap = ADANYA `url` (bukan progress 100); progres tidak pernah mundur.
 *
 * [http] hanya diisi pemanggil tes (MockEngine); pemakaian normal memakai OkHttp bawaan.
 */
open class MusicClient(private val baseUrl: String, private val token: suspend () -> String?, http: HttpClient? = null) {
    private val http = http ?: HttpClient(OkHttp) {
        install(HttpTimeout) { requestTimeoutMillis = 30_000; connectTimeoutMillis = 15_000 }
        expectSuccess = false
    }
    private val json = Json { ignoreUnknownKeys = true }
    private val base = baseUrl.trimEnd('/')

    val configured: Boolean get() = base.isNotBlank()

    /** Info stream terakhir yang berhasil di-resolve (init langsung atau poll selesai). */
    var lastStreamInfo: StreamInfo? = null
        private set

    /**
     * Satu POST tanpa retry. Galat HTTP dipetakan dari amplop error Worker (item 4, pola yang
     * sama dengan `TikTokClient.error`: `error` dibaca sebagai [JsonObject], diambil `code` +
     * `message` + `retryable` + `retryAfterSeconds`); header `Retry-After` dipakai bila bodi
     * tidak membawa `retryAfterSeconds`.
     */
    private suspend fun postOnce(path: String, body: JsonObject): JsonObject {
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
        if (!res.status.isSuccess()) throw errorFrom(res.status.value, text, res.headers)
        return json.parseToJsonElement(text).jsonObject
    }

    /** POST dengan retry 429/5xx yang `retryable`, menunggu sesuai `Retry-After` (item 9). */
    private suspend fun post(path: String, body: JsonObject): JsonObject {
        var attempt = 0
        while (true) {
            try {
                return postOnce(path, body)
            } catch (e: CancellationException) {
                throw e
            } catch (e: MusicException) {
                attempt++
                val waitMs = e.retryAfterSeconds?.let { it * 1_000 } ?: RETRY_BACKOFF_MS * attempt
                val canRetry = e.retryable && (e.status == 429 || e.status >= 500) &&
                    attempt <= MAX_RETRY_ATTEMPTS && waitMs in 1..MAX_AUTO_RETRY_WAIT_MS
                if (!canRetry) throw e
                Log.i(TAG, "retry $path setelah ${waitMs}ms (percobaan $attempt, ${e.code})")
                delay(waitMs)
            }
        }
    }

    internal fun errorFrom(status: Int, text: String, headers: Headers): MusicException {
        val errObj = runCatching { json.parseToJsonElement(text).jsonObject["error"] as? JsonObject }.getOrNull()
        // Bentuk lama: `error` berupa string polos — tetap dipakai sebagai pesan.
        val legacy = runCatching { json.parseToJsonElement(text).jsonObject["error"] as? JsonPrimitive } .getOrNull()?.content
        val message = errObj?.str("message")?.takeIf { it.isNotBlank() } ?: legacy?.takeIf { it.isNotBlank() }
        val code = errObj?.str("code")
        val retryable = (errObj?.get("retryable") as? JsonPrimitive)?.booleanOrNull ?: false
        val retryAfter = (errObj?.get("retryAfterSeconds") as? JsonPrimitive)?.longOrNull
            ?: headers["Retry-After"]?.toLongOrNull()
        return MusicException(
            message ?: when {
                status == 401 -> "Sesi berakhir. Masuk ulang."
                status == 403 -> "Akun ini tidak diizinkan memakai pemutar musik."
                status >= 500 -> "Server musik sedang bermasalah. Coba lagi sebentar."
                else -> "Server musik balas HTTP $status."
            },
            status, code, retryable, retryAfter,
        )
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
    open suspend fun resolveStream(videoId: String, onProgress: (Int, String) -> Unit = { _, _ -> }): String =
        resolveStreamInfo(videoId, onProgress = onProgress).url
            ?: throw MusicException("Server musik tidak memberi URL audio.")

    /**
     * Resolve audio + info asalnya (item 10-klien). Init langsung (direct/R2) mengembalikan
     * `url` + `source`/`mimeType`/`itag`; jalur konverter mengembalikan `job` lalu di-poll
     * sampai `url` muncul (sumbernya konverter, format yang diminta mp3).
     *
     * [forceConverter] meminta jalur konverter walau direct tersedia — dikirim sebagai
     * `"direct": false` di bodi init (param yang dikenali Worker, `worker/index.ts` rute
     * `/v1/stream/init`). Bila tidak diminta, field `direct` tidak dikirim sama sekali
     * sehingga perilaku default tetap jalur direct. Default mati.
     */
    open suspend fun resolveStreamInfo(
        videoId: String,
        forceConverter: Boolean = false,
        onProgress: (Int, String) -> Unit = { _, _ -> },
    ): StreamInfo {
        onProgress(0, MusicQueue.progressLabel(0))
        val init = post("/v1/stream/init", buildJsonObject {
            put("videoId", videoId); put("format", "mp3")
            if (forceConverter) put("direct", false)
        })
        val info = StreamInfo.fromInit(init)
        info.url?.let { onProgress(100, ""); return record(videoId, info) }
        val job = init.str("job") ?: throw MusicException("Server musik tidak memberi tugas konversi.")
        val deadline = System.currentTimeMillis() + CONVERT_TIMEOUT_MS
        var highest = 0
        while (System.currentTimeMillis() < deadline) {
            delay(POLL_INTERVAL_MS)
            val step = post("/v1/stream/poll", buildJsonObject { put("job", job) })
            step.str("url")?.takeIf { it.isNotBlank() }?.let { url ->
                onProgress(100, "")
                // Poll tidak membawa mimeType: hasil konverter memakai format yang diminta (mp3).
                return record(videoId, info.copy(url = url, source = step.str("source") ?: "converter", mimeType = info.mimeType ?: "audio/mpeg"))
            }
            val p = (step["progress"] as? JsonPrimitive)?.intOrNull ?: 0
            if (p > highest) { highest = p; onProgress(p, MusicQueue.progressLabel(p)) }
        }
        throw MusicException("Menyiapkan audio terlalu lama. Coba lagu lain.")
    }

    /** Simpan + catat asal audio supaya bisa dilacak (dan di-fallback bila opus bermasalah). */
    private fun record(videoId: String, info: StreamInfo): StreamInfo {
        lastStreamInfo = info
        if (info.isOpus) Log.w(TAG, "stream $videoId direct opus itag=${info.itag} — awasi pemutaran, fallback konverter via direct=false")
        else Log.i(TAG, "stream $videoId source=${info.source} mimeType=${info.mimeType} itag=${info.itag}")
        return info
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
        /** Retry otomatis maksimal 2x (3 percobaan total), masing-masing tunggu paling lama 30 detik. */
        const val MAX_RETRY_ATTEMPTS = 2
        const val MAX_AUTO_RETRY_WAIT_MS = 30_000L
        /** Backoff bila server retryable tapi tidak memberi Retry-After: 1 dtk, lalu 2 dtk. */
        const val RETRY_BACKOFF_MS = 1_000L
        const val TAG = "MusicClient"
    }
}
