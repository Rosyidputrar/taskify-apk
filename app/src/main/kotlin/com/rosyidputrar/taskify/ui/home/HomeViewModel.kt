package com.rosyidputrar.taskify.ui.home

import com.rosyidputrar.taskify.core.device.DeviceToday
import com.rosyidputrar.taskify.core.device.DeviceSummary
import com.rosyidputrar.taskify.data.AppPreferences
import com.rosyidputrar.taskify.core.store.calendar.CalendarRepository
import com.rosyidputrar.taskify.core.domain.home.HomeLayout
import com.rosyidputrar.taskify.core.domain.calendar.DayActivity
import com.rosyidputrar.taskify.core.designsystem.component.launchSafe
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOf
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
    /** Perubahan yang ditolak server dan belum ditangani (Pengaturan → Sinkronisasi). */
    val rejectedChanges: Long = 0,
    /** Aktivitas per hari untuk titik di pita minggu (3 hari lalu → 3 hari depan). */
    val week: Map<String, DayActivity> = emptyMap(),
    /** Susunan Beranda pilihan pengguna. */
    val layout: HomeLayout = HomeLayout.Default,
    /** Kesehatan Digital & Notifikasi hari ini (null = belum ada data/izin). */
    val device: DeviceToday = DeviceToday(null, null),
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val home: HomeRepository,
    private val tasks: TaskRepository,
    private val tags: TagRepository,
    private val messenger: Messenger,
    private val rejectedChanges: Flow<Long> = flowOf(0L),
    private val calendar: CalendarRepository? = null,
    private val prefs: AppPreferences? = null,
    private val deviceSummary: DeviceSummary? = null,
) : ViewModel() {
    private val today = todayFlow().stateIn(viewModelScope, SharingStarted.Eagerly, Dates.today())

    private data class Parts(val focus: List<Task>, val progress: Map<String, Progress>, val summary: TodaySummary)

    val state: StateFlow<HomeUiState> = today.flatMapLatest { t ->
        val parts = combine(tasks.watchFocus(t, 6), tasks.watchSubtaskProgress(), home.watchSummary(t)) { f, p, s -> Parts(f, p, s) }
        val extras = combine(
            rejectedChanges,
            weekActivity(t),
            prefs?.homeLayout ?: flowOf(HomeLayout.Default),
            deviceSummary?.watchToday() ?: flowOf(DeviceToday(null, null)),
        ) { r, w, l, d -> Extras(r, w, l, d) }
        combine(parts, home.watchJournalStreak(t), home.watchActivity(t), extras) { p, streak, activity, x ->
            HomeUiState(t, p.focus, p.progress, p.summary, streak, activity, loaded = true, rejectedChanges = x.rejected, week = x.week, layout = x.layout, device = x.device)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Titik aktivitas pita minggu: jurnal/catatan/transaksi + tugas per tanggal jatuh tempo. */
    private fun weekActivity(today: String): Flow<Map<String, DayActivity>> {
        val cal = calendar ?: return flowOf(emptyMap())
        val from = Dates.addDays(today, -3L)
        val to = Dates.addDays(today, 3L)
        return combine(cal.watchActivity(from, to), cal.watchTasksInRange(from, to)) { other, list ->
            val byDay = list.groupBy { it.dueDate.orEmpty() }
            (other.keys + byDay.keys).filter { it.isNotEmpty() }.associateWith { day ->
                val base = other[day] ?: DayActivity()
                val tasksOfDay = byDay[day].orEmpty()
                base.copy(tasks = tasksOfDay.size, tasksDone = tasksOfDay.count { it.status == com.rosyidputrar.taskify.core.domain.tasks.TaskStatus.Done })
            }
        }
    }

    fun setLayout(layout: HomeLayout) {
        val p = prefs ?: return
        launchSafe { p.setHomeLayout(layout) }
    }

    fun toggle(task: Task) {
        launchSafe {
            val outcome = tasks.toggle(task, today.value)
            val undo = outcome.undo
            if (outcome.message != null && undo != null) messenger.undoable(outcome.message!!, undo)
        }
    }

    /** Catat cepat dari kartu Fokus: tanpa tanggal → hari ini. */
    fun quickAdd(text: String) {
        if (text.isBlank()) return
        launchSafe {
            val p = parseQuickAdd(text, today.value)
            val id = tasks.create(NewTask(p.title, dueDate = p.dueDate ?: today.value, dueTime = p.dueTime, priority = p.priority ?: 0))
            if (p.tags.isNotEmpty()) tags.addByName("task", id, p.tags)
            messenger.undoable("Tugas ditambahkan") { tasks.get(id)?.let { tasks.remove(it) } }
        }
    }
}

private data class Extras(val rejected: Long, val week: Map<String, DayActivity>, val layout: HomeLayout, val device: DeviceToday)
