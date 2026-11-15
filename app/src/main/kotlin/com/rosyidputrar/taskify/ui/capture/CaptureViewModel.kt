package com.rosyidputrar.taskify.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.domain.capture.CaptureTarget
import com.rosyidputrar.taskify.core.domain.capture.extractTags
import com.rosyidputrar.taskify.core.domain.capture.parseCapturePrefix
import com.rosyidputrar.taskify.core.domain.tasks.parseQuickAdd
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.store.journal.JournalRepository
import com.rosyidputrar.taskify.core.store.notes.NoteRepository
import com.rosyidputrar.taskify.core.store.tasks.NewTask
import com.rosyidputrar.taskify.core.store.tasks.TagRepository
import com.rosyidputrar.taskify.core.store.tasks.TaskRepository
import kotlinx.coroutines.launch

/**
 * Simpan hasil sheet Tangkap: tugas (parser bahasa natural), jurnal hari ini, atau catatan bebas.
 * Mode Otomatis mengenali awalan "j:" / "c:" seperti webapp; transaksi menyusul bersama Keuangan.
 */
class CaptureViewModel(
    private val tasks: TaskRepository,
    private val tags: TagRepository,
    private val messenger: Messenger,
    private val journal: JournalRepository,
    private val notes: NoteRepository,
) : ViewModel() {

    fun save(kind: CaptureKind, text: String, onSaved: () -> Unit) {
        if (text.isBlank()) return
        when (kind) {
            CaptureKind.Auto -> when (val p = parseCapturePrefix(text)) {
                null -> saveTask(text, onSaved)
                else -> if (p.target == CaptureTarget.Journal) saveJournal(p.body, p.tags, onSaved) else saveNote(p.body, p.tags, onSaved)
            }
            CaptureKind.Task -> saveTask(text, onSaved)
            CaptureKind.Journal -> extractTags(text).let { (body, t) -> saveJournal(body, t, onSaved) }
            CaptureKind.Note -> extractTags(text).let { (body, t) -> saveNote(body, t, onSaved) }
            CaptureKind.Transaction -> Unit
        }
    }

    private fun saveTask(text: String, onSaved: () -> Unit) {
        viewModelScope.launch {
            val today = Dates.today()
            val p = parseQuickAdd(text, today)
            val id = tasks.create(NewTask(p.title, dueDate = p.dueDate, dueTime = p.dueTime, priority = p.priority ?: 0))
            if (p.tags.isNotEmpty()) tags.addByName("task", id, p.tags)
            val whenLabel = p.dueDate?.let { " · " + Dates.relativeDayLabel(it, today).lowercase() + (p.dueTime?.let { t -> " $t" } ?: "") } ?: ""
            messenger.undoable("Tugas ditambahkan$whenLabel") { tasks.get(id)?.let { tasks.remove(it) } }
            onSaved()
        }
    }

    private fun saveJournal(body: String, tagNames: List<String>, onSaved: () -> Unit) {
        if (body.isBlank()) return
        viewModelScope.launch {
            val id = journal.create(Dates.today(), content = body)
            if (tagNames.isNotEmpty()) tags.addByName("journal_entry", id, tagNames)
            messenger.undoable("Masuk ke jurnal hari ini") { journal.remove(id) }
            onSaved()
        }
    }

    private fun saveNote(body: String, tagNames: List<String>, onSaved: () -> Unit) {
        if (body.isBlank()) return
        viewModelScope.launch {
            val id = notes.create(content = body)
            if (tagNames.isNotEmpty()) tags.addByName("note", id, tagNames)
            messenger.undoable("Catatan disimpan") { notes.remove(id) }
            onSaved()
        }
    }
}
