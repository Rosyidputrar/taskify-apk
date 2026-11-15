package com.rosyidputrar.taskify.core.ui.markdown

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.FormatStrikethrough
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rosyidputrar.taskify.core.domain.editor.EditState
import com.rosyidputrar.taskify.core.domain.editor.MarkdownAction
import com.rosyidputrar.taskify.core.domain.editor.MarkdownEdit

fun TextFieldValue.toEdit() = EditState(text, selection.start, selection.end)
fun EditState.toField() = TextFieldValue(text, TextRange(start, end))

fun TextFieldValue.applyMarkdown(action: MarkdownAction): TextFieldValue = MarkdownEdit.apply(toEdit(), action).toField()

/** Perubahan dari keyboard: Enter di daftar melanjutkan/mengakhiri daftar seperti editor webapp. */
fun TextFieldValue.withListContinuation(next: TextFieldValue): TextFieldValue =
    MarkdownEdit.continueList(toEdit(), next.toEdit())?.toField() ?: next

private val LINE_HEIGHT = 28.sp

/**
 * Kolom tulis "kertas bergaris": garis tipis setiap baris dan garis margin berwarna modul di kiri,
 * seperti halaman buku catatan. Ctrl/⌘ + B / I / K untuk tebal, miring, tautan (keyboard fisik).
 */
@Composable
fun RuledMarkdownField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    accent: Color,
    modifier: Modifier = Modifier,
    label: String = "Isi",
    minLines: Int = 10,
) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.bodyLarge.copy(
        color = colors.onSurface,
        lineHeight = LINE_HEIGHT,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
    val rule = colors.outlineVariant.copy(alpha = 0.55f)
    val margin = accent.copy(alpha = 0.45f)
    BasicTextField(
        value = value,
        onValueChange = { onValueChange(value.withListContinuation(it)) },
        textStyle = style,
        cursorBrush = SolidColor(accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = label }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || !(event.isCtrlPressed || event.isMetaPressed)) return@onPreviewKeyEvent false
                val action = when (event.key) {
                    Key.B -> MarkdownAction.Bold
                    Key.I -> MarkdownAction.Italic
                    Key.K -> MarkdownAction.Link
                    else -> null
                } ?: return@onPreviewKeyEvent false
                onValueChange(value.applyMarkdown(action))
                true
            },
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = (LINE_HEIGHT.value * minLines).dp)
                    .drawBehind {
                        val step = LINE_HEIGHT.toPx()
                        var y = step
                        while (y <= size.height + 0.5f) {
                            drawLine(rule, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                            y += step
                        }
                        val x = 10.dp.toPx()
                        drawLine(margin, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5.dp.toPx())
                    }
                    .padding(start = 22.dp, end = 4.dp),
            ) {
                if (value.text.isEmpty()) Text(placeholder, style = style.copy(color = colors.onSurfaceVariant.copy(alpha = 0.75f)))
                inner()
            }
        },
    )
}

private data class Tool(val label: String, val icon: ImageVector, val action: MarkdownAction)

private val TOOLS = listOf(
    Tool("Tebal", Icons.Rounded.FormatBold, MarkdownAction.Bold),
    Tool("Miring", Icons.Rounded.FormatItalic, MarkdownAction.Italic),
    Tool("Coret", Icons.Rounded.FormatStrikethrough, MarkdownAction.Strike),
    Tool("Judul", Icons.Rounded.Title, MarkdownAction.Heading),
    Tool("Checklist", Icons.Rounded.Checklist, MarkdownAction.Checklist),
    Tool("Daftar", Icons.AutoMirrored.Rounded.FormatListBulleted, MarkdownAction.Bullet),
    Tool("Daftar bernomor", Icons.Rounded.FormatListNumbered, MarkdownAction.Numbered),
    Tool("Kutipan", Icons.Rounded.FormatQuote, MarkdownAction.Quote),
    Tool("Kode", Icons.Rounded.Code, MarkdownAction.Code),
    Tool("Tautan", Icons.Rounded.Link, MarkdownAction.Link),
)

/** Bilah format di atas keyboard. */
@Composable
fun FormatToolbar(onAction: (MarkdownAction) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
        TOOLS.forEach { tool ->
            IconButton(onClick = { onAction(tool.action) }) {
                Icon(tool.icon, contentDescription = tool.label)
            }
        }
    }
}

internal val PreviewStyle: TextStyle @Composable get() = MaterialTheme.typography.bodyLarge.copy(lineHeight = LINE_HEIGHT)
