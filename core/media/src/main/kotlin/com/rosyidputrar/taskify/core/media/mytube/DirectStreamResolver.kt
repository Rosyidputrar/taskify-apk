package com.rosyidputrar.taskify.core.media.mytube

import com.rosyidputrar.taskify.core.domain.media.MusicQuality
import com.rosyidputrar.taskify.core.innertube.InnerTube
import com.rosyidputrar.taskify.core.innertube.PlaybackAuthState
import com.rosyidputrar.taskify.core.innertube.models.MyTubeClient
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
 * Urutan klien mengikuti ArchiveTune jalur live (C2-1,
 * `morideobfuscator/javascript/src/index.js:34-36,736`): login penuh →
 * WEB_CREATOR dulu, lalu WEB_REMIX, MWEB, ANDROID; anonim → ANDROID dulu
 * (satu-satunya yang terverifikasi memberi URL anonim; WEB_REMIX/MWEB
 * anonim terverifikasi UNPLAYABLE dari VM), lalu WEB_REMIX, MWEB.
 * `clients` eksplisit (dipakai tes) mengalahkan urutan ini.
 *
 * `PlayerBody.playbackContext` (signatureTimestamp) diisi lewat
 * [signatureTimestampProvider] bila klien mendukungnya (C1-8, C2-5);
 * PoToken ditempel bila state sudah membawa token (C1-3/4 — mesin mint
 * BotGuard Tahap 5; tanpa token, field tetap null seperti sebelumnya).
 *
 * Batasan decipher (C1-7, Tahap 4): format yang HANYA punya
 * `signatureCipher` dikumpulkan dengan `cipher`-nya tapi TIDAK dipilih —
 * decipher dari `base.js` BELUM diimplementasikan. Kalau suatu hari semua
 * format audio berubah jadi cipher-only, jalur ini gagal total
 * (`NoFormat`). Itu kegagalan yang aman, bukan diam-diam rusak.
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
    private val clients: List<MyTubeClient>? = null,
    private val attemptTimeoutMs: Long = DIRECT_TIMEOUT_MS,
    /** Penyedia `signatureTimestamp` (C1-8); `null` = tanpa timestamp (perilaku lama). */
    private val signatureTimestampProvider: (suspend (MyTubeClient, String) -> Int?)? = null,
    /** Pastikan `visitorData` siap bila tanpa konteks login (C2-13); `null` = tanpa jaminan (perilaku lama). */
    private val ensureVisitorData: (suspend () -> String?)? = null,
    /** Pastikan PoToken sebelum resolve (Tahap 5); `null` = tanpa token (perilaku Tahap 2-4). */
    private val poTokenEnsurer: PoTokenEnsurer? = null,
    /**
     * Blacklist klien gagal (Tahap 7, C2-19); `null` = tanpa blacklist
     * (perilaku lama). Klien yang sedang di-blacklist untuk video + sesi
     * ini dilewati — resolve pindah ke klien lain.
     */
    private val failedStreamClients: FailedStreamClients? = null,
) {

    /**
     * Resolve satu URL audio langsung. Klien dicoba berurutan; tiap klien
     * mendapat satu retry langsung bila gagal HTTP 429/5xx/403 (flaky
     * sesaat — pola `callInnertubeWithRetry` Worker). `LOGIN_REQUIRED`
     * di-retry sampai dua kali tambahan (total 3 percobaan per klien):
     * status ini terbukti flaky per permintaan — `player` yang sama bisa
     * menjawab `LOGIN_REQUIRED` lalu `OK` pada percobaan berikut — dan
     * jatuh ke klien berikut tanpa retry justru yang berbahaya: fallback
     * WEB_REMIX ber-cookie bisa menghasilkan URL ber-`n` yang tidak
     * di-decipher, lalu googlevideo menolaknya dengan 403 saat diputar
     * (teori koheren 403, lihat PLAN.md §D — TEORI, bukan fakta).
     *
     * Tangga kualitas (Tahap 8, C2-23 — ArchiveTune `resolvePlaybackData`):
     * bila satu tier gagal total (semua klien), coba tier berikut
     * ([qualityLadder]); hanya kegagalan format (`NoFormat`/`Unplayable`)
     * yang menuruni tangga — kegagalan auth/jaringan/rate-limit tidak
     * bergantung kualitas, jadi langsung dikembalikan. Bila semua tier
     * gagal, kegagalan tier terakhir yang dikembalikan (pola AT).
     * [networkMetered] menurunkan `Auto` ke `High` (C2-25).
     *
     * Bila semua klien gagal, kegagalan yang dikembalikan adalah kegagalan
     * klien UTAMA (paling diagnostik).
     *
     * [authState] diterima sebagai parameter (A.8.3) — pemanggil (Tahap 6)
     * meneruskan state dari `MyTubeAuthStore`; bawaan memakai state
     * transport saat ini.
     */
    open suspend fun resolve(
        videoId: String,
        quality: MusicQuality = MusicQuality.Auto,
        authState: PlaybackAuthState = innerTube.currentAuthState(),
        preferMp4a: Boolean = false,
        /** Itag yang dipakai terakhir untuk lagu ini (Tahap 6, C2-17); null = ranking biasa. */
        pinnedItag: Int? = null,
        /** Jaringan metered (Tahap 8, C2-25): `Auto` turun ke `High`. */
        networkMetered: Boolean = false,
    ): Result<DirectStream> {
        // C2-12 (ArchiveTune `InvalidPlaybackLoginContextException`): cookie
        // login ada tapi konteks tidak lengkap (tanpa dataSyncId) → tolak,
        // jangan pakai sesi setengah jalan. `player` tidak pernah dipanggil.
        if (authState.hasLoginCookie && !authState.hasPlaybackLoginContext) {
            return Result.failure(StreamFailureException(StreamFailure.InvalidLoginContext))
        }
        // C2-13: tanpa konteks login, pastikan visitorData siap dulu
        // (ArchiveTune `ensureVisitorDataReady`); gagal mengambil = lanjut
        // tanpa visitorData seperti sebelumnya, bukan gagal resolve.
        var effectiveAuth = authState
        if (!authState.hasPlaybackLoginContext && authState.visitorData.isNullOrBlank()) {
            val fetched = runCatching { ensureVisitorData?.invoke() }.getOrNull()
                ?.trim()?.takeIf { it.isNotEmpty() }
            if (fetched != null) effectiveAuth = authState.copy(visitorData = fetched).normalized()
        }
        // Tahap 5 (C1-2): pastikan PoToken video + sesi sebelum resolve —
        // best-effort, gagal = lanjut tanpa token (bukan galat resolve).
        effectiveAuth = poTokenEnsurer?.ensure(videoId, effectiveAuth) ?: effectiveAuth
        val effectiveClients = clients ?: clientsFor(effectiveAuth)
        // Tahap 7 (C2-19): lewati klien yang sedang di-blacklist untuk
        // video + sesi ini (ArchiveTune `buildStreamClientOrder.filterNot
        // isStreamClientTemporarilyBlocked`); bila semuanya terblokir,
        // laporkan AllClientsFailed supaya pemutar bisa menyegarkan sesi.
        val fingerprint = effectiveAuth.streamCacheFingerprint
        val attemptedClients = effectiveClients.filterNot { client ->
            failedStreamClients?.isBlocked(videoId, FailedStreamClients.clientKey(client), fingerprint) == true
        }
        if (attemptedClients.isEmpty() && effectiveClients.isNotEmpty()) {
            return Result.failure(
                StreamFailureException(
                    StreamFailure.AllClientsFailed(
                        failedStreamClients?.blockedClientKeys(videoId, fingerprint).orEmpty(),
                    ),
                ),
            )
        }
        // Tangga kualitas (Tahap 8, C2-23): tier dihitung dari kualitas
        // efektif (Auto + metered = High); tiap tier mencoba semua klien.
        val tiers = qualityLadder(effectiveQuality(quality, networkMetered))
        var lastFailure: StreamFailureException? = null
        for (tier in tiers) {
            val attempt = resolveWithQuality(tier, attemptedClients, videoId, effectiveAuth, preferMp4a, pinnedItag)
            if (attempt.isSuccess) return attempt
            val failure = attempt.exceptionOrNull() as? StreamFailureException
                ?: return attempt // exception tak terduga: teruskan apa adanya
            lastFailure = failure
            // Hanya kegagalan format yang layak coba tier berikut.
            if (failure.failure !is StreamFailure.NoFormat && failure.failure !is StreamFailure.Unplayable) {
                return Result.failure(failure)
            }
        }
        return Result.failure(lastFailure ?: StreamFailureException(StreamFailure.NoFormat))
    }

    /** Satu tier kualitas: semua klien dicoba berurutan; gagal total → kegagalan klien utama. */
    private suspend fun resolveWithQuality(
        quality: MusicQuality,
        attemptedClients: List<MyTubeClient>,
        videoId: String,
        effectiveAuth: PlaybackAuthState,
        preferMp4a: Boolean,
        pinnedItag: Int?,
    ): Result<DirectStream> {
        var firstFailure: StreamFailureException? = null
        for (client in attemptedClients) {
            val attempt = resolveWithClient(client, videoId, quality, effectiveAuth, preferMp4a, pinnedItag)
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
        pinnedItag: Int? = null,
        networkMetered: Boolean = false,
    ): DirectStream? =
        try {
            resolve(videoId, quality, authState, preferMp4a, pinnedItag, networkMetered).getOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    private suspend fun resolveWithClient(
        client: MyTubeClient,
        videoId: String,
        quality: MusicQuality,
        authState: PlaybackAuthState,
        preferMp4a: Boolean,
        pinnedItag: Int? = null,
    ): Result<DirectStream> {
        var last: StreamFailureException? = null
        var attempt = 0
        while (true) {
            try {
                return Result.success(resolveOnce(client, videoId, quality, authState, preferMp4a, pinnedItag))
            } catch (e: CancellationException) {
                throw e
            } catch (e: StreamFailureException) {
                last = e
                // Batas retry tergantung jenis kegagalan: HTTP flaky 1x ulang,
                // LOGIN_REQUIRED 2x ulang (flaky per permintaan, lihat KDoc resolve).
                val maxAttempts = if (e.failure is StreamFailure.LoginRequired) MAX_LOGIN_REQUIRED_ATTEMPTS else MAX_ATTEMPTS_PER_CLIENT
                if (!e.failure.isRetryable || attempt >= maxAttempts - 1) return Result.failure(e)
                attempt++
                // Retry langsung, tanpa jeda — flaky sesaat.
            }
        }
    }

    private suspend fun resolveOnce(
        client: MyTubeClient,
        videoId: String,
        quality: MusicQuality,
        authState: PlaybackAuthState,
        preferMp4a: Boolean,
        pinnedItag: Int? = null,
    ): DirectStream {
        // Hanya `null` dari withTimeoutOrNull yang berarti timeout: exception
        // HTTP/I-O dipetakan dulu ke StreamFailure di dalam blok, supaya
        // 403/429 tidak salah dilaporkan sebagai Timeout.
        // C1-3/C2-5: PoToken player + signatureTimestamp ditempel bila ada
        // (keduanya `null` selama mesin mint BotGuard belum ada — wiring ini
        // yang membuat token mengalir begitu Tahap 5 selesai).
        val poToken = authState.resolvePlayerPoToken(client, videoId = videoId)
        val signatureTimestamp = if (client.useSignatureTimestamp) {
            runCatching { signatureTimestampProvider?.invoke(client, videoId) }.getOrNull()
        } else {
            null
        }
        val raw = withTimeoutOrNull(attemptTimeoutMs) {
            try {
                innerTube.player(
                    client, videoId,
                    signatureTimestamp = signatureTimestamp,
                    poToken = poToken,
                    authState = authState,
                )
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

        // Durasi lagu yang diharapkan (Tahap 8, C2-24): saring preview
        // hanya bila login (ArchiveTune `isLikelyPreview` dipakai saat
        // `canUseLoggedInPlayback`); anonim = tanpa saring.
        val expectedDurationMs = if (authState.hasPlaybackLoginContext) {
            (pick(root, "videoDetails.lengthSeconds") as? JsonPrimitive)
                ?.content?.toLongOrNull()?.takeIf { it > 0 }?.times(1000L)
        } else null
        val picked = pickAudioFormat(parseAudioFormats(root), quality, preferMp4a, pinnedItag, expectedDurationMs)
            ?: throw StreamFailureException(StreamFailure.NoFormat)
        val expires = (pick(root, "streamingData.expiresInSeconds") as? JsonPrimitive)
            ?.content?.toIntOrNull()?.takeIf { it > 0 }
        val loudness = loudnessFromPlayer(root)
        // C1-4: PoToken GVS ditempel ke URL sebagai `pot` (ArchiveTune
        // `applyGvsPoToken`); tanpa token, URL dipakai mentah seperti dulu.
        val rawUrl = picked.url
        val url = authState.resolveGvsPoToken(client, videoId)
            ?.let { appendPoTokenToStreamUrl(rawUrl, it) } ?: rawUrl
        // Tahap 9 (C2-21): URL telemetri putar dari respons `player`
        // (`playbackTracking.videostatsPlaybackUrl.baseUrl` ala AT
        // `remotePlaybackTrackingUrl()`); dipakai mencatat putar, bukan audio.
        val playbackTrackingUrl = pickStr(root, "playbackTracking.videostatsPlaybackUrl.baseUrl")
            .trim().takeIf { it.isNotEmpty() }
        return DirectStream(
            url = url,
            mimeType = picked.mimeType,
            itag = picked.itag,
            bitrate = picked.bitrate,
            expiresInSeconds = expires,
            loudnessDb = loudness?.loudnessDb,
            perceptualLoudnessDb = loudness?.perceptualLoudnessDb,
            contentLength = picked.contentLength
                ?: urlQueryParam(picked.url, "clen")?.toLongOrNull()?.takeIf { it > 0 },
            resolvedClient = client.clientName,
            playbackTrackingUrl = playbackTrackingUrl,
        )
    }

    /** Hanya format audio ber-URL langsung yang dikumpulkan; cipher-only dikumpulkan dengan cipher-nya (tidak dipilih sampai decipher ada). */
    private fun parseAudioFormats(root: JsonElement): List<PlayerAudioFormat> {
        val out = mutableListOf<PlayerAudioFormat>()
        val all = pickArr(root, "streamingData.formats") +
            pickArr(root, "streamingData.adaptiveFormats")
        for (f in all) {
            val mime = pickStr(f, "mimeType")
            val url = pickStr(f, "url")
            val cipher = pickStr(f, "signatureCipher").ifEmpty { pickStr(f, "cipher") }
            if (!mime.startsWith("audio/") || (url.isEmpty() && cipher.isEmpty())) continue
            out.add(
                PlayerAudioFormat(
                    itag = (pick(f, "itag") as? JsonPrimitive)?.content?.toIntOrNull() ?: 0,
                    mimeType = mime,
                    bitrate = (pick(f, "bitrate") as? JsonPrimitive)?.content?.toIntOrNull() ?: 0,
                    url = url,
                    loudnessDb = (pick(f, "loudnessDb") as? JsonPrimitive)?.content?.toDoubleOrNull(),
                    contentLength = (pick(f, "contentLength") as? JsonPrimitive)?.content?.toLongOrNull()?.takeIf { it > 0 },
                    cipher = cipher.takeIf { it.isNotEmpty() },
                    approxDurationMs = (pick(f, "approxDurationMs") as? JsonPrimitive)?.content?.toLongOrNull()?.takeIf { it > 0 },
                    audioSampleRate = (pick(f, "audioSampleRate") as? JsonPrimitive)?.content?.toIntOrNull()?.takeIf { it > 0 },
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

        /** Satu percobaan awal + dua retry langsung untuk `LOGIN_REQUIRED` flaky. */
        const val MAX_LOGIN_REQUIRED_ATTEMPTS = 3

        /** Urutan klien anonim: ANDROID dulu (satu-satunya yang terverifikasi memberi URL anonim); WEB_REMIX/MWEB anonim terverifikasi UNPLAYABLE dari VM, jadi urutannya hanya soal urutan fallback. */
        val DEFAULT_CLIENTS: List<MyTubeClient> =
            listOf(MyTubeClient.ANDROID, MyTubeClient.WEB_REMIX, MyTubeClient.MWEB)

        /** Urutan klien saat login penuh (C2-1, ArchiveTune jalur live): WEB_CREATOR dulu. */
        val LOGIN_CLIENTS: List<MyTubeClient> =
            listOf(MyTubeClient.WEB_CREATOR, MyTubeClient.WEB_REMIX, MyTubeClient.MWEB, MyTubeClient.ANDROID)

        /** Urutan klien menurut konteks login [authState]. */
        fun clientsFor(authState: PlaybackAuthState): List<MyTubeClient> =
            if (authState.hasPlaybackLoginContext) LOGIN_CLIENTS else DEFAULT_CLIENTS

        /**
         * Kunci `NAMA@VERSI` untuk nama klien hasil resolve (Tahap 7):
         * dipakai pemutar saat menandai klien gagal dari `resolvedClient`
         * (tanpa versi) — dicocokkan ke daftar klien yang dikenal.
         * Nama yang tidak dikenal dikembalikan ternormalisasi apa adanya.
         */
        fun clientKeyForName(clientName: String): String {
            val client = (LOGIN_CLIENTS + DEFAULT_CLIENTS)
                .firstOrNull { it.clientName.equals(clientName, ignoreCase = true) }
            return if (client != null) FailedStreamClients.clientKey(client)
            else FailedStreamClients.normalizeClientKey(clientName)
        }
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
