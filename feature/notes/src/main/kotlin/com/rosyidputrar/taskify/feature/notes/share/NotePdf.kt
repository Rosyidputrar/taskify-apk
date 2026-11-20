package com.rosyidputrar.taskify.feature.notes.share

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import com.rosyidputrar.taskify.core.domain.text.Markdown
import com.rosyidputrar.taskify.core.domain.text.MdBlock
import java.io.OutputStream

/**
 * Catatan → PDF A4 (tanpa pustaka tambahan): blok Markdown yang sama dengan tampilan Baca,
 * digambar dengan StaticLayout dan dipecah per baris antar halaman. Selalu bertema terang
 * (siap cetak), nomor halaman di kaki.
 */
object NotePdf {
    private const val PAGE_W = 595 // A4 dalam poin (1/72")
    private const val PAGE_H = 842
    private const val MARGIN = 56f
    private const val FOOTER = 28f

    private val INK = Color.rgb(0x1D, 0x1B, 0x16)
    private val MUTED = Color.rgb(0x6B, 0x66, 0x5C)
    private val ACCENT = Color.rgb(0x1D, 0x58, 0x67)
    private val LINE = Color.rgb(0xE3, 0xDD, 0xD0)
    private val CODE_BG = Color.rgb(0xF2, 0xEE, 0xE6)

    const val WIDTH = PAGE_W
    const val HEIGHT = PAGE_H

    /** Tempat halaman digambar — PdfDocument di aplikasi, Bitmap di tes. */
    interface Pages {
        fun start(number: Int): Canvas
        fun finish(number: Int)
    }

    fun write(out: OutputStream, title: String?, content: String, subtitle: String?) {
        val doc = PdfDocument()
        try {
            var page: PdfDocument.Page? = null
            render(title, content, subtitle, object : Pages {
                override fun start(number: Int) = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, number).create()).also { page = it }.canvas
                override fun finish(number: Int) { page?.let(doc::finishPage); page = null }
            })
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    /** Gambar catatan ke [pages]; mengembalikan jumlah halaman. */
    fun render(title: String?, content: String, subtitle: String?, pages: Pages): Int = Writer(pages).run {
        title?.trim()?.takeIf { it.isNotEmpty() }?.let { text(it, paint(24f, serif = true, bold = true), gapAfter = 6f) }
        subtitle?.let { text(it, paint(10f, color = MUTED), gapAfter = 14f) }
        Markdown.parseBlocks(content).forEach(::block)
        finish()
    }

    private fun paint(size: Float, serif: Boolean = false, bold: Boolean = false, mono: Boolean = false, color: Int = INK) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(
            when { mono -> Typeface.MONOSPACE; serif -> Typeface.SERIF; else -> Typeface.SANS_SERIF },
            if (bold) Typeface.BOLD else Typeface.NORMAL,
        )
    }

    private class Writer(private val pages: Pages) {
        private var pageNo = 0
        private var page: Canvas? = null
        private var y = MARGIN
        private val width = PAGE_W - 2 * MARGIN
        private val bottom = PAGE_H - MARGIN - FOOTER
        private val body = paint(11f)

        private val canvas: Canvas get() = page ?: newPage()

        private fun newPage(): Canvas {
            page?.let { finishPage(it) }
            pageNo++
            return pages.start(pageNo).also { page = it; y = MARGIN }
        }

        private fun finishPage(c: Canvas) {
            val foot = paint(8.5f, color = MUTED)
            val label = "$pageNo"
            c.drawText(label, PAGE_W - MARGIN - foot.measureText(label), PAGE_H - MARGIN + 6f, foot)
            c.drawText("Taskify", MARGIN, PAGE_H - MARGIN + 6f, foot)
            pages.finish(pageNo)
        }

        fun finish(): Int {
            if (page == null) newPage()
            page?.let { finishPage(it) }
            page = null
            return pageNo
        }

        fun text(s: CharSequence, p: TextPaint, gapAfter: Float = 8f, indent: Float = 0f, spacing: Float = 1.35f, decorate: ((Canvas, Float, Float) -> Unit)? = null) {
            val layout = StaticLayout.Builder.obtain(s, 0, s.length, p, (width - indent).toInt().coerceAtLeast(40))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, spacing)
                .setIncludePad(false)
                .build()
            place(layout, MARGIN + indent, decorate)
            y += gapAfter
        }

        /** Gambar [layout] mulai [y]; baris yang tidak muat pindah ke halaman berikutnya. */
        private fun place(layout: StaticLayout, x: Float, decorate: ((Canvas, Float, Float) -> Unit)?) {
            var line = 0
            if (page == null) newPage()
            while (line < layout.lineCount) {
                val start = layout.getLineTop(line)
                var end = line
                while (end < layout.lineCount && layout.getLineBottom(end) - start <= bottom - y) end++
                if (end == line) {
                    if (y > MARGIN) { newPage(); continue }
                    end = line + 1 // satu baris lebih tinggi dari halaman: paksa
                }
                val h = (layout.getLineBottom(end - 1) - start).toFloat()
                val c = canvas
                decorate?.invoke(c, y, y + h)
                c.save()
                c.clipRect(x - 1f, y, PAGE_W.toFloat(), y + h)
                c.translate(x, y - start)
                layout.draw(c)
                c.restore()
                y += h
                line = end
                if (line < layout.lineCount) newPage()
            }
        }

        private fun space(h: Float) { if (y + h < bottom) y += h }

        fun block(b: MdBlock) {
            when (b) {
                is MdBlock.Heading -> {
                    space(6f)
                    val size = when (b.level) { 1 -> 18f; 2 -> 15.5f; 3 -> 13.5f; else -> 12f }
                    text(inline(b.text), paint(size, serif = true, bold = true), gapAfter = 6f, spacing = 1.2f)
                }
                is MdBlock.Paragraph -> text(inline(b.text), body)
                is MdBlock.ListItem -> {
                    val marker = when {
                        b.checked == true -> "☑  "
                        b.checked == false -> "☐  "
                        b.number != null -> "${b.number}.  "
                        else -> "•  "
                    }
                    val s = SpannableStringBuilder(marker).append(inline(b.text))
                    if (b.checked == true) {
                        s.setSpan(StrikethroughSpan(), marker.length, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        s.setSpan(ForegroundColorSpan(MUTED), marker.length, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    if (b.checked != null) s.setSpan(ForegroundColorSpan(ACCENT), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    // Baris lanjutan rata dengan teks, bukan dengan penanda.
                    s.setSpan(LeadingMarginSpan.Standard(0, body.measureText(marker).toInt()), 0, s.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
                    text(s, body, gapAfter = 4f, indent = 16f * b.depth)
                }
                is MdBlock.Quote -> {
                    val bar = Paint().apply { color = LINE }
                    text(inline(b.text), paint(11f, color = MUTED), indent = 14f) { c, top, bot -> c.drawRect(MARGIN, top, MARGIN + 3f, bot, bar) }
                }
                is MdBlock.Code -> {
                    val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CODE_BG }
                    space(2f)
                    text(b.code.trimEnd(), paint(9.5f, mono = true), gapAfter = 10f, indent = 10f, spacing = 1.25f) { c, top, bot ->
                        c.drawRoundRect(RectF(MARGIN, top - 6f, MARGIN + width, bot + 6f), 6f, 6f, bg)
                    }
                    space(4f)
                }
                is MdBlock.Table -> {
                    val rows = listOf(b.header) + b.rows
                    val s = SpannableStringBuilder()
                    rows.forEachIndexed { i, row ->
                        val start = s.length
                        s.append(row.joinToString("   │   ") { plain(it) })
                        if (i == 0) s.setSpan(StyleSpan(Typeface.BOLD), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        if (i < rows.lastIndex) s.append('\n')
                    }
                    val rule = Paint().apply { color = LINE; strokeWidth = 0.8f }
                    text(s, paint(10f), spacing = 1.5f) { c, top, _ -> c.drawLine(MARGIN, top - 3f, MARGIN + width, top - 3f, rule) }
                }
                is MdBlock.Rule -> {
                    if (page == null) newPage()
                    space(6f)
                    canvas.drawLine(MARGIN, y, MARGIN + width, y, Paint().apply { color = LINE; strokeWidth = 1f })
                    space(12f)
                }
            }
        }

        private fun plain(text: String) = Markdown.parseInline(text).joinToString("") { it.text }

        /** Inline Markdown → teks bergaya (tebal, miring, coret, kode, tautan). */
        private fun inline(text: String): CharSequence {
            val s = SpannableStringBuilder()
            Markdown.parseInline(text).forEach { span ->
                val start = s.length
                s.append(span.text)
                val end = s.length
                if (start == end) return@forEach
                fun set(o: Any) = s.setSpan(o, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                val st = span.style
                when {
                    st.bold && st.italic -> set(StyleSpan(Typeface.BOLD_ITALIC))
                    st.bold -> set(StyleSpan(Typeface.BOLD))
                    st.italic -> set(StyleSpan(Typeface.ITALIC))
                }
                if (st.strike) set(StrikethroughSpan())
                if (st.code) { set(TypefaceSpan("monospace")); set(BackgroundColorSpan(CODE_BG)) }
                st.link?.let { url ->
                    set(ForegroundColorSpan(ACCENT)); set(UnderlineSpan())
                    if (url != span.text) {
                        val a = s.length
                        s.append(" ($url)")
                        s.setSpan(ForegroundColorSpan(MUTED), a, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            }
            return s
        }
    }
}
