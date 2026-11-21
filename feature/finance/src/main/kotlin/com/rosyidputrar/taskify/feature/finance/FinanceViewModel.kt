package com.rosyidputrar.taskify.feature.finance

import com.rosyidputrar.taskify.core.designsystem.component.launchSafe
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.domain.calendar.shiftMonth
import com.rosyidputrar.taskify.core.domain.finance.BudgetStatus
import com.rosyidputrar.taskify.core.domain.finance.Category
import com.rosyidputrar.taskify.core.domain.finance.CategoryKind
import com.rosyidputrar.taskify.core.domain.finance.CategorySlice
import com.rosyidputrar.taskify.core.domain.finance.DayTotals
import com.rosyidputrar.taskify.core.domain.finance.Debt
import com.rosyidputrar.taskify.core.domain.finance.PeriodTotals
import com.rosyidputrar.taskify.core.domain.finance.Recurring
import com.rosyidputrar.taskify.core.domain.finance.RecurringSchedule
import com.rosyidputrar.taskify.core.domain.finance.SubscriptionBurn
import com.rosyidputrar.taskify.core.domain.finance.Transaction
import com.rosyidputrar.taskify.core.domain.finance.TxDay
import com.rosyidputrar.taskify.core.domain.finance.TxKind
import com.rosyidputrar.taskify.core.domain.finance.Wallet
import com.rosyidputrar.taskify.core.domain.finance.buildCancelReminderPlan
import com.rosyidputrar.taskify.core.domain.finance.firstOccurrenceFrom
import com.rosyidputrar.taskify.core.domain.finance.groupByDay
import com.rosyidputrar.taskify.core.domain.finance.summarizeSubscriptions
import com.rosyidputrar.taskify.core.domain.finance.transactionsCsv
import com.rosyidputrar.taskify.core.domain.finance.transactionsCsvName
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.domain.time.todayFlow
import com.rosyidputrar.taskify.core.store.finance.CategoryInput
import com.rosyidputrar.taskify.core.store.finance.DebtInput
import com.rosyidputrar.taskify.core.store.finance.FinanceRepository
import com.rosyidputrar.taskify.core.store.finance.RecurringInput
import com.rosyidputrar.taskify.core.store.finance.TxFilter
import com.rosyidputrar.taskify.core.store.finance.WalletInput
import com.rosyidputrar.taskify.core.store.tasks.NewTask
import com.rosyidputrar.taskify.core.store.tasks.TaskRepository
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

/** Tab Keuangan; [monthly] = memakai pemilih bulan. */
enum class FinanceTab(val label: String, val monthly: Boolean) {
    Overview("Ringkasan", true),
    Transactions("Transaksi", true),
    Budget("Anggaran", true),
    Recurring("Berulang", false),
    Debt("Hutang", false),
    Manage("Kelola", false),
}

data class TxListFilter(val kind: TxKind? = null, val categoryId: String? = null, val search: String = "") {
    val active: Boolean get() = kind != null || categoryId != null || search.isNotBlank()
}

data class MonthData(
    val totals: PeriodTotals = PeriodTotals(),
    val daily: Map<String, DayTotals> = emptyMap(),
    val budgets: List<BudgetStatus> = emptyList(),
    val recent: List<Transaction> = emptyList(),
)

data class FinanceUiState(
    val today: String = "",
    val month: String = "",
    val tab: FinanceTab = FinanceTab.Overview,
    val walletId: String? = null,
    val wallets: List<Wallet> = emptyList(),
    val categories: List<Category> = emptyList(),
    val monthData: MonthData = MonthData(),
    val sliceKind: CategoryKind = CategoryKind.Expense,
    val slices: List<CategorySlice> = emptyList(),
    val txFilter: TxListFilter = TxListFilter(),
    val transactions: List<Transaction> = emptyList(),
    val recurring: List<Recurring> = emptyList(),
    val debts: List<Debt> = emptyList(),
    val loaded: Boolean = false,
) {
    val activeWallets: List<Wallet> get() = wallets.filterNot { it.archived }
    val totalBalance: Long get() = activeWallets.sumOf { it.balance }
    val days: List<TxDay> get() = groupByDay(transactions)
    val due: List<Recurring> get() = recurring.filter { it.isDue(today) }
    val subscriptionBurn: SubscriptionBurn get() = summarizeSubscriptions(recurring, today)
}

/** Semua aksi layar Keuangan — dipisah agar konten bisa dirender tanpa database. */
interface FinanceActions {
    fun setTab(tab: FinanceTab)
    fun shiftMonth(amount: Long)
    fun setWallet(id: String?)
    fun setSliceKind(kind: CategoryKind)
    fun setTxFilter(filter: TxListFilter)
    fun removeTx(tx: Transaction)
    fun postOccurrence(r: Recurring)
    fun skipOccurrence(r: Recurring)
    fun setRecurringActive(r: Recurring, active: Boolean)
    fun removeRecurring(r: Recurring)
    fun saveRecurring(input: RecurringInput, onDone: () -> Unit)
    fun saveRecurringWithReminder(input: RecurringInput, remindDaysBefore: Long?, onDone: () -> Unit) = saveRecurring(input, onDone)
    fun scheduleCancelReminder(r: Recurring, daysBefore: Long) {}
    fun cancelSubscription(r: Recurring) {}
    fun saveBudget(id: String?, categoryId: String?, amount: Long, alertPct: Int, onDone: () -> Unit)
    fun removeBudget(b: BudgetStatus)
    fun createDebt(input: DebtInput, onDone: () -> Unit)
    fun settleDebt(d: Debt, walletId: String?)
    fun unsettleDebt(d: Debt)
    fun removeDebt(d: Debt)
    fun saveWallet(id: String?, input: WalletInput, onDone: () -> Unit)
    fun setWalletArchived(w: Wallet, archived: Boolean)
    fun removeWallet(w: Wallet, onDone: () -> Unit)
    fun saveCategory(id: String?, input: CategoryInput, onDone: () -> Unit)
    fun removeCategory(c: Category)
    /** CSV transaksi bulan ini sesuai filter: (nama file, isi) atau null bila kosong. */
    suspend fun exportCsv(): Pair<String, String>?
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class FinanceViewModel(
    private val repo: FinanceRepository,
    private val messenger: Messenger,
    private val tasks: TaskRepository? = null,
) : ViewModel(), FinanceActions {
    private val today = todayFlow().stateIn(viewModelScope, SharingStarted.Eagerly, Dates.today())
    private val month = MutableStateFlow(Dates.monthStart(today.value))
    private val tab = MutableStateFlow(FinanceTab.Overview)
    private val walletId = MutableStateFlow<String?>(null)
    private val sliceKind = MutableStateFlow(CategoryKind.Expense)
    private val txFilter = MutableStateFlow(TxListFilter())

    private val monthData = month.flatMapLatest { m ->
        val last = Dates.monthEnd(m)
        combine(
            repo.watchPeriodTotals(m, last),
            repo.watchDailyTotals(m, last),
            repo.watchBudgets(m, last),
            repo.watchTransactions(TxFilter(m, last, limit = 6)),
        ) { t, d, b, r -> MonthData(t, d, b, r) }
    }

    private val slices = combine(month, sliceKind) { m, k -> m to k }.flatMapLatest { (m, k) -> repo.watchCategoryBreakdown(m, Dates.monthEnd(m), k) }

    private val txs = combine(month, walletId, txFilter.debounce { if (it.search.isEmpty()) 0 else 200 }.distinctUntilChanged()) { m, w, f -> Triple(m, w, f) }
        .flatMapLatest { (m, w, f) -> repo.watchTransactions(TxFilter(m, Dates.monthEnd(m), f.kind, w, f.categoryId, f.search)) }

    private data class Lists(val wallets: List<Wallet>, val categories: List<Category>, val recurring: List<Recurring>, val debts: List<Debt>)
    private val lists = combine(repo.watchWallets(), repo.watchCategories(), repo.watchRecurring(), repo.watchDebts()) { w, c, r, d -> Lists(w, c, r, d) }

    private data class Ui(val today: String, val month: String, val tab: FinanceTab, val walletId: String?, val sliceKind: CategoryKind, val filter: TxListFilter)
    private val ui = combine(combine(today, month, tab) { a, b, c -> Triple(a, b, c) }, walletId, sliceKind, txFilter) { (t, m, tb), w, k, f -> Ui(t, m, tb, w, k, f) }

    val state: StateFlow<FinanceUiState> = combine(ui, lists, monthData, slices, txs) { u, l, md, s, tx ->
        FinanceUiState(
            today = u.today, month = u.month, tab = u.tab, walletId = u.walletId,
            wallets = l.wallets, categories = l.categories, monthData = md, sliceKind = u.sliceKind, slices = s,
            txFilter = u.filter, transactions = tx, recurring = l.recurring, debts = l.debts, loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FinanceUiState(today = today.value, month = month.value))

    init {
        // Transaksi dari notifikasi yang dicatat sebelum asal aplikasinya disimpan → pakai ikon aplikasinya juga.
        launchSafe { runCatching { repo.backfillNotificationSources() } }
    }

    override fun setTab(tab: FinanceTab) { this.tab.value = tab }
    override fun shiftMonth(amount: Long) { month.value = shiftMonth(month.value, amount) }
    override fun setWallet(id: String?) {
        walletId.value = id
        if (id != null) tab.value = FinanceTab.Transactions
    }
    override fun setSliceKind(kind: CategoryKind) { sliceKind.value = kind }
    override fun setTxFilter(filter: TxListFilter) { txFilter.value = filter }

    /** Jalankan aksi tulis; galat validasi (FinanceException) tampil sebagai pesan. */
    private fun act(block: suspend () -> Unit) {
        launchSafe {
            try {
                block()
            } catch (e: IllegalArgumentException) {
                messenger.show(e.message ?: "Gagal menyimpan")
            }
        }
    }

    override fun removeTx(tx: Transaction) = act {
        repo.removeTx(tx.id)
        messenger.undoable("Transaksi dihapus") { repo.restoreTx(tx.id) }
    }

    override fun postOccurrence(r: Recurring) = act {
        if (repo.postOccurrence(r)) messenger.show("${r.title} dicatat · ${com.rosyidputrar.taskify.core.common.Rupiah.format(r.amount)}")
    }

    override fun skipOccurrence(r: Recurring) = act { repo.skipOccurrence(r) }
    override fun setRecurringActive(r: Recurring, active: Boolean) = act { repo.setRecurringActive(r, active, today.value) }
    override fun removeRecurring(r: Recurring) = act {
        repo.removeRecurring(r.id)
        messenger.show("${r.title} dihapus")
    }

    override fun saveRecurring(input: RecurringInput, onDone: () -> Unit) = act {
        repo.saveRecurring(input, today.value)
        onDone()
    }

    override fun saveRecurringWithReminder(input: RecurringInput, remindDaysBefore: Long?, onDone: () -> Unit) = act {
        repo.saveRecurring(input, today.value)
        val taskRepo = tasks
        if (taskRepo != null && remindDaysBefore != null && input.kind == TxKind.Expense) {
            val next = firstOccurrenceFrom(RecurringSchedule(input.rrule, input.startOn, input.endOn), today.value)
            val plan = next?.let { buildCancelReminderPlan(input.title, input.amount, it, remindDaysBefore, today.value, input.autoPost) }
            if (plan != null) {
                val id = taskRepo.create(NewTask(title = plan.title, dueDate = plan.dueDate, remindAt = plan.remindAt, notes = plan.notes))
                messenger.undoable("Pengingat dipasang (${Dates.relativeDayLabel(plan.remindDate, today.value)} 09:00)") {
                    taskRepo.get(id)?.let { taskRepo.remove(it) }
                }
            }
        }
        onDone()
    }

    override fun scheduleCancelReminder(r: Recurring, daysBefore: Long) = act {
        val taskRepo = tasks ?: return@act
        val next = r.nextRunOn ?: return@act
        val plan = buildCancelReminderPlan(r.title, r.amount, next, daysBefore, today.value, r.autoPost) ?: return@act
        val id = taskRepo.create(NewTask(title = plan.title, dueDate = plan.dueDate, remindAt = plan.remindAt, notes = plan.notes))
        messenger.undoable("Pengingat ${r.title} dipasang (${Dates.relativeDayLabel(plan.remindDate, today.value)} 09:00)") {
            taskRepo.get(id)?.let { taskRepo.remove(it) }
        }
    }

    override fun cancelSubscription(r: Recurring) = act {
        repo.setRecurringActive(r, false, today.value)
        messenger.undoable("${r.title} ditandai dibatalkan") {
            repo.setRecurringActive(r, true, today.value)
        }
    }

    override fun saveBudget(id: String?, categoryId: String?, amount: Long, alertPct: Int, onDone: () -> Unit) = act {
        repo.saveBudget(id, categoryId, amount, alertPct, month.value)
        onDone()
    }

    override fun removeBudget(b: BudgetStatus) = act {
        repo.removeBudget(b.id)
        messenger.undoable("Anggaran dihapus") {
            if (!repo.restoreBudget(b.id)) messenger.show("Sudah ada anggaran untuk kategori ini, jadi tidak dipulihkan")
        }
    }

    override fun createDebt(input: DebtInput, onDone: () -> Unit) = act {
        repo.createDebt(input, today.value)
        onDone()
    }

    override fun settleDebt(d: Debt, walletId: String?) = act {
        repo.settleDebt(d, walletId, today.value)
        messenger.show("${d.counterparty}: lunas")
    }

    override fun unsettleDebt(d: Debt) = act { repo.unsettleDebt(d) }
    override fun removeDebt(d: Debt) = act {
        repo.removeDebt(d)
        messenger.show("Catatan ${d.counterparty} dihapus")
    }

    override fun saveWallet(id: String?, input: WalletInput, onDone: () -> Unit) = act {
        if (input.name.isBlank()) throw IllegalArgumentException("Isi nama dompet")
        if (id == null) repo.createWallet(input) else repo.updateWallet(id, input)
        onDone()
    }

    override fun setWalletArchived(w: Wallet, archived: Boolean) = act {
        repo.setWalletArchived(w.id, archived)
        if (archived && walletId.value == w.id) walletId.value = null
    }

    override fun removeWallet(w: Wallet, onDone: () -> Unit) = act {
        repo.removeWallet(w.id)
        if (walletId.value == w.id) walletId.value = null
        onDone()
    }

    override fun saveCategory(id: String?, input: CategoryInput, onDone: () -> Unit) = act {
        if (input.name.isBlank()) throw IllegalArgumentException("Isi nama kategori")
        if (id == null) repo.createCategory(input) else repo.updateCategory(id, input.name, input.color, input.icon)
        onDone()
    }

    override fun removeCategory(c: Category) = act {
        repo.removeCategory(c.id)
        messenger.show("Kategori ${c.name} dihapus. Transaksi lamanya tetap tersimpan.")
    }

    /** Ekspor memakai query khusus tanpa LIMIT (bukan daftar layar yang dibatasi 2000). */
    override suspend fun exportCsv(): Pair<String, String>? {
        val m = month.value
        val f = txFilter.value
        val rows = repo.exportTransactions(TxFilter(m, Dates.monthEnd(m), f.kind, walletId.value, f.categoryId, f.search))
        if (rows.isEmpty()) {
            messenger.show("Tidak ada transaksi untuk diekspor")
            return null
        }
        return transactionsCsvName(m) to transactionsCsv(rows)
    }
}
