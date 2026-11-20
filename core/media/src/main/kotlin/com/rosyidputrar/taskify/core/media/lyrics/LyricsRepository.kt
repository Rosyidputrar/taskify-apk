package com.rosyidputrar.taskify.core.media.lyrics

import com.rosyidputrar.taskify.core.domain.media.Lyrics
import com.rosyidputrar.taskify.core.domain.media.Song
import com.rosyidputrar.taskify.core.innertube.MyTube
import kotlinx.coroutines.CancellationException

/**
 * Gabungan sumber lirik — pengganti lirik Worker lama (`syncedLyrics` /
 * `lyrics`) untuk Tahap 6-7. Urutan (PLAN G.7,
 * perilaku Worker `resolveSynced` di `worker/index.ts`):
 *
 * 1. LRCLib — LRC sinkron bila ada (`synced` terisi), teks polos bila
 *    hanya itu yang ada (`synced: null`), instrumental bila LRCLib
 *    memastikan (`synced: []` — jangan tampilkan panel lirik kosong).
 * 2. Lirik teks YouTube (`browse` MPLYt) — `synced: null`.
 * 3. Transkrip YouTube (`get_transcript`) — LRC hasil uraian cue.
 *
 * Aturan Worker yang dipertahankan: kegagalan LRCLib TIDAK PERNAH jadi
 * error selama teks tersedia; semua sumber gagal -> `null`, bukan throw
 * (pemanggil menampilkan "lirik tidak tersedia", bukan error). Hanya
 * pembatalan coroutine yang diteruskan.
 */
class LyricsRepository(
    private val lrcLib: LrcLibLyricsProvider = LrcLibLyricsProvider(),
    private val myTube: MyTube = MyTube(),
    private val myTubeLyrics: MyTubeLyricsProvider = MyTubeLyricsProvider(myTube),
    private val transcript: MyTubeTranscriptProvider = MyTubeTranscriptProvider(myTube),
) {

    /**
     * Lirik untuk satu lagu. [lyricsId] (`MPLYt_…`) bila sudah diketahui
     * (dari `Track`); bila null, lirik teks YouTube dicari lewat `next`.
     * [durationSeconds] null = tidak diketahui (LRCLib ditanya tanpa
     * durasi, toleransi tidak berlaku).
     */
    suspend fun getLyrics(
        videoId: String,
        title: String,
        artist: String,
        album: String? = null,
        durationSeconds: Int? = null,
        lyricsId: String? = null,
    ): Lyrics? {
        val duration = durationSeconds ?: -1
        // 1. LRCLib dulu — lebih murah dan memberi sinkron.
        runCatching { lrcLib.fetchLyrics(title, artist, album, duration) }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull()?.let { found ->
                return Lyrics(found.synced, found.plain, SOURCE_LRCLIB)
            }
        // 2. Lirik teks YouTube.
        val textResult = if (!lyricsId.isNullOrBlank()) {
            myTubeLyrics.getLyricsById(lyricsId)
        } else {
            myTubeLyrics.getLyrics(videoId, title, artist, album, duration)
        }
        textResult.getOrNull()?.takeIf { it.isNotBlank() }?.let { text ->
            return Lyrics(null, text, SOURCE_MYTUBE)
        }
        // 3. Transkrip YouTube -> LRC.
        transcript.getLyrics(videoId, title, artist, album, duration)
            .getOrNull()?.takeIf { it.isNotBlank() }?.let { lrc ->
                val lines = LrcParser.parseLrc(lrc)
                if (lines.isNotEmpty()) {
                    return Lyrics(lines, lines.joinToString("\n") { it.text }, SOURCE_MYTUBE)
                }
            }
        return null
    }

    /** Jalan pintas dari [Song] (album tidak ada di model `Song`). */
    suspend fun getLyrics(song: Song, lyricsId: String? = null): Lyrics? =
        getLyrics(
            videoId = song.videoId,
            title = song.title,
            artist = song.artist,
            album = null,
            durationSeconds = song.seconds,
            lyricsId = lyricsId,
        )

    companion object {
        /** Nilai `Lyrics.source` — sama dengan Worker (`'lrclib' | 'youtube'`). */
        const val SOURCE_LRCLIB = "lrclib"
        const val SOURCE_MYTUBE = "youtube"
    }
}
