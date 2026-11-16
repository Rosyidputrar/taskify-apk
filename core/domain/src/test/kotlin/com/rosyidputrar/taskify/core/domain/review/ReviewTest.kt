package com.rosyidputrar.taskify.core.domain.review

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReviewTest {
    @Test fun weekAndMonthRanges() {
        // 2026-11-16 = Senin
        val w = Review.periodRange(ReviewPeriod.Week, "2026-11-19")
        assertEquals(Range("2026-11-16", "2026-11-22"), w.current)
        assertEquals(Range("2026-11-09", "2026-11-15"), w.previous)
        val m = Review.periodRange(ReviewPeriod.Month, "2026-03-31")
        assertEquals(Range("2026-03-01", "2026-03-31"), m.current)
        assertEquals(Range("2026-02-01", "2026-02-28"), m.previous)
        assertEquals("2026-11-12", Review.shift(ReviewPeriod.Week, "2026-11-19", -1))
        assertEquals("2026-02-28", Review.shift(ReviewPeriod.Month, "2026-03-31", -1))
        assertEquals("16 Nov – 22 Nov 2026", Review.label(ReviewPeriod.Week, w.current))
    }

    @Test fun elapsed() {
        val r = Range("2026-11-16", "2026-11-22")
        assertEquals(3, Review.elapsedDays(r, "2026-11-18"))
        assertEquals(7, Review.elapsedDays(r, "2026-12-01"))
    }

    @Test fun heatLevelAndPercent() {
        assertEquals(listOf(0, 1, 2, 3, 4), listOf(0, 1, 3, 5, 8).map { Review.heatLevel(it, 8) })
        assertEquals(50, Review.percentChange(15, 10))
        assertEquals(0, Review.percentChange(0, 0))
        assertNull(Review.percentChange(5, 0))
        assertEquals(-33, Review.percentChange(2, 3))
        assertEquals(-50, Review.percentChange(1, 2))
    }

    @Test fun heatGrid() {
        val g = Review.heatGrid("2026-11-18", mapOf(
            "2026-11-17" to HeatDay("2026-11-17", tasks = 2, journal = 1),
            "2026-05-01" to HeatDay("2026-05-01", tasks = 9), // di luar 26 minggu
        ))
        assertEquals(26, g.columns.size)
        assertEquals("2026-11-22", g.end)
        assertEquals("2026-05-25", g.start)
        assertEquals("2026-11-16", g.columns.last().first())
        assertEquals(3, g.max)
        assertEquals(1, g.activeDays)
    }

    @Test fun summaryText() {
        val now = PeriodStats(tasksDone = 12, expense = 1_250_000, journalDays = 4, moodAvg = 3.8, snippetsNew = 2, versionsNew = 1)
        val before = PeriodStats(tasksDone = 8, expense = 1_000_000)
        assertEquals(
            "Kamu menyelesaikan 12 tugas (+50% dari minggu lalu), berjurnal 4 dari 7 hari, mengeluarkan Rp 1.250.000 (+25%). " +
                "Rata-rata perasaanmu baik. Vault bertambah 2 snippet & 1 versi project.",
            Review.summary(now, before, ReviewPeriod.Week, 7),
        )
        assertEquals(
            "Kamu menyelesaikan 0 tugas, berjurnal 0 dari 3 hari, mengeluarkan Rp 0.",
            Review.summary(PeriodStats(), PeriodStats(), ReviewPeriod.Month, 3),
        )
    }

    @Test fun bestDayAndFill() {
        val pts = Review.fillDays(Range("2026-11-16", "2026-11-18"), mapOf("2026-11-17" to DayPoint("2026-11-17", tasksDone = 3)))
        assertEquals(3, pts.size)
        assertEquals("2026-11-17", Review.bestDay(pts)?.day)
        assertNull(Review.bestDay(Review.fillDays(Range("2026-11-16", "2026-11-17"), emptyMap())))
        assertEquals(75, Review.onTimePercent(PeriodStats(tasksDue = 4, tasksDueDone = 3)))
    }
}
