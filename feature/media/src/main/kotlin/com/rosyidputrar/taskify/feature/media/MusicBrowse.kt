package com.rosyidputrar.taskify.feature.media

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.designsystem.component.EmptyState
import com.rosyidputrar.taskify.core.designsystem.component.IndexCard
import com.rosyidputrar.taskify.core.designsystem.component.ListSkeleton
import com.rosyidputrar.taskify.core.designsystem.component.SkeletonLine
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.rosyidputrar.taskify.core.designsystem.theme.SerifFamily
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.media.BrowseTarget
import com.rosyidputrar.taskify.core.domain.media.CollectionKind
import com.rosyidputrar.taskify.core.domain.media.DiscoverySection
import com.rosyidputrar.taskify.core.domain.media.MusicCatalog
import com.rosyidputrar.taskify.core.domain.media.RadioStation
import com.rosyidputrar.taskify.core.domain.media.TopResult

/** Satu kartu di baris geser (album, artis, playlist, podcast, lagu, stasiun). */
data class CardItem(val key: String, val title: String, val subtitle: String, val image: String, val circle: Boolean = false, val live: Boolean = false, val onClick: () -> Unit)

/** Judul seksi gaya "Kertas & Tinta" yang sama dengan modul lain (label indeks huruf besar, aksen modul). */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = TaskifyTextStyles.indexLabel, color = TaskifyTheme.modules.accent(TaskifyModule.Music),
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp).semantics { heading() })
}

@Composable
private fun MediaCard(item: CardItem) {
    Column(Modifier.width(140.dp).clickable(onClickLabel = "Buka ${item.title}", onClick = item.onClick), horizontalAlignment = if (item.circle) Alignment.CenterHorizontally else Alignment.Start) {
        SongArt(item.image, 140.dp, shape = if (item.circle) CircleShape else RoundedCornerShape(16.dp), live = item.live)
        Spacer(Modifier.padding(top = 6.dp))
        Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = if (item.circle) TextAlign.Center else TextAlign.Start)
        if (item.subtitle.isNotBlank()) Text(item.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CardListRow(item: CardItem, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clickable(onClick = item.onClick).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        SongArt(item.image, 56.dp, shape = if (item.circle) CircleShape else RoundedCornerShape(10.dp), live = item.live)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (item.subtitle.isNotBlank()) Text(item.subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Baris kartu geser berjudul; [grid] = tampilkan sebagai daftar (filter jenis tertentu). */
fun LazyListScope.cardRow(key: String, title: String?, items: List<CardItem>, grid: Boolean = false) {
    if (items.isEmpty()) return
    if (title != null) item(key = "h-$key") { SectionTitle(title) }
    if (grid) items(items, key = { "$key-${it.key}" }) { CardListRow(it, Modifier.animateItem()) }
    else item(key = "row-$key") {
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.key }) { MediaCard(it) }
        }
    }
}

@Composable
fun TopResultCard(top: TopResult, actions: MusicActions) {
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Music)
    IndexCard(TaskifyModule.Music, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), onClick = { actions.playTop(top) },
        onClickLabel = if (top.playable) "Putar" else "Buka", tinted = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SongArt(top.cover, 88.dp, shape = if (top.kind?.lowercase() == "artis") CircleShape else RoundedCornerShape(14.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("HASIL TERATAS", style = TaskifyTextStyles.indexLabel, color = accent)
                Text(top.title, style = MaterialTheme.typography.titleMedium.copy(fontFamily = SerifFamily), maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(top.kind, top.byline.ifBlank { null }).joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (top.playable) Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = accent)
        }
    }
}

/** Kartu untuk seluruh rak Jelajah/mood; lagu diputar sebagai antrean rak itu. */
fun LazyListScope.discovery(prefix: String, sections: List<DiscoverySection>, actions: MusicActions) {
    sections.forEachIndexed { i, section ->
        val key = "$prefix-$i"
        when (section) {
            is DiscoverySection.Songs -> cardRow(key, section.title, section.items.map { s ->
                CardItem(s.videoId, s.title, s.artist, s.cover) { actions.playSong(s) }
            })
            is DiscoverySection.Albums -> cardRow(key, section.title, section.items.map { a ->
                CardItem(a.browseId, a.title, listOf(a.artist, a.year).filter { it.isNotBlank() }.joinToString(" · "), a.cover) { actions.open(BrowseTarget.Album(a.browseId, a.title)) }
            })
            is DiscoverySection.Artists -> cardRow(key, section.title, section.items.map { a ->
                CardItem(a.browseId, a.name, a.subscribers, a.cover, circle = true) { actions.open(BrowseTarget.Artist(a.browseId, a.name)) }
            })
            is DiscoverySection.Playlists -> cardRow(key, section.title, section.items.map { p ->
                CardItem(p.browseId.ifEmpty { p.playlistId }, p.title, p.author, p.cover) { MusicCatalog.route(p)?.let(actions::open) }
            })
        }
    }
}

fun LazyListScope.exploreTab(state: MusicUiState, actions: MusicActions) {
    val e = state.explore
    if (!state.online && !e.loaded) {
        item(key = "x-off") { EmptyState(TaskifyModule.Music, "Tidak ada koneksi", "Jelajah butuh internet. Stasiun radio tetap bisa dicoba saat koneksi kembali.") }
        return
    }
    if (e.loading && !e.loaded) { item(key = "x-load") { ExploreSkeleton() }; return }
    e.error?.let { err ->
        item(key = "x-err") {
            EmptyState(TaskifyModule.Music, "Jelajah belum bisa dimuat", err, action = { OutlinedButton(onClick = { actions.loadExplore(true) }) { Text("Coba lagi") } })
        }
    }
    e.moods.forEachIndexed { gi, group ->
        item(key = "x-mood-$gi") {
            Column {
                SectionTitle(group.title.ifBlank { "Mood & genre" })
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    group.items.forEach { m -> AssistChip(onClick = { actions.open(BrowseTarget.Mood(m.params, m.title)) }, label = { Text(m.title) }) }
                }
            }
        }
    }
    if (e.stations.isNotEmpty()) stationRow("x-radio", "Radio langsung", e.stations, state, actions)
    discovery("x-home", e.home, actions)
    discovery("x-charts", e.charts.map { s -> retitle(s, "Tangga lagu · ") }, actions)
    discovery("x-new", e.releases, actions)
}

private fun retitle(s: DiscoverySection, prefix: String): DiscoverySection = when (s) {
    is DiscoverySection.Songs -> s.copy(title = prefix + s.title)
    is DiscoverySection.Albums -> s.copy(title = prefix + s.title)
    is DiscoverySection.Artists -> s.copy(title = prefix + s.title)
    is DiscoverySection.Playlists -> s.copy(title = prefix + s.title)
}

fun LazyListScope.stationRow(key: String, title: String, stations: List<RadioStation>, state: MusicUiState, actions: MusicActions) {
    cardRow(key, title, stations.map { st ->
        val on = state.player.current?.videoId == st.toSong().videoId
        CardItem(st.id, st.name, (if (on) "Sedang diputar · " else "") + st.moodLabel + " · " + st.provider, "", live = true) {
            if (on) actions.togglePlay() else actions.playStation(st)
        }
    })
}

/** Isi halaman koleksi/artis/mood yang sedang dibuka. */
fun LazyListScope.pageContent(page: BrowsePage, state: MusicUiState, actions: MusicActions) {
    when {
        page.loading -> { item(key = "p-load") { ListSkeleton(TaskifyModule.Music, rows = 6, label = "Memuat…") }; return }
        page.error != null -> {
            item(key = "p-err") { EmptyState(TaskifyModule.Music, "Gagal memuat", page.error, action = { OutlinedButton(onClick = actions::retryPage) { Text("Coba lagi") } }) }
            return
        }
    }
    page.collection?.let { c ->
        item(key = "p-head") {
            PageHead(c.cover, c.title, MusicCatalog.subtitle(c), c.description, circle = false, playLabel = if (c.kind == CollectionKind.Podcast) "Putar terbaru" else "Putar",
                canPlay = c.tracks.isNotEmpty(), actions = actions)
        }
        itemsIndexed(c.tracks, key = { _, s -> "p-t-" + s.videoId }) { i, s ->
            SongRow(s, playing = state.player.current?.videoId == s.videoId, onClick = { actions.playSongs(c.tracks, i) }, index = if (c.kind == CollectionKind.Album) i + 1 else null,
                onRadio = if (c.kind == CollectionKind.Podcast) null else ({ actions.startRadio(s) }), modifier = Modifier.animateItem())
        }
        if (c.tracks.isEmpty()) item(key = "p-empty") { EmptyState(TaskifyModule.Music, "Kosong", "Belum ada lagu di sini.") }
        if (c.cursor != null) item(key = "p-more") {
            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                OutlinedButton(onClick = actions::loadMorePage, enabled = !page.loadingMore) { Text(if (page.loadingMore) "Memuat…" else "Muat lebih banyak") }
            }
        }
    }
    page.artist?.let { a ->
        item(key = "p-head") {
            PageHead(a.cover, a.name, listOf(a.monthlyListeners, a.subscribers.takeIf { a.monthlyListeners.isBlank() }.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "),
                a.description, circle = true, playLabel = "Putar lagu teratas", canPlay = a.topSongs.isNotEmpty() || a.topSongsPlaylistId != null, actions = actions,
                radio = a.topSongs.firstOrNull()?.let { s -> { actions.startRadio(s) } })
        }
        if (a.topSongs.isNotEmpty()) {
            item(key = "p-h-top") { SectionTitle("Lagu teratas") }
            itemsIndexed(a.topSongs, key = { _, s -> "p-top-" + s.videoId }) { i, s ->
                SongRow(s, playing = state.player.current?.videoId == s.videoId, onClick = { actions.playSongs(a.topSongs, i) }, onRadio = { actions.startRadio(s) }, modifier = Modifier.animateItem())
            }
        }
        cardRow("p-albums", "Album", a.albums.map { al -> CardItem(al.browseId, al.title, al.year, al.cover) { actions.open(BrowseTarget.Album(al.browseId, al.title)) } })
        cardRow("p-singles", "Single & EP", a.singles.map { al -> CardItem(al.browseId, al.title, al.year, al.cover) { actions.open(BrowseTarget.Album(al.browseId, al.title)) } })
        cardRow("p-related", "Artis serupa", a.related.map { r -> CardItem(r.browseId, r.name, r.subscribers, r.cover, circle = true) { actions.open(BrowseTarget.Artist(r.browseId, r.name)) } })
    }
    page.mood?.let { m ->
        if (m.sections.isEmpty()) item(key = "p-empty") { EmptyState(TaskifyModule.Music, "Kosong", "Belum ada playlist untuk kategori ini.") }
        discovery("p-mood", m.sections, actions)
    }
}

@Composable
private fun PageHead(cover: String, title: String, subtitle: String, description: String, circle: Boolean, playLabel: String, canPlay: Boolean, actions: MusicActions, radio: (() -> Unit)? = null) {
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Music)
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SongArt(cover, 180.dp, shape = if (circle) CircleShape else RoundedCornerShape(20.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = SerifFamily), textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        if (canPlay) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { actions.playPage(false) }, colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = TaskifyTheme.modules.onAccent(TaskifyModule.Music))) {
                Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(playLabel)
            }
            OutlinedButton(onClick = { actions.playPage(true) }) { Icon(Icons.Rounded.Shuffle, contentDescription = "Acak") }
            if (radio != null) OutlinedButton(onClick = radio) { Icon(Icons.Rounded.Radio, contentDescription = "Radio artis") }
        }
    }
}

/** Kerangka Jelajah saat memuat: baris chip + dua rak kartu (bentuknya sama dengan isi aslinya). */
@Composable
private fun ExploreSkeleton() {
    Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "Memuat Jelajah…" }) {
        repeat(2) {
            SkeletonLine(Modifier.padding(start = 20.dp, top = 18.dp, bottom = 10.dp), widthFraction = 0.35f)
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(3) {
                    Column(Modifier.width(140.dp)) {
                        Box(Modifier.size(140.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh))
                        SkeletonLine(Modifier.padding(top = 8.dp), widthFraction = 0.8f)
                        SkeletonLine(Modifier.padding(top = 6.dp), widthFraction = 0.5f, height = 10.dp)
                    }
                }
            }
        }
    }
}
