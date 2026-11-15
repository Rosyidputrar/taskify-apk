package com.rosyidputrar.taskify.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.domain.tasks.parseQuickAdd
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.store.tasks.NewTask
import com.rosyidputrar.taskify.core.store.tasks.TagRepository
import com.rosyidputrar.taskify.core.store.tasks.TaskRepository
import kotlinx.coroutines.launch

/**
 * Simpan hasil sheet Tangkap. Saat ini menyimpan TUGAS (dengan parser bahasa natural);
 * jurnal, catatan, dan transaksi menyusul bersama modulnya.
 */
class CaptureViewModel(
    private val tasks: TaskRepository,
    private val tags: TagRepository,
    private val messenger: Messenger,
) : ViewModel() {

    fun saveTask(text: String, onSaved: () -> Unit) {
        if (text.isBlank()) return
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
}
