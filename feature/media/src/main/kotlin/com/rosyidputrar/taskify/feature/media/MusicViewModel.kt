package com.rosyidputrar.taskify.feature.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.designsystem.component.launchSafe
import com.rosyidputrar.taskify.core.domain.media.ArtistPage
import com.rosyidputrar.taskify.core.domain.media.BrowseTarget
import com.rosyidputrar.taskify.core.domain.media.CollectionKind
import com.rosyidputrar.taskify.core.domain.media.DiscoverySection
import com.rosyidputrar.taskify.core.domain.media.Lyrics
import com.rosyidputrar.taskify.core.domain.media.MoodGroup
import com.rosyidputrar.taskify.core.domain.media.MoodPage
import com.rosyidputrar.taskify.core.domain.media.MusicCatalog
import com.rosyidputrar.taskify.core.domain.media.MusicCollection
import com.rosyidputrar.taskify.core.domain.media.MusicQuality
import com.rosyidputrar.taskify.core.domain.media.MusicSettings
import com.rosyidputrar.taskify.core.domain.media.RadioSource
import com.rosyidputrar.taskify.core.domain.media.RadioStation
import com.rosyidputrar.taskify.core.domain.media.SearchFilter
import com.rosyidputrar.taskify.core.domain.media.SearchResults
import com.rosyidputrar.taskify.core.domain.media.SleepTimer
import com.rosyidputrar.taskify.core.domain.media.Song
import com.rosyidputrar.taskify.core.domain.media.Suggestion
import com.rosyidputrar.taskify.core.domain.media.TopResult
import com.rosyidputrar.taskify.core.domain.media.YtState
import com.rosyidputrar.taskify.core.domain.media.YtStatus
import com.rosyidputrar.taskify.core.media.LocalRadioStations
import com.rosyidputrar.taskify.core.media.MusicPlayer
import com.rosyidputrar.taskify.core.media.YouTubeMusicClient
import com.rosyidputrar.taskify.core.media.YouTubeMusicException
import com.rosyidputrar.taskify.core.media.PlaybackPosition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class MusicTab(val label: String) { Explore("Jelajah"), Search("Cari"), Queue("Antrean"), Lyrics("Lirik") }

data class MusicSearch(
    val query: String = "",
    val filter: SearchFilter = SearchFilter.All,
    val results: SearchResults = SearchResults(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val searched: Boolean = false,
    val suggestions: List<Suggestion> = emptyList(),
) {
    val cursor: String? get() = results.cursor
}

/** Lirik lagu yang sedang diputar; [lyrics] `null` + tidak memuat = tidak ada lirik. */
data class LyricsState(val videoId: String = "", val lyrics: Lyrics? = null, val loading: Boolean = false, val error: String? = null)

/** Satu halaman yang dibuka di dalam layar Musik (album, playlist, artis, podcast, mood). */
data class BrowsePage(
    val target: BrowseTarget,
    val loading: Boolean = true,
    val error: String? = null,
    val collection: MusicCollection? = null,
    val artist: ArtistPage? = null,
    val mood: MoodPage? = null,
    val loadingMore: Boolean = false,
) {
    val title: String get() = collection?.title ?: artist?.name ?: mood?.title?.ifBlank { null } ?: when (target) {
        is BrowseTarget.Album -> target.title
        is BrowseTarget.Playlist -> target.title
        is BrowseTarget.Artist -> target.title
        is BrowseTarget.Podcast -> target.title
        is BrowseTarget.Mood -> target.title
        else -> ""
    }
}

data class ExploreState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val home: List<DiscoverySection> = emptyList(),
    val charts: List<DiscoverySection> = emptyList(),
    val releases: List<DiscoverySection> = emptyList(),
    val moods: List<MoodGroup> = emptyList(),
    val stations: List<RadioStation> = emptyList(),
)

data class MusicUiState(
    val online: Boolean = true,
    val tab: MusicTab = MusicTab.Explore,
    val search: MusicSearch = MusicSearch(),
    val player: YtState = YtState(),
    val position: PlaybackPosition = PlaybackPosition(),
    val lyrics: LyricsState = LyricsState(),
    val pages: List<BrowsePage> = emptyList(),
    val explore: ExploreState = ExploreState(),
    val settings: MusicSettings = MusicSettings(),
    val sleep: SleepTimer? = null,
    /** Sedang memutar dari link/radio/saran (menunggu server). */
    val busy: String? = null,
) {
    val page: BrowsePage? get() = pages.lastOrNull()
}

interface MusicActions {
    fun setTab(tab: MusicTab) {}
    fun setQuery(q: String) {}
    fun setFilter(f: SearchFilter) {}
    fun search() {}
    fun loadMore() {}
    /** Putar lagu ke-[index] dari daftar lagu hasil pencarian. */
    fun play(index: Int) {}
    fun playSongs(songs: List<Song>, index: Int) {}
    /** Lagu tunggal dari hasil cari/Jelajah: hanya lagu itu + lagu serupa — hasil lain TIDAK masuk antrean. */
    fun playSong(song: Song) {}
    fun playTop(top: TopResult) {}
    fun togglePlay() {}
    fun next() {}
    fun prev() {}
    fun seek(ms: Long) {}
    fun jump(position: Int) {}
    fun toggleRepeat() {}
    fun toggleShuffle() {}
    fun toggleAutoContinue() {}
    fun stop() {}
    fun open(target: BrowseTarget) {}
    fun back() {}
    fun retryPage() {}
    fun loadMorePage() {}
    fun playPage(shuffle: Boolean) {}
    fun startRadio(song: Song) {}
    fun pickSuggestion(s: Suggestion) {}
    fun openLink(url: String) {}
    fun loadExplore(force: Boolean = false) {}
    fun playStation(station: RadioStation) {}
    fun seekLyric(seconds: Double) {}
    fun setQuality(q: MusicQuality) {}
    fun toggleNormalize() {}
    fun toggleSponsorBlock() {}
    fun togglePreferMp4a() {}
    fun setSleep(minutes: Int?) {}
    fun sleepEndOfSong() {}
}

/** Status online (petunjuk saja; diisi app dari NetworkMonitor). */
fun interface OnlineFlow {
    fun flow(): Flow<Boolean>
}

class MusicViewModel(
    private val player: MusicPlayer,
    private val client: YouTubeMusicClient,
    online: OnlineFlow,
    /** Snackbar aplikasi (sama dengan modul lain); null di tes. */
    private val messenger: Messenger? = null,
) : ViewModel(), MusicActions {
    private val local = MutableStateFlow(MusicUiState())
    private var searchJob: Job? = null
    private var suggestJob: Job? = null
    private var pageJob: Job? = null
    private var lyricsJob: Job? = null
    private val extras = combine(player.settings, player.sleep) { set, sleep -> set to sleep }

    val state: StateFlow<MusicUiState> = combine(local, player.state, player.position, online.flow(), extras) { l, p, pos, on, ex ->
        l.copy(player = p, position = pos, online = on, settings = ex.first, sleep = ex.second)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    init {
        // Lirik mengikuti lagu yang sedang diputar (dan keterangannya, yang bisa membawa lirik inline).
        launchSafe {
            player.state.map { s -> s.current to s.current?.let { c -> s.details[c.videoId] } }.distinctUntilChanged().collect { (song, details) ->
                if (song == null) { lyricsJob?.cancel(); local.update { it.copy(lyrics = LyricsState()) }; return@collect }
                val cur = local.value.lyrics
                if (cur.videoId == song.videoId && (cur.lyrics != null || cur.error != null || (cur.loading && details == null))) return@collect
                loadLyrics(song, details)
            }
        }
        if (local.value.tab == MusicTab.Explore) loadExplore()
    }

    private fun loadLyrics(song: Song, details: com.rosyidputrar.taskify.core.domain.media.TrackDetails?) {
        lyricsJob?.cancel()
        val id = song.videoId
        when {
            song.live -> { local.update { it.copy(lyrics = LyricsState(id)) }; return }
            details == null -> { local.update { it.copy(lyrics = LyricsState(id, loading = true)) }; return }
            details.lyricsChecked -> { local.update { it.copy(lyrics = LyricsState(id, details.lyrics)) }; return }
        }
        local.update { it.copy(lyrics = LyricsState(id, loading = true)) }
        lyricsJob = launchSafe {
            // Repository lirik (LRCLib -> teks YouTube -> transkrip); null =
            // tidak ada lirik, bukan galat. lyricsId dari Track dipakai untuk
            // langkah teks YouTube supaya tidak memanggil `next` lagi.
            val r = runCatching { client.syncedLyrics(song, details?.lyricsId) }
            if (local.value.lyrics.videoId != id) return@launchSafe
            local.update { it.copy(lyrics = LyricsState(id, r.getOrNull()?.takeIf { l -> !l.empty || l.instrumental }, false, r.exceptionOrNull()?.message)) }
        }
    }

    private fun say(text: String?) { if (!text.isNullOrBlank()) messenger?.show(text) }

    // ------------------------------------------------------------------ pencarian

    override fun setTab(tab: MusicTab) {
        local.update { it.copy(tab = tab) }
        if (tab == MusicTab.Explore) loadExplore()
    }

    override fun setQuery(q: String) {
        local.update { it.copy(search = it.search.copy(query = q, suggestions = if (q.isBlank()) emptyList() else it.search.suggestions)) }
        suggestJob?.cancel()
        val text = q.trim()
        if (text.length < 2 || MusicCatalog.isLink(text)) { local.update { it.copy(search = it.search.copy(suggestions = emptyList())) }; return }
        suggestJob = launchSafe {
            delay(SUGGEST_DEBOUNCE_MS)
            val r = runCatching { client.suggest(text) }
            if (local.value.search.query.trim() != text) return@launchSafe
            val e = r.exceptionOrNull()
            // Galat jaringan/server sesaat: dropdown disembunyikan diam-diam (bukan fitur utama).
            // Tapi galat auth InnerTube (401/403 — padanan 401/403 PR #8) ditampilkan,
            // kalau tidak saran "tidak pernah muncul" tanpa petunjuk apa pun.
            // Pesan fasad untuk auth anonim adalah "YouTube menolak permintaan",
            // bukan "Sesi berakhir" (tidak ada sesi yang bisa habis).
            val blocked = (e as? YouTubeMusicException)?.takeIf { it.isAuth }?.message
            local.update { it.copy(search = it.search.copy(suggestions = r.getOrDefault(emptyList()).take(8), error = blocked ?: it.search.error)) }
        }
    }

    override fun setFilter(f: SearchFilter) {
        local.update { it.copy(search = it.search.copy(filter = f)) }
        if (local.value.search.searched) search()
    }

    override fun search() {
        val s = local.value.search
        val q = s.query.trim()
        if (q.isEmpty()) return
        suggestJob?.cancel()
        if (MusicCatalog.isLink(q)) { openLink(q); return }
        searchJob?.cancel()
        local.update { it.copy(tab = MusicTab.Search, pages = emptyList(), search = s.copy(loading = true, error = null, suggestions = emptyList())) }
        searchJob = launchSafe {
            try {
                val results = client.search(q, s.filter)
                local.update { it.copy(search = it.search.copy(results = results, loading = false, searched = true)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(search = it.search.copy(loading = false, error = e.message, searched = true)) }
            }
        }
    }

    override fun loadMore() {
        val s = local.value.search
        val cursor = s.cursor ?: return
        if (s.loading || s.loadingMore) return
        local.update { it.copy(search = s.copy(loadingMore = true, error = null)) }
        launchSafe {
            try {
                val next = client.searchMore(cursor)
                local.update { it.copy(search = it.search.copy(results = it.search.results.merge(next), loadingMore = false)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(search = it.search.copy(loadingMore = false, error = e.message)) }
            }
        }
    }

    override fun pickSuggestion(s: Suggestion) {
        suggestJob?.cancel()
        local.update { it.copy(search = it.search.copy(suggestions = emptyList())) }
        when {
            s.type == "video" && s.videoId.isNotEmpty() -> playVideo(s.videoId)
            s.type == "artist" && s.browseId.isNotEmpty() -> open(BrowseTarget.Artist(s.browseId, s.text))
            else -> { local.update { it.copy(search = it.search.copy(query = s.text)) }; search() }
        }
    }

    /** Link YouTube/YouTube Music (ditempel atau dibagikan) → lagu, playlist, album, artis, podcast. */
    override fun openLink(url: String) {
        val link = MusicCatalog.findLink(url) ?: url.trim()
        local.update { it.copy(busy = "Membuka link…", search = it.search.copy(suggestions = emptyList())) }
        launchSafe {
            try {
                val target = MusicCatalog.route(client.resolveUrl(link)) ?: throw IllegalStateException("Link ini tidak bisa dibuka di pemutar musik.")
                local.update { it.copy(busy = null) }
                open(target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(busy = null) }
                say(e.message ?: "Link tidak dikenali.")
            }
        }
    }

    // ------------------------------------------------------------------ pemutaran

    override fun play(index: Int) { local.value.search.results.songs.getOrNull(index)?.let(::playSong) }
    override fun playSongs(songs: List<Song>, index: Int) = player.playQueue(songs, index)
    override fun playSong(song: Song) = player.playSimilar(song)
    override fun playTop(top: TopResult) {
        top.toSong()?.let { song ->
            playSong(song); return
        }
        // Hasil teratas bukan lagu: buka artis/album yang cocok di daftar hasil.
        val r = local.value.search.results
        r.artists.firstOrNull { it.name.equals(top.title, true) }?.let { open(BrowseTarget.Artist(it.browseId, it.name)); return }
        r.albums.firstOrNull { it.title.equals(top.title, true) }?.let { open(BrowseTarget.Album(it.browseId, it.title)) }
    }
    override fun togglePlay() = player.togglePlay()
    override fun next() = player.next()
    override fun prev() = player.prev()
    override fun seek(ms: Long) { if (!player.seekTo(ms)) say("Audio ini tidak bisa digeser.") }
    override fun seekLyric(seconds: Double) { if (local.value.player.seekable) player.seekTo((seconds * 1000).toLong()) }
    override fun jump(position: Int) = player.jump(position)
    override fun toggleRepeat() = player.toggleRepeat()
    override fun toggleShuffle() = player.toggleShuffle()
    override fun toggleAutoContinue() = player.toggleAutoContinue()
    override fun stop() = player.stop()
    override fun playStation(station: RadioStation) = player.playStation(station)

    private fun playVideo(videoId: String) {
        local.update { it.copy(busy = "Menyiapkan lagu…") }
        launchSafe {
            try {
                val t = client.track(videoId)
                val song = t.song ?: Song(videoId, "Lagu", "", "", emptyList())
                player.playQueue(listOf(song) + t.upNext, 0)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say(e.message)
            } finally {
                local.update { it.copy(busy = null) }
            }
        }
    }

    /** Mix radio dari satu lagu: lagu itu dulu, lalu 49 lagu serupa (lanjutan lewat cursor radio). */
    override fun startRadio(song: Song) = radio(song, null, "Menyiapkan radio…")

    private fun radio(seed: Song?, playlistId: String?, label: String) {
        local.update { it.copy(busy = label) }
        launchSafe {
            try {
                val r = client.radio(videoId = seed?.videoId?.takeIf { playlistId == null }, playlistId = playlistId)
                val songs = (listOfNotNull(seed) + r.songs)
                if (songs.isEmpty()) { say("Radio ini sedang kosong."); return@launchSafe }
                player.playQueue(songs, 0, RadioSource(r.playlistId.ifEmpty { playlistId.orEmpty() }, r.cursor))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say(e.message)
            } finally {
                local.update { it.copy(busy = null) }
            }
        }
    }

    // ------------------------------------------------------------------ halaman koleksi

    override fun open(target: BrowseTarget) {
        when (target) {
            is BrowseTarget.Video -> { playVideo(target.videoId); return }
            is BrowseTarget.Radio -> { radio(null, target.playlistId, "Menyiapkan mix…"); return }
            else -> Unit
        }
        if (local.value.page?.target?.key == target.key) return
        local.update { it.copy(pages = (it.pages + BrowsePage(target)).takeLast(MAX_PAGES)) }
        loadPage(target)
    }

    override fun back() {
        pageJob?.cancel()
        local.update { it.copy(pages = it.pages.dropLast(1)) }
    }

    override fun retryPage() { local.value.page?.let { p -> updatePage(p.target) { BrowsePage(p.target) }; loadPage(p.target) } }

    private fun updatePage(target: BrowseTarget, f: (BrowsePage) -> BrowsePage) =
        local.update { s -> s.copy(pages = s.pages.map { if (it.target.key == target.key) f(it) else it }) }

    private fun loadPage(target: BrowseTarget) {
        pageJob?.cancel()
        pageJob = launchSafe {
            try {
                val page = when (target) {
                    is BrowseTarget.Album -> BrowsePage(target, false, collection = client.album(target.browseId))
                    is BrowseTarget.Playlist -> BrowsePage(target, false, collection = client.playlist(target.browseId))
                    is BrowseTarget.Podcast -> BrowsePage(target, false, collection = client.podcast(target.browseId))
                    is BrowseTarget.Artist -> BrowsePage(target, false, artist = client.artist(target.browseId))
                    is BrowseTarget.Mood -> BrowsePage(target, false, mood = client.mood(target.params, target.title))
                    else -> return@launchSafe
                }
                updatePage(target) { page }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updatePage(target) { it.copy(loading = false, error = e.message ?: "Gagal memuat.") }
            }
        }
    }

    override fun loadMorePage() {
        val p = local.value.page ?: return
        val c = p.collection ?: return
        val cursor = c.cursor ?: return
        if (p.loadingMore) return
        updatePage(p.target) { it.copy(loadingMore = true) }
        launchSafe {
            try {
                // Podcast: judul acara halaman pertama diteruskan — respons
                // lanjutan tidak mengulang judul itu (gap Tahap 6).
                val showTitle = if (c.kind == CollectionKind.Podcast) c.title else ""
                val next = client.collectionMore(c.kind, cursor, c.id, showTitle)
                updatePage(p.target) { it.copy(loadingMore = false, collection = it.collection?.merge(next)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updatePage(p.target) { it.copy(loadingMore = false) }
                say(e.message)
            }
        }
    }

    /**
     * Putar seluruh koleksi. Playlist panjang: halaman lanjutan (≤ 100 per halaman) diambil dulu
     * sampai habis supaya antrean tidak berhenti di lagu ke-100 tanpa terlihat.
     */
    override fun playPage(shuffle: Boolean) {
        val p = local.value.page ?: return
        p.artist?.let { a ->
            val pid = a.topSongsPlaylistId
            if (pid != null) { open(BrowseTarget.Playlist(pid, "Lagu teratas ${a.name}")); return }
            if (a.topSongs.isNotEmpty()) startQueue(a.topSongs, shuffle)
            return
        }
        val c = p.collection ?: return
        if (c.cursor == null) { startQueue(c.tracks, shuffle); return }
        local.update { it.copy(busy = "Mengambil semua lagu…") }
        launchSafe {
            var all = c
            try {
                var pages = 0
                while (all.cursor != null && pages < MAX_COLLECTION_PAGES) {
                    // Podcast: judul acara halaman pertama diteruskan (gap Tahap 6, sama seperti loadMorePage).
                    val showTitle = if (c.kind == CollectionKind.Podcast) c.title else ""
                    all = all.merge(client.collectionMore(c.kind, all.cursor!!, c.id, showTitle))
                    pages++
                }
                updatePage(p.target) { it.copy(collection = all) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Sebagian halaman sudah cukup untuk mulai.
            } finally {
                local.update { it.copy(busy = null) }
            }
            startQueue(all.tracks, shuffle)
        }
    }

    private fun startQueue(songs: List<Song>, shuffle: Boolean) {
        if (songs.isEmpty()) return
        if (shuffle != local.value.player.shuffle) player.toggleShuffle()
        player.playQueue(songs, if (shuffle) songs.indices.random() else 0)
    }

    // ------------------------------------------------------------------ jelajah

    override fun loadExplore(force: Boolean) {
        val e = local.value.explore
        if (e.loading || (e.loaded && !force && e.error == null)) return
        local.update { it.copy(explore = it.explore.copy(loading = true, error = null)) }
        launchSafe {
            coroutineScope {
                val home = async { runCatching { client.home() } }
                val charts = async { runCatching { client.charts("ID") } }
                val releases = async { runCatching { client.newReleases() } }
                val moods = async { runCatching { client.moods() } }
                val stations = async { runCatching { client.stations() } }
                val all = listOf(home.await(), charts.await(), releases.await(), moods.await())
                val firstError = all.firstNotNullOfOrNull { it.exceptionOrNull() }
                local.update {
                    it.copy(explore = ExploreState(
                        loading = false, loaded = true,
                        error = if (all.all { r -> r.isFailure }) firstError?.message ?: "Gagal memuat Jelajah." else null,
                        home = home.await().getOrDefault(emptyList()),
                        charts = charts.await().getOrDefault(emptyList()),
                        releases = releases.await().getOrDefault(emptyList()),
                        moods = moods.await().getOrDefault(emptyList()),
                        stations = stations.await().getOrDefault(LocalRadioStations.FALLBACK),
                    ))
                }
            }
        }
    }

    // ------------------------------------------------------------------ pengaturan

    override fun setQuality(q: MusicQuality) = player.updateSettings { it.copy(quality = q) }
    override fun toggleNormalize() = player.updateSettings { it.copy(normalize = !it.normalize) }
    override fun toggleSponsorBlock() = player.updateSettings { it.copy(sponsorBlock = !it.sponsorBlock) }
    override fun togglePreferMp4a() = player.updateSettings { it.copy(preferMp4a = !it.preferMp4a) }
    override fun setSleep(minutes: Int?) = player.setSleep(minutes)
    override fun sleepEndOfSong() = player.setSleep(null, endOfSong = true)

    companion object {
        const val SUGGEST_DEBOUNCE_MS = 250L
        const val MAX_PAGES = 8
        /** Playlist ±1000 lagu cukup; lebih dari itu antrean terlalu panjang untuk disimpan. */
        const val MAX_COLLECTION_PAGES = 9
    }
}

/** Status pemutar ringkas untuk dipakai modul lain (Mode Fokus). */
fun YtState.isActive(): Boolean = status != YtStatus.Idle
