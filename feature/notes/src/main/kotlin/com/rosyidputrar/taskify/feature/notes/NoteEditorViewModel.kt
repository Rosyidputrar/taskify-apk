package com.rosyidputrar.taskify.feature.notes

import com.rosyidputrar.taskify.core.designsystem.component.launchSafe
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.domain.editor.Autosaver
import com.rosyidputrar.taskify.core.domain.editor.SaveState
import com.rosyidputrar.taskify.core.store.notes.NoteRepository
import com.rosyidputrar.taskify.core.store.tasks.TagRef
import com.rosyidputrar.taskify.core.store.tasks.TagRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Catatan yang ada ([id]) atau draf baru; [date] = langsung ditempel ke tanggal itu (dari Kalender). */
data class NoteEditorArgs(val id: String? = null, val date: String? = null)

data class NoteDraftUi(
    val loaded: Boolean = false,
    val missing: Boolean = false,
    val id: String? = null,
    val date: String? = null,
    val title: String = "",
    val body: TextFieldValue = TextFieldValue(),
    val pinned: Boolean = false,
    val trashed: Boolean = false,
)

const val NOTE_ENTITY = "note"

/** Editor catatan — perilaku simpan otomatis sama dengan Jurnal (lihat [Autosaver]). */
@OptIn(ExperimentalCoroutinesApi::class)
class NoteEditorViewModel(
    args: NoteEditorArgs,
    private val repo: NoteRepository,
    private val tags: TagRepository,
    private val messenger: Messenger,
    private val appScope: CoroutineScope,
) : ViewModel() {
    private val _ui = MutableStateFlow(NoteDraftUi())
    val ui: StateFlow<NoteDraftUi> = _ui
    private val savedId = MutableStateFlow(args.id)
    private val saver = Autosaver(appScope, if (args.id != null) SaveState.Saved else SaveState.Idle) { save(it) }
    val saveState: StateFlow<SaveState> = saver.state

    data class TagState(val selected: List<TagRef> = emptyList(), val all: List<TagRef> = emptyList())

    val tagState: StateFlow<TagState> = savedId.flatMapLatest { id ->
        if (id == null) flowOf(TagState()) else combine(tags.watchEntityTags(NOTE_ENTITY, id), tags.watchAll()) { s, a -> TagState(s, a) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TagState())

    init {
        if (args.id != null) {
            launchSafe {
                val note = repo.get(args.id)
                _ui.value = note?.let {
                    NoteDraftUi(true, false, it.id, it.noteDate, it.title.orEmpty(), TextFieldValue(it.content, TextRange(it.content.length)), it.pinned, it.deletedAt != null)
                } ?: NoteDraftUi(loaded = true, missing = true)
            }
        } else {
            _ui.value = NoteDraftUi(loaded = true, date = args.date)
        }
    }

    private suspend fun save(patch: Map<String, Any?>): Boolean {
        val id = savedId.value
        if (id == null) {
            val v = _ui.value
            if (v.title.isBlank() && v.body.text.isBlank()) return false
            val newId = repo.create(v.date, v.title, v.body.text, v.pinned)
            savedId.value = newId
            _ui.update { it.copy(id = newId) }
        } else {
            repo.update(id, patch)
        }
        return true
    }

    fun setTitle(title: String) {
        if (title == _ui.value.title) return
        _ui.update { it.copy(title = title) }
        saver.queue(mapOf("title" to title))
    }

    fun setBody(value: TextFieldValue) {
        val changed = value.text != _ui.value.body.text
        _ui.update { it.copy(body = value) }
        if (changed) saver.queue(mapOf("content" to value.text))
    }

    /** Tempel ke tanggal (muncul di Kalender) atau lepas ([date] null = catatan bebas). */
    fun setDate(date: String?) {
        _ui.update { it.copy(date = date) }
        saver.queue(mapOf("note_date" to date), immediate = true)
    }

    fun togglePin() {
        val pinned = !_ui.value.pinned
        _ui.update { it.copy(pinned = pinned) }
        saver.queue(mapOf("is_pinned" to pinned), immediate = true)
    }

    fun retry() { launchSafe { saver.flush() } }
    fun flush() { saver.flushLater() }

    fun remove(onDone: () -> Unit) {
        val id = savedId.value ?: return onDone()
        appScope.launch {
            saver.flush()
            repo.remove(id)
            messenger.undoable("Catatan dipindahkan ke Sampah") { repo.restore(id) }
        }
        onDone()
    }

    fun restore() {
        val id = savedId.value ?: return
        launchSafe {
            repo.restore(id)
            _ui.update { it.copy(trashed = false) }
        }
    }

    fun addTag(name: String) { savedId.value?.let { id -> launchSafe { tags.addByName(NOTE_ENTITY, id, listOf(name)) } } }
    fun removeTag(tag: TagRef) { savedId.value?.let { id -> launchSafe { tags.remove(NOTE_ENTITY, id, tag.id) } } }

    override fun onCleared() {
        saver.flushLater()
    }
}
