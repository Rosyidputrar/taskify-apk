package com.rosyidputrar.taskify.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.common.Rupiah
import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.domain.capture.Capture
import com.rosyidputrar.taskify.core.domain.capture.CaptureType
import com.rosyidputrar.taskify.core.domain.capture.extractTags
import com.rosyidputrar.taskify.core.domain.capture.matchCategory
import com.rosyidputrar.taskify.core.domain.capture.parseCapture
import com.rosyidputrar.taskify.core.domain.finance.TxInput
import com.rosyidputrar.taskify.core.domain.finance.TxKind
import com.rosyidputrar.taskify.core.domain.tasks.parseQuickAdd
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.store.finance.FinanceRepository
import com.rosyidputrar.taskify.core.store.journal.JournalRepository
import com.rosyidputrar.taskify.core.store.notes.NoteRepository
import com.rosyidputrar.taskify.core.store.tasks.NewTask
import com.rosyidputrar.taskify.core.store.tasks.TagRepository
import com.rosyidputrar.taskify.core.store.tasks.TaskRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Simpan hasil sheet Tangkap (port `useCapture` webapp): satu kalimat → tugas, transaksi,
 * jurnal hari ini, atau catatan bebas. Mode Otomatis memakai [parseCapture]; jenis yang dipilih
 * manual memaksa tujuan simpan.
 */
class CaptureViewModel(
    private val tasks: TaskRepository,
    private val tags: TagRepository,
    private val messenger: Messenger,
    private val journal: JournalRepository,
    private val notes: NoteRepository,
    private val finance: FinanceRepository,
) : ViewModel() {

    fun save(kind: CaptureKind, text: String, onSaved: () -> Unit) {
        if (text.isBlank()) return
        val today = Dates.today()
        val c = parseCapture(text, today)
        viewModelScope.launch {
            val ok = when (kind) {
                CaptureKind.Auto -> when (c.type) {
                    CaptureType.Task -> saveTask(text, today)
                    CaptureType.Expense, CaptureType.Income -> saveTransaction(c)
                    CaptureType.Journal -> saveJournal(c.title, c.tags, today)
                    CaptureType.Note -> saveNote(c.rawText, c.tags)
                }
                CaptureKind.Task -> saveTask(text, today)
                CaptureKind.Transaction -> if (c.amount != null) saveTransaction(c) else {
                    messenger.show("Sertakan nominal, mis. \"kopi 25rb\"")
                    false
                }
                CaptureKind.Journal -> extractTags(text).let { (body, t) -> saveJournal(body, t, today) }
                CaptureKind.Note -> extractTags(text).let { (body, t) -> saveNote(body, t) }
            }
            if (ok) onSaved()
        }
    }

    private suspend fun saveTask(text: String, today: String): Boolean {
        val p = parseQuickAdd(text, today)
        val id = tasks.create(NewTask(p.title, dueDate = p.dueDate, dueTime = p.dueTime, priority = p.priority ?: 0))
        if (p.tags.isNotEmpty()) tags.addByName("task", id, p.tags)
        val whenLabel = p.dueDate?.let { " · " + Dates.relativeDayLabel(it, today).lowercase() + (p.dueTime?.let { t -> " $t" } ?: "") } ?: ""
        messenger.undoable("Tugas ditambahkan$whenLabel") { tasks.get(id)?.let { tasks.remove(it) } }
        return true
    }

    /** Transaksi ke dompet aktif pertama; kategori ditebak dari kata kunci (sama dengan webapp). */
    private suspend fun saveTransaction(c: Capture): Boolean {
        val amount = c.amount ?: return false
        val wallet = finance.watchWallets(includeArchived = false).first().firstOrNull()
        if (wallet == null) {
            messenger.show("Buat dompet dulu di Keuangan › Kelola")
            return false
        }
        val kind = if (c.type == CaptureType.Income) TxKind.Income else TxKind.Expense
        val category = matchCategory(finance.watchCategories().first(), { it.kind.value }, { it.name }, kind.value, c.categoryHint, c.title)
        val id = finance.createTx(TxInput(kind, amount, wallet.id, categoryId = category?.id, occurredOn = c.date ?: Dates.today(), title = c.title.ifBlank { null }))
        val label = (if (kind == TxKind.Income) "Pemasukan " else "Pengeluaran ") + Rupiah.format(amount) + (category?.let { " · ${it.name}" } ?: "")
        messenger.undoable(label) { finance.removeTx(id) }
        return true
    }

    private suspend fun saveJournal(body: String, tagNames: List<String>, today: String): Boolean {
        if (body.isBlank()) return false
        val id = journal.create(today, content = body)
        if (tagNames.isNotEmpty()) tags.addByName("journal_entry", id, tagNames)
        messenger.undoable("Masuk ke jurnal hari ini") { journal.remove(id) }
        return true
    }

    private suspend fun saveNote(body: String, tagNames: List<String>): Boolean {
        if (body.isBlank()) return false
        val id = notes.create(content = body)
        if (tagNames.isNotEmpty()) tags.addByName("note", id, tagNames)
        messenger.undoable("Catatan disimpan") { notes.remove(id) }
        return true
    }
}
