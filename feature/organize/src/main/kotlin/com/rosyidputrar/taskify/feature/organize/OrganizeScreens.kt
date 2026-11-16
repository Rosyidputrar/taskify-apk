package com.rosyidputrar.taskify.feature.organize

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rosyidputrar.taskify.core.designsystem.component.EmptyState
import com.rosyidputrar.taskify.core.designsystem.component.IndexCard
import com.rosyidputrar.taskify.core.designsystem.component.ModuleHeader
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.namedColor
import com.rosyidputrar.taskify.core.domain.search.EntityRow
import com.rosyidputrar.taskify.core.domain.search.label
import com.rosyidputrar.taskify.core.domain.tags.PALETTE_NAMES
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.store.cross.TrashRepository
import com.rosyidputrar.taskify.feature.links.EntityLine
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.ceil

// ================================================================== daftar tag

@Composable
fun TagsScreen(contentPadding: PaddingValues, onOpenTag: (String) -> Unit, onBack: (() -> Unit)? = null, backButton: @Composable (() -> Unit) -> Unit = {}) {
    val vm: TagsViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    TagsContent(state, contentPadding, onOpenTag, onBack, backButton)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagsContent(state: TagsUiState, contentPadding: PaddingValues, onOpenTag: (String) -> Unit, onBack: (() -> Unit)? = null, backButton: @Composable (() -> Unit) -> Unit = {}) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "header") {
            ModuleHeader(TaskifyModule.Tags, "Tag", subtitle = "${state.tags.size} tag · lintas semua modul", navigation = onBack?.let { { backButton(it) } })
        }
        if (state.loaded && state.tags.isEmpty()) item(key = "empty") {
            EmptyState(TaskifyModule.Tags, "Belum ada tag", "Tambahkan tag di tugas, jurnal, catatan, atau transaksi — atau ketik #tag saat quick add.")
        }
        item(key = "cloud") {
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                state.tags.forEach { t ->
                    val color = namedColor(t.color)
                    Row(
                        Modifier.border(1.dp, color.copy(alpha = 0.5f), MaterialTheme.shapes.large).background(color.copy(alpha = 0.1f), MaterialTheme.shapes.large)
                            .clickable { onOpenTag(t.id) }.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("#", color = color, style = MaterialTheme.typography.titleMedium)
                        Text(t.name, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.width(8.dp))
                        Text("${t.count}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ================================================================== satu tag

@Composable
fun TagScreen(tagId: String, contentPadding: PaddingValues, onBack: () -> Unit, backButton: @Composable (() -> Unit) -> Unit) {
    val vm: TagViewModel = koinViewModel(key = "tag-$tagId") { parametersOf(tagId) }
    val state by vm.state.collectAsStateWithLifecycle()
    TagContent(state, vm, contentPadding, onBack, backButton)
}

@Composable
fun TagContent(state: TagUiState, actions: TagActions, contentPadding: PaddingValues, onBack: () -> Unit, backButton: @Composable (() -> Unit) -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val tag = state.tag
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "header") {
            ModuleHeader(
                TaskifyModule.Tags, tag?.let { "#${it.name}" } ?: "Tag",
                subtitle = if (tag != null) "${state.items.size} item" else if (state.loaded) "Tag tidak ditemukan" else null,
                navigation = { backButton(onBack) },
                actions = { if (tag != null) IconButton(onClick = { editing = true }) { Icon(Icons.Outlined.Edit, contentDescription = "Ubah tag") } },
            )
        }
        if (state.loaded && tag != null && state.items.isEmpty()) item(key = "empty") {
            EmptyState(TaskifyModule.Tags, "Belum ada item", "Tambahkan tag ini di tugas, jurnal, catatan, atau transaksi.")
        }
        state.groups.forEach { (type, rows) ->
            item(key = "g-${type.value}") {
                IndexCard(TaskifyModule.Tags, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), contentPadding = 12.dp) {
                    Text("${type.label} · ${rows.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    rows.forEach { r -> EntityLine(r, onClick = { actions.open(r) }) }
                }
            }
        }
    }
    if (editing && tag != null) TagEditDialog(tag.name, tag.color, actions, onDeleted = onBack, onDismiss = { editing = false })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagEditDialog(name: String, color: String?, actions: TagActions, onDeleted: () -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(name) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ubah tag") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(text, { text = it }, label = { Text("Nama") }, prefix = { Text("#") }, singleLine = true)
                Text("Warna", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PALETTE_NAMES.forEach { c ->
                        Box(
                            Modifier.size(32.dp).background(namedColor(c), CircleShape).clickable { actions.setColor(c) },
                            contentAlignment = Alignment.Center,
                        ) { if (c == color) Icon(Icons.Rounded.Check, contentDescription = "Warna $c dipilih", tint = Color.White, modifier = Modifier.size(18.dp)) }
                    }
                }
                TextButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Outlined.DeleteForever, null, tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(6.dp))
                    Text("Hapus tag", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = { actions.rename(text) { ok -> if (ok) onDismiss() } }, enabled = text.isNotBlank()) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Hapus tag #$name?") },
        text = { Text("Tag dilepas dari semua item. Itemnya sendiri tidak terhapus.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDismiss(); actions.delete(onDeleted) }) { Text("Hapus tag", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } },
    )
}

// ================================================================== Sampah

@Composable
fun TrashScreen(contentPadding: PaddingValues, onBack: (() -> Unit)? = null, backButton: @Composable (() -> Unit) -> Unit = {}) {
    val vm: TrashViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    TrashContent(state, vm, contentPadding, onBack, backButton)
}

/** Sisa hari sebelum terhapus permanen otomatis. */
fun daysLeft(deletedAt: String?, now: Long): Int {
    val ms = deletedAt?.let(Dates::parseTimestampMillis) ?: return TrashRepository.RETENTION_DAYS
    val left = ms + TrashRepository.RETENTION_DAYS * 86_400_000L - now
    return maxOf(0, ceil(left / 86_400_000.0).toInt())
}

@Composable
fun TrashContent(state: TrashUiState, actions: TrashActions, contentPadding: PaddingValues, onBack: (() -> Unit)? = null, backButton: @Composable (() -> Unit) -> Unit = {}) {
    var confirmEmpty by rememberSaveable { mutableStateOf(false) }
    var confirmRow by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "header") {
            ModuleHeader(
                TaskifyModule.Trash, "Sampah", subtitle = "Item dihapus otomatis setelah ${TrashRepository.RETENTION_DAYS} hari",
                navigation = onBack?.let { { backButton(it) } },
                actions = { if (state.rows.isNotEmpty()) TextButton(onClick = { confirmEmpty = true }) { Icon(Icons.Outlined.DeleteSweep, null); Spacer(Modifier.width(6.dp)); Text("Kosongkan") } },
            )
        }
        if (state.loaded && state.rows.isEmpty()) item(key = "empty") {
            EmptyState(TaskifyModule.Trash, "Sampah kosong", "Item yang kamu hapus akan muncul di sini dan bisa dipulihkan.")
        }
        items(state.rows, key = { it.type.value + it.id }) { r ->
            IndexCard(TaskifyModule.Trash, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp).animateItem(), contentPadding = 10.dp) {
                EntityLine(r.copy(deletedAt = null), onClick = null, trailing = {
                    IconButton(onClick = { actions.restore(r) }) { Icon(Icons.Outlined.RestoreFromTrash, contentDescription = "Pulihkan ${r.title}") }
                    IconButton(onClick = { confirmRow = r.type.value + ":" + r.id }) { Icon(Icons.Outlined.DeleteForever, contentDescription = "Hapus permanen ${r.title}", tint = MaterialTheme.colorScheme.error) }
                })
                val deleted = r.deletedAt?.let(Dates::parseTimestampMillis)?.let { Dates.isoToLocal(Dates.millisToIso(it)) }
                Text(
                    listOfNotNull(deleted?.let { "dihapus ${Dates.format(it.first, "d MMM")}" }, "terhapus permanen dalam ${daysLeft(r.deletedAt, state.now)} hari").joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 46.dp),
                )
            }
        }
    }
    if (confirmEmpty) AlertDialog(
        onDismissRequest = { confirmEmpty = false },
        title = { Text("Kosongkan Sampah?") },
        text = { Text("${state.rows.size} item dihapus permanen beserta tag & tautannya. Tidak bisa dibatalkan.") },
        confirmButton = { TextButton(onClick = { confirmEmpty = false; actions.emptyAll() }) { Text("Kosongkan", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Batal") } },
    )
    confirmRow?.let { key ->
        val row: EntityRow? = state.rows.firstOrNull { it.type.value + ":" + it.id == key }
        if (row == null) confirmRow = null else AlertDialog(
            onDismissRequest = { confirmRow = null },
            title = { Text("Hapus permanen \"${row.title}\"?") },
            text = { Text("Tidak bisa dipulihkan lagi.") },
            confirmButton = { TextButton(onClick = { confirmRow = null; actions.destroy(row) }) { Text("Hapus permanen", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmRow = null }) { Text("Batal") } },
        )
    }
}

