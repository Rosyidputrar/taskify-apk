package com.rosyidputrar.taskify.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.designsystem.component.launchSafe
import com.rosyidputrar.taskify.core.domain.focus.FocusRun
import com.rosyidputrar.taskify.core.domain.focus.PomodoroConfig
import com.rosyidputrar.taskify.core.domain.tasks.Task
import com.rosyidputrar.taskify.core.domain.tasks.TaskStatus
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.store.focus.FocusRepository
import com.rosyidputrar.taskify.core.store.focus.FocusStats
import com.rosyidputrar.taskify.core.store.tasks.TaskRepository
import com.rosyidputrar.taskify.focus.FocusController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

data class FocusUiState(
    val run: FocusRun? = null,
    /** Tugas yang difokuskan (sedang berjalan) atau dipilih (belum mulai). */
    val task: Task? = null,
    val subtasks: List<Task> = emptyList(),
    /** Pilihan tugas saat belum mulai: jatuh tempo hari ini & terlambat. */
    val candidates: List<Task> = emptyList(),
    val selectedTaskId: String? = null,
    val config: PomodoroConfig = PomodoroConfig(),
    val dnd: Boolean = false,
    val dndGranted: Boolean = false,
    val today: FocusStats = FocusStats(),
    val loaded: Boolean = false,
)

interface FocusActions {
    fun select(taskId: String?) {}
    fun start() {}
    fun pause() {}
    fun resume() {}
    fun skip() {}
    fun stop() {}
    fun toggle(task: Task) {}
    fun completeTask() {}
    fun setFocusMinutes(minutes: Int) {}
    fun setDnd(enabled: Boolean) {}

    companion object {
        val None = object : FocusActions {}
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FocusViewModel(
    initialTaskId: String?,
    private val controller: FocusController,
    private val tasks: TaskRepository,
    history: FocusRepository,
) : ViewModel(), FocusActions {
    private val selected = MutableStateFlow(initialTaskId)
    private val dndGranted = MutableStateFlow(controller.dndAccessGranted())

    private val taskId = combine(controller.run, selected) { run, sel -> if (run != null) run.taskId else sel }

    private val taskFlow = taskId.flatMapLatest { id -> if (id == null) flowOf(null) else tasks.watchTask(id) }
    private val subtaskFlow = taskId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else tasks.watchSubtasks(id) }
    private val today = Dates.today()
    private val settings = combine(controller.config, controller.dnd, dndGranted) { c, d, g -> Triple(c, d, g) }

    private data class Current(val run: FocusRun?, val selected: String?, val task: Task?, val subtasks: List<Task>)

    private val current = combine(controller.run, selected, taskFlow, subtaskFlow, ::Current)

    val state: StateFlow<FocusUiState> = combine(
        current,
        tasks.watchFocus(today, limit = 12),
        settings,
        history.watchStats(today, today),
    ) { cur, candidates, (config, dnd, granted), stats ->
        FocusUiState(
            run = cur.run,
            task = cur.task,
            subtasks = cur.subtasks.filter { it.status != TaskStatus.Cancelled },
            candidates = candidates,
            selectedTaskId = cur.selected,
            config = config,
            dnd = dnd,
            dndGranted = granted,
            today = stats,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState(selectedTaskId = initialTaskId))

    init {
        launchSafe { controller.catchUp(alert = false) }
    }

    /** Dipanggil saat kembali dari Setelan sistem (izin Jangan Ganggu). */
    fun recheckDnd() {
        dndGranted.value = controller.dndAccessGranted()
    }

    override fun select(taskId: String?) {
        selected.value = taskId
    }

    override fun start() {
        val s = state.value
        val task = s.task?.takeIf { it.id == s.selectedTaskId }
        launchSafe { controller.start(task?.id, task?.title) }
    }

    override fun pause() { launchSafe { controller.pause() } }
    override fun resume() { launchSafe { controller.resume() } }
    override fun skip() { launchSafe { controller.skip() } }
    override fun stop() { launchSafe { controller.stop() } }

    override fun toggle(task: Task) { launchSafe { tasks.toggle(task) } }

    /** Tugas utama selesai → tandai selesai & akhiri sesi (fokus yang dijalani tetap dicatat). */
    override fun completeTask() {
        val task = state.value.task ?: return
        launchSafe {
            tasks.toggle(task)
            controller.stop()
            selected.value = null
        }
    }

    override fun setFocusMinutes(minutes: Int) {
        controller.setConfig(controller.config.value.copy(focusMinutes = minutes))
    }

    override fun setDnd(enabled: Boolean) {
        controller.setDnd(enabled)
    }
}
