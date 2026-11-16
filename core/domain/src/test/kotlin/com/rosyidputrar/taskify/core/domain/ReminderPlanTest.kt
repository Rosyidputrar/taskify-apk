package com.rosyidputrar.taskify.core.domain

import com.rosyidputrar.taskify.core.domain.tasks.ReminderPlanner
import com.rosyidputrar.taskify.core.domain.time.Dates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderPlanTest {
    private val now = Dates.parseTimestampMillis("2026-10-07T02:00:00.000Z")!!
    private val past = "2026-10-07T01:59:30.000Z" // 30 dtk lalu (masih dalam jeda)
    private val future = "2026-10-07T03:00:00.000Z"
    private val later = "2026-10-07T04:00:00.000Z"

    @Test fun newFutureReminderIsSetOnce() {
        val first = ReminderPlanner.plan(mapOf("a" to future), emptyMap(), now)
        assertEquals(listOf("a"), first.set.map { it.taskId })
        assertEquals(Dates.parseTimestampMillis(future), first.set.single().atMillis)
        assertEquals(mapOf("a" to future), first.scheduled)

        // Perubahan tabel lain (tugas lain diedit, data tersinkron) → tidak dipasang ulang.
        val again = ReminderPlanner.plan(mapOf("a" to future), first.scheduled, now + 1_000)
        assertTrue(again.set.isEmpty())
        assertTrue(again.cancel.isEmpty())
        assertEquals(first.scheduled, again.scheduled)
    }

    @Test fun firedReminderDoesNotFireAgainOnLaterChanges() {
        // Dipasang saat masih akan datang…
        val scheduled = ReminderPlanner.plan(mapOf("a" to future), emptyMap(), now).scheduled
        val afterFire = Dates.parseTimestampMillis(future)!! + 20_000
        // …lalu berbunyi; 20 dtk kemudian tabel berubah. Inilah bug lama: alarm lampau dipasang
        // ulang dan langsung berbunyi lagi.
        val plan = ReminderPlanner.plan(mapOf("a" to future), scheduled, afterFire)
        assertTrue(plan.set.isEmpty(), "alarm yang sudah berbunyi tidak boleh dipasang ulang")
        assertTrue(plan.cancel.isEmpty())
    }

    @Test fun changedReminderIsRescheduled() {
        val scheduled = mapOf("a" to future)
        val plan = ReminderPlanner.plan(mapOf("a" to later), scheduled, now)
        assertEquals(listOf("a" to later), plan.set.map { it.taskId to it.remindAt })
        assertEquals(mapOf("a" to later), plan.scheduled)
    }

    @Test fun snoozeAfterFiringSchedulesAgain() {
        val scheduled = mapOf("a" to past)
        val snoozed = "2026-10-07T02:10:00.000Z"
        val plan = ReminderPlanner.plan(mapOf("a" to snoozed), scheduled, now)
        assertEquals(listOf(snoozed), plan.set.map { it.remindAt })
    }

    @Test fun justDueReminderArrivingLateFiresOnce() {
        // Dibuat di laptop untuk "barusan", tiba lewat sinkron 30 dtk kemudian → bunyi sekali.
        val first = ReminderPlanner.plan(mapOf("a" to past), emptyMap(), now)
        assertEquals(listOf("a"), first.set.map { it.taskId })
        val second = ReminderPlanner.plan(mapOf("a" to past), first.scheduled, now + 5_000)
        assertTrue(second.set.isEmpty())
    }

    @Test fun removedOrCompletedTasksAreCancelled() {
        val plan = ReminderPlanner.plan(mapOf("b" to future), mapOf("a" to future, "b" to future), now)
        assertEquals(setOf("a"), plan.cancel)
        assertTrue(plan.set.isEmpty())
        assertEquals(mapOf("b" to future), plan.scheduled)
    }

    @Test fun forceReschedulesOnlyFutureAlarms() {
        // Setelah reboot AlarmManager kosong: semua yang akan datang dipasang ulang, yang sudah
        // lewat (walau masih dalam jeda) tidak dibunyikan lagi.
        val scheduled = mapOf("a" to future, "b" to past)
        val plan = ReminderPlanner.plan(scheduled, scheduled, now, force = true)
        assertEquals(listOf("a"), plan.set.map { it.taskId })
    }

    @Test fun legacyStateWithoutTimestampsDoesNotRefirePastReminders() {
        // Data versi lama hanya menyimpan id tugas.
        val legacy = mapOf("a" to ReminderPlanner.UNKNOWN, "b" to ReminderPlanner.UNKNOWN)
        val plan = ReminderPlanner.plan(mapOf("a" to future, "b" to past), legacy, now)
        assertEquals(listOf("a"), plan.set.map { it.taskId })
        assertEquals(mapOf("a" to future, "b" to past), plan.scheduled)
    }

    @Test fun unparseableTimestampIsIgnored() {
        val plan = ReminderPlanner.plan(mapOf("a" to "bukan-waktu"), emptyMap(), now)
        assertTrue(plan.set.isEmpty())
        assertTrue(plan.scheduled.isEmpty())
    }
}
