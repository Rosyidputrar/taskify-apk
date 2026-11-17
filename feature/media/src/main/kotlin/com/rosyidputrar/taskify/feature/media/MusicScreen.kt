package com.rosyidputrar.taskify.feature.media

import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyMotion
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.rosyidputrar.taskify.core.designsystem.component.EmptyState
import com.rosyidputrar.taskify.core.designsystem.component.FitText
import com.rosyidputrar.taskify.core.designsystem.component.ListSkeleton
import com.rosyidputrar.taskify.core.designsystem.component.ModuleHeader
import com.rosyidputrar.taskify.core.designsystem.component.TaskifyHaptics
import com.rosyidputrar.taskify.core.designsystem.component.TaskifyLoadingIndicator
import com.rosyidputrar.taskify.core.designsystem.component.rememberHaptics
import com.rosyidputrar.taskify.core.designsystem.theme.SerifFamily
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.media.BrowseTarget
import com.rosyidputrar.taskify.core.domain.media.LyricsSync
import com.rosyidputrar.taskify.core.domain.media.MusicQueue
import com.rosyidputrar.taskify.core.domain.media.RepeatMode
import com.rosyidputrar.taskify.core.domain.media.SearchFilter
import com.rosyidputrar.taskify.core.domain.media.SleepTimer
import com.rosyidputrar.taskify.core.domain.media.Song
import com.rosyidputrar.taskify.core.domain.media.StreamSource
import com.rosyidputrar.taskify.core.domain.media.Suggestion
import com.rosyidputrar.taskify.core.domain.media.YtStatus
import org.koin.compose.viewmodel.koinViewModel

/** [link] = link YouTube dari Bagikan/Spotlight yang langsung dibuka. */
@Composable
fun MusicScreen(contentPadding: PaddingValues, onBack: (() -> Unit)? = null, backButton: @Composable (() -> Unit) -> Unit = {}, link: String? = null) {
    val vm: MusicViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(link) { if (!link.isNullOrBlank()) vm.openLink(link) }
    MusicContent(state, vm, contentPadding, onBack, backButton)
}

/** Gambar lagu (atau ikon catatan musik bila tak ada). */
@Composable
fun SongArt(url: String, size: Dp, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(size / 6), live: Boolean = false) {
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Music)
    Box(modifier.size(size).clip(shape).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Icon(if (live) Icons.Rounded.Radio else Icons.Rounded.MusicNote, null, tint = accent, modifier = Modifier.size(size / 2))
        if (url.isNotEmpty()) AsyncImage(url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

/** Satu "layar" di dalam Musik: akar (tab) atau halaman koleksi pada kedalaman tumpukan tertentu. */
private data class MusicFrame(val page: BrowsePage?, val depth: Int)

/**
 * Gerak halaman dalam Musik = gerak layar aplikasi (PR animasi: pegas 0.95/420 geser, 1/420 pudar,
 * paralaks 1/3), supaya membuka album/artis terasa sama dengan membuka layar lain.
 */
private object MusicMotion {
    val offset get() = TaskifyMotion.screenOffset
    val float get() = TaskifyMotion.screenFloat
}

@Composable
fun MusicContent(state: MusicUiState, actions: MusicActions, contentPadding: PaddingValues, onBack: (() -> Unit)? = null, backButton: @Composable (() -> Unit) -> Unit = {}) {
    val haptics = rememberHaptics()
    @Suppress("NAME_SHADOWING")
    val actions = remember(actions, haptics) { HapticMusicActions(actions, haptics) }
    var settingsOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = state.page != null) { actions.back() }
    // Posisi gulir tiap halaman diingat selama halaman itu masih di tumpukan.
    val listStates = remember { mutableMapOf<String, LazyListState>() }
    val stackKeys = state.pages.map { it.target.key }
    LaunchedEffect(stackKeys) { listStates.keys.retainAll(stackKeys.toSet() + ROOT_KEY) }
    AnimatedContent(
        targetState = MusicFrame(state.page, state.pages.size),
        contentKey = { it.page?.target?.key ?: ROOT_KEY },
        transitionSpec = {
            // Halaman teratas selalu buram penuh (tanpa fade); hanya halaman di bawahnya yang meredup.
            if (targetState.depth >= initialState.depth) {
                slideInHorizontally(MusicMotion.offset) { it } togetherWith
                    (slideOutHorizontally(MusicMotion.offset) { -it / 3 } + fadeOut(MusicMotion.float, 0.4f))
            } else {
                // Kembali: halaman yang ditutup tetap di atas lalu geser keluar.
                ((slideInHorizontally(MusicMotion.offset) { -it / 3 } + fadeIn(MusicMotion.float, 0.4f)) togetherWith
                    slideOutHorizontally(MusicMotion.offset) { it }).apply { targetContentZIndex = -1f }
            }
        },
        label = "music-page",
    ) { frame ->
        val page = frame.page
        val listState = listStates.getOrPut(page?.target?.key ?: ROOT_KEY) { LazyListState() }
        LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), state = listState, contentPadding = contentPadding) {
            item(key = "header") {
                ModuleHeader(
                    // Judul koleksi tampil besar di kepala halaman; header cukup menyebut jenisnya.
                    TaskifyModule.Music, if (page != null) pageKindLabel(page.target).ifBlank { "Musik" } else "Musik",
                    subtitle = if (page != null) (if (page.loading || page.error != null) page.title.ifBlank { null } else null) else "Jelajah, cari, putar di latar, lirik",
                    navigation = when {
                        page != null -> { { backButton { actions.back() } } }
                        else -> onBack?.let { { backButton(it) } }
                    },
                    actions = { if (state.configured) IconButton(onClick = { settingsOpen = true }) { Icon(Icons.Rounded.Settings, contentDescription = "Pengaturan musik") } },
                )
            }
            if (!state.configured) {
                item(key = "off") { EmptyState(TaskifyModule.Music, "Pemutar musik belum diatur", "Isi MUSIC_API_URL saat build (GitHub Secrets) untuk mengaktifkan pemutar.") }
                return@LazyColumn
            }
            state.busy?.let { label ->
                item(key = "busy") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).animateItem()) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            if (page != null) {
                if (state.player.status != YtStatus.Idle) item(key = "page-mini") { MiniPlayerBar(state, actions, onOpen = actions::back, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) }
                pageContent(page, state, actions)
                return@LazyColumn
            }
            if (state.player.status != YtStatus.Idle) item(key = "now") { NowPlaying(state, actions, Modifier.animateItem()) }
            item(key = "tabs") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                    MusicTab.entries.forEachIndexed { i, t -> SegmentedButton(state.tab == t, { actions.setTab(t) }, SegmentedButtonDefaults.itemShape(i, MusicTab.entries.size), label = { FitText(t.label) }) }
                }
            }
            when (state.tab) {
                MusicTab.Explore -> exploreTab(state, actions)
                MusicTab.Search -> searchTab(state, actions)
                MusicTab.Queue -> queueTab(state, actions)
                MusicTab.Lyrics -> item(key = "lyrics") { Lyrics(state, actions) }
            }
        }
    }
    if (settingsOpen) MusicSettingsSheet(state, actions) { settingsOpen = false }
}

private const val ROOT_KEY = "root"

private fun pageKindLabel(t: BrowseTarget): String = when (t) {
    is BrowseTarget.Album -> "Album"
    is BrowseTarget.Playlist -> "Playlist"
    is BrowseTarget.Artist -> "Artis"
    is BrowseTarget.Podcast -> "Podcast"
    is BrowseTarget.Mood -> "Mood & genre"
    else -> ""
}

@Composable
private fun NowPlaying(state: MusicUiState, actions: MusicActions, modifier: Modifier = Modifier) {
    val p = state.player
    val song = p.current ?: return
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Music)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(24.dp), modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SongArt(song.cover, 220.dp, live = song.live)
            Text(song.title, style = MaterialTheme.typography.titleLarge.copy(fontFamily = SerifFamily), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(MusicQueue.barStatus(p, state.online), style = MaterialTheme.typography.bodyMedium, color = if (p.status == YtStatus.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
            when {
                p.status == YtStatus.Preparing -> LinearProgressIndicator(progress = { p.percent / 100f }, modifier = Modifier.fillMaxWidth())
                song.live -> Text("Siaran langsung — tidak bisa digeser", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> SeekBar(state, actions)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = actions::toggleShuffle) { Icon(Icons.Rounded.Shuffle, contentDescription = if (p.shuffle) "Acak: hidup" else "Acak: mati", tint = if (p.shuffle) accent else MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = actions::prev, enabled = state.online) { Icon(Icons.Rounded.SkipPrevious, contentDescription = "Sebelumnya", modifier = Modifier.size(32.dp)) }
                FilledIconButton(onClick = actions::togglePlay, modifier = Modifier.size(64.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent, contentColor = TaskifyTheme.modules.onAccent(TaskifyModule.Music))) {
                    if (p.status == YtStatus.Preparing) TaskifyLoadingIndicator(size = 28.dp, color = TaskifyTheme.modules.onAccent(TaskifyModule.Music))
                    else Icon(if (p.status == YtStatus.Playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (p.status == YtStatus.Playing) "Jeda" else "Putar", modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = actions::next, enabled = state.online) { Icon(Icons.Rounded.SkipNext, contentDescription = "Berikutnya", modifier = Modifier.size(32.dp)) }
                IconButton(onClick = actions::toggleRepeat) {
                    Icon(if (p.repeat == RepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, contentDescription = p.repeat.label,
                        tint = if (p.repeat != RepeatMode.Off) accent else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!song.live) OutlinedButton(onClick = { actions.startRadio(song) }) { Icon(Icons.Rounded.Radio, null); Spacer(Modifier.width(6.dp)); Text("Radio") }
                SleepButton(state.sleep, actions)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().toggleable(p.autoContinue, role = Role.Switch) { actions.toggleAutoContinue() }) {
                Text(if (p.radio != null) "Lanjutkan otomatis dari radio" else "Lanjutkan otomatis dengan lagu serupa", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Switch(p.autoContinue, onCheckedChange = null)
            }
            TextButton(onClick = actions::stop) { Icon(Icons.Rounded.Close, null); Spacer(Modifier.width(6.dp)); Text("Hentikan & kosongkan antrean") }
        }
    }
}

@Composable
private fun SeekBar(state: MusicUiState, actions: MusicActions) {
    val p = state.player
    val song = p.current ?: return
    val dur = state.position.durationMs.takeIf { it > 0 } ?: ((song.seconds ?: 0) * 1000L)
    val seekable = p.seekable && dur > 0
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val value = if (dragging) dragValue else if (dur > 0) (state.position.positionMs.toFloat() / dur).coerceIn(0f, 1f) else 0f
    Slider(value, { dragging = true; dragValue = it }, onValueChangeFinished = { actions.seek((dragValue * dur).toLong()); dragging = false }, enabled = seekable,
        modifier = Modifier.semantics { if (!seekable) contentDescription = "Posisi lagu (tidak bisa digeser)" })
    Row(Modifier.fillMaxWidth()) {
        Text(MusicQueue.formatClock((value * dur / 1000).toLong()), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.weight(1f))
        // Durasi tidak diketahui (null) → tanpa total, bukan "0:00".
        if (dur > 0) Text(MusicQueue.formatClock(dur / 1000), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
    }
    if (!p.seekable && p.currentStream?.source == StreamSource.SOURCE_CONVERTER) {
        Text("Audio dari konverter tidak bisa digeser" + if (state.settings.archive) " — menunggu arsip…" else "",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SleepButton(sleep: SleepTimer?, actions: MusicActions) {
    var open by remember { mutableStateOf(false) }
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Music)
    Box {
        OutlinedButton(onClick = { open = true }) {
            Icon(Icons.Rounded.Bedtime, null, tint = if (sleep != null) accent else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(sleep?.label(System.currentTimeMillis())?.removePrefix("Berhenti ")?.replaceFirstChar { it.uppercase() } ?: "Pewaktu tidur", maxLines = 1)
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            SleepTimer.OPTIONS.forEach { m -> DropdownMenuItem(text = { Text("$m menit") }, onClick = { open = false; actions.setSleep(m) }) }
            DropdownMenuItem(text = { Text("Akhir lagu ini") }, onClick = { open = false; actions.sleepEndOfSong() })
            if (sleep != null) DropdownMenuItem(text = { Text("Matikan pewaktu") }, onClick = { open = false; actions.setSleep(null) })
        }
    }
}

private fun LazyListScope.searchTab(state: MusicUiState, actions: MusicActions) {
    val s = state.search
    item(key = "search") {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                s.query, actions::setQuery, placeholder = { Text("Judul, artis, atau tempel link YouTube…") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = { if (s.query.isNotEmpty()) IconButton(onClick = { actions.setQuery("") }) { Icon(Icons.Rounded.Close, contentDescription = "Hapus teks") } },
                singleLine = true, shape = MaterialTheme.shapes.large, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { actions.search() }), modifier = Modifier.fillMaxWidth(),
            )
            if (s.suggestions.isNotEmpty()) Suggestions(s.suggestions, actions)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchFilter.entries.forEach { f -> FilterChip(s.filter == f, { actions.setFilter(f) }, label = { Text(f.label) }) }
            }
            if (!state.online) Text("Tidak ada koneksi — pencarian & pemutaran butuh internet.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            s.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        }
    }
    if (s.loading) { item(key = "s-skeleton") { ListSkeleton(TaskifyModule.Music, rows = 5, label = "Mencari…") }; return }
    val r = s.results
    if (s.searched && r.isEmpty && s.error == null) item(key = "none") {
        EmptyState(TaskifyModule.Music, "Tidak ada hasil", "Coba kata kunci lain atau ganti filter.")
    }
    val all = s.filter == SearchFilter.All
    r.top?.takeIf { all }?.let { top -> item(key = "top") { TopResultCard(top, actions) } }
    val songs = r.songs
    if (songs.isNotEmpty()) {
        if (all) item(key = "h-songs") { SectionTitle("Lagu & video") }
        itemsIndexed(songs, key = { _, song -> "r-" + song.videoId }) { i, song ->
            SongRow(song, playing = state.player.current?.videoId == song.videoId, onClick = { actions.play(i) }, onRadio = { actions.startRadio(song) }, modifier = Modifier.animateItem())
        }
    }
    if (r.albums.isNotEmpty()) cardRow("albums", if (all) "Album" else null, r.albums.map { a -> CardItem(a.browseId, a.title, listOf(a.artist, a.year).filter { it.isNotBlank() }.joinToString(" · "), a.cover) { actions.open(BrowseTarget.Album(a.browseId, a.title)) } }, grid = !all)
    if (r.artists.isNotEmpty()) cardRow("artists", if (all) "Artis" else null, r.artists.map { a -> CardItem(a.browseId, a.name, a.subscribers, a.cover, circle = true) { actions.open(BrowseTarget.Artist(a.browseId, a.name)) } }, grid = !all)
    if (r.playlists.isNotEmpty()) cardRow("playlists", if (all) "Playlist" else null, r.playlists.map { p -> CardItem(p.browseId, p.title, p.author, p.cover) { com.rosyidputrar.taskify.core.domain.media.MusicCatalog.route(p)?.let(actions::open) } }, grid = !all)
    if (r.podcasts.isNotEmpty()) cardRow("podcasts", if (all) "Podcast" else null, r.podcasts.map { p -> CardItem(p.browseId, p.title, p.author, p.cover) { actions.open(BrowseTarget.Podcast(p.browseId, p.title)) } }, grid = !all)
    if (r.episodes.isNotEmpty()) {
        if (all) item(key = "h-episodes") { SectionTitle("Episode") }
        itemsIndexed(r.episodes, key = { _, e -> "e-" + e.videoId }) { _, e ->
            SongRow(e, playing = state.player.current?.videoId == e.videoId, onClick = { actions.playSong(e) }, modifier = Modifier.animateItem())
        }
    }
    if (s.cursor != null) item(key = "more") {
        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
            OutlinedButton(onClick = actions::loadMore, enabled = !s.loadingMore) { Text(if (s.loadingMore) "Memuat…" else "Muat lebih banyak") }
        }
    }
}

@Composable
private fun Suggestions(list: List<Suggestion>, actions: MusicActions) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 4.dp)) {
            list.forEach { s ->
                Row(Modifier.fillMaxWidth().clickable { actions.pickSuggestion(s) }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(when (s.type) { "artist" -> Icons.Rounded.Person; "video" -> Icons.Rounded.MusicNote; else -> Icons.Outlined.Search }, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (s.subtitle.isNotBlank()) Text(s.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (s.type == "query") Icon(Icons.Rounded.NorthWest, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

private fun LazyListScope.queueTab(state: MusicUiState, actions: MusicActions) {
    val list = state.player.ordered
    if (list.isEmpty()) {
        item(key = "q-empty") { EmptyState(TaskifyModule.Music, "Antrean kosong", "Putar lagu dari Jelajah atau hasil pencarian; lagu-lagu di bawahnya ikut masuk antrean.") }
        return
    }
    item(key = "q-total") {
        val (total, complete) = MusicQueue.knownTotalSeconds(list.drop(state.player.position))
        val radio = state.player.radio
        Text(listOf("${list.size} lagu", if (total > 0) (if (complete) "" else "±") + com.rosyidputrar.taskify.core.domain.media.MusicCatalog.formatLong(total) + " tersisa" else "",
            if (radio != null) "dari radio" else "").filter { it.isNotEmpty() }.joinToString(" · "),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
    }
    itemsIndexed(list, key = { i, song -> "q-$i-" + song.videoId }) { i, song ->
        SongRow(song, playing = i == state.player.position, onClick = { actions.jump(i) }, index = i + 1, modifier = Modifier.animateItem())
    }
}

@Composable
fun SongRow(song: Song, playing: Boolean, onClick: () -> Unit, index: Int? = null, onRadio: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Music)
    var menu by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 20.dp, end = if (onRadio != null) 4.dp else 20.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (index != null) Text("$index", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp))
        SongArt(song.thumb, 48.dp, live = song.live)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.bodyLarge, color = if (playing) accent else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (playing) Icon(Icons.Rounded.GraphicEq, contentDescription = "Sedang diputar", tint = accent)
        else Text(MusicQueue.formatDuration(song), style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onRadio != null) Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Opsi ${song.title}") }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Mulai radio dari lagu ini") }, leadingIcon = { Icon(Icons.Rounded.Radio, null) }, onClick = { menu = false; onRadio() })
            }
        }
    }
}

@Composable
private fun Lyrics(state: MusicUiState, actions: MusicActions) {
    val l = state.lyrics
    val song = state.player.current
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        when {
            song == null -> EmptyState(TaskifyModule.Music, "Belum ada lagu", "Lirik muncul di sini saat lagu diputar.")
            song.live -> Text("Siaran radio langsung tidak punya lirik.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            l.loading -> ListSkeleton(TaskifyModule.Music, rows = 5, lines = 1, label = "Memuat lirik…")
            l.error != null -> Text(l.error, color = MaterialTheme.colorScheme.error)
            l.lyrics?.instrumental == true -> Text("Lagu instrumental — tanpa lirik.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            l.lyrics?.hasSynced == true -> SyncedLyrics(state, actions)
            l.lyrics?.plain.isNullOrBlank() -> Text("Lagu ini tidak punya lirik (atau belum tersedia).", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> Column {
                Text(l.lyrics!!.plain, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = SerifFamily), lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.2f)
                LyricsSource(l.lyrics.source)
            }
        }
    }
}

@Composable
private fun LyricsSource(source: String) {
    Text("Sumber lirik: " + if (source == "youtube") "YouTube" else "LRCLib", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp))
}

/** Lirik bergulir: baris aktif disorot & digulir ke tengah; ketuk baris → lompat (bila bisa di-seek). */
@Composable
private fun SyncedLyrics(state: MusicUiState, actions: MusicActions) {
    val lines = state.lyrics.lyrics?.synced.orEmpty()
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Music)
    val active = LyricsSync.activeIndex(lines, state.position.positionMs / 1000.0)
    val list = rememberLazyListState()
    LaunchedEffect(active) { if (active >= 0) list.animateScrollToItem((active - 2).coerceAtLeast(0)) }
    val seekable = state.player.seekable
    Column {
        LazyColumn(Modifier.fillMaxWidth().height(440.dp), state = list) {
            itemsIndexed(lines, key = { i, _ -> "l-$i" }) { i, line ->
                val isActive = i == active
                Text(
                    line.text.ifBlank { "♪" },
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = SerifFamily, fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal),
                    color = when {
                        isActive -> accent
                        i < active -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.fillMaxWidth()
                        .then(if (seekable) Modifier.clickable(onClickLabel = "Putar dari baris ini") { actions.seekLyric(line.t) } else Modifier)
                        .padding(vertical = 6.dp),
                )
            }
        }
        if (!seekable) Text("Ketuk baris untuk melompat tersedia saat audio bisa digeser.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LyricsSource(state.lyrics.lyrics?.source.orEmpty())
    }
}

/** Bilah musik mini di atas dok (tampil di semua layar saat ada antrean). */
@Composable
fun MiniPlayer(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val vm: MusicViewModel = koinViewModel(key = "mini-player")
    val state by vm.state.collectAsStateWithLifecycle()
    // Simpan keadaan terakhir yang aktif: saat musik dihentikan, bilah tetap berisi lagu terakhir
    // selama animasi turunnya (bukan kosong mendadak).
    var last by remember { mutableStateOf<MusicUiState?>(null) }
    if (state.player.status != YtStatus.Idle) last = state
    val shown = last ?: return
    MiniPlayerBar(shown, vm, onOpen, modifier)
}

@Composable
fun MiniPlayerBar(state: MusicUiState, actions: MusicActions, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val p = state.player
    val song = p.current ?: return
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Music)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(18.dp), shadowElevation = 6.dp, modifier = modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.clickable(onClickLabel = "Buka pemutar", onClick = onOpen).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SongArt(song.thumb, 40.dp, live = song.live)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(MusicQueue.barStatus(p, state.online), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = actions::togglePlay) {
                    if (p.status == YtStatus.Preparing) TaskifyLoadingIndicator(size = 24.dp, module = TaskifyModule.Music)
                    else Icon(if (p.status == YtStatus.Playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (p.status == YtStatus.Playing) "Jeda" else "Putar", tint = accent)
                }
                IconButton(onClick = actions::next) { Icon(Icons.Rounded.SkipNext, contentDescription = "Berikutnya") }
            }
            val dur = state.position.durationMs
            if (dur > 0 && !song.live) LinearProgressIndicator(progress = { (state.position.positionMs.toFloat() / dur).coerceIn(0f, 1f) }, color = accent, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 4.dp))
        }
    }
}

/** Getar halus per makna (lihat [TaskifyHaptics]) tanpa mengubah logika aksi. */
private class HapticMusicActions(private val a: MusicActions, private val h: TaskifyHaptics) : MusicActions by a {
    override fun setTab(tab: MusicTab) { h.tick(); a.setTab(tab) }
    override fun setFilter(f: SearchFilter) { h.tick(); a.setFilter(f) }
    override fun play(index: Int) { h.tap(); a.play(index) }
    override fun playSongs(songs: List<Song>, index: Int) { h.tap(); a.playSongs(songs, index) }
    override fun playSong(song: Song) { h.tap(); a.playSong(song) }
    override fun playPage(shuffle: Boolean) { h.tap(); a.playPage(shuffle) }
    override fun playStation(station: com.rosyidputrar.taskify.core.domain.media.RadioStation) { h.tap(); a.playStation(station) }
    override fun togglePlay() { h.tap(); a.togglePlay() }
    override fun next() { h.tick(); a.next() }
    override fun prev() { h.tick(); a.prev() }
    override fun toggleRepeat() { h.tick(); a.toggleRepeat() }
    override fun toggleShuffle() { h.tick(); a.toggleShuffle() }
    override fun toggleAutoContinue() { h.tick(); a.toggleAutoContinue() }
    override fun open(target: BrowseTarget) { h.tick(); a.open(target) }
}
