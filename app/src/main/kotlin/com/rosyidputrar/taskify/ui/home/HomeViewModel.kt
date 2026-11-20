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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import com.rosyidputrar.taskify.core.domain.home.HomeShortcut
import com.rosyidputrar.taskify.core.domain.home.HomeShortcuts
import com.rosyidputrar.taskify.core.domain.home.ShortcutRef
import com.rosyidputrar.taskify.core.domain.home.ShortcutType
import com.rosyidputrar.taskify.core.domain.search.EntityRow
import com.rosyidputrar.taskify.core.store.home.HomeShortcutRepository
import com.rosyidputrar.taskify.core.store.search.SearchRepository
import com.rosyidputrar.taskify.ui.shell.AppEvent
import com.rosyidputrar.taskify.ui.shell.AppEvents
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
    /** Pintasan yang disematkan (termasuk yang sasarannya sudah tidak ada — untuk Susun Beranda). */
    val pinned: List<HomeShortcut> = emptyList(),
    /** "Lanjutkan" otomatis: isi yang terakhir diubah. */
    val recent: List<HomeShortcut> = emptyList(),
    /** Lembar "Tambah pintasan". */
    val picker: ShortcutPickerUi = ShortcutPickerUi(),
) {
    /** Isi baris Pintasan sesuai susunan (sematan dulu, lalu Lanjutkan bila aktif). */
    val shortcutRow: List<HomeShortcut> get() = HomeShortcuts.row(pinned, recent, layout.showRecent)
}

/** Isi lembar "Tambah pintasan": hasil cari (bila ada kata kunci) + daftar/dompet/tag. */
data class ShortcutPickerUi(
    val query: String = "",
    val results: List<HomeShortcut> = emptyList(),
    val collections: List<HomeShortcut> = emptyList(),
    val pinnedKeys: Set<String> = emptySet(),
    val searching: Boolean = false,
)

/** Aksi pintasan Hari ini — default tanpa efek supaya konten bisa dirender di tes. */
interface HomeShortcutActions {
    fun open(ref: ShortcutRef) {}
    fun togglePin(shortcut: HomeShortcut) {}
    fun move(ref: ShortcutRef, by: Int) {}
    fun removeUnavailable() {}
    fun setPickerQuery(query: String) {}
}

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
    private val shortcuts: HomeShortcutRepository? = null,
    private val search: SearchRepository? = null,
) : ViewModel(), HomeShortcutActions {
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
        combine(parts, home.watchJournalStreak(t), home.watchActivity(t), extras, shortcutParts()) { p, streak, activity, x, sc ->
            HomeUiState(
                t, p.focus, p.progress, p.summary, streak, activity, loaded = true, rejectedChanges = x.rejected, week = x.week,
                layout = x.layout, device = x.device, pinned = sc.pinned, recent = sc.recent, picker = sc.picker,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private data class ShortcutParts(val pinned: List<HomeShortcut>, val recent: List<HomeShortcut>, val picker: ShortcutPickerUi)

    private val pickerQuery = MutableStateFlow("")

    /** Hasil cari lembar "Tambah pintasan" — ditunda sebentar supaya tidak mencari tiap ketukan. */
    @OptIn(FlowPreview::class)
    private val pickerResults: Flow<Pair<String, List<HomeShortcut>>> = pickerQuery
        .debounce { if (it.isBlank()) 0L else 200L }
        .mapLatest { q -> q to if (q.isBlank()) emptyList() else runCatching { search?.search(q, 20).orEmpty() }.getOrDefault(emptyList()).mapNotNull { it.toShortcut() } }

    private fun shortcutParts(): Flow<ShortcutParts> {
        val repo = shortcuts ?: return flowOf(ShortcutParts(emptyList(), emptyList(), ShortcutPickerUi()))
        val picker = combine(pickerQuery, pickerResults, repo.watchCollections(), repo.watchPinnedKeys()) { q, (forQuery, results), collections, keys ->
            ShortcutPickerUi(q, if (forQuery == q) results else emptyList(), collections, keys, searching = q.isNotBlank() && forQuery != q)
        }
        return combine(repo.watchPinned(), repo.watchRecent(), picker) { pinned, recent, pk -> ShortcutParts(pinned, recent, pk) }
    }

    override fun open(ref: ShortcutRef) = AppEvents.send(AppEvent.OpenShortcut(ref))

    override fun togglePin(shortcut: HomeShortcut) {
        val repo = shortcuts ?: return
        launchSafe {
            when (repo.toggle(shortcut.ref, shortcut.title)) {
                null -> messenger.show("Maksimal ${HomeShortcuts.MAX_PINNED} pintasan. Lepas salah satu dulu.")
                false -> messenger.undoable("Dilepas dari Hari ini") { repo.pin(shortcut.ref, shortcut.title) }
                true -> Unit
            }
        }
    }

    override fun move(ref: ShortcutRef, by: Int) {
        val repo = shortcuts ?: return
        launchSafe { repo.move(ref, by) }
    }

    override fun removeUnavailable() {
        val repo = shortcuts ?: return
        val gone = state.value.pinned.filterNot { it.available }.map { it.ref.key }
        launchSafe { repo.removeUnavailable(gone) }
    }

    override fun setPickerQuery(query: String) { pickerQuery.value = query }

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

/** Hasil pencarian → calon pintasan (jenis entitas sama dengan kode pintasan). */
internal fun EntityRow.toShortcut(): HomeShortcut? {
    val type = ShortcutType.from(type.value) ?: return null
    val sub = when (type) {
        ShortcutType.Transaction -> subtitle?.substringBefore('|')
        else -> subtitle
    }
    return HomeShortcut(ShortcutRef(type, id), title.ifBlank { type.label }, sub)
}
