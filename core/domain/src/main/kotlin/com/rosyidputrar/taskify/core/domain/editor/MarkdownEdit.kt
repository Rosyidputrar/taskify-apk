package com.rosyidputrar.taskify.core.domain.editor

import kotlin.math.max
import kotlin.math.min

/** Teks + seleksi editor (indeks karakter, seperti TextFieldValue). */
data class EditState(val text: String, val start: Int, val end: Int = start) {
    val min: Int get() = min(start, end)
    val max: Int get() = max(start, end)
    val collapsed: Boolean get() = start == end
}

/** Aksi toolbar Markdown — sama dengan toolbar editor webapp. */
sealed interface MarkdownAction {
    data class Wrap(val before: String, val after: String, val placeholder: String) : MarkdownAction
    data class LinePrefix(val prefix: String) : MarkdownAction

    companion object {
        val Bold = Wrap("**", "**", "teks tebal")
        val Italic = Wrap("_", "_", "teks miring")
        val Strike = Wrap("~~", "~~", "teks dicoret")
        val Code = Wrap("`", "`", "kode")
        val Link = Wrap("[", "](https://)", "teks")
        val Heading = LinePrefix("## ")
        val Bullet = LinePrefix("- ")
        val Numbered = LinePrefix("1. ")
        val Checklist = LinePrefix("- [ ] ")
        val Quote = LinePrefix("> ")
    }
}

object MarkdownEdit {
    fun apply(state: EditState, action: MarkdownAction): EditState = when (action) {
        is MarkdownAction.Wrap -> wrap(state, action)
        is MarkdownAction.LinePrefix -> togglePrefix(state, action.prefix)
    }

    /** Bungkus seleksi; spasi di tepi seleksi tetap di luar penanda. Tanpa seleksi → placeholder terpilih. */
    private fun wrap(state: EditState, a: MarkdownAction.Wrap): EditState {
        val s = state.min
        val e = state.max
        val raw = state.text.substring(s, e)
        val lead = raw.takeWhile { it.isWhitespace() }
        val trail = raw.drop(lead.length).takeLastWhile { it.isWhitespace() }
        val selected = raw.trim().ifEmpty { a.placeholder }
        val text = state.text.substring(0, s) + lead + a.before + selected + a.after + trail + state.text.substring(e)
        val from = s + lead.length + a.before.length
        return EditState(text, from, from + selected.length)
    }

    private val LIST_PREFIX = Regex("^(\\s*)([-*+] \\[[ xX]] |[-*+] |\\d+\\. |> |#{1,6} )")

    /**
     * Pasang/lepas awalan baris pada semua baris yang terseleksi. Awalan blok lain di baris itu
     * diganti (mis. "- " → "- [ ] "), jadi tombol checklist pada daftar biasa langsung bekerja.
     */
    private fun togglePrefix(state: EditState, prefix: String): EditState {
        val text = state.text
        val lineStart = text.lastIndexOf('\n', state.min - 1) + 1
        val lineEndRaw = text.indexOf('\n', state.max)
        val lineEnd = if (lineEndRaw < 0) text.length else lineEndRaw
        val lines = text.substring(lineStart, lineEnd).split("\n")
        val allHave = lines.all { it.trimStart().startsWith(prefix) }
        var delta = 0
        var firstDelta = 0
        val changed = lines.mapIndexed { idx, line ->
            val replaced = if (allHave) {
                val indent = line.takeWhile { it == ' ' || it == '\t' }
                indent + line.drop(indent.length).removePrefix(prefix)
            } else {
                val m = LIST_PREFIX.find(line)
                if (m != null) m.groupValues[1] + prefix + line.substring(m.value.length) else prefix + line
            }
            val d = replaced.length - line.length
            if (idx == 0) firstDelta = d
            delta += d
            replaced
        }.joinToString("\n")
        val newText = text.substring(0, lineStart) + changed + text.substring(lineEnd)
        val start = (state.min + firstDelta).coerceAtLeast(lineStart)
        val end = (state.max + delta).coerceAtLeast(start)
        return if (state.collapsed) EditState(newText, start) else EditState(newText, start, end)
    }

    private val CONTINUE = Regex("^(\\s*)([-*+] \\[[ xX]] |[-*+] |(\\d+)\\. |> )(.*)$")

    /**
     * Enter di dalam daftar: dipanggil saat [next] = [prev] + satu "\n" yang baru diketik di posisi
     * kursor. Item berisi → item baru dengan penanda berikutnya; item kosong → daftar diakhiri.
     * Mengembalikan null bila perubahan bukan Enter di daftar (biarkan apa adanya).
     */
    fun continueList(prev: EditState, next: EditState): EditState? {
        if (!prev.collapsed || !next.collapsed) return null
        val pos = prev.start
        if (next.text.length != prev.text.length + 1 || next.start != pos + 1) return null
        if (next.text[pos] != '\n' || next.text.removeRange(pos, pos + 1) != prev.text) return null
        val lineStart = prev.text.lastIndexOf('\n', pos - 1) + 1
        val line = prev.text.substring(lineStart, pos)
        val m = CONTINUE.find(line) ?: return null
        val indent = m.groupValues[1]
        val marker = m.groupValues[2]
        val num = m.groupValues[3]
        val rest = m.groupValues[4]
        if (rest.isBlank()) {
            val text = prev.text.substring(0, lineStart) + prev.text.substring(pos)
            return EditState(text, lineStart)
        }
        val nextMarker = if (num.isNotEmpty()) {
            val n = num.toLongOrNull()?.takeIf { it < Long.MAX_VALUE } ?: return null
            "${n + 1}. "
        } else {
            marker.replace(Regex("\\[[xX]]"), "[ ]")
        }
        val insert = "\n$indent$nextMarker"
        val text = prev.text.substring(0, pos) + insert + prev.text.substring(pos)
        return EditState(text, pos + insert.length)
    }
}
