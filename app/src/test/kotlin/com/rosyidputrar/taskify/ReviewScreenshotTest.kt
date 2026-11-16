package com.rosyidputrar.taskify

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.finance.CategorySlice
import com.rosyidputrar.taskify.core.domain.review.DayPoint
import com.rosyidputrar.taskify.core.domain.review.HeatDay
import com.rosyidputrar.taskify.core.domain.review.PeriodStats
import com.rosyidputrar.taskify.core.domain.review.Review
import com.rosyidputrar.taskify.core.domain.review.ReviewPeriod
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.store.review.BestMood
import com.rosyidputrar.taskify.core.store.review.BigExpense
import com.rosyidputrar.taskify.feature.review.ReviewActions
import com.rosyidputrar.taskify.feature.review.ReviewContent
import com.rosyidputrar.taskify.feature.review.ReviewNav
import com.rosyidputrar.taskify.feature.review.ReviewUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

object SampleReview {
    const val TODAY = "2026-11-19"

    /** Pola aktivitas deterministik 26 minggu (tanpa acak). */
    private val heat: Map<String, HeatDay> = Review.heatRange(TODAY).let { r ->
        (0 until r.days).mapNotNull { i ->
            val day = Dates.addDays(r.from, i.toLong())
            if (day > TODAY) return@mapNotNull null
            val v = (i * 7 + i / 5) % 11
            if (v < 3) null else day to HeatDay(day, tasks = v / 2, journal = if (i % 3 == 0) 1 else 0, transactions = v % 4, vault = if (i % 17 == 0) 2 else 0)
        }.toMap()
    }

    fun state(period: ReviewPeriod = ReviewPeriod.Week): ReviewUiState {
        val ranges = Review.periodRange(period, TODAY)
        val daily = Review.fillDays(ranges.current, (0 until ranges.current.days).associate { i ->
            val day = Dates.addDays(ranges.current.from, i.toLong())
            day to if (day > TODAY) DayPoint(day) else DayPoint(day, tasksDone = listOf(3, 5, 2, 6, 1, 4, 2)[i % 7], expense = listOf(85_000L, 42_000L, 310_000L, 25_000L, 120_000L, 0L, 64_000L)[i % 7], journal = if (i % 2 == 0) 1 else 0, mood = if (i % 2 == 0) listOf(4.0, 3.0, 5.0, 2.0)[i % 4] else null)
        })
        return ReviewUiState(
            period = period, today = TODAY, ranges = ranges,
            now = PeriodStats(tasksDone = 16, tasksCreated = 19, tasksDue = 14, tasksDueDone = 11, income = 7_500_000, expense = 862_000, txCount = 21,
                journalEntries = 3, journalDays = 2, moodAvg = 3.7, snippetsNew = 2, versionsNew = 1),
            before = PeriodStats(tasksDone = 12, expense = 1_040_000),
            daily = daily,
            categories = listOf(
                CategorySlice("c1", "Makan & minum", "orange", "utensils", 412_000, 9),
                CategorySlice("c2", "Transportasi", "sky", "car", 230_000, 6),
                CategorySlice("c3", "Belanja", "rose", "shopping-bag", 145_000, 2),
                CategorySlice("c4", "Langganan", "violet", "tv", 75_000, 1),
            ),
            bigExpense = BigExpense("Servis motor", 310_000, "2026-11-18"),
            bestMood = BestMood("j1", "Rilis Tahap 8 berjalan mulus", "2026-11-17", 5),
            heat = heat,
        )
    }

    object Actions : ReviewActions {
        override fun setPeriod(period: ReviewPeriod) = Unit
        override fun shift(dir: Int) = Unit
        override fun goToday() = Unit
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReviewScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val padding = PaddingValues(top = 24.dp, bottom = 24.dp)

    private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent { TaskifyTheme(darkTheme = dark) { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Config(qualifiers = "w393dp-h2300dp-xxhdpi")
    @Test fun week() = shot("review_week_light", false) { ReviewContent(SampleReview.state(), SampleReview.Actions, ReviewNav(), padding) }

    @Config(qualifiers = "w393dp-h2300dp-xxhdpi")
    @Test fun monthDark() = shot("review_month_dark", true) { ReviewContent(SampleReview.state(ReviewPeriod.Month), SampleReview.Actions, ReviewNav(), padding) }
}
