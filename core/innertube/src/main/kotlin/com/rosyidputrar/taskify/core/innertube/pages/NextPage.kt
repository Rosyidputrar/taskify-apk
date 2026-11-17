package com.rosyidputrar.taskify.core.innertube.pages

import com.rosyidputrar.taskify.core.domain.media.Song
import com.rosyidputrar.taskify.core.domain.media.Thumb
import com.rosyidputrar.taskify.core.domain.media.Track
import kotlinx.serialization.json.JsonElement

/**
 * Parser `next` (track + upNext), lirik teks, dan radio — port `parseNext`,
 * `parseLyrics`, `parseRadio`, `parseRadioMore` dari `src/ytmusic/parse.ts`
 * (Worker). Keluarannya model Taskify (`Track`, `Song`).
 */

/** Panel antrean hulu berisi 50 baris, baris pertama lagu yang diminta → sisanya 49. */
const val MAX_UP_NEXT = 49

/** Hasil satu halaman mix radio: lagu-lagunya plus token lanjutan (kosong = habis). */
data class RadioMix(val songs: List<Song>, val token: String)

fun parseNext(res: JsonElement, videoId: String, limit: Int = MAX_UP_NEXT): Track {
    val tabs = pickArr(
        res,
        "contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer." +
            "watchNextTabbedResultsRenderer.tabs",
    )

    var current: JsonElement? = null
    val upNext = mutableListOf<Song>()
    var lyricsId = ""

    for (tab in tabs) {
        val tr = pick(tab, "tabRenderer")
        val epId = pickStr(tr, "endpoint.browseEndpoint.browseId")
        if (epId.startsWith("MPLY")) lyricsId = epId

        val contents = pickArr(
            tr,
            "content.musicQueueRenderer.content.playlistPanelRenderer.contents",
        )
        if (contents.isEmpty()) continue

        current = pick(contents[0], "playlistPanelVideoRenderer")
        val take = minOf(maxOf(1, limit), MAX_UP_NEXT)
        for (qc in contents.subList(1, minOf(1 + take, contents.size))) {
            val qv = pick(qc, "playlistPanelVideoRenderer")
            if (!isObj(qv)) continue
            upNext.add(panelSong(qv))
        }
    }

    val thumbList = thumbs(pickArr(current, "thumbnail.thumbnails"))
    val trackDuration = pickStr(current, "lengthText.runs.0.text")
    val song = Song(
        videoId = videoId,
        title = pickStr(current, "title.runs.0.text"),
        artist = pickStr(current, "longBylineText.runs.0.text"),
        duration = trackDuration,
        thumbnails = thumbList.ifEmpty {
            listOf(
                Thumb(
                    url = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg", width = 480, height = 360,
                ),
            )
        },
        url = watchUrl(videoId),
        durationSeconds = parseDurationText(trackDuration),
    )
    return Track(
        videoId = videoId,
        lyricsId = lyricsId,
        upNext = upNext,
        durationSeconds = song.durationSeconds,
        loudnessDb = null,
        lyrics = null,
        lyricsChecked = false,
        song = song,
    )
}

fun parseLyrics(res: JsonElement): String {
    return pickStr(
        res,
        "contents.sectionListRenderer.contents.0.musicDescriptionShelfRenderer.description.runs.0.text",
    )
}

/** Satu baris panel antrean (`playlistPanelVideoRenderer`) jadi `Song`. */
fun panelSong(qv: JsonElement?): Song {
    val videoId = pickStr(qv, "videoId")
    val duration = pickStr(qv, "lengthText.runs.0.text")
    return Song(
        videoId = videoId,
        title = pickStr(qv, "title.runs.0.text"),
        artist = pickStr(qv, "longBylineText.runs.0.text"),
        duration = duration,
        thumbnails = thumbs(pickArr(qv, "thumbnail.thumbnails")),
        url = watchUrl(videoId),
        durationSeconds = parseDurationText(duration),
    )
}

/**
 * Panel antrean (`playlistPanelRenderer`) halaman `next` pertama, atau
 * null bila hulu tidak memberi panel sama sekali (mis. seed tidak dikenal —
 * itu mix kosong, bukan error).
 */
private fun queuePanel(res: JsonElement): JsonElement? {
    val tabs = pickArr(
        res,
        "contents.singleColumnMusicWatchNextResultsRenderer.tabbedRenderer." +
            "watchNextTabbedResultsRenderer.tabs",
    )
    for (tab in tabs) {
        val panel = pick(tab, "tabRenderer.content.musicQueueRenderer.content.playlistPanelRenderer")
        if (isObj(panel)) return panel
    }
    return null
}

/** Token lanjutan mix radio, gaya `nextRadioContinuationData` (bukan gaya browse/search). */
private fun radioContinuationToken(container: JsonElement?): String {
    return pickStr(container, "continuations.0.nextRadioContinuationData.continuation")
}

/**
 * Mix radio dari halaman `next` pertama. Beda dengan `parseNext`: SEMUA
 * baris panel dikembalikan, termasuk baris pertama (lagu seed) — radio
 * adalah daftar putar, bukan "lagu + antrean".
 */
fun parseRadio(res: JsonElement): RadioMix {
    val panel = queuePanel(res)
    val songs = mutableListOf<Song>()
    for (qc in pickArr(panel, "contents")) {
        val qv = pick(qc, "playlistPanelVideoRenderer")
        if (!isObj(qv)) continue
        val song = panelSong(qv)
        if (song.videoId.isNotEmpty()) songs.add(song)
    }
    return RadioMix(songs, radioContinuationToken(panel))
}

/**
 * Halaman lanjutan mix radio — bentuknya
 * `continuationContents.playlistPanelContinuation`, bukan panel antrean.
 */
fun parseRadioMore(res: JsonElement): RadioMix {
    val cont = pick(res, "continuationContents.playlistPanelContinuation")
    val songs = mutableListOf<Song>()
    for (qc in pickArr(cont, "contents")) {
        val qv = pick(qc, "playlistPanelVideoRenderer")
        if (!isObj(qv)) continue
        val song = panelSong(qv)
        if (song.videoId.isNotEmpty()) songs.add(song)
    }
    return RadioMix(songs, radioContinuationToken(cont))
}
