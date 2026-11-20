package com.rosyidputrar.taskify.feature.notes

import com.rosyidputrar.taskify.core.designsystem.component.launchSafe
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.domain.notes.Note
import com.rosyidputrar.taskify.core.domain.notes.NoteScope
import com.rosyidputrar.taskify.core.domain.notes.groupLabel
import com.rosyidputrar.taskify.core.domain.notes.groupNotes
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.domain.time.todayFlow
import com.rosyidputrar.taskify.core.store.notes.NoteCounts
import com.rosyidputrar.taskify.core.store.notes.NoteRepository
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

data class NotesFilter(val scope: NoteScope = NoteScope.All, val search: String = "", val trash: Boolean = false)

data class NoteGroup(val key: String, val label: String, val notes: List<Note>)

data class NotesUiState(
    val today: String = "",
    val filter: NotesFilter = NotesFilter(),
    val pinned: List<Note> = emptyList(),
    val groups: List<NoteGroup> = emptyList(),
    val counts: NoteCounts = NoteCounts(),
    val loaded: Boolean = false,
) {
    val isEmpty: Boolean get() = pinned.isEmpty() && groups.isEmpty()

    fun subtitle(): String = if (counts.total == 0) "Belum ada catatan" else buildString {
        append("${counts.total} catatan")
        if (counts.dated > 0) append(" · ${counts.dated} bertanggal")
        if (counts.pinned > 0) append(" · ${counts.pinned} disematkan")
    }
}

interface NotesActions {
    fun setScope(scope: NoteScope)
    fun setSearch(text: String)
    fun toggleTrash()
    fun togglePin(note: Note)
    fun restore(note: Note)
    /** Pindahkan ke Sampah (geser ke kiri) — bisa diurungkan dari snackbar. */
    fun remove(note: Note) {}
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class NotesViewModel(
    private val repo: NoteRepository,
    private val messenger: Messenger,
) : ViewModel(), NotesActions {
    private val filter = MutableStateFlow(NotesFilter())
    private val today = todayFlow().stateIn(viewModelScope, SharingStarted.Eagerly, Dates.today())

    private val notes = filter.debounce { if (it.search.isEmpty()) 0 else 200 }.distinctUntilChanged()
        .flatMapLatest { f -> repo.watchNotes(f.scope, f.search, f.trash) }

    val state: StateFlow<NotesUiState> = combine(notes, repo.watchCounts(), filter, today) { list, counts, f, t ->
        val g = groupNotes(list, f.trash)
        NotesUiState(
            today = t,
            filter = f,
            pinned = g.pinned,
            groups = g.groups.map { (key, items) -> NoteGroup(key, groupLabel(key), items) },
            counts = counts,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState(today = today.value))

    override fun setScope(scope: NoteScope) { filter.value = filter.value.copy(scope = scope) }
    override fun setSearch(text: String) { filter.value = filter.value.copy(search = text) }
    override fun toggleTrash() { filter.value = filter.value.copy(trash = !filter.value.trash) }

    override fun togglePin(note: Note) {
        launchSafe { repo.update(note.id, mapOf("is_pinned" to !note.pinned)) }
    }

    override fun restore(note: Note) {
        launchSafe {
            repo.restore(note.id)
            messenger.show("Catatan dipulihkan")
        }
    }

    override fun remove(note: Note) {
        launchSafe {
            repo.remove(note.id)
            messenger.undoable("Catatan dipindahkan ke Sampah") { repo.restore(note.id) }
        }
    }
}
