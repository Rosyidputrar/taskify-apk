package com.rosyidputrar.taskify.core.innertube.pages

import com.rosyidputrar.taskify.core.domain.media.AlbumRef
import com.rosyidputrar.taskify.core.domain.media.ArtistRef
import com.rosyidputrar.taskify.core.domain.media.PlaylistRef
import com.rosyidputrar.taskify.core.domain.media.PodcastRef
import com.rosyidputrar.taskify.core.domain.media.SearchResults
import com.rosyidputrar.taskify.core.domain.media.Song
import com.rosyidputrar.taskify.core.domain.media.TopResult
import kotlinx.serialization.json.JsonElement

/**
 * Parser hasil pencarian — port `parseSearch`, `parseSearchMore`,
 * `searchToken`, dan `collectSearchRows` dari `src/ytmusic/parse.ts`
 * (Worker). Keluarannya model Taskify (`SearchResults` dkk), bukan
 * model ArchiveTune.
 */
fun parseSearch(res: JsonElement, query: String): SearchResults {
    val songs = mutableListOf<Song>()
    val albums = mutableListOf<AlbumRef>()
    val artists = mutableListOf<ArtistRef>()
    val playlists = mutableListOf<PlaylistRef>()
    val podcasts = mutableListOf<PodcastRef>()
    val episodes = mutableListOf<Song>()
    var topResult: TopResult? = null

    val sections = pickArr(
        res,
        "contents.tabbedSearchResultsRenderer.tabs.0.tabRenderer.content.sectionListRenderer.contents",
    )

    for (sec in sections) {
        val card = pick(sec, "musicCardShelfRenderer")
        // Nama artis dari header kartu — HANYA bila kartu itu memang kartu
        // artis (subjudul diawali "Artis"): baris lagu di dalam kartu artis
        // tidak membawa artis di subtitlenya (['Lagu', '3.33']), jadi tanpa
        // ini artisnya kosong. Kartu video/album (judul = judul lagu/album)
        // TIDAK boleh dipakai sebagai artis.
        var cardArtist = ""
        if (isObj(card)) {
            val vid = pickStr(card, "title.runs.0.navigationEndpoint.watchEndpoint.videoId")
            val cardTitle = pickStr(card, "title.runs.0.text")
            val cardSubtitles = runTexts(pickArr(card, "subtitle.runs"))
            if (cardSubtitles.firstOrNull()?.lowercase() in ARTIST_WORDS) {
                cardArtist = cardTitle
            }
            topResult = TopResult(
                title = cardTitle,
                videoId = vid,
                subtitles = cardSubtitles,
                thumbnails = thumbs(pickArr(card, "thumbnail.musicThumbnailRenderer.thumbnail.thumbnails")),
                url = watchUrl(vid),
            )
        }

        // Baris kartu diproses terpisah dari rak di bawahnya supaya
        // [cardArtist] hanya berlaku untuk baris kartu itu, bukan untuk
        // seluruh hasil pencarian. Urutan dipertahankan: kartu dulu.
        collectSearchRows(
            pickArr(sec, "musicCardShelfRenderer.contents"),
            songs, albums, artists, playlists, podcasts, episodes,
            cardArtist = cardArtist,
        )
        collectSearchRows(
            pickArr(sec, "itemSectionRenderer.contents") +
                pickArr(sec, "musicShelfRenderer.contents"),
            songs, albums, artists, playlists, podcasts, episodes,
        )
    }

    return SearchResults(
        top = topResult,
        songs = songs,
        albums = albums,
        playlists = playlists,
        artists = artists,
        podcasts = podcasts,
        episodes = episodes,
        cursor = searchToken(res).takeIf { it.isNotEmpty() },
    )
}

/**
 * Halaman lanjutan hasil pencarian — bentuknya
 * `continuationContents.musicShelfContinuation.contents`, bukan bentuk
 * halaman pertama.
 */
fun parseSearchMore(res: JsonElement): SearchResults {
    val songs = mutableListOf<Song>()
    val albums = mutableListOf<AlbumRef>()
    val artists = mutableListOf<ArtistRef>()
    val playlists = mutableListOf<PlaylistRef>()
    val podcasts = mutableListOf<PodcastRef>()
    val episodes = mutableListOf<Song>()
    collectSearchRows(
        pickArr(res, "continuationContents.musicShelfContinuation.contents"),
        songs, albums, artists, playlists, podcasts, episodes,
    )
    return SearchResults(
        songs = songs,
        albums = albums,
        playlists = playlists,
        artists = artists,
        podcasts = podcasts,
        episodes = episodes,
        cursor = searchToken(res).takeIf { it.isNotEmpty() },
    )
}

/** Token lanjutan untuk `search` — gaya LAMA (`nextContinuationData`). */
fun searchToken(res: JsonElement): String {
    return pickStr(deepFind(res, "nextContinuationData"), "continuation")
}

private fun flex(i: Int) = "flexColumns.$i.musicResponsiveListItemFlexColumnRenderer"

/**
 * Mengumpulkan baris hasil ke keranjang sesuai jenisnya. Dipisahkan dari
 * `parseSearch` karena halaman LANJUTAN memakai bentuk respons yang sama
 * sekali berbeda sementara isi barisnya identik.
 */
private fun collectSearchRows(
    items: List<JsonElement>,
    songs: MutableList<Song>,
    albums: MutableList<AlbumRef>,
    artists: MutableList<ArtistRef>,
    playlists: MutableList<PlaylistRef>,
    podcasts: MutableList<PodcastRef>,
    episodes: MutableList<Song>,
    // Nama artis dari header kartu hasil teratas ("" bila bukan kartu
    // artis): fallback artis untuk baris kartu yang subtitlenya tidak
    // membawa artis. Hanya diisi pemanggil untuk isi kartu itu sendiri.
    cardArtist: String = "",
) {
    for (item in items) {
        val r = pick(item, "musicResponsiveListItemRenderer")
        if (!isObj(r)) continue

        val title = pickStr(r, "${flex(0)}.text.runs.0.text")
        val nav = pick(r, "${flex(0)}.text.runs.0.navigationEndpoint")
        val vid = pickStr(nav, "watchEndpoint.videoId")
            .ifEmpty { pickStr(r, "playlistItemData.videoId") }
        // Untuk album/artis/playlist, navigationEndpoint berada di tingkat
        // BARIS, bukan di dalam flexColumns[0].
        val browseId =
            pickStr(nav, "browseEndpoint.browseId")
                .ifEmpty { pickStr(r, "navigationEndpoint.browseEndpoint.browseId") }

        val sub = runTexts(pickArr(r, "${flex(1)}.text.runs"))
        val first = (sub.getOrNull(0) ?: "").lowercase()
        val isTypeWord =
            SONG_WORDS.contains(first) ||
                ALBUM_WORDS.contains(first) ||
                ARTIST_WORDS.contains(first) ||
                PLAYLIST_WORDS.contains(first) ||
                EPISODE_WORDS.contains(first)
        val kind = if (isTypeWord) first else ""
        // Baris video sering TIDAK menyebut jenisnya, sehingga sub[0] justru
        // nama artisnya.
        val rawArtist = (if (isTypeWord) sub.getOrNull(1) else sub.getOrNull(0)) ?: ""
        // Baris kartu hasil teratas TIDAK membawa artis: sub hanya kata
        // jenis + durasi (['Lagu', ' • ', '3.33']) — string bentuk durasi
        // JANGAN PERNAH dipakai sebagai artis. Bila artis kosong atau
        // berbentuk durasi, pakai nama artis dari header kartu ("" bila
        // kartunya bukan kartu artis, mis. kartu video/album).
        val artist = if (rawArtist.isEmpty() || isDurationText(rawArtist)) cardArtist else rawArtist
        val last = sub.lastOrNull() ?: ""
        val duration = if (isDurationText(last)) last
        else pickStr(r, "fixedColumns.0.musicResponsiveListItemFixedColumnRenderer.text.runs.0.text")
        val thumbList = thumbs(pickArr(r, "thumbnail.musicThumbnailRenderer.thumbnail.thumbnails"))

        if (browseId.startsWith("MPED") || EPISODE_WORDS.contains(kind)) {
            // Episode podcast — HARUS dicek sebelum lagu: barisnya punya
            // `videoId` sehingga tanpa cabang ini ia masuk ke `songs`.
            // Durasi TIDAK ada di baris ini.
            if (vid.isEmpty()) continue
            if (episodes.any { it.videoId == vid }) continue
            val rest = if (EPISODE_WORDS.contains(kind)) sub.drop(1) else sub
            val show = rest.drop(1).joinToString(" ")
            val publishedAt = rest.getOrNull(0) ?: ""
            episodes.add(
                Song(
                    videoId = vid,
                    title = title,
                    // Episode diputar sebagai lagu: artis = nama acara · tanggal.
                    artist = listOf(show, publishedAt).filter { it.isNotBlank() }.joinToString(" · "),
                    duration = "",
                    thumbnails = thumbList,
                    url = watchUrl(vid),
                    durationSeconds = null,
                ),
            )
        } else if (vid.isNotEmpty() || SONG_WORDS.contains(kind)) {
            // Kartu hasil teratas dan daftar di bawahnya bisa memuat lagu
            // yang sama; yang pertama (dari kartu) menang karena membawa durasi.
            // TAPI bila versi pertama artisnya kosong dan versi rak bawah
            // membawa artis asli, isi artisnya dari versi itu — jangan buang
            // versi benar, dan jangan buang durasi versi kartu.
            if (vid.isNotEmpty()) {
                val existingIdx = songs.indexOfFirst { it.videoId == vid }
                if (existingIdx >= 0) {
                    val existing = songs[existingIdx]
                    if (existing.artist.isBlank() && artist.isNotBlank()) {
                        songs[existingIdx] = existing.copy(artist = artist)
                    }
                    continue
                }
            }
            songs.add(
                Song(
                    videoId = vid,
                    title = title,
                    artist = artist,
                    duration = duration,
                    thumbnails = thumbList,
                    url = watchUrl(vid),
                    durationSeconds = parseDurationText(duration),
                ),
            )
        } else if (browseId.startsWith("MPREb") || ALBUM_WORDS.contains(kind)) {
            albums.add(
                AlbumRef(
                    browseId = browseId,
                    title = title,
                    artist = artist,
                    year = sub.getOrNull(2) ?: "",
                    thumbnails = thumbList,
                ),
            )
        } else if (ARTIST_WORDS.contains(kind)) {
            artists.add(
                ArtistRef(
                    browseId = browseId,
                    name = title,
                    subscribers = sub.getOrNull(1) ?: "",
                    thumbnails = thumbList,
                ),
            )
        } else if (browseId.startsWith("MPSP")) {
            // Acara podcast — penandanya hanya awalan `MPSP`.
            if (podcasts.any { it.browseId == browseId }) continue
            val authorParts = if (sub.getOrNull(0)?.lowercase() == "podcast") sub.drop(1) else sub
            podcasts.add(
                PodcastRef(
                    browseId = browseId,
                    title = title,
                    author = authorParts.joinToString(" "),
                    thumbnails = thumbList,
                ),
            )
        } else if (browseId.startsWith("VL") || PLAYLIST_WORDS.contains(kind)) {
            val playlistId = if (browseId.startsWith("VL")) browseId.removePrefix("VL") else browseId
            playlists.add(
                PlaylistRef(
                    browseId = browseId,
                    playlistId = playlistId,
                    title = title,
                    author = artist,
                    thumbnails = thumbList,
                ),
            )
        }
    }
}
