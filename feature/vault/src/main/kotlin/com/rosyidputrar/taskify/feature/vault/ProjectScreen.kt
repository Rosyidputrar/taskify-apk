package com.rosyidputrar.taskify.feature.vault

import com.rosyidputrar.taskify.core.designsystem.component.launchSafe
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.designsystem.component.EmptyState
import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.designsystem.component.ModuleHeader
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.designsystem.theme.namedColor
import com.rosyidputrar.taskify.core.domain.files.Attachment
import com.rosyidputrar.taskify.core.domain.files.AttachmentStatus
import com.rosyidputrar.taskify.core.domain.files.EntityType
import com.rosyidputrar.taskify.core.domain.files.WarningLevel
import com.rosyidputrar.taskify.core.domain.files.formatBytes
import com.rosyidputrar.taskify.core.domain.files.nextVersionLabel
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.domain.vault.CodeProject
import com.rosyidputrar.taskify.core.files.AttachmentManager
import com.rosyidputrar.taskify.core.files.PreparedFile
import com.rosyidputrar.taskify.core.files.UploadProgress
import com.rosyidputrar.taskify.core.store.files.DuplicateInfo
import com.rosyidputrar.taskify.core.store.vault.VaultRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

data class ProjectUiState(val loaded: Boolean = false, val project: CodeProject? = null, val versions: List<Attachment> = emptyList())

/** Versi ZIP yang sedang disiapkan untuk diunggah. */
data class PendingVersion(val file: PreparedFile, val duplicate: DuplicateInfo?, val label: String)

class ProjectViewModel(
    private val id: String,
    private val vault: VaultRepository,
    private val files: AttachmentManager,
    private val messenger: Messenger,
) : ViewModel(), ProjectActions {
    val state: StateFlow<ProjectUiState> = combine(vault.watchProject(id), files.watch(EntityType.CodeProject, id)) { p, v -> ProjectUiState(true, p, v) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectUiState())

    private val _pending = MutableStateFlow<PendingVersion?>(null)
    val pending: StateFlow<PendingVersion?> = _pending
    private val _preparing = MutableStateFlow(false)
    val preparing: StateFlow<Boolean> = _preparing

    private fun act(block: suspend () -> Unit) {
        launchSafe {
            try { block() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { messenger.show(e.message ?: "Gagal") }
        }
    }

    override fun progress(att: Attachment): Flow<UploadProgress?> = files.progress(att.id)

    /** Pilih ZIP → disalin & dianalisis di perangkat (isi, peringatan, duplikat) → label otomatis. */
    override fun pick(uri: Uri) = act {
        _preparing.value = true
        try {
            val prepared = files.prepare(uri, compress = false)
            _pending.value = PendingVersion(prepared, files.findDuplicate(prepared.sha256), nextVersionLabel(state.value.versions.map { it.versionLabel }))
        } finally {
            _preparing.value = false
        }
    }

    override fun discardPending() {
        _pending.value?.let { files.discard(it.file) }
        _pending.value = null
    }

    override fun uploadPending(label: String, note: String) = act {
        val p = _pending.value ?: return@act
        _pending.value = null
        files.commit(p.file, EntityType.CodeProject, id, label, note)
        messenger.show("${label.ifBlank { p.file.name }} masuk antrean unggah")
    }

    override fun open(att: Attachment) = act { files.open(att) }
    override fun saveTo(att: Attachment, uri: Uri) = act { files.saveTo(att, uri); messenger.show("${att.fileName} disimpan") }
    override fun retry(att: Attachment) = act { if (!files.retry(att)) messenger.show("File aslinya tidak ada di perangkat ini.") }
    override fun remove(att: Attachment) = act { files.remove(att); messenger.show("Versi ${att.versionLabel ?: att.fileName} dihapus") }
    override fun toggleFavorite() = act { state.value.project?.let { vault.setProjectFavorite(it.id, !it.favorite) } }
    override fun edit(name: String, description: String?, repoUrl: String?) = act { vault.saveProject(id, name, description, repoUrl) }
    override fun removeProject(onDone: () -> Unit) = act {
        vault.removeProject(id)
        messenger.undoable("Project dipindahkan ke Sampah") { vault.restoreProject(id) }
        onDone()
    }

    override fun onCleared() {
        _pending.value?.let { files.discard(it.file) }
    }
}

interface ProjectActions {
    fun progress(att: Attachment): Flow<UploadProgress?>
    fun pick(uri: Uri)
    fun discardPending()
    fun uploadPending(label: String, note: String)
    fun open(att: Attachment)
    fun saveTo(att: Attachment, uri: Uri)
    fun retry(att: Attachment)
    fun remove(att: Attachment)
    fun toggleFavorite()
    fun edit(name: String, description: String?, repoUrl: String?)
    fun removeProject(onDone: () -> Unit)
}

@Composable
fun ProjectScreen(id: String, contentPadding: PaddingValues, onBack: () -> Unit, onViewZip: (String) -> Unit, backButton: @Composable (() -> Unit) -> Unit) {
    val vm: ProjectViewModel = koinViewModel(key = "project-$id") { parametersOf(id) }
    val state by vm.state.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val preparing by vm.preparing.collectAsStateWithLifecycle()
    ProjectContent(state, pending, preparing, vm, contentPadding, onBack, onViewZip, backButton)
}

@Composable
fun ProjectContent(
    state: ProjectUiState,
    pending: PendingVersion?,
    preparing: Boolean,
    actions: ProjectActions,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onViewZip: (String) -> Unit,
    backButton: @Composable (() -> Unit) -> Unit,
) {
    val project = state.project
    var editing by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val pickZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(actions::pick) }
    val uriHandler = LocalUriHandler.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "header") {
            ModuleHeader(
                TaskifyModule.Vault,
                project?.name ?: "Project",
                subtitle = project?.let { "${it.versions} versi · ${formatBytes(it.totalSize)}" },
                navigation = { backButton(onBack) },
                actions = {
                    if (project != null) {
                        IconButton(onClick = actions::toggleFavorite) {
                            Icon(if (project.favorite) Icons.Rounded.Star else Icons.Outlined.StarOutline, contentDescription = if (project.favorite) "Hapus dari favorit" else "Favoritkan",
                                tint = if (project.favorite) TaskifyTheme.modules.accent(TaskifyModule.Vault) else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Lainnya") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Ubah project") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; editing = true })
                                DropdownMenuItem(text = { Text("Pindahkan ke Sampah") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; actions.removeProject(onBack) })
                            }
                        }
                    }
                },
            )
        }
        if (state.loaded && project == null) {
            item(key = "missing") { EmptyState(TaskifyModule.Vault, "Project tidak ditemukan", "Mungkin sudah dihapus atau belum tersinkron ke perangkat ini.") }
            return@LazyColumn
        }
        project?.let { p ->
            item(key = "info") {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    p.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    p.repoUrl?.let { url ->
                        TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) {
                            Icon(Icons.Outlined.Link, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(url, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    FilledTonalButton(onClick = { pickZip.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }, enabled = !preparing) {
                        Icon(Icons.Outlined.Upload, null, Modifier.size(18.dp))
                        Text(if (preparing) "Memeriksa arsip…" else "Unggah versi baru (.zip)", Modifier.padding(start = 6.dp))
                    }
                    if (preparing) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
        item(key = "versions-h") {
            Text("VERSI", style = TaskifyTextStyles.indexLabel, color = TaskifyTheme.modules.accent(TaskifyModule.Vault), modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp))
        }
        if (state.loaded && state.versions.isEmpty()) {
            item(key = "v-empty") { Text("Belum ada versi. Unggah ZIP source code-mu — isinya dicek sebelum dikirim.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp)) }
        }
        items(state.versions, key = { it.id }) { v -> VersionRow(v, actions, onViewZip) }
    }
    if (editing && project != null) ProjectDialog(project, onDismiss = { editing = false }) { n, d, r -> actions.edit(n, d, r); editing = false }
    pending?.let { VersionSheet(it, actions) }
}

@Composable
private fun VersionRow(v: Attachment, actions: ProjectActions, onViewZip: (String) -> Unit) {
    val flow = remember(v.id) { runCatching { actions.progress(v) }.getOrElse { emptyFlow() } }
    val progress by flow.collectAsState(initial = null)
    var menu by remember { mutableStateOf(false) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let { actions.saveTo(v, it) } }
    val date = Dates.isoToLocal(v.createdAt)?.first?.let { Dates.format(it, "d MMM yyyy") } ?: ""
    val status = when {
        progress?.running == true -> "mengunggah ${((progress?.fraction ?: 0f) * 100).toInt()}%"
        v.status == AttachmentStatus.Uploaded -> null
        v.status == AttachmentStatus.Failed -> "gagal diunggah"
        else -> "menunggu jaringan"
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium, onClick = { onViewZip(v.id) }) {
            Column {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.FolderZip, null, tint = TaskifyTheme.modules.accent(TaskifyModule.Vault))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(v.versionLabel ?: "Tanpa label", style = MaterialTheme.typography.titleSmall)
                        Text(listOfNotNull(v.fileName, formatBytes(v.sizeBytes), date, status).joinToString(" · "), style = MaterialTheme.typography.labelMedium,
                            color = if (v.status == AttachmentStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        v.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    }
                    if (v.status == AttachmentStatus.Failed && progress?.running != true) TextButton(onClick = { actions.retry(v) }) { Text("Ulangi") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Aksi versi ${v.versionLabel ?: v.fileName}") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Lihat isi") }, onClick = { menu = false; onViewZip(v.id) })
                            DropdownMenuItem(text = { Text("Simpan ke perangkat…") }, onClick = { menu = false; save.launch(v.fileName) })
                            DropdownMenuItem(text = { Text("Buka dengan aplikasi lain") }, onClick = { menu = false; actions.open(v) })
                            DropdownMenuItem(text = { Text("Hapus versi") }, onClick = { menu = false; actions.remove(v) })
                        }
                    }
                }
                if (progress?.running == true) LinearProgressIndicator(progress = { progress?.fraction ?: 0f }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Ringkasan arsip sebelum diunggah: isi, peringatan, duplikat, label & catatan versi. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersionSheet(p: PendingVersion, actions: ProjectActions) {
    var label by rememberSaveable(p.file.sha256) { mutableStateOf(p.label) }
    var note by rememberSaveable(p.file.sha256) { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = actions::discardPending, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Unggah versi", style = MaterialTheme.typography.headlineSmall)
            Text(p.file.name, style = MaterialTheme.typography.titleMedium)
            val zip = p.file.zip
            Text(
                if (zip != null) "${zip.files} file · ${formatBytes(zip.totalSize)} isi · ${formatBytes(p.file.size)} terkompresi" else "${formatBytes(p.file.size)} · bukan arsip ZIP (tetap bisa disimpan)",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            zip?.warnings?.forEach { w ->
                val tone = if (w.level == WarningLevel.Danger) MaterialTheme.colorScheme.error else namedColor("amber")
                Surface(color = tone.copy(alpha = 0.12f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp)) {
                        Icon(if (w.level == WarningLevel.Danger) Icons.Outlined.ErrorOutline else Icons.Outlined.WarningAmber, null, tint = tone)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(w.title, style = MaterialTheme.typography.titleSmall, color = tone)
                            Text(w.detail, style = MaterialTheme.typography.bodySmall)
                            Text(w.paths.joinToString("\n"), style = TaskifyTextStyles.code, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            p.duplicate?.let { d ->
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Info, null)
                        Spacer(Modifier.width(10.dp))
                        Text("File identik sudah tersimpan: ${listOfNotNull(d.versionLabel, d.fileName).joinToString(" · ")}. Tetap bisa diunggah.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            OutlinedTextField(label, { label = it }, label = { Text("Label versi") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(note, { note = it }, label = { Text("Catatan rilis (opsional)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Row {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = actions::discardPending) { Text("Batal") }
                Button(onClick = { actions.uploadPending(label, note) }) { Text("Unggah") }
            }
        }
    }
}
