package com.rosyidputrar.taskify.core.innertube.pages

import com.rosyidputrar.taskify.core.domain.media.AlbumRef
import com.rosyidputrar.taskify.core.domain.media.ArtistRef
import com.rosyidputrar.taskify.core.domain.media.DiscoverySection
import com.rosyidputrar.taskify.core.domain.media.MoodCategory
import com.rosyidputrar.taskify.core.domain.media.MoodGroup
import com.rosyidputrar.taskify.core.domain.media.PlaylistRef
import com.rosyidputrar.taskify.core.domain.media.Song
import com.rosyidputrar.taskify.core.domain.media.Thumb
import kotlinx.serialization.json.JsonElement

/**
 * Parser halaman discovery (home / charts / rilisan baru / mood) — port
 * `parseHome`, `parseCharts`, `parseNewReleases`, `parseMoodCategories`,
 * `findMoodCategory`, `parseDiscoverySections` dari `src/ytmusic/parse.ts`
 * (Worker). Rak yang bentuknya tidak dikenali dilewati diam-diam.
 */

/** Batas pengaman: belum pernah terlihat lebih dari 8 seksi di satu halaman. */
private const val MAX_DISCOVERY_SECTIONS = 10

private fun discoverySectionList(res: JsonElement): List<JsonElement> {
    val single = pickArr(
        res,
        "contents.singleColumnBrowseResultsRenderer.tabs.0.tabRenderer.content." +
            "sectionListRenderer.contents",
    )
    if (single.isNotEmpty()) return single
    return pickArr(
        res,
        "contents.twoColumnBrowseResultsRenderer.tabs.0.tabRenderer.content." +
            "sectionListRenderer.contents",
    )
}

private fun discoveryShelfTitle(section: JsonElement): String =
    pickStr(
        section,
        "musicCarouselShelfRenderer.header.musicCarouselShelfBasicHeaderRenderer.title.runs.0.text",
    ).ifEmpty {
        pickStr(section, "musicShelfRenderer.header.musicShelfBasicHeaderRenderer.title.runs.0.text")
    }.ifEmpty {
        pickStr(section, "gridRenderer.header.gridHeaderRenderer.title.runs.0.text")
    }

internal fun twoRowThumbs(rend: JsonElement?): List<Thumb> =
    thumbs(pickArr(rend, "thumbnailRenderer.musicThumbnailRenderer.thumbnail.thumbnails"))

private fun playlistRefFrom(
    browseId: String,
    title: String,
    author: String,
    thumbnails: List<Thumb>,
): PlaylistRef {
    val playlistId = if (browseId.startsWith("VL")) browseId.removePrefix("VL") else browseId
    return PlaylistRef(
        browseId = browseId,
        playlistId = playlistId,
        title = title,
        author = author,
        thumbnails = thumbnails,
    )
}

private sealed interface ClassifiedItem {
    val kind: String
    data class Songs(val item: Song) : ClassifiedItem { override val kind = "songs" }
    data class Albums(val item: AlbumRef) : ClassifiedItem { override val kind = "albums" }
    data class Artists(val item: ArtistRef) : ClassifiedItem { override val kind = "artists" }
    data class Playlists(val item: PlaylistRef) : ClassifiedItem { override val kind = "playlists" }
}

/**
 * Kartu dua baris: album (nav `MPREb_`), playlist (nav `VL…`/`PL…`/
 * `OLAK5uy_`), atau video/lagu (nav `watchEndpoint`).
 */
private fun classifyTwoRow(item: JsonElement): ClassifiedItem? {
    val rend = pick(item, "musicTwoRowItemRenderer")
    if (!isObj(rend)) return null
    val title = pickStr(rend, "title.runs.0.text")
    if (title.isEmpty()) return null
    val sub = runTexts(pickArr(rend, "subtitle.runs"))
    val thumbnails = twoRowThumbs(rend)
    val nav = pick(rend, "navigationEndpoint")
    val browseId = pickStr(nav, "browseEndpoint.browseId")
    val videoId = pickStr(nav, "watchEndpoint.videoId")

    if (browseId.startsWith("MPREb_")) {
        // Subtitle album/single bentuknya [jenis, artis] — TAPI bila artis
        // tidak ditulis hulu, slot itu diisi tahun (['Single', '2014']) atau
        // durasi: string bentuk durasi/tahun JANGAN PERNAH dipakai sebagai
        // artis. Tidak ada sumber artis lain di kartu ini, jadi artis
        // kosong lebih benar daripada tahun/durasi.
        val rawArtist = sub.getOrNull(1) ?: ""
        val artist = if (isDurationText(rawArtist) || isYearText(rawArtist)) "" else rawArtist
        return ClassifiedItem.Albums(
            AlbumRef(
                browseId = browseId,
                title = title,
                artist = artist,
                year = "",
                thumbnails = thumbnails,
            ),
        )
    }
    if (
        browseId.startsWith("VL") || browseId.startsWith("PL") ||
        browseId.startsWith("OLAK5uy_") || browseId.startsWith("MPSP")
    ) {
        // Author playlist = run terakhir subtitle — TAPI string bentuk
        // durasi JANGAN PERNAH dipakai sebagai author.
        val rawAuthor = if (sub.size > 1) sub.last() else ""
        val author = if (isDurationText(rawAuthor)) "" else rawAuthor
        return ClassifiedItem.Playlists(
            playlistRefFrom(
                browseId,
                title,
                author,
                thumbnails,
            ),
        )
    }
    /**
     * Kartu video yang nav-nya halaman detail, bukan watch langsung: `MPED`
     * + videoId 11 karakter. Subtitle-nya terbalik dibanding kartu video
     * biasa — `[durasi, artis]` — jadi artisnya run terakhir.
     */
    val mpedVideoId = Regex("""^MPED([\w-]{11})$""").find(browseId)?.groupValues?.getOrNull(1)
    if (!mpedVideoId.isNullOrEmpty()) {
        // Subtitle [durasi, artis] — artisnya run terakhir, TAPI bila
        // urutannya terbalik / artis tidak ada, run terakhir bisa berupa
        // durasi: string bentuk durasi JANGAN PERNAH dipakai sebagai artis.
        val rawArtist = if (sub.size > 1) sub.last() else ""
        val artist = if (isDurationText(rawArtist)) "" else rawArtist
        return ClassifiedItem.Songs(
            Song(
                videoId = mpedVideoId,
                title = title,
                artist = artist,
                duration = "",
                thumbnails = thumbnails,
                url = watchUrl(mpedVideoId),
                durationSeconds = null,
            ),
        )
    }
    if (videoId.isNotEmpty()) {
        // Subtitle kartu video bentuknya [artis, jumlah tayang] — TAPI bila
        // artis tidak ditulis hulu, run pertama bisa berupa durasi: string
        // bentuk durasi JANGAN PERNAH dipakai sebagai artis (pola bug yang
        // sama seperti baris kartu hasil cari Tahap 9).
        val rawArtist = sub.getOrNull(0) ?: ""
        val artist = if (isDurationText(rawArtist)) "" else rawArtist
        return ClassifiedItem.Songs(
            Song(
                videoId = videoId,
                title = title,
                artist = artist,
                duration = "",
                thumbnails = thumbnails,
                url = watchUrl(videoId),
                durationSeconds = null,
            ),
        )
    }
    return null
}

/**
 * Baris daftar di rak discovery — bentuknya sama dengan baris trek
 * album/playlist, tapi jenisnya ditentukan dari nav-nya.
 */
private fun classifyListRow(item: JsonElement): ClassifiedItem? {
    val rend = pick(item, "musicResponsiveListItemRenderer")
    if (!isObj(rend)) return null
    val title = pickStr(rend, "flexColumns.0.musicResponsiveListItemFlexColumnRenderer.text.runs.0.text")
    if (title.isEmpty()) return null
    val nav = pick(rend, "flexColumns.0.musicResponsiveListItemFlexColumnRenderer.text.runs.0.navigationEndpoint")
    val videoId =
        pickStr(nav, "watchEndpoint.videoId")
            .ifEmpty { pickStr(rend, "playlistItemData.videoId") }
    val browseId =
        pickStr(nav, "browseEndpoint.browseId")
            .ifEmpty { pickStr(rend, "navigationEndpoint.browseEndpoint.browseId") }
    val sub = runTexts(pickArr(rend, "flexColumns.1.musicResponsiveListItemFlexColumnRenderer.text.runs"))
    val thumbnails = thumbs(pickArr(rend, "thumbnail.musicThumbnailRenderer.thumbnail.thumbnails"))

    if (videoId.isNotEmpty()) return ClassifiedItem.Songs(rowToSong(rend))
    if (browseId.startsWith("MPREb_")) {
        // Subtitle baris album bentuknya [artis, tahun] — TAPI bila artis
        // tidak ditulis hulu, run pertama bisa berupa tahun (atau durasi):
        // string bentuk durasi/tahun JANGAN PERNAH dipakai sebagai artis.
        // Tahunnya tetap dibaca terpisah di bawah.
        val rawArtist = sub.getOrNull(0) ?: ""
        val artist = if (isDurationText(rawArtist) || isYearText(rawArtist)) "" else rawArtist
        return ClassifiedItem.Albums(
            AlbumRef(
                browseId = browseId,
                title = title,
                artist = artist,
                year = sub.firstOrNull { isYearText(it) } ?: "",
                thumbnails = thumbnails,
            ),
        )
    }
    if (browseId.startsWith("UC")) {
        return ClassifiedItem.Artists(
            ArtistRef(
                browseId = browseId,
                name = title,
                subscribers = sub.joinToString(" "),
                thumbnails = thumbnails,
            ),
        )
    }
    if (
        browseId.startsWith("VL") ||
        browseId.startsWith("PL") ||
        browseId.startsWith("OLAK5uy_") ||
        browseId.startsWith("MPSP")
    ) {
        // Author playlist = run pertama subtitle — TAPI string bentuk
        // durasi JANGAN PERNAH dipakai sebagai author.
        val rawAuthor = sub.getOrNull(0) ?: ""
        val author = if (isDurationText(rawAuthor)) "" else rawAuthor
        return ClassifiedItem.Playlists(
            playlistRefFrom(browseId, title, author, thumbnails),
        )
    }
    return null
}

/**
 * Satu rak discovery → satu seksi, atau null bila raknya tidak dikenali /
 * kosong. Rak `gridRenderer` tidak punya judul sendiri, jadi memakai judul
 * halaman sebagai gantinya.
 */
private fun parseDiscoverySection(section: JsonElement, pageTitle: String): DiscoverySection? {
    val title = discoveryShelfTitle(section).ifEmpty { pageTitle }
    val classified = mutableListOf<ClassifiedItem>()

    val carousel = pick(section, "musicCarouselShelfRenderer")
    val shelf = pick(section, "musicShelfRenderer")
    val grid = pick(section, "gridRenderer")
    if (isObj(carousel) || isObj(shelf)) {
        val contents = pickArr(if (isObj(carousel)) carousel else shelf, "contents")
        for (item in contents) {
            val c = classifyTwoRow(item) ?: classifyListRow(item)
            if (c != null) classified.add(c)
        }
    } else if (isObj(grid)) {
        for (item in pickArr(grid, "items")) {
            val c = classifyTwoRow(item)
            if (c != null) classified.add(c)
        }
    } else {
        return null
    }

    if (classified.isEmpty()) return null
    val kind = classified[0].kind
    // Seksi campuran hanya mengembalikan jenis item pertamanya.
    val same = classified.filter { it.kind == kind }
    return when (kind) {
        "songs" -> DiscoverySection.Songs(title, same.map { (it as ClassifiedItem.Songs).item })
        "albums" -> DiscoverySection.Albums(title, same.map { (it as ClassifiedItem.Albums).item })
        "artists" -> DiscoverySection.Artists(title, same.map { (it as ClassifiedItem.Artists).item })
        else -> DiscoverySection.Playlists(title, same.map { (it as ClassifiedItem.Playlists).item })
    }
}

/** Semua seksi yang dikenali di satu halaman discovery. */
fun parseDiscoverySections(res: JsonElement): List<DiscoverySection> {
    val pageTitle = pickStr(res, "header.musicHeaderRenderer.title.runs.0.text")
    val out = mutableListOf<DiscoverySection>()
    for (section in discoverySectionList(res)) {
        if (out.size >= MAX_DISCOVERY_SECTIONS) break
        val parsed = parseDiscoverySection(section, pageTitle)
        if (parsed != null) out.add(parsed)
    }
    return out
}

/**
 * Home: semua rak yang dikenali, apa pun jenis isinya. Lagu home TIDAK
 * punya durasi — hulu tidak mengirimnya di rak ini.
 */
fun parseHome(res: JsonElement): List<DiscoverySection> = parseDiscoverySections(res)

/**
 * Charts: daftar lagunya sendiri TIDAK ada di respons tanpa login — yang
 * ada adalah playlist video, genre, album teratas, artis teratas, podcast.
 * (Label negara dari tombol pemilih tidak dipakai klien Worker lama; di sini
 * juga tidak dikembalikan.)
 */
fun parseCharts(res: JsonElement): List<DiscoverySection> = parseDiscoverySections(res)

fun parseNewReleases(res: JsonElement): List<DiscoverySection> = parseDiscoverySections(res)

/**
 * Daftar kategori mood & genre: grid `musicNavigationButtonRenderer`, judul
 * di `buttonText`, token di `clickCommand.browseEndpoint.params`.
 */
fun parseMoodCategories(res: JsonElement): List<MoodGroup> {
    val sections = mutableListOf<MoodGroup>()
    for (section in discoverySectionList(res)) {
        val grid = pick(section, "gridRenderer")
        if (!isObj(grid)) continue
        val title = pickStr(grid, "header.gridHeaderRenderer.title.runs.0.text")
        val items = mutableListOf<MoodCategory>()
        for (item in pickArr(grid, "items")) {
            val btn = pick(item, "musicNavigationButtonRenderer")
            if (!isObj(btn)) continue
            val t = pickStr(btn, "buttonText.runs.0.text")
            val params = pickStr(btn, "clickCommand.browseEndpoint.params")
            if (t.isNotEmpty() && params.isNotEmpty()) items.add(MoodCategory(t, params))
        }
        if (title.isNotEmpty() && items.isNotEmpty()) sections.add(MoodGroup(title, items))
    }
    return sections
}

/** Cari kategori mood berdasarkan judul, tanpa peduli besar-kecil huruf dan spasi ganda. */
fun findMoodCategory(categories: List<MoodGroup>, mood: String): MoodCategory? {
    fun norm(s: String): String = s.trim().replace(Regex("\\s+"), " ").lowercase()
    val want = norm(mood)
    if (want.isEmpty()) return null
    for (section in categories) {
        for (item in section.items) {
            if (norm(item.title) == want) return item
        }
    }
    return null
}
