package com.rosyidputrar.taskify.feature.tasks.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.designsystem.theme.namedColor
import com.rosyidputrar.taskify.core.domain.tasks.parseQuickAdd
import com.rosyidputrar.taskify.core.domain.tasks.priorityLabel
import com.rosyidputrar.taskify.core.domain.time.Dates

/**
 * Bar catat cepat dengan PRATINJAU langsung: token yang dikenali ("besok", "jam 9", "#kerja",
 * "!3") muncul sebagai chip di bawah kotak saat diketik, sehingga hasilnya terlihat sebelum disimpan.
 */
@Composable
fun QuickAddBar(today: String, onSubmit: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "besok jam 9 rapat #kerja !2") {
    var text by rememberSaveable { mutableStateOf("") }
    val parsed = remember(text, today) { if (text.isBlank()) null else parseQuickAdd(text, today) }
    val submit = {
        if (text.isNotBlank()) {
            onSubmit(text)
            text = ""
        }
    }
    Column(modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(placeholder) },
            leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            trailingIcon = {
                if (text.isNotBlank()) {
                    IconButton(onClick = submit) { Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Tambah tugas") }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        AnimatedVisibility(parsed != null && (parsed.dueDate != null || parsed.priority != null || parsed.tags.isNotEmpty())) {
            val p = parsed ?: return@AnimatedVisibility
            FlowRow(
                Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                p.dueDate?.let { PreviewChip(Icons.Outlined.CalendarToday, Dates.relativeDayLabel(it, today)) }
                p.dueTime?.let { PreviewChip(Icons.Outlined.Schedule, it) }
                p.priority?.let { PreviewChip(Icons.Outlined.Flag, "Prioritas ${priorityLabel(it).lowercase()}") }
                p.tags.forEach { tag ->
                    AssistChip(
                        onClick = {},
                        label = { Text("#$tag") },
                        colors = AssistChipDefaults.assistChipColors(labelColor = namedColor(null)),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewChip(icon: ImageVector, label: String) {
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Tasks)
    AssistChip(
        onClick = {},
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
        colors = AssistChipDefaults.assistChipColors(leadingIconContentColor = accent, labelColor = accent),
    )
}
