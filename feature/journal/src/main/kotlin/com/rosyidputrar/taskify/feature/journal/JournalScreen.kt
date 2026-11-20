package com.rosyidputrar.taskify.feature.journal

import com.rosyidputrar.taskify.core.designsystem.component.SwipeToDelete
import com.rosyidputrar.taskify.core.domain.journal.JournalEntry
import androidx.compose.runtime.remember
import com.rosyidputrar.taskify.core.designsystem.component.rememberHaptics
import com.rosyidputrar.taskify.core.designsystem.component.TaskifyHaptics
import com.rosyidputrar.taskify.core.designsystem.component.ListSkeleton
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rosyidputrar.taskify.core.designsystem.component.EmptyState
import com.rosyidputrar.taskify.core.designsystem.component.IndexCard
import com.rosyidputrar.taskify.core.designsystem.component.ModuleHeader
import com.rosyidputrar.taskify.core.designsystem.component.MoodIcons
import com.rosyidputrar.taskify.core.designsystem.component.moodColor
import com.rosyidputrar.taskify.core.designsystem.component.moodIcon
import com.rosyidputrar.taskify.core.designsystem.theme.SerifFamily
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.journal.MOOD_LABELS
import com.rosyidputrar.taskify.core.domain.journal.agoLabel
import com.rosyidputrar.taskify.core.domain.journal.promptFor
import com.rosyidputrar.taskify.core.domain.text.Markdown
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.feature.journal.component.JournalCard
import com.rosyidputrar.taskify.feature.journal.component.MoodDistribution
import com.rosyidputrar.taskify.feature.journal.component.MoodLineChart
import com.rosyidputrar.taskify.feature.journal.component.WeekdayMood
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun JournalScreen(
    contentPadding: PaddingValues,
    onOpenEntry: (String) -> Unit,
    onNewEntry: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    backButton: @Composable (() -> Unit) -> Unit = {},
    viewModel: JournalViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    JournalContent(state, viewModel, contentPadding, onOpenEntry, onNewEntry, onBack, backButton)
}

@Composable
fun JournalContent(
    state: JournalUiState,
    actions: JournalActions,
    contentPadding: PaddingValues,
    onOpenEntry: (String) -> Unit,
    onNewEntry: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    backButton: @Composable (() -> Unit) -> Unit = {},
) {
    val haptics = rememberHaptics()
    @Suppress("NAME_SHADOWING")
    val actions = remember(actions, haptics) { HapticJournalActions(actions, haptics) }
    val trash = state.filter.trash
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            ModuleHeader(
                TaskifyModule.Journal,
                if (trash) "Sampah jurnal" else "Jurnal",
                subtitle = if (trash) "Entri yang dihapus bisa dipulihkan" else state.subtitle(),
                navigation = onBack?.let { { backButton(it) } },
                actions = {
                    IconButton(onClick = actions::toggleTrash) {
                        Icon(if (trash) Icons.Rounded.Close else Icons.Outlined.DeleteOutline, contentDescription = if (trash) "Tutup Sampah" else "Sampah jurnal")
                    }
                },
            )
        }
        if (!trash) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                    JournalTab.entries.forEachIndexed { i, tab ->
                        SegmentedButton(
                            selected = state.tab == tab,
                            onClick = { actions.setTab(tab) },
                            shape = SegmentedButtonDefaults.itemShape(i, JournalTab.entries.size),
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        }
        if (state.tab == JournalTab.Insight && !trash) insight(state, actions, onOpenEntry) else shelf(state, actions, onOpenEntry, onNewEntry)
    }
}

private fun LazyListScope.shelf(state: JournalUiState, actions: JournalActions, onOpenEntry: (String) -> Unit, onNewEntry: (String) -> Unit) {
    val trash = state.filter.trash
    if (!trash) item(key = "today") { TodayPage(state, onNewEntry) }
    item(key = "search") {
        OutlinedTextField(
            value = state.filter.search,
            onValueChange = actions::setSearch,
            placeholder = { Text("Cari di judul & isi…") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = if (state.filter.search.isNotEmpty()) {
                { IconButton(onClick = { actions.setSearch("") }) { Icon(Icons.Rounded.Close, contentDescription = "Hapus pencarian") } }
            } else null,
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
    item(key = "moods") {
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(selected = state.filter.mood == null, onClick = { actions.setMood(null) }, label = { Text("Semua mood") }, leadingIcon = { Icon(Icons.Outlined.Mood, null, Modifier.size(18.dp)) })
            }
            items(5) { i ->
                val mood = i + 1
                val color = moodColor(mood)
                FilterChip(
                    selected = state.filter.mood == mood,
                    onClick = { actions.setMood(if (state.filter.mood == mood) null else mood) },
                    label = { Text(MOOD_LABELS[i]) },
                    leadingIcon = { Icon(MoodIcons[i], null, Modifier.size(18.dp), tint = color) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color.copy(alpha = 0.18f), selectedLabelColor = MaterialTheme.colorScheme.onSurface),
                )
            }
        }
    }
    if (!state.loaded) item(key = "skeleton") { ListSkeleton(TaskifyModule.Journal, rows = 4, lines = 2) }
    if (state.loaded && state.isEmpty) {
        item(key = "empty") {
            val filtered = state.filter.search.isNotEmpty() || state.filter.mood != null
            EmptyState(
                TaskifyModule.Journal,
                title = when { trash -> "Sampah kosong"; filtered -> "Tidak ada yang cocok"; else -> "Rak masih kosong" },
                message = when {
                    trash -> "Entri jurnal yang dihapus muncul di sini dan bisa dipulihkan."
                    filtered -> "Coba kata lain atau lepas filter mood."
                    else -> "Tulis entri pertamamu. Satu tanggal boleh punya banyak entri."
                },
            )
        }
    }
    if (state.pinned.isNotEmpty()) {
        stickyHeading("pinned", "Disematkan", state.pinned.size)
        items(state.pinned, key = { "p-${it.id}" }) { entry ->
            SwipeToDelete(onDelete = { actions.remove(entry) }, enabled = !trash, label = "Pindahkan ke Sampah", modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp).animateItem()) {
                JournalCard(entry, state.today, onOpen = { onOpenEntry(entry.id) })
            }
        }
    }
    state.groups.forEach { group ->
        stickyHeading(group.key, group.label, group.entries.size)
        items(group.entries, key = { it.id }) { entry ->
            // Geser ke kiri → Sampah (tidak berlaku di tampilan Sampah sendiri).
            SwipeToDelete(onDelete = { actions.remove(entry) }, enabled = !trash, label = "Pindahkan ke Sampah", modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp).animateItem()) {
                JournalCard(
                    entry,
                    state.today,
                    onOpen = { onOpenEntry(entry.id) },
                    onRestore = if (trash) ({ actions.restore(entry) }) else null,
                )
            }
        }
    }
}

private fun LazyListScope.stickyHeading(key: String, label: String, count: Int) {
    stickyHeader(key = "h-$key") {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label.uppercase(), style = TaskifyTextStyles.indexLabel, color = TaskifyTheme.modules.accent(TaskifyModule.Journal), modifier = Modifier.semantics { heading() })
            Spacer(Modifier.weight(1f))
            Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "Halaman hari ini": pancingan menulis + tombol mulai; berubah jadi "tulis lagi" bila sudah ada entri. */
@Composable
private fun TodayPage(state: JournalUiState, onNewEntry: (String) -> Unit) {
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Journal)
    IndexCard(
        TaskifyModule.Journal,
        tinted = true,
        onClick = { onNewEntry(state.today) },
        onClickLabel = "Tulis entri hari ini",
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text("HALAMAN ${Dates.format(state.today, "EEEE, d MMMM").uppercase()}", style = TaskifyTextStyles.indexLabel, color = accent)
        Text(
            promptFor(state.today),
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = SerifFamily, fontStyle = FontStyle.Italic),
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (state.todayCount > 0) "${state.todayCount} entri hari ini" else "Belum menulis hari ini",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { onNewEntry(state.today) }) {
                Icon(Icons.Outlined.EditNote, null, Modifier.size(18.dp))
                Text(if (state.todayCount > 0) "Tulis lagi" else "Mulai menulis", Modifier.padding(start = 6.dp))
            }
        }
    }
}

private fun LazyListScope.insight(state: JournalUiState, actions: JournalActions, onOpenEntry: (String) -> Unit) {
    val ins = state.insight
    val stats = ins.stats
    item(key = "stats") {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Streak", "${state.streak} hari", Icons.Outlined.LocalFireDepartment, Modifier.weight(1f))
            StatTile("Menulis", "${stats?.daysWritten ?: 0}/${ins.range} hari", Icons.Outlined.EditNote, Modifier.weight(1f))
            StatTile("Rata-rata", stats?.averageLabel() ?: "–", moodIcon(stats?.average?.let { Math.round(it).toInt() }) ?: Icons.Outlined.Mood, Modifier.weight(1f))
        }
    }
    item(key = "range") {
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ALL_RANGES.forEach { days ->
                FilterChip(selected = ins.range == days, onClick = { actions.setRange(days) }, label = { Text("$days hari") })
            }
        }
    }
    item(key = "chart") {
        InsightCard("Mood ${ins.range} hari", if ((stats?.daysWithMood ?: 0) > 0) "${stats!!.daysWithMood} hari tercatat" else "Pilih mood saat menulis untuk melihat tren.") {
            MoodLineChart(ins.series)
        }
    }
    if (stats != null && stats.daysWithMood > 0) {
        item(key = "dist") { InsightCard("Sebaran mood", "Jumlah hari per mood") { MoodDistribution(stats.distribution) } }
        item(key = "weekday") { InsightCard("Mood per hari", "Rata-rata menurut hari dalam minggu") { WeekdayMood(stats.byWeekday) } }
    }
    if (ins.onThisDay.isNotEmpty()) {
        item(key = "past") {
            InsightCard("Hari ini di masa lalu", null, icon = Icons.Outlined.History) {
                Column {
                    ins.onThisDay.forEach { e ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenEntry(e.id) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(moodIcon(e.mood) ?: Icons.Outlined.EditNote, null, tint = moodColor(e.mood), modifier = Modifier.size(20.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${agoLabel(e.entryDate, state.today)} · ${Dates.format(e.entryDate, "d MMM yyyy")}", style = MaterialTheme.typography.labelMedium, color = TaskifyTheme.modules.accent(TaskifyModule.Journal))
                                Text(e.title ?: Markdown.plainExcerpt(e.content, 80).ifEmpty { "Tanpa judul" }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = TaskifyTheme.modules.accent(TaskifyModule.Journal), modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun InsightCard(title: String, subtitle: String?, icon: ImageVector? = null, content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                icon?.let { Icon(it, null, tint = TaskifyTheme.modules.accent(TaskifyModule.Journal), modifier = Modifier.size(18.dp)) }
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            content()
        }
    }
}

/** Getar halus per makna (lihat [TaskifyHaptics]) tanpa mengubah logika aksi. */
private class HapticJournalActions(private val a: JournalActions, private val h: TaskifyHaptics) : JournalActions by a {
    override fun setTab(tab: JournalTab) { h.tick(); a.setTab(tab) }
    override fun setMood(mood: Int?) { h.tick(); a.setMood(mood) }
    override fun toggleTrash() { h.tick(); a.toggleTrash() }
    override fun setRange(days: Int) { h.tick(); a.setRange(days) }
    override fun restore(entry: JournalEntry) { h.success(); a.restore(entry) }
    override fun remove(entry: JournalEntry) { h.success(); a.remove(entry) }
}
