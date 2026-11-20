package com.rosyidputrar.taskify.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.designsystem.theme.namedColor
import com.rosyidputrar.taskify.core.domain.tags.normalizeTagName
import com.rosyidputrar.taskify.core.store.tasks.TagRef

/** Tag picker: chip tag terpasang (hapus dengan ✕), kotak tambah, dan saran dari tag yang ada. [enabled] = false (mode Baca) → chip tampil tanpa ✕, kotak tambah & saran disembunyikan. */
@Composable
fun TagPicker(selected: List<TagRef>, all: List<TagRef>, onAdd: (String) -> Unit, onRemove: (TagRef) -> Unit, enabled: Boolean = true) {
    if (!enabled) {
        // Mode Baca: tag tampil sebagai label berwarna biasa — BUKAN chip yang tampak mati/abu-abu.
        if (selected.isNotEmpty()) TagLabels(selected)
        return
    }
    var input by rememberSaveable { mutableStateOf("") }
    val query = normalizeTagName(input)
    val chosen = selected.map { it.id }.toSet()
    val suggestions = all.filter { it.id !in chosen && (query.isEmpty() || it.name.contains(query)) }.take(8)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (selected.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                selected.forEach { tag ->
                    InputChip(
                        selected = true,
                        onClick = { onRemove(tag) },
                        enabled = enabled,
                        label = { Text("#${tag.name}") },
                        trailingIcon = if (enabled) ({ Icon(Icons.Rounded.Close, contentDescription = "Lepas tag ${tag.name}", modifier = Modifier.size(16.dp)) }) else null,
                        colors = InputChipDefaults.inputChipColors(selectedLabelColor = namedColor(tag.color)),
                    )
                }
            }
        }
        if (enabled) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Tambah tag…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (query.isNotEmpty()) onAdd(query)
                    input = ""
                }),
                modifier = Modifier.fillMaxWidth(),
            )
            if (suggestions.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestions.forEach { tag ->
                        SuggestionChip(onClick = { onAdd(tag.name); input = "" }, label = { Text("#${tag.name}", color = namedColor(tag.color)) })
                    }
                }
            } else if (query.isNotEmpty()) {
                Text("Tekan selesai untuk membuat #$query", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Daftar tag hanya-baca: label berwarna sesuai tag, tidak bisa diketuk. */
@Composable
fun TagLabels(tags: List<TagRef>, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.forEach { tag ->
            val color = namedColor(tag.color)
            Surface(color = color.copy(alpha = 0.14f), shape = MaterialTheme.shapes.small) {
                Text("#${tag.name}", style = MaterialTheme.typography.labelLarge, color = color, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
    }
}
