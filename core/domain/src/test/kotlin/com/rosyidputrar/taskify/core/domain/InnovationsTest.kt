package com.rosyidputrar.taskify.core.domain

import com.rosyidputrar.taskify.core.domain.device.NotifCategory
import com.rosyidputrar.taskify.core.domain.finance.Debt
import com.rosyidputrar.taskify.core.domain.finance.DebtDirection
import com.rosyidputrar.taskify.core.domain.finance.DraftIntelligence
import com.rosyidputrar.taskify.core.domain.finance.Recurring
import com.rosyidputrar.taskify.core.domain.finance.TxKind
import com.rosyidputrar.taskify.core.domain.finance.buildCancelReminderPlan
import com.rosyidputrar.taskify.core.domain.finance.monthlyEquivalent
import com.rosyidputrar.taskify.core.domain.finance.summarizeSubscriptions
import com.rosyidputrar.taskify.core.domain.focus.DigestKind
import com.rosyidputrar.taskify.core.domain.focus.FocusDigestInput
import com.rosyidputrar.taskify.core.domain.focus.summarizeFocusNotifs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InnovationsTest {
    private val today = "2026-11-21"

    private fun rec(
        id: String,
        title: String,
        amount: Long,
        rrule: String,
        next: String?,
        kind: TxKind = TxKind.Expense,
        active: Boolean = true,
        autoPost: Boolean = true,
    ) = Recurring(
        id = id, kind = kind, amount = amount, walletId = "w1", toWalletId = null, categoryId = null,
        title = title, note = null, rrule = rrule, startOn = "2026-01-01", endOn = null, nextRunOn = next,
        autoPost = autoPost, active = active,
    )

    @Test fun subscriptionBurnAndUpcomingSummary() {
        assertEquals(100_000L, monthlyEquivalent(1_200_000, "FREQ=YEARLY;INTERVAL=1"))
        assertEquals(50_000L, monthlyEquivalent(100_000, "FREQ=MONTHLY;INTERVAL=2"))

        val items = listOf(
            rec("1", "Netflix", 186_000, "FREQ=MONTHLY;INTERVAL=1", "2026-11-25"),
            rec("2", "Domain", 240_000, "FREQ=YEARLY;INTERVAL=1", "2026-11-22"),
            rec("3", "ChatGPT", 300_000, "FREQ=MONTHLY;INTERVAL=1", "2026-12-20"), // >14 hari, tetap masuk burn rate
            rec("4", "Gaji", 8_000_000, "FREQ=MONTHLY;INTERVAL=1", "2026-11-25", kind = TxKind.Income), // bukan pengeluaran
            rec("5", "Spotify (dijeda)", 55_000, "FREQ=MONTHLY;INTERVAL=1", "2026-11-23", active = false),
        )
        val burn = summarizeSubscriptions(items, today, withinDays = 14)
        assertEquals(3, burn.activeExpenseCount)
        assertEquals(186_000L + 20_000L + 300_000L, burn.monthlyExpense)
        assertEquals(burn.monthlyExpense * 12, burn.yearlyExpense)
        // Domain (1 hari) lebih dekat dari Netflix (4 hari); ChatGPT di luar 14 hari.
        assertEquals(listOf("Domain" to 1L, "Netflix" to 4L), burn.upcoming.map { it.recurring.title to it.daysUntil })
    }

    @Test fun cancelReminderPlanComputesLeadDaysAndClampsPastToToday() {
        val normal = buildCancelReminderPlan("ChatGPT", 300_000, "2027-02-06", daysBefore = 3, today = today, autoPost = true)!!
        assertEquals("2027-02-06", normal.dueDate)
        assertEquals("2027-02-03", normal.remindDate)
        assertEquals("2027-02-03T02:00:00.000Z", normal.remindAt)
        assertTrue(normal.title.contains("ChatGPT"))

        // Jatuh tempo besok, minta H-7 → tidak boleh mundur ke masa lalu; dijepit ke today.
        val clamped = buildCancelReminderPlan("Domain", 240_000, "2026-11-22", daysBefore = 7, today = today)!!
        assertEquals(today, clamped.remindDate)
    }

    @Test fun draftIntelligenceMatchesOpenDebtAndNormalizesMerchant() {
        assertEquals("mytelkomsel apps", DraftIntelligence.normalizeMerchantKey("  MyTelkomsel-Apps!! "))
        assertNull(DraftIntelligence.normalizeMerchantKey("x"))

        val lentBudi = Debt("d1", DebtDirection.Lent, "Budi Santoso", 250_000, "2026-11-30", null, settledAt = null, createdAt = "")
        val borrowedAndi = Debt("d2", DebtDirection.Borrowed, "Andi", 150_000, "2026-11-25", null, settledAt = null, createdAt = "")
        val settledCici = Debt("d3", DebtDirection.Lent, "Cici", 250_000, null, null, settledAt = "2026-11-01T00:00:00Z", createdAt = "")
        val debts = listOf(lentBudi, borrowedAndi, settledCici)

        // Uang masuk dari Budi 250rb → cocok Piutang Budi.
        assertEquals(lentBudi, DraftIntelligence.matchOpenDebt(debts, TxKind.Income, 250_000, "Budi", "Dana Masuk", "Transfer masuk dari BUDI SANTOSO"))
        // Arah salah (uang keluar ke Budi) → tidak cocok Piutang.
        assertNull(DraftIntelligence.matchOpenDebt(debts, TxKind.Expense, 250_000, "Budi", "Transfer", "Transfer ke BUDI"))
        // Uang keluar ke Andi 150rb → cocok Hutang ke Andi.
        assertEquals(borrowedAndi, DraftIntelligence.matchOpenDebt(debts, TxKind.Expense, 150_000, "Andi", "Transfer", "Transfer ke ANDI Rp150.000"))
        // Hutang sudah lunas → tidak ditawarkan lagi.
        assertNull(DraftIntelligence.matchOpenDebt(debts, TxKind.Income, 250_000, "Cici", "Dana Masuk", "Dari Cici Rp250.000"))
        // Nominal tidak sama → tidak cocok.
        assertNull(DraftIntelligence.matchOpenDebt(debts, TxKind.Income, 200_000, "Budi", "Dana Masuk", "Dari Budi Rp200.000"))
    }

    @Test fun focusDigestPrioritizesTransactionsAndTaskHintsInWindow() {
        val start = 1_000_000L
        val end = 2_500_000L
        val entries = listOf(
            FocusDigestInput("old", "WhatsApp", "Budi", null, "Tolong kirim besok", NotifCategory.Chat, postedAt = start - 10),
            FocusDigestInput("msg", "WhatsApp", "Sari", null, "Halo lagi di mana?", NotifCategory.Chat, postedAt = start + 100),
            FocusDigestInput("task", "WhatsApp", "Bos", null, "Tolong kirim revisi sebelum jam 5", NotifCategory.Chat, postedAt = start + 200),
            FocusDigestInput("tx", "wondr by BNI", "QRIS", null, "Pembayaran QRIS sebesar 13.500", NotifCategory.Money, postedAt = start + 300, moneyAmount = 13_500, moneyKind = TxKind.Expense, moneySource = "wondr by BNI", moneyParty = "MyTelkomsel Apps"),
            FocusDigestInput("noise", "Game", "Energy penuh", null, "Ayo main lagi", NotifCategory.Other, postedAt = start + 400),
        )
        val digest = summarizeFocusNotifs(entries, start, end, focusedMinutes = 25, taskTitle = "Kerjakan audit")
        assertEquals(4, digest.totalNotifs) // "old" di luar jendela
        assertEquals(1, digest.transactionCount)
        assertEquals(1, digest.taskHintCount)
        assertEquals(listOf(DigestKind.Transaction, DigestKind.TaskHint, DigestKind.Message), digest.highlights.map { it.kind })
        assertTrue(digest.highlights[0].headline.contains("MyTelkomsel Apps"))
        assertTrue(digest.highlights[1].suggestedTaskTitle.contains("Bos"))
    }
}
