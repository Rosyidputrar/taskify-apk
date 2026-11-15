package com.rosyidputrar.taskify.core.ui.markdown

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.designsystem.theme.MonoFamily
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.domain.text.Markdown
import com.rosyidputrar.taskify.core.domain.text.MdBlock
import com.rosyidputrar.taskify.core.domain.text.MdSpan

/**
 * Pratinjau Markdown native (tanpa WebView, tanpa HTML). [onToggleChecklist] menerima nomor
 * baris asli (1-based) sehingga centang di pratinjau langsung mengubah teks sumber.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    onToggleChecklist: ((Int) -> Unit)? = null,
) {
    val blocks = remember(markdown) { Markdown.parseBlocks(markdown) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block -> Block(block, accent, style, onToggleChecklist) }
    }
}

@Composable
private fun Block(block: MdBlock, accent: Color, style: TextStyle, onToggle: ((Int) -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    when (block) {
        is MdBlock.Heading -> {
            val headingStyle = when (block.level) {
                1 -> MaterialTheme.typography.headlineSmall
                2 -> MaterialTheme.typography.titleLarge
                else -> MaterialTheme.typography.titleMedium
            }
            Text(
                inline(block.text, accent),
                style = headingStyle,
                modifier = Modifier.padding(top = if (block.level <= 2) 8.dp else 4.dp).semantics { heading() },
            )
        }
        is MdBlock.Paragraph -> Text(inline(block.text, accent), style = style)
        is MdBlock.ListItem -> ListRow(block, accent, style, onToggle)
        is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(accent.copy(alpha = 0.6f), RoundedCornerShape(2.dp)))
            Text(
                inline(block.text, accent),
                style = style.copy(fontStyle = FontStyle.Italic, color = colors.onSurfaceVariant),
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        is MdBlock.Code -> Surface(color = colors.surfaceContainerHigh, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
            Text(
                block.code,
                style = TaskifyTextStyles.code,
                softWrap = false,
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
            )
        }
        is MdBlock.Table -> Table(block, accent, style)
        is MdBlock.Rule -> HorizontalDivider(Modifier.padding(vertical = 6.dp), color = colors.outlineVariant)
    }
}

@Composable
private fun ListRow(item: MdBlock.ListItem, accent: Color, style: TextStyle, onToggle: ((Int) -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    val checked = item.checked
    val toggle = if (checked != null && onToggle != null) Modifier.clickable(role = Role.Checkbox, onClickLabel = if (checked) "Batalkan centang" else "Centang") { onToggle(item.line) } else Modifier
    Row(
        Modifier.fillMaxWidth().then(toggle).padding(start = (item.depth * 18).dp).semantics {
            if (checked != null) stateDescription = if (checked) "Selesai" else "Belum selesai"
        },
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.width(22.dp).padding(top = 3.dp), contentAlignment = Alignment.TopCenter) {
            when {
                checked != null -> Box(
                    Modifier.size(18.dp)
                        .background(if (checked) accent else Color.Transparent, RoundedCornerShape(5.dp))
                        .border(BorderStroke(1.5.dp, if (checked) accent else colors.outline), RoundedCornerShape(5.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (checked) Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.surface, modifier = Modifier.size(14.dp))
                }
                item.number != null -> Text("${item.number}.", style = style.copy(color = accent, fontWeight = FontWeight.SemiBold))
                else -> Box(Modifier.padding(top = 7.dp).size(6.dp).background(accent, RoundedCornerShape(3.dp)))
            }
        }
        Text(
            inline(item.text, accent),
            style = if (checked == true) style.copy(color = colors.onSurfaceVariant, textDecoration = TextDecoration.LineThrough) else style,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Table(table: MdBlock.Table, accent: Color, style: TextStyle) {
    val line = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxWidth().border(1.dp, line, MaterialTheme.shapes.small)) {
        (listOf(table.header) + table.rows).forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = line)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                table.header.indices.forEach { col ->
                    Text(
                        inline(row.getOrElse(col) { "" }, accent),
                        style = if (index == 0) style.copy(fontWeight = FontWeight.SemiBold) else style,
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

/** Potongan inline → AnnotatedString; tautan dibuka lewat LocalUriHandler (browser). */
@Composable
private fun inline(text: String, accent: Color): AnnotatedString {
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest
    return remember(text, accent, codeBg) { buildInline(Markdown.parseInline(text), accent, codeBg) }
}

internal fun buildInline(spans: List<MdSpan>, accent: Color, codeBackground: Color): AnnotatedString = buildAnnotatedString {
    spans.forEach { span ->
        val s = span.style
        val spanStyle = SpanStyle(
            fontWeight = if (s.bold) FontWeight.Bold else null,
            fontStyle = if (s.italic) FontStyle.Italic else null,
            textDecoration = if (s.strike) TextDecoration.LineThrough else null,
            fontFamily = if (s.code) MonoFamily else null,
            background = if (s.code) codeBackground else Color.Unspecified,
        )
        val link = s.link
        if (link != null && (link.startsWith("http://") || link.startsWith("https://") || link.startsWith("mailto:"))) {
            withLink(LinkAnnotation.Url(link, TextLinkStyles(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline)))) {
                withStyle(spanStyle) { append(span.text) }
            }
        } else {
            withStyle(spanStyle) { append(span.text) }
        }
    }
}
