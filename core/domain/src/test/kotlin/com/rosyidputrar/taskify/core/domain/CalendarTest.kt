package com.rosyidputrar.taskify.core.domain

import com.rosyidputrar.taskify.core.domain.calendar.DayActivity
import com.rosyidputrar.taskify.core.domain.calendar.buildMonthGrid
import com.rosyidputrar.taskify.core.domain.calendar.buildWeek
import com.rosyidputrar.taskify.core.domain.calendar.calendarTasks
import com.rosyidputrar.taskify.core.domain.calendar.describeActivity
import com.rosyidputrar.taskify.core.domain.calendar.mergeActivity
import com.rosyidputrar.taskify.core.domain.calendar.shiftMonth
import com.rosyidputrar.taskify.core.domain.journal.moodLabel
import com.rosyidputrar.taskify.core.domain.tasks.Task
import com.rosyidputrar.taskify.core.domain.tasks.TaskStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalendarTest {
    private fun task(id: String, due: String, rule: String? = null, time: String? = null, status: TaskStatus = TaskStatus.Todo, priority: Int = 0) =
        Task(id, "u", null, null, null, id, null, status, priority, due, time, null, rule, null, 0.0, "", "", null)

    @Test fun monthGridStartsMondayAndCoversWholeWeeks() {
        val grid = buildMonthGrid("2026-10-15")
        assertEquals("2026-09-28", grid.first().date) // Senin
        assertEquals("2026-11-01", grid.last().date) // Minggu
        assertEquals(35, grid.size)
        assertEquals(31, grid.count { it.inMonth })
        assertEquals(42, buildMonthGrid("2026-08-01").size) // Agustus 2026 butuh 6 minggu
    }

    @Test fun weekAndMonthShift() {
        assertEquals("2026-10-05", buildWeek("2026-10-07").first())
        assertEquals("2026-10-11", buildWeek("2026-10-11").last())
        assertEquals("2026-12-01", shiftMonth("2026-10-31", 2))
        assertEquals("2026-09-01", shiftMonth("2026-10-07", -1))
    }

    @Test fun virtualOccurrencesAfterActiveRowAndNotInPast() {
        val weekly = task("w", "2026-10-01", "FREQ=WEEKLY;INTERVAL=1;BYDAY=TH")
        val map = calendarTasks(listOf(weekly), listOf(weekly), "2026-10-01", "2026-10-31", today = "2026-10-10")
        assertEquals(false, map["2026-10-01"]!!.single().virtual) // baris aktif nyata (terlambat)
        assertEquals(null, map["2026-10-08"]) // kejadian lampau tidak digambar
        assertEquals(listOf("2026-10-15", "2026-10-22", "2026-10-29"), map.filterValues { it.any { i -> i.virtual } }.keys.toList())
    }

    @Test fun orderingWithinDay() {
        val items = calendarTasks(
            listOf(
                task("done", "2026-10-07", status = TaskStatus.Done),
                task("noTime", "2026-10-07", priority = 3),
                task("late", "2026-10-07", time = "15:00:00"),
                task("early", "2026-10-07", time = "08:00:00"),
            ),
            emptyList(), "2026-10-01", "2026-10-31", "2026-10-07",
        )["2026-10-07"]!!
        assertEquals(listOf("early", "late", "noTime", "done"), items.map { it.task.id })
    }

    @Test fun mergeAndDescribe() {
        val tasks = calendarTasks(listOf(task("a", "2026-10-07"), task("b", "2026-10-07", status = TaskStatus.Done)), emptyList(), "2026-10-01", "2026-10-31", "2026-10-07")
        val merged = mergeActivity(mapOf("2026-10-07" to DayActivity(journal = 1, mood = 4, transactions = 2, expense = 50_000)), tasks)
        val day = merged["2026-10-07"]!!
        assertEquals(2, day.tasks)
        assertEquals(1, day.tasksDone)
        assertEquals("2 tugas (1 selesai), jurnal (mood Baik), 2 transaksi, pengeluaran Rp 50.000", describeActivity(day))
        assertTrue(DayActivity().isEmpty)
    }

    @Test fun moodLabels() {
        assertEquals("Sangat baik", moodLabel(4.6))
        assertEquals("Biasa", moodLabel(3))
        assertEquals(null, moodLabel(null))
    }
}
