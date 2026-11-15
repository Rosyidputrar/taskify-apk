package com.rosyidputrar.taskify.feature.vault

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.designsystem.theme.namedColor
import com.rosyidputrar.taskify.core.domain.files.Attachment
import com.rosyidputrar.taskify.core.domain.files.TreeNode
import com.rosyidputrar.taskify.core.domain.files.WarningLevel
import com.rosyidputrar.taskify.core.domain.files.ZipSummary
import com.rosyidputrar.taskify.core.domain.files.buildTree
import com.rosyidputrar.taskify.core.domain.files.formatBytes
import com.rosyidputrar.taskify.core.domain.files.isImagePath
import com.rosyidputrar.taskify.core.domain.files.isTextPath
import com.rosyidputrar.taskify.core.domain.files.summarizeZip
import com.rosyidputrar.taskify.core.domain.files.unwrapSingleRoot
import com.rosyidputrar.taskify.core.domain.vault.languageFromPath
import com.rosyidputrar.taskify.core.files.AttachmentManager
import com.rosyidputrar.taskify.core.files.ZipReader
import com.rosyidputrar.taskify.core.store.files.AttachmentRepository
import com.rosyidputrar.taskify.feature.vault.component.CodeView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.File

sealed interface EntryPreview {
    data class Text(val path: String, val content: String, val truncated: Boolean) : EntryPreview
    data class Picture(val path: String, val image: ImageBitmap) : EntryPreview
    data class Unsupported(val path: String, val size: Long) : EntryPreview
}

data class ZipViewerState(
    val attachment: Attachment? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val summary: ZipSummary? = null,
    val root: TreeNode? = null,
    /** Folder yang sedang dibuka (rantai dari akar). */
    val stack: List<TreeNode> = emptyList(),
    val preview: EntryPreview? = null,
) {
    val folder: TreeNode? get() = stack.lastOrNull() ?: root
}

class ZipViewerViewModel(private val attachmentId: String, private val repo: AttachmentRepository, private val files: AttachmentManager, private val messenger: Messenger) : ViewModel() {
    private val _state = MutableStateFlow(ZipViewerState())
    val state: StateFlow<ZipViewerState> = _state
    private var local: File? = null

    init {
        viewModelScope.launch {
            try {
                val att = repo.get(attachmentId) ?: throw IllegalStateException("Versi tidak ditemukan")
                _state.update { it.copy(attachment = att) }
                val file = files.ensureLocal(att)
                local = file
                val entries = withContext(Dispatchers.IO) { ZipReader.entries(file) }
                val root = buildTree(entries).unwrapSingleRoot()
                _state.update { it.copy(loading = false, summary = summarizeZip(entries), root = root) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = if (e is java.util.zip.ZipException) "Bukan arsip ZIP yang valid" else e.message ?: "Gagal membuka arsip") }
            }
        }
    }

    fun openNode(node: TreeNode) {
        if (node.dir) {
            _state.update { it.copy(stack = it.stack + node, preview = null) }
            return
        }
        val file = local ?: return
        viewModelScope.launch {
            val preview = withContext(Dispatchers.IO) {
                when {
                    isTextPath(node.path) -> ZipReader.readText(file, node.path)?.let { (text, cut) -> EntryPreview.Text(node.path, text, cut) }
                    isImagePath(node.path) && !node.path.endsWith(".svg", true) -> ZipReader.readBytes(file, node.path)?.let { bytes ->
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()?.let { EntryPreview.Picture(node.path, it) }
                    }
                    else -> null
                } ?: EntryPreview.Unsupported(node.path, node.size)
            }
            _state.update { it.copy(preview = preview) }
        }
    }

    /** Kembali satu tingkat; false bila sudah di akar tanpa pratinjau. */
    fun up(): Boolean {
        val s = _state.value
        return when {
            s.preview != null -> { _state.update { it.copy(preview = null) }; true }
            s.stack.isNotEmpty() -> { _state.update { it.copy(stack = it.stack.dropLast(1)) }; true }
            else -> false
        }
    }

    fun saveEntry(path: String, target: Uri) {
        val att = _state.value.attachment ?: return
        viewModelScope.launch {
            runCatching { files.saveZipEntry(att, path, target) }
                .onSuccess { messenger.show("${path.substringAfterLast('/')} disimpan") }
                .onFailure { messenger.show(it.message ?: "Gagal menyimpan") }
        }
    }
}

@Composable
fun ZipViewerScreen(attachmentId: String, onBack: () -> Unit) {
    val vm: ZipViewerViewModel = koinViewModel(key = "zip-$attachmentId") { parametersOf(attachmentId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val back = { if (!vm.up()) onBack() }
    BackHandler(onBack = back)
    ZipViewerContent(state, back, vm::openNode, vm::saveEntry)
}

@Composable
fun ZipViewerContent(state: ZipViewerState, back: () -> Unit, open: (TreeNode) -> Unit, save: (String, Uri) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val preview = state.preview
    var savePath by rememberSaveable { mutableStateOf("") }
    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri -> if (uri != null && savePath.isNotEmpty()) save(savePath, uri) }
    Column(Modifier.fillMaxSize().background(colors.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali") }
            Column(Modifier.weight(1f)) {
                Text(
                    preview?.let { p -> pathOf(p).substringAfterLast('/') } ?: state.folder?.name?.ifEmpty { null } ?: (state.attachment?.versionLabel ?: "Isi arsip"),
                    style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    preview?.let(::pathOf) ?: listOfNotNull(state.attachment?.fileName, state.summary?.let { "${it.files} file · ${formatBytes(it.totalSize)}" }).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (preview != null) {
                IconButton(onClick = { savePath = pathOf(preview); saver.launch(pathOf(preview).substringAfterLast('/')) }) { Icon(Icons.Outlined.Download, contentDescription = "Simpan file ini") }
            }
        }
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("Mengambil arsip…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            state.error != null -> Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Outlined.ErrorOutline, null, tint = colors.error, modifier = Modifier.size(40.dp))
                Text(state.error, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
            }
            preview != null -> PreviewPane(preview)
            else -> FolderList(state, open)
        }
    }
}

private fun pathOf(p: EntryPreview) = when (p) {
    is EntryPreview.Text -> p.path
    is EntryPreview.Picture -> p.path
    is EntryPreview.Unsupported -> p.path
}

@Composable
private fun FolderList(state: ZipViewerState, open: (TreeNode) -> Unit) {
    val folder = state.folder ?: return
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        if (state.stack.isEmpty()) {
            state.summary?.warnings?.forEach { w ->
                item(key = "w-${w.title}") {
                    val tone = if (w.level == WarningLevel.Danger) MaterialTheme.colorScheme.error else namedColor("amber")
                    Surface(color = tone.copy(alpha = 0.12f), shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (w.level == WarningLevel.Danger) Icons.Outlined.ErrorOutline else Icons.Outlined.WarningAmber, null, tint = tone)
                            Spacer(Modifier.width(10.dp))
                            Text(w.title, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        if (state.stack.isNotEmpty()) {
            item(key = "crumb") {
                Text(
                    "/" + state.stack.joinToString("/") { it.name },
                    style = TaskifyTextStyles.code, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
        items(folder.children, key = { it.path }) { node ->
            Row(
                Modifier.fillMaxWidth().clickable(onClickLabel = if (node.dir) "Buka folder" else "Lihat file") { open(node) }.padding(horizontal = 20.dp, vertical = 12.dp)
                    .semantics(mergeDescendants = true) { contentDescription = "${if (node.dir) "Folder" else "File"} ${node.name}, ${formatBytes(node.size)}" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    when { node.dir -> Icons.Outlined.Folder; isImagePath(node.path) -> Icons.Outlined.Image; else -> Icons.Outlined.Description },
                    null,
                    tint = if (node.dir) TaskifyTheme.modules.accent(TaskifyModule.Vault) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(14.dp))
                Text(node.name + if (node.dir) "/" else "", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (node.dir) "${node.children.size} item · ${formatBytes(node.size)}" else formatBytes(node.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PreviewPane(p: EntryPreview) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (p) {
            is EntryPreview.Text -> {
                CodeView(p.content, languageFromPath(p.path))
                if (p.truncated) Text("Ditampilkan 512 KB pertama. Simpan file untuk isi lengkap.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is EntryPreview.Picture -> Image(p.image, contentDescription = p.path.substringAfterLast('/'), contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
            is EntryPreview.Unsupported -> Text(
                "File ini (${formatBytes(p.size)}) tidak bisa dipratinjau di sini. Simpan ke perangkat dengan tombol unduh di atas.",
                style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp),
            )
        }
    }
}
