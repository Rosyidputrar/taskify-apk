package com.rosyidputrar.taskify.core.store

import com.powersync.PowerSyncDatabase
import com.powersync.inMemory
import com.rosyidputrar.taskify.core.domain.finance.BudgetLevel
import com.rosyidputrar.taskify.core.domain.finance.CategoryKind
import com.rosyidputrar.taskify.core.domain.finance.DebtDirection
import com.rosyidputrar.taskify.core.domain.finance.FinanceException
import com.rosyidputrar.taskify.core.domain.finance.TxInput
import com.rosyidputrar.taskify.core.domain.finance.TxKind
import com.rosyidputrar.taskify.core.domain.finance.WalletType
import com.rosyidputrar.taskify.core.domain.finance.debtSettleTxId
import com.rosyidputrar.taskify.core.domain.finance.recurringTxId
import com.rosyidputrar.taskify.core.domain.tasks.RecurrenceFreq
import com.rosyidputrar.taskify.core.domain.tasks.RecurrenceState
import com.rosyidputrar.taskify.core.domain.tasks.buildRule
import com.rosyidputrar.taskify.core.store.finance.CategoryInput
import com.rosyidputrar.taskify.core.store.finance.DebtInput
import com.rosyidputrar.taskify.core.store.finance.FinanceRepository
import com.rosyidputrar.taskify.core.store.finance.RecurringInput
import com.rosyidputrar.taskify.core.store.finance.TxFilter
import com.rosyidputrar.taskify.core.store.finance.WalletInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FinanceRepositoryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = PowerSyncDatabase.inMemory(TaskifySchema.schema, scope)
    private val finance = FinanceRepository(db) { "u" }
    private val today = "2026-10-07"

    @After fun tearDown() = runBlocking {
        db.close()
        scope.cancel()
    }

    private suspend fun setup(): Triple<String, String, String> {
        val cash = finance.createWallet(WalletInput("Tunai", WalletType.Cash, 100_000, "emerald", "banknote"))
        val bank = finance.createWallet(WalletInput("Bank", WalletType.Bank, 1_000_000, "sky", "landmark"))
        val food = finance.createCategory(CategoryInput("Makan", CategoryKind.Expense, "orange", "utensils"))
        return Triple(cash, bank, food)
    }

    @Test fun balancesTotalsAndBreakdownFollowWebappRules() = runBlocking {
        val (cash, bank, food) = setup()
        finance.createTx(TxInput(TxKind.Expense, 25_000, cash, categoryId = food, occurredOn = today, occurredTime = "12:15", title = "Makan siang"))
        finance.createTx(TxInput(TxKind.Income, 5_000_000, bank, occurredOn = today, title = "Gaji"))
        finance.createTx(TxInput(TxKind.Transfer, 200_000, bank, toWalletId = cash, categoryId = food, occurredOn = "2026-10-06"))
        finance.createDebt(DebtInput(DebtDirection.Lent, "Andi", 50_000, null, null, cash), today)

        val wallets = finance.watchWallets().first().associateBy { it.name }
        assertEquals(100_000 - 25_000 + 200_000 - 50_000L, wallets["Tunai"]!!.balance)
        assertEquals(1_000_000 + 5_000_000 - 200_000L, wallets["Bank"]!!.balance)
        assertEquals(3, wallets["Tunai"]!!.txCount)

        val totals = finance.watchPeriodTotals("2026-10-01", "2026-10-31").first()
        assertEquals(5_000_000, totals.income)
        assertEquals(25_000, totals.expense) // transfer & hutang tidak dihitung
        assertEquals(2, totals.count)

        val slices = finance.watchCategoryBreakdown("2026-10-01", "2026-10-31", CategoryKind.Expense).first()
        assertEquals(listOf("Makan" to 25_000L), slices.map { it.name to it.total })
        assertEquals(25_000, finance.watchDailyTotals("2026-10-01", "2026-10-31").first()[today]!!.expense)

        val rows = finance.watchTransactions(TxFilter("2026-10-01", "2026-10-31")).first()
        assertEquals(4, rows.size)
        val transfer = rows.first { it.kind == TxKind.Transfer }
        assertNull(transfer.categoryId) // transfer tanpa kategori
        assertEquals("Tunai", transfer.toWalletName)
        assertEquals(listOf("Makan siang"), finance.watchTransactions(TxFilter("2026-10-01", "2026-10-31", search = "makan")).first().map { it.displayTitle })
        assertEquals(2, finance.watchTransactions(TxFilter("2026-10-01", "2026-10-31", walletId = bank)).first().size)
        assertEquals(1, finance.watchTransactions(TxFilter("2026-10-01", "2026-10-31", kind = TxKind.Income)).first().size)
    }

    @Test fun editRemoveRestoreAndWalletRules() = runBlocking {
        val (cash, bank, food) = setup()
        val id = finance.createTx(TxInput(TxKind.Expense, 10_000, cash, categoryId = food, occurredOn = today))
        finance.updateTx(id, TxInput(TxKind.Expense, 12_000, bank, categoryId = food, occurredOn = today, title = "Kopi"))
        assertEquals(12_000, finance.getTransaction(id)!!.amount)
        assertEquals("Bank", finance.getTransaction(id)!!.walletName)
        assertFailsWith<FinanceException> { finance.removeWallet(bank) }
        finance.removeTx(id)
        assertTrue(finance.watchTransactions(TxFilter("2026-10-01", "2026-10-31")).first().isEmpty())
        finance.restoreTx(id)
        assertEquals(1, finance.watchTransactions(TxFilter("2026-10-01", "2026-10-31")).first().size)
        finance.removeWallet(cash) // tanpa transaksi → boleh dihapus
        finance.setWalletArchived(bank, true)
        assertEquals(0, finance.watchWallets(includeArchived = false).first().size)
        assertEquals(1, finance.watchWallets().first().size)
    }

    @Test fun budgetsAndRestoreClash() = runBlocking {
        val (cash, _, food) = setup()
        finance.saveBudget(null, food, 100_000, 80, "2026-10-01")
        finance.saveBudget(null, null, 1_000_000, 80, "2026-10-01")
        finance.createTx(TxInput(TxKind.Expense, 85_000, cash, categoryId = food, occurredOn = today))
        val budgets = finance.watchBudgets("2026-10-01", "2026-10-31").first()
        assertEquals(listOf("Total pengeluaran", "Makan"), budgets.map { it.name })
        assertEquals(BudgetLevel.Warn, budgets[1].level)
        assertEquals(85_000, budgets[0].spent)
        assertTrue(finance.watchBudgets("2026-09-01", "2026-09-30").first().isEmpty()) // belum berlaku
        assertFailsWith<FinanceException> { finance.saveBudget(null, food, 0, 80, "2026-10-01") }

        val foodBudget = budgets[1].id
        finance.removeBudget(foodBudget)
        finance.saveBudget(null, food, 50_000, 90, "2026-10-01")
        assertFalse(finance.restoreBudget(foodBudget)) // sudah ada anggaran baru untuk kategori itu
    }

    @Test fun recurringPostsWithDeterministicIdsAndNoDuplicates() = runBlocking {
        val (cash, _, food) = setup()
        val rule = buildRule(RecurrenceState(RecurrenceFreq.Monthly), "2026-08-31")!!
        finance.saveRecurring(RecurringInput(kind = TxKind.Expense, amount = 186_000, walletId = cash, toWalletId = null, categoryId = food,
            title = "Listrik", note = null, rrule = rule, startOn = "2026-08-31", endOn = null, autoPost = true), today)
        val r = finance.watchRecurring().first().single()
        assertEquals("2026-08-31", r.nextRunOn)

        assertEquals(2, finance.processDueRecurring(today)) // 31 Agu + 30 Sep
        assertEquals("2026-10-31", finance.watchRecurring().first().single().nextRunOn)
        assertEquals(0, finance.processDueRecurring(today))
        assertEquals("Listrik", finance.getTransaction(recurringTxId(r.id, "2026-09-30"))!!.title)

        // Perangkat lain yang belum tahu next_run_on baru memproses ulang → ID sama, tidak dobel.
        db.execute("UPDATE recurring_transactions SET next_run_on = '2026-08-31' WHERE id = ?", listOf(r.id))
        assertEquals(0, finance.processDueRecurring(today))
        assertEquals(2, finance.watchTransactions(TxFilter("2026-08-01", "2026-10-31")).first().size)

        // Manual: Catat / Lewati.
        finance.saveRecurring(RecurringInput(kind = TxKind.Income, amount = 5_000_000, walletId = cash, toWalletId = null, categoryId = null,
            title = "Gaji", note = null, rrule = buildRule(RecurrenceState(RecurrenceFreq.Monthly), "2026-10-01")!!, startOn = "2026-10-01", endOn = null, autoPost = false), today)
        val gaji = finance.watchRecurring().first().first { it.title == "Gaji" }
        assertTrue(gaji.isDue(today))
        assertEquals(0, finance.processDueRecurring(today)) // manual tidak diproses otomatis
        finance.postOccurrence(gaji)
        val afterPost = finance.watchRecurring().first().first { it.title == "Gaji" }
        assertEquals("2026-11-01", afterPost.nextRunOn)
        finance.skipOccurrence(afterPost)
        assertEquals("2026-12-01", finance.watchRecurring().first().first { it.title == "Gaji" }.nextRunOn)

        finance.setRecurringActive(afterPost, false, today)
        assertFalse(finance.watchRecurring().first().first { it.title == "Gaji" }.active)
    }

    @Test fun debtsOpenSettleUnsettleRemove() = runBlocking {
        val (cash, bank, _) = setup()
        val id = finance.createDebt(DebtInput(DebtDirection.Borrowed, "Budi", 300_000, "2026-10-01", "beli laptop", bank), today)
        var debt = finance.watchDebts().first().single()
        assertTrue(debt.isOverdue(today))
        assertEquals(1_300_000, finance.watchWallets().first().first { it.id == bank }.balance)

        finance.settleDebt(debt, cash, today)
        debt = finance.watchDebts().first().single()
        assertTrue(debt.settled)
        assertEquals("Pelunasan ke Budi", finance.getTransaction(debtSettleTxId(id))!!.title)
        assertEquals(100_000 - 300_000L, finance.watchWallets().first().first { it.id == cash }.balance)

        finance.unsettleDebt(debt)
        assertEquals(100_000, finance.watchWallets().first().first { it.id == cash }.balance)
        finance.settleDebt(finance.watchDebts().first().single(), cash, today) // hidupkan lagi baris lama
        assertEquals(-200_000, finance.watchWallets().first().first { it.id == cash }.balance)

        finance.removeDebt(finance.watchDebts().first().single())
        assertTrue(finance.watchDebts().first().isEmpty())
        assertEquals(100_000, finance.watchWallets().first().first { it.id == cash }.balance)
        assertEquals(1_000_000, finance.watchWallets().first().first { it.id == bank }.balance)
        assertEquals(0, finance.watchPeriodTotals("2026-10-01", "2026-10-31").first().count)
    }
}
