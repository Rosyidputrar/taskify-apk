package com.rosyidputrar.taskify.feature.journal.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.designsystem.component.moodColor
import com.rosyidputrar.taskify.core.designsystem.component.moodIcon
import com.rosyidputrar.taskify.core.designsystem.theme.SerifFamily
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.journal.JournalEntry
import com.rosyidputrar.taskify.core.domain.journal.moodLabel
import com.rosyidputrar.taskify.core.domain.text.Markdown
import com.rosyidputrar.taskify.core.domain.time.Dates

/**
 * Kartu entri di rak: blok tanggal (angka serif + hari), strip warna mood di tepi, judul atau
 * kalimat pembuka, cuplikan, lalu mood · jumlah kata · sematan.
 */
@Composable
fun JournalCard(entry: JournalEntry, today: String, onOpen: () -> Unit, modifier: Modifier = Modifier, onRestore: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val excerpt = Markdown.plainExcerpt(entry.content, 180)
    val heading = entry.title ?: excerpt.substringBefore(". ").take(80).ifEmpty { "Tanpa judul" }
    val body = if (entry.title != null) excerpt else excerpt.removePrefix(heading).trimStart('.', ' ')
    val mood = moodLabel(entry.mood)
    val words = Markdown.wordCount(entry.content)
    val description = listOfNotNull(Dates.relativeDayLabel(entry.entryDate, today), heading, mood?.let { "mood $it" }, "$words kata", if (entry.pinned) "disematkan" else null).joinToString(", ")
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.padding(vertical = 12.dp).width(6.dp).fillMaxHeight().background(moodColor(entry.mood), RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)))
            Column(Modifier.width(64.dp).padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Dates.format(entry.entryDate, "d"), style = TaskifyTextStyles.numeric.copy(fontSize = TaskifyTextStyles.numeric.fontSize * 0.8f), color = colors.onSurface)
                Text(Dates.format(entry.entryDate, "EEE").uppercase(), style = TaskifyTextStyles.indexLabel, color = colors.onSurfaceVariant)
            }
            Column(Modifier.weight(1f).padding(top = 14.dp, bottom = 14.dp, end = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(heading, style = MaterialTheme.typography.titleMedium.copy(fontFamily = SerifFamily), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (body.isNotEmpty()) Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    moodIcon(entry.mood)?.let { Icon(it, null, tint = moodColor(entry.mood), modifier = Modifier.size(16.dp)) }
                    val meta = listOfNotNull(mood, "$words kata").joinToString(" · ")
                    Text(meta, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    if (entry.pinned) Icon(Icons.Rounded.PushPin, null, tint = TaskifyTheme.modules.accent(TaskifyModule.Journal), modifier = Modifier.size(14.dp))
                }
            }
            if (onRestore != null) {
                IconButton(onClick = onRestore, modifier = Modifier.align(Alignment.CenterVertically).clearAndSetSemantics {
                    contentDescription = "Pulihkan entri"
                    onClick { onRestore(); true }
                }) { Icon(Icons.Outlined.RestoreFromTrash, contentDescription = null) }
            }
        }
    }
}
