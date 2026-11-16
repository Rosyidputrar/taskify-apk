package com.rosyidputrar.taskify.feature.attachments

import com.rosyidputrar.taskify.core.designsystem.component.ConfirmDeleteDialog
import com.rosyidputrar.taskify.core.designsystem.component.LoadingRow
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rosyidputrar.taskify.core.domain.files.Attachment
import com.rosyidputrar.taskify.core.domain.files.AttachmentStatus
import com.rosyidputrar.taskify.core.domain.files.EntityType
import com.rosyidputrar.taskify.core.domain.files.attachmentLabel
import com.rosyidputrar.taskify.core.domain.files.formatBytes
import com.rosyidputrar.taskify.core.files.UploadProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.File

/**
 * Bagian lampiran sebuah entitas (foto jurnal, struk transaksi, file tugas). Unggahan masuk
 * antrean dan tetap jalan offline/latar; status & progres tampil per file.
 */
@Composable
fun AttachmentsSection(type: EntityType, entityId: String, modifier: Modifier = Modifier, label: String = attachmentLabel(type)) {
    val vm: AttachmentsViewModel = koinViewModel(key = "att-${type.value}-$entityId") { parametersOf(type, entityId) }
    val items by vm.items.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    AttachmentsContent(items, busy, vm, modifier, label)
}

@Composable
fun AttachmentsContent(items: List<Attachment>, busy: String?, actions: AttachmentActions, modifier: Modifier = Modifier, label: String = "Lampiran", localFile: (Attachment) -> File? = { null }) {
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris -> if (uris.isNotEmpty()) actions.add(uris) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) actions.add(uris) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (items.isNotEmpty()) Text("${items.size} file", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items.forEach { att -> AttachmentRow(att, actions, localFile(att)) }
        busy?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LoadingRow(it)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = { pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                label = { Text("Foto") },
                leadingIcon = { Icon(Icons.Outlined.AddPhotoAlternate, null, Modifier.size(18.dp)) },
            )
            AssistChip(
                onClick = { pickFile.launch(arrayOf("*/*")) },
                label = { Text("File") },
                leadingIcon = { Icon(Icons.Outlined.AttachFile, null, Modifier.size(18.dp)) },
            )
        }
        if (items.isEmpty() && busy == null) {
            Text("Maks. 100 MB per file. Foto besar diperkecil otomatis.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AttachmentRow(att: Attachment, actions: AttachmentActions, previewFile: File?) {
    val progressFlow = remember(att.id) { runCatching { actions.progress(att) }.getOrElse { emptyFlow() } }
    val progress: UploadProgress? by progressFlow.collectAsState(initial = null)
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(att.mimeType ?: "application/octet-stream")) { uri -> uri?.let { actions.saveTo(att, it) } }
    val context = LocalContext.current
    val local = previewFile ?: remember(att.id, att.status) { if (actions.hasLocalCopy(att)) File(File(context.filesDir, "attachments"), att.id) else null }
    val status = statusText(att, progress)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClickLabel = "Buka") { actions.open(att) }
            .semantics(mergeDescendants = true) { contentDescription = "${att.fileName}, ${formatBytes(att.sizeBytes)}, $status" },
    ) {
        Column {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Thumbnail(att, local)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(listOfNotNull(att.versionLabel, att.fileName).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${formatBytes(att.sizeBytes)} · $status",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (att.status == AttachmentStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    att.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
                if (att.status == AttachmentStatus.Failed && progress?.running != true) TextButton(onClick = { actions.retry(att) }) { Text("Ulangi") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Aksi ${att.fileName}") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Buka") }, onClick = { menu = false; actions.open(att) })
                        DropdownMenuItem(text = { Text("Simpan ke perangkat…") }, onClick = { menu = false; save.launch(att.fileName) })
                        if (att.status != AttachmentStatus.Uploaded) DropdownMenuItem(text = { Text("Batalkan unggahan") }, onClick = { menu = false; actions.cancel(att) })
                        DropdownMenuItem(text = { Text("Hapus") }, onClick = { menu = false; confirmDelete = true })
                    }
                }
                if (confirmDelete) {
                    ConfirmDeleteDialog(
                        title = "Hapus lampiran?",
                        message = "${att.fileName} dihapus dari perangkat ini dan dari server. Tidak bisa dibatalkan.",
                        onConfirm = { confirmDelete = false; actions.remove(att) },
                        onDismiss = { confirmDelete = false },
                    )
                }
            }
            val running = progress?.running == true
            if (running) {
                LinearProgressIndicator(progress = { progress?.fraction ?: 0f }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private fun statusText(att: Attachment, p: UploadProgress?): String = when {
    p?.running == true -> "mengunggah ${((p.fraction ?: 0f) * 100).toInt()}%"
    att.status == AttachmentStatus.Uploaded -> "tersimpan di cloud"
    att.status == AttachmentStatus.Failed -> p?.error?.let { "gagal: $it" } ?: "gagal diunggah"
    else -> "menunggu jaringan"
}

/** Pratinjau kecil: gambar dari salinan lokal (tanpa mengunduh otomatis), ikon untuk jenis lain. */
@Composable
private fun Thumbnail(att: Attachment, local: File?) {
    val bitmap by produceState<ImageBitmap?>(null, att.id, local) {
        value = if (att.isImage && local != null && local.exists()) withContext(Dispatchers.IO) { decodeThumb(local) } else null
    }
    Box(Modifier.size(52.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        val b = bitmap
        if (b != null) {
            Image(b, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(iconFor(att), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (att.status != AttachmentStatus.Uploaded) {
            Icon(
                if (att.status == AttachmentStatus.Failed) Icons.Outlined.ErrorOutline else Icons.Outlined.CloudOff,
                null,
                tint = if (att.status == AttachmentStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomEnd).padding(2.dp).size(14.dp),
            )
        }
    }
}

fun iconFor(att: Attachment): ImageVector = when {
    att.isImage -> Icons.Outlined.Image
    att.isPdf -> Icons.Outlined.PictureAsPdf
    att.isZip -> Icons.Outlined.FolderZip
    else -> Icons.Outlined.Description
}

private fun decodeThumb(file: File): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 160) sample *= 2
    BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()
