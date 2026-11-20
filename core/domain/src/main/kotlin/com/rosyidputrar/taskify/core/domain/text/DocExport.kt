package com.rosyidputrar.taskify.core.domain.text

/** Format berkas untuk membagikan dokumen (catatan, jurnal). */
enum class ShareFormat(val label: String, val extension: String, val mime: String, val hint: String) {
    Markdown("Markdown", "md", "text/markdown", "Format asli — rapi di Obsidian, Notion, GitHub"),
    Text("Teks biasa", "txt", "text/plain", "Tanpa simbol Markdown, terbaca di mana saja"),
    Html("Halaman web", "html", "text/html", "Dibuka di browser dengan format lengkap"),
    Pdf("PDF", "pdf", "application/pdf", "Siap dicetak atau dikirim sebagai dokumen"),
    Image("Gambar", "png", "image/png", "Satu lembar gambar — pas untuk chat & story"),
}

/**
 * Ubah dokumen (judul + keterangan + isi Markdown) ke format bagikan. Murni & teruji JVM; PDF &
 * gambar digambar di Android dari [MdBlock] yang sama sehingga isinya konsisten antar format.
 * [meta] = keterangan di bawah judul, mis. "Jumat, 20 November 2026 · Mood Baik".
 */
object DocExport {
    /** Markdown utuh: judul jadi `# Judul`, keterangan jadi baris miring di bawahnya. */
    fun markdown(title: String?, content: String, meta: String? = null): String {
        val parts = listOfNotNull(
            title?.trim()?.takeIf { it.isNotEmpty() }?.let { "# $it" },
            meta?.trim()?.takeIf { it.isNotEmpty() }?.let { "_${it}_" },
            content.trimEnd().takeIf { it.isNotBlank() },
        )
        return parts.joinToString("\n\n") + "\n"
    }

    /** Teks polos: simbol Markdown dibuang, checklist jadi ☐/☑, tautan jadi "teks (url)". */
    fun plainText(title: String?, content: String, meta: String? = null): String {
        val out = StringBuilder()
        val t = title?.trim()?.takeIf { it.isNotEmpty() }
        val m = meta?.trim()?.takeIf { it.isNotEmpty() }
        when {
            t != null && m != null -> out.append(t).append('\n').append(m).append("\n\n")
            t != null -> out.append(t).append("\n\n")
            m != null -> out.append(m).append("\n\n")
        }
        val blocks = Markdown.parseBlocks(content)
        blocks.forEachIndexed { i, b ->
            val prev = blocks.getOrNull(i - 1)
            // Item daftar beruntun tidak diberi baris kosong di antaranya.
            if (i > 0) out.append(if (b is MdBlock.ListItem && prev is MdBlock.ListItem) "\n" else "\n\n")
            out.append(
                when (b) {
                    is MdBlock.Heading -> plain(b.text)
                    is MdBlock.Paragraph -> plain(b.text)
                    is MdBlock.ListItem -> "  ".repeat(b.depth) + bullet(b) + plain(b.text)
                    is MdBlock.Quote -> plain(b.text).lines().joinToString("\n") { "│ $it" }
                    is MdBlock.Code -> b.code.trimEnd()
                    is MdBlock.Table -> (listOf(b.header) + b.rows).joinToString("\n") { row -> row.joinToString(" | ") { plain(it) } }
                    is MdBlock.Rule -> "———"
                },
            )
        }
        return out.toString().trimEnd() + "\n"
    }

    /** Dokumen HTML mandiri (CSS di dalam, tanpa skrip, semua teks di-escape). */
    fun html(title: String?, content: String, meta: String? = null): String {
        val t = title?.trim()?.takeIf { it.isNotEmpty() }
        val sb = StringBuilder()
        sb.append("<!doctype html>\n<html lang=\"id\">\n<head>\n<meta charset=\"utf-8\">\n")
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
        sb.append("<title>").append(esc(t ?: "Catatan")).append("</title>\n<style>\n").append(CSS).append("</style>\n</head>\n<body>\n<article>\n")
        t?.let { sb.append("<h1 class=\"title\">").append(esc(it)).append("</h1>\n") }
        meta?.let { sb.append("<p class=\"meta\">").append(esc(it)).append("</p>\n") }
        val blocks = Markdown.parseBlocks(content)
        var i = 0
        while (i < blocks.size) {
            val b = blocks[i]
            if (b is MdBlock.ListItem) {
                // Kumpulkan item beruntun → <ul>/<ol> bersarang sesuai kedalaman.
                // Daftar baru dimulai bila jenis item tingkat atas berubah (mis. checklist → bernomor).
                val kind = listKind(b)
                val run = generateSequence(i) { it + 1 }
                    .takeWhile { j -> j < blocks.size && (blocks[j] as? MdBlock.ListItem)?.let { it.depth > 0 || listKind(it) == kind } == true }
                    .map { blocks[it] as MdBlock.ListItem }.toList()
                list(sb, run)
                i += run.size
                continue
            }
            when (b) {
                is MdBlock.Heading -> { val l = (b.level + 1).coerceAtMost(6); sb.append("<h$l>").append(inline(b.text)).append("</h$l>\n") }
                is MdBlock.Paragraph -> sb.append("<p>").append(inline(b.text).replace("\n", "<br>\n")).append("</p>\n")
                is MdBlock.Quote -> sb.append("<blockquote>").append(inline(b.text).replace("\n", "<br>\n")).append("</blockquote>\n")
                is MdBlock.Code -> sb.append("<pre><code").append(b.language?.let { " class=\"language-${esc(it)}\"" } ?: "").append(">").append(esc(b.code.trimEnd())).append("</code></pre>\n")
                is MdBlock.Table -> {
                    sb.append("<div class=\"table\"><table>\n<thead><tr>")
                    b.header.forEach { sb.append("<th>").append(inline(it)).append("</th>") }
                    sb.append("</tr></thead>\n<tbody>\n")
                    b.rows.forEach { r -> sb.append("<tr>"); r.forEach { sb.append("<td>").append(inline(it)).append("</td>") }; sb.append("</tr>\n") }
                    sb.append("</tbody></table></div>\n")
                }
                is MdBlock.Rule -> sb.append("<hr>\n")
                is MdBlock.ListItem -> Unit
            }
            i++
        }
        sb.append("</article>\n</body>\n</html>\n")
        return sb.toString()
    }

    /** Nama berkas aman dari judul (atau kalimat pertama isi); karakter terlarang dibuang. */
    fun fileName(title: String?, content: String, format: ShareFormat): String = fileName(title, content, format.extension)

    fun fileName(title: String?, content: String, extension: String, fallback: String = "Catatan"): String {
        val base = title?.trim()?.takeIf { it.isNotEmpty() }
            ?: Markdown.plainExcerpt(content, 48).takeIf { it.isNotBlank() }
            ?: fallback
        return "${safeName(base).ifEmpty { fallback }}.$extension"
    }

    /** Buang karakter terlarang di nama berkas, rapikan spasi, maksimal 60 karakter. */
    fun safeName(raw: String): String =
        raw.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ").replace(Regex("\\s+"), " ").trim().trimEnd('.').take(60).trim()

    // ------------------------------------------------------------------ bantu

    private fun bullet(b: MdBlock.ListItem) = when {
        b.checked == true -> "☑ "
        b.checked == false -> "☐ "
        b.number != null -> "${b.number}. "
        else -> "• "
    }

    /** Teks polos dari inline Markdown. */
    fun plain(text: String): String = Markdown.parseInline(text).joinToString("") { s ->
        val link = s.style.link
        if (link != null && link != s.text) "${s.text} ($link)" else s.text
    }

    private fun listKind(b: MdBlock.ListItem) = when { b.number != null -> 0; b.checked != null -> 1; else -> 2 }

    private fun list(sb: StringBuilder, items: List<MdBlock.ListItem>) {
        // Tumpukan tag yang terbuka per kedalaman.
        val open = ArrayDeque<String>()
        var depth = -1
        items.forEach { item ->
            val tag = if (item.number != null) "ol" else "ul"
            while (depth > item.depth) { sb.append("</li></").append(open.removeLast()).append(">\n"); depth-- }
            if (depth == item.depth) sb.append("</li>\n")
            while (depth < item.depth) {
                val cls = if (item.checked != null) " class=\"tasks\"" else ""
                sb.append("<").append(tag).append(cls).append(">\n"); open.addLast(tag); depth++
            }
            sb.append("<li>")
            item.checked?.let { sb.append(if (it) "<span class=\"box done\">✓</span> " else "<span class=\"box\"></span> ") }
            sb.append(if (item.checked == true) "<s>${inline(item.text)}</s>" else inline(item.text))
        }
        while (open.isNotEmpty()) { sb.append("</li></").append(open.removeLast()).append(">\n") }
    }

    private fun inline(text: String): String = Markdown.parseInline(text).joinToString("") { span(it) }

    private fun span(s: MdSpan): String {
        var h = esc(s.text)
        if (s.style.code) h = "<code>$h</code>"
        if (s.style.bold) h = "<strong>$h</strong>"
        if (s.style.italic) h = "<em>$h</em>"
        if (s.style.strike) h = "<s>$h</s>"
        s.style.link?.let { url -> if (SAFE_URL.containsMatchIn(url)) h = "<a href=\"${esc(url)}\">$h</a>" }
        return h
    }

    private fun esc(s: String) = buildString(s.length) {
        s.forEach { c ->
            when (c) {
                '&' -> append("&amp;"); '<' -> append("&lt;"); '>' -> append("&gt;")
                '"' -> append("&quot;"); '\'' -> append("&#39;"); else -> append(c)
            }
        }
    }

    /** Hanya tautan web/surel — `javascript:` dan sejenisnya tidak dijadikan tautan. */
    private val SAFE_URL = Regex("^(https?://|mailto:)", RegexOption.IGNORE_CASE)

    private val CSS = """
        :root { color-scheme: light dark; --ink: #1d1b16; --muted: #6b665c; --paper: #faf7f1; --line: #e3ddd0; --accent: #1d5867; --code: #efe9dd; }
        @media (prefers-color-scheme: dark) { :root { --ink: #ece6da; --muted: #a39d91; --paper: #16150f; --line: #34312a; --accent: #8fcfdc; --code: #24221c; } }
        * { box-sizing: border-box; }
        body { margin: 0; background: var(--paper); color: var(--ink); font: 17px/1.65 system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
        article { max-width: 720px; margin: 0 auto; padding: 40px 20px 64px; }
        h1, h2, h3, h4, h5, h6 { font-family: Georgia, "Times New Roman", serif; line-height: 1.25; margin: 1.4em 0 .5em; }
        h1.title { font-size: 2.1em; margin-top: 0; }
        .meta { color: var(--muted); margin-top: -.3em; font-size: .9em; }
        a { color: var(--accent); }
        blockquote { margin: 1em 0; padding: .2em 1em; border-left: 3px solid var(--line); color: var(--muted); }
        code { font-family: ui-monospace, "SF Mono", Menlo, Consolas, monospace; font-size: .9em; background: var(--code); padding: .1em .35em; border-radius: 4px; }
        pre { background: var(--code); padding: 14px 16px; border-radius: 10px; overflow-x: auto; }
        pre code { background: none; padding: 0; }
        hr { border: 0; border-top: 1px solid var(--line); margin: 2em 0; }
        .table { overflow-x: auto; }
        table { border-collapse: collapse; margin: 1em 0; }
        th, td { border: 1px solid var(--line); padding: 6px 12px; text-align: left; }
        ul.tasks { list-style: none; padding-left: 1.2em; }
        .box { display: inline-block; width: 1em; height: 1em; border: 1.5px solid var(--muted); border-radius: 3px; text-align: center; line-height: .95em; font-size: .9em; vertical-align: -.1em; }
        .box.done { background: var(--accent); border-color: var(--accent); color: var(--paper); }
        li s { color: var(--muted); }
    """.trimIndent() + "\n"
}
