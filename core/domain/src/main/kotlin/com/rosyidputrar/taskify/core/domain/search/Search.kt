package com.rosyidputrar.taskify.core.domain.search

import com.rosyidputrar.taskify.core.domain.files.EntityType
import java.text.Normalizer

/** Label tampilan jenis entitas lintas modul (pencarian, tag, tautan, Sampah). */
val EntityType.label: String
    get() = when (this) {
        EntityType.Task -> "Tugas"
        EntityType.JournalEntry -> "Jurnal"
        EntityType.Note -> "Catatan"
        EntityType.Transaction -> "Transaksi"
        EntityType.Snippet -> "Snippet"
        EntityType.CodeProject -> "Project"
    }

/** Satu entitas dari `ENTITY_UNION` (+ cuplikan pencarian bila ada). */
data class EntityRow(
    val type: EntityType,
    val id: String,
    val title: String,
    val subtitle: String?,
    val deletedAt: String? = null,
    val snippet: String? = null,
)

data class TagHit(val id: String, val name: String, val color: String?, val count: Int)

data class SnippetPart(val text: String, val hit: Boolean)

object Search {
    const val MARK_OPEN = "⟪"
    const val MARK_CLOSE = "⟫"

    /** "bayar list" → `"bayar"* "list"*` (semua kata wajib ada, awalan cocok). Sama dengan webapp. */
    fun toFtsQuery(input: String): String? {
        val words = Normalizer.normalize(input.lowercase(), Normalizer.Form.NFKD)
            .replace(Regex("[\\u0300-\\u036f]"), "")
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotEmpty() }
            .take(8)
        return if (words.isEmpty()) null else words.joinToString(" ") { "\"$it\"*" }
    }

    /** Pecah cuplikan ⟪…⟫ menjadi bagian biasa & sorotan. */
    fun splitSnippet(snippet: String): List<SnippetPart> {
        val parts = mutableListOf<SnippetPart>()
        var last = 0
        Regex("$MARK_OPEN(.*?)$MARK_CLOSE").findAll(snippet).forEach { m ->
            if (m.range.first > last) parts += SnippetPart(snippet.substring(last, m.range.first), false)
            parts += SnippetPart(m.groupValues[1], true)
            last = m.range.last + 1
        }
        if (last < snippet.length) parts += SnippetPart(snippet.substring(last), false)
        return parts
    }

    /**
     * Subtitle transaksi dari ENTITY_UNION berbentuk `tanggal|jenis|jumlah`; entitas lain apa
     * adanya (tanggal / bahasa / kosong).
     */
    data class TxSubtitle(val date: String, val kind: String, val amount: Long)

    fun txSubtitle(raw: String?): TxSubtitle? {
        val p = raw?.split('|') ?: return null
        if (p.size != 3) return null
        return TxSubtitle(p[0], p[1], p[2].toLongOrNull() ?: 0)
    }
}
