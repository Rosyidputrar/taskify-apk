/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

// Diadaptasi dari ArchiveTune
// `app/.../playback/PlaybackStreamRecoveryTracker.kt` (c7f0e24) —
// verbatim kecuali paket + satu metode tambahan [reset] (lihat KDoc-nya).
// Lihat licenses/ARCHIVETUNE.md.

package com.rosyidputrar.taskify.core.media.mytube

/**
 * Sekali-per-lagu: pemulihan 403/jaringan/parser/bot hanya boleh satu kali
 * sampai pemutaran pulih (READY/berbunyi) atau lagu berganti (Tahap 7,
 * C2-19 — ArchiveTune `PlaybackStreamRecoveryTracker`).
 */
internal class PlaybackStreamRecoveryTracker {
    private var attemptedMediaId: String? = null

    fun registerRetryAttempt(mediaId: String): Boolean {
        if (attemptedMediaId == mediaId) return false
        attemptedMediaId = mediaId
        return true
    }

    fun onPlaybackRecovered(mediaId: String?) {
        if (mediaId != null && attemptedMediaId == mediaId) {
            attemptedMediaId = null
        }
    }

    fun onMediaItemChanged(currentMediaId: String?) {
        if (attemptedMediaId != currentMediaId) {
            attemptedMediaId = null
        }
    }

    /**
     * Tambahan Taskify (bukan di ArchiveTune): percobaan putar BARU
     * (pengguna menekan putar lagi setelah galat) mendapat jatah
     * pemulihan segar, walau lagunya sama. ArchiveTune mereset lewat
     * READY setelah prepare baru; Taskify mereset di awal percobaan
     * supaya perilaku sekali-per-percobaan yang sudah dites tetap
     * berlaku walau READY belum sempat terpantau.
     */
    fun reset() {
        attemptedMediaId = null
    }
}
