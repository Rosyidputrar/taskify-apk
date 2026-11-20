package com.rosyidputrar.taskify.feature.ai.tools

import com.rosyidputrar.taskify.core.domain.finance.BudgetStatus
import com.rosyidputrar.taskify.core.domain.finance.Category
import com.rosyidputrar.taskify.core.domain.finance.CategoryKind
import com.rosyidputrar.taskify.core.domain.finance.CategorySlice
import com.rosyidputrar.taskify.core.domain.finance.PeriodTotals
import com.rosyidputrar.taskify.core.domain.finance.Transaction
import com.rosyidputrar.taskify.core.domain.finance.TxInput
import com.rosyidputrar.taskify.core.domain.finance.TxKind
import com.rosyidputrar.taskify.core.domain.finance.Wallet
import com.rosyidputrar.taskify.core.domain.journal.JournalEntry
import com.rosyidputrar.taskify.core.domain.notes.Note
import com.rosyidputrar.taskify.core.domain.search.EntityRow
import com.rosyidputrar.taskify.core.domain.tasks.Task
import com.rosyidputrar.taskify.core.store.finance.FinanceRepository
import com.rosyidputrar.taskify.core.store.finance.TxFilter
import com.rosyidputrar.taskify.core.store.journal.JournalRepository
import com.rosyidputrar.taskify.core.store.notes.NoteRepository
import com.rosyidputrar.taskify.core.store.search.SearchRepository
import com.rosyidputrar.taskify.core.store.tasks.NewTask
import com.rosyidputrar.taskify.core.store.tasks.SmartView
import com.rosyidputrar.taskify.core.store.tasks.TaskList
import com.rosyidputrar.taskify.core.store.tasks.TaskRepository
import com.rosyidputrar.taskify.core.store.tasks.TaskView
import kotlinx.coroutines.flow.first

/**
 * Data Taskify yang boleh disentuh asisten — sengaja sempit: tugas, catatan, jurnal, keuangan.
 * Sandi, Brankas kode, dan Berkas TIDAK ada di sini.
 */
interface AiData {
    suspend fun search(query: String, limit: Int): List<EntityRow>
    suspend fun tasks(view: SmartView, today: String): List<Task>
    suspend fun task(id: String): Task?
    suspend fun subtasks(id: String): List<Task>
    suspend fun taskLists(): List<TaskList>
    suspend fun notes(query: String): List<Note>
    suspend fun note(id: String): Note?
    suspend fun journal(query: String): List<JournalEntry>
    suspend fun journalEntry(id: String): JournalEntry?
    suspend fun totals(from: String, to: String): PeriodTotals
    suspend fun breakdown(from: String, to: String, kind: CategoryKind): List<CategorySlice>
    suspend fun wallets(): List<Wallet>
    suspend fun categories(kind: CategoryKind): List<Category>
    suspend fun budgets(month: String, monthLast: String): List<BudgetStatus>
    suspend fun transactions(from: String, to: String, kind: TxKind?, query: String, limit: Int): List<Transaction>
    suspend fun transaction(id: String): Transaction?

    suspend fun createTask(input: NewTask): String
    suspend fun createTx(input: TxInput): String
    suspend fun createNote(date: String?, title: String?, content: String): String
}

/** [AiData] di atas repositori lokal (PowerSync) — tulisan ikut tersinkron seperti biasa. */
class StoreAiData(
    private val tasks: TaskRepository,
    private val notes: NoteRepository,
    private val journal: JournalRepository,
    private val finance: FinanceRepository,
    private val searchRepo: SearchRepository,
) : AiData {
    override suspend fun search(query: String, limit: Int) = searchRepo.search(query, limit)
    override suspend fun tasks(view: SmartView, today: String) = tasks.watchView(TaskView.Smart(view), today).first()
    override suspend fun task(id: String) = tasks.get(id)?.takeIf { it.deletedAt == null }
    override suspend fun subtasks(id: String) = tasks.watchSubtasks(id).first()
    override suspend fun taskLists() = tasks.watchLists().first()
    override suspend fun notes(query: String) = notes.watchNotes(search = query).first()
    override suspend fun note(id: String) = notes.get(id)?.takeIf { it.deletedAt == null }
    override suspend fun journal(query: String) = journal.watchEntries(search = query).first()
    override suspend fun journalEntry(id: String) = journal.get(id)?.takeIf { it.deletedAt == null }
    override suspend fun totals(from: String, to: String) = finance.watchPeriodTotals(from, to).first()
    override suspend fun breakdown(from: String, to: String, kind: CategoryKind) = finance.watchCategoryBreakdown(from, to, kind).first()
    override suspend fun wallets() = finance.watchWallets(includeArchived = false).first()
    override suspend fun categories(kind: CategoryKind) = finance.watchCategories(kind).first()
    override suspend fun budgets(month: String, monthLast: String) = finance.watchBudgets(month, monthLast).first()
    override suspend fun transactions(from: String, to: String, kind: TxKind?, query: String, limit: Int) =
        finance.watchTransactions(TxFilter(from, to, kind = kind, search = query, limit = limit)).first()
    override suspend fun transaction(id: String) = finance.getTransaction(id)

    override suspend fun createTask(input: NewTask) = tasks.create(input)
    override suspend fun createTx(input: TxInput) = finance.createTx(input)
    override suspend fun createNote(date: String?, title: String?, content: String) = notes.create(date = date, title = title, content = content)
}
