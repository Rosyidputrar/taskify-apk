package com.rosyidputrar.taskify.feature.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rosyidputrar.taskify.core.designsystem.component.EmptyState
import com.rosyidputrar.taskify.core.designsystem.component.ModuleHeader
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.designsystem.theme.namedColor
import com.rosyidputrar.taskify.core.domain.tasks.GroupTone
import com.rosyidputrar.taskify.core.store.tasks.SmartView
import com.rosyidputrar.taskify.core.store.tasks.TaskView
import com.rosyidputrar.taskify.feature.tasks.component.ListDialog
import com.rosyidputrar.taskify.feature.tasks.component.QuickAddBar
import com.rosyidputrar.taskify.feature.tasks.component.TaskRow
import org.koin.compose.viewmodel.koinViewModel

private val SMART_CHIPS = listOf(SmartView.All, SmartView.Today, SmartView.Upcoming, SmartView.Overdue)

@Composable
fun TasksScreen(
    contentPadding: PaddingValues,
    openTaskId: String? = null,
    onBack: (() -> Unit)? = null,
    backButton: @Composable (() -> Unit) -> Unit = {},
    viewModel: TasksViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TasksContent(state, viewModel, contentPadding, openTaskId, onBack, backButton)
}

/** Aksi layar Tugas — dipisah agar konten bisa dirender tanpa database (tes tangkapan layar). */
interface TasksActions {
    fun quickAdd(text: String)
    fun setSegment(segment: Segment)
    fun setView(view: TaskView)
    fun toggleHighPriority()
    fun toggle(task: com.rosyidputrar.taskify.core.domain.tasks.Task)
    fun remove(task: com.rosyidputrar.taskify.core.domain.tasks.Task)
    fun createList(name: String, color: String)
    fun renameList(id: String, name: String, color: String)
    fun removeList(id: String)
}

@Composable
fun TasksContent(
    state: TasksUiState,
    viewModel: TasksActions,
    contentPadding: PaddingValues,
    openTaskId: String? = null,
    onBack: (() -> Unit)? = null,
    backButton: @Composable (() -> Unit) -> Unit = {},
) {
    var detailId by rememberSaveable(openTaskId) { mutableStateOf(openTaskId) }
    var listDialog by rememberSaveable { mutableStateOf<String?>(null) } // "new" atau id daftar
    val filter = state.filter
    val selectedList = (filter.view as? TaskView.List)?.listId

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            val subtitle = buildString {
                append("${state.counts.all} aktif")
                if (state.counts.overdue > 0) append(" · ${state.counts.overdue} terlambat")
                if (state.counts.today > 0) append(" · ${state.counts.today} hari ini")
            }
            ModuleHeader(TaskifyModule.Tasks, "Tugas", subtitle = subtitle, navigation = onBack?.let { { backButton(it) } })
        }
        item {
            QuickAddBar(state.today, onSubmit = viewModel::quickAdd, modifier = Modifier.padding(horizontal = 20.dp))
        }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Segment.entries.forEachIndexed { i, segment ->
                    SegmentedButton(
                        selected = filter.segment == segment,
                        onClick = { viewModel.setSegment(segment) },
                        shape = SegmentedButtonDefaults.itemShape(i, Segment.entries.size),
                        label = { Text(segment.label) },
                    )
                }
            }
        }
        if (filter.segment == Segment.Active) {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SMART_CHIPS) { view ->
                        val count = when (view) {
                            SmartView.All -> state.counts.all
                            SmartView.Today -> state.counts.today
                            SmartView.Upcoming -> state.counts.upcoming
                            SmartView.Overdue -> state.counts.overdue
                            else -> 0
                        }
                        FilterChip(
                            selected = filter.view == TaskView.Smart(view),
                            onClick = { viewModel.setView(TaskView.Smart(view)) },
                            label = { Text(if (count > 0) "${view.label} $count" else view.label) },
                        )
                    }
                    items(state.lists, key = { it.id }) { list ->
                        FilterChip(
                            selected = selectedList == list.id,
                            onClick = { if (selectedList == list.id) listDialog = list.id else viewModel.setView(TaskView.List(list.id)) },
                            label = { Text(list.name) },
                            leadingIcon = { Box(Modifier.size(10.dp).background(namedColor(list.color), CircleShape)) },
                            trailingIcon = if (selectedList == list.id) {
                                { Icon(Icons.Outlined.Edit, contentDescription = "Ubah daftar", modifier = Modifier.size(16.dp)) }
                            } else {
                                null
                            },
                        )
                    }
                    item {
                        FilterChip(selected = false, onClick = { listDialog = "new" }, label = { Text("Daftar") }, leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(16.dp)) })
                    }
                    item {
                        FilterChip(selected = filter.highPriorityOnly, onClick = viewModel::toggleHighPriority, label = { Text("Prioritas tinggi") })
                    }
                }
            }
        }

        val groups = state.groups
        val showEmpty = state.loaded && (if (filter.segment == Segment.Active) groups.isEmpty() else state.doneItems.isEmpty())
        if (showEmpty) {
            item {
                EmptyState(
                    TaskifyModule.Tasks,
                    title = if (filter.segment == Segment.Done) "Belum ada yang selesai" else "Semua beres",
                    message = if (filter.segment == Segment.Done) {
                        "Tugas yang kamu centang akan tersimpan di sini."
                    } else {
                        "Ketik di atas: \"besok jam 9 rapat #kerja\" langsung jadi tugas bertanggal dan bertag."
                    },
                )
            }
        }
        groups.forEach { group ->
            stickyHeader(key = "h-${group.key}") {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        group.label.uppercase(),
                        style = TaskifyTextStyles.indexLabel,
                        color = when (group.tone) {
                            GroupTone.Danger -> MaterialTheme.colorScheme.error
                            GroupTone.Muted -> MaterialTheme.colorScheme.onSurfaceVariant
                            GroupTone.Normal -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.weight(1f))
                    Text("${group.items.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(group.items, key = { it.id }) { task ->
                TaskRow(
                    task = task,
                    today = state.today,
                    progress = state.progress[task.id],
                    tags = state.tags[task.id].orEmpty(),
                    list = task.listId?.let { state.listNames[it] }.takeIf { selectedList == null },
                    onToggle = { viewModel.toggle(task) },
                    onOpen = { detailId = task.id },
                    onDelete = { viewModel.remove(task) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        if (filter.segment == Segment.Done) {
            items(state.doneItems, key = { it.id }) { task ->
                TaskRow(
                    task = task,
                    today = state.today,
                    progress = state.progress[task.id],
                    tags = state.tags[task.id].orEmpty(),
                    list = task.listId?.let { state.listNames[it] },
                    onToggle = { viewModel.toggle(task) },
                    onOpen = { detailId = task.id },
                    onDelete = { viewModel.remove(task) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }

    detailId?.let { id -> TaskDetailSheet(id, state.today, onDismiss = { detailId = null }) }

    when (val target = listDialog) {
        null -> Unit
        "new" -> ListDialog("Daftar baru", onSave = { name, color -> viewModel.createList(name, color); listDialog = null }, onDismiss = { listDialog = null })
        else -> {
            val list = state.listNames[target]
            if (list != null) {
                ListDialog(
                    "Ubah daftar",
                    initialName = list.name,
                    initialColor = list.color ?: "teal",
                    onSave = { name, color -> viewModel.renameList(list.id, name, color); listDialog = null },
                    onDismiss = { listDialog = null },
                    onDelete = { viewModel.removeList(list.id); listDialog = null },
                )
            }
        }
    }
}

