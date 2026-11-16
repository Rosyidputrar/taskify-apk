package com.rosyidputrar.taskify.core.domain.notes

import com.rosyidputrar.taskify.core.domain.time.Dates

/** Satu baris `notes`. [noteDate] null = catatan bebas; terisi = ditempel ke hari itu. */
data class Note(
    val id: String,
    val noteDate: String?,
    val title: String?,
    val content: String,
    val pinned: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
)

/** Filter jenis: semua, yang ditempel ke tanggal, atau catatan bebas. [value] = parameter SQL. */
enum class NoteScope(val value: String, val label: String) {
    All("all", "Semua"), Dated("dated", "Bertanggal"), Free("free", "Bebas"),
}

/** Escape `%`, `_`, `\` agar dibaca apa adanya oleh `LIKE … ESCAPE '\'`. */
fun escapeLike(input: String): String = input.replace(Regex("[\\\\%_]")) { "\\" + it.value }

const val FREE_GROUP = "bebas"

data class NoteGroups(val pinned: List<Note>, val groups: List<Pair<String, List<Note>>>)

/**
 * Kelompokkan hasil query (terurut pin → tanggal → dibuat): "Disematkan", lalu per bulan
 * (terbaru dulu), lalu "Tanpa tanggal". Di Sampah tidak ada grup sematan.
 */
fun groupNotes(notes: List<Note>, trash: Boolean): NoteGroups {
    val pinned = if (trash) emptyList() else notes.filter { it.pinned }
    val rest = if (trash) notes else notes.filterNot { it.pinned }
    val byKey = LinkedHashMap<String, MutableList<Note>>()
    rest.forEach { n -> byKey.getOrPut(n.noteDate?.take(7) ?: FREE_GROUP) { mutableListOf() } += n }
    val groups = byKey.entries.map { it.key to it.value.toList() }.sortedWith { (a, _), (b, _) ->
        when {
            a == FREE_GROUP -> 1
            b == FREE_GROUP -> -1
            else -> b.compareTo(a)
        }
    }
    return NoteGroups(pinned, groups)
}

fun groupLabel(key: String): String =
    if (key == FREE_GROUP) "Tanpa tanggal" else Dates.format("$key-01", "MMMM yyyy")

fun noteDateLabel(date: String?, today: String): String = date?.let { Dates.relativeDayLabel(it, today) } ?: "Bebas"

fun notesToMarkdown(notes: List<Note>): String = notes.joinToString("\n---\n\n") { n ->
    val head = "## ${n.title?.trim()?.ifEmpty { null } ?: "Tanpa judul"}"
    val meta = n.noteDate?.let { "\n_${it}_\n" } ?: ""
    "$head\n$meta\n${n.content.trim()}\n"
}
