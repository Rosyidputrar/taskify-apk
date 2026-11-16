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
import com.rosyidputrar.taskify.SampleData.TODAY
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.finance.BudgetStatus
import com.rosyidputrar.taskify.core.domain.finance.Category
import com.rosyidputrar.taskify.core.domain.finance.CategoryKind
import com.rosyidputrar.taskify.core.domain.finance.CategorySlice
import com.rosyidputrar.taskify.core.domain.finance.DayTotals
import com.rosyidputrar.taskify.core.domain.finance.Debt
import com.rosyidputrar.taskify.core.domain.finance.DebtDirection
import com.rosyidputrar.taskify.core.domain.finance.PeriodTotals
import com.rosyidputrar.taskify.core.domain.finance.Recurring
import com.rosyidputrar.taskify.core.domain.finance.Transaction
import com.rosyidputrar.taskify.core.domain.finance.TxKind
import com.rosyidputrar.taskify.core.domain.finance.Wallet
import com.rosyidputrar.taskify.core.domain.finance.WalletType
import com.rosyidputrar.taskify.core.store.finance.CategoryInput
import com.rosyidputrar.taskify.core.store.finance.DebtInput
import com.rosyidputrar.taskify.core.store.finance.RecurringInput
import com.rosyidputrar.taskify.core.store.finance.WalletInput
import com.rosyidputrar.taskify.feature.finance.FinanceActions
import com.rosyidputrar.taskify.feature.finance.FinanceContent
import com.rosyidputrar.taskify.feature.finance.FinanceTab
import com.rosyidputrar.taskify.feature.finance.FinanceUiState
import com.rosyidputrar.taskify.feature.finance.MonthData
import com.rosyidputrar.taskify.feature.finance.TxDraft
import com.rosyidputrar.taskify.feature.finance.TxEditorActions
import com.rosyidputrar.taskify.feature.finance.TxEditorContent
import com.rosyidputrar.taskify.feature.finance.TxEditorState
import com.rosyidputrar.taskify.feature.finance.TxListFilter
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Data contoh Keuangan (tanggal tetap agar gambar stabil). */
object SampleFinance {
    val wallets = listOf(
        Wallet("w1", "Tunai", WalletType.Cash, 0, "emerald", "banknote", 1.0, false, 412_500, 18),
        Wallet("w2", "BCA", WalletType.Bank, 0, "sky", "landmark", 2.0, false, 7_850_000, 9),
        Wallet("w3", "GoPay", WalletType.EWallet, 0, "teal", "smartphone", 3.0, false, 96_000, 22),
        Wallet("w4", "Tabungan lama", WalletType.Bank, 0, "slate", "piggy-bank", 4.0, true, 1_200_000, 3),
    )
    val categories = listOf(
        Category("c1", "Makan & Minum", CategoryKind.Expense, "orange", "utensils", 1.0, 30),
        Category("c2", "Transportasi", CategoryKind.Expense, "sky", "car", 2.0, 12),
        Category("c3", "Tagihan", CategoryKind.Expense, "amber", "zap", 3.0, 4),
        Category("c4", "Belanja", CategoryKind.Expense, "pink", "shopping-bag", 4.0, 6),
        Category("c5", "Hiburan", CategoryKind.Expense, "violet", "film", 5.0, 3),
        Category("c6", "Gaji", CategoryKind.Income, "emerald", "briefcase", 6.0, 1),
        Category("c7", "Freelance", CategoryKind.Income, "teal", "laptop", 7.0, 2),
    )

    private fun tx(id: String, kind: TxKind, amount: Long, date: String, title: String?, cat: Category?, wallet: String = "Tunai", time: String? = null, to: String? = null, rec: String? = null) =
        Transaction(id, kind, amount, "w", if (to != null) "w2" else null, cat?.id, date, time, title, null, recurringId = rec,
            categoryName = cat?.name, categoryColor = cat?.color, categoryIcon = cat?.icon, walletName = wallet, toWalletName = to)

    val transactions = listOf(
        tx("t1", TxKind.Expense, 35_000, TODAY, "Makan siang", categories[0], time = "12:15:00"),
        tx("t2", TxKind.Expense, 18_000, TODAY, "Grab ke kantor", categories[1], "GoPay", time = "08:05:00"),
        tx("t3", TxKind.Income, 5_000_000, "2026-10-06", "Gaji Oktober", categories[5], "BCA", rec = "r"),
        tx("t4", TxKind.Transfer, 500_000, "2026-10-06", null, null, "BCA", to = "Tunai"),
        tx("t5", TxKind.Expense, 186_000, "2026-10-05", "Listrik", categories[2], "BCA", rec = "r2"),
        tx("t6", TxKind.Expense, 249_000, "2026-10-04", null, categories[3], "BCA"),
        tx("t7", TxKind.Expense, 54_000, "2026-10-03", "Nonton", categories[4]),
    )

    private val slices = listOf(
        CategorySlice("c3", "Tagihan", "amber", "zap", 486_000, 3),
        CategorySlice("c1", "Makan & Minum", "orange", "utensils", 412_000, 14),
        CategorySlice("c4", "Belanja", "pink", "shopping-bag", 249_000, 1),
        CategorySlice("c2", "Transportasi", "sky", "car", 126_000, 7),
        CategorySlice("c5", "Hiburan", "violet", "film", 54_000, 1),
    )

    private val daily = (1..7).associate { d ->
        val day = "2026-10-%02d".format(d)
        day to DayTotals(day, if (d == 6) 5_000_000 else 0, listOf(320_000L, 95_000, 120_000, 249_000, 186_000, 40_000, 53_000)[d - 1])
    }

    val budgets = listOf(
        BudgetStatus("b0", null, 4_000_000, 80, "2026-10-01", null, "Total pengeluaran", null, null, 1_327_000),
        BudgetStatus("b1", "c1", 1_200_000, 80, "2026-10-01", null, "Makan & Minum", "orange", "utensils", 412_000),
        BudgetStatus("b2", "c4", 300_000, 80, "2026-10-01", null, "Belanja", "pink", "shopping-bag", 249_000),
        BudgetStatus("b3", "c3", 450_000, 90, "2026-10-01", null, "Tagihan", "amber", "zap", 486_000),
    )

    val recurring = listOf(
        Recurring("r1", TxKind.Expense, 54_990, "w2", null, "c5", "Netflix", null, "FREQ=MONTHLY;INTERVAL=1;BYMONTHDAY=5", "2026-01-05", null, "2026-10-05", false, true, "Hiburan", "violet", "film", "BCA"),
        Recurring("r2", TxKind.Income, 5_000_000, "w2", null, "c6", "Gaji", null, "FREQ=MONTHLY;INTERVAL=1;BYMONTHDAY=25", "2026-01-25", null, "2026-10-25", true, true, "Gaji", "emerald", "briefcase", "BCA"),
        Recurring("r3", TxKind.Expense, 186_000, "w2", null, "c3", "Listrik", null, "FREQ=MONTHLY;INTERVAL=1;BYMONTHDAY=20", "2026-01-20", null, "2026-10-20", true, true, "Tagihan", "amber", "zap", "BCA"),
        Recurring("r4", TxKind.Expense, 99_000, "w2", null, "c5", "Spotify keluarga", null, "FREQ=YEARLY;INTERVAL=1", "2026-03-01", null, "2027-03-01", false, false, "Hiburan", "violet", "film", "BCA"),
    )

    val debts = listOf(
        Debt("d1", DebtDirection.Lent, "Andi", 150_000, "2026-10-01", "patungan tiket", null, ""),
        Debt("d2", DebtDirection.Borrowed, "Kak Sari", 500_000, "2026-10-30", null, null, ""),
        Debt("d3", DebtDirection.Lent, "Budi", 75_000, null, null, "2026-10-02T00:00:00.000Z", ""),
    )

    fun state(tab: FinanceTab = FinanceTab.Overview) = FinanceUiState(
        today = TODAY, month = "2026-10-01", tab = tab, wallets = wallets, categories = categories,
        monthData = MonthData(PeriodTotals(5_000_000, 1_327_000, 9), daily, budgets, transactions.take(5)),
        slices = slices, txFilter = TxListFilter(), transactions = transactions, recurring = recurring, debts = debts, loaded = true,
    )

    object Actions : FinanceActions, TxEditorActions {
        override fun setTab(tab: FinanceTab) = Unit
        override fun shiftMonth(amount: Long) = Unit
        override fun setWallet(id: String?) = Unit
        override fun setSliceKind(kind: CategoryKind) = Unit
        override fun setTxFilter(filter: TxListFilter) = Unit
        override fun removeTx(tx: Transaction) = Unit
        override fun postOccurrence(r: Recurring) = Unit
        override fun skipOccurrence(r: Recurring) = Unit
        override fun setRecurringActive(r: Recurring, active: Boolean) = Unit
        override fun removeRecurring(r: Recurring) = Unit
        override fun saveRecurring(input: RecurringInput, onDone: () -> Unit) = Unit
        override fun saveBudget(id: String?, categoryId: String?, amount: Long, alertPct: Int, onDone: () -> Unit) = Unit
        override fun removeBudget(b: BudgetStatus) = Unit
        override fun createDebt(input: DebtInput, onDone: () -> Unit) = Unit
        override fun settleDebt(d: Debt, walletId: String?) = Unit
        override fun unsettleDebt(d: Debt) = Unit
        override fun removeDebt(d: Debt) = Unit
        override fun saveWallet(id: String?, input: WalletInput, onDone: () -> Unit) = Unit
        override fun setWalletArchived(w: Wallet, archived: Boolean) = Unit
        override fun removeWallet(w: Wallet, onDone: () -> Unit) = Unit
        override fun saveCategory(id: String?, input: CategoryInput, onDone: () -> Unit) = Unit
        override fun removeCategory(c: Category) = Unit
        override fun exportCsv(): Pair<String, String>? = null
        override fun back() = Unit
        override fun update(block: (TxDraft) -> TxDraft) = Unit
        override fun kind(kind: TxKind) = Unit
        override fun add(delta: Long) = Unit
        override fun save() = Unit
        override fun remove() = Unit
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class FinanceScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val padding = PaddingValues(top = 24.dp, bottom = 24.dp)

    private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent { TaskifyTheme(darkTheme = dark) { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    private fun tab(name: String, tab: FinanceTab, dark: Boolean = false) = shot(name, dark) {
        FinanceContent(SampleFinance.state(tab), SampleFinance.Actions, padding, {}, {})
    }

    @Config(qualifiers = "w393dp-h2100dp-xxhdpi")
    @Test fun overviewTall() = tab("finance_overview_tall_light", FinanceTab.Overview)

    @Test fun overviewDark() = tab("finance_overview_dark", FinanceTab.Overview, dark = true)

    @Config(qualifiers = "w393dp-h1500dp-xxhdpi")
    @Test fun transactions() = tab("finance_transactions_light", FinanceTab.Transactions)

    @Test fun budget() = tab("finance_budget_light", FinanceTab.Budget)

    @Config(qualifiers = "w393dp-h1300dp-xxhdpi")
    @Test fun recurring() = tab("finance_recurring_light", FinanceTab.Recurring)

    @Test fun debt() = tab("finance_debt_dark", FinanceTab.Debt, dark = true)

    @Config(qualifiers = "w393dp-h1700dp-xxhdpi")
    @Test fun manage() = tab("finance_manage_light", FinanceTab.Manage)

    private val editorState = TxEditorState(
        TxDraft(loaded = true, kind = TxKind.Expense, amountText = "35rb", walletId = "w1", categoryId = "c1", date = TODAY, time = "12:15", title = "Makan siang"),
        SampleFinance.wallets.filterNot { it.archived },
        SampleFinance.categories,
    )

    @Test fun editorLight() = shot("finance_tx_editor_light", false) { TxEditorContent(editorState, TODAY, SampleFinance.Actions) }

    @Test fun editorTransferDark() = shot("finance_tx_editor_transfer_dark", true) {
        TxEditorContent(editorState.copy(draft = editorState.draft.copy(kind = TxKind.Transfer, amountText = "1,5jt", walletId = "w2", toWalletId = "w1", title = "")), TODAY, SampleFinance.Actions)
    }
}

