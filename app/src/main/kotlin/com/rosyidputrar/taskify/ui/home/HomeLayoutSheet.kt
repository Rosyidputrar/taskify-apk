package com.rosyidputrar.taskify.ui.home

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
fun HomeLayoutSheet(layout: HomeLayout, onChange: (HomeLayout) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        HomeLayoutEditor(layout, onChange, onDone = onDismiss)
    }
}

/** Isi lembar Susun Beranda (tanpa sheet — bisa diuji). */
@Composable
fun HomeLayoutEditor(layout: HomeLayout, onChange: (HomeLayout) -> Unit, onDone: () -> Unit) {
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("BERANDA", style = TaskifyTextStyles.indexLabel, color = MaterialTheme.colorScheme.primary)
            Text("Susun Beranda", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(
                "Urutkan bagian dan pilih ringkasan yang penting buatmu. Tersimpan di perangkat ini.",
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
