package com.rosyidputrar.taskify.core.media.youtube

import com.rosyidputrar.taskify.core.domain.media.MusicQuality
import com.rosyidputrar.taskify.core.innertube.InnerTube
import com.rosyidputrar.taskify.core.innertube.PlaybackAuthState
import com.rosyidputrar.taskify.core.innertube.models.YouTubeClient
import com.rosyidputrar.taskify.core.innertube.pages.loudnessFromPlayer
import com.rosyidputrar.taskify.core.innertube.pages.pick
import com.rosyidputrar.taskify.core.innertube.pages.pickArr
import com.rosyidputrar.taskify.core.innertube.pages.pickStr
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException

private val mediaJson = Json { ignoreUnknownKeys = true }

/**
 * Resolve stream audio LANGSUNG dari endpoint `player` InnerTube — port
 * `resolveDirectStream`/`tryDirectStream` dari `worker/direct.ts`.
 *
 * Urutan klien: (1) ANDROID 20.10.4 (utama — satu-satunya yang terverifikasi
 * Worker memberi `OK` + `streamingData`), (2) WEB_REMIX, (3) MWEB.
 * Perhatian: WEB_REMIX terverifikasi Worker memberi `UNPLAYABLE` untuk
 * `player`, dan MWEB belum pernah diprobe — jadi rantai fallback ini
 * best-effort; bila semua klien gagal, resolve mengembalikan kegagalan
 * terketik ([StreamFailure]) — tidak ada lagi fallback konverter sejak
 * rewrite direct (Tahap 8).
 * `PlayerBody.playbackContext` (signatureTimestamp) selalu null (Tahap 1),
 * jadi fallback tanpa signatureTimestamp itu juga belum terverifikasi live.
 *
 * Batasan yang sama seperti Worker (jangan dianggap sudah selesai):
 * format yang HANYA punya `signatureCipher` (tanpa `url`) dilewati — pada
 * fixture probe tidak ada satu pun format seperti itu, jadi decipher dari
 * `base.js` TIDAK diimplementasikan. Kalau suatu hari semua format audio
 * berubah jadi cipher-only, jalur ini gagal total (`NoFormat`).
 * Itu kegagalan yang aman, bukan diam-diam rusak.
 *
 * `resolve` mengembalikan `Result<DirectStream>`; kegagalannya terketik
 * ([StreamFailure], dibungkus [StreamFailureException]). `tryResolve` tidak
 * pernah melempar (kecuali pembatalan coroutine) — gagal apa pun → `null`,
 * seperti `tryDirectStream` Worker.
 *
 * `open` supaya tes pemutar bisa memakai subclass palsu: `resolve` memakai `withTimeoutOrNull` yang tidak jalan
 * di bawah `runTest` (pelajaran Tahap 2/3), jadi fake bergate adalah
 * satu-satunya cara deterministik menguji orkestrasi efek.
 */
open class DirectStreamResolver(
    private val innerTube: InnerTube = InnerTube(),
    private val clients: List<YouTubeClient> = DEFAULT_CLIENTS,
    private val attemptTimeoutMs: Long = DIRECT_TIMEOUT_MS,
) {

    /**
     * Resolve satu URL audio langsung. Klien dicoba berurutan; tiap klien
     * mendapat satu retry langsung bila gagal HTTP 429/5xx/403 (flaky
     * sesaat — pola `callInnertubeWithRetry` Worker). `LOGIN_REQUIRED`
     * TIDAK di-retry: sifatnya flaky per permintaan dan `visitorData`
     * terbukti bukan penawar. Bila semua klien gagal, kegagalan yang
     * dikembalikan adalah kegagalan klien UTAMA (paling diagnostik).
     *
     * [authState] diterima sebagai parameter (A.8.3) — pemanggil (Tahap 6)
     * meneruskan state dari `YouTubeAuthStore`; bawaan memakai state
     * transport saat ini.
     */
    open suspend fun resolve(
        videoId: String,
        quality: MusicQuality = MusicQuality.Auto,
        authState: PlaybackAuthState = innerTube.currentAuthState(),
        preferMp4a: Boolean = false,
    ): Result<DirectStream> {
        var firstFailure: StreamFailureException? = null
        for (client in clients) {
            val attempt = resolveWithClient(client, videoId, quality, authState, preferMp4a)
            if (attempt.isSuccess) return attempt
            val failure = attempt.exceptionOrNull() as? StreamFailureException
                ?: return attempt // exception tak terduga: teruskan apa adanya
            if (firstFailure == null) firstFailure = failure
        }
        return Result.failure(firstFailure ?: StreamFailureException(StreamFailure.NoFormat))
    }

    /** Seperti `tryDirectStream` Worker: gagal apa pun → `null`, tidak pernah melempar. */
    open suspend fun tryResolve(
        videoId: String,
        quality: MusicQuality = MusicQuality.Auto,
        authState: PlaybackAuthState = innerTube.currentAuthState(),
        preferMp4a: Boolean = false,
    ): DirectStream? =
        try {
            resolve(videoId, quality, authState, preferMp4a).getOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    private suspend fun resolveWithClient(
        client: YouTubeClient,
        videoId: String,
        quality: MusicQuality,
        authState: PlaybackAuthState,
        preferMp4a: Boolean,
    ): Result<DirectStream> {
        var last: StreamFailureException? = null
        repeat(MAX_ATTEMPTS_PER_CLIENT) { attempt ->
            try {
                return Result.success(resolveOnce(client, videoId, quality, authState, preferMp4a))
            } catch (e: CancellationException) {
                throw e
            } catch (e: StreamFailureException) {
                last = e
                val retryable = e.failure is StreamFailure.Http &&
                    (e.failure.status == 429 || e.failure.status == 403 || e.failure.status >= 500)
                if (!retryable || attempt == MAX_ATTEMPTS_PER_CLIENT - 1) return Result.failure(e)
                // Retry langsung, tanpa jeda — flaky sesaat.
            }
        }
        return Result.failure(last ?: StreamFailureException(StreamFailure.NoFormat))
    }

    private suspend fun resolveOnce(
        client: YouTubeClient,
        videoId: String,
        quality: MusicQuality,
        authState: PlaybackAuthState,
        preferMp4a: Boolean,
    ): DirectStream {
        // Hanya `null` dari withTimeoutOrNull yang berarti timeout: exception
        // HTTP/I-O dipetakan dulu ke StreamFailure di dalam blok, supaya
        // 403/429 tidak salah dilaporkan sebagai Timeout.
        val raw = withTimeoutOrNull(attemptTimeoutMs) {
            try {
                innerTube.player(client, videoId, authState = authState)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw StreamFailureException(e.toStreamFailure())
            }
        } ?: throw StreamFailureException(StreamFailure.Timeout)

        val root = try {
            mediaJson.parseToJsonElement(raw)
        } catch (_: Exception) {
            throw StreamFailureException(StreamFailure.InvalidResponse)
        }
        if (root !is JsonObject) throw StreamFailureException(StreamFailure.InvalidResponse)

        val status = pickStr(root, "playabilityStatus.status")
        if (status == "LOGIN_REQUIRED") {
            throw StreamFailureException(
                StreamFailure.LoginRequired(pickStr(root, "playabilityStatus.reason")),
            )
        }
        if (status != "OK") {
            val reason = pickStr(root, "playabilityStatus.reason")
                .ifEmpty { "playabilityStatus ${status.ifEmpty { "kosong" }}" }
            throw StreamFailureException(StreamFailure.Unplayable(reason))
        }

        val picked = pickAudioFormat(parseAudioFormats(root), quality, preferMp4a)
            ?: throw StreamFailureException(StreamFailure.NoFormat)
        val expires = (pick(root, "streamingData.expiresInSeconds") as? JsonPrimitive)
            ?.content?.toIntOrNull()?.takeIf { it > 0 }
        val loudness = loudnessFromPlayer(root)
        return DirectStream(
            url = picked.url,
            mimeType = picked.mimeType,
            itag = picked.itag,
            bitrate = picked.bitrate,
            expiresInSeconds = expires,
            loudnessDb = loudness?.loudnessDb,
            perceptualLoudnessDb = loudness?.perceptualLoudnessDb,
        )
    }

    /** Hanya format audio ber-URL langsung yang dikumpulkan; cipher-only dilewati. */
    private fun parseAudioFormats(root: JsonElement): List<PlayerAudioFormat> {
        val out = mutableListOf<PlayerAudioFormat>()
        val all = pickArr(root, "streamingData.formats") +
            pickArr(root, "streamingData.adaptiveFormats")
        for (f in all) {
            val mime = pickStr(f, "mimeType")
            val url = pickStr(f, "url")
            if (!mime.startsWith("audio/") || url.isEmpty()) continue
            out.add(
                PlayerAudioFormat(
                    itag = (pick(f, "itag") as? JsonPrimitive)?.content?.toIntOrNull() ?: 0,
                    mimeType = mime,
                    bitrate = (pick(f, "bitrate") as? JsonPrimitive)?.content?.toIntOrNull() ?: 0,
                    url = url,
                    loudnessDb = (pick(f, "loudnessDb") as? JsonPrimitive)?.content?.toDoubleOrNull(),
                ),
            )
        }
        return out
    }

    companion object {
        /** Batas tunggu resolve direct — sama seperti `DIRECT_TIMEOUT_MS` Worker. */
        const val DIRECT_TIMEOUT_MS = 10_000L

        /** Satu percobaan awal + satu retry langsung untuk HTTP flaky. */
        const val MAX_ATTEMPTS_PER_CLIENT = 2

        /** Urutan klien: ANDROID utama, WEB_REMIX, MWEB. */
        val DEFAULT_CLIENTS: List<YouTubeClient> =
            listOf(YouTubeClient.ANDROID, YouTubeClient.WEB_REMIX, YouTubeClient.MWEB)
    }
}

/** HTTP non-2xx dari `player` → [StreamFailure.Http]; timeout → [StreamFailure.Timeout]; I/O lain → [StreamFailure.Network]. */
fun Throwable.toStreamFailure(): StreamFailure =
    when (this) {
        is StreamFailureException -> failure
        is HttpRequestTimeoutException -> StreamFailure.Timeout
        is ResponseException -> StreamFailure.Http(response.status.value)
        is IOException -> StreamFailure.Network(message ?: "I/O gagal")
        else -> StreamFailure.Network(message ?: "gagal memanggil player")
    }
