package com.rosyidputrar.taskify.feature.files

import androidx.compose.runtime.remember
import com.rosyidputrar.taskify.core.designsystem.component.rememberHaptics
import com.rosyidputrar.taskify.core.designsystem.component.TaskifyHaptics
import com.rosyidputrar.taskify.core.designsystem.component.ListSkeleton
import com.rosyidputrar.taskify.core.designsystem.component.TaskifyLoadingIndicator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rosyidputrar.taskify.core.designsystem.component.EmptyState
import com.rosyidputrar.taskify.core.designsystem.component.IndexCard
import com.rosyidputrar.taskify.core.designsystem.component.ModuleHeader
import com.rosyidputrar.taskify.core.designsystem.theme.SerifFamily
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.designsystem.theme.namedColor
import com.rosyidputrar.taskify.core.domain.files.ExpiryOption
import com.rosyidputrar.taskify.core.domain.files.TempFile
import com.rosyidputrar.taskify.core.domain.files.TempFileRules
import com.rosyidputrar.taskify.core.domain.files.TempFileState
import com.rosyidputrar.taskify.core.domain.files.TempFiles
import com.rosyidputrar.taskify.core.domain.files.formatBytes
import com.rosyidputrar.taskify.core.files.UploadJob
import com.rosyidputrar.taskify.core.files.UploadPhase
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun FilesScreen(
    contentPadding: PaddingValues,
    onBack: (() -> Unit)? = null,
    backButton: @Composable (() -> Unit) -> Unit = {},
    viewModel: FilesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FilesContent(state, viewModel, contentPadding, onBack, backButton)
}

internal fun copyText(context: Context, label: String, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
}

internal fun shareText(context: Context, title: String, url: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, title).putExtra(Intent.EXTRA_TEXT, url)
    runCatching { context.startActivity(Intent.createChooser(send, "Bagikan tautan").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * Berkas publik: simpan berkas di server dan bagikan lewat tautan yang bisa dibuka siapa saja.
 * Daftar dibaca dari database lokal (tetap tampil offline); setiap perubahan butuh internet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilesContent(
    state: FilesUiState,
    actions: FilesActions,
    contentPadding: PaddingValues,
    onBack: (() -> Unit)? = null,
    backButton: @Composable (() -> Unit) -> Unit = {},
) {
    val haptics = rememberHaptics()
    @Suppress("NAME_SHADOWING")
    val actions = remember(actions, haptics) { HapticFilesActions(actions, haptics) }
    val context = LocalContext.current
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { actions.pick(it) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "header") {
            ModuleHeader(
                TaskifyModule.Files, "Berkas",
                subtitle = "Simpan & bagikan lewat tautan publik",
                navigation = onBack?.let { { backButton(it) } },
            )
        }
        if (!state.online) item(key = "offline") {
            Surface(color = namedColor("amber").copy(alpha = 0.14f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CloudOff, null, tint = namedColor("amber"))
                    Spacer(Modifier.width(10.dp))
                    Text("Sedang offline. Daftar tetap bisa dilihat; unggah dan ubah butuh internet.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item(key = "quota") { QuotaCard(state.used, state.files.size) }
        item(key = "upload") {
            IndexCard(TaskifyModule.Files, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text("Unggah berkas", style = MaterialTheme.typography.titleMedium.copy(fontFamily = SerifFamily))
                Text("Maks. 1 GB total. Tautan publik langsung dibuat; bisa dicabut kapan saja.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Text("Masa berlaku tautan & berkas", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpiryOption.entries.forEach { e -> FilterChip(state.expiry == e, { actions.setExpiry(e) }, label = { Text(e.label) }) }
                }
                Text("Setelah lewat, tautan berhenti bekerja dan berkas dihapus otomatis.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                Button(onClick = { pick.launch(arrayOf("*/*")) }, enabled = state.online, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.CloudUpload, null); Spacer(Modifier.width(8.dp)); Text("Pilih berkas")
                }
            }
        }
        items(state.jobs, key = { "j-" + it.key }) { job -> JobRow(job, actions, Modifier.animateItem()) }
        val listed = state.listed
        if (!state.loaded) item(key = "skeleton") { ListSkeleton(TaskifyModule.Files, rows = 4) }
        if (state.loaded && listed.isEmpty() && state.jobs.isEmpty()) item(key = "empty") {
            EmptyState(TaskifyModule.Files, "Belum ada berkas", "Unggah berkas untuk mendapatkan tautan yang bisa dibuka siapa saja, tanpa login.")
        }
        items(listed, key = { it.id }) { f ->
            FileRow(f, state.now, onOpen = { detailId = f.id }, onCopy = {
                copyText(context, f.fileName, actions.link(f)); actions.copied()
            }, modifier = Modifier.animateItem())
        }
    }
    state.files.firstOrNull { it.id == detailId }?.let { file ->
        FileSheet(file, state, actions, onDismiss = { detailId = null })
    }
}

@Composable
private fun QuotaCard(used: Long, count: Int) {
    val fraction = (used.toDouble() / TempFileRules.QUOTA_BYTES).toFloat().coerceIn(0f, 1f)
    val color = when {
        fraction >= 0.95f -> MaterialTheme.colorScheme.error
        fraction >= 0.8f -> namedColor("amber")
        else -> TaskifyTheme.modules.accent(TaskifyModule.Files)
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(formatBytes(used), style = MaterialTheme.typography.titleLarge.copy(fontFamily = SerifFamily))
            Text("  dari 1 GB", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text("$count berkas", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { fraction }, color = color, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(MaterialTheme.shapes.small).semantics { contentDescription = "Kuota terpakai ${(fraction * 100).toInt()} persen" },
        )
    }
}

@Composable
private fun JobRow(job: UploadJob, actions: FilesActions, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    job.phase.active -> TaskifyLoadingIndicator(size = 28.dp, module = TaskifyModule.Files)
                    job.phase == UploadPhase.Failed -> Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(28.dp))
                    else -> Icon(Icons.Outlined.CheckCircle, null, tint = namedColor("emerald"), modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(job.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            job.phase.label + if (job.phase == UploadPhase.Uploading) " ${(job.progress * 100).toInt()}%" else "",
                            formatBytes(job.size), job.error,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (job.phase == UploadPhase.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (job.phase.active) IconButton(onClick = { actions.cancel(job.key) }) { Icon(Icons.Rounded.Close, contentDescription = "Batalkan unggah ${job.name}") }
                else if (job.phase == UploadPhase.Failed) IconButton(onClick = { actions.dismiss(job.key) }) { Icon(Icons.Rounded.Close, contentDescription = "Tutup") }
            }
            if (job.phase == UploadPhase.Uploading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { job.progress }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun FileRow(file: TempFile, now: Long, onOpen: () -> Unit, onCopy: () -> Unit, modifier: Modifier = Modifier) {
    val state = TempFiles.state(file, now)
    IndexCard(TaskifyModule.Files, onClick = onOpen, onClickLabel = "Buka detail berkas", modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp), contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FileGlyph(file.mimeType, file.fileName)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(file.fileName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${formatBytes(file.sizeBytes)} · ${file.downloadCount}× diunduh", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val label = when (state) {
                        TempFileState.Pending -> "Belum selesai diunggah"
                        else -> TempFiles.expiryShort(file.expiresAt, now)
                    }
                    Text(label, style = MaterialTheme.typography.labelMedium,
                        color = if (state == TempFileState.Ready) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                    if (!file.isPublic && state == TempFileState.Ready) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Outlined.VisibilityOff, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(" Privat", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (TempFiles.isShareable(file, now)) IconButton(onClick = onCopy) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Salin tautan ${file.fileName}") }
        }
    }
}

/** Getar halus per makna (lihat [TaskifyHaptics]) tanpa mengubah logika aksi. */
private class HapticFilesActions(private val a: FilesActions, private val h: TaskifyHaptics) : FilesActions by a {
    override fun setExpiry(expiry: ExpiryOption) { h.tick(); a.setExpiry(expiry) }
    override fun copied() { h.success(); a.copied() }
    override fun setPublic(file: TempFile, value: Boolean) { h.toggle(value); a.setPublic(file, value) }
    override fun applyExpiry(file: TempFile, expiry: ExpiryOption) { h.tick(); a.applyExpiry(file, expiry) }
}
