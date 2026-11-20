package com.rosyidputrar.taskify.ui.home

import com.rosyidputrar.taskify.core.domain.home.HomeShortcuts
import com.rosyidputrar.taskify.core.domain.home.HomeShortcut
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.designsystem.component.RuledLine
import com.rosyidputrar.taskify.core.designsystem.component.rememberHaptics
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.domain.home.HomeLayout

/** Lembar "Susun Beranda": perubahan langsung diterapkan (Beranda di belakangnya ikut berubah). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeLayoutSheet(
    layout: HomeLayout,
    onChange: (HomeLayout) -> Unit,
    onDismiss: () -> Unit,
    pinned: List<HomeShortcut> = emptyList(),
    shortcutActions: HomeShortcutActions = object : HomeShortcutActions {},
    onAddShortcut: (() -> Unit)? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        HomeLayoutEditor(layout, onChange, onDone = onDismiss, pinned = pinned, shortcutActions = shortcutActions, onAddShortcut = onAddShortcut)
    }
}

/** Isi lembar Susun Beranda (tanpa sheet — bisa diuji). */
@Composable
fun HomeLayoutEditor(
    layout: HomeLayout,
    onChange: (HomeLayout) -> Unit,
    onDone: () -> Unit,
    pinned: List<HomeShortcut> = emptyList(),
    shortcutActions: HomeShortcutActions = object : HomeShortcutActions {},
    onAddShortcut: (() -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("BERANDA", style = TaskifyTextStyles.indexLabel, color = MaterialTheme.colorScheme.primary)
            Text("Susun Beranda", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(
                "Urutkan bagian, pilih ringkasan, dan atur pintasan yang penting buatmu.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            GroupLabel("BAGIAN")
            layout.sections.forEachIndexed { i, s ->
                LayoutRow(
                    label = s.label,
                    visible = s !in layout.hiddenSections,
                    canUp = i > 0,
                    canDown = i < layout.sections.lastIndex,
                    onMove = { by -> haptics.tick(); onChange(layout.moveSection(s, by)) },
                    onToggle = { on -> haptics.toggle(on); onChange(layout.toggleSection(s)) },
                )
            }
            Spacer(Modifier.height(4.dp))
            RuledLine()
            Spacer(Modifier.height(4.dp))
            ShortcutsGroup(layout, onChange, pinned, shortcutActions, onAddShortcut)
            Spacer(Modifier.height(4.dp))
            RuledLine()
            Spacer(Modifier.height(4.dp))
            GroupLabel("UBIN RINGKASAN")
            layout.tiles.forEachIndexed { i, t ->
                LayoutRow(
                    label = t.label,
                    visible = t !in layout.hiddenTiles,
                    canUp = i > 0,
                    canDown = i < layout.tiles.lastIndex,
                    onMove = { by -> haptics.tick(); onChange(layout.moveTile(t, by)) },
                    onToggle = { on -> haptics.toggle(on); onChange(layout.toggleTile(t)) },
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { haptics.tap(); onChange(HomeLayout.Default) },
                enabled = !layout.isDefault,
            ) { Text("Kembalikan bawaan") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDone) { Text("Selesai") }
        }
    }
}

/**
 * Kelompok PINTASAN: saklar "Lanjutkan" otomatis, urutan sematan (naik/turun), lepas, dan
 * bersihkan sematan yang isinya sudah dihapus.
 */
@Composable
private fun ShortcutsGroup(
    layout: HomeLayout,
    onChange: (HomeLayout) -> Unit,
    pinned: List<HomeShortcut>,
    actions: HomeShortcutActions,
    onAdd: (() -> Unit)?,
) {
    val haptics = rememberHaptics()
    Row(verticalAlignment = Alignment.CenterVertically) {
        GroupLabel("PINTASAN · ${pinned.size}/${HomeShortcuts.MAX_PINNED}")
        Spacer(Modifier.weight(1f))
        if (onAdd != null) TextButton(onClick = onAdd, enabled = pinned.size < HomeShortcuts.MAX_PINNED) {
            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Tambah")
        }
    }
    Row(
        Modifier.fillMaxWidth()
            .toggleable(value = layout.showRecent, role = Role.Switch, onValueChange = { on -> haptics.toggle(on); onChange(layout.toggleRecent()) })
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Lanjutkan otomatis", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Tampilkan isi yang terakhir kamu buka atau ubah, setelah pintasan tersemat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = layout.showRecent, onCheckedChange = null)
    }
    if (pinned.isEmpty()) {
        Text(
            "Belum ada yang disematkan. Tekan lama kartu di Hari ini, atau pilih \"Sematkan ke Hari ini\" di menu catatan, jurnal, snippet, dan chat asisten.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }
    pinned.forEachIndexed { i, sc ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { haptics.tick(); actions.move(sc.ref, -1) }, enabled = i > 0) {
                Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Naikkan ${sc.title}")
            }
            IconButton(onClick = { haptics.tick(); actions.move(sc.ref, 1) }, enabled = i < pinned.lastIndex) {
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Turunkan ${sc.title}")
            }
            Icon(sc.ref.icon(), null, tint = TaskifyTheme.modules.accent(sc.ref.module()), modifier = Modifier.padding(start = 4.dp).size(20.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    sc.title.ifBlank { sc.ref.type.label }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (sc.available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (sc.available) sc.caption() else "Sudah dihapus atau di Sampah",
                    style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (sc.available) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
            IconButton(onClick = { haptics.tap(); actions.togglePin(sc) }) {
                Icon(Icons.Outlined.Close, contentDescription = "Lepas ${sc.title}")
            }
        }
    }
    if (pinned.any { !it.available }) {
        TextButton(onClick = { haptics.tap(); actions.removeUnavailable() }) { Text("Bersihkan yang sudah dihapus") }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(text, style = TaskifyTextStyles.indexLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun LayoutRow(label: String, visible: Boolean, canUp: Boolean, canDown: Boolean, onMove: (Int) -> Unit, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onMove(-1) }, enabled = canUp) {
            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Naikkan $label")
        }
        IconButton(onClick = { onMove(1) }, enabled = canDown) {
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Turunkan $label")
        }
        Row(
            Modifier.weight(1f).toggleable(value = visible, role = Role.Switch, onValueChange = onToggle).padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (visible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = visible, onCheckedChange = null)
        }
    }
}
