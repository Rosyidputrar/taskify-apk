package com.rosyidputrar.taskify.core.ui.share

import android.graphics.Bitmap
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
 * Dokumen yang dibagikan: judul, keterangan ([meta], mis. tanggal & mood), isi Markdown,
 * label modul ("CATATAN") dan warna aksennya (ARGB).
 */
data class ShareDoc(
    val title: String?,
    val meta: String?,
    val markdown: String,
    val label: String,
    val accent: Int = DocRender.TEAL,
)

/**
 * Dokumen → PDF A4 atau gambar PNG, tanpa pustaka tambahan. Blok Markdown sama dengan tampilan
 * Baca, digambar dengan StaticLayout. Selalu bertema terang (siap cetak & terbaca di chat).
 *
 * - PDF: dipecah per baris antar halaman, nomor halaman di kaki.
 * - Gambar: satu lembar setinggi isi (lebar 1080 px); isi yang terlalu panjang dipotong rapi
 *   dengan keterangan agar dibagikan sebagai PDF.
 */
object DocRender {
    const val PAGE_W = 595 // A4 dalam poin (1/72")
    const val PAGE_H = 842
    private const val MARGIN = 56f
    private const val FOOTER = 28f

    /** Batas tinggi gambar dalam poin (±6000 px pada lebar 1080) — menjaga memori tetap wajar. */
    const val IMAGE_MAX_PT = 3300f
    private const val IMAGE_MARGIN = 40f

    val TEAL = Color.rgb(0x1D, 0x58, 0x67)
    private val INK = Color.rgb(0x1D, 0x1B, 0x16)
    private val MUTED = Color.rgb(0x6B, 0x66, 0x5C)
    private val LINE = Color.rgb(0xE3, 0xDD, 0xD0)
    private val CODE_BG = Color.rgb(0xF2, 0xEE, 0xE6)
    private val PAPER = Color.rgb(0xFA, 0xF7, 0xF1)

    /** Tempat halaman digambar — PdfDocument di aplikasi, Bitmap di tes. */
    interface Pages {
        fun start(number: Int): Canvas
        fun finish(number: Int)
    }

    fun writePdf(out: OutputStream, doc: ShareDoc) {
        val pdf = PdfDocument()
        try {
            var page: PdfDocument.Page? = null
            renderPages(doc, object : Pages {
                override fun start(number: Int) = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, number).create()).also { page = it }.canvas
                override fun finish(number: Int) { page?.let(pdf::finishPage); page = null }
            })
            pdf.writeTo(out)
        } finally {
            pdf.close()
        }
    }

    /** Gambar dokumen ke halaman A4; mengembalikan jumlah halaman. */
    fun renderPages(doc: ShareDoc, pages: Pages): Int {
        val w = Writer(pages, pageHeight = PAGE_H.toFloat(), margin = MARGIN, footer = FOOTER, accent = doc.accent, maxPages = Int.MAX_VALUE) { c, n ->
            val foot = paint(8.5f, color = MUTED)
            c.drawText("$n", PAGE_W - MARGIN - foot.measureText("$n"), PAGE_H - MARGIN + 6f, foot)
            c.drawText("Taskify · ${doc.label}", MARGIN, PAGE_H - MARGIN + 6f, foot)
        }
        w.document(doc, withLabel = false)
        return w.finish()
    }

    /** Hasil gambar: [truncated] = isi terlalu panjang dan dipotong. */
    class Image(val bitmap: Bitmap, val truncated: Boolean)

    /** Satu lembar PNG selebar [widthPx]; tinggi mengikuti isi (maks [IMAGE_MAX_PT]). */
    fun image(doc: ShareDoc, widthPx: Int = 1080): Image {
        val scale = widthPx / PAGE_W.toFloat()
        // Lintasan 1: ukur tinggi isi (gambar ke kanvas 1×1, tidak ada yang tersimpan).
        val probe = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val measure = imageWriter(doc, Canvas(probe))
        measure.document(doc, withLabel = true)
        measure.finish()
        probe.recycle()
        val truncated = measure.truncated
        val contentBottom = measure.y + if (truncated) 26f else 0f
        val heightPt = contentBottom + IMAGE_MARGIN + 28f
        // Lintasan 2: gambar sungguhan dengan tinggi pas.
        val bitmap = try {
            Bitmap.createBitmap(widthPx, (heightPt * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            throw IllegalStateException("Dokumen terlalu panjang untuk dibagikan sebagai gambar — pakai PDF.")
        }
        val canvas = Canvas(bitmap)
        canvas.drawColor(PAPER)
        canvas.scale(scale, scale)
        // Strip aksen tipis di tepi atas, seperti kartu indeks.
        canvas.drawRect(0f, 0f, PAGE_W.toFloat(), 5f, Paint().apply { color = doc.accent })
        val w = imageWriter(doc, canvas)
        w.document(doc, withLabel = true)
        w.finish()
        val muted = paint(9f, color = MUTED)
        if (truncated) {
            canvas.drawText("… isi terpotong — bagikan sebagai PDF untuk versi lengkap", IMAGE_MARGIN, w.y + 16f, paint(9.5f, color = MUTED).apply { typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC) })
        }
        val footY = heightPt - IMAGE_MARGIN + 6f
        canvas.drawLine(IMAGE_MARGIN, footY - 16f, PAGE_W - IMAGE_MARGIN, footY - 16f, Paint().apply { color = LINE; strokeWidth = 0.8f })
        canvas.drawText("Taskify · ${doc.label}", IMAGE_MARGIN, footY, muted)
        return Image(bitmap, truncated)
    }

    private fun imageWriter(doc: ShareDoc, canvas: Canvas) = Writer(
        object : Pages {
            override fun start(number: Int) = canvas
            override fun finish(number: Int) = Unit
        },
        pageHeight = IMAGE_MAX_PT, margin = IMAGE_MARGIN, footer = 0f, accent = doc.accent, maxPages = 1,
    ) { _, _ -> }

    internal fun paint(size: Float, serif: Boolean = false, bold: Boolean = false, mono: Boolean = false, color: Int = INK) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(
            when { mono -> Typeface.MONOSPACE; serif -> Typeface.SERIF; else -> Typeface.SANS_SERIF },
            if (bold) Typeface.BOLD else Typeface.NORMAL,
        )
    }

    /**
     * Penulis berurutan dari atas ke bawah. Halaman baru dibuka saat baris tidak muat; bila sudah
     * mencapai [maxPages], penulisan berhenti dan [truncated] = true.
     */
    private class Writer(
        private val pages: Pages,
        private val pageHeight: Float,
        private val margin: Float,
        footer: Float,
        private val accent: Int,
        private val maxPages: Int,
        private val drawFooter: (Canvas, Int) -> Unit,
    ) {
        private var pageNo = 0
        private var page: Canvas? = null
        var y = margin
            private set
        var truncated = false
            private set
        private val width = PAGE_W - 2 * margin
        private val bottom = pageHeight - margin - footer
        private val body = paint(11f)

        private val canvas: Canvas? get() = page ?: newPage()

        private fun newPage(): Canvas? {
            if (pageNo >= maxPages) { truncated = true; return null }
            page?.let { finishPage(it) }
            pageNo++
            return pages.start(pageNo).also { page = it; y = margin }
        }

        private fun finishPage(c: Canvas) {
            drawFooter(c, pageNo)
            pages.finish(pageNo)
        }

        fun finish(): Int {
            if (page == null && !truncated) newPage()
            page?.let { finishPage(it) }
            page = null
            return pageNo
        }

        fun document(doc: ShareDoc, withLabel: Boolean) {
            if (withLabel) label(doc.label)
            doc.title?.trim()?.takeIf { it.isNotEmpty() }?.let { text(it, paint(24f, serif = true, bold = true), gapAfter = 6f, spacing = 1.15f) }
            doc.meta?.trim()?.takeIf { it.isNotEmpty() }?.let { text(it, paint(10f, color = MUTED), gapAfter = 14f) }
            for (b in Markdown.parseBlocks(doc.markdown)) {
                if (truncated) break
                block(b)
            }
        }

        /** Label modul berbentuk tab (mis. "CATATAN") dengan warna aksen. */
        private fun label(text: String) {
            val c = canvas ?: return
            val p = paint(8.5f, bold = true, color = Color.WHITE).apply { letterSpacing = 0.12f }
            val t = text.uppercase()
            val w = p.measureText(t) + 16f
            c.drawRoundRect(RectF(margin, y, margin + w, y + 17f), 3f, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
            c.drawText(t, margin + 8f, y + 12f, p)
            y += 17f + 14f
        }

        fun text(s: CharSequence, p: TextPaint, gapAfter: Float = 8f, indent: Float = 0f, spacing: Float = 1.35f, decorate: ((Canvas, Float, Float) -> Unit)? = null) {
            if (truncated) return
            val layout = StaticLayout.Builder.obtain(s, 0, s.length, p, (width - indent).toInt().coerceAtLeast(40))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, spacing)
                .setIncludePad(false)
                .build()
            place(layout, margin + indent, decorate)
            if (!truncated) y += gapAfter
        }

        /** Gambar [layout] mulai [y]; baris yang tidak muat pindah ke halaman berikutnya. */
        private fun place(layout: StaticLayout, x: Float, decorate: ((Canvas, Float, Float) -> Unit)?) {
            var line = 0
            if (page == null && newPage() == null) return
            while (line < layout.lineCount) {
                val start = layout.getLineTop(line)
                var end = line
                while (end < layout.lineCount && layout.getLineBottom(end) - start <= bottom - y) end++
                if (end == line) {
                    if (y > margin) { if (newPage() == null) return; continue }
                    end = line + 1 // satu baris lebih tinggi dari halaman: paksa
                }
                val h = (layout.getLineBottom(end - 1) - start).toFloat()
                val c = canvas ?: return
                decorate?.invoke(c, y, y + h)
                c.save()
                c.clipRect(x - 1f, y, PAGE_W.toFloat(), y + h)
                c.translate(x, y - start)
                layout.draw(c)
                c.restore()
                y += h
                line = end
                if (line < layout.lineCount && newPage() == null) return
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
                    if (b.checked != null) s.setSpan(ForegroundColorSpan(accent), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    // Baris lanjutan rata dengan teks, bukan dengan penanda.
                    s.setSpan(LeadingMarginSpan.Standard(0, body.measureText(marker).toInt()), 0, s.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
                    text(s, body, gapAfter = 4f, indent = 16f * b.depth)
                }
                is MdBlock.Quote -> {
                    val bar = Paint().apply { color = LINE }
                    text(inline(b.text), paint(11f, color = MUTED), indent = 14f) { c, top, bot -> c.drawRect(margin, top, margin + 3f, bot, bar) }
                }
                is MdBlock.Code -> {
                    val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CODE_BG }
                    space(2f)
                    text(b.code.trimEnd(), paint(9.5f, mono = true), gapAfter = 10f, indent = 10f, spacing = 1.25f) { c, top, bot ->
                        c.drawRoundRect(RectF(margin, top - 6f, margin + width, bot + 6f), 6f, 6f, bg)
                    }
                    space(4f)
                }
                is MdBlock.Table -> tableBlock(b.header, b.rows)
                is MdBlock.Rule -> {
                    val c = canvas ?: return
                    space(6f)
                    c.drawLine(margin, y, margin + width, y, Paint().apply { color = LINE; strokeWidth = 1f })
                    space(12f)
                }
            }
        }

        private fun plain(text: String) = Markdown.parseInline(text).joinToString("") { it.text }

        /**
         * Tabel sebagai grid: lebar kolom diukur dari isi terpanjang (diskalakan bila melebihi
         * lebar halaman), header berlatar, garis pemisah antar sel. Sel panjang dibungkus di
         * dalam kolomnya; baris yang tidak muat pindah ke halaman berikutnya.
         */
        private fun tableBlock(header: List<String>, rows: List<List<String>>) {
            if (truncated) return
            val cols = header.size
            if (cols == 0) return
            val bodyP = paint(10f)
            val headP = paint(10f, bold = true)
            val padX = 8f
            val padY = 5f
            fun plainW(text: String, p: TextPaint) = plain(text).split('\n').maxOfOrNull { p.measureText(it) } ?: 0f
            val raw = (0 until cols).map { c ->
                var w = plainW(header[c], headP)
                for (r in rows) w = maxOf(w, plainW(r.getOrElse(c) { "" }, bodyP))
                w + padX * 2
            }
            val total = raw.sum()
            val widths = if (total > width) raw.map { (it * width / total).coerceAtLeast(24f) } else raw
            val line = Paint().apply { color = LINE; strokeWidth = 0.8f }
            val headBg = Paint().apply { color = CODE_BG }
            space(4f)
            val allRows = listOf(header to true) + rows.map { it to false }
            for ((cells, isHeader) in allRows) {
                if (truncated) return
                val p = if (isHeader) headP else bodyP
                val texts = (0 until cols).map { c -> inline(cells.getOrElse(c) { "" }).ifEmpty { " " } }
                val layouts = texts.mapIndexed { c, t ->
                    StaticLayout.Builder.obtain(t, 0, t.length, p, (widths[c] - padX * 2).toInt().coerceAtLeast(24))
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.35f).setIncludePad(false).build()
                }
                val rowH = layouts.maxOf { it.height }.toFloat() + padY * 2
                if (y + rowH > bottom) {
                    if (newPage() == null) return
                }
                val c = canvas ?: return
                val top = y
                if (isHeader) c.drawRect(margin, top, margin + width, top + rowH, headBg)
                var x = margin
                layouts.forEachIndexed { i, l ->
                    c.save()
                    c.translate(x + padX, top + padY)
                    l.draw(c)
                    c.restore()
                    x += widths[i]
                }
                // Garis vertikal antar kolom + garis bawah baris (header: garis lebih tegas).
                var vx = margin
                for (i in 0 until cols) {
                    vx += widths[i]
                    if (i < cols - 1) c.drawLine(vx, top, vx, top + rowH, line)
                }
                c.drawLine(margin, top + rowH, margin + width, top + rowH, line.apply { strokeWidth = if (isHeader) 1.2f else 0.8f })
                y += rowH
            }
            y += 8f
        }

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
                    set(ForegroundColorSpan(accent)); set(UnderlineSpan())
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
