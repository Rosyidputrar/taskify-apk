/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

// Diadaptasi dari ArchiveTune `lyrics/LyricsProvider.kt` (v15.1.0, c7f0e24)
// — kontrak yang sama (`isEnabled`, `getLyrics`, `getAllLyrics`), tapi
// `isEnabled` tanpa `Context` (Taskify belum punya DataStore pengaturan
// lirik; semua provider bawaan selalu aktif) dan paket Taskify.
// Lihat `licenses/ARCHIVETUNE.md`.

package com.rosyidputrar.taskify.core.media.lyrics

/**
 * Satu sumber lirik. [getLyrics] mengembalikan teks lirik terbaik sumber
 * itu — LRC sinkron bila ada, teks polos bila tidak — atau gagal
 * (`Result.failure`) bila sumber itu tidak punya apa-apa. [id] adalah
 * videoId YouTube; [duration] detik lagu (`-1` = tidak diketahui).
 */
interface LyricsProvider {
    val name: String

    fun isEnabled(): Boolean

    suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String>

    suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        getLyrics(id, title, artist, album, duration).onSuccess(callback)
    }
}
