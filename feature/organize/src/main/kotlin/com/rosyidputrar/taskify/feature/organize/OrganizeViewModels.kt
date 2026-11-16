package com.rosyidputrar.taskify.feature.organize

import com.rosyidputrar.taskify.core.designsystem.component.launchSafe
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.domain.files.EntityType
import com.rosyidputrar.taskify.core.domain.search.EntityRow
import com.rosyidputrar.taskify.core.domain.search.label
import com.rosyidputrar.taskify.core.store.cross.SecretTrashRow
import com.rosyidputrar.taskify.core.store.cross.TrashRepository
import com.rosyidputrar.taskify.core.store.tasks.TagRef
import com.rosyidputrar.taskify.core.store.tasks.TagRepository
import com.rosyidputrar.taskify.feature.links.EntityOpener
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

// ---------------------------------------------------------------- daftar tag

data class TagsUiState(val tags: List<TagRef> = emptyList(), val loaded: Boolean = false)

class TagsViewModel(repo: TagRepository) : ViewModel() {
    val state: StateFlow<TagsUiState> = repo.watchAll().map { TagsUiState(it, true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TagsUiState())
}

// ---------------------------------------------------------------- satu tag

data class TagUiState(val tag: TagRef? = null, val items: List<EntityRow> = emptyList(), val loaded: Boolean = false) {
    /** Item dikelompokkan per jenis, urutan jenis tetap. */
    val groups: List<Pair<EntityType, List<EntityRow>>> get() = EntityType.entries.mapNotNull { t -> items.filter { it.type == t }.takeIf { it.isNotEmpty() }?.let { t to it } }
}

interface TagActions {
    fun open(row: EntityRow)
    fun rename(name: String, onDone: (Boolean) -> Unit)
    fun setColor(color: String)
    fun delete(onDone: () -> Unit)
}

class TagViewModel(private val tagId: String, private val repo: TagRepository, private val opener: EntityOpener, private val messenger: Messenger) : ViewModel(), TagActions {
    val state: StateFlow<TagUiState> = combine(repo.watchTag(tagId), repo.watchTagged(tagId)) { t, items -> TagUiState(t, items, true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TagUiState())

    override fun open(row: EntityRow) = opener.open(row.type, row.id)

    override fun rename(name: String, onDone: (Boolean) -> Unit) {
        launchSafe {
            val ok = repo.rename(tagId, name)
            if (!ok) messenger.show("Nama kosong atau sudah dipakai tag lain.")
            onDone(ok)
        }
    }

    override fun setColor(color: String) { launchSafe { repo.setColor(tagId, color) } }

    override fun delete(onDone: () -> Unit) {
        launchSafe {
            val name = state.value.tag?.name
            repo.deleteTag(tagId)
            messenger.show("Tag #${name ?: ""} dihapus")
            onDone()
        }
    }
}

// ---------------------------------------------------------------- Sampah

data class TrashUiState(
    val rows: List<EntityRow> = emptyList(),
    val loaded: Boolean = false,
    val now: Long = System.currentTimeMillis(),
    /** Item Sandi & Konfigurasi yang dihapus. */
    val secrets: List<SecretTrashRow> = emptyList(),
) {
    val isEmpty: Boolean get() = rows.isEmpty() && secrets.isEmpty()
    val total: Int get() = rows.size + secrets.size
}

interface TrashActions {
    fun restore(row: EntityRow)
    fun destroy(row: EntityRow)
    fun emptyAll()
    fun restoreSecret(row: SecretTrashRow) = Unit
    fun destroySecret(row: SecretTrashRow) = Unit
}

class TrashViewModel(private val repo: TrashRepository, private val messenger: Messenger) : ViewModel(), TrashActions {
    val state: StateFlow<TrashUiState> = combine(repo.watchTrash(), repo.watchSecretTrash()) { rows, secrets -> TrashUiState(rows, true, secrets = secrets) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrashUiState())

    override fun restoreSecret(row: SecretTrashRow) {
        launchSafe { repo.restoreSecret(row.type, row.id); messenger.show("${row.type.label} dipulihkan") }
    }

    override fun destroySecret(row: SecretTrashRow) {
        launchSafe { repo.destroySecret(row.type, listOf(row.id)); messenger.show("\"${row.title}\" dihapus permanen") }
    }

    override fun restore(row: EntityRow) {
        launchSafe { repo.restore(row.type, row.id); messenger.show("${row.type.label} dipulihkan") }
    }

    override fun destroy(row: EntityRow) {
        launchSafe { repo.destroy(row.type, listOf(row.id)); messenger.show("\"${row.title}\" dihapus permanen") }
    }

    override fun emptyAll() {
        launchSafe { repo.emptyTrash(); messenger.show("Sampah dikosongkan") }
    }
}

val organizeFeatureModule = module {
    viewModel { TagsViewModel(get()) }
    viewModel { p -> TagViewModel(p.get(), get(), get(), get()) }
    viewModel { TrashViewModel(get(), get()) }
}
