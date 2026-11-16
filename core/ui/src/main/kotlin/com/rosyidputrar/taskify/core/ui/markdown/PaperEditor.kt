package com.rosyidputrar.taskify.core.ui.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.designsystem.component.IndexTab
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.editor.SaveState
import com.rosyidputrar.taskify.core.domain.text.Markdown
import com.rosyidputrar.taskify.core.ui.SaveIndicator

/** Bilah atas editor: kembali · [leading] (mis. chip tanggal) · status simpan · [actions]. */
@Composable
fun EditorTopBar(
    onBack: () -> Unit,
    saveState: SaveState,
    onRetry: () -> Unit,
    leading: @Composable RowScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali") }
        leading()
        Spacer(Modifier.weight(1f))
        SaveIndicator(saveState, onRetry)
        actions()
    }
}

/** Chip tanggal di bilah atas editor. */
@Composable
fun DateChip(label: String, onClick: () -> Unit, module: TaskifyModule, contentDescription: String = "Ubah tanggal, $label") {
    val accent = TaskifyTheme.modules.accent(module)
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Outlined.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp), tint = accent) },
        colors = AssistChipDefaults.assistChipColors(containerColor = TaskifyTheme.modules.tint(module, MaterialTheme.colorScheme.surface)),
        border = null,
        modifier = Modifier.semantics { this.contentDescription = contentDescription },
    )
}

/**
 * Editor "lembar kertas" bersama Jurnal & Catatan: judul serif, kolom tulis bergaris dengan
 * margin berwarna modul, mode Tulis/Baca (pratinjau dengan checklist yang bisa dicentang),
 * jumlah kata, dan bilah format yang menempel di atas keyboard.
 */
@Composable
fun PaperEditor(
    module: TaskifyModule,
    title: String,
    onTitleChange: (String) -> Unit,
    body: TextFieldValue,
    onBodyChange: (TextFieldValue) -> Unit,
    bodyPlaceholder: String,
    preview: Boolean,
    onPreviewChange: (Boolean) -> Unit,
    topBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    titlePlaceholder: String = "Judul (opsional)",
    titleMaxLength: Int = 200,
    banner: @Composable ColumnScope.() -> Unit = {},
    beforeBody: @Composable ColumnScope.() -> Unit = {},
    afterBody: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val accent = TaskifyTheme.modules.accent(module)
    Column(modifier.fillMaxSize().background(colors.background).statusBarsPadding()) {
        topBar()
        Column(
            Modifier.weight(1f).fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 760.dp)
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            banner()
            IndexTab(module)
            val titleStyle = MaterialTheme.typography.headlineMedium.copy(color = colors.onSurface)
            BasicTextField(
                value = title,
                onValueChange = { if (it.length <= titleMaxLength) onTitleChange(it) },
                textStyle = titleStyle,
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                singleLine = false,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Judul" },
                decorationBox = { inner ->
                    if (title.isEmpty()) Text(titlePlaceholder, style = titleStyle.copy(color = colors.onSurfaceVariant.copy(alpha = 0.6f)))
                    inner()
                },
            )
            beforeBody()
            Row(verticalAlignment = Alignment.CenterVertically) {
                SingleChoiceSegmentedButtonRow {
                    listOf("Tulis" to false, "Baca" to true).forEachIndexed { i, (label, value) ->
                        SegmentedButton(
                            selected = preview == value,
                            onClick = { onPreviewChange(value) },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                            icon = {},
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("${Markdown.wordCount(body.text)} kata", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
            if (preview) {
                if (body.text.isBlank()) {
                    Text("Belum ada isi.", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                } else {
                    MarkdownText(
                        body.text,
                        accent = accent,
                        style = PreviewStyle,
                        onToggleChecklist = { line ->
                            val text = Markdown.toggleChecklistLine(body.text, line)
                            onBodyChange(TextFieldValue(text, TextRange(body.selection.start.coerceAtMost(text.length))))
                        },
                    )
                }
            } else {
                RuledMarkdownField(body, onBodyChange, bodyPlaceholder, accent, label = "Isi")
            }
            afterBody()
            Spacer(Modifier.height(24.dp))
        }
        if (!preview) {
            Surface(color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth().imePadding()) {
                Column(Modifier.navigationBarsPadding()) {
                    HorizontalDivider(color = colors.outlineVariant)
                    FormatToolbar(onAction = { onBodyChange(body.applyMarkdown(it)) })
                }
            }
        } else {
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}
