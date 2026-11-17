package com.rosyidputrar.taskify.core.domain.media

/**
 * Katalog YouTube Music dari Worker music-taskify: hasil pencarian lengkap, halaman album /
 * playlist / artis / podcast, Jelajah (home, tangga lagu, rilis baru, mood), radio, saran, dan
 * link YouTube yang ditempel/dibagikan. Semua model murni (tanpa Android) supaya bisa dites JVM.
 */
data class AlbumRef(val browseId: String, val title: String, val artist: String, val year: String, val thumbnails: List<Thumb>) {
    val thumb: String get() = thumbnails.firstOrNull()?.url.orEmpty()
    val cover: String get() = bestThumb(thumbnails)
}

data class PlaylistRef(val browseId: String, val playlistId: String, val title: String, val author: String, val thumbnails: List<Thumb>) {
    val cover: String get() = bestThumb(thumbnails)
}

data class ArtistRef(val browseId: String, val name: String, val subscribers: String, val thumbnails: List<Thumb>) {
    val cover: String get() = bestThumb(thumbnails)
}

data class PodcastRef(val browseId: String, val title: String, val author: String, val thumbnails: List<Thumb>) {
    val cover: String get() = bestThumb(thumbnails)
}

/** Hasil teratas pencarian; [videoId] kosong = bukan lagu (album/artis) — jangan diputar. */
data class TopResult(val title: String, val videoId: String, val subtitles: List<String>, val thumbnails: List<Thumb>, val url: String = "") {
    val cover: String get() = bestThumb(thumbnails)
    val playable: Boolean get() = videoId.length == 11

    /** Subjudul tanpa kata jenis di depan ("Video", "Lagu", …) dan tanpa durasi di belakang. */
    val byline: String get() {
        val parts = subtitles.filter { it.isNotBlank() }.toMutableList()
        if (parts.firstOrNull()?.lowercase() in KIND_WORDS) parts.removeAt(0)
        if (parts.lastOrNull()?.let { MusicQueue.parseDuration(it) } != null) parts.removeAt(parts.size - 1)
        return parts.joinToString(" · ")
    }

    /** Jenis dari potongan pertama bila memang kata jenis. */
    val kind: String? get() = subtitles.firstOrNull()?.takeIf { it.lowercase() in KIND_WORDS }

    /** Lagu siap putar dari hasil teratas (hanya bila [playable]). */
    fun toSong(): Song? = if (!playable) null else Song(
        videoId, title, byline.substringBefore(" · "),
        subtitles.lastOrNull()?.takeIf { MusicQueue.parseDuration(it) != null }.orEmpty(), thumbnails, url,
        subtitles.lastOrNull()?.let { MusicQueue.parseDuration(it) },
    )

    private companion object {
        val KIND_WORDS = setOf("lagu", "video", "album", "artis", "playlist", "podcast", "episode", "single", "ep", "song", "artist")
    }
}

/** Seluruh hasil satu halaman pencarian, dikelompokkan per jenis. */
data class SearchResults(
    val top: TopResult? = null,
    val songs: List<Song> = emptyList(),
    val albums: List<AlbumRef> = emptyList(),
    val playlists: List<PlaylistRef> = emptyList(),
    val artists: List<ArtistRef> = emptyList(),
    val podcasts: List<PodcastRef> = emptyList(),
    val episodes: List<Song> = emptyList(),
    val cursor: String? = null,
) {
    val isEmpty: Boolean get() = top == null && songs.isEmpty() && albums.isEmpty() && playlists.isEmpty() && artists.isEmpty() &&
        podcasts.isEmpty() && episodes.isEmpty()

    /** Gabung halaman lanjutan: tiap daftar didedup (Worker tidak menjamin unik lintas halaman). */
    fun merge(next: SearchResults): SearchResults = SearchResults(
        top = top ?: next.top,
        songs = MusicQueue.mergePages(songs, next.songs),
        albums = mergeBy(albums, next.albums) { it.browseId },
        playlists = mergeBy(playlists, next.playlists) { it.browseId },
        artists = mergeBy(artists, next.artists) { it.browseId },
        podcasts = mergeBy(podcasts, next.podcasts) { it.browseId },
        episodes = MusicQueue.mergePages(episodes, next.episodes),
        cursor = next.cursor,
    )
}

fun <T> mergeBy(existing: List<T>, incoming: List<T>, key: (T) -> String): List<T> {
    val seen = existing.map(key).toHashSet()
    return existing + incoming.filter { key(it).isNotEmpty() && seen.add(key(it)) }
}

fun bestThumb(thumbs: List<Thumb>): String = thumbs.filter { it.url.isNotEmpty() }.maxByOrNull { it.width }?.url.orEmpty()

/** Jenis halaman koleksi yang bisa dibuka di dalam layar Musik. */
enum class CollectionKind(val label: String) { Album("Album"), Playlist("Playlist"), Podcast("Podcast") }

/**
 * Album / playlist / acara podcast: daftar lagu + cursor halaman lanjutan. Trek album biasanya
 * tanpa gambar — gambar album dipakai untuk semua treknya (lihat [MusicCatalog.albumTracks]).
 */
data class MusicCollection(
    val kind: CollectionKind,
    val id: String,
    val title: String,
    val subtitle: String,
    val thumbnails: List<Thumb>,
    val tracks: List<Song>,
    val cursor: String? = null,
    val trackCount: Int? = null,
    val description: String = "",
) {
    val cover: String get() = bestThumb(thumbnails)
    fun merge(next: MusicCollection): MusicCollection = copy(tracks = MusicQueue.mergePages(tracks, next.tracks), cursor = next.cursor)
}

data class ArtistPage(
    val browseId: String,
    val name: String,
    val subscribers: String,
    val monthlyListeners: String,
    val description: String,
    val thumbnails: List<Thumb>,
    val topSongs: List<Song>,
    val topSongsPlaylistId: String?,
    val albums: List<AlbumRef>,
    val singles: List<AlbumRef>,
    val related: List<ArtistRef>,
) {
    val cover: String get() = bestThumb(thumbnails)
}

/** Satu rak halaman Jelajah; jenis item mengikuti `kind` dari Worker. */
sealed interface DiscoverySection {
    val title: String
    data class Songs(override val title: String, val items: List<Song>) : DiscoverySection
    data class Albums(override val title: String, val items: List<AlbumRef>) : DiscoverySection
    data class Artists(override val title: String, val items: List<ArtistRef>) : DiscoverySection
    data class Playlists(override val title: String, val items: List<PlaylistRef>) : DiscoverySection
}

data class MoodCategory(val title: String, val params: String)
data class MoodGroup(val title: String, val items: List<MoodCategory>)
data class MoodPage(val title: String, val params: String, val sections: List<DiscoverySection>)

data class RadioPageResult(val songs: List<Song>, val playlistId: String, val seedVideoId: String, val cursor: String?)

/** Stasiun radio langsung (SomaFM, Radio France, Radio Paradise); [mood] menurut jenis kerja. */
data class RadioStation(
    val id: String,
    val name: String,
    val url: String,
    val mood: String,
    val description: String,
    val provider: String,
    val homepage: String = "",
    val vocals: Boolean = false,
) {
    val moodLabel: String get() = when (mood) {
        "deep" -> "Kerja dalam"
        "light" -> "Ringan"
        "jazz" -> "Jazz"
        "chill" -> "Santai"
        else -> mood.replaceFirstChar { it.uppercase() }
    }

    /** Diputar sebagai lagu "live": URL langsung, tanpa konversi/lirik/seek. */
    fun toSong(): Song = Song("live:$id", name, provider, "", emptyList(), url, null, live = true)
}

data class Suggestion(val text: String, val type: String, val subtitle: String = "", val videoId: String = "", val browseId: String = "")

/** Hasil `/v1/resolve-url`. */
data class ResolvedLink(val kind: String, val videoId: String = "", val browseId: String = "", val playlistId: String = "", val url: String = "")

/** Ke mana sebuah item/link dibuka di layar Musik. */
sealed interface BrowseTarget {
    data class Album(val browseId: String, val title: String = "") : BrowseTarget
    data class Playlist(val browseId: String, val title: String = "") : BrowseTarget
    data class Artist(val browseId: String, val title: String = "") : BrowseTarget
    data class Podcast(val browseId: String, val title: String = "") : BrowseTarget
    data class Mood(val params: String, val title: String) : BrowseTarget
    /** Mix radio dari playlist `RD…` (bukan playlist biasa). */
    data class Radio(val playlistId: String, val title: String = "") : BrowseTarget
    data class Video(val videoId: String) : BrowseTarget

    val key: String get() = when (this) {
        is Album -> "album:$browseId"
        is Playlist -> "playlist:$browseId"
        is Artist -> "artist:$browseId"
        is Podcast -> "podcast:$browseId"
        is Mood -> "mood:$params"
        is Radio -> "radio:$playlistId"
        is Video -> "video:$videoId"
    }
}

object MusicCatalog {
    /**
     * Rute untuk satu browseId dari kartu playlist/album: `MPSP…` = seri podcast, `VLRD…`/`RD…` =
     * mix radio (dibuka lewat `/v1/radio`), `MPREb_…` = album, `UC…` = artis, sisanya playlist.
     */
    fun route(browseId: String, title: String = ""): BrowseTarget? {
        val id = browseId.trim()
        return when {
            id.isEmpty() -> null
            id.startsWith("MPSP") -> BrowseTarget.Podcast(id, title)
            id.startsWith("VLRD") -> BrowseTarget.Radio(id.removePrefix("VL"), title)
            id.startsWith("RD") -> BrowseTarget.Radio(id, title)
            id.startsWith("MPREb") -> BrowseTarget.Album(id, title)
            id.startsWith("UC") -> BrowseTarget.Artist(id, title)
            id.startsWith("VL") -> BrowseTarget.Playlist(id, title)
            else -> BrowseTarget.Playlist("VL$id", title)
        }
    }

    fun route(p: PlaylistRef): BrowseTarget? = route(p.browseId.ifEmpty { p.playlistId }, p.title)

    /** Hasil resolve-url → tujuan, mengikuti tabel API (RD → radio, VLOLAK → playlist, …). */
    fun route(r: ResolvedLink): BrowseTarget? = when (r.kind) {
        "video" -> r.videoId.takeIf { it.isNotEmpty() }?.let { BrowseTarget.Video(it) }
        "playlist" -> r.playlistId.takeIf { it.isNotEmpty() }?.let { if (it.startsWith("RD")) BrowseTarget.Radio(it) else BrowseTarget.Playlist(if (it.startsWith("VL")) it else "VL$it") }
            ?: r.browseId.takeIf { it.isNotEmpty() }?.let { route(it) }
        "album" -> r.browseId.takeIf { it.isNotEmpty() }?.let { if (it.startsWith("VL")) BrowseTarget.Playlist(it) else BrowseTarget.Album(it) }
            ?: r.playlistId.takeIf { it.isNotEmpty() }?.let { BrowseTarget.Playlist("VL$it") }
        "artist" -> r.browseId.takeIf { it.isNotEmpty() }?.let { BrowseTarget.Artist(it) }
        "podcast" -> r.browseId.takeIf { it.isNotEmpty() }?.let { BrowseTarget.Podcast(it) }
        else -> null
    }

    private val YT_LINK = Regex("""(?:https?://)?(?:www\.|m\.|music\.)?(?:youtube\.com|youtu\.be)/[^\s<>"']+""", RegexOption.IGNORE_CASE)

    /** Link YouTube / YouTube Music pertama di teks (mis. dari Bagikan), atau `null`. */
    fun findLink(text: String): String? = YT_LINK.find(text)?.value?.trimEnd('.', ',', ')', ']', '!', '?')

    /** Teks pencarian yang sebenarnya link YouTube → resolve, bukan cari. */
    fun isLink(text: String): Boolean = findLink(text.trim())?.length?.let { it >= text.trim().length - 1 } == true

    /** Trek album/playlist tanpa gambar memakai gambar koleksinya. */
    fun albumTracks(tracks: List<Song>, cover: List<Thumb>): List<Song> = tracks.map { if (it.thumbnails.isEmpty()) it.copy(thumbnails = cover) else it }

    /** Subjudul koleksi untuk kepala halaman. */
    fun subtitle(c: MusicCollection): String {
        val (total, complete) = MusicQueue.knownTotalSeconds(c.tracks)
        val count = c.trackCount ?: c.tracks.size
        val dur = if (total > 0) (if (complete && c.cursor == null) "" else "±") + formatLong(total) else ""
        return listOf(c.subtitle, if (count > 0) "$count ${if (c.kind == CollectionKind.Podcast) "episode" else "lagu"}" else "", dur).filter { it.isNotBlank() }.joinToString(" · ")
    }

    /** "1 j 05 mnt" / "42 mnt". */
    fun formatLong(seconds: Long): String {
        val m = seconds / 60
        return if (m >= 60) "${m / 60} j %02d mnt".format(m % 60) else "$m mnt"
    }
}
