package com.rosyidputrar.taskify.feature.journal

import com.rosyidputrar.taskify.core.designsystem.component.launchSafe
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.domain.journal.JournalEntry
import com.rosyidputrar.taskify.core.domain.journal.MoodPoint
import com.rosyidputrar.taskify.core.domain.journal.MoodStats
import com.rosyidputrar.taskify.core.domain.journal.journalStreak
import com.rosyidputrar.taskify.core.domain.journal.moodStats
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.domain.time.todayFlow
import com.rosyidputrar.taskify.core.store.journal.JournalRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class JournalTab(val label: String) { Shelf("Rak"), Insight("Insight") }

data class JournalFilter(val search: String = "", val mood: Int? = null, val trash: Boolean = false)

data class MonthGroup(val key: String, val label: String, val entries: List<JournalEntry>)

data class JournalInsight(
    val range: Int = 30,
    val series: List<MoodPoint> = emptyList(),
    val stats: MoodStats? = null,
    val onThisDay: List<JournalEntry> = emptyList(),
)

data class JournalUiState(
    val today: String = "",
    val tab: JournalTab = JournalTab.Shelf,
    val filter: JournalFilter = JournalFilter(),
    val pinned: List<JournalEntry> = emptyList(),
    val groups: List<MonthGroup> = emptyList(),
    val total: Int = 0,
    val streak: Int = 0,
    val todayCount: Int = 0,
    val insight: JournalInsight = JournalInsight(),
    val loaded: Boolean = false,
)

/** Aksi layar Jurnal — dipisah agar konten bisa dirender tanpa database (tes tangkapan layar). */
interface JournalActions {
    fun setTab(tab: JournalTab)
    fun setSearch(text: String)
    fun setMood(mood: Int?)
    fun toggleTrash()
    fun setRange(days: Int)
    fun restore(entry: JournalEntry)
    /** Pindahkan ke Sampah (geser ke kiri) — bisa diurungkan dari snackbar. */
    fun remove(entry: JournalEntry) {}
}

fun groupByMonth(entries: List<JournalEntry>): List<MonthGroup> =
    entries.groupBy { it.entryDate.take(7) }.map { (key, items) -> MonthGroup(key, Dates.format("$key-01", "MMMM yyyy"), items) }

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class JournalViewModel(
    private val repo: JournalRepository,
    private val messenger: Messenger,
) : ViewModel(), JournalActions {
    private val tab = MutableStateFlow(JournalTab.Shelf)
    private val filter = MutableStateFlow(JournalFilter())
    private val range = MutableStateFlow(30)
    private val today = todayFlow().stateIn(viewModelScope, SharingStarted.Eagerly, Dates.today())

    private val queryFilter = filter.debounce { if (it.search.isEmpty()) 0 else 200 }.distinctUntilChanged()
    private val entries = queryFilter.flatMapLatest { f -> repo.watchEntries(f.search, f.mood, f.trash) }

    private data class Meta(val streak: Int, val todayCount: Int)
    private val meta = today.flatMapLatest { t ->
        combine(repo.watchDays(), repo.watchOnDate(t)) { days, onDate -> Meta(journalStreak(days, t), onDate.size) }
    }

    private val insight = combine(today, range) { t, r -> t to r }.flatMapLatest { (t, r) ->
        combine(repo.watchMoodSeries(t, r), repo.watchOnThisDay(t)) { series, past -> JournalInsight(r, series, moodStats(series), past) }
    }

    val state: StateFlow<JournalUiState> = combine(entries, meta, insight, filter, combine(tab, today) { a, b -> a to b }) { list, m, ins, f, (tb, t) ->
        val pinned = if (f.trash) emptyList() else list.filter { it.pinned }
        val rest = if (f.trash) list else list.filterNot { it.pinned }
        JournalUiState(
            today = t,
            tab = tb,
            filter = f,
            pinned = pinned,
            groups = groupByMonth(rest),
            total = list.size,
            streak = m.streak,
            todayCount = m.todayCount,
            insight = ins,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalUiState(today = today.value))

    override fun setTab(tab: JournalTab) { this.tab.value = tab }
    override fun setSearch(text: String) { filter.value = filter.value.copy(search = text) }
    override fun setMood(mood: Int?) { filter.value = filter.value.copy(mood = mood) }
    override fun toggleTrash() { filter.value = filter.value.copy(trash = !filter.value.trash) }
    override fun setRange(days: Int) { range.value = days }

    override fun restore(entry: JournalEntry) {
        launchSafe {
            repo.restore(entry.id)
            messenger.show("Entri jurnal dipulihkan")
        }
    }

    override fun remove(entry: JournalEntry) {
        launchSafe {
            repo.remove(entry.id)
            messenger.undoable("Entri dipindahkan ke Sampah") { repo.restore(entry.id) }
        }
    }
}

/** Ringkasan untuk subjudul header. */
fun JournalUiState.subtitle(): String = buildString {
    append(if (streak > 0) "Streak $streak hari" else "Belum ada streak")
    if (total > 0 && !filter.trash) append(" · $total entri")
}

internal val JournalUiState.isEmpty: Boolean get() = pinned.isEmpty() && groups.isEmpty()

internal fun MoodStats.averageLabel(): String = average?.let { "%.1f".format(it).replace('.', ',') } ?: "–"

internal val ALL_RANGES = listOf(30, 90)
