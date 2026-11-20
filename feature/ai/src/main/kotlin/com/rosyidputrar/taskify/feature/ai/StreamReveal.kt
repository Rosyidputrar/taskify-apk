package com.rosyidputrar.taskify.feature.ai

/**
 * Tampilan streaming yang halus: teks tidak muncul per huruf/kata. Selama streaming hanya bagian
 * sampai batas aman terakhir (akhir kalimat / baris; di dalam blok kode: akhir baris) yang
 * ditampilkan, lalu bagian baru di-fade-in. Teks dipecah per blok Markdown supaya blok baru
 * bisa muncul sendiri-sendiri tanpa mengganggu blok di atasnya.
 */
object StreamReveal {
    private val SENTENCE_END = setOf('.', '!', '?', '…', ':', ';')

    /** Bagian [raw] yang boleh ditampilkan saat ini. [final] = streaming selesai → semua. */
    fun committed(raw: String, final: Boolean): String {
        if (final) return raw
        val fence = openFence(raw)
        var cut = raw.lastIndexOf('\n') + 1 // baris lengkap selalu aman
        if (!fence) {
            // Akhir kalimat yang sudah diikuti spasi (bukan "3.5", bukan "Rp1.000").
            for (i in raw.length - 2 downTo maxOf(cut, 0)) {
                if (raw[i] in SENTENCE_END && raw[i + 1].isWhitespace()) { cut = maxOf(cut, i + 1); break }
            }
        }
        return raw.substring(0, cut).trimEnd(' ', '\t')
    }

    /** true = ada blok kode ``` / ~~~ yang belum ditutup. */
    fun openFence(text: String): Boolean {
        var open: String? = null
        text.lineSequence().forEach { line ->
            val t = line.trimStart()
            val marker = when {
                t.startsWith("```") -> "```"
                t.startsWith("~~~") -> "~~~"
                else -> null
            }
            if (marker != null) open = if (open == null) marker else if (open == marker) null else open
        }
        return open != null
    }

    /**
     * Pecah Markdown menjadi blok tingkat atas di baris kosong — kecuali di dalam blok kode atau bila
     * baris berikutnya berindentasi (lanjutan daftar / kode bertingkat), supaya tidak ada struktur
     * yang terbelah.
     */
    fun blocks(text: String): List<String> {
        val lines = text.split('\n')
        val out = mutableListOf<String>()
        val cur = mutableListOf<String>()
        var fence: String? = null
        fun flush() {
            while (cur.isNotEmpty() && cur.last().isBlank()) cur.removeAt(cur.lastIndex)
            if (cur.isNotEmpty()) out += cur.joinToString("\n")
            cur.clear()
        }
        lines.forEachIndexed { i, line ->
            val t = line.trimStart()
            val marker = when {
                t.startsWith("```") -> "```"
                t.startsWith("~~~") -> "~~~"
                else -> null
            }
            if (marker != null) fence = if (fence == null) marker else if (fence == marker) null else fence
            if (fence == null && marker == null && line.isBlank()) {
                val next = lines.drop(i + 1).firstOrNull { it.isNotBlank() }
                val continues = next != null && (next.startsWith(" ") || next.startsWith("\t"))
                if (!continues && cur.isNotEmpty()) { flush(); return@forEachIndexed }
            }
            if (cur.isEmpty() && line.isBlank()) return@forEachIndexed
            cur += line
        }
        flush()
        return out
    }
}
