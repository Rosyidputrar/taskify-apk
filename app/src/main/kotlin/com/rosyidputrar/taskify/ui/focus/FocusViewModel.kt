package com.rosyidputrar.taskify.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.designsystem.component.launchSafe
import com.rosyidputrar.taskify.core.domain.focus.FocusDigestInput
import com.rosyidputrar.taskify.core.domain.focus.FocusDigestItem
import com.rosyidputrar.taskify.core.domain.focus.FocusRun
import com.rosyidputrar.taskify.core.domain.focus.FocusSessionDigest
import com.rosyidputrar.taskify.core.domain.focus.PomodoroConfig
import com.rosyidputrar.taskify.core.domain.focus.summarizeFocusNotifs
import com.rosyidputrar.taskify.core.domain.tasks.Task
import com.rosyidputrar.taskify.core.domain.tasks.TaskStatus
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.store.device.NotifAction
import com.rosyidputrar.taskify.core.store.device.NotificationLogRepository
import com.rosyidputrar.taskify.core.store.focus.FocusRepository
import com.rosyidputrar.taskify.core.store.focus.FocusStats
import com.rosyidputrar.taskify.core.store.tasks.NewTask
import com.rosyidputrar.taskify.core.store.tasks.TaskRepository
import com.rosyidputrar.taskify.focus.FocusController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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
    val autoMusicStationId: String? = null,
    /** Ringkasan notifikasi penting yang masuk selama sesi fokus terakhir. */
    val digest: FocusSessionDigest? = null,
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
    fun catchUp() {}
    fun toggle(task: Task) {}
    fun completeTask() {}
    fun setFocusMinutes(minutes: Int) {}
    fun setDnd(enabled: Boolean) {}
    fun setAutoMusicStation(stationId: String?) {}
    fun dismissDigest() {}
    fun makeTaskFromDigest(item: FocusDigestItem) {}

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
    private val notifs: NotificationLogRepository? = null,
    private val messenger: Messenger? = null,
) : ViewModel(), FocusActions {
    private val selected = MutableStateFlow(initialTaskId)
    private val dndGranted = MutableStateFlow(controller.dndAccessGranted())

    private val taskId = combine(controller.run, selected) { run, sel -> if (run != null) run.taskId else sel }

    private val taskFlow = taskId.flatMapLatest { id -> if (id == null) flowOf(null) else tasks.watchTask(id) }
    private val subtaskFlow = taskId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else tasks.watchSubtasks(id) }
    private val today = Dates.today()

    private data class Settings(val config: PomodoroConfig, val dnd: Boolean, val granted: Boolean, val autoMusicId: String?)

    private val settings = combine(controller.config, controller.dnd, dndGranted, controller.autoMusicStationId, ::Settings)

    private val digestFlow = controller.lastEndedFocus.flatMapLatest { win ->
        if (win == null) flowOf(null)
        else if (notifs == null) flowOf(summarizeFocusNotifs(emptyList(), win.startedAtMs, win.endedAtMs, win.minutes, win.taskTitle))
        else notifs.watchSince(win.startedAtMs).map { rows ->
            val inputs = rows.map { e ->
                FocusDigestInput(
                    id = e.id,
                    appLabel = e.appLabel,
                    title = e.title,
                    conversation = e.conversation,
                    text = e.text,
                    category = e.category,
                    postedAt = e.postedAt,
                    moneyAmount = e.money?.amount,
                    moneyKind = e.money?.kind,
                    moneySource = e.money?.source,
                    moneyParty = e.money?.counterparty,
                )
            }
            summarizeFocusNotifs(inputs, win.startedAtMs, win.endedAtMs, win.minutes, win.taskTitle)
        }
    }

    private data class Current(val run: FocusRun?, val selected: String?, val task: Task?, val subtasks: List<Task>)

    private val current = combine(controller.run, selected, taskFlow, subtaskFlow, ::Current)

    val state: StateFlow<FocusUiState> = combine(
        current,
        tasks.watchFocus(today, limit = 12),
        settings,
        history.watchStats(today, today),
        digestFlow,
    ) { cur, candidates, s, stats, digest ->
        FocusUiState(
            run = cur.run,
            task = cur.task,
            subtasks = cur.subtasks.filter { it.status != TaskStatus.Cancelled },
            candidates = candidates,
            selectedTaskId = cur.selected,
            config = s.config,
            dnd = s.dnd,
            dndGranted = s.granted,
            autoMusicStationId = s.autoMusicId,
            digest = digest,
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
    override fun catchUp() { launchSafe { controller.catchUp(alert = true) } }

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

    override fun setAutoMusicStation(stationId: String?) {
        controller.setAutoMusicStation(stationId)
    }

    override fun dismissDigest() {
        controller.dismissDigest()
    }

    override fun makeTaskFromDigest(item: FocusDigestItem) {
        launchSafe {
            val id = tasks.create(NewTask(title = item.suggestedTaskTitle, notes = "Dari ringkasan fokus · ${item.appLabel}"))
            notifs?.markHandled(item.id, NotifAction.Task)
            messenger?.undoable("Tugas dibuat dari ringkasan fokus") {
                tasks.get(id)?.let { tasks.remove(it) }
                notifs?.markHandled(item.id, null)
            }
        }
    }
}
