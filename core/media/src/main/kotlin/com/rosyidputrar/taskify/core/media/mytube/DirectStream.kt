package com.rosyidputrar.taskify.core.media.mytube

/**
 * Satu URL audio langsung (`*.googlevideo.com`), hasil resolve endpoint
 * `player` — port `DirectStream` dari `worker/direct.ts`.
 *
 * URL berumur pendek (`expiresInSeconds` ~6 jam dari hulu): jangan di-cache
 * lama dan jangan disimpan lintas sesi. URL mendukung `Range`, jadi seek
 * mungkin.
 */
data class DirectStream(
    /** URL audio langsung (`*.googlevideo.com`), mendukung `Range`. */
    val url: String,
    /** Mis. `audio/webm; codecs="opus"` atau `audio/mp4; codecs="mp4a.40.2"`. */
    val mimeType: String,
    /** Nomor itag format yang dipilih, mis. 251 (opus) atau 140 (mp4a). */
    val itag: Int,
    /** Bitrate format yang dipilih, dalam bit/detik. */
    val bitrate: Int,
    /** Sisa umur URL dalam detik (dari `streamingData.expiresInSeconds`). */
    val expiresInSeconds: Int?,
    /** Kekerasan lagu dalam dB (dari `playerConfig.audioConfig`, jatuh ke per-format). */
    val loudnessDb: Double?,
    /** Kekerasan perseptual dalam dB (hanya dari `playerConfig.audioConfig`). */
    val perceptualLoudnessDb: Double?,
    /** Panjang isi audio dalam byte (`contentLength` format / `clen` URL); `null` = tidak diketahui. Dipakai mengisi `length` DataSpec supaya request media pertama membawa `Range` (porting ArchiveTune Tahap 1). */
    val contentLength: Long? = null,
    /**
     * Nama klien InnerTube yang menghasilkan URL ini (`ANDROID`/`WEB_REMIX`/`MWEB`);
     * `null` = tidak dicatat. Diagnostik 403 (lihat `StreamSource.resolvedClient`).
     */
    val resolvedClient: String? = null,
    /**
     * URL telemetri putar (`playbackTracking.videostatsPlaybackUrl.baseUrl`
     * dari respons `player`); `null` = tidak ada. Dipakai `registerPlayback`
     * (Tahap 9, C2-21) untuk mencatat putar di riwayat YouTube — bukan URL audio.
     */
    val playbackTrackingUrl: String? = null,
)
