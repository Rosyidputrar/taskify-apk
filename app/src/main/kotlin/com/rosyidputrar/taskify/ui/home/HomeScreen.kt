package com.rosyidputrar.taskify.ui.home

import com.rosyidputrar.taskify.ui.shell.sharedBounds
import com.rosyidputrar.taskify.core.domain.device.formatDuration
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.FilledTonalButton
import com.rosyidputrar.taskify.core.domain.home.HomeTile
import com.rosyidputrar.taskify.core.domain.home.HomeSection
import com.rosyidputrar.taskify.core.domain.home.HomeLayout
import com.rosyidputrar.taskify.core.domain.calendar.DayActivity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.IconButton
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.semantics.contentDescription
import com.rosyidputrar.taskify.core.designsystem.component.SkeletonCard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rosyidputrar.taskify.core.common.Rupiah
import com.rosyidputrar.taskify.core.designsystem.component.EmptyState
import com.rosyidputrar.taskify.core.designsystem.component.moodIcon
import com.rosyidputrar.taskify.core.domain.journal.moodLabel
import com.rosyidputrar.taskify.core.designsystem.component.IndexCard
import com.rosyidputrar.taskify.core.designsystem.component.IndexTab
import com.rosyidputrar.taskify.core.designsystem.component.RuledLine
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.auth.homeGreeting
import com.rosyidputrar.taskify.core.domain.tasks.Task
import com.rosyidputrar.taskify.core.domain.tasks.dueLabel
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.store.home.ActivityItem
import com.rosyidputrar.taskify.core.store.home.ActivityKind
import com.rosyidputrar.taskify.feature.tasks.component.ProgressRing
import com.rosyidputrar.taskify.feature.tasks.component.QuickAddBar
import com.rosyidputrar.taskify.feature.tasks.component.TaskCheck
import com.rosyidputrar.taskify.ui.shell.AppEvent
import com.rosyidputrar.taskify.ui.shell.AppEvents
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    displayName: String?,
    onOpenModule: (TaskifyModule) -> Unit,
    onOpenTask: (String) -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeContent(state, displayName, contentPadding, onOpenModule, onOpenTask, viewModel::toggle, viewModel::quickAdd, viewModel::setLayout)
}

@Composable
fun HomeContent(
    state: HomeUiState,
    displayName: String?,
    contentPadding: PaddingValues,
    onOpenModule: (TaskifyModule) -> Unit,
    onOpenTask: (String) -> Unit,
    onToggle: (Task) -> Unit,
    onQuickAdd: (String) -> Unit,
    onLayoutChange: (HomeLayout) -> Unit = {},
) {
    val today = state.today
    val layout = state.layout
    var arranging by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "header") { TodayHeader(today, homeGreeting(Dates.greeting(), displayName), onArrange = { arranging = true }) }
        if (state.rejectedChanges > 0) {
            item(key = "rejected") { RejectedChangesBanner(state.rejectedChanges, onOpen = { onOpenModule(TaskifyModule.Settings) }) }
        }
        val sections = layout.visibleSections
        if (!state.loaded) {
            if (HomeSection.Week in sections) item(key = "week") { WeekRibbon(today, state.week, onSelect = { day -> AppEvents.send(AppEvent.OpenCalendar(day)) }) }
            // Data pertama dari database lokal belum tiba: kerangka Fokus, ringkasan, dan lini waktu.
            item(key = "skeleton") { HomeSkeleton() }
            return@LazyColumn
        }
        if (sections.isEmpty()) {
            item(key = "all-hidden") {
                EmptyState(
                    TaskifyModule.Home,
                    title = "Beranda dikosongkan",
                    message = "Semua bagian sedang disembunyikan. Ketuk ikon susun di atas untuk menampilkannya lagi.",
                )
            }
        }
        sections.forEach { section ->
            when (section) {
                HomeSection.Week -> item(key = "week") { WeekRibbon(today, state.week, onSelect = { day -> AppEvents.send(AppEvent.OpenCalendar(day)) }) }
                HomeSection.Focus -> item(key = "focus") { FocusCard(state, onToggle, onOpenTask, onQuickAdd, onSeeAll = { onOpenModule(TaskifyModule.Tasks) }) }
                HomeSection.Summary -> if (layout.visibleTiles.isNotEmpty()) item(key = "summary") { SummaryBento(state, layout.visibleTiles, onOpenModule) }
                HomeSection.Activity -> activitySection(state, onOpenTask)
            }
        }
    }
    if (arranging) HomeLayoutSheet(layout, onLayoutChange, onDismiss = { arranging = false })
}

private fun LazyListScope.activitySection(state: HomeUiState, onOpenTask: (String) -> Unit) {
    item(key = "activity-title") {
        Text(
            "Aktivitas hari ini",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp).semantics { heading() },
        )
    }
    if (state.activity.isEmpty()) {
        item(key = "activity-empty") {
            EmptyState(
                TaskifyModule.Calendar,
                title = "Lini waktu masih kosong",
                message = "Tugas, jurnal, catatan, dan transaksi hari ini akan tersusun di sini menurut jamnya.",
            )
        }
    }
    items(state.activity, key = { "${it.kind}-${it.id}" }) { item ->
        TimelineRow(item, onClick = {
            when (item.kind) {
                ActivityKind.Task -> onOpenTask(item.id)
                ActivityKind.Journal -> AppEvents.send(AppEvent.OpenJournal(id = item.id))
                ActivityKind.Note -> AppEvents.send(AppEvent.OpenNote(id = item.id))
                ActivityKind.Transaction -> AppEvents.send(AppEvent.OpenTransaction(id = item.id))
            }
        })
    }
}

@Composable
private fun HomeSkeleton() {
    Column(
        Modifier.padding(horizontal = 20.dp).semantics(mergeDescendants = true) { contentDescription = "Memuat ringkasan hari ini…" },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SkeletonCard(TaskifyModule.Tasks, lines = 4)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonCard(TaskifyModule.Tasks, Modifier.weight(1f), lines = 1, leadingDot = false)
            SkeletonCard(TaskifyModule.Journal, Modifier.weight(1f), lines = 1, leadingDot = false)
        }
        repeat(3) { SkeletonCard(TaskifyModule.Calendar, lines = 1) }
    }
}

/** Pengingat tetap (bukan hanya snackbar) bahwa ada perubahan yang tidak tersimpan di server. */
@Composable
private fun RejectedChangesBanner(count: Long, onOpen: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(MaterialTheme.shapes.large)
            .clickable(onClickLabel = "Buka Pengaturan sinkronisasi", role = Role.Button, onClick = onOpen),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("$count perubahan ditolak server", style = MaterialTheme.typography.titleSmall)
                Text("Tidak tersimpan. Ketuk untuk melihat & coba lagi.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TodayHeader(today: String, greeting: String, onArrange: () -> Unit) {
    val date = Dates.parse(today)
    Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { IndexTab(TaskifyModule.Home, text = greeting) }
            IconButton(onClick = onArrange) {
                Icon(Icons.Outlined.Tune, contentDescription = "Susun Beranda", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(Dates.format(today, "EEEE"), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "${date.dayOfMonth} ${Dates.format(today, "MMMM")}",
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        Box(Modifier.padding(end = 12.dp)) { RuledLine() }
    }
}

/**
 * Strip 7 hari (3 hari lalu → 3 hari depan). Ketuk hari → Kalender. Titik di bawah tanggal =
 * ada tugas / jurnal / catatan / transaksi hari itu (warna modul masing-masing).
 */
@Composable
private fun WeekRibbon(today: String, week: Map<String, DayActivity>, onSelect: (String) -> Unit) {
    val days = (-3L..3L).map { Dates.addDays(today, it) }
    val modules = TaskifyTheme.modules
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(days, key = { it }) { day ->
            val isToday = day == today
            val activity = week[day] ?: DayActivity()
            val dots = weekDots(activity)
            val description = buildString {
                append(Dates.format(day, "EEEE, d MMMM"))
                if (isToday) append(", hari ini")
                dots.forEach { (kind, count) -> append(", $count ${kind.label}") }
            }
            Surface(
                onClick = { onSelect(day) },
                shape = MaterialTheme.shapes.medium,
                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { contentDescription = description },
            ) {
                Column(
                    Modifier.width(52.dp).padding(top = 10.dp, bottom = 8.dp).clearAndSetSemantics {},
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(Dates.format(day, "EEE"), style = MaterialTheme.typography.labelMedium)
                    Text(Dates.parse(day).dayOfMonth.toString(), style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    // Tinggi tetap: hari tanpa aktivitas tidak membuat pita "meloncat".
                    Row(Modifier.height(6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        dots.forEach { (kind, _) ->
                            val color = if (isToday) MaterialTheme.colorScheme.onPrimary else modules.accent(kind.module)
                            Box(Modifier.size(6.dp).clip(CircleShape).background(color))
                        }
                    }
                }
            }
        }
    }
}

private enum class DotKind(val label: String, val module: TaskifyModule) {
    Tasks("tugas", TaskifyModule.Tasks),
    Journal("jurnal", TaskifyModule.Journal),
    Notes("catatan", TaskifyModule.Notes),
    Money("transaksi", TaskifyModule.Finance),
}

private fun weekDots(a: DayActivity): List<Pair<DotKind, Int>> = listOfNotNull(
    a.tasks.takeIf { it > 0 }?.let { DotKind.Tasks to it },
    a.journal.takeIf { it > 0 }?.let { DotKind.Journal to it },
    a.notes.takeIf { it > 0 }?.let { DotKind.Notes to it },
    a.transactions.takeIf { it > 0 }?.let { DotKind.Money to it },
)

@Composable
private fun FocusCard(state: HomeUiState, onToggle: (Task) -> Unit, onOpenTask: (String) -> Unit, onQuickAdd: (String) -> Unit, onSeeAll: () -> Unit) {
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Tasks)
    val top = state.focus.firstOrNull()
    IndexCard(TaskifyModule.Tasks, tinted = true, tabWidth = 8.dp, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("FOKUS SEKARANG", style = TaskifyTextStyles.indexLabel, color = accent, modifier = Modifier.weight(1f))
            Text("Semua tugas", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onSeeAll).padding(4.dp))
        }
        Spacer(Modifier.height(8.dp))
        if (top == null) {
            Text(if (state.loaded) "Semua beres untuk hari ini" else "Memuat…", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Tugas yang jatuh tempo hari ini dan yang terlambat muncul di sini.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.sharedBounds("task-${top.id}").clickable { onOpenTask(top.id) }) {
                TaskCheck(top, { onToggle(top) }, Modifier.padding(top = 4.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(top.title, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    dueLabel(top, state.today)?.let {
                        val overdue = top.dueDate != null && top.dueDate!! < state.today
                        Text(it, style = MaterialTheme.typography.labelLarge, color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                state.progress[top.id]?.takeIf { it.total > 0 }?.let { ProgressRing(it.done, it.total, accent, size = 40.dp, stroke = 4.dp, showLabel = true) }
            }
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = { AppEvents.send(AppEvent.OpenFocus(top.id)) }) {
                Icon(Icons.Outlined.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Mulai fokus")
            }
            val rest = state.focus.drop(1).take(4)
            val largeFont = LocalDensity.current.fontScale > 1.3f
            if (rest.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("LALU", style = TaskifyTextStyles.indexLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                rest.forEach { task ->
                    Row(
                        Modifier.fillMaxWidth().sharedBounds("task-${task.id}").clickable { onOpenTask(task.id) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TaskCheck(task, { onToggle(task) })
                        Spacer(Modifier.width(12.dp))
                        val due = dueLabel(task, state.today)
                        if (largeFont) {
                            // Huruf besar: waktu pindah ke bawah judul supaya judul tidak terdesak.
                            Column(Modifier.weight(1f)) {
                                Text(task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                due?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        } else {
                            Text(task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            due?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        QuickAddBar(state.today, onSubmit = onQuickAdd, placeholder = "Tambah tugas hari ini…")
    }
}


private data class Tile(val module: TaskifyModule, val label: String, val value: String, val hint: String, val icon: ImageVector? = null)

@Composable
private fun SummaryBento(state: HomeUiState, visible: List<HomeTile>, onOpenModule: (TaskifyModule) -> Unit) {
    val s = state.summary
    val mood = s.moodToday?.roundToInt()?.coerceIn(1, 5)
    val tiles = visible.map { tile ->
        when (tile) {
            HomeTile.Tasks -> Tile(TaskifyModule.Tasks, tile.label, "${s.tasksTodayDone}/${s.tasksToday}", if (s.tasksOverdue > 0) "${s.tasksOverdue} terlambat" else "tidak ada yang terlambat")
            HomeTile.Mood -> Tile(TaskifyModule.Journal, tile.label, moodLabel(mood) ?: "—", if (s.journalToday > 0) "${s.journalToday} catatan jurnal" else "Belum menulis", moodIcon(mood))
            HomeTile.Spending -> Tile(TaskifyModule.Finance, tile.label, rupiahShort(s.expenseToday), "bulan ini Rp ${Rupiah.compact(s.expenseMonth)}")
            HomeTile.Streak -> Tile(TaskifyModule.Review, tile.label, "${state.streak}", "hari berturut-turut")
            HomeTile.Balance -> Tile(TaskifyModule.Finance, tile.label, rupiahShort(s.balance), if (s.balance < 0) "lebih banyak keluar" else "total semua dompet")
            HomeTile.Income -> Tile(TaskifyModule.Finance, tile.label, rupiahShort(s.incomeMonth), "keluar Rp ${Rupiah.compact(s.expenseMonth)}")
            HomeTile.ScreenTime -> Tile(
                TaskifyModule.Wellbeing, tile.label,
                state.device.screenMs?.let(::formatDuration) ?: "—",
                if (state.device.screenMs == null) "buka Kesehatan Digital" else "hari ini",
            )
            HomeTile.Notifications -> Tile(
                TaskifyModule.Notifications, tile.label,
                state.device.notifications?.toString() ?: "—",
                if (state.device.notifications == null) "buka Notifikasi" else "masuk hari ini",
            )
        }
    }
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(IntrinsicSize.Max)) {
                row.forEach { tile -> BentoTile(tile, onOpenModule) }
                // Jumlah ubin ganjil: ubin terakhir tetap selebar setengah supaya grid rapi.
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RowScope.BentoTile(tile: Tile, onOpenModule: (TaskifyModule) -> Unit) {
    IndexCard(
        tile.module,
        onClick = { onOpenModule(tile.module) },
        onClickLabel = "Buka ${tile.module.label}",
        modifier = Modifier.weight(1f).fillMaxHeight(),
        contentPadding = 14.dp,
    ) {
        Text(tile.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            tile.icon?.let {
                Icon(it, contentDescription = null, tint = TaskifyTheme.modules.accent(tile.module), modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(tile.value, style = if (tile.value.length > 8) MaterialTheme.typography.titleLarge else TaskifyTextStyles.numeric, maxLines = 1)
        }
        Text(tile.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun rupiahShort(amount: Long): String = if (amount == 0L) "Rp 0" else "Rp ${Rupiah.compact(amount)}"

/** Baris lini waktu: kolom jam (monospace), rel warna modul, judul. */
@Composable
private fun TimelineRow(item: ActivityItem, onClick: () -> Unit) {
    val module = when (item.kind) {
        ActivityKind.Task -> TaskifyModule.Tasks
        ActivityKind.Journal -> TaskifyModule.Journal
        ActivityKind.Note -> TaskifyModule.Notes
        ActivityKind.Transaction -> TaskifyModule.Finance
    }
    val accent = TaskifyTheme.modules.accent(module)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp).height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(item.time.ifEmpty { "—" }, style = TaskifyTextStyles.code, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(52.dp))
        Box(Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant)) {
            Box(Modifier.size(10.dp).align(Alignment.Center).background(accent, CircleShape))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(module.label.uppercase(), style = TaskifyTextStyles.indexLabel, color = accent)
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
                color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
        item.amount?.let {
            Text(
                (if (item.isIncome) "+" else "−") + Rupiah.format(it),
                style = MaterialTheme.typography.labelLarge,
                color = if (item.isIncome) TaskifyTheme.modules.accent(TaskifyModule.Finance) else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
