package com.rosyidputrar.taskify.feature.notes.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.NoteAlt
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.designsystem.theme.SerifFamily
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.notes.Note
import com.rosyidputrar.taskify.core.domain.notes.noteDateLabel
import com.rosyidputrar.taskify.core.domain.text.Markdown

/**
 * Kartu catatan untuk grid staggered: tinggi mengikuti isi (cuplikan sampai 8 baris), label
 * tanggal tempel atau "Bebas", progres checklist bila ada. Tahan lama = sematkan/lepas.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(note: Note, today: String, onOpen: () -> Unit, onTogglePin: () -> Unit, modifier: Modifier = Modifier, onRestore: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Notes)
    val excerpt = Markdown.plainExcerpt(note.content, 320)
    val title = note.title ?: excerpt.take(60).substringBefore(". ").ifEmpty { "Tanpa judul" }
    val body = if (note.title != null) excerpt else excerpt.removePrefix(title).trimStart('.', ' ')
    val (done, total) = Markdown.checklistProgress(note.content)
    val dateLabel = noteDateLabel(note.noteDate, today)
    val description = listOfNotNull(title, if (note.noteDate != null) "ditempel $dateLabel" else "catatan bebas", if (total > 0) "checklist $done dari $total" else null, if (note.pinned) "disematkan" else null).joinToString(", ")
    val shape = MaterialTheme.shapes.large
    Surface(
        color = if (note.pinned) TaskifyTheme.modules.tint(TaskifyModule.Notes, colors.surfaceContainerLow) else colors.surfaceContainerLow,
        shape = shape,
        modifier = modifier
            .clip(shape)
            .combinedClickable(onClickLabel = "Buka catatan", onLongClickLabel = if (note.pinned) "Lepas sematan" else "Sematkan", onClick = onOpen, onLongClick = onTogglePin)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                customActions = listOf(CustomAccessibilityAction(if (note.pinned) "Lepas sematan" else "Sematkan") { onTogglePin(); true })
            },
    ) {
        Column {
            // Tab kertas di atas kartu (penanda indeks versi horizontal).
            Box(Modifier.padding(start = 16.dp).width(36.dp).height(5.dp).background(accent, RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp)))
            Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(if (note.noteDate != null) Icons.Outlined.CalendarToday else Icons.Outlined.NoteAlt, null, tint = accent, modifier = Modifier.size(13.dp))
                    Text(dateLabel, style = MaterialTheme.typography.labelSmall, color = accent, modifier = Modifier.weight(1f))
                    if (note.pinned) Icon(Icons.Rounded.PushPin, null, tint = accent, modifier = Modifier.size(14.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontFamily = SerifFamily), maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (body.isNotEmpty()) Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 8, overflow = TextOverflow.Ellipsis)
                if (total > 0) {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.Checklist, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        LinearProgressIndicator(progress = { done.toFloat() / total }, color = accent, trackColor = colors.surfaceContainerHighest, modifier = Modifier.weight(1f).clip(RoundedCornerShape(2.dp)), drawStopIndicator = {})
                        Text("$done/$total", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    }
                }
                if (onRestore != null) {
                    IconButton(onClick = onRestore, modifier = Modifier.align(Alignment.End)) { Icon(Icons.Outlined.RestoreFromTrash, contentDescription = "Pulihkan catatan") }
                }
            }
        }
    }
}

