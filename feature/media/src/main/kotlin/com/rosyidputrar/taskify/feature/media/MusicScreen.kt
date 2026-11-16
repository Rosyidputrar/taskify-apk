package com.rosyidputrar.taskify.feature.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.rosyidputrar.taskify.core.designsystem.component.EmptyState
import com.rosyidputrar.taskify.core.designsystem.component.ModuleHeader
import com.rosyidputrar.taskify.core.designsystem.theme.SerifFamily
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.media.MusicQueue
import com.rosyidputrar.taskify.core.domain.media.RepeatMode
import com.rosyidputrar.taskify.core.domain.media.SearchFilter
import com.rosyidputrar.taskify.core.domain.media.Song
import com.rosyidputrar.taskify.core.domain.media.YtStatus
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MusicScreen(contentPadding: PaddingValues, onBack: (() -> Unit)? = null, backButton: @Composable (() -> Unit) -> Unit = {}) {
    val vm: MusicViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    MusicContent(state, vm, contentPadding, onBack, backButton)
}

/** Gambar lagu (atau ikon catatan musik bila tak ada). */
@Composable
fun SongArt(url: String, size: Dp, modifier: Modifier = Modifier) {
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Music)
    Box(modifier.size(size).clip(RoundedCornerShape(size / 6)).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.MusicNote, null, tint = accent, modifier = Modifier.size(size / 2))
        if (url.isNotEmpty()) AsyncImage(url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun MusicContent(state: MusicUiState, actions: MusicActions, contentPadding: PaddingValues, onBack: (() -> Unit)? = null, backButton: @Composable (() -> Unit) -> Unit = {}) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "header") {
            ModuleHeader(TaskifyModule.Music, "Musik", subtitle = "Cari lagu, putar di latar, lirik", navigation = onBack?.let { { backButton(it) } })
        }
        if (!state.configured) {
            item(key = "off") { EmptyState(TaskifyModule.Music, "Pemutar musik belum diatur", "Isi MUSIC_API_URL saat build (GitHub Secrets) untuk mengaktifkan pemutar.") }
            return@LazyColumn
        }
        if (state.player.status != YtStatus.Idle) item(key = "now") { NowPlaying(state, actions) }
        item(key = "tabs") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                MusicTab.entries.forEachIndexed { i, t -> SegmentedButton(state.tab == t, { actions.setTab(t) }, SegmentedButtonDefaults.itemShape(i, MusicTab.entries.size), label = { Text(t.label) }) }
            }
        }
        when (state.tab) {
            MusicTab.Search -> searchTab(state, actions)
            MusicTab.Queue -> queueTab(state, actions)
            MusicTab.Lyrics -> item(key = "lyrics") { Lyrics(state) }
        }
    }
}

@Composable
private fun NowPlaying(state: MusicUiState, actions: MusicActions) {
    val p = state.player
    val song = p.current ?: return
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Music)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SongArt(song.cover, 220.dp)
            Text(song.title, style = MaterialTheme.typography.titleLarge.copy(fontFamily = SerifFamily), textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(MusicQueue.barStatus(p, state.online), style = MaterialTheme.typography.bodyMedium, color = if (p.status == YtStatus.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
            if (p.status == YtStatus.Preparing) LinearProgressIndicator(progress = { p.percent / 100f }, modifier = Modifier.fillMaxWidth())
            else {
                val dur = state.position.durationMs.takeIf { it > 0 } ?: ((MusicQueue.parseDuration(song.duration) ?: 0) * 1000L)
                var dragging by remember { mutableStateOf(false) }
                var dragValue by remember { mutableFloatStateOf(0f) }
                val value = if (dragging) dragValue else if (dur > 0) (state.position.positionMs.toFloat() / dur).coerceIn(0f, 1f) else 0f
                Slider(value, { dragging = true; dragValue = it }, onValueChangeFinished = { actions.seek((dragValue * dur).toLong()); dragging = false }, enabled = dur > 0)
                Row(Modifier.fillMaxWidth()) {
                    Text(MusicQueue.formatClock((value * dur / 1000).toLong()), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.weight(1f))
                    Text(MusicQueue.formatClock(dur / 1000), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = actions::toggleShuffle) { Icon(Icons.Rounded.Shuffle, contentDescription = if (p.shuffle) "Acak: hidup" else "Acak: mati", tint = if (p.shuffle) accent else MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = actions::prev, enabled = state.online) { Icon(Icons.Rounded.SkipPrevious, contentDescription = "Sebelumnya", modifier = Modifier.size(32.dp)) }
                FilledIconButton(onClick = actions::togglePlay, modifier = Modifier.size(64.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent, contentColor = TaskifyTheme.modules.onAccent(TaskifyModule.Music))) {
                    if (p.status == YtStatus.Preparing) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp, color = TaskifyTheme.modules.onAccent(TaskifyModule.Music))
                    else Icon(if (p.status == YtStatus.Playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (p.status == YtStatus.Playing) "Jeda" else "Putar", modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = actions::next, enabled = state.online) { Icon(Icons.Rounded.SkipNext, contentDescription = "Berikutnya", modifier = Modifier.size(32.dp)) }
                IconButton(onClick = actions::toggleRepeat) {
                    Icon(if (p.repeat == RepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, contentDescription = p.repeat.label,
                        tint = if (p.repeat != RepeatMode.Off) accent else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Lanjutkan otomatis dengan lagu serupa", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Switch(p.autoContinue, { actions.toggleAutoContinue() })
            }
            OutlinedButton(onClick = actions::stop) { Icon(Icons.Rounded.Close, null); Spacer(Modifier.width(6.dp)); Text("Hentikan & kosongkan antrean") }
        }
    }
}

private fun LazyListScope.searchTab(state: MusicUiState, actions: MusicActions) {
    val s = state.search
    item(key = "search") {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                s.query, actions::setQuery, placeholder = { Text("Judul lagu atau artis…") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true, shape = MaterialTheme.shapes.large, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { actions.search() }), modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchFilter.entries.forEach { f -> FilterChip(s.filter == f, { actions.setFilter(f) }, label = { Text(f.label) }) }
            }
            if (!state.online) Text("Tidak ada koneksi — pencarian & pemutaran butuh internet.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            s.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            if (s.loading && s.results.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
    if (s.searched && !s.loading && s.results.isEmpty() && s.error == null) item(key = "none") {
        EmptyState(TaskifyModule.Music, "Tidak ada hasil", "Coba kata kunci lain atau ganti filter.")
    }
    itemsIndexed(s.results, key = { _, song -> "r-" + song.videoId }) { i, song ->
        SongRow(song, playing = state.player.current?.videoId == song.videoId, onClick = { actions.play(i) })
    }
    if (s.cursor != null) item(key = "more") {
        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
            OutlinedButton(onClick = actions::loadMore, enabled = !s.loading) { Text(if (s.loading) "Memuat…" else "Muat lebih banyak") }
        }
    }
}

private fun LazyListScope.queueTab(state: MusicUiState, actions: MusicActions) {
    val list = state.player.ordered
    if (list.isEmpty()) {
        item(key = "q-empty") { EmptyState(TaskifyModule.Music, "Antrean kosong", "Putar lagu dari hasil pencarian; lagu-lagu di bawahnya ikut masuk antrean.") }
        return
    }
    itemsIndexed(list, key = { i, song -> "q-$i-" + song.videoId }) { i, song ->
        SongRow(song, playing = i == state.player.position, onClick = { actions.jump(i) }, index = i + 1)
    }
}

@Composable
fun SongRow(song: Song, playing: Boolean, onClick: () -> Unit, index: Int? = null) {
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Music)
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (index != null) Text("$index", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(28.dp))
        SongArt(song.thumb, 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.bodyLarge, color = if (playing) accent else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (playing) Icon(Icons.Rounded.GraphicEq, contentDescription = "Sedang diputar", tint = accent)
        else Text(MusicQueue.formatDuration(song.duration), style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Lyrics(state: MusicUiState) {
    val l = state.lyrics
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        when {
            state.player.current == null -> EmptyState(TaskifyModule.Music, "Belum ada lagu", "Lirik muncul di sini saat lagu diputar.")
            l.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            l.error != null -> Text(l.error, color = MaterialTheme.colorScheme.error)
            l.text.isNullOrBlank() -> Text("Lagu ini tidak punya lirik (atau belum tersedia).", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> Text(l.text, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = SerifFamily), lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.2f)
        }
    }
}

/** Bilah musik mini di atas dok (tampil di semua layar saat ada antrean). */
@Composable
fun MiniPlayer(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val vm: MusicViewModel = koinViewModel(key = "mini-player")
    val state by vm.state.collectAsStateWithLifecycle()
    if (state.player.status == YtStatus.Idle) return
    MiniPlayerBar(state, vm, onOpen, modifier)
}

@Composable
fun MiniPlayerBar(state: MusicUiState, actions: MusicActions, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val p = state.player
    val song = p.current ?: return
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Music)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(18.dp), shadowElevation = 6.dp, modifier = modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.clickable(onClickLabel = "Buka pemutar", onClick = onOpen).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SongArt(song.thumb, 40.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(MusicQueue.barStatus(p, state.online), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = actions::togglePlay) {
                    if (p.status == YtStatus.Preparing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(if (p.status == YtStatus.Playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (p.status == YtStatus.Playing) "Jeda" else "Putar", tint = accent)
                }
                IconButton(onClick = actions::next) { Icon(Icons.Rounded.SkipNext, contentDescription = "Berikutnya") }
            }
            val dur = state.position.durationMs
            if (dur > 0) LinearProgressIndicator(progress = { (state.position.positionMs.toFloat() / dur).coerceIn(0f, 1f) }, color = accent, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 4.dp))
        }
    }
}

