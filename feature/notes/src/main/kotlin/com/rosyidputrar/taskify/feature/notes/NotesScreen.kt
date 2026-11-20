package com.rosyidputrar.taskify.feature.notes

import com.rosyidputrar.taskify.core.designsystem.component.SwipeToDelete
import androidx.compose.runtime.remember
import com.rosyidputrar.taskify.core.designsystem.component.rememberHaptics
import com.rosyidputrar.taskify.core.designsystem.component.TaskifyHaptics
import com.rosyidputrar.taskify.core.designsystem.component.ListSkeleton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rosyidputrar.taskify.core.designsystem.component.EmptyState
import com.rosyidputrar.taskify.core.designsystem.component.ModuleHeader
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.notes.Note
import com.rosyidputrar.taskify.core.domain.notes.NoteScope
import com.rosyidputrar.taskify.feature.notes.component.NoteCard
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun NotesScreen(
    contentPadding: PaddingValues,
    onOpenNote: (String) -> Unit,
    onNewNote: () -> Unit,
    onBack: (() -> Unit)? = null,
    backButton: @Composable (() -> Unit) -> Unit = {},
    viewModel: NotesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    NotesContent(state, viewModel, contentPadding, onOpenNote, onNewNote, onBack, backButton)
}

/** Overview catatan: grid staggered (tinggi kartu mengikuti isi), disematkan di atas, lalu per bulan. */
@Composable
fun NotesContent(
    state: NotesUiState,
    actions: NotesActions,
    contentPadding: PaddingValues,
    onOpenNote: (String) -> Unit,
    onNewNote: () -> Unit,
    onBack: (() -> Unit)? = null,
    backButton: @Composable (() -> Unit) -> Unit = {},
) {
    val haptics = rememberHaptics()
    @Suppress("NAME_SHADOWING")
    val actions = remember(actions, haptics) { HapticNotesActions(actions, haptics) }
    val trash = state.filter.trash
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Notes)
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(160.dp),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding(),
        ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalItemSpacing = 10.dp,
        modifier = Modifier.fillMaxSize(),
    ) {
        full("header") {
            ModuleHeader(
                TaskifyModule.Notes,
                if (trash) "Sampah catatan" else "Catatan",
                subtitle = if (trash) "Catatan yang dihapus bisa dipulihkan" else state.subtitle(),
                navigation = onBack?.let { { backButton(it) } },
                modifier = Modifier.bleed(16.dp),
                actions = {
                    IconButton(onClick = actions::toggleTrash) {
                        Icon(if (trash) Icons.Rounded.Close else Icons.Outlined.DeleteOutline, contentDescription = if (trash) "Tutup Sampah" else "Sampah catatan")
                    }
                    if (!trash) {
                        FilledTonalIconButton(onClick = onNewNote, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = accent, contentColor = TaskifyTheme.modules.onAccent(TaskifyModule.Notes))) {
                            Icon(Icons.Rounded.Add, contentDescription = "Catatan baru")
                        }
                    }
                },
            )
        }
        full("search") {
            OutlinedTextField(
                value = state.filter.search,
                onValueChange = actions::setSearch,
                placeholder = { Text("Cari judul & isi catatan…") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = if (state.filter.search.isNotEmpty()) {
                    { IconButton(onClick = { actions.setSearch("") }) { Icon(Icons.Rounded.Close, contentDescription = "Hapus pencarian") } }
                } else null,
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            )
        }
        full("scope") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
                NoteScope.entries.forEachIndexed { i, scope ->
                    SegmentedButton(
                        selected = state.filter.scope == scope,
                        onClick = { actions.setScope(scope) },
                        shape = SegmentedButtonDefaults.itemShape(i, NoteScope.entries.size),
                        label = { Text(scope.label) },
                    )
                }
            }
        }
        if (!state.loaded) full("skeleton") { ListSkeleton(TaskifyModule.Notes, rows = 4, lines = 2) }
        if (state.loaded && state.isEmpty) {
            full("empty") {
                val filtered = state.filter.search.isNotEmpty() || state.filter.scope != NoteScope.All
                EmptyState(
                    TaskifyModule.Notes,
                    title = when { trash -> "Sampah kosong"; filtered -> "Tidak ada yang cocok"; else -> "Belum ada catatan" },
                    message = when {
                        trash -> "Catatan yang dihapus muncul di sini dan bisa dipulihkan."
                        filtered -> "Coba kata lain atau pilih jenis Semua."
                        else -> "Catatan bebas untuk ide, atau tempel ke tanggal agar muncul di Kalender."
                    },
                )
            }
        }
        if (state.pinned.isNotEmpty()) {
            heading("pinned", "Disematkan", state.pinned.size)
            noteItems(state.pinned, state, actions, onOpenNote, prefix = "p-")
        }
        state.groups.forEach { group ->
            heading(group.key, group.label, group.notes.size)
            noteItems(group.notes, state, actions, onOpenNote)
        }
    }
}

/** Lebarkan item melewati padding grid (header modul punya padding sendiri). */
private fun Modifier.bleed(horizontal: Dp) = layout { measurable, constraints ->
    val extra = horizontal.roundToPx() * 2
    val placeable = measurable.measure(constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra))
    layout(placeable.width - extra, placeable.height) { placeable.place(-horizontal.roundToPx(), 0) }
}

private fun LazyStaggeredGridScope.full(key: String, content: @Composable () -> Unit) {
    item(key = key, span = StaggeredGridItemSpan.FullLine) { content() }
}

private fun LazyStaggeredGridScope.heading(key: String, label: String, count: Int) {
    full("h-$key") {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), style = TaskifyTextStyles.indexLabel, color = TaskifyTheme.modules.accent(TaskifyModule.Notes), modifier = Modifier.semantics { heading() })
            Spacer(Modifier.weight(1f))
            Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun LazyStaggeredGridScope.noteItems(notes: List<Note>, state: NotesUiState, actions: NotesActions, onOpenNote: (String) -> Unit, prefix: String = "") {
    items(notes, key = { prefix + it.id }) { note ->
        // Geser ke kiri → Sampah (tidak berlaku di tampilan Sampah sendiri).
        SwipeToDelete(onDelete = { actions.remove(note) }, enabled = !state.filter.trash, label = "Pindahkan ke Sampah", modifier = Modifier.animateItem()) {
            NoteCard(
                note,
                state.today,
                onOpen = { onOpenNote(note.id) },
                onTogglePin = { if (!state.filter.trash) actions.togglePin(note) },
                onRestore = if (state.filter.trash) ({ actions.restore(note) }) else null,
            )
        }
    }
}

/** Getar halus per makna (lihat [TaskifyHaptics]) tanpa mengubah logika aksi. */
private class HapticNotesActions(private val a: NotesActions, private val h: TaskifyHaptics) : NotesActions by a {
    override fun setScope(scope: NoteScope) { h.tick(); a.setScope(scope) }
    override fun toggleTrash() { h.tick(); a.toggleTrash() }
    override fun togglePin(note: Note) { h.toggle(!note.pinned); a.togglePin(note) }
    override fun restore(note: Note) { h.success(); a.restore(note) }
    override fun remove(note: Note) { h.success(); a.remove(note) }
}
