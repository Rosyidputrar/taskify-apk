package com.rosyidputrar.taskify.core.domain.reminders

import com.rosyidputrar.taskify.core.domain.finance.BudgetLevel
import com.rosyidputrar.taskify.core.domain.finance.BudgetStatus
import com.rosyidputrar.taskify.core.domain.finance.Recurring
import com.rosyidputrar.taskify.core.domain.finance.TxKind
import com.rosyidputrar.taskify.core.domain.time.Dates
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContextRemindersTest {
    private fun bill(id: String, next: String?, auto: Boolean = false, active: Boolean = true) =
        Recurring(id, TxKind.Expense, 54_990, "w1", null, null, "Netflix", null, "FREQ=MONTHLY;INTERVAL=1", "2026-01-01", null, next, auto, active)

    @Test fun eachDueBillIsAnnouncedOncePerOccurrence() {
        val today = "2026-11-16"
        val list = listOf(bill("a", "2026-11-16"), bill("b", "2026-11-20"), bill("c", "2026-11-10", auto = true), bill("d", "2026-11-01", active = false))
        val first = ContextReminders.billsToNotify(list, today, emptySet())
        assertEquals(listOf("a"), first.map { it.id }, "hanya manual, aktif, jatuh tempo")
        val notified = first.map(ContextReminders::billKey).toSet()
        assertTrue(ContextReminders.billsToNotify(list, today, notified).isEmpty(), "tidak diulang")
        // Kejadian berikutnya (bulan depan) diumumkan lagi.
        val nextMonth = listOf(bill("a", "2026-12-16"))
        assertEquals(1, ContextReminders.billsToNotify(nextMonth, "2026-12-16", notified).size)
    }

    private fun budget(id: String, amount: Long, spent: Long, alert: Int = 80) =
        BudgetStatus(id, null, amount, alert, "2026-11-01", null, "Makan", null, null, spent)

    @Test fun budgetWarnsThenOverEachOnce() {
        val month = "2026-11-01"
        val warn = budget("b1", 1_000_000, 850_000)
        assertEquals(BudgetLevel.Warn, warn.level)
        val first = ContextReminders.budgetAlerts(listOf(warn, budget("b2", 1_000_000, 100_000)), month, emptySet())
        assertEquals(listOf("b1"), first.map { it.id })
        val notified = setOf(ContextReminders.budgetKey(warn, month, BudgetLevel.Warn))
        assertTrue(ContextReminders.budgetAlerts(listOf(warn), month, notified).isEmpty())
        val over = warn.copy(spent = 1_050_000)
        assertEquals(listOf("b1"), ContextReminders.budgetAlerts(listOf(over), month, notified).map { it.id }, "naik ke Over → sekali lagi")
    }

    @Test fun journalNudgeOnlyProtectsAStreak() {
        assertTrue(ContextReminders.shouldNudgeJournal(streak = 4, wroteToday = false))
        assertFalse(ContextReminders.shouldNudgeJournal(streak = 4, wroteToday = true))
        assertFalse(ContextReminders.shouldNudgeJournal(streak = 0, wroteToday = false))
    }

    @Test fun nextNudgeTimeIsInWib() {
        // 2026-11-16 12:00 WIB → 20:00 WIB = 8 jam lagi.
        val noon = Dates.localDateTimeToMillis("2026-11-16", "12:00")
        assertEquals(8 * 3_600_000L, ContextReminders.delayUntil("20:00", noon))
        // Sudah lewat → besok.
        val night = Dates.localDateTimeToMillis("2026-11-16", "21:00")
        assertEquals(23 * 3_600_000L, ContextReminders.delayUntil("20:00", night))
    }

    @Test fun oldKeysArePruned() {
        val keys = setOf("a|2026-01-01", "a|2026-11-01", "b1|2026-11-01|Warn", "aneh")
        assertEquals(setOf("a|2026-11-01", "b1|2026-11-01|Warn", "aneh"), ContextReminders.pruneKeys(keys, "2026-11-16"))
    }
}
