package com.rosyidputrar.taskify.core.domain.text

/**
 * Helper Markdown yang dipakai Jurnal & Catatan. Fungsi murni (port `lib/markdown.ts` webapp) +
 * pengurai blok/inline sederhana untuk pratinjau native. HTML mentah tidak pernah dirender: semua
 * teks diperlakukan sebagai teks biasa, jadi aman dari injeksi.
 */
object Markdown {
    private const val EXCERPT_SCAN = 4000
    private const val CACHE_MAX = 1500
    private val cache = object : LinkedHashMap<String, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > CACHE_MAX
    }

    private val FENCE = Regex("```[\\s\\S]*?```")
    private val INLINE_CODE = Regex("`([^`]*)`")
    private val IMAGE = Regex("!\\[[^\\]]*]\\([^)]*\\)")
    private val LINK = Regex("\\[([^\\]]*)]\\([^)]*\\)")
    private val LINE_MARK = Regex("^\\s{0,3}(#{1,6}|>|[-*+]\\s+\\[[ xX]]|[-*+]|\\d+\\.)\\s*", RegexOption.MULTILINE)
    private val EMPHASIS = Regex("(\\*\\*|__|\\*|_|~~)")
    private val SPACES = Regex("\\s+")

    /** Cuplikan teks polos untuk kartu/daftar. Hanya awal teks yang diproses; hasil di-cache. */
    fun plainExcerpt(markdown: String, max: Int = 160): String {
        val head = markdown.take(EXCERPT_SCAN)
        val key = "$max|$head"
        synchronized(cache) { cache[key]?.let { return it } }
        val text = head
            .replace(FENCE, " ")
            .replace(INLINE_CODE, "$1")
            .replace(IMAGE, " ")
            .replace(LINK, "$1")
            .replace(LINE_MARK, "")
            .replace(EMPHASIS, "")
            .replace(SPACES, " ")
            .trim()
        val out = if (text.length > max) text.take(max - 1).trimEnd() + "…" else text
        synchronized(cache) { cache[key] = out }
        return out
    }

    /**
     * Cuplikan per baris untuk kartu catatan: sintaks dibuang tapi baris tetap terpisah, jadi
     * daftar tidak menyatu jadi satu kalimat. Baris kosong & blok kode dilewati.
     */
    fun plainLines(markdown: String, maxLines: Int = 8, maxChars: Int = 320): String {
        val out = mutableListOf<String>()
        var total = 0
        for (raw in markdown.take(EXCERPT_SCAN).replace(FENCE, "\n").lines()) {
            val line = raw
                .replace(INLINE_CODE, "$1")
                .replace(IMAGE, " ")
                .replace(LINK, "$1")
                .replace(LINE_MARK, "")
                .replace(EMPHASIS, "")
                .replace(SPACES, " ")
                .trim()
            if (line.isEmpty()) continue
            if (out.size == maxLines || total + line.length > maxChars) {
                if (out.isNotEmpty()) out[out.lastIndex] = out.last().trimEnd() + "…"
                break
            }
            out += line
            total += line.length
        }
        return out.joinToString("\n")
    }

    fun wordCount(text: String): Int = text.trim().let { if (it.isEmpty()) 0 else it.split(SPACES).size }

    private val CHECK = Regex("^(\\s*(?:[-*+]|\\d+\\.)\\s+)\\[( |x|X)]")

    /** Toggle checklist di baris tertentu (1-based): "- [ ]" ↔ "- [x]". */
    fun toggleChecklistLine(markdown: String, line: Int): String {
        val lines = markdown.split("\n").toMutableList()
        val current = lines.getOrNull(line - 1) ?: return markdown
        val m = CHECK.find(current) ?: return markdown
        lines[line - 1] = m.groupValues[1] + "[" + (if (m.groupValues[2] == " ") "x" else " ") + "]" + current.substring(m.range.last + 1)
        return lines.joinToString("\n")
    }

    /** Jumlah checklist selesai / total (untuk kartu catatan). */
    fun checklistProgress(markdown: String): Pair<Int, Int> {
        var done = 0
        var total = 0
        markdown.lineSequence().forEach { l ->
            CHECK.find(l)?.let { total++; if (it.groupValues[2] != " ") done++ }
        }
        return done to total
    }

    // ------------------------------------------------------------------ blok

    private val HEADING = Regex("^\\s{0,3}(#{1,6})\\s+(.*?)\\s*#*\\s*$")
    private val RULE = Regex("^\\s{0,3}([-*_])(\\s*\\1){2,}\\s*$")
    private val LIST = Regex("^(\\s*)([-*+]|(\\d+)[.)])\\s+(?:\\[( |x|X)]\\s+|\\[( |x|X)]$)?(.*)$")
    private val QUOTE = Regex("^\\s{0,3}>\\s?(.*)$")
    private val TABLE_SEP = Regex("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$")

    fun parseBlocks(markdown: String): List<MdBlock> {
        val lines = markdown.replace("\r\n", "\n").split("\n")
        val out = mutableListOf<MdBlock>()
        var i = 0
        val paragraph = StringBuilder()
        var paragraphLine = 0
        fun flushParagraph() {
            if (paragraph.isNotEmpty()) out += MdBlock.Paragraph(paragraph.toString(), paragraphLine)
            paragraph.clear()
        }
        while (i < lines.size) {
            val line = lines[i]
            val number = i + 1
            when {
                line.trimStart().startsWith("```") -> {
                    flushParagraph()
                    val lang = line.trimStart().removePrefix("```").trim()
                    val code = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trimStart().startsWith("```")) code += lines[i++]
                    out += MdBlock.Code(code.joinToString("\n"), lang.ifEmpty { null }, number)
                }
                line.isBlank() -> flushParagraph()
                RULE.matches(line) -> { flushParagraph(); out += MdBlock.Rule(number) }
                HEADING.matches(line) -> {
                    flushParagraph()
                    val m = HEADING.find(line)!!
                    out += MdBlock.Heading(m.groupValues[1].length, m.groupValues[2], number)
                }
                QUOTE.matches(line) -> {
                    flushParagraph()
                    val text = mutableListOf(QUOTE.find(line)!!.groupValues[1])
                    while (i + 1 < lines.size && QUOTE.matches(lines[i + 1])) text += QUOTE.find(lines[++i])!!.groupValues[1]
                    out += MdBlock.Quote(text.joinToString("\n"), number)
                }
                LIST.matches(line) -> {
                    flushParagraph()
                    val m = LIST.find(line)!!
                    val indent = m.groupValues[1].replace("\t", "    ").length / 2
                    val mark = m.groupValues[4].ifEmpty { m.groupValues[5] }
                    out += MdBlock.ListItem(
                        depth = indent.coerceAtMost(4),
                        number = m.groupValues[3].toIntOrNull(),
                        checked = if (mark.isEmpty()) null else mark != " ",
                        text = m.groupValues[6],
                        line = number,
                    )
                }
                line.contains('|') && i + 1 < lines.size && TABLE_SEP.matches(lines[i + 1]) -> {
                    flushParagraph()
                    val header = cells(line)
                    i += 2
                    val rows = mutableListOf<List<String>>()
                    while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) rows += cells(lines[i++])
                    i--
                    out += MdBlock.Table(header, rows, number)
                }
                else -> {
                    if (paragraph.isEmpty()) paragraphLine = number else paragraph.append('\n')
                    paragraph.append(line.trim())
                }
            }
            i++
        }
        flushParagraph()
        return out
    }

    private fun cells(line: String): List<String> =
        line.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }

    // ------------------------------------------------------------------ inline

    private val URL = Regex("^https?://[^\\s<>()]+[^\\s<>().,;:!?'\"]")

    /** Urai teks satu blok jadi potongan bergaya (tebal, miring, coret, kode, tautan). */
    fun parseInline(text: String): List<MdSpan> {
        val out = mutableListOf<MdSpan>()
        parseInto(text, MdStyle(), out)
        // Gabungkan potongan bertetangga dengan gaya sama.
        val merged = mutableListOf<MdSpan>()
        for (span in out) {
            val last = merged.lastOrNull()
            if (last != null && last.style == span.style) merged[merged.lastIndex] = last.copy(text = last.text + span.text) else merged += span
        }
        return merged
    }

    private fun parseInto(s: String, style: MdStyle, out: MutableList<MdSpan>) {
        val buf = StringBuilder()
        fun flush() { if (buf.isNotEmpty()) { out += MdSpan(buf.toString(), style); buf.clear() } }
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length && s[i + 1] in "\\`*_~[]()#+-.!|>") {
                buf.append(s[i + 1]); i += 2; continue
            }
            if (c == '`' && !style.code) {
                val end = s.indexOf('`', i + 1)
                if (end > i + 1) {
                    flush(); out += MdSpan(s.substring(i + 1, end), style.copy(code = true)); i = end + 1; continue
                }
            }
            if (c == '[' && style.link == null) {
                val close = s.indexOf("](", i + 1)
                val end = if (close > 0) s.indexOf(')', close + 2) else -1
                if (close > i && end > close) {
                    val url = s.substring(close + 2, end).trim()
                    if (url.isNotEmpty() && !url.contains(' ')) {
                        flush(); parseInto(s.substring(i + 1, close), style.copy(link = url), out); i = end + 1; continue
                    }
                }
            }
            if ((c == 'h') && style.link == null && !style.code && (i == 0 || !s[i - 1].isLetterOrDigit())) {
                val m = URL.find(s.substring(i))
                if (m != null) {
                    flush(); out += MdSpan(m.value, style.copy(link = m.value)); i += m.value.length
                    continue
                }
            }
            val delim = when {
                s.startsWith("**", i) -> "**"
                s.startsWith("__", i) -> "__"
                s.startsWith("~~", i) -> "~~"
                c == '*' || c == '_' -> c.toString()
                else -> null
            }
            if (delim != null && i + delim.length < s.length && !s[i + delim.length].isWhitespace()) {
                val intraword = delim[0] == '_' && i > 0 && s[i - 1].isLetterOrDigit()
                val end = if (intraword) -1 else findClosing(s, delim, i + delim.length)
                if (end > i + delim.length) {
                    flush()
                    val inner = s.substring(i + delim.length, end)
                    val next = when (delim) {
                        "**", "__" -> style.copy(bold = true)
                        "~~" -> style.copy(strike = true)
                        else -> style.copy(italic = true)
                    }
                    parseInto(inner, next, out)
                    i = end + delim.length
                    continue
                }
            }
            buf.append(c)
            i++
        }
        flush()
    }

    private fun findClosing(s: String, delim: String, from: Int): Int {
        var j = s.indexOf(delim, from)
        while (j > 0) {
            val beforeOk = !s[j - 1].isWhitespace()
            val after = j + delim.length
            // "*" tunggal tidak boleh jadi bagian "**"; "_" penutup tidak boleh di tengah kata.
            val single = delim.length == 1 && (s.getOrNull(after) == delim[0] || s.getOrNull(j - 1) == delim[0])
            val intraword = delim[0] == '_' && (s.getOrNull(after)?.isLetterOrDigit() == true)
            if (beforeOk && !single && !intraword) return j
            j = s.indexOf(delim, j + 1)
        }
        return -1
    }
}

sealed interface MdBlock {
    /** Nomor baris (1-based) awal blok di teks asli. */
    val line: Int

    data class Heading(val level: Int, val text: String, override val line: Int) : MdBlock
    data class Paragraph(val text: String, override val line: Int) : MdBlock
    data class ListItem(val depth: Int, val number: Int?, val checked: Boolean?, val text: String, override val line: Int) : MdBlock
    data class Quote(val text: String, override val line: Int) : MdBlock
    data class Code(val code: String, val language: String?, override val line: Int) : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>, override val line: Int) : MdBlock
    data class Rule(override val line: Int) : MdBlock
}

data class MdStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strike: Boolean = false,
    val code: Boolean = false,
    val link: String? = null,
)

data class MdSpan(val text: String, val style: MdStyle)
