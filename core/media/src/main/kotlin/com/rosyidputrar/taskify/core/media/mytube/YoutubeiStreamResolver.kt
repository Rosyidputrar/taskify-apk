/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Fasad pemutaran youtubei.js (porting ArchiveTune Tahap 3): jembatan antara
 * [com.rosyidputrar.taskify.core.youtubei.YoutubeiResolver] (QuickJS +
 * youtubei.js 18.0.0, adaptasi morideobfuscator ArchiveTune, GPL-3.0) dan
 * jalur direct Taskify ([DirectStream]/[StreamFailure]).
 *
 * Perilaku: `resolve()` mencoba youtubei dulu; gagal apa pun → pemanggil
 * ([MusicPlayer]) jatuh ke [DirectStreamResolver] (jalur Kotlin yang sudah
 * ada), jadi kegagalan JS tidak pernah mematikan pemutaran selama jalur
 * direct masih bisa. PoToken dipastikan lewat [PoTokenEnsurer] (Tahap 5)
 * sebelum resolve; bila ensurer null, JS memakai token sesi yang ada di
 * [PlaybackAuthState] atau tanpa token.
 *
 * Decipher (Tahap 4, C1-7/C2-5): `n` + `signatureCipher` + `signatureTimestamp`
 * TIDAK diimplementasikan ulang di Kotlin — semuanya terjadi di dalam
 * bundle youtubei.js (`index.js` + `stream-url.js`) saat `resolve`, sama
 * seperti di ArchiveTune (decipher hidup di bundle, bukan di kode AT).
 * Jalur direct Kotlin tetap melewati format cipher-only (tidak dipilih,
 * tidak menggagalkan resolve); format itu hanya bisa diputar lewat jalur
 * JS ini. `signatureTimestamp` jalur direct ditangani
 * [SignatureTimestampProvider] (Tahap 2).
 */
package com.rosyidputrar.taskify.core.media.mytube

import android.content.Context
import android.util.Log
import com.rosyidputrar.taskify.core.domain.media.MusicQuality
import com.rosyidputrar.taskify.core.innertube.PlaybackAuthState
import com.rosyidputrar.taskify.core.media.ResolvedStreamRegistry
import com.rosyidputrar.taskify.core.youtubei.YoutubeiAudioQuality
import com.rosyidputrar.taskify.core.youtubei.YoutubeiException
import com.rosyidputrar.taskify.core.youtubei.YoutubeiFailureKind
import com.rosyidputrar.taskify.core.youtubei.YoutubeiNetworkConfiguration
import com.rosyidputrar.taskify.core.youtubei.YoutubeiResolvedStream
import com.rosyidputrar.taskify.core.youtubei.YoutubeiResolver
import com.rosyidputrar.taskify.core.youtubei.YoutubeiResolutionPriority
import com.rosyidputrar.taskify.core.youtubei.YoutubeiStreamRequest
import kotlinx.coroutines.CancellationException
import okhttp3.Dns
import java.util.TimeZone

/**
 * Resolve stream lewat youtubei.js (QuickJS). Dibuat malas: [YoutubeiResolver]
 * (dan QuickJS di dalamnya) baru dibuat saat pertama dipakai, bukan saat
 * aplikasi start — pemutaran direct tetap jalan tanpa QuickJS.
 *
 * Kelas + [resolve] dibuka (`open`) supaya tes pemutar bisa memalsukannya
 * tanpa QuickJS/native (lihat `MusicPlayerTest.FakeYoutubei`).
 *
 * Catatan: `preferMp4a` diabaikan jalur ini — youtubei.js memilih format
 * dari tier kualitas, bukan dari preferensi codec. `networkMetered`
 * diteruskan ke bundle (Tahap 8): `AUTO` di jaringan metered turun ke
 * `HIGH` di dalam bundle (`preferredFormat`).
 */
open class YoutubeiStreamResolver(
    context: Context,
    private val videoPoTokenProvider: (suspend (String) -> String?)? = null,
    /** Ensure PoToken sebelum resolve (Tahap 5); null = tanpa token (perilaku Tahap 3/4). */
    private val poTokenEnsurer: PoTokenEnsurer? = null,
) {
    private val appContext = context.applicationContext

    private val resolver by lazy {
        YoutubeiResolver(
            context = appContext,
            diagnostics = { Log.d(TAG, it) },
            // Jaringan youtubei.js memakai klien OkHttp sendiri (seperti
            // ArchiveTune); tanpa proxy khusus dan DNS sistem — JANGAN paksa
            // IPv4 di sini (hipotesis IP sudah dilemahkan penyelidikan).
            networkConfigurationProvider = {
                YoutubeiNetworkConfiguration(
                    proxy = null,
                    proxyUsername = null,
                    proxyPassword = null,
                    dns = Dns.SYSTEM,
                )
            },
        )
    }

    /** Muat bundle + polyfill ke QuickJS lebih awal (mis. saat aplikasi start). */
    suspend fun preWarm() = resolver.preWarm()

    /** Buang sesi JS (dipakai setelah login berubah; lihat Tahap 5). */
    suspend fun invalidateSessions() = resolver.invalidateSessions()

    /**
     * Resolve satu video lewat youtubei.js. Hasil sukses didaftarkan ke
     * [ResolvedStreamRegistry] (contentLength + header media dari resolver,
     * supaya request media pertama membawa `Range` + header yang benar).
     */
    open suspend fun resolve(
        videoId: String,
        quality: MusicQuality,
        authState: PlaybackAuthState,
        @Suppress("UNUSED_PARAMETER") preferMp4a: Boolean,
        /** Itag yang dipakai terakhir untuk lagu ini (Tahap 6, C2-17) → `pinnedItag` youtubei.js. */
        pinnedItag: Int? = null,
        /** Jaringan metered (Tahap 8, C2-25): `AUTO` turun ke `HIGH` di bundle. */
        networkMetered: Boolean = false,
    ): Result<DirectStream> {
        // Gate konteks login yang sama dengan jalur direct (C2-12): cookie
        // login tanpa dataSyncId ditolak sebelum memanggil resolver JS.
        if (authState.hasLoginCookie && !authState.hasPlaybackLoginContext) {
            return Result.failure(StreamFailureException(StreamFailure.InvalidLoginContext))
        }
        // Tahap 5 (C1-2): pastikan PoToken video + sesi sebelum resolve —
        // best-effort, gagal = lanjut tanpa token (bukan galat resolve).
        val effectiveAuth = poTokenEnsurer?.ensure(videoId, authState) ?: authState
        return try {
            val resolved = resolver.resolve(
                request = YoutubeiStreamRequest(
                    mediaId = videoId,
                    quality = quality.toYoutubeiQuality(),
                    networkMetered = networkMetered,
                    authFingerprint = effectiveAuth.streamCacheFingerprint,
                    pinnedItag = pinnedItag,
                    requiresSongMetadata = false,
                    cookie = effectiveAuth.cookie,
                    visitorData = effectiveAuth.visitorData,
                    dataSyncId = effectiveAuth.dataSyncId.takeIf { effectiveAuth.hasLoginCookie },
                    sessionPoToken = effectiveAuth.poTokenGvsSession,
                    videoPoToken = effectiveAuth.poTokenGvs.takeIf {
                        effectiveAuth.poTokenGvsVideoId == videoId
                    },
                    language = "id",
                    location = "ID",
                    timezone = TimeZone.getDefault().id,
                ),
                priority = YoutubeiResolutionPriority.FOREGROUND,
                videoPoTokenProvider = videoPoTokenProvider ?: { replacementId ->
                    // Tahap 5: JS minta token video pengganti (mis. saat
                    // resolvePrepared pindah video) — mint lewat ensurer.
                    poTokenEnsurer?.ensure(replacementId, authState)?.let { ensured ->
                        ensured.poTokenGvs.takeIf { ensured.poTokenGvsVideoId == replacementId }
                    }
                },
            )
            val direct = resolved.toDirectStream(
                clientName = if (effectiveAuth.hasLoginCookie) "WEB_CREATOR" else "MWEB",
            )
            direct.contentLength?.let { length ->
                ResolvedStreamRegistry.put(direct.url, length, resolved.requestHeaders)
            }
            Log.i(
                TAG,
                "resolve $videoId OK klien=${direct.resolvedClient} itag=${direct.itag} " +
                    "runtime=${resolved.runtimeVersion}",
            )
            Result.success(direct)
        } catch (e: CancellationException) {
            throw e
        } catch (e: YoutubeiException) {
            Result.failure(StreamFailureException(e.toStreamFailure()))
        } catch (e: Exception) {
            Result.failure(
                StreamFailureException(
                    StreamFailure.Network(e.message ?: "youtubei gagal"),
                ),
            )
        }
    }

    private companion object {
        const val TAG = "YoutubeiStreamResolver"
    }
}

internal fun MusicQuality.toYoutubeiQuality(): YoutubeiAudioQuality =
    when (this) {
        MusicQuality.Auto -> YoutubeiAudioQuality.AUTO
        MusicQuality.High -> YoutubeiAudioQuality.HIGH
        MusicQuality.Low -> YoutubeiAudioQuality.LOW
        MusicQuality.Highest -> YoutubeiAudioQuality.HIGHEST
    }

/**
 * Pemetaan murni [YoutubeiResolvedStream] → [DirectStream] (bisa diuji tanpa
 * QuickJS/jaringan). `expiresInSeconds` dihitung dari `expiresAtMs`
 * terhadap `nowMs` (bukan jam dinding resolver).
 */
internal fun YoutubeiResolvedStream.toDirectStream(
    clientName: String,
    nowMs: Long = System.currentTimeMillis(),
): DirectStream {
    val expiresInSeconds = ((expiresAtMs - nowMs) / 1_000L).toInt().takeIf { it > 0 }
    return DirectStream(
        url = url,
        mimeType = mimeType,
        itag = formatId,
        bitrate = bitrate,
        expiresInSeconds = expiresInSeconds,
        loudnessDb = loudnessDb,
        perceptualLoudnessDb = perceptualLoudnessDb,
        contentLength = contentLength.takeIf { it > 0 },
        resolvedClient = clientName,
        playbackTrackingUrl = playbackTrackingUrl,
    )
}

internal fun YoutubeiException.toStreamFailure(): StreamFailure =
    when (kind) {
        YoutubeiFailureKind.PO_TOKEN -> StreamFailure.BotDetection(message ?: "")
        YoutubeiFailureKind.LOGIN_REQUIRED -> StreamFailure.LoginRequired(message ?: "")
        YoutubeiFailureKind.NO_FORMAT -> StreamFailure.NoFormat
        YoutubeiFailureKind.UNAVAILABLE -> StreamFailure.Unplayable(message ?: "")
        YoutubeiFailureKind.DECIPHER -> StreamFailure.NoFormat
        YoutubeiFailureKind.HTTP -> httpStatus?.let { StreamFailure.Http(it) }
            ?: StreamFailure.Network(message ?: "HTTP youtubei gagal")
        YoutubeiFailureKind.TIMEOUT -> StreamFailure.Timeout
        YoutubeiFailureKind.NETWORK -> StreamFailure.Network(message ?: "")
        YoutubeiFailureKind.INVALID_RESPONSE -> StreamFailure.InvalidResponse
        YoutubeiFailureKind.INTERNAL -> StreamFailure.Network(message ?: "")
    }
