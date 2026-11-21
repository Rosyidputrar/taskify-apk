package com.rosyidputrar.taskify.feature.vault.config

import com.rosyidputrar.taskify.core.designsystem.component.launchSafe
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.domain.sandi.ConfigFormat
import com.rosyidputrar.taskify.core.domain.sandi.ConfigParser
import com.rosyidputrar.taskify.core.domain.vault.CodeProject
import com.rosyidputrar.taskify.core.sandi.DecryptedVersion
import com.rosyidputrar.taskify.core.sandi.DecryptedVersions
import com.rosyidputrar.taskify.core.sandi.SandiService
import com.rosyidputrar.taskify.core.sandi.SecureClipboard
import com.rosyidputrar.taskify.core.store.sandi.ConfigFile
import com.rosyidputrar.taskify.core.store.sandi.ConfigRepository
import com.rosyidputrar.taskify.core.store.vault.VaultRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ================================================================== berkas

data class ConfigFileUiState(
    val file: ConfigFile? = null,
    val loaded: Boolean = false,
    val locked: Boolean = true,
    val versions: DecryptedVersions = DecryptedVersions(),
    val selectedId: String? = null,
    val busy: Boolean = false,
) {
    val selected: DecryptedVersion? get() = versions.versions.firstOrNull { it.row.id == selectedId } ?: versions.versions.firstOrNull()
    /** Versi tepat sebelum yang dipilih (daftar terbaru dulu). */
    val previous: DecryptedVersion? get() {
        val list = versions.versions
        val i = list.indexOfFirst { it.row.id == selected?.row?.id }
        return if (i >= 0) list.getOrNull(i + 1) else null
    }
    val isLatest: Boolean get() = selected != null && selected?.row?.id == versions.versions.firstOrNull()?.row?.id
}

interface ConfigFileActions {
    fun select(versionId: String)
    fun copyValue(key: String, value: String)
    fun copyRaw(text: String)
    fun restore(version: DecryptedVersion)
    fun updateMeta(name: String, environment: String)
    fun remove(onDone: () -> Unit)
}

class ConfigFileViewModel(
    private val fileId: String,
    private val repo: ConfigRepository,
    private val service: SandiService,
    private val clipboard: SecureClipboard,
    private val messenger: Messenger,
) : ViewModel(), ConfigFileActions {
    private val selected = MutableStateFlow<String?>(null)
    private val busy = MutableStateFlow(false)

    val state: StateFlow<ConfigFileUiState> = combine(
        repo.watchFile(fileId), service.session.vault.map { it == null }, service.versions(fileId), selected, busy,
    ) { f, locked, v, s, b -> ConfigFileUiState(f, true, locked, v, s, b) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConfigFileUiState())

    override fun select(versionId: String) { selected.value = versionId; service.session.touch() }

    override fun copyValue(key: String, value: String) {
        if (clipboard.copy(key, value, true)) messenger.show("$key disalin · dihapus dari papan klip dalam 30 dtk")
        service.session.touch()
    }

    override fun copyRaw(text: String) {
        if (clipboard.copy(state.value.file?.name ?: "Konfigurasi", text, true)) messenger.show("Isi berkas disalin · dihapus dari papan klip dalam 30 dtk")
        service.session.touch()
    }

    override fun restore(version: DecryptedVersion) = launchBusy {
        val id = service.saveConfigVersion(fileId, version.payload.text, "Dipulihkan dari v${version.row.number}")
        selected.value = id
        messenger.show("v${version.row.number} dipulihkan sebagai versi baru")
    }

    override fun updateMeta(name: String, environment: String) = launchBusy {
        val f = state.value.file ?: return@launchBusy
        repo.updateMeta(fileId, name.ifBlank { f.name }, environment, f.projectId)
    }

    override fun remove(onDone: () -> Unit) = launchBusy {
        val name = state.value.file?.name ?: "Berkas"
        repo.removeFile(fileId)
        messenger.undoable("\"$name\" dihapus") { repo.restoreFile(fileId) }
        onDone()
    }

    private fun launchBusy(block: suspend () -> Unit) {
        busy.value = true
        launchSafe {
            try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { messenger.show(e.message ?: "Gagal") } finally { busy.value = false }
        }
    }
}

// ================================================================== editor

data class ConfigEditorUiState(
    val isNew: Boolean,
    val loaded: Boolean = false,
    val locked: Boolean = true,
    val name: String = "",
    val format: ConfigFormat = ConfigFormat.Env,
    val formatTouched: Boolean = false,
    val environment: String = "",
    val projectId: String? = null,
    val projects: List<CodeProject> = emptyList(),
    val text: String = "",
    val baseText: String = "",
    val note: String = "",
    val saving: Boolean = false,
    val error: String? = null,
) {
    val unchanged get() = !isNew && text == baseText

    /** Ada ketikan yang akan hilang bila editor ditinggalkan. */
    val dirty get() = loaded && if (isNew) name.isNotBlank() || text.isNotBlank() || note.isNotBlank() else text != baseText || note.isNotBlank()
}

interface ConfigEditorActions {
    fun setName(v: String)
    fun setFormat(v: ConfigFormat)
    fun setEnvironment(v: String)
    fun setProject(id: String?)
    fun setText(v: String)
    fun setNote(v: String)
    fun save(onSaved: (String) -> Unit)
}

class ConfigEditorViewModel(
    private val fileId: String?,
    private val projectId: String?,
    private val repo: ConfigRepository,
    vault: VaultRepository,
    private val service: SandiService,
    private val messenger: Messenger,
) : ViewModel(), ConfigEditorActions {
    private val local = MutableStateFlow(ConfigEditorUiState(isNew = fileId == null, loaded = fileId == null, projectId = projectId))

    val state: StateFlow<ConfigEditorUiState> = combine(local, service.session.vault.map { it == null }, vault.watchProjects("")) { l, locked, p ->
        l.copy(locked = locked, projects = p)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    init {
        if (fileId != null) launchSafe {
            val file = repo.watchFile(fileId).first()
            local.update {
                it.copy(
                    name = file?.name ?: "",
                    format = file?.format ?: ConfigFormat.Env,
                    environment = file?.environment ?: "",
                    projectId = file?.projectId,
                )
            }
            // Tunggu sampai brankas Sandi terbuka lalu muat versi terbaru terdekripsi.
            // Tanpa ini, membuka editor saat brankas terkunci membuat isi kosong permanen.
            service.session.vault.first { it != null }
            val latest = service.versions(fileId).first { it.ready }.versions.firstOrNull()
            val text = latest?.payload?.text ?: ""
            local.update {
                it.copy(loaded = true, text = text, baseText = text)
            }
        }
    }

    override fun setName(v: String) = local.update {
        it.copy(name = v, format = if (it.isNew && !it.formatTouched) ConfigParser.guessFormat(v, it.text) else it.format)
    }
    override fun setFormat(v: ConfigFormat) = local.update { it.copy(format = v, formatTouched = true) }
    override fun setEnvironment(v: String) = local.update { it.copy(environment = v) }
    override fun setProject(id: String?) = local.update { it.copy(projectId = id) }
    override fun setText(v: String) { local.update { it.copy(text = v, error = null) }; service.session.touch() }
    override fun setNote(v: String) = local.update { it.copy(note = v) }

    override fun save(onSaved: (String) -> Unit) {
        val s = local.value
        val parsed = ConfigParser.parse(s.text, s.format)
        val size = ConfigParser.sizeProblem(s.text)
        if (size != null) { local.update { it.copy(error = size.message) }; return }
        if (parsed.blocking) { local.update { it.copy(error = parsed.problems.first().message) }; return }
        if (s.isNew && s.name.isBlank()) { local.update { it.copy(error = "Beri nama berkasnya, mis. .env.production") }; return }
        local.update { it.copy(saving = true, error = null) }
        launchSafe {
            try {
                val id = if (fileId == null) {
                    service.createConfigFile(s.name.trim(), s.format, s.environment, s.projectId, s.text, s.note)
                } else {
                    service.saveConfigVersion(fileId, s.text, s.note); fileId
                }
                messenger.show(if (fileId == null) "Berkas konfigurasi dibuat" else "Versi baru disimpan")
                onSaved(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(error = e.message ?: "Gagal menyimpan") }
            } finally {
                local.update { it.copy(saving = false) }
            }
        }
    }
}
