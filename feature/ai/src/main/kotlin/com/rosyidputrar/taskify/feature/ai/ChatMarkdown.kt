package com.rosyidputrar.taskify.feature.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.LocalMarkdownA11yLabels
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.elements.MarkdownCheckBox
import com.mikepenz.markdown.model.markdownDimens
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.MarkdownA11yLabels
import com.mikepenz.markdown.model.rememberMarkdownState
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.SyntaxThemes
import kotlinx.coroutines.delay
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.conflate

/** Jeda minimum antar pembaruan teks streaming di layar (bagian baru muncul per kalimat/baris). */
private const val REVEAL_EVERY_MS = 280L
private const val FADE_MS = 320

private val IndonesianLabels = MarkdownA11yLabels().copy(
    blockquote = "Kutipan",
    codeBlock = "Blok kode",
    codeBlockWithLanguage = { "Blok kode, $it" },
    codeLanguage = { "Bahasa: $it" },
    codeFallbackLanguage = "KODE",
    copyCode = "Salin kode",
)

/**
 * Jawaban asisten dalam Markdown lengkap (GFM: judul, tebal/miring/coret, daftar, checklist,
 * kutipan, tabel, tautan, garis, kode inline & blok kode berwarna dengan tombol salin).
 *
 * Saat [streaming]: teks tidak muncul per huruf. Hanya kalimat/baris yang sudah lengkap yang
 * ditampilkan (paling cepat tiap [REVEAL_EVERY_MS]); blok baru muncul dengan fade + tumbuh halus,
 * kalimat baru di blok terakhir di-fade-in di atas teks lama yang tetap diam.
 */
@Composable
fun ChatMarkdown(text: String, streaming: Boolean, modifier: Modifier = Modifier) {
    val latest by rememberUpdatedState(text)
    var shown by remember { mutableStateOf(if (streaming) StreamReveal.committed(text, final = false) else text) }
    LaunchedEffect(streaming) {
        if (!streaming) { shown = latest; return@LaunchedEffect }
        // Bereaksi hanya saat teks berubah (tanpa polling); jeda setelah tiap pembaruan = batas laju,
        // nilai yang datang selama jeda digabung (conflate) jadi yang terbaru saja yang dipakai.
        snapshotFlow { latest }.conflate().collect { raw ->
            val next = StreamReveal.committed(raw, final = false)
            if (next != shown && next.length >= shown.length) {
                shown = next
                delay(REVEAL_EVERY_MS)
            }
        }
    }
    LaunchedEffect(text) { if (!streaming) shown = text }
    // Animasi hanya untuk jawaban yang sedang/baru saja mengalir — riwayat lama tampil langsung.
    val animate = remember { streaming }
    val blocks = remember(shown) { StreamReveal.blocks(shown) }
    val style = rememberChatMarkdownStyle()
    CompositionLocalProvider(LocalMarkdownA11yLabels provides IndonesianLabels) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            blocks.forEachIndexed { i, block ->
                key(i) {
                    val visible = remember { MutableTransitionState(!animate).apply { targetState = true } }
                    AnimatedVisibility(
                        visibleState = visible,
                        enter = fadeIn(tween(FADE_MS, easing = LinearOutSlowInEasing)) +
                            expandVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow), expandFrom = androidx.compose.ui.Alignment.Top),
                    ) {
                        FadeIn(block, animate = animate) { MarkdownBlock(it, style) }
                    }
                }
            }
        }
    }
}

/**
 * Ganti isi dengan halus: isi lama tetap tampil penuh di bawah, isi baru (yang memuat isi lama
 * sebagai awalan) di atasnya naik dari transparan — jadi hanya bagian baru yang terlihat memudar
 * masuk. Struktur komposisi selalu sama (tidak ada pemasangan ulang yang membuat kedip).
 */
@Composable
private fun FadeIn(text: String, animate: Boolean, content: @Composable (String) -> Unit) {
    var base by remember { mutableStateOf(text) }
    var top by remember { mutableStateOf(text) }
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(text) {
        if (text == top) return@LaunchedEffect
        if (!animate) { base = text; top = text; alpha.snapTo(1f); return@LaunchedEffect }
        base = top
        top = text
        alpha.snapTo(0f)
        alpha.animateTo(1f, tween(FADE_MS, easing = LinearOutSlowInEasing))
        base = top
    }
    Box {
        if (base != top) Box(Modifier.clearAndSetSemantics {}) { content(base) }
        Box(Modifier.graphicsLayer { this.alpha = alpha.value }) { content(top) }
    }
}

private class ChatMarkdownStyle(val highlights: Highlights.Builder)

@Composable
private fun rememberChatMarkdownStyle(): ChatMarkdownStyle {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return remember(dark) { ChatMarkdownStyle(Highlights.Builder().theme(SyntaxThemes.atom(darkMode = dark))) }
}

@Composable
private fun MarkdownBlock(text: String, style: ChatMarkdownStyle) {
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    // Parse sinkron & pertahankan hasil lama: tidak ada kedip kosong saat streaming / saat digulir ulang.
    val state = rememberMarkdownState(text, retainState = true, immediate = true)
    Markdown(
        markdownState = state,
        modifier = Modifier.fillMaxWidth(),
        colors = markdownColor(
            text = scheme.onSurface,
            codeBackground = scheme.surfaceContainerHigh,
            inlineCodeBackground = scheme.surfaceContainerHighest,
            dividerColor = scheme.outlineVariant,
            tableBackground = scheme.surfaceContainerLow,
        ),
        typography = markdownTypography(
            h1 = type.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            h2 = type.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            h3 = type.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            h4 = type.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            h5 = type.titleSmall,
            h6 = type.labelLarge,
            text = type.bodyLarge,
            paragraph = type.bodyLarge,
            ordered = type.bodyLarge,
            bullet = type.bodyLarge,
            list = type.bodyLarge,
            quote = type.bodyLarge.copy(color = scheme.onSurfaceVariant),
            code = type.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            inlineCode = type.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            table = type.bodyMedium,
            textLink = TextLinkStyles(SpanStyle(color = scheme.primary, fontWeight = FontWeight.Medium, textDecoration = TextDecoration.Underline)),
        ),
        // Sel lebih sempit dari bawaan (160dp): tabel 2 kolom muat di ponsel, yang lebih lebar bisa digeser.
        dimens = markdownDimens(tableCellWidth = 128.dp, tableCellPadding = 12.dp),
        components = markdownComponents(
            checkbox = { MarkdownCheckBox(it.content, it.node, it.typography.text) },
            codeFence = { MarkdownHighlightedCodeFence(it.content, it.node, it.typography.code, highlightsBuilder = style.highlights, showHeader = true, immediate = true) },
            codeBlock = { MarkdownHighlightedCodeBlock(it.content, it.node, it.typography.code, highlightsBuilder = style.highlights, showHeader = true, immediate = true) },
        ),
    )
}
