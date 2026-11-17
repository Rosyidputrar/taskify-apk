package com.rosyidputrar.taskify.core.innertube.pages

import com.rosyidputrar.taskify.core.domain.media.CollectionKind
import com.rosyidputrar.taskify.core.domain.media.MusicCatalog
import com.rosyidputrar.taskify.core.domain.media.MusicCollection
import com.rosyidputrar.taskify.core.domain.media.Song
import kotlinx.serialization.json.JsonElement

/**
 * Parser album/playlist — port `parseCollection`, `parseCollectionMore`,
 * `collectionToken` dari `src/ytmusic/parse.ts` (Worker).
 */

/** Halaman lanjutan koleksi: treknya + token berikut (kosong = habis). */
data class CollectionContinuation(val tracks: List<Song>, val token: String)

private const val HEADER =
    "contents.twoColumnBrowseResultsRenderer.tabs.0.tabRenderer.content." +
        "sectionListRenderer.contents.0.musicResponsiveHeaderRenderer"
private const val SHELF =
    "contents.twoColumnBrowseResultsRenderer.secondaryContents.sectionListRenderer.contents.0."

private fun flex(i: Int) = "flexColumns.$i.musicResponsiveListItemFlexColumnRenderer"
private const val FIXED = "fixedColumns.0.musicResponsiveListItemFixedColumnRenderer"

fun rowToSong(rend: JsonElement?): Song {
    val videoId =
        pickStr(rend, "playlistItemData.videoId")
            .ifEmpty { pickStr(rend, "${flex(0)}.text.runs.0.navigationEndpoint.watchEndpoint.videoId") }
    val duration = pickStr(rend, "$FIXED.text.runs.0.text")
    // Kolom artis = run pertama flexColumns[1] — TAPI bila artis tidak
    // ditulis hulu, run itu bisa berupa durasi: string bentuk durasi
    // JANGAN PERNAH dipakai sebagai artis (pola bug yang sama seperti
    // baris kartu hasil cari Tahap 9). Artis kosong lalu diisi dari header
    // koleksi oleh collectTracks bila ada.
    val rawArtist = pickStr(rend, "${flex(1)}.text.runs.0.text")
    val artist = if (isDurationText(rawArtist)) "" else rawArtist
    return Song(
        videoId = videoId,
        title = pickStr(rend, "${flex(0)}.text.runs.0.text"),
        artist = artist,
        duration = duration,
        thumbnails = thumbs(pickArr(rend, "thumbnail.musicThumbnailRenderer.thumbnail.thumbnails")),
        url = watchUrl(videoId),
        durationSeconds = parseDurationText(duration),
    )
}

/** "378 rb x ditonton • 140 lagu • 184+ jam" → 140. Nol bila tidak disebutkan. */
private fun declaredTrackCount(text: String): Int {
    val m = Regex("""(\d[\d.,]*)\s*(?:lagu|songs?|tracks?)""", RegexOption.IGNORE_CASE).find(text)
        ?: return 0
    val n = m.groupValues[1].replace(Regex("[.,]"), "").toIntOrNull() ?: return 0
    return n
}

fun collectTracks(rows: List<JsonElement>, by: String): List<Song> {
    val tracks = mutableListOf<Song>()
    val seen = mutableSetOf<String>()
    for (row in rows) {
        val rend = pick(row, "musicResponsiveListItemRenderer")
        // Elemen terakhir array trek bisa berupa penanda lanjutan, bukan lagu.
        if (!isObj(rend)) continue
        val song = rowToSong(rend)
        if (song.videoId.isNotEmpty() && song.videoId in seen) continue
        if (song.videoId.isNotEmpty()) seen.add(song.videoId)
        tracks.add(song.copy(artist = song.artist.ifEmpty { by }))
    }
    return tracks
}

/** Dipakai untuk album maupun playlist — bentuk responsnya mirip. */
fun parseCollection(res: JsonElement, id: String, kind: CollectionKind): MusicCollection {
    val header = pick(res, HEADER)

    val title =
        pickStr(header, "title.runs.0.text")
            .ifEmpty { pickStr(res, "microformat.microformatDataRenderer.title") }
    /**
     * Album menaruh nama artis di `straplineTextOne`. Playlist TIDAK punya
     * field itu — jadi `by` playlist memang kosong, bukan gagal di-parse.
     */
    val by = pickStr(header, "straplineTextOne.runs.0.text")

    var year = ""
    for (t in runTexts(pickArr(header, "subtitle.runs"))) {
        if (isYearText(t)) year = t
    }

    val trackThumbs = thumbs(pickArr(header, "thumbnail.musicThumbnailRenderer.thumbnail.thumbnails"))
    val tracks = collectTracks(
        pickArr(res, "${SHELF}musicShelfRenderer.contents") +
            pickArr(res, "${SHELF}musicPlaylistShelfRenderer.contents"),
        by,
    )

    val second = runTexts(pickArr(header, "secondSubtitle.runs")).joinToString("")
    val declared = declaredTrackCount(second)

    return MusicCollection(
        kind = kind,
        id = id,
        title = title,
        subtitle = listOf(by, year).filter { it.isNotBlank() }.joinToString(" · "),
        thumbnails = trackThumbs,
        tracks = MusicCatalog.albumTracks(tracks, trackThumbs),
        cursor = collectionToken(res).takeIf { it.isNotEmpty() },
        // Yang diklaim hulu lebih berguna daripada jumlah halaman ini.
        trackCount = if (declared > 0) declared else tracks.size,
    )
}

/**
 * Halaman lanjutan album/playlist — bentuknya
 * `onResponseReceivedActions[].appendContinuationItemsAction.continuationItems`.
 */
fun parseCollectionMore(res: JsonElement): CollectionContinuation {
    val rows = mutableListOf<JsonElement>()
    for (action in pickArr(res, "onResponseReceivedActions")) {
        rows += pickArr(action, "appendContinuationItemsAction.continuationItems")
    }
    return CollectionContinuation(
        tracks = collectTracks(rows, ""),
        token = collectionToken(res),
    )
}

/**
 * Token lanjutan untuk `browse` — gaya BARU (`continuationCommand.token`).
 * JANGAN memakai `searchToken` di sini: respons playlist memuat KEDUA gaya
 * token, dan yang gaya lama dijawab hulu dengan HTTP 200 berisi 0 trek.
 */
fun collectionToken(res: JsonElement): String {
    return pickStr(deepFind(res, "continuationCommand"), "token")
}
