package com.rosyidputrar.taskify.core.ui.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import com.rosyidputrar.taskify.core.domain.vault.Highlighter
import com.rosyidputrar.taskify.core.domain.vault.TokenType

/**
 * Kartu kode untuk dibagikan sebagai gambar: jendela gelap dengan tiga titik, judul & bahasa,
 * nomor baris, dan pewarnaan sintaks (tokenizer yang sama dengan tampilan snippet).
 *
 * Lebar mengikuti baris terpanjang (40–100 kolom); baris yang lebih panjang dibungkus dan nomor
 * barisnya hanya di baris pertama. Kode sangat panjang dipotong di [MAX_LINES] baris.
 */
object CodeImage {
    const val MAX_LINES = 220
    private const val MIN_COLS = 40
    private const val MAX_COLS = 100

    private const val FONT = 26f // px
    private const val PAD = 56f // bingkai luar
    private const val INNER = 36f
    private const val BAR = 72f

    private val WINDOW = Color.rgb(0x1F, 0x22, 0x28)
    private val TEXT = Color.rgb(0xE6, 0xE1, 0xD6)
    private val DIM = Color.rgb(0x7D, 0x83, 0x8C)
    private val COLORS = mapOf(
        TokenType.Keyword to Color.rgb(0xC7, 0x92, 0xEA),
        TokenType.String to Color.rgb(0xA5, 0xD6, 0x8A),
        TokenType.Comment to Color.rgb(0x7D, 0x83, 0x8C),
        TokenType.Number to Color.rgb(0xF7, 0x8C, 0x6C),
        TokenType.Tag to Color.rgb(0x82, 0xC8, 0xF0),
    )

    class Image(val bitmap: Bitmap, val truncated: Boolean)

    fun render(code: String, language: String, title: String, languageLabel: String, accent: Int = DocRender.TEAL): Image {
        val all = code.trimEnd('\n').replace("\t", "    ").lines()
        val truncated = all.size > MAX_LINES
        val lines = if (truncated) all.take(MAX_LINES) else all

        val mono = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = FONT; typeface = Typeface.MONOSPACE; color = TEXT }
        val charW = mono.measureText("M")
        val cols = (lines.maxOfOrNull { it.length } ?: 0).coerceIn(MIN_COLS, MAX_COLS)
        val gutterDigits = lines.size.toString().length.coerceAtLeast(2)
        val gutter = charW * (gutterDigits + 2)
        val codeW = (charW * cols).toInt() + 2
        val windowW = INNER * 2 + gutter + codeW
        val width = (windowW + PAD * 2).toInt()

        // Warnai seluruh kode sekali, lalu potong per baris (token bisa melintasi baris, mis. komentar blok).
        val colored = SpannableStringBuilder()
        Highlighter.tokenize(lines.joinToString("\n"), language).forEach { t ->
            val start = colored.length
            colored.append(t.text)
            COLORS[t.type]?.let { c -> colored.setSpan(ForegroundColorSpan(c), start, colored.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
            if (t.type == TokenType.Comment) colored.setSpan(StyleSpan(Typeface.ITALIC), start, colored.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        // Jaga-jaga: bila token tidak menutup teks persis, pakai teks polos agar potongan per baris tetap benar.
        val source: CharSequence = if (colored.length == lines.sumOf { it.length } + (lines.size - 1).coerceAtLeast(0)) colored else lines.joinToString("\n")
        var offset = 0
        val layouts = lines.map { line ->
            val piece = source.subSequence(offset, offset + line.length)
            offset += line.length + 1
            StaticLayout.Builder.obtain(piece.ifEmpty { " " }, 0, piece.ifEmpty { " " }.length, mono, codeW)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.3f).setIncludePad(false).build()
        }
        val codeH = layouts.sumOf { it.height }.toFloat()
        val footer = if (truncated) 54f else 0f
        val windowH = BAR + INNER * 0.6f + codeH + INNER + footer
        val height = (windowH + PAD * 2).toInt()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        // Latar: gradasi lembut dari warna aksen modul.
        val bg = Paint().apply { shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), accent, darken(accent, 0.55f), Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)
        val win = RectF(PAD, PAD, PAD + windowW, PAD + windowH)
        c.drawRoundRect(RectF(win.left, win.top + 10f, win.right, win.bottom + 14f), 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 0, 0, 0) })
        c.drawRoundRect(win, 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WINDOW })

        // Bilah jendela: tiga titik, judul di tengah, bahasa di kanan.
        listOf(Color.rgb(0xFF, 0x5F, 0x57), Color.rgb(0xFE, 0xBC, 0x2E), Color.rgb(0x28, 0xC8, 0x40)).forEachIndexed { i, col ->
            c.drawCircle(win.left + INNER + i * 30f, win.top + BAR / 2, 9f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col })
        }
        val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; color = DIM; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL) }
        val lang = languageLabel.uppercase()
        val langW = small.measureText(lang)
        c.drawText(lang, win.right - INNER - langW, win.top + BAR / 2 + 8f, small)
        val titlePaint = TextPaint(small).apply { color = TEXT; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
        val roomForTitle = windowW - INNER * 2 - 120f - langW - 24f
        val shownTitle = android.text.TextUtils.ellipsize(title, titlePaint, roomForTitle.coerceAtLeast(60f), android.text.TextUtils.TruncateAt.END).toString()
        c.drawText(shownTitle, win.left + (windowW - titlePaint.measureText(shownTitle)) / 2, win.top + BAR / 2 + 8f, titlePaint)
        c.drawLine(win.left, win.top + BAR, win.right, win.top + BAR, Paint().apply { color = Color.argb(28, 255, 255, 255); strokeWidth = 2f })

        // Kode + nomor baris.
        val num = TextPaint(mono).apply { color = Color.argb(110, 0xE6, 0xE1, 0xD6) }
        var y = win.top + BAR + INNER * 0.6f
        val codeX = win.left + INNER + gutter
        layouts.forEachIndexed { i, l ->
            val n = "${i + 1}"
            // Nomor rata kanan di kolom gutter, sejajar baseline baris pertama.
            c.drawText(n, codeX - charW - num.measureText(n), y + l.getLineBaseline(0), num)
            c.save(); c.translate(codeX, y); l.draw(c); c.restore()
            y += l.height
        }
        if (truncated) {
            c.drawText("… ${all.size - MAX_LINES} baris lagi — bagikan sebagai berkas untuk kode lengkap", win.left + INNER, y + 38f, small)
        }
        // Tanda kecil di bawah bingkai.
        val mark = TextPaint(small).apply { color = Color.argb(170, 255, 255, 255); textSize = 20f }
        c.drawText("Taskify · Vault", PAD, height - PAD / 2 + 6f, mark)
        return Image(bitmap, truncated)
    }

    private fun darken(color: Int, f: Float) = Color.rgb((Color.red(color) * f).toInt(), (Color.green(color) * f).toInt(), (Color.blue(color) * f).toInt())
}
