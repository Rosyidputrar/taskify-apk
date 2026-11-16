package com.rosyidputrar.taskify.feature.files

import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.component.LoadingRow
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.designsystem.theme.SerifFamily
import com.rosyidputrar.taskify.core.designsystem.theme.namedColor
import com.rosyidputrar.taskify.core.domain.files.ExpiryOption
import com.rosyidputrar.taskify.core.domain.files.TempFile
import com.rosyidputrar.taskify.core.domain.files.TempFileRules
import com.rosyidputrar.taskify.core.domain.files.TempFileState
import com.rosyidputrar.taskify.core.domain.files.TempFiles
import com.rosyidputrar.taskify.core.domain.files.formatBytes
import com.rosyidputrar.taskify.core.domain.time.Dates

internal fun dateTime(ts: String): String = Dates.parseTimestampMillis(ts)?.let { Dates.isoToLocal(Dates.millisToIso(it)) }
    ?.let { (d, t) -> "${Dates.format(d, "d MMM yyyy")} $t" } ?: ts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FileSheet(file: TempFile, state: FilesUiState, actions: FilesActions, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        FileDetail(file, state, actions, onDeleted = onDismiss)
    }
}

/** Detail satu berkas: tautan, publik/privat, masa berlaku, ganti nama, unduh, hapus. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FileDetail(file: TempFile, state: FilesUiState, actions: FilesActions, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val fileState = TempFiles.state(file, state.now)
    val shareable = TempFiles.isShareable(file, state.now)
    val editable = fileState != TempFileState.Expired && state.online && state.busy == null
    var name by rememberSaveable(file.id) { mutableStateOf(file.fileName) }
    var expiry by rememberSaveable(file.id) { mutableStateOf(ExpiryOption.DEFAULT) }
    var confirmToken by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(file.mimeType.ifBlank { "application/octet-stream" })) { uri ->
        uri?.let { actions.download(file, it) }
    }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FileGlyph(file.mimeType, file.fileName, size = 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(file.fileName, style = MaterialTheme.typography.titleMedium.copy(fontFamily = SerifFamily), maxLines = 3)
                Text("${formatBytes(file.sizeBytes)} · diunggah ${dateTime(file.createdAt)}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.busy != null) state.downloadProgress?.let { p -> LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth()) }
            ?: LoadingRow(
                when (state.busy) {
                    "download" -> "Mengunduh…"
                    "delete" -> "Menghapus…"
                    "rename" -> "Mengganti nama…"
                    "token" -> "Membuat tautan baru…"
                    "expiry" -> "Memperbarui masa berlaku…"
                    else -> "Menyimpan…"
                },
                module = TaskifyModule.Files,
            )
        when (fileState) {
            TempFileState.Expired -> Note("Berkas ini sudah kedaluwarsa. Tautannya tidak bekerja lagi dan berkasnya dihapus otomatis dalam beberapa saat.", true)
            TempFileState.Pending -> Note("Unggahan belum selesai. Bila tidak sedang diunggah, hapus lalu unggah ulang. Pesanan yang ditinggalkan dibersihkan otomatis.", true)
            TempFileState.Ready -> {
                HorizontalDivider()
                Text("Tautan", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.toggleable(file.isPublic, enabled = editable, role = Role.Switch) { actions.setPublic(file, it) }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (file.isPublic) "Siapa pun yang punya tautan bisa mengunduh" else "Privat", style = MaterialTheme.typography.bodyLarge)
                        Text(if (file.isPublic) "Matikan untuk menghentikan tautan sementara tanpa menghapus berkas." else "Tautan tidak bekerja sampai diaktifkan lagi.",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(file.isPublic, onCheckedChange = null, enabled = editable)
                }
                if (shareable) {
                    val url = actions.link(file)
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            SelectionContainer(Modifier.weight(1f)) {
                                Text(url, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), maxLines = 2)
                            }
                            IconButton(onClick = { copyText(context, file.fileName, url); actions.copied() }) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Salin tautan") }
                            IconButton(onClick = { shareText(context, file.fileName, url) }) { Icon(Icons.Outlined.Share, contentDescription = "Bagikan tautan") }
                        }
                    }
                    Text(
                        "${file.downloadCount}× diunduh" + (file.lastDownloadAt?.let { " · terakhir ${dateTime(it)}" } ?: "") + " · " + TempFiles.expiryLabel(file.expiresAt, state.now),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { confirmToken = true }, enabled = editable) {
                    Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Buat tautan baru (cabut yang lama)")
                }
            }
        }
        if (fileState != TempFileState.Expired) {
            HorizontalDivider()
            Text("Masa berlaku baru, dihitung dari sekarang", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpiryOption.entries.forEach { e -> FilterChip(expiry == e, { expiry = e }, label = { Text(e.label) }, enabled = editable) }
            }
            FilledTonalButton(onClick = { actions.applyExpiry(file, expiry) }, enabled = editable) { Text("Terapkan") }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(name, { if (it.length <= TempFileRules.MAX_NAME) name = it }, label = { Text("Nama berkas") }, singleLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = { actions.rename(file, name) }, enabled = editable && name.isNotBlank() && name.trim() != file.fileName) { Text("Simpan") }
            }
        }
        HorizontalDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (fileState == TempFileState.Ready) OutlinedButton(onClick = { save.launch(file.fileName) }, enabled = state.online && state.busy == null, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Download, null); Spacer(Modifier.width(6.dp)); Text("Unduh")
            }
            OutlinedButton(onClick = { confirmDelete = true }, enabled = state.online && state.busy == null, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(6.dp)); Text("Hapus", color = MaterialTheme.colorScheme.error)
            }
        }
        if (!state.online) Text("Offline: perubahan butuh koneksi internet.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (confirmToken) AlertDialog(
        onDismissRequest = { confirmToken = false },
        title = { Text("Buat tautan baru?") },
        text = { Text("Tautan lama langsung berhenti bekerja. Siapa pun yang sudah menerimanya tidak bisa mengunduh lagi.") },
        confirmButton = { TextButton(onClick = { confirmToken = false; actions.regenerate(file) }) { Text("Buat tautan baru") } },
        dismissButton = { TextButton(onClick = { confirmToken = false }) { Text("Batal") } },
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Hapus berkas?") },
        text = { Text("Berkas dihapus dari server dan tautannya berhenti bekerja. Tidak bisa dibatalkan.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; actions.delete(file, onDeleted) }) { Text("Hapus", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } },
    )
}

@Composable
private fun Note(text: String, warning: Boolean) {
    Surface(color = (if (warning) namedColor("amber") else MaterialTheme.colorScheme.secondary).copy(alpha = 0.14f), shape = MaterialTheme.shapes.medium) {
        Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
