/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package com.rosyidputrar.taskify.core.youtubei

import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.Proxy

enum class YoutubeiAudioQuality {
    LOW,
    HIGH,
    HIGHEST,
    AUTO,
}

enum class YoutubeiResolutionPriority {
    FOREGROUND,
    BACKGROUND,
}

enum class YoutubeiFailureKind {
    LOGIN_REQUIRED,
    UNAVAILABLE,
    NO_FORMAT,
    PO_TOKEN,
    NETWORK,
    HTTP,
    DECIPHER,
    TIMEOUT,
    INVALID_RESPONSE,
    INTERNAL,
}

data class YoutubeiStreamRequest(
    val mediaId: String,
    val quality: YoutubeiAudioQuality,
    val networkMetered: Boolean,
    val authFingerprint: String,
    val pinnedItag: Int? = null,
    val requiresSongMetadata: Boolean = false,
    val cookie: String? = null,
    val visitorData: String? = null,
    val dataSyncId: String? = null,
    val sessionPoToken: String? = null,
    val videoPoToken: String? = null,
    val language: String = "en",
    val location: String = "US",
    val timezone: String = "UTC",
)

data class YoutubeiResolvedStream(
    val url: String,
    val requestHeaders: Map<String, String>,
    val formatId: Int,
    val mimeType: String,
    val codecs: String,
    val bitrate: Int,
    val sampleRate: Int?,
    val contentLength: Long,
    val expiresAtMs: Long,
    val runtimeVersion: String,
    val title: String?,
    val durationSeconds: Int?,
    val thumbnailUrl: String?,
    val loudnessDb: Double?,
    val perceptualLoudnessDb: Double?,
    val playbackTrackingUrl: String?,
    /** C S-2: klien InnerTube yang benar-benar dipakai JS (`MWEB`/`WEB_CREATOR`); null bila bundle lama tidak mengembalikannya. */
    val clientName: String? = null,
)

data class YoutubeiNetworkConfiguration(
    val proxy: Proxy?,
    val proxyUsername: String?,
    val proxyPassword: String?,
    val dns: Dns,
    val interceptors: List<Interceptor> = emptyList(),
    /**
     * Klien dasar yang dipakai sebagai basis ([OkHttpClient.newBuilder]):
     * connection pool, DNS, dan socket factory DIBAGI dengan klien ini —
     * jadi request `player` youtubei.js keluar lewat jalur jaringan yang
     * sama dengan InnerTube/media (C M-1, perbaikan 403 ikatan IP).
     * `null` = bangun klien segar (perilaku lama, hanya untuk tes).
     * Produksi mengisi ini dengan `SharedHttpClient.client`.
     */
    val baseClient: OkHttpClient? = null,
)

class YoutubeiException(
    val kind: YoutubeiFailureKind,
    message: String,
    val httpStatus: Int? = null,
    cause: Throwable? = null,
) : IOException(message, cause)
