package com.rosyidputrar.taskify.feature.tasks

import com.rosyidputrar.taskify.core.designsystem.component.launchSafe
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.domain.tasks.Task
import com.rosyidputrar.taskify.core.domain.tasks.TaskGroup
import com.rosyidputrar.taskify.core.domain.tasks.groupByDate
import com.rosyidputrar.taskify.core.domain.tasks.parseQuickAdd
import com.rosyidputrar.taskify.core.domain.time.todayFlow
import com.rosyidputrar.taskify.core.store.tasks.NewTask
import com.rosyidputrar.taskify.core.store.tasks.Progress
import com.rosyidputrar.taskify.core.store.tasks.SmartView
import com.rosyidputrar.taskify.core.store.tasks.TagRef
import com.rosyidputrar.taskify.core.store.tasks.TagRepository
import com.rosyidputrar.taskify.core.store.tasks.TaskList
import com.rosyidputrar.taskify.core.store.tasks.TaskRepository
import com.rosyidputrar.taskify.core.store.tasks.TaskView
import com.rosyidputrar.taskify.core.store.tasks.ViewCounts
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Segment(val label: String) { Active("Aktif"), Done("Selesai") }

data class TasksFilter(
    val segment: Segment = Segment.Active,
    val view: TaskView = TaskView.Smart(SmartView.All),
    val highPriorityOnly: Boolean = false,
)

data class TasksUiState(
    val today: String = "",
    val filter: TasksFilter = TasksFilter(),
    val groups: List<TaskGroup> = emptyList(),
    val doneItems: List<Task> = emptyList(),
    val counts: ViewCounts = ViewCounts(),
    val lists: List<TaskList> = emptyList(),
    val progress: Map<String, Progress> = emptyMap(),
    val tags: Map<String, List<TagRef>> = emptyMap(),
    val loaded: Boolean = false,
) {
    val listNames: Map<String, TaskList> get() = lists.associateBy { it.id }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModel(
    private val tasks: TaskRepository,
    private val tags: TagRepository,
    private val messenger: Messenger,
) : ViewModel(), TasksActions {
    private val filter = MutableStateFlow(TasksFilter())
    private val today = todayFlow().stateIn(viewModelScope, SharingStarted.Eagerly, com.rosyidputrar.taskify.core.domain.time.Dates.today())

    private val rows = combine(filter, today) { f, t -> f to t }.flatMapLatest { (f, t) ->
        val view = if (f.segment == Segment.Done) TaskView.Smart(SmartView.Done) else f.view
        tasks.watchView(view, t)
    }

    private data class Extras(val counts: ViewCounts, val lists: List<TaskList>, val progress: Map<String, Progress>, val tags: Map<String, List<TagRef>>)

    private val extras = today.flatMapLatest { t ->
        combine(tasks.watchCounts(t), tasks.watchLists(), tasks.watchSubtaskProgress(), tags.watchTagMap("task")) { c, l, p, g -> Extras(c, l, p, g) }
    }

    val state: StateFlow<TasksUiState> = combine(rows, extras, filter, today) { items, ex, f, t ->
        val visible = if (f.highPriorityOnly) items.filter { it.priority >= 3 } else items
        TasksUiState(
            today = t,
            filter = f,
            groups = if (f.segment == Segment.Active) groupByDate(visible, t) else emptyList(),
            doneItems = if (f.segment == Segment.Done) visible else emptyList(),
            counts = ex.counts,
            lists = ex.lists,
            progress = ex.progress,
            tags = ex.tags,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState(today = today.value))

    override fun setSegment(segment: Segment) { filter.value = filter.value.copy(segment = segment) }
    override fun setView(view: TaskView) { filter.value = filter.value.copy(segment = Segment.Active, view = view) }
    override fun toggleHighPriority() { filter.value = filter.value.copy(highPriorityOnly = !filter.value.highPriorityOnly) }

    /** Catat cepat: "besok jam 9 rapat #kerja !2" → tugas bertanggal, berjam, bertag. */
    override fun quickAdd(text: String) {
        val input = text.trim()
        if (input.isEmpty()) return
        launchSafe {
            val parsed = parseQuickAdd(input, today.value)
            val listId = (filter.value.view as? TaskView.List)?.listId
            // Di tampilan "Hari ini", tugas tanpa tanggal langsung jatuh hari ini.
            val defaultDate = if ((filter.value.view as? TaskView.Smart)?.view == SmartView.Today) today.value else null
            val id = tasks.create(
                NewTask(
                    title = parsed.title,
                    listId = listId,
                    dueDate = parsed.dueDate ?: defaultDate,
                    dueTime = parsed.dueTime,
                    priority = parsed.priority ?: 0,
                ),
            )
            if (parsed.tags.isNotEmpty()) tags.addByName("task", id, parsed.tags)
            messenger.undoable("Tugas ditambahkan") { tasks.get(id)?.let { tasks.remove(it) } }
        }
    }

    override fun toggle(task: Task) {
        launchSafe {
            val outcome = tasks.toggle(task, today.value)
            val undo = outcome.undo
            if (outcome.message != null && undo != null) messenger.undoable(outcome.message!!, undo)
        }
    }

    override fun remove(task: Task) {
        launchSafe {
            val outcome = tasks.remove(task)
            outcome.undo?.let { messenger.undoable(outcome.message ?: "Dihapus", it) }
        }
    }

    override fun createList(name: String, color: String) {
        if (name.isBlank()) return
        launchSafe {
            val id = tasks.createList(name, color)
            setView(TaskView.List(id))
        }
    }

    override fun renameList(id: String, name: String, color: String) {
        launchSafe { tasks.updateList(id, name, color) }
    }

    override fun removeList(id: String) {
        launchSafe {
            tasks.removeList(id)
            setView(TaskView.Smart(SmartView.All))
            messenger.show("Daftar dihapus. Tugasnya pindah ke Tanpa daftar.")
        }
    }
}
