package com.rosyidputrar.taskify.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.domain.tasks.RecurrenceState
import com.rosyidputrar.taskify.core.domain.tasks.Task
import com.rosyidputrar.taskify.core.domain.tasks.buildRule
import com.rosyidputrar.taskify.core.domain.tasks.scheduleChange
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.store.tasks.NewTask
import com.rosyidputrar.taskify.core.store.tasks.TagRef
import com.rosyidputrar.taskify.core.store.tasks.TagRepository
import com.rosyidputrar.taskify.core.store.tasks.TaskList
import com.rosyidputrar.taskify.core.store.tasks.TaskRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TaskDetailState(
    val task: Task? = null,
    val subtasks: List<Task> = emptyList(),
    val tags: List<TagRef> = emptyList(),
    val allTags: List<TagRef> = emptyList(),
    val lists: List<TaskList> = emptyList(),
    val loaded: Boolean = false,
)

/** Detail satu tugas. Setiap perubahan langsung ditulis ke SQLite lokal (tanpa tombol Simpan). */
class TaskDetailViewModel(
    private val taskId: String,
    private val tasks: TaskRepository,
    private val tagRepo: TagRepository,
    private val messenger: Messenger,
) : ViewModel() {

    val state: StateFlow<TaskDetailState> = combine(
        tasks.watchTask(taskId),
        tasks.watchSubtasks(taskId),
        tagRepo.watchEntityTags("task", taskId),
        tagRepo.watchAll(),
        tasks.watchLists(),
    ) { task, subs, tags, all, lists -> TaskDetailState(task, subs, tags, all, lists, loaded = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskDetailState())

    private fun current() = state.value.task

    private fun save(patch: Map<String, Any?>) {
        viewModelScope.launch { tasks.update(taskId, patch) }
    }

    fun rename(title: String) {
        if (title.isNotBlank() && title.trim() != current()?.title) save(mapOf("title" to title))
    }

    fun setNotes(notes: String) {
        if (notes != (current()?.notes ?: "")) save(mapOf("notes" to notes.ifBlank { null }))
    }

    fun setSchedule(date: String?, time: String?) {
        val task = current() ?: return
        save(scheduleChange(task, date, time))
    }

    fun setPriority(priority: Int) = save(mapOf("priority" to priority))

    fun setList(listId: String?) = save(mapOf("list_id" to listId))

    fun setRecurrence(state: RecurrenceState) {
        val task = current() ?: return
        val anchor = task.dueDate ?: Dates.today()
        val patch = linkedMapOf<String, Any?>("rrule" to buildRule(state, anchor))
        // Pengulangan butuh tanggal: tugas tanpa tanggal mulai hari ini.
        if (task.dueDate == null) patch["due_date"] = anchor
        save(patch)
    }

    fun setRemindAt(remindAt: String?) = save(mapOf("remind_at" to remindAt))

    fun toggleDone() {
        val task = current() ?: return
        viewModelScope.launch {
            val outcome = tasks.toggle(task)
            val undo = outcome.undo
            if (outcome.message != null && undo != null) messenger.undoable(outcome.message!!, undo)
        }
    }

    fun cancelTask() {
        val task = current() ?: return
        viewModelScope.launch {
            val outcome = tasks.cancel(task)
            outcome.undo?.let { messenger.undoable(outcome.message ?: "Dibatalkan", it) }
        }
    }

    fun delete(onDone: () -> Unit) {
        val task = current() ?: return
        viewModelScope.launch {
            val outcome = tasks.remove(task)
            outcome.undo?.let { messenger.undoable(outcome.message ?: "Dihapus", it) }
            onDone()
        }
    }

    fun restore() {
        viewModelScope.launch { tasks.restore(taskId) }
    }

    // ------------------------------------------------------------------ subtask

    fun addSubtask(title: String) {
        val task = current() ?: return
        if (title.isBlank()) return
        viewModelScope.launch { tasks.create(NewTask(title = title, parentId = task.id, listId = task.listId)) }
    }

    fun toggleSubtask(sub: Task) {
        viewModelScope.launch { tasks.toggle(sub) }
    }

    fun renameSubtask(sub: Task, title: String) {
        if (title.isBlank() || title == sub.title) return
        viewModelScope.launch { tasks.update(sub.id, mapOf("title" to title)) }
    }

    fun removeSubtask(sub: Task) {
        viewModelScope.launch { tasks.remove(sub) }
    }

    // ------------------------------------------------------------------ tag

    fun addTag(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { tagRepo.addByName("task", taskId, listOf(name)) }
    }

    fun removeTag(tag: TagRef) {
        viewModelScope.launch { tagRepo.remove("task", taskId, tag.id) }
    }
}
