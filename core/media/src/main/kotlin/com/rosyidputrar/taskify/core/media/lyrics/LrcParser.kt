package com.rosyidputrar.taskify.core.media.lyrics

import com.rosyidputrar.taskify.core.domain.media.LyricLine

/**
 * Pengurai LRC (lirik sinkron) — MURNI, tanpa jaringan. Port
 * `worker/lrc.ts` (music-taskify) ke Kotlin; perilaku yang sama:
 *
 * - `[mm:ss.xx]` / `[mm:ss.xxx]` / `[mm:ss]` / `[hh:mm:ss.xx]`
 *   (pemisah fraksi `.` atau `:`),
 * - beberapa penanda waktu dalam satu baris -> baris diduplikasi,
 * - tag metadata `[ti:] [ar:] [al:] [au:] [by:] [length:] …` dilewati,
 * - `[offset: ±ms]` menggeser SEMUA penanda waktu (dijepit di 0),
 * - baris yang tidak bisa di-parse dilewati,
 * - hasil diurutkan dan diduplikasi persis (t + teks sama) dibuang.
 *
 * Yang TIDAK dibawa: penanda per-kata `<mm:ss.xx>` (model Taskify
 * [LyricLine] hanya punya `t` + `text`; kata-kata di dalam baris
 * dipertahankan sebagai teks biasa, penandanya dibuang).
 */
object LrcParser {

    private val TIME_TAG = Regex("""\[(\d+):(\d{2})(?::(\d{2}))?(?:[.:](\d{2,3}))?]""")
    private val WORD_TAG = Regex("""<\d+:\d{2}(?:[.:]\d{2,3})?>""")
    private val META_TAG = Regex("""^\[([A-Za-z]+):""")
    private val OFFSET_TAG = Regex("""^\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)

    private fun toSeconds(hours: String?, minutes: String, seconds: String, fraction: String?): Double {
        val h = hours?.toDoubleOrNull() ?: 0.0
        val m = minutes.toDoubleOrNull() ?: 0.0
        val s = seconds.toDoubleOrNull() ?: 0.0
        val f = fraction?.let { it.toDoubleOrNull()?.div(if (it.length == 2) 100.0 else 1000.0) } ?: 0.0
        return Math.round((h * 3600 + m * 60 + s + f) * 1000) / 1000.0
    }

    fun parseLrc(raw: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        var offsetMs = 0L
        for (rawLine in raw.removePrefix("\uFEFF").split("\n")) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val offsetMatch = OFFSET_TAG.find(line)
            if (offsetMatch != null) {
                offsetMatch.groupValues[1].toLongOrNull()?.let { offsetMs = it }
                continue
            }
            if (META_TAG.containsMatchIn(line)) continue
            val times = mutableListOf<Double>()
            var lastIndex = 0
            for (match in TIME_TAG.findAll(line)) {
                val parts = match.value.removeSurrounding("[", "]").split(":")
                if (parts.size == 3) {
                    val fracParts = parts[2].split(".", ":")
                    times += toSeconds(parts[0], parts[1], fracParts[0], fracParts.getOrNull(1))
                } else {
                    val fracParts = parts[1].split(".", ":")
                    times += toSeconds(null, parts[0], fracParts[0], fracParts.getOrNull(1))
                }
                lastIndex = match.range.last + 1
            }
            if (times.isEmpty()) continue
            val text = line.substring(lastIndex).replace(WORD_TAG, "").trim()
            for (t in times) {
                val shifted = maxOf(0.0, Math.round((t + offsetMs / 1000.0) * 1000) / 1000.0)
                lines += LyricLine(shifted, text)
            }
        }
        lines.sortBy { it.t }
        val seen = mutableSetOf<String>()
        return lines.filter { seen.add("${it.t}\n${it.text}") }
    }

    /** Baris sinkron -> teks LRC (`[mm:ss.mmm]teks` per baris). */
    fun formatLrc(lines: List<LyricLine>): String =
        lines.joinToString("\n") { line ->
            val ms = (line.t * 1000).toLong()
            "[%02d:%02d.%03d]%s".format(ms / 60000, (ms / 1000) % 60, ms % 1000, line.text)
        }
}
