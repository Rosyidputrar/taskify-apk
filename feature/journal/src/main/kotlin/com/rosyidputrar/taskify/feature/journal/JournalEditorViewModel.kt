package com.rosyidputrar.taskify.feature.journal

import com.rosyidputrar.taskify.core.designsystem.component.launchSafe
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.domain.editor.Autosaver
import com.rosyidputrar.taskify.core.domain.editor.SaveState
import com.rosyidputrar.taskify.core.domain.journal.ReflectionKind
import com.rosyidputrar.taskify.core.domain.journal.promptFor
import com.rosyidputrar.taskify.core.domain.journal.reflectionTemplate
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.store.journal.JournalRepository
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

/** Argumen editor: entri yang ada ([id]) atau draf baru di [date], opsional dengan templat refleksi. */
data class JournalEditorArgs(
    val id: String? = null,
    val date: String? = null,
    val template: ReflectionKind? = null,
    val from: String? = null,
    val to: String? = null,
)

data class JournalDraftUi(
    val loaded: Boolean = false,
    val missing: Boolean = false,
    val id: String? = null,
    val date: String = "",
    val title: String = "",
    val body: TextFieldValue = TextFieldValue(),
    val mood: Int? = null,
    val pinned: Boolean = false,
    val trashed: Boolean = false,
    val prompt: String = "",
)

const val JOURNAL_ENTITY = "journal_entry"

/**
 * Editor jurnal dengan simpan otomatis (port perilaku `JournalEditor` webapp):
 * draf baru baru dibuat di DB saat ada isi pertama; teks disimpan 0,7 dtk setelah berhenti
 * mengetik; mood/tanggal/pin langsung. State lokal adalah sumber kebenaran selama mengedit,
 * jadi data sinkron tidak menimpa ketikan. Simpan berjalan di scope aplikasi agar flush saat
 * layar ditutup tetap selesai.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JournalEditorViewModel(
    args: JournalEditorArgs,
    private val repo: JournalRepository,
    private val tags: TagRepository,
    private val messenger: Messenger,
    private val appScope: CoroutineScope,
) : ViewModel() {
    private val _ui = MutableStateFlow(JournalDraftUi())
    val ui: StateFlow<JournalDraftUi> = _ui

    private val saver = Autosaver(appScope, if (args.id != null) SaveState.Saved else SaveState.Idle) { patch -> save(patch) }
    val saveState: StateFlow<SaveState> = saver.state

    private val savedId = MutableStateFlow(args.id)

    data class TagState(val selected: List<TagRef> = emptyList(), val all: List<TagRef> = emptyList())

    val tagState: StateFlow<TagState> = savedId.flatMapLatest { id ->
        if (id == null) flowOf(TagState()) else combine(tags.watchEntityTags(JOURNAL_ENTITY, id), tags.watchAll()) { s, a -> TagState(s, a) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TagState())

    init {
        val today = Dates.today()
        if (args.id != null) {
            launchSafe {
                val entry = repo.get(args.id)
                _ui.value = if (entry == null) {
                    JournalDraftUi(loaded = true, missing = true)
                } else {
                    JournalDraftUi(
                        loaded = true,
                        id = entry.id,
                        date = entry.entryDate,
                        title = entry.title.orEmpty(),
                        body = TextFieldValue(entry.content, TextRange(entry.content.length)),
                        mood = entry.mood,
                        pinned = entry.pinned,
                        trashed = entry.deletedAt != null,
                        prompt = promptFor(entry.entryDate),
                    )
                }
            }
        } else {
            val date = args.date ?: today
            val draft = if (args.template != null && args.from != null && args.to != null) reflectionTemplate(args.template, args.from, args.to) else null
            _ui.value = JournalDraftUi(
                loaded = true,
                date = date,
                title = draft?.title.orEmpty(),
                body = TextFieldValue(draft?.content.orEmpty(), TextRange(draft?.content?.length ?: 0)),
                prompt = promptFor(date),
            )
            // Templat refleksi langsung disimpan sebagai draf supaya tidak hilang bila editor ditutup.
            if (draft != null) saver.queue(mapOf("title" to draft.title))
        }
    }

    private suspend fun save(patch: Map<String, Any?>): Boolean {
        val id = savedId.value
        if (id == null) {
            val v = _ui.value
            if (v.title.isBlank() && v.body.text.isBlank() && v.mood == null) return false
            val newId = repo.create(v.date, v.title, v.body.text, v.mood, v.pinned)
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

    fun setMood(mood: Int?) {
        _ui.update { it.copy(mood = mood) }
        saver.queue(mapOf("mood" to mood), immediate = true)
    }

    fun setDate(date: String) {
        _ui.update { it.copy(date = date, prompt = promptFor(date)) }
        saver.queue(mapOf("entry_date" to date), immediate = true)
    }

    fun togglePin() {
        val pinned = !_ui.value.pinned
        _ui.update { it.copy(pinned = pinned) }
        saver.queue(mapOf("is_pinned" to pinned), immediate = true)
    }

    fun retry() { launchSafe { saver.flush() } }

    /** Dipanggil saat aplikasi ke latar / layar ditutup. */
    fun flush() { saver.flushLater() }

    fun remove(onDone: () -> Unit) {
        val id = savedId.value ?: return onDone()
        appScope.launch {
            saver.flush()
            repo.remove(id)
            messenger.undoable("Entri jurnal dipindahkan ke Sampah") { repo.restore(id) }
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

    fun addTag(name: String) { savedId.value?.let { id -> launchSafe { tags.addByName(JOURNAL_ENTITY, id, listOf(name)) } } }
    fun removeTag(tag: TagRef) { savedId.value?.let { id -> launchSafe { tags.remove(JOURNAL_ENTITY, id, tag.id) } } }

    override fun onCleared() {
        saver.flushLater()
    }
}
