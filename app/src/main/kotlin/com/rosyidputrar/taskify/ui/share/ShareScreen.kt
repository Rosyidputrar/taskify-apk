package com.rosyidputrar.taskify.ui.share

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.common.Rupiah
import com.rosyidputrar.taskify.core.designsystem.component.EmptyState
import com.rosyidputrar.taskify.core.designsystem.component.IndexCard
import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.designsystem.component.ModuleHeader
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.domain.files.EntityType
import com.rosyidputrar.taskify.core.domain.files.ExpiryOption
import com.rosyidputrar.taskify.core.domain.files.formatBytes
import com.rosyidputrar.taskify.core.domain.files.nextVersionLabel
import com.rosyidputrar.taskify.core.domain.share.Share
import com.rosyidputrar.taskify.core.domain.media.TikTok
import androidx.compose.runtime.remember
import com.rosyidputrar.taskify.core.domain.share.SharedKind
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.domain.vault.CodeProject
import com.rosyidputrar.taskify.core.domain.vault.languageFromPath
import com.rosyidputrar.taskify.core.files.AttachmentManager
import com.rosyidputrar.taskify.core.files.PickedFile
import com.rosyidputrar.taskify.core.files.TempFileUploader
import com.rosyidputrar.taskify.core.store.files.AttachmentRepository
import com.rosyidputrar.taskify.core.store.finance.FinanceRepository
import com.rosyidputrar.taskify.core.store.finance.RecentExpense
import com.rosyidputrar.taskify.core.store.journal.JournalRepository
import com.rosyidputrar.taskify.core.store.tasks.NewTask
import com.rosyidputrar.taskify.core.store.tasks.TaskRepository
import com.rosyidputrar.taskify.core.store.vault.VaultRepository
import com.rosyidputrar.taskify.feature.files.FileGlyph
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import java.io.File

data class ShareUiState(
    val content: ShareContent? = null,
    val projects: List<CodeProject> = emptyList(),
    val expenses: List<RecentExpense> = emptyList(),
    val busy: String? = null,
)

/** Tujuan satu berkas. */
sealed interface FileTarget {
    data class Project(val id: String?) : FileTarget
    data object Snippet : FileTarget
    data object Journal : FileTarget
    data class Receipt(val txId: String) : FileTarget
    data object PublicFile : FileTarget
}

interface ShareActions {
    fun saveTask()
    fun saveJournal()
    fun saveSnippet()
    fun quickCapture(): String
    fun saveFile(file: SharedFile, target: FileTarget)
    fun dismissText()
    fun finish()
}

class ShareViewModel(
    private val inbox: ShareInbox,
    private val tasks: TaskRepository,
    private val journal: JournalRepository,
    private val vault: VaultRepository,
    private val attachmentsRepo: AttachmentRepository,
    private val attachments: AttachmentManager,
    private val tempFiles: TempFileUploader,
    finance: FinanceRepository,
    private val messenger: Messenger,
) : ViewModel(), ShareActions {
    private val busy = MutableStateFlow<String?>(null)
    val state: StateFlow<ShareUiState> = combine(inbox.content, vault.watchProjects(), finance.watchRecentExpenses(), busy) { c, p, e, b -> ShareUiState(c, p, e, b) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShareUiState(inbox.content.value))

    private val text get() = state.value.content?.merged.orEmpty()
    private val title get() = state.value.content?.title.orEmpty()

    private fun act(key: String, block: suspend () -> String) {
        if (busy.value != null) return
        busy.value = key
        viewModelScope.launch {
            try {
                messenger.show(block())
                inbox.done(key)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messenger.show(e.message ?: "Gagal menyimpan")
            } finally {
                busy.value = null
            }
        }
    }

    override fun saveTask() = act(ShareInbox.TEXT_KEY) {
        val first = Share.firstLine(title, text)
        tasks.create(NewTask(title = first, dueDate = Dates.today(), notes = text.takeIf { it != first }))
        "Tugas dibuat untuk hari ini"
    }

    override fun saveJournal() = act(ShareInbox.TEXT_KEY) {
        journal.create(Dates.today(), title.ifBlank { null }, text)
        "Disimpan ke jurnal hari ini"
    }

    override fun saveSnippet() = act(ShareInbox.TEXT_KEY) {
        vault.saveSnippet(null, Share.firstLine(title, text), "plaintext", text, null)
        "Disimpan sebagai snippet"
    }

    override fun quickCapture(): String {
        val t = text.replace(Regex("\\s+"), " ").take(300)
        inbox.done(ShareInbox.TEXT_KEY)
        return t
    }

    override fun dismissText() = inbox.done(ShareInbox.TEXT_KEY)

    override fun saveFile(file: SharedFile, target: FileTarget) = act(file.key) {
        when (target) {
            is FileTarget.Project -> {
                val projectId = target.id ?: vault.saveProject(null, file.name.replace(Regex("\\.zip$", RegexOption.IGNORE_CASE), ""), null, null)
                val labels = attachmentsRepo.versionLabels(projectId)
                attachments.commit(attachments.prepareLocal(file.file, file.name, file.mime, compress = false), EntityType.CodeProject, projectId, versionLabel = nextVersionLabel(labels))
                "Versi project disimpan · mengunggah"
            }
            FileTarget.Snippet -> {
                vault.saveSnippet(null, file.name, languageFromPath(file.name), file.file.readText(), "Dibagikan dari aplikasi lain")
                "Snippet dibuat"
            }
            FileTarget.Journal -> {
                val id = journal.create(Dates.today(), "Lampiran: ${file.name}", "")
                attachments.commit(attachments.prepareLocal(file.file, file.name, file.mime), EntityType.JournalEntry, id)
                "Dilampirkan ke jurnal hari ini"
            }
            is FileTarget.Receipt -> {
                attachments.commit(attachments.prepareLocal(file.file, file.name, file.mime), EntityType.Transaction, target.txId)
                "Struk dilampirkan ke transaksi"
            }
            FileTarget.PublicFile -> {
                // Salinan terpisah: unggah berjalan di latar, sedangkan kotak masuk membersihkan berkasnya.
                val keep = File(file.file.parentFile?.parentFile, "share-upload").apply { mkdirs() }.let { File(it, file.key) }
                file.file.copyTo(keep, overwrite = true)
                tempFiles.start(PickedFile(Uri.fromFile(keep), file.name, keep.length(), file.mime), ExpiryOption.DEFAULT)
                "Mengunggah ke Berkas (tautan 7 hari)"
            }
        }
    }

    override fun finish() = inbox.clear()
}

@Composable
fun ShareScreen(contentPadding: PaddingValues, onCapture: (String) -> Unit, onDone: () -> Unit, backButton: @Composable (() -> Unit) -> Unit, onTikTok: (String) -> Unit = {}) {
    val vm: ShareViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    ShareContentView(state, vm, contentPadding, onCapture, onDone, backButton, extraTop = { text -> TikTokShareCard(text, onTikTok) })
}

/** Teks dari aplikasi TikTok → niatnya hampir pasti mengunduh: tawarkan pengunduh paling atas. */
@Composable
private fun TikTokShareCard(text: String, onOpen: (String) -> Unit) {
    val link = remember(text) { TikTok.findLink(text) } ?: return
    IndexCard(TaskifyModule.TikTok, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text("Link TikTok terdeteksi", style = MaterialTheme.typography.titleMedium)
        Text(link, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.padding(4.dp))
        Button(onClick = { onOpen(link) }, modifier = Modifier.fillMaxWidth()) { Text("Lihat & unduh video/foto") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShareContentView(
    state: ShareUiState,
    actions: ShareActions,
    contentPadding: PaddingValues,
    onCapture: (String) -> Unit,
    onDone: () -> Unit,
    backButton: @Composable (() -> Unit) -> Unit,
    extraTop: @Composable (String) -> Unit = {},
) {
    val content = state.content
    val text = content?.merged.orEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "header") {
            ModuleHeader(TaskifyModule.Home, "Dibagikan ke Taskify", subtitle = "Pilih tujuan untuk setiap item", navigation = { backButton { actions.finish(); onDone() } })
        }
        if (content == null) {
            item(key = "empty") {
                EmptyState(TaskifyModule.Home, "Tidak ada yang dibagikan", "Dari aplikasi lain (galeri, file manager, browser), pilih Bagikan → Taskify.",
                    action = { Button(onClick = onDone) { Text("Kembali") } })
            }
            return@LazyColumn
        }
        if (text.isNotBlank()) item(key = "extra") { extraTop(text) }
        if (text.isNotBlank()) item(key = "text") {
            IndexCard(TaskifyModule.Home, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Text(text, Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()).padding(12.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                }
                Spacer(Modifier.padding(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = actions::saveTask, enabled = state.busy == null) { Icon(Icons.Outlined.TaskAlt, null); Spacer(Modifier.width(6.dp)); Text("Tugas") }
                    FilledTonalButton(onClick = actions::saveJournal, enabled = state.busy == null) { Icon(Icons.Outlined.EditNote, null); Spacer(Modifier.width(6.dp)); Text("Jurnal") }
                    FilledTonalButton(onClick = actions::saveSnippet, enabled = state.busy == null) { Icon(Icons.Outlined.Code, null); Spacer(Modifier.width(6.dp)); Text("Snippet") }
                    OutlinedButton(onClick = { onCapture(actions.quickCapture()) }) { Icon(Icons.Outlined.Bolt, null); Spacer(Modifier.width(6.dp)); Text("Catat cepat…") }
                }
            }
        }
        if (content.skipped > 0) item(key = "skipped") {
            Text(
                "${content.skipped} berkas tidak diterima (tidak bisa dibaca, terlalu besar, atau melebihi ${com.rosyidputrar.taskify.core.domain.share.Share.MAX_FILES} berkas).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }
        items(content.files, key = { it.key }) { f -> FileTargetCard(f, state, actions, Modifier.animateItem()) }
        if (state.busy != null) item(key = "busy") { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) }
        item(key = "finish") {
            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { actions.finish(); onDone() }) { Text("Abaikan sisanya") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FileTargetCard(file: SharedFile, state: ShareUiState, actions: ShareActions, modifier: Modifier) {
    val kind = file.kind
    val options: List<Pair<String, FileTarget>> = buildList {
        if (kind == SharedKind.Zip) {
            add("Project baru: ${file.name.removeSuffix(".zip")}" to FileTarget.Project(null))
            state.projects.take(8).forEach { add("Versi baru · ${it.name}" to FileTarget.Project(it.id)) }
        }
        if (kind == SharedKind.Text) add("Snippet baru" to FileTarget.Snippet)
        if (kind != SharedKind.Zip) add("Lampiran jurnal hari ini" to FileTarget.Journal)
        if (kind == SharedKind.Image || kind == SharedKind.Other) state.expenses.take(6).forEach { t ->
            add("Struk · ${t.title} · ${Rupiah.format(t.amount)} · ${runCatching { Dates.format(t.day, "d MMM") }.getOrDefault(t.day)}" to FileTarget.Receipt(t.id))
        }
        add("Berkas publik (tautan unduh)" to FileTarget.PublicFile)
    }
    var selected by rememberSaveable(file.key) { mutableStateOf(0) }
    IndexCard(TaskifyModule.Home, modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FileGlyph(file.mime, file.name)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(file.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(formatBytes(file.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.padding(4.dp))
        Text("Simpan ke", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        options.forEachIndexed { i, (label, _) ->
            Row(
                Modifier.fillMaxWidth().selectable(selected == i, role = Role.RadioButton) { selected = i }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected == i, onClick = null)
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Button(onClick = { options.getOrNull(selected)?.let { actions.saveFile(file, it.second) } }, enabled = state.busy == null, modifier = Modifier.fillMaxWidth()) { Text("Simpan") }
    }
}
