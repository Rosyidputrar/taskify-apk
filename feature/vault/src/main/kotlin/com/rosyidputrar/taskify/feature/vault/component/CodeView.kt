package com.rosyidputrar.taskify.feature.vault.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.designsystem.theme.namedColor
import com.rosyidputrar.taskify.core.domain.vault.Highlighter
import com.rosyidputrar.taskify.core.domain.vault.TokenType
import com.rosyidputrar.taskify.core.domain.vault.fillTemplate
import com.rosyidputrar.taskify.core.domain.vault.templateVariables

/** Warna token: tinta kalem yang tetap terbaca di terang & gelap. */
@Composable
private fun tokenColors(): Map<TokenType, Color> = mapOf(
    TokenType.Keyword to namedColor("violet"),
    TokenType.String to namedColor("emerald"),
    TokenType.Comment to MaterialTheme.colorScheme.onSurfaceVariant,
    TokenType.Number to namedColor("orange"),
    TokenType.Tag to namedColor("sky"),
)

@Composable
fun highlighted(code: String, language: String): AnnotatedString {
    val colors = tokenColors()
    return remember(code, language, colors) {
        buildAnnotatedString {
            Highlighter.tokenize(code, language).forEach { t ->
                val c = colors[t.type]
                if (c == null) append(t.text) else withStyle(
                    SpanStyle(color = c, fontStyle = if (t.type == TokenType.Comment) FontStyle.Italic else null, fontWeight = if (t.type == TokenType.Keyword) FontWeight.SemiBold else null),
                ) { append(t.text) }
            }
        }
    }
}

/**
 * Blok kode berwarna dengan nomor baris; [maxLines] membatasi pratinjau di kartu. Baris panjang
 * digulir horizontal (tidak dibungkus) agar indentasi tetap terbaca.
 */
@Composable
fun CodeView(code: String, language: String, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, lineNumbers: Boolean = true) {
    val lines = code.lines()
    val shown = if (lines.size > maxLines) lines.take(maxLines).joinToString("\n") else code
    val count = minOf(lines.size, maxLines)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = 10.dp)) {
            if (lineNumbers) {
                Column(Modifier.padding(start = 10.dp, end = 8.dp)) {
                    Text((1..count).joinToString("\n"), style = TaskifyTextStyles.code, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
            Text(
                highlighted(shown, language),
                style = TaskifyTextStyles.code,
                softWrap = false,
                maxLines = maxLines,
                overflow = TextOverflow.Clip,
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(start = if (lineNumbers) 0.dp else 12.dp, end = 12.dp),
            )
        }
    }
}

/** Salin ke clipboard (ditandai sensitif bila isinya tampak rahasia — Android 13+ menyembunyikan pratinjaunya). */
fun copyToClipboard(context: Context, label: String, text: String, sensitive: Boolean = false) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    if (sensitive && Build.VERSION.SDK_INT >= 33) {
        clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    }
    clipboard.setPrimaryClip(clip)
}

/** Kode tampak berisi rahasia (token/kunci/password) → salinan ditandai sensitif. */
fun looksSecret(code: String) = Regex("(?i)(api[_-]?key|secret|token|password|passwd|private[_-]?key)\\s*[:=]").containsMatchIn(code)

/** Dialog isi variabel template {{nama}} sebelum menyalin. */
@Composable
fun TemplateDialog(code: String, onDismiss: () -> Unit, onCopy: (String) -> Unit) {
    val vars = remember(code) { templateVariables(code) }
    val values = remember(code) { mutableStateMapOf<String, String>() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Isi variabel template") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                vars.forEach { v ->
                    OutlinedTextField(values[v] ?: "", { values[v] = it }, label = { Text(v) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Text("Variabel yang dikosongkan tetap ditulis {{nama}}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = { onCopy(fillTemplate(code, values.filterValues { it.isNotEmpty() })) }) { Text("Salin") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
