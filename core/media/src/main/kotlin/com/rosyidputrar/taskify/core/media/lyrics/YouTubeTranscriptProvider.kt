/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

// Diadaptasi dari ArchiveTune `lyrics/YouTubeSubtitleLyricsProvider.kt`
// (v15.1.0, c7f0e24) — sumber yang sama (`get_transcript` -> LRC), tapi
// tanpa PoToken di tahap 1 (ArchiveTune memakai
// `YTPlayerUtils.ensureWebPoTokensForSubtitles`; Taskify belum punya
// mesin PoToken, lihat PLAN G.10). Bila YouTube menolak tanpa token,
// hasilnya gagal dan [LyricsRepository] jatuh ke `null`, bukan error.
// Lihat `licenses/ARCHIVETUNE.md`.

package com.rosyidputrar.taskify.core.media.lyrics

import com.rosyidputrar.taskify.core.innertube.YouTube

/**
 * Transkrip sinkron YouTube via `get_transcript` (fasad `:core:innertube`).
 * Mengembalikan teks LRC (`[mm:ss.mmm]teks` per baris) — fallback ketiga
 * setelah LRCLib dan lirik teks YouTube.
 */
class YouTubeTranscriptProvider(
    private val youTube: YouTube = YouTube(),
) : LyricsProvider {

    override val name: String = "YouTube Subtitle"

    override fun isEnabled(): Boolean = true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> = runCatching {
        youTube.transcript(id).getOrThrow()
            .takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Transcript unavailable")
    }
}
