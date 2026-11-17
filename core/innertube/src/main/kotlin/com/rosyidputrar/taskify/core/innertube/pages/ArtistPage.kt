package com.rosyidputrar.taskify.core.innertube.pages

import com.rosyidputrar.taskify.core.domain.media.AlbumRef
import com.rosyidputrar.taskify.core.domain.media.ArtistPage
import com.rosyidputrar.taskify.core.domain.media.ArtistRef
import com.rosyidputrar.taskify.core.domain.media.Song
import kotlinx.serialization.json.JsonElement

/**
 * Parser halaman artis — port `parseArtist` dari `src/ytmusic/parse.ts`
 * (Worker, ~400 baris, parser terbesar). Rak yang bentuknya tidak dikenali
 * dilewati diam-diam.
 */
fun parseArtist(res: JsonElement, browseId: String): ArtistPage? {
    val header = pick(res, "header.musicImmersiveHeaderRenderer")
    val name =
        pickStr(header, "title.runs.0.text")
            .ifEmpty { pickStr(res, "microformat.microformatDataRenderer.title") }
    if (name.isEmpty()) return null

    val sections = artistSectionList(res)
    val topSongs = mutableListOf<Song>()
    var topSongsPlaylistId: String? = null
    val albums = mutableListOf<AlbumRef>()
    val singles = mutableListOf<AlbumRef>()
    val relatedArtists = mutableListOf<ArtistRef>()
    var shelfDescription = ""

    for (section in sections) {
        val shelf = pick(section, "musicShelfRenderer")
        if (isObj(shelf)) {
            // Rak lagu teratas — satu-satunya `musicShelfRenderer` yang
            // berisi baris lagu; rak pertama yang punya baris yang dipakai.
            if (topSongs.isEmpty()) {
                val found = collectTracks(pickArr(shelf, "contents"), name)
                if (found.isNotEmpty()) {
                    topSongs += found
                    val playlistBrowseId =
                        pickStr(shelf, "bottomEndpoint.browseEndpoint.browseId")
                            .ifEmpty { pickStr(shelf, "title.runs.0.navigationEndpoint.browseEndpoint.browseId") }
                    topSongsPlaylistId = playlistBrowseId.takeIf { it.startsWith("VL") }
                }
            }
            continue
        }

        val carousel = pick(section, "musicCarouselShelfRenderer")
        if (isObj(carousel)) {
            val title = artistShelfTitle(section)
            val lower = title.lowercase()
            val items = pickArr(carousel, "contents")
            if (Regex("""(single|\bep\b)""").containsMatchIn(lower)) {
                for (item in items) {
                    artistAlbumFromTwoRow(item, name)?.let { singles.add(it) }
                }
            } else {
                // Rak album vs rak artis terkait dibedakan dari isi kartunya,
                // bukan dari judulnya (judulnya ikut bahasa `hl`).
                for (item in items) {
                    val album = artistAlbumFromTwoRow(item, name)
                    if (album != null) {
                        albums.add(album)
                        continue
                    }
                    artistRelatedFromTwoRow(item)?.let { relatedArtists.add(it) }
                }
            }
            continue
        }

        // Rak deskripsi "Tentang" — hanya cadangan bila header tak punya deskripsi.
        val descShelf = pick(section, "musicDescriptionShelfRenderer")
        if (isObj(descShelf) && shelfDescription.isEmpty()) {
            shelfDescription = runTexts(pickArr(descShelf, "description.runs")).joinToString("")
        }
    }

    val microThumbs = thumbs(
        pickArr(res, "microformat.microformatDataRenderer.thumbnail.thumbnails"),
    )
    val headerThumbs = thumbs(
        pickArr(header, "thumbnail.musicThumbnailRenderer.thumbnail.thumbnails"),
    )
    val description = runTexts(pickArr(header, "description.runs")).joinToString("").ifEmpty { shelfDescription }

    return ArtistPage(
        browseId = browseId,
        name = name,
        subscribers = pickStr(
            header,
            "subscriptionButton.subscribeButtonRenderer.subscriberCountText.runs.0.text",
        ),
        monthlyListeners = pickStr(header, "monthlyListenerCount.runs.0.text"),
        description = description,
        thumbnails = microThumbs.ifEmpty { headerThumbs },
        topSongs = topSongs,
        topSongsPlaylistId = topSongsPlaylistId,
        albums = albums,
        singles = singles,
        related = relatedArtists,
    )
}

/** Daftar seksi halaman artis: tab pertama yang punya `sectionListRenderer`. */
private fun artistSectionList(res: JsonElement): List<JsonElement> {
    val singleTabs = pickArr(res, "contents.singleColumnBrowseResultsRenderer.tabs")
    for (tab in singleTabs) {
        val contents = pickArr(tab, "tabRenderer.content.sectionListRenderer.contents")
        if (contents.isNotEmpty()) return contents
    }
    // Bentuk cadangan: kolom ganda dengan tab yang sama strukturnya.
    return pickArr(
        res,
        "contents.twoColumnBrowseResultsRenderer.tabs.0.tabRenderer.content." +
            "sectionListRenderer.contents",
    )
}

/** Kartu dua baris rak album/single → `AlbumRef`, artisnya nama artis halaman ini. */
private fun artistAlbumFromTwoRow(item: JsonElement, artistName: String): AlbumRef? {
    val rend = pick(item, "musicTwoRowItemRenderer")
    if (!isObj(rend)) return null
    val browseId = pickStr(rend, "navigationEndpoint.browseEndpoint.browseId")
    if (!browseId.startsWith("MPREb_")) return null
    val title = pickStr(rend, "title.runs.0.text")
    if (title.isEmpty()) return null
    val sub = runTexts(pickArr(rend, "subtitle.runs"))
    // Subtitle album hanya tahun ("2022"); single "Single • 2022" — tahunnya
    // selalu run TERAKHIR yang berbentuk tahun.
    val year = sub.reversed().firstOrNull { isYearText(it) } ?: ""
    return AlbumRef(
        browseId = browseId,
        title = title,
        artist = artistName,
        year = year,
        thumbnails = twoRowThumbs(rend),
    )
}

/** Kartu dua baris rak artis terkait → `ArtistRef` (subtitle = audiens bulanan). */
private fun artistRelatedFromTwoRow(item: JsonElement): ArtistRef? {
    val rend = pick(item, "musicTwoRowItemRenderer")
    if (!isObj(rend)) return null
    val browseId = pickStr(rend, "navigationEndpoint.browseEndpoint.browseId")
    if (!browseId.startsWith("UC")) return null
    val name = pickStr(rend, "title.runs.0.text")
    if (name.isEmpty()) return null
    return ArtistRef(
        browseId = browseId,
        name = name,
        subscribers = runTexts(pickArr(rend, "subtitle.runs")).joinToString(" "),
        thumbnails = twoRowThumbs(rend),
    )
}

/** Judul rak carousel ("Album", "Single & EP", …) atau rak lagu ("Lagu teratas"). */
private fun artistShelfTitle(section: JsonElement): String =
    pickStr(
        section,
        "musicCarouselShelfRenderer.header.musicCarouselShelfBasicHeaderRenderer.title.runs.0.text",
    ).ifEmpty { pickStr(section, "musicShelfRenderer.title.runs.0.text") }
