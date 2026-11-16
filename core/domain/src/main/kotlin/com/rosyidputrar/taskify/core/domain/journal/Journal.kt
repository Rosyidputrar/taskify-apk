package com.rosyidputrar.taskify.core.domain.journal

import com.rosyidputrar.taskify.core.domain.time.Dates
import java.time.LocalDate
import kotlin.math.roundToInt

/** Satu entri `journal_entries`. Satu tanggal boleh punya banyak entri. */
data class JournalEntry(
    val id: String,
    val entryDate: String,
    val title: String?,
    val content: String,
    val mood: Int?,
    val pinned: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
)

/** Pancingan menulis (placeholder editor), sama dengan webapp. */
val JOURNAL_PROMPTS = listOf(
    "Apa yang paling kamu syukuri hari ini?",
    "Apa yang berjalan baik hari ini, dan apa yang ingin diperbaiki?",
    "Satu hal kecil yang membuatmu tersenyum hari ini…",
    "Apa yang sedang ada di pikiranmu?",
    "Apa pelajaran dari hari ini?",
)

/** Pancingan tetap per tanggal (tidak berganti tiap kali layar digambar ulang). */
fun promptFor(date: String): String = JOURNAL_PROMPTS[Math.floorMod(date.hashCode(), JOURNAL_PROMPTS.size)]

enum class ReflectionKind { Week, Month }

data class Draft(val title: String, val content: String)

/** Templat refleksi dari Review (port `reflectionTemplate` webapp). */
fun reflectionTemplate(kind: ReflectionKind, from: String, to: String): Draft {
    val monthly = kind == ReflectionKind.Month
    val label = if (monthly) Dates.format(from, "MMMM yyyy") else "${Dates.format(from, "d MMM")} – ${Dates.format(to, "d MMM yyyy")}"
    val unit = if (monthly) "bulan" else "minggu"
    return Draft(
        title = "Refleksi $unit $label",
        content = listOf(
            "### Yang berjalan baik", "- ", "",
            "### Yang bisa diperbaiki", "- ", "",
            "### Pelajaran", "- ", "",
            "### Fokus $unit depan", "- [ ] ", "",
        ).joinToString("\n"),
    )
}

/** Streak = hari berturut-turut berjurnal sampai hari ini (atau kemarin, agar belum "putus"). */
fun journalStreak(days: Set<String>, today: String): Int {
    var day = if (today in days) today else Dates.addDays(today, -1)
    var count = 0
    while (day in days) {
        count++
        day = Dates.addDays(day, -1)
    }
    return count
}

data class MoodPoint(val day: String, val mood: Double?, val entries: Int)

/** Lengkapi deret per hari [days] hari terakhir; hari tanpa jurnal = mood null. */
fun moodSeries(rows: List<MoodPoint>, today: String, days: Int): List<MoodPoint> {
    val from = Dates.addDays(today, -(days - 1).toLong())
    val map = rows.associateBy { it.day }
    return (0 until days).map { i ->
        val day = Dates.addDays(from, i.toLong())
        map[day] ?: MoodPoint(day, null, 0)
    }
}

data class MoodStats(
    val average: Double?,
    val daysWritten: Int,
    val daysWithMood: Int,
    /** Jumlah hari per mood 1–5 (indeks 0 = mood 1). */
    val distribution: List<Int>,
    /** Rata-rata mood per hari dalam minggu, Senin (0) … Minggu (6); null = belum ada data. */
    val byWeekday: List<Double?>,
)

fun moodStats(series: List<MoodPoint>): MoodStats {
    val withMood = series.filter { it.mood != null }
    val average = if (withMood.isEmpty()) null else withMood.sumOf { it.mood!! } / withMood.size
    val distribution = (1..5).map { m -> withMood.count { it.mood!!.roundToInt() == m } }
    val byWeekday = (0..6).map { wd ->
        val moods = withMood.filter { Dates.weekdayOf(it.day) == wd }.map { it.mood!! }
        if (moods.isEmpty()) null else moods.average()
    }
    return MoodStats(average, series.count { it.entries > 0 }, withMood.size, distribution, byWeekday)
}

/** Tepat satu bulan lalu; null bila tanggalnya tidak ada di bulan lalu (mis. 31 Maret → 31 Feb). */
fun monthAgo(today: String): String? {
    val d = LocalDate.parse(today)
    val prev = d.minusMonths(1)
    return if (prev.dayOfMonth == d.dayOfMonth) prev.toString() else null
}

/** "2 tahun lalu" / "1 bulan lalu" untuk kartu "Hari ini di masa lalu". */
fun agoLabel(entryDate: String, today: String): String {
    val years = Math.round(Dates.diffDays(entryDate, today) / 365.0).toInt()
    return if (years >= 1) "$years tahun lalu" else "1 bulan lalu"
}

/** Ekspor semua jurnal menjadi satu file Markdown (format sama dengan webapp). */
fun journalToMarkdown(entries: List<JournalEntry>): String = entries.joinToString("\n---\n\n") { e ->
    val head = "## ${e.entryDate}" + (e.title?.let { " — $it" } ?: "")
    val mood = e.mood?.let { "\n_Mood: ${moodLabel(it)}_\n" } ?: ""
    "$head\n$mood\n${e.content.trim()}\n"
}
