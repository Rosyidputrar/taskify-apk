package com.rosyidputrar.taskify.core.innertube.pages

import com.rosyidputrar.taskify.core.domain.media.CollectionKind
import com.rosyidputrar.taskify.core.domain.media.MusicCatalog
import com.rosyidputrar.taskify.core.domain.media.MusicCollection
import com.rosyidputrar.taskify.core.domain.media.Song
import kotlinx.serialization.json.JsonElement

/**
 * Parser halaman acara podcast — port `parsePodcast`, `parsePodcastMore`,
 * `podcastToken` dari `src/ytmusic/parse.ts` (Worker). Episode diputar
 * sebagai lagu (`Song`, artis = nama acara · tanggal) mengikuti pemetaan
 * episode Worker lama yang sudah ada.
 */

private fun collectPodcastEpisodes(sections: List<JsonElement>, showTitle: String): List<Song> {
    val items = mutableListOf<JsonElement>()
    for (s in sections) items += pickArr(s, "musicShelfRenderer.contents")
    return collectPodcastEpisodeItems(items, showTitle)
}

private fun collectPodcastEpisodeItems(items: List<JsonElement>, showTitle: String): List<Song> {
    val out = mutableListOf<Song>()
    val seen = mutableSetOf<String>()
    for (item in items) {
        val m = pick(item, "musicMultiRowListItemRenderer")
        if (!isObj(m)) continue
        val videoId = pickStr(m, "onTap.watchEndpoint.videoId")
        if (videoId.isEmpty() || videoId in seen) continue
        val title = pickStr(m, "title.runs.0.text")
        if (title.isEmpty()) continue
        seen.add(videoId)
        val sub = runTexts(pickArr(m, "subtitle.runs"))
        val duration = runTexts(
            pickArr(m, "playbackProgress.musicPlaybackProgressRenderer.durationText.runs"),
        ).joinToString(" ")
        val publishedAt = sub.lastOrNull() ?: ""
        out.add(
            Song(
                videoId = videoId,
                title = title,
                artist = listOf(showTitle, publishedAt).filter { it.isNotBlank() }.joinToString(" · "),
                duration = duration,
                thumbnails = thumbs(pickArr(m, "thumbnail.musicThumbnailRenderer.thumbnail.thumbnails")),
                url = watchUrl(videoId),
                durationSeconds = parseEpisodeDurationText(duration),
            ),
        )
    }
    return out
}

/**
 * Halaman satu acara podcast (`browse` `MPSP…`), atau null bila hulu tidak
 * mengenal id itu (browse yang tidak ada dijawab 200 TANPA `contents`).
 */
fun parsePodcast(res: JsonElement, browseId: String): MusicCollection? {
    val sections = pickArr(
        res,
        "contents.twoColumnBrowseResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents",
    )
    var header: JsonElement? = null
    for (s in sections) {
        val h = pick(s, "musicResponsiveHeaderRenderer")
        if (isObj(h)) {
            header = h
            break
        }
    }
    val title = pickStr(header, "title.runs.0.text")
    if (title.isEmpty()) return null
    val description = runsText(pickArr(header, "description.musicDescriptionShelfRenderer.description.runs"))
    val thumbs = thumbs(pickArr(header, "thumbnail.musicThumbnailRenderer.thumbnail.thumbnails"))
    val episodes = collectPodcastEpisodes(
        pickArr(
            res,
            "contents.twoColumnBrowseResultsRenderer.secondaryContents.sectionListRenderer.contents",
        ),
        title,
    )
    return MusicCollection(
        kind = CollectionKind.Podcast,
        id = browseId,
        title = title,
        subtitle = pickStr(header, "straplineTextOne.runs.0.text"),
        thumbnails = thumbs,
        tracks = MusicCatalog.albumTracks(episodes, thumbs),
        cursor = podcastToken(res).takeIf { it.isNotEmpty() },
        description = description,
    )
}

/**
 * Halaman lanjutan acara podcast — bentuknya
 * `continuationContents.musicShelfContinuation.contents`, sama seperti
 * halaman lanjutan pencarian. `showTitle` dibawa dari halaman pertama.
 */
fun parsePodcastMore(res: JsonElement, showTitle: String): CollectionContinuation {
    val episodes = collectPodcastEpisodeItems(
        pickArr(res, "continuationContents.musicShelfContinuation.contents"),
        showTitle,
    )
    return CollectionContinuation(tracks = episodes, token = podcastToken(res))
}

/**
 * Token lanjutan untuk halaman podcast — gaya LAMA (`nextContinuationData`),
 * SAMA seperti search, BUKAN gaya baru `collectionToken`.
 */
fun podcastToken(res: JsonElement): String {
    return pickStr(deepFind(res, "nextContinuationData"), "continuation")
}
