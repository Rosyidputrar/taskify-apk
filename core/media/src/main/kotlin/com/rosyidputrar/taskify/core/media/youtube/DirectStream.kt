package com.rosyidputrar.taskify.core.media.youtube

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
)
