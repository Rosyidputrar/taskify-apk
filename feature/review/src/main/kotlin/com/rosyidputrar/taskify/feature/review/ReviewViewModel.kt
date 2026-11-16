package com.rosyidputrar.taskify.feature.review

import kotlinx.coroutines.flow.flowOf
import com.rosyidputrar.taskify.core.store.focus.FocusStats
import com.rosyidputrar.taskify.core.store.focus.FocusRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.domain.finance.CategoryKind
import com.rosyidputrar.taskify.core.domain.finance.CategorySlice
import com.rosyidputrar.taskify.core.domain.review.DayPoint
import com.rosyidputrar.taskify.core.domain.review.HeatDay
import com.rosyidputrar.taskify.core.domain.review.PeriodRanges
import com.rosyidputrar.taskify.core.domain.review.PeriodStats
import com.rosyidputrar.taskify.core.domain.review.Review
import com.rosyidputrar.taskify.core.domain.review.ReviewPeriod
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.domain.time.todayFlow
import com.rosyidputrar.taskify.core.store.finance.FinanceRepository
import com.rosyidputrar.taskify.core.store.review.BestMood
import com.rosyidputrar.taskify.core.store.review.BigExpense
import com.rosyidputrar.taskify.core.store.review.ReviewRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class ReviewUiState(
    val period: ReviewPeriod = ReviewPeriod.Week,
    val today: String = Dates.today(),
    val ranges: PeriodRanges = Review.periodRange(ReviewPeriod.Week, Dates.today()),
    val now: PeriodStats? = null,
    val before: PeriodStats = PeriodStats(),
    val daily: List<DayPoint> = emptyList(),
    val categories: List<CategorySlice> = emptyList(),
    val bigExpense: BigExpense? = null,
    val bestMood: BestMood? = null,
    val heat: Map<String, HeatDay> = emptyMap(),
    /** Mode Fokus (riwayat lokal perangkat ini). */
    val focus: FocusStats = FocusStats(),
    val focusBefore: FocusStats = FocusStats(),
) {
    val isCurrent get() = today in ranges.current
    val elapsed get() = Review.elapsedDays(ranges.current, today)
    val label get() = Review.label(period, ranges.current)
    val heatGrid get() = Review.heatGrid(today, heat)
}

interface ReviewActions {
    fun setPeriod(period: ReviewPeriod)
    fun shift(dir: Int)
    fun goToday()
}

private data class Selection(val period: ReviewPeriod, val anchor: String?)

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModel(
    private val repo: ReviewRepository,
    private val finance: FinanceRepository,
    private val focus: FocusRepository? = null,
) : ViewModel(), ReviewActions {
    private val selection = MutableStateFlow(Selection(ReviewPeriod.Week, null))

    val state: StateFlow<ReviewUiState> = combine(selection, todayFlow()) { s, t -> s to t }
        .flatMapLatest { (s, today) ->
            val ranges = Review.periodRange(s.period, s.anchor ?: today)
            val cur = ranges.current
            val stats = combine(repo.watchStats(cur), repo.watchStats(ranges.previous)) { a, b -> a to b }
            val highlights = combine(repo.watchBigExpense(cur), repo.watchBestMood(cur)) { a, b -> a to b }
            val focusStats = focus?.let { f -> combine(f.watchStats(cur.from, cur.to), f.watchStats(ranges.previous.from, ranges.previous.to)) { a, b -> a to b } }
                ?: flowOf(FocusStats() to FocusStats())
            val main = combine(stats, repo.watchDaily(cur), finance.watchCategoryBreakdown(cur.from, cur.to, CategoryKind.Expense), highlights, repo.watchHeatmap(Review.heatRange(today))) { (now, before), daily, cats, (big, mood), heat ->
                ReviewUiState(s.period, today, ranges, now, before, Review.fillDays(cur, daily), cats.take(5), big, mood, heat)
            }
            combine(main, focusStats) { st, (f, fb) -> st.copy(focus = f, focusBefore = fb) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewUiState())

    override fun setPeriod(period: ReviewPeriod) { selection.value = selection.value.copy(period = period) }

    override fun shift(dir: Int) {
        val s = selection.value
        val anchor = Review.shift(s.period, s.anchor ?: state.value.today, dir)
        // Tidak melompat ke masa depan
        selection.value = s.copy(anchor = if (anchor > state.value.today) null else anchor)
    }

    override fun goToday() { selection.value = selection.value.copy(anchor = null) }
}
