package com.rosyidputrar.taskify.feature.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rosyidputrar.taskify.core.common.Rupiah
import com.rosyidputrar.taskify.core.designsystem.component.ModuleHeader
import com.rosyidputrar.taskify.core.designsystem.component.moodIcon
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.calendar.CalendarTaskItem
import com.rosyidputrar.taskify.core.domain.calendar.buildWeek
import com.rosyidputrar.taskify.core.domain.calendar.describeActivity
import com.rosyidputrar.taskify.core.domain.journal.moodLabel
import com.rosyidputrar.taskify.core.domain.tasks.Task
import com.rosyidputrar.taskify.core.domain.tasks.TaskStatus
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.feature.tasks.TaskDetailSheet
import com.rosyidputrar.taskify.feature.tasks.component.QuickAddBar
import com.rosyidputrar.taskify.feature.tasks.component.TaskCheck
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

interface CalendarActions {
    fun select(date: String)
    fun shift(months: Long)
    fun goToday()
    fun toggleKind(kind: CalendarKind)
    fun quickAdd(text: String)
    fun toggle(task: Task)
}

private object NoCalendarActions : CalendarActions {
    override fun select(date: String) = Unit
    override fun shift(months: Long) = Unit
    override fun goToday() = Unit
    override fun toggleKind(kind: CalendarKind) = Unit
    override fun quickAdd(text: String) = Unit
    override fun toggle(task: Task) = Unit
}

@Composable
fun CalendarScreen(
    contentPadding: PaddingValues,
    initialDate: String? = null,
    onOpenModule: (TaskifyModule) -> Unit,
    onOpenJournal: (id: String?, date: String) -> Unit = { _, _ -> onOpenModule(TaskifyModule.Journal) },
    onOpenNote: (id: String?, date: String) -> Unit = { _, _ -> onOpenModule(TaskifyModule.Notes) },
    onOpenTransaction: (id: String?, date: String) -> Unit = { _, _ -> onOpenModule(TaskifyModule.Finance) },
) {
    val viewModel: CalendarViewModel = koinViewModel(key = "calendar-$initialDate") { parametersOf(initialDate) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = remember(viewModel) {
        object : CalendarActions {
            override fun select(date: String) = viewModel.select(date)
            override fun shift(months: Long) = viewModel.shift(months)
            override fun goToday() = viewModel.goToday()
            override fun toggleKind(kind: CalendarKind) = viewModel.toggleKind(kind)
            override fun quickAdd(text: String) = viewModel.quickAdd(text)
            override fun toggle(task: Task) = viewModel.toggle(task)
        }
    }
    CalendarContent(state, actions, contentPadding, onOpenModule, onOpenJournal = onOpenJournal, onOpenNote = onOpenNote, onOpenTransaction = onOpenTransaction)
}

@Composable
fun CalendarContent(
    state: CalendarUiState,
    actions: CalendarActions = NoCalendarActions,
    contentPadding: PaddingValues,
    onOpenModule: (TaskifyModule) -> Unit = {},
    detailEnabled: Boolean = true,
    onOpenJournal: (id: String?, date: String) -> Unit = { _, _ -> onOpenModule(TaskifyModule.Journal) },
    onOpenNote: (id: String?, date: String) -> Unit = { _, _ -> onOpenModule(TaskifyModule.Notes) },
    onOpenTransaction: (id: String?, date: String) -> Unit = { _, _ -> onOpenModule(TaskifyModule.Finance) },
) {
    val listState = rememberLazyListState()
    // Grid bulan adalah item ke-2; setelah tergulir keluar, strip minggu muncul di atas.
    val collapsed by remember { derivedStateOf { listState.firstVisibleItemIndex >= 3 } }
    var detailTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    val title = Dates.format(state.month, "MMMM yyyy")

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = contentPadding) {
            item {
                ModuleHeader(
                    TaskifyModule.Calendar,
                    title = title.replaceFirstChar { it.uppercase() },
                    subtitle = if (state.selected == state.today) "Hari ini" else Dates.format(state.selected, "EEEE, d MMMM"),
                    actions = {
                        IconButton(onClick = { actions.shift(-1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Bulan sebelumnya") }
                        TextButton(onClick = actions::goToday) { Text("Hari ini") }
                        IconButton(onClick = { actions.shift(1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Bulan berikutnya") }
                    },
                )
            }
            item { Legend(state.visible, actions::toggleKind) }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    WeekdayHeader()
                    MonthGrid(
                        month = state.month,
                        grid = state.grid,
                        today = state.today,
                        selected = state.selected,
                        activity = state.activity,
                        visible = state.visible,
                        onSelect = actions::select,
                        onShift = actions::shift,
                    )
                }
            }
            item { DayHeading(state) }
            dayAgenda(state, actions, onOpenTask = { detailTaskId = it }, onOpenModule = onOpenModule, onOpenJournal = onOpenJournal, onOpenNote = onOpenNote, onOpenTransaction = onOpenTransaction)
        }

        AnimatedVisibility(
            visible = collapsed,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 4.dp) {
                Column(Modifier.padding(top = contentPadding.calculateTopPadding()).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(title.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                    WeekdayHeader()
                    WeekStrip(buildWeek(state.selected), state.today, state.selected, state.activity, state.visible, actions::select)
                }
            }
        }
    }

    if (detailEnabled) detailTaskId?.let { TaskDetailSheet(it, state.today, onDismiss = { detailTaskId = null }) }
}

@Composable
private fun Legend(visible: Set<CalendarKind>, onToggle: (CalendarKind) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(CalendarKind.entries) { kind ->
            val accent = TaskifyTheme.modules.accent(kind.module())
            FilterChip(
                selected = kind in visible,
                onClick = { onToggle(kind) },
                label = { Text(kind.label) },
                leadingIcon = { Box(Modifier.size(10.dp).background(accent, CircleShape)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accent.copy(alpha = 0.14f),
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }
}

@Composable
private fun DayHeading(state: CalendarUiState) {
    val summary = describeActivity(state.selectedActivity).ifEmpty { "Belum ada aktivitas" }
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 4.dp)) {
        Text(
            Dates.format(state.selected, "EEEE, d MMMM yyyy"),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(summary.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(title: String, module: TaskifyModule, trailing: String? = null) {
    item(key = "section-$title") {
        val accent = TaskifyTheme.modules.accent(module)
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 16.dp, height = 4.dp).background(accent, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(title.uppercase(), style = TaskifyTextStyles.indexLabel, color = accent, modifier = Modifier.weight(1f).semantics { heading() })
            trailing?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.dayAgenda(
    state: CalendarUiState,
    actions: CalendarActions,
    onOpenTask: (String) -> Unit,
    onOpenModule: (TaskifyModule) -> Unit,
    onOpenJournal: (String?, String) -> Unit,
    onOpenNote: (String?, String) -> Unit,
    onOpenTransaction: (String?, String) -> Unit,
) {
    val a = state.selectedActivity
    val day = state.selected
    val visible = state.visible

    if (CalendarKind.Tasks in visible) {
        section("Tugas", TaskifyModule.Tasks, trailing = if (a.tasks > 0) "${a.tasksDone}/${a.tasks} selesai" else null)
        item(key = "quickadd-${state.selected}") {
            QuickAddBar(state.today, onSubmit = actions::quickAdd, placeholder = "Tambah tugas di tanggal ini…", modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        }
        if (state.selectedTasks.isEmpty()) emptyLine("tasks", "Tidak ada tugas.")
        items(state.selectedTasks, key = { "t-${it.task.id}-${it.date}" }) { item -> CalendarTaskRow(item, state.today, actions, onOpenTask) }
    }
    if (CalendarKind.Journal in visible) {
        section("Jurnal", TaskifyModule.Journal, trailing = moodLabel(a.mood)?.let { "mood $it" })
        items(state.agenda.journal, key = { "j-${it.id}" }) { j ->
            AgendaRow(module = TaskifyModule.Journal, title = j.title.ifEmpty { "Tanpa judul" }, meta = j.time, icon = moodIcon(j.mood), onClick = { onOpenJournal(j.id, day) })
        }
        if (state.agenda.journal.isEmpty()) emptyLine("journal", "Belum ada catatan jurnal.", "Tulis jurnal") { onOpenJournal(null, day) }
        else emptyLine("journal-more", "", "Tulis entri lagi") { onOpenJournal(null, day) }
    }
    if (CalendarKind.Notes in visible) {
        section("Catatan", TaskifyModule.Notes)
        items(state.agenda.notes, key = { "n-${it.id}" }) { n ->
            AgendaRow(module = TaskifyModule.Notes, title = n.title.ifEmpty { "Tanpa judul" }, icon = if (n.pinned) Icons.Outlined.PushPin else null, onClick = { onOpenNote(n.id, day) })
        }
        if (state.agenda.notes.isEmpty()) emptyLine("notes", "Belum ada catatan di tanggal ini.", "Buat catatan") { onOpenNote(null, day) }
        else emptyLine("notes-more", "", "Catatan baru") { onOpenNote(null, day) }
    }
    if (CalendarKind.Finance in visible) {
        val totals = buildList {
            if (a.expense > 0) add("keluar ${Rupiah.compact(a.expense)}")
            if (a.income > 0) add("masuk ${Rupiah.compact(a.income)}")
        }.joinToString(" · ").ifEmpty { null }
        section("Keuangan", TaskifyModule.Finance, trailing = totals)
        if (state.agenda.transactions.isEmpty()) emptyLine("finance", "Tidak ada transaksi.", "Catat transaksi") { onOpenTransaction(null, day) }
        items(state.agenda.transactions, key = { "x-${it.id}" }) { t ->
            val sign = when (t.kind) { "income" -> "+"; "expense" -> "−"; else -> "" }
            AgendaRow(
                module = TaskifyModule.Finance,
                title = t.title,
                meta = listOfNotNull(t.time.ifEmpty { null }, t.category, t.wallet, if (t.kind == "transfer") "transfer" else null).joinToString(" · "),
                icon = when (t.kind) { "income" -> Icons.Outlined.ArrowDownward; "expense" -> Icons.Outlined.ArrowUpward; else -> Icons.Outlined.SwapHoriz },
                trailing = sign + Rupiah.format(t.amount),
                trailingColor = if (t.kind == "income") TaskifyTheme.modules.accent(TaskifyModule.Finance) else null,
                onClick = { onOpenTransaction(t.id, day) },
            )
        }
        if (state.agenda.transactions.isNotEmpty()) emptyLine("finance-more", "", "Catat transaksi") { onOpenTransaction(null, day) }
    }
    if (CalendarKind.Vault in visible) {
        section("Vault", TaskifyModule.Vault)
        if (state.agenda.vault.isEmpty()) emptyLine("vault", "Tidak ada item vault baru.")
        items(state.agenda.vault, key = { "v-${it.id}" }) { v ->
            AgendaRow(
                module = TaskifyModule.Vault,
                title = v.title,
                meta = when (v.kind) { "snippet" -> "Snippet baru"; "project" -> "Project baru"; else -> "Versi file baru" },
                icon = if (v.kind == "snippet") Icons.Outlined.Code else Icons.Outlined.Folder,
                onClick = { onOpenModule(TaskifyModule.Vault) },
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.emptyLine(key: String, text: String, action: String? = null, onAction: () -> Unit = {}) {
    item(key = "empty-$key") {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (action != null) TextButton(onClick = onAction) { Text(action) }
        }
    }
}

/**
 * Tugas di agenda. Kejadian VIRTUAL (tugas berulang yang belum punya baris) digambar dengan
 * lingkaran putus-putus dan ikon ↻; mengetuknya membuka seri aslinya.
 */
@Composable
private fun CalendarTaskRow(item: CalendarTaskItem, today: String, actions: CalendarActions, onOpenTask: (String) -> Unit) {
    val task = item.task
    val done = task.status == TaskStatus.Done
    val outline = MaterialTheme.colorScheme.outline
    Row(
        Modifier.fillMaxWidth().clickable { onOpenTask(task.id) }.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.virtual) {
            Box(
                Modifier.size(26.dp).drawBehind {
                    drawCircle(outline, style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
                },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Repeat, contentDescription = "Kejadian berulang", modifier = Modifier.size(14.dp), tint = outline) }
        } else {
            TaskCheck(task, { actions.toggle(task) })
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                color = if (done || item.virtual) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            val meta = listOfNotNull(
                Dates.shortTime(task.dueTime).ifEmpty { null },
                if (item.virtual) "berulang" else null,
                if (!item.virtual && item.date < today && !done) "terlambat" else null,
            ).joinToString(" · ")
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (task.isRecurring && !item.virtual) Icon(Icons.Outlined.Repeat, contentDescription = "Berulang", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AgendaRow(
    module: TaskifyModule,
    title: String,
    meta: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    trailing: String? = null,
    trailingColor: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit,
) {
    val accent = TaskifyTheme.modules.accent(module)
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(26.dp).border(2.dp, accent.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { icon?.let { Icon(it, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp)) } }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!meta.isNullOrEmpty()) Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = trailingColor ?: MaterialTheme.colorScheme.onSurface) }
    }
}

