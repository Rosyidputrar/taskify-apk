package com.rosyidputrar.taskify.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.domain.tasks.Task
import com.rosyidputrar.taskify.core.domain.tasks.parseQuickAdd
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.domain.time.todayFlow
import com.rosyidputrar.taskify.core.store.home.ActivityItem
import com.rosyidputrar.taskify.core.store.home.HomeRepository
import com.rosyidputrar.taskify.core.store.home.TodaySummary
import com.rosyidputrar.taskify.core.store.tasks.NewTask
import com.rosyidputrar.taskify.core.store.tasks.Progress
import com.rosyidputrar.taskify.core.store.tasks.TagRepository
import com.rosyidputrar.taskify.core.store.tasks.TaskRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val today: String = Dates.today(),
    val focus: List<Task> = emptyList(),
    val progress: Map<String, Progress> = emptyMap(),
    val summary: TodaySummary = TodaySummary(),
    val streak: Int = 0,
    val activity: List<ActivityItem> = emptyList(),
    val loaded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val home: HomeRepository,
    private val tasks: TaskRepository,
    private val tags: TagRepository,
    private val messenger: Messenger,
) : ViewModel() {
    private val today = todayFlow().stateIn(viewModelScope, SharingStarted.Eagerly, Dates.today())

    private data class Parts(val focus: List<Task>, val progress: Map<String, Progress>, val summary: TodaySummary)

    val state: StateFlow<HomeUiState> = today.flatMapLatest { t ->
        val parts = combine(tasks.watchFocus(t, 6), tasks.watchSubtaskProgress(), home.watchSummary(t)) { f, p, s -> Parts(f, p, s) }
        combine(parts, home.watchJournalStreak(t), home.watchActivity(t)) { p, streak, activity ->
            HomeUiState(t, p.focus, p.progress, p.summary, streak, activity, loaded = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun toggle(task: Task) {
        viewModelScope.launch {
            val outcome = tasks.toggle(task, today.value)
            val undo = outcome.undo
            if (outcome.message != null && undo != null) messenger.undoable(outcome.message!!, undo)
        }
    }

    /** Catat cepat dari kartu Fokus: tanpa tanggal → hari ini. */
    fun quickAdd(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val p = parseQuickAdd(text, today.value)
            val id = tasks.create(NewTask(p.title, dueDate = p.dueDate ?: today.value, dueTime = p.dueTime, priority = p.priority ?: 0))
            if (p.tags.isNotEmpty()) tags.addByName("task", id, p.tags)
            messenger.undoable("Tugas ditambahkan") { tasks.get(id)?.let { tasks.remove(it) } }
        }
    }
}
