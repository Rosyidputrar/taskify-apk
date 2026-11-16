package com.rosyidputrar.taskify.feature.calendar

import com.rosyidputrar.taskify.core.designsystem.component.launchSafe
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.domain.calendar.CalendarTaskItem
import com.rosyidputrar.taskify.core.domain.calendar.DayActivity
import com.rosyidputrar.taskify.core.domain.calendar.MonthCell
import com.rosyidputrar.taskify.core.domain.calendar.buildMonthGrid
import com.rosyidputrar.taskify.core.domain.calendar.calendarTasks
import com.rosyidputrar.taskify.core.domain.calendar.firstOfMonth
import com.rosyidputrar.taskify.core.domain.calendar.mergeActivity
import com.rosyidputrar.taskify.core.domain.calendar.shiftMonth
import com.rosyidputrar.taskify.core.domain.tasks.Task
import com.rosyidputrar.taskify.core.domain.tasks.parseQuickAdd
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.domain.time.todayFlow
import com.rosyidputrar.taskify.core.store.calendar.CalendarRepository
import com.rosyidputrar.taskify.core.store.calendar.DayAgenda
import com.rosyidputrar.taskify.core.store.tasks.NewTask
import com.rosyidputrar.taskify.core.store.tasks.TagRepository
import com.rosyidputrar.taskify.core.store.tasks.TaskRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Modul yang bisa disaring di legenda kalender. */
enum class CalendarKind(val label: String) { Tasks("Tugas"), Journal("Jurnal"), Notes("Catatan"), Finance("Keuangan"), Vault("Vault") }

data class CalendarUiState(
    val today: String = Dates.today(),
    val month: String = firstOfMonth(Dates.today()),
    val selected: String = Dates.today(),
    val grid: List<MonthCell> = emptyList(),
    val activity: Map<String, DayActivity> = emptyMap(),
    val tasksByDay: Map<String, List<CalendarTaskItem>> = emptyMap(),
    val agenda: DayAgenda = DayAgenda(),
    val visible: Set<CalendarKind> = CalendarKind.entries.toSet(),
    val loaded: Boolean = false,
) {
    val selectedActivity: DayActivity get() = activity[selected] ?: DayActivity()
    val selectedTasks: List<CalendarTaskItem> get() = tasksByDay[selected].orEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    initialDate: String?,
    private val calendar: CalendarRepository,
    private val tasks: TaskRepository,
    private val tags: TagRepository,
    private val messenger: Messenger,
) : ViewModel() {
    private val today = todayFlow().stateIn(viewModelScope, SharingStarted.Eagerly, Dates.today())
    private val selected = MutableStateFlow(initialDate ?: Dates.today())
    private val visible = MutableStateFlow(CalendarKind.entries.toSet())

    private data class MonthData(
        val month: String,
        val grid: List<MonthCell>,
        val activity: Map<String, DayActivity>,
        val tasksByDay: Map<String, List<CalendarTaskItem>>,
    )

    /** Data satu bulan (rentang grid penuh, termasuk hari bulan tetangga yang tampil). */
    private val monthData = combine(selected, today) { s, t -> firstOfMonth(s) to t }.flatMapLatest { (month, t) ->
        val grid = buildMonthGrid(month)
        val from = grid.first().date
        val to = grid.last().date
        combine(
            calendar.watchActivity(from, to),
            calendar.watchTasksInRange(from, to),
            calendar.watchActiveRecurring(to),
        ) { other, real, recurring ->
            val byDay = calendarTasks(real, recurring, from, to, t)
            MonthData(month, grid, mergeActivity(other, byDay), byDay)
        }
    }

    private val agenda = selected.flatMapLatest { date ->
        combine(calendar.watchJournal(date), calendar.watchNotes(date), calendar.watchTransactions(date), calendar.watchVault(date)) { j, n, t, v ->
            DayAgenda(j, n, t, v)
        }
    }

    val state: StateFlow<CalendarUiState> = combine(monthData, agenda, selected, visible, today) { m, a, s, v, t ->
        CalendarUiState(t, m.month, s, m.grid, m.activity, m.tasksByDay, a, v, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState(selected = selected.value, month = firstOfMonth(selected.value), grid = buildMonthGrid(firstOfMonth(selected.value))))

    fun select(date: String) { selected.value = date }

    fun shift(months: Long) {
        val current = selected.value
        val target = shiftMonth(current, months)
        // Pindah bulan → pilih hari ini bila bulannya bulan ini, selain itu tanggal 1.
        selected.value = if (firstOfMonth(today.value) == target) today.value else target
    }

    fun goToday() { selected.value = today.value }

    fun toggleKind(kind: CalendarKind) {
        val current = visible.value
        visible.value = if (kind in current && current.size > 1) current - kind else current + kind
    }

    /** Catat cepat di hari terpilih: tanpa tanggal di teks → tanggal yang sedang dibuka. */
    fun quickAdd(text: String) {
        if (text.isBlank()) return
        launchSafe {
            val p = parseQuickAdd(text, today.value)
            val id = tasks.create(NewTask(p.title, dueDate = p.dueDate ?: selected.value, dueTime = p.dueTime, priority = p.priority ?: 0))
            if (p.tags.isNotEmpty()) tags.addByName("task", id, p.tags)
            messenger.undoable("Tugas ditambahkan") { tasks.get(id)?.let { tasks.remove(it) } }
        }
    }

    fun toggle(task: Task) {
        launchSafe {
            val outcome = tasks.toggle(task, today.value)
            val undo = outcome.undo
            if (outcome.message != null && undo != null) messenger.undoable(outcome.message!!, undo)
        }
    }
}
