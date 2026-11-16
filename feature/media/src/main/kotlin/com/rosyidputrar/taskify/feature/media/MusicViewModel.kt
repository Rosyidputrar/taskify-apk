package com.rosyidputrar.taskify.feature.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.domain.media.MusicQueue
import com.rosyidputrar.taskify.core.domain.media.SearchFilter
import com.rosyidputrar.taskify.core.domain.media.Song
import com.rosyidputrar.taskify.core.domain.media.YtState
import com.rosyidputrar.taskify.core.media.MusicClient
import com.rosyidputrar.taskify.core.media.MusicPlayer
import com.rosyidputrar.taskify.core.media.PlaybackPosition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MusicTab(val label: String) { Search("Cari"), Queue("Antrean"), Lyrics("Lirik") }

data class MusicSearch(
    val query: String = "",
    val filter: SearchFilter = SearchFilter.Songs,
    val results: List<Song> = emptyList(),
    val cursor: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val searched: Boolean = false,
)

data class LyricsState(val lyricsId: String = "", val text: String? = null, val loading: Boolean = false, val error: String? = null)

data class MusicUiState(
    val configured: Boolean = true,
    val online: Boolean = true,
    val tab: MusicTab = MusicTab.Search,
    val search: MusicSearch = MusicSearch(),
    val player: YtState = YtState(),
    val position: PlaybackPosition = PlaybackPosition(),
    val lyrics: LyricsState = LyricsState(),
)

interface MusicActions {
    fun setTab(tab: MusicTab)
    fun setQuery(q: String)
    fun setFilter(f: SearchFilter)
    fun search()
    fun loadMore()
    fun play(index: Int)
    fun togglePlay()
    fun next()
    fun prev()
    fun seek(ms: Long)
    fun jump(position: Int)
    fun toggleRepeat()
    fun toggleShuffle()
    fun toggleAutoContinue()
    fun stop()
}

/** Status online (petunjuk saja; diisi app dari NetworkMonitor). */
fun interface OnlineFlow {
    fun flow(): Flow<Boolean>
}

class MusicViewModel(private val player: MusicPlayer, private val client: MusicClient, online: OnlineFlow) : ViewModel(), MusicActions {
    private val local = MutableStateFlow(MusicUiState(configured = player.configured))
    private var searchJob: Job? = null

    val state: StateFlow<MusicUiState> = combine(local, player.state, player.position, online.flow()) { l, p, pos, on -> l.copy(player = p, position = pos, online = on) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    init {
        // Lirik mengikuti lagu yang sedang diputar.
        viewModelScope.launch {
            player.state.collect { s ->
                val id = s.lyricsId
                if (id != local.value.lyrics.lyricsId) loadLyrics(id)
            }
        }
    }

    private fun loadLyrics(id: String) {
        local.update { it.copy(lyrics = LyricsState(id, if (id.isEmpty()) null else null, loading = id.isNotEmpty())) }
        if (id.isEmpty()) return
        viewModelScope.launch {
            val r = runCatching { player.lyrics(id) }
            if (local.value.lyrics.lyricsId != id) return@launch
            local.update { it.copy(lyrics = LyricsState(id, r.getOrNull(), false, r.exceptionOrNull()?.message)) }
        }
    }

    override fun setTab(tab: MusicTab) = local.update { it.copy(tab = tab) }
    override fun setQuery(q: String) = local.update { it.copy(search = it.search.copy(query = q)) }
    override fun setFilter(f: SearchFilter) { local.update { it.copy(search = it.search.copy(filter = f)) }; if (local.value.search.searched) search() }

    override fun search() {
        val s = local.value.search
        val q = s.query.trim()
        if (q.isEmpty()) return
        searchJob?.cancel()
        local.update { it.copy(tab = MusicTab.Search, search = s.copy(loading = true, error = null)) }
        searchJob = viewModelScope.launch {
            try {
                val page = client.search(q, s.filter)
                local.update { it.copy(search = it.search.copy(results = page.songs, cursor = page.cursor.takeIf { s.filter.pages }, loading = false, searched = true)) }
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
        if (s.loading) return
        local.update { it.copy(search = s.copy(loading = true)) }
        viewModelScope.launch {
            try {
                val page = client.searchMore(cursor)
                local.update { it.copy(search = it.search.copy(results = MusicQueue.mergePages(it.search.results, page.songs), cursor = page.cursor, loading = false)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(search = it.search.copy(loading = false, error = e.message)) }
            }
        }
    }

    override fun play(index: Int) = player.playQueue(local.value.search.results, index)
    override fun togglePlay() = player.togglePlay()
    override fun next() = player.next()
    override fun prev() = player.prev()
    override fun seek(ms: Long) = player.seekTo(ms)
    override fun jump(position: Int) = player.jump(position)
    override fun toggleRepeat() = player.toggleRepeat()
    override fun toggleShuffle() = player.toggleShuffle()
    override fun toggleAutoContinue() = player.toggleAutoContinue()
    override fun stop() = player.stop()
}
