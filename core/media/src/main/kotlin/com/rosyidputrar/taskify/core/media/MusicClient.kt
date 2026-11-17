package com.rosyidputrar.taskify.core.media

import android.util.Log
import com.rosyidputrar.taskify.core.domain.media.AlbumRef
import com.rosyidputrar.taskify.core.domain.media.ArtistPage
import com.rosyidputrar.taskify.core.domain.media.ArtistRef
import com.rosyidputrar.taskify.core.domain.media.CollectionKind
import com.rosyidputrar.taskify.core.domain.media.DiscoverySection
import com.rosyidputrar.taskify.core.domain.media.LyricLine
import com.rosyidputrar.taskify.core.domain.media.Lyrics
import com.rosyidputrar.taskify.core.domain.media.MoodCategory
import com.rosyidputrar.taskify.core.domain.media.MoodGroup
import com.rosyidputrar.taskify.core.domain.media.MoodPage
import com.rosyidputrar.taskify.core.domain.media.MusicCatalog
import com.rosyidputrar.taskify.core.domain.media.MusicCollection
import com.rosyidputrar.taskify.core.domain.media.MusicQuality
import com.rosyidputrar.taskify.core.domain.media.MusicQueue
import com.rosyidputrar.taskify.core.domain.media.PlaylistRef
import com.rosyidputrar.taskify.core.domain.media.PodcastRef
import com.rosyidputrar.taskify.core.domain.media.RadioPageResult
import com.rosyidputrar.taskify.core.domain.media.RadioStation
import com.rosyidputrar.taskify.core.domain.media.ResolvedLink
import com.rosyidputrar.taskify.core.domain.media.SearchFilter
import com.rosyidputrar.taskify.core.domain.media.SearchResults
import com.rosyidputrar.taskify.core.domain.media.Song
import com.rosyidputrar.taskify.core.domain.media.SponsorSegment
import com.rosyidputrar.taskify.core.domain.media.StreamSource
import com.rosyidputrar.taskify.core.domain.media.Suggestion
import com.rosyidputrar.taskify.core.domain.media.Thumb
import com.rosyidputrar.taskify.core.domain.media.TopResult
import com.rosyidputrar.taskify.core.domain.media.Track
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
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
) : Exception(message) {
    /** Sesi Supabase habis (setelah refresh pun masih 401) → UI menawarkan "Masuk ulang". */
    val sessionExpired: Boolean get() = status == 401
}

/** Hasil resolve audio: URL siap putar + asal audionya (item 10-klien). */
data class StreamInfo(
    val url: String?,
    val source: String?,
    val mimeType: String?,
    val itag: Int?,
    val bitrate: Int? = null,
    /** Epoch ms kedaluwarsa URL direct (`expiresInSeconds`). */
    val expiresAt: Long? = null,
    val loudnessDb: Double? = null,
    val job: String? = null,
) {
    /** Jalur direct yang memilih opus (itag 251) — perlu dilacak, bisa gagal di perangkat tertentu. */
    val isOpus: Boolean get() = mimeType?.contains("opus", ignoreCase = true) == true

    fun toSource(): StreamSource = StreamSource(source.orEmpty(), mimeType, itag, expiresAt, loudnessDb, job)

    companion object {
        fun fromInit(init: JsonObject, now: Long = System.currentTimeMillis()): StreamInfo {
            val str = { k: String -> (init[k] as? JsonPrimitive)?.takeIf { it.isString }?.content }
            return StreamInfo(
                url = str("url")?.takeIf { it.isNotBlank() },
                source = str("source"),
                mimeType = str("mimeType"),
                itag = (init["itag"] as? JsonPrimitive)?.intOrNull,
                bitrate = (init["bitrate"] as? JsonPrimitive)?.intOrNull,
                expiresAt = (init["expiresInSeconds"] as? JsonPrimitive)?.longOrNull?.let { now + it * 1_000 },
                loudnessDb = (init["loudnessDb"] as? JsonPrimitive)?.doubleOrNull,
                job = str("job"),
            )
        }
    }
}

/** Opsi resolve audio: kualitas direct, paksa konverter (`direct:false`), abaikan cache init (`force`). */
data class StreamOptions(val quality: MusicQuality = MusicQuality.Auto, val forceConverter: Boolean = false, val force: Boolean = false)

/** Hasil `/v1/now-playing` (atau resolve biasa): info lagu (bila diminta) + audio siap putar. */
data class Playback(val track: Track?, val info: StreamInfo)

/**
 * Klien REST Worker music-taskify (port `api-client.ts`). Token Supabase diambil SEGAR setiap
 * permintaan; penanda audio siap = ADANYA `url` (bukan progress 100); progres tidak pernah mundur.
 *
 * 401 → [refresh] sesi sekali lalu ulangi; bila masih 401 baru dilempar ("Masuk ulang").
 * [http] hanya diisi pemanggil tes (MockEngine); pemakaian normal memakai OkHttp bawaan.
 */
open class MusicClient(
    private val baseUrl: String,
    private val token: suspend () -> String?,
    http: HttpClient? = null,
    private val refresh: suspend () -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
) {
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

    /** `false` setelah Worker menjawab 503 R2_NOT_CONFIGURED: arsip tidak dicoba lagi sesi ini. */
    @Volatile var archiveAvailable: Boolean = true
        private set

    /**
     * Satu permintaan tanpa retry. Galat HTTP dipetakan dari amplop error Worker (item 4, pola yang
     * sama dengan `TikTokClient.error`); header `Retry-After` dipakai bila bodi tidak membawa
     * `retryAfterSeconds`. [get] = query string untuk rute GET (`/v1/radio/stations`, sponsorblock).
     */
    private suspend fun sendOnce(path: String, body: JsonObject?, query: Map<String, String> = emptyMap()): JsonElement {
        var t = runCatching { token() }.getOrNull()
        if (t.isNullOrBlank()) {
            runCatching { refresh() }
            t = runCatching { token() }.getOrNull()
        }
        if (t.isNullOrBlank()) throw MusicException(MSG_SESSION, 401, CODE_UNAUTHORIZED)
        val res = try {
            if (body == null) http.get("$base$path") {
                header("Authorization", "Bearer $t")
                query.forEach { (k, v) -> parameter(k, v) }
            } else http.post("$base$path") {
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
        return runCatching { json.parseToJsonElement(text) }.getOrElse { throw MusicException("Balasan server musik tidak bisa dibaca.") }
    }

    /** Permintaan dengan retry 429/5xx yang `retryable` (ikut `Retry-After`) + sekali refresh sesi pada 401. */
    private suspend fun send(path: String, body: JsonObject?, query: Map<String, String> = emptyMap()): JsonElement {
        var attempt = 0
        var refreshed = false
        while (true) {
            try {
                return sendOnce(path, body, query)
            } catch (e: CancellationException) {
                throw e
            } catch (e: MusicException) {
                if (e.status == 401 && !refreshed) {
                    refreshed = true
                    Log.i(TAG, "401 di $path — refresh sesi lalu ulangi sekali")
                    runCatching { refresh() }
                    continue
                }
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

    private suspend fun post(path: String, body: JsonObject): JsonObject =
        send(path, body) as? JsonObject ?: throw MusicException("Balasan server musik tidak bisa dibaca.")

    internal fun errorFrom(status: Int, text: String, headers: Headers): MusicException {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
        val errObj = root?.get("error") as? JsonObject
        // Bentuk lama: `error` berupa string polos — tetap dipakai sebagai pesan.
        val legacy = (root?.get("error") as? JsonPrimitive)?.takeIf { it.isString }?.content
        val serverMessage = errObj?.str("message")?.takeIf { it.isNotBlank() } ?: legacy?.takeIf { it.isNotBlank() }
        val code = errObj?.str("code")
        val retryable = (errObj?.get("retryable") as? JsonPrimitive)?.booleanOrNull ?: false
        val retryAfter = (errObj?.get("retryAfterSeconds") as? JsonPrimitive)?.longOrNull
            ?: headers["Retry-After"]?.toLongOrNull()
        return MusicException(MusicErrors.message(status, code, serverMessage, retryAfter), status, code, retryable, retryAfter)
    }

    // ------------------------------------------------------------------ pencarian & saran

    open suspend fun search(query: String, filter: SearchFilter): SearchResults =
        results(post("/v1/search", buildJsonObject { put("q", query); filter.value?.let { put("filter", it) } }))

    /** Halaman lanjutan dikirim HANYA dengan cursor (menyertakan `q` ditolak 400). */
    open suspend fun searchMore(cursor: String): SearchResults = results(post("/v1/search", buildJsonObject { put("cursor", cursor) }))

    open suspend fun suggest(query: String): List<Suggestion> =
        (post("/v1/suggest", buildJsonObject { put("q", query) })["suggestions"] as? JsonArray).orEmpty().mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            Suggestion(o.str("text") ?: return@mapNotNull null, o.str("type") ?: "query", o.str("subtitle").orEmpty(), o.str("videoId").orEmpty(), o.str("browseId").orEmpty())
        }

    open suspend fun resolveUrl(url: String): ResolvedLink {
        val o = post("/v1/resolve-url", buildJsonObject { put("url", url) })
        return ResolvedLink(o.str("kind").orEmpty(), o.str("videoId").orEmpty(), o.str("browseId").orEmpty(), o.str("playlistId").orEmpty(), o.str("url").orEmpty())
    }

    // ------------------------------------------------------------------ lagu & lirik

    open suspend fun track(videoId: String, includeLyrics: Boolean = false): Track =
        trackFrom(post("/v1/track", buildJsonObject { put("videoId", videoId); if (includeLyrics) put("includeLyrics", true) }), videoId, includeLyrics)

    /** Beberapa lagu sekaligus (≤ 10); lagu yang gagal dilewati. */
    open suspend fun tracks(videoIds: List<String>): List<Track> =
        (post("/v1/tracks", buildJsonObject { put("videoIds", JsonArray(videoIds.take(10).map { JsonPrimitive(it) })) })["results"] as? JsonArray).orEmpty()
            .mapNotNull { e -> ((e as? JsonObject)?.get("track") as? JsonObject)?.let { t -> trackFrom(t, t.str("videoId").orEmpty(), false) } }

    suspend fun lyrics(lyricsId: String): String = post("/v1/lyrics", buildJsonObject { put("lyricsId", lyricsId) }).str("text").orEmpty()

    /** Lirik bergulir (LRCLib, fallback teks YouTube). */
    open suspend fun syncedLyrics(videoId: String): Lyrics = lyricsFrom(post("/v1/lyrics/synced", buildJsonObject { put("videoId", videoId) }))
        ?: Lyrics(null, "", "lrclib")

    // ------------------------------------------------------------------ koleksi

    open suspend fun album(browseId: String): MusicCollection = collection(CollectionKind.Album, post("/v1/album", buildJsonObject { put("browseId", browseId) }), browseId)
    open suspend fun playlist(browseId: String): MusicCollection =
        collection(CollectionKind.Playlist, post("/v1/playlist", buildJsonObject { put("browseId", if (browseId.startsWith("VL")) browseId else "VL$browseId") }), browseId)
    open suspend fun podcast(browseId: String): MusicCollection = podcastFrom(post("/v1/podcast", buildJsonObject { put("browseId", browseId) }), browseId)

    /** Halaman lanjutan koleksi; cursor dikirim ke rute yang SAMA dengan halaman pertama. */
    open suspend fun collectionMore(kind: CollectionKind, cursor: String, id: String): MusicCollection {
        val body = buildJsonObject { put("cursor", cursor) }
        return when (kind) {
            CollectionKind.Album -> collection(kind, post("/v1/album", body), id)
            CollectionKind.Playlist -> collection(kind, post("/v1/playlist", body), id)
            CollectionKind.Podcast -> podcastFrom(post("/v1/podcast", body), id)
        }
    }

    open suspend fun artist(browseId: String): ArtistPage {
        val o = post("/v1/artist", buildJsonObject { put("browseId", browseId) })
        return ArtistPage(
            o.str("browseId") ?: browseId, o.str("name").orEmpty(), o.str("subscribers").orEmpty(), o.str("monthlyListeners").orEmpty(),
            o.str("description").orEmpty(), o.thumbs(), (o["topSongs"] as? JsonArray).songs(), o.str("topSongsPlaylistId")?.takeIf { it.isNotBlank() },
            (o["albums"] as? JsonArray).albums(), (o["singles"] as? JsonArray).albums(), (o["relatedArtists"] as? JsonArray).artists(),
        )
    }

    // ------------------------------------------------------------------ jelajah

    open suspend fun home(): List<DiscoverySection> = sections(post("/v1/home", buildJsonObject { }))
    open suspend fun charts(country: String? = "ID"): List<DiscoverySection> = sections(post("/v1/charts", buildJsonObject { country?.let { put("country", it) } }))
    open suspend fun newReleases(): List<DiscoverySection> = sections(post("/v1/new-releases", buildJsonObject { }))

    open suspend fun moods(): List<MoodGroup> = (post("/v1/mood", buildJsonObject { })["sections"] as? JsonArray).orEmpty().mapNotNull { e ->
        val o = e as? JsonObject ?: return@mapNotNull null
        val items = (o["items"] as? JsonArray).orEmpty().mapNotNull { i ->
            val m = i as? JsonObject ?: return@mapNotNull null
            MoodCategory(m.str("title") ?: return@mapNotNull null, m.str("params") ?: return@mapNotNull null)
        }
        MoodGroup(o.str("title").orEmpty(), items).takeIf { items.isNotEmpty() }
    }

    open suspend fun mood(params: String, title: String = ""): MoodPage {
        val o = post("/v1/mood", buildJsonObject { put("params", params) })
        return MoodPage(o.str("mood")?.takeIf { it.isNotBlank() } ?: title, params, sections(o))
    }

    // ------------------------------------------------------------------ radio

    /** Mix radio dari lagu ([videoId]) atau id mix `RDAMVM…`/`RDCLAK…` ([playlistId]). */
    open suspend fun radio(videoId: String? = null, playlistId: String? = null): RadioPageResult =
        radioFrom(post("/v1/radio", buildJsonObject { videoId?.let { put("videoId", it) }; playlistId?.let { put("playlistId", it) } }))

    open suspend fun radioMore(cursor: String): RadioPageResult = radioFrom(post("/v1/radio", buildJsonObject { put("cursor", cursor) }))

    /** Stasiun radio langsung; luring/galat → daftar lokal [RadioStations.FALLBACK]. */
    open suspend fun stations(): List<RadioStation> {
        val arr = send("/v1/radio/stations", null) as? JsonArray ?: return RadioStations.FALLBACK
        return arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            RadioStation(
                o.str("id") ?: return@mapNotNull null, o.str("name").orEmpty(), o.str("url") ?: return@mapNotNull null, o.str("mood").orEmpty(),
                o.str("description").orEmpty(), o.str("provider").orEmpty(), o.str("homepage").orEmpty(), (o["vocals"] as? JsonPrimitive)?.booleanOrNull ?: false,
            )
        }.ifEmpty { RadioStations.FALLBACK }
    }

    /** Segmen SponsorBlock (kategori bawaan); `[]` = tanpa segmen. 502 = layanan mati → dilempar. */
    open suspend fun sponsorSegments(videoId: String): List<SponsorSegment> {
        val o = send("/v1/sponsorblock", null, mapOf("videoId" to videoId)) as? JsonObject ?: return emptyList()
        return (o["segments"] as? JsonArray).orEmpty().mapNotNull { e ->
            val s = e as? JsonObject ?: return@mapNotNull null
            val start = (s["start"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            val end = (s["end"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null
            SponsorSegment(s.str("category").orEmpty(), start, end, s.str("actionType") == "mute")
        }.sortedBy { it.start }
    }

    // ------------------------------------------------------------------ audio

    /** URL audio siap putar (mp3: m4a dari konverter tidak bisa diputar — lihat catatan webapp). */
    open suspend fun resolveStream(videoId: String, onProgress: (Int, String) -> Unit = { _, _ -> }): String =
        resolveStreamInfo(videoId, onProgress = onProgress).url
            ?: throw MusicException("Server musik tidak memberi URL audio.")

    /** Kompatibel lama: hanya flag paksa konverter. */
    open suspend fun resolveStreamInfo(
        videoId: String,
        forceConverter: Boolean = false,
        onProgress: (Int, String) -> Unit = { _, _ -> },
    ): StreamInfo = resolveStreamInfo(videoId, StreamOptions(forceConverter = forceConverter), onProgress)

    /**
     * Resolve audio + info asalnya (item 10-klien). Init langsung (direct/R2) mengembalikan
     * `url` + `source`/`mimeType`/`itag`/`expiresInSeconds`; jalur konverter mengembalikan `job`
     * lalu di-poll sampai `url` muncul. Poll yang dijawab INVALID_JOB / 502 (job basi, konverter
     * tersendat) di-init ulang SEKALI dengan `force: true` (lewati cache init 5 menit).
     */
    open suspend fun resolveStreamInfo(videoId: String, options: StreamOptions, onProgress: (Int, String) -> Unit = { _, _ -> }): StreamInfo {
        onProgress(0, MusicQueue.progressLabel(0))
        val init = post("/v1/stream/init", initBody(videoId, options))
        return finishStream(videoId, StreamInfo.fromInit(init, now()), options, onProgress)
    }

    /**
     * Satu panggilan untuk lagu yang mulai diputar: info lagu + lirik inline + audio. Bila bagian
     * stream membawa `{error}`, info lagu tetap dipakai dan audio di-resolve ulang lewat init biasa.
     */
    open suspend fun playback(videoId: String, options: StreamOptions, withTrack: Boolean, onProgress: (Int, String) -> Unit = { _, _ -> }): Playback {
        if (!withTrack) return Playback(null, resolveStreamInfo(videoId, options, onProgress))
        onProgress(0, MusicQueue.progressLabel(0))
        val o = post("/v1/now-playing", initBody(videoId, options).let { b -> JsonObject(b + ("includeLyrics" to JsonPrimitive(true))) })
        val track = (o["track"] as? JsonObject)?.let { trackFrom(it, videoId, true) }
        val stream = o["stream"] as? JsonObject
        val info = if (stream == null || stream["error"] != null) {
            stream?.get("error")?.let { Log.w(TAG, "now-playing $videoId: stream gagal, init ulang") }
            resolveStreamInfo(videoId, options.copy(force = true), onProgress)
        } else finishStream(videoId, StreamInfo.fromInit(stream, now()), options, onProgress)
        return Playback(track, info)
    }

    private fun initBody(videoId: String, options: StreamOptions) = buildJsonObject {
        put("videoId", videoId); put("format", "mp3")
        if (options.quality != MusicQuality.Auto) put("quality", options.quality.value)
        if (options.forceConverter) put("direct", false)
        if (options.force) put("force", true)
    }

    private suspend fun finishStream(videoId: String, info: StreamInfo, options: StreamOptions, onProgress: (Int, String) -> Unit): StreamInfo {
        info.url?.let { onProgress(100, ""); return record(videoId, info) }
        var job = info.job ?: throw MusicException("Server musik tidak memberi tugas konversi.")
        var current = info
        var reinit = false
        val deadline = now() + CONVERT_TIMEOUT_MS
        var highest = 0
        while (now() < deadline) {
            delay(POLL_INTERVAL_MS)
            val step = try {
                post("/v1/stream/poll", buildJsonObject { put("job", job) })
            } catch (e: MusicException) {
                if (reinit || !(e.code == CODE_INVALID_JOB || e.status == 502)) throw e
                // Job basi / konverter tersendat → init ulang segar, sekali.
                reinit = true
                Log.i(TAG, "poll $videoId gagal (${e.code ?: e.status}) — init ulang force")
                val again = StreamInfo.fromInit(post("/v1/stream/init", initBody(videoId, options.copy(force = true))), now())
                again.url?.let { onProgress(100, ""); return record(videoId, again) }
                job = again.job ?: throw e
                current = again
                continue
            }
            step.str("url")?.takeIf { it.isNotBlank() }?.let { url ->
                onProgress(100, "")
                // Poll tidak membawa mimeType: hasil konverter memakai format yang diminta (mp3).
                return record(videoId, current.copy(url = url, source = step.str("source") ?: StreamSource.SOURCE_CONVERTER,
                    mimeType = current.mimeType ?: "audio/mpeg", job = job))
            }
            val p = (step["progress"] as? JsonPrimitive)?.intOrNull ?: 0
            if (p > highest) { highest = p; onProgress(p, MusicQueue.progressLabel(p)) }
        }
        throw MusicException("Menyiapkan audio terlalu lama. Coba lagu lain.", code = "TIMEOUT")
    }

    /**
     * Arsipkan hasil konverter ke R2 → URL yang bisa di-seek. 409 NOT_READY dicoba ulang beberapa
     * kali; 503 R2_NOT_CONFIGURED mematikan arsip untuk sesi ini. Gagal → `null` (tidak fatal).
     */
    open suspend fun archive(videoId: String, job: String): StreamInfo? {
        if (!archiveAvailable) return null
        repeat(ARCHIVE_ATTEMPTS) { attempt ->
            try {
                val o = post("/v1/stream/archive", buildJsonObject { put("videoId", videoId); put("format", "mp3"); put("job", job) })
                val url = o.str("url")?.takeIf { it.isNotBlank() } ?: return null
                return StreamInfo(url, StreamSource.SOURCE_R2, o.str("mimeType") ?: "audio/mpeg", null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: MusicException) {
                when {
                    e.status == 503 || e.code == CODE_R2_NOT_CONFIGURED -> { archiveAvailable = false; return null }
                    e.status == 409 || e.code == CODE_NOT_READY -> if (attempt < ARCHIVE_ATTEMPTS - 1) delay(ARCHIVE_RETRY_MS) else return null
                    else -> return null
                }
            }
        }
        return null
    }

    /** Simpan + catat asal audio supaya bisa dilacak (dan di-fallback bila opus bermasalah). */
    private fun record(videoId: String, info: StreamInfo): StreamInfo {
        lastStreamInfo = info
        if (info.isOpus) Log.i(TAG, "stream $videoId direct opus itag=${info.itag} — fallback konverter otomatis bila gagal diputar")
        else Log.i(TAG, "stream $videoId source=${info.source} mimeType=${info.mimeType} itag=${info.itag}")
        return info
    }

    // ------------------------------------------------------------------ parser

    private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.int(key: String) = (this[key] as? JsonPrimitive)?.intOrNull

    private fun JsonObject.thumbs(key: String = "thumbnails"): List<Thumb> = (this[key] as? JsonArray).orEmpty().mapNotNull { t ->
        val o = t as? JsonObject ?: return@mapNotNull null
        Thumb(o.str("url") ?: return@mapNotNull null, o.int("width") ?: 0, o.int("height") ?: 0)
    }

    private fun results(o: JsonObject) = SearchResults(
        top = (o["topResult"] as? JsonObject)?.let { t ->
            TopResult(t.str("title").orEmpty(), t.str("videoId").orEmpty(), (t["subtitles"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }, t.thumbs(), t.str("url").orEmpty())
        }?.takeIf { it.title.isNotBlank() },
        songs = (o["songs"] as? JsonArray).songs(),
        albums = (o["albums"] as? JsonArray).albums(),
        playlists = (o["playlists"] as? JsonArray).playlists(),
        artists = (o["artists"] as? JsonArray).artists(),
        podcasts = (o["podcasts"] as? JsonArray).orEmpty().mapNotNull { e ->
            val p = e as? JsonObject ?: return@mapNotNull null
            PodcastRef(p.str("browseId") ?: return@mapNotNull null, p.str("title").orEmpty(), p.str("author").orEmpty(), p.thumbs())
        },
        episodes = (o["episodes"] as? JsonArray).episodes(),
        cursor = o.str("cursor")?.takeIf { it.isNotBlank() },
    )

    private fun trackFrom(o: JsonObject, videoId: String, includeLyrics: Boolean): Track {
        val lyricsEl = o["lyrics"]
        return Track(
            o.str("videoId") ?: videoId, o.str("lyricsId").orEmpty(), (o["upNext"] as? JsonArray).songs(),
            o.int("durationSeconds"), (o["loudnessDb"] as? JsonPrimitive)?.doubleOrNull,
            (lyricsEl as? JsonObject)?.let(::lyricsFrom),
            // `""` = tidak diminta; `null` = diminta, tidak ada; objek = ada.
            lyricsChecked = includeLyrics && (lyricsEl is JsonNull || lyricsEl is JsonObject),
            song = o.str("title")?.takeIf { it.isNotBlank() }?.let { title ->
                Song(o.str("videoId") ?: videoId, title, o.str("artist").orEmpty(), o.str("duration").orEmpty(), o.thumbs(), o.str("canonicalUrl").orEmpty(), o.int("durationSeconds"))
            },
        )
    }

    private fun lyricsFrom(o: JsonObject): Lyrics? {
        val syncedEl = o["synced"]
        val synced = when (syncedEl) {
            is JsonArray -> syncedEl.mapNotNull { e ->
                val l = e as? JsonObject ?: return@mapNotNull null
                LyricLine((l["t"] as? JsonPrimitive)?.doubleOrNull ?: return@mapNotNull null, l.str("text").orEmpty())
            }
            else -> null
        }
        return Lyrics(synced, o.str("plain").orEmpty(), o.str("source").orEmpty())
    }

    private fun collection(kind: CollectionKind, o: JsonObject, id: String): MusicCollection {
        val thumbs = o.thumbs()
        return MusicCollection(
            kind, o.str("id") ?: id, o.str("title").orEmpty(),
            listOf(o.str("by").orEmpty(), o.str("year").orEmpty()).filter { it.isNotBlank() }.joinToString(" · "),
            thumbs, MusicCatalog.albumTracks((o["tracks"] as? JsonArray).songs(), thumbs), o.str("cursor")?.takeIf { it.isNotBlank() },
            o.int("trackCount"),
        )
    }

    private fun podcastFrom(o: JsonObject, id: String): MusicCollection {
        val thumbs = o.thumbs()
        return MusicCollection(
            CollectionKind.Podcast, o.str("browseId") ?: id, o.str("title").orEmpty(), o.str("author").orEmpty(), thumbs,
            MusicCatalog.albumTracks((o["episodes"] as? JsonArray).episodes(), thumbs), o.str("cursor")?.takeIf { it.isNotBlank() },
            description = o.str("description").orEmpty(),
        )
    }

    private fun radioFrom(o: JsonObject) =
        RadioPageResult((o["songs"] as? JsonArray).songs(), o.str("playlistId").orEmpty(), o.str("seedVideoId").orEmpty(), o.str("cursor")?.takeIf { it.isNotBlank() })

    private fun sections(o: JsonObject): List<DiscoverySection> = (o["sections"] as? JsonArray).orEmpty().mapNotNull { e ->
        val s = e as? JsonObject ?: return@mapNotNull null
        val title = s.str("title").orEmpty()
        val items = s["items"] as? JsonArray
        when (s.str("kind")) {
            "songs" -> DiscoverySection.Songs(title, items.songs()).takeIf { it.items.isNotEmpty() }
            "albums" -> DiscoverySection.Albums(title, items.albums()).takeIf { it.items.isNotEmpty() }
            "artists" -> DiscoverySection.Artists(title, items.artists()).takeIf { it.items.isNotEmpty() }
            "playlists" -> DiscoverySection.Playlists(title, items.playlists()).takeIf { it.items.isNotEmpty() }
            else -> null // rak yang tidak dikenal dilewati, bukan galat
        }
    }

    private fun JsonArray?.songs(): List<Song> = this.orEmpty().mapNotNull { e ->
        val s = e as? JsonObject ?: return@mapNotNull null
        val id = s.str("videoId")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        Song(id, s.str("title").orEmpty(), s.str("artist").orEmpty(), s.str("duration").orEmpty(), s.thumbs(), s.str("url").orEmpty(), s.int("durationSeconds"))
    }

    /** Episode diputar sebagai lagu: artis = nama acara. */
    private fun JsonArray?.episodes(): List<Song> = this.orEmpty().mapNotNull { e ->
        val s = e as? JsonObject ?: return@mapNotNull null
        val id = s.str("videoId")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val show = s.str("show").orEmpty()
        val published = s.str("publishedAt").orEmpty()
        Song(id, s.str("title").orEmpty(), listOf(show, published).filter { it.isNotBlank() }.joinToString(" · "), s.str("duration").orEmpty(), s.thumbs(), s.str("url").orEmpty(), s.int("durationSeconds"))
    }

    private fun JsonArray?.albums(): List<AlbumRef> = this.orEmpty().mapNotNull { e ->
        val a = e as? JsonObject ?: return@mapNotNull null
        AlbumRef(a.str("browseId")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null, a.str("title").orEmpty(), a.str("artist").orEmpty(), a.str("year").orEmpty(), a.thumbs())
    }

    private fun JsonArray?.playlists(): List<PlaylistRef> = this.orEmpty().mapNotNull { e ->
        val p = e as? JsonObject ?: return@mapNotNull null
        val browse = p.str("browseId").orEmpty()
        val pid = p.str("playlistId").orEmpty()
        if (browse.isBlank() && pid.isBlank()) return@mapNotNull null
        PlaylistRef(browse, pid, p.str("title").orEmpty(), p.str("author").orEmpty(), p.thumbs())
    }

    private fun JsonArray?.artists(): List<ArtistRef> = this.orEmpty().mapNotNull { e ->
        val a = e as? JsonObject ?: return@mapNotNull null
        ArtistRef(a.str("browseId")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null, a.str("name").orEmpty(), a.str("subscribers").orEmpty(), a.thumbs())
    }

    companion object {
        const val POLL_INTERVAL_MS = 1_200L
        const val CONVERT_TIMEOUT_MS = 90_000L
        /** Retry otomatis maksimal 2x (3 percobaan total), masing-masing tunggu paling lama 30 detik. */
        const val MAX_RETRY_ATTEMPTS = 2
        const val MAX_AUTO_RETRY_WAIT_MS = 30_000L
        /** Backoff bila server retryable tapi tidak memberi Retry-After: 1 dtk, lalu 2 dtk. */
        const val RETRY_BACKOFF_MS = 1_000L
        const val ARCHIVE_ATTEMPTS = 4
        const val ARCHIVE_RETRY_MS = 3_000L
        const val TAG = "MusicClient"

        const val CODE_UNAUTHORIZED = "UNAUTHORIZED"
        const val CODE_INVALID_JOB = "INVALID_JOB"
        const val CODE_NOT_READY = "NOT_READY"
        const val CODE_R2_NOT_CONFIGURED = "R2_NOT_CONFIGURED"
        const val MSG_SESSION = "Sesi berakhir. Masuk ulang untuk memutar musik."
    }
}

/** Pesan galat siap tampil per `code` amplop Worker (pesan server dipakai bila lebih spesifik). */
object MusicErrors {
    fun message(status: Int, code: String?, server: String?, retryAfter: Long?): String = when (code) {
        "UNAUTHORIZED" -> MusicClient.MSG_SESSION
        "FORBIDDEN" -> "Akun ini belum diizinkan memakai pemutar musik."
        "RATE_LIMITED" -> server ?: "Terlalu banyak permintaan. Coba lagi${retryAfter?.let { " dalam $it detik" } ?: " sebentar lagi"}."
        "UPSTREAM_UNAVAILABLE" -> "YouTube Music sedang tidak bisa dihubungi. Coba lagi sebentar."
        "CONVERTER_ERROR" -> "Konverter audio sedang bermasalah. Coba lagi atau pilih lagu lain."
        "INVALID_CURSOR" -> "Halaman lanjutan sudah kedaluwarsa. Ulangi pencarian."
        "NOT_FOUND" -> server ?: "Tidak ditemukan."
        "R2_NOT_CONFIGURED" -> "Arsip audio belum aktif di server."
        "INTERNAL" -> "Server musik sedang bermasalah. Coba lagi sebentar."
        else -> server ?: when {
            status == 401 -> MusicClient.MSG_SESSION
            status == 403 -> "Akun ini belum diizinkan memakai pemutar musik."
            status == 429 -> "Terlalu banyak permintaan. Coba lagi sebentar lagi."
            status >= 500 -> "Server musik sedang bermasalah. Coba lagi sebentar."
            else -> "Server musik balas HTTP $status."
        }
    }
}

/** Daftar stasiun lokal untuk luring (sumber kebenaran tetap `GET /v1/radio/stations`). */
object RadioStations {
    val FALLBACK = listOf(
        RadioStation("soma-dronezone", "Drone Zone", "https://ice1.somafm.com/dronezone-128-mp3", "deep", "Ambient tanpa beat. Paling netral untuk kerja dalam.", "SomaFM", "https://somafm.com/dronezone/"),
        RadioStation("soma-deepspaceone", "Deep Space One", "https://ice1.somafm.com/deepspaceone-128-mp3", "deep", "Ambient luar angkasa, tenang dan panjang.", "SomaFM", "https://somafm.com/deepspaceone/"),
        RadioStation("soma-groovesalad", "Groove Salad", "https://ice1.somafm.com/groovesalad-128-mp3", "light", "Downtempo ringan untuk kerja santai.", "SomaFM", "https://somafm.com/groovesalad/"),
        RadioStation("soma-sonicuniverse", "Sonic Universe", "https://ice1.somafm.com/sonicuniverse-128-mp3", "jazz", "Jazz modern tanpa banyak vokal.", "SomaFM", "https://somafm.com/sonicuniverse/"),
        RadioStation("soma-lush", "Lush", "https://ice1.somafm.com/lush-128-mp3", "chill", "Elektronik lembut dengan vokal.", "SomaFM", "https://somafm.com/lush/", vocals = true),
    )
}
