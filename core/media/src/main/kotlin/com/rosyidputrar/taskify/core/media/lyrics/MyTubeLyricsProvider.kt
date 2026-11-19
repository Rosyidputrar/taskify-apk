/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

// Diadaptasi dari ArchiveTune `lyrics/MyTubeLyricsProvider.kt`
// (v15.1.0, c7f0e24) — alur yang sama (`next` -> lyricsEndpoint ->
// `browse` MPLYt), tapi lewat fasad `:core:innertube` Taskify
// (`MyTube.next()` memberi `Track.lyricsId`, `MyTube.lyrics()` memberi
// teks). Ditambah [getLyricsById] supaya pemanggil yang sudah punya
// `lyricsId` (mis. dari `Track`) tidak perlu memanggil `next` lagi.
// Lihat `licenses/ARCHIVETUNE.md`.

package com.rosyidputrar.taskify.core.media.lyrics

import com.rosyidputrar.taskify.core.innertube.MyTube

/**
 * Lirik teks YouTube Music via `browse` `MPLYt_…` (fasad `:core:innertube`).
 * Teks polos, tanpa sinkronisasi (`synced: null` di [LyricsRepository]).
 */
class MyTubeLyricsProvider(
    private val myTube: MyTube = MyTube(),
) : LyricsProvider {

    override val name: String = "YouTube Music"

    override fun isEnabled(): Boolean = true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> = runCatching {
        val lyricsId = myTube.next(id).getOrThrow().lyricsId
            .takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Lyrics endpoint not found")
        getLyricsById(lyricsId).getOrThrow()
    }

    /** Lirik teks langsung dari `lyricsId` (`MPLYt_…`), tanpa `next`. */
    suspend fun getLyricsById(lyricsId: String): Result<String> = runCatching {
        myTube.lyrics(lyricsId).getOrThrow()
            .takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Lyrics unavailable")
    }
}
