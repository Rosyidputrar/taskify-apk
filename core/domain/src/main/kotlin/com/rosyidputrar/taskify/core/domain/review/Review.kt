package com.rosyidputrar.taskify.core.domain.review

import com.rosyidputrar.taskify.core.common.Rupiah
import com.rosyidputrar.taskify.core.domain.calendar.buildWeek
import com.rosyidputrar.taskify.core.domain.journal.moodLabel
import com.rosyidputrar.taskify.core.domain.time.Dates
import kotlin.math.floor
import kotlin.math.roundToInt

/** Review mingguan/bulanan — port `review-queries.ts` + ringkasan `ReviewPage.tsx` webapp. */
enum class ReviewPeriod(val label: String, val unit: String) { Week("Mingguan", "minggu"), Month("Bulanan", "bulan") }

data class Range(val from: String, val to: String) {
    val days: Int get() = (Dates.diffDays(from, to) + 1).toInt()
    operator fun contains(day: String) = day in from..to
}

data class PeriodRanges(val current: Range, val previous: Range)

/** Ringkasan satu periode. Keuangan mengikuti modul Keuangan: tanpa transfer & transaksi hutang. */
data class PeriodStats(
    val tasksDone: Int = 0,
    val tasksCreated: Int = 0,
    val tasksDue: Int = 0,
    val tasksDueDone: Int = 0,
    val income: Long = 0,
    val expense: Long = 0,
    val txCount: Int = 0,
    val journalEntries: Int = 0,
    val journalDays: Int = 0,
    val moodAvg: Double? = null,
    val snippetsNew: Int = 0,
    val versionsNew: Int = 0,
)

data class DayPoint(val day: String, val tasksDone: Int = 0, val expense: Long = 0, val journal: Int = 0, val mood: Double? = null)

/** Satu hari di heatmap: skor = tugas selesai + jurnal + catatan bertanggal + transaksi + vault baru. */
data class HeatDay(val day: String, val tasks: Int = 0, val journal: Int = 0, val notes: Int = 0, val transactions: Int = 0, val vault: Int = 0) {
    val score: Int get() = tasks + journal + notes + transactions + vault

    fun describe(): String = "$tasks tugas selesai, $journal jurnal, $notes catatan, $transactions transaksi, $vault vault"
}

/** Kolom heatmap (minggu, Senin–Minggu); [max] = skor hari tersibuk dalam rentang. */
data class HeatGrid(val start: String, val end: String, val columns: List<List<String>>, val max: Int, val total: Int, val activeDays: Int)

object Review {
    /** Rentang periode yang memuat [anchor] + periode sebelumnya yang setara. Minggu = Senin–Minggu. */
    fun periodRange(period: ReviewPeriod, anchor: String): PeriodRanges = when (period) {
        ReviewPeriod.Week -> {
            val week = buildWeek(anchor)
            val from = week.first()
            PeriodRanges(Range(from, week.last()), Range(Dates.addDays(from, -7), Dates.addDays(from, -1)))
        }
        ReviewPeriod.Month -> {
            val from = Dates.monthStart(anchor)
            val prev = Dates.parse(from).minusMonths(1).toString()
            PeriodRanges(Range(from, Dates.monthEnd(from)), Range(prev, Dates.monthEnd(prev)))
        }
    }

    fun shift(period: ReviewPeriod, anchor: String, dir: Int): String =
        if (period == ReviewPeriod.Week) Dates.addDays(anchor, dir * 7L) else Dates.parse(anchor).plusMonths(dir.toLong()).toString()

    fun label(period: ReviewPeriod, range: Range): String =
        if (period == ReviewPeriod.Week) "${Dates.format(range.from, "d MMM")} – ${Dates.format(range.to, "d MMM yyyy")}"
        else Dates.format(range.from, "MMMM yyyy")

    /** Hari yang sudah berjalan dalam periode (periode berjalan dihitung sampai hari ini). */
    fun elapsedDays(range: Range, today: String): Int {
        val isCurrent = today in range
        return minOf(Dates.diffDays(range.from, if (isCurrent) today else range.to).toInt() + 1, range.days)
    }

    /** Perubahan persen terhadap periode sebelumnya; null jika tidak bermakna (sebelumnya 0). */
    fun percentChange(current: Number, previous: Number): Int? {
        val c = current.toDouble()
        val p = previous.toDouble()
        if (p == 0.0) return if (c == 0.0) 0 else null
        return jsRound((c - p) / p * 100)
    }

    /** Math.round JavaScript (setengah ke atas, juga untuk negatif). */
    private fun jsRound(x: Double): Int = floor(x + 0.5).toInt()

    /** Level 0–4 relatif terhadap hari tersibuk dalam rentang yang ditampilkan. */
    fun heatLevel(score: Int, max: Int): Int {
        if (score <= 0) return 0
        val ratio = score.toDouble() / maxOf(1, max)
        return when {
            ratio > 0.75 -> 4
            ratio > 0.5 -> 3
            ratio > 0.25 -> 2
            else -> 1
        }
    }

    /** Rentang heatmap: [weeks] minggu penuh yang berakhir di minggu [today]. */
    fun heatRange(today: String, weeks: Int = 26): Range {
        val lastWeek = buildWeek(today)
        return Range(Dates.addDays(lastWeek.first(), -(weeks - 1) * 7L), lastWeek.last())
    }

    fun heatGrid(today: String, byDay: Map<String, HeatDay>, weeks: Int = 26): HeatGrid {
        val r = heatRange(today, weeks)
        val columns = List(weeks) { w -> List(7) { d -> Dates.addDays(r.from, w * 7L + d) } }
        val inRange = byDay.values.filter { it.day in r }
        return HeatGrid(r.from, r.to, columns, inRange.maxOfOrNull { it.score } ?: 0, inRange.sumOf { it.score }, inRange.count { it.score > 0 })
    }

    /** Titik harian lengkap untuk rentang (hari tanpa data = nol). */
    fun fillDays(range: Range, points: Map<String, DayPoint>): List<DayPoint> =
        List(range.days) { i -> Dates.addDays(range.from, i.toLong()).let { points[it] ?: DayPoint(it) } }

    /** Kalimat ringkasan periode (sama dengan webapp). */
    fun summary(now: PeriodStats, before: PeriodStats, period: ReviewPeriod, elapsed: Int): String {
        val unit = period.unit
        val doneDelta = percentChange(now.tasksDone, before.tasksDone)
        val expDelta = percentChange(now.expense, before.expense)
        fun signed(d: Int) = (if (d >= 0) "+" else "") + d
        val parts = listOf(
            "Kamu menyelesaikan ${now.tasksDone} tugas" + if (doneDelta != null && before.tasksDone != 0) " (${signed(doneDelta)}% dari $unit lalu)" else "",
            "berjurnal ${now.journalDays} dari $elapsed hari",
            "mengeluarkan ${Rupiah.format(now.expense)}" + if (expDelta != null && before.expense != 0L) " (${signed(expDelta)}%)" else "",
        )
        val sb = StringBuilder(parts.joinToString(", ") + ".")
        moodLabel(now.moodAvg)?.let { sb.append(" Rata-rata perasaanmu ${it.lowercase()}.") }
        if (now.snippetsNew + now.versionsNew > 0) sb.append(" Vault bertambah ${now.snippetsNew} snippet & ${now.versionsNew} versi project.")
        return sb.toString()
    }

    /** "Tepat jadwal": persen tugas ber-tenggat yang selesai; null bila tidak ada tenggat. */
    fun onTimePercent(s: PeriodStats): Int? = if (s.tasksDue == 0) null else (s.tasksDueDone * 100.0 / s.tasksDue).roundToInt()

    /** Hari paling produktif (tugas selesai terbanyak; seri → hari paling awal). */
    fun bestDay(points: List<DayPoint>): DayPoint? = points.fold(null as DayPoint?) { b, p -> if (p.tasksDone > (b?.tasksDone ?: 0)) p else b }
}
