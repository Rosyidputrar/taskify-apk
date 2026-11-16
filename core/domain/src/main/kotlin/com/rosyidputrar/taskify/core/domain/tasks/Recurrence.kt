package com.rosyidputrar.taskify.core.domain.tasks

import com.rosyidputrar.taskify.core.domain.time.Dates
import java.time.LocalDate
import java.time.YearMonth

/**
 * Tugas berulang dengan RRULE (RFC 5545) — port dari `features/tasks/recurrence.ts`.
 *
 * Model "bergulir": dalam satu seri hanya SATU baris aktif yang membawa rrule; saat diselesaikan
 * baris itu menjadi riwayat dan baris baru dibuat untuk kejadian berikutnya. Kejadian masa depan
 * di kalender bersifat VIRTUAL (dihitung, tidak disimpan).
 *
 * Rule selalu dibuat dengan BY* eksplisit sehingga tidak bergantung DTSTART. Tanggal 29–31 jatuh
 * di HARI TERAKHIR bulan yang tidak punya tanggal itu (`BYMONTHDAY=28,29,30,31;BYSETPOS=-1`),
 * bukan dilewati seperti bawaan RFC 5545. Rule lama `BYMONTHDAY=31` dinormalkan saat dibaca.
 *
 * Mesin di bawah sengaja kecil: hanya bentuk rule yang dibuat Taskify (DAILY, WEEKLY+BYDAY,
 * MONTHLY+BYMONTHDAY(+BYSETPOS), YEARLY+BYMONTH+BYMONTHDAY, INTERVAL, UNTIL, COUNT) — hasilnya
 * diuji sama dengan rrule.js di webapp. String dengan bagian yang tidak dikenal → tidak berulang.
 */
enum class RecurrenceFreq { None, Daily, Weekdays, Weekly, Monthly, Yearly }

data class RecurrenceState(
    val freq: RecurrenceFreq = RecurrenceFreq.None,
    val interval: Int = 1,
    /** 0 = Senin … 6 = Minggu. */
    val byDay: List<Int> = emptyList(),
    val until: String? = null,
    /** Bulanan saja: tanggal yang dimaksud rule (1–31), dibawa apa adanya saat rule diedit. */
    val monthDay: Int? = null,
)

val NO_RECURRENCE = RecurrenceState()

val WEEKDAY_SHORT = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
private val WEEKDAY_CODES = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")

private enum class Freq { DAILY, WEEKLY, MONTHLY, YEARLY }

/** Bentuk terurai sebuah RRULE. Urutan [parts] dipertahankan untuk serialisasi kanonik. */
private data class Rule(
    val freq: Freq,
    val interval: Int = 1,
    val byDay: List<Int> = emptyList(),
    val byMonthDay: List<Int> = emptyList(),
    val byMonth: List<Int> = emptyList(),
    val bySetPos: List<Int> = emptyList(),
    val until: String? = null,
    val count: Int? = null,
)

private fun parseRuleString(rule: String): Rule? {
    val parts = rule.removePrefix("RRULE:").split(';').filter { it.isNotBlank() }.associate {
        val (k, v) = it.split('=', limit = 2).let { p -> p[0].uppercase() to p.getOrElse(1) { "" } }
        k to v
    }
    val freq = when (parts["FREQ"]) {
        "DAILY" -> Freq.DAILY
        "WEEKLY" -> Freq.WEEKLY
        "MONTHLY" -> Freq.MONTHLY
        "YEARLY" -> Freq.YEARLY
        else -> return null
    }
    val known = setOf("FREQ", "INTERVAL", "BYDAY", "BYMONTHDAY", "BYMONTH", "BYSETPOS", "UNTIL", "COUNT", "WKST")
    if (parts.keys.any { it !in known }) return null
    fun ints(key: String) = parts[key]?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
    val byDay = parts["BYDAY"]?.split(',')?.map { code -> WEEKDAY_CODES.indexOf(code.trim().uppercase()) } ?: emptyList()
    if (byDay.any { it < 0 }) return null // BYDAY berprefiks (mis. 1MO) tidak dibuat Taskify
    val until = parts["UNTIL"]?.let { u -> if (u.length >= 8) "${u.substring(0, 4)}-${u.substring(4, 6)}-${u.substring(6, 8)}" else null }
    return Rule(
        freq = freq,
        interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
        byDay = byDay.sorted(),
        byMonthDay = ints("BYMONTHDAY"),
        byMonth = ints("BYMONTH"),
        bySetPos = ints("BYSETPOS"),
        until = until,
        count = parts["COUNT"]?.toIntOrNull(),
    )
}

private fun monthEndDays(day: Int) = (28..day).toList()

/** "tanggal N" (29–31, atau 29 Feb tahunan) → "tanggal N atau hari terakhir bulan itu". */
private fun clampToMonthEnd(rule: Rule): Rule {
    if (rule.bySetPos.isNotEmpty() || rule.byMonthDay.size != 1) return rule
    val day = rule.byMonthDay[0]
    val clamp = when (rule.freq) {
        Freq.MONTHLY -> day in 29..31
        Freq.YEARLY -> day == 29 && rule.byMonth == listOf(2)
        else -> false
    }
    return if (clamp) rule.copy(byMonthDay = monthEndDays(day), bySetPos = listOf(-1)) else rule
}

/** Serialisasi kanonik, sama dengan rrule.js `optionsToString` untuk opsi yang dibuat buildRule. */
private fun Rule.toRuleString(): String = buildList {
    add("FREQ=${freq.name}")
    add("INTERVAL=$interval")
    if (byDay.isNotEmpty()) add("BYDAY=" + byDay.joinToString(",") { WEEKDAY_CODES[it] })
    if (byMonth.isNotEmpty()) add("BYMONTH=" + byMonth.joinToString(","))
    if (byMonthDay.isNotEmpty()) add("BYMONTHDAY=" + byMonthDay.joinToString(","))
    if (until != null) add("UNTIL=" + until.replace("-", "") + "T235959Z")
    if (bySetPos.isNotEmpty()) add("BYSETPOS=" + bySetPos.joinToString(","))
}.joinToString(";")

/** Bangun string RRULE dari state editor. [anchor] = due_date (untuk bulanan/tahunan). */
fun buildRule(state: RecurrenceState, anchor: String): String? {
    if (state.freq == RecurrenceFreq.None) return null
    val interval = state.interval.coerceAtLeast(1)
    val a = Dates.parse(anchor)
    val base = when (state.freq) {
        RecurrenceFreq.Daily -> Rule(Freq.DAILY, interval)
        RecurrenceFreq.Weekdays -> Rule(Freq.WEEKLY, 1, byDay = listOf(0, 1, 2, 3, 4))
        RecurrenceFreq.Weekly -> Rule(Freq.WEEKLY, interval, byDay = state.byDay.ifEmpty { listOf(Dates.weekdayOf(anchor)) }.distinct().sorted())
        RecurrenceFreq.Monthly -> Rule(
            Freq.MONTHLY, interval,
            byMonthDay = listOf(state.monthDay?.takeIf { it in 1..31 } ?: a.dayOfMonth),
        )
        RecurrenceFreq.Yearly -> Rule(Freq.YEARLY, interval, byMonth = listOf(a.monthValue), byMonthDay = listOf(a.dayOfMonth))
        RecurrenceFreq.None -> return null
    }
    return clampToMonthEnd(base.copy(until = state.until)).toRuleString()
}

/** Parse string RRULE ke state editor. String tak dikenal → tidak berulang. */
fun parseRule(rule: String?): RecurrenceState {
    if (rule.isNullOrBlank()) return NO_RECURRENCE
    val r = parseRuleString(rule) ?: return NO_RECURRENCE
    return when (r.freq) {
        Freq.DAILY -> RecurrenceState(RecurrenceFreq.Daily, r.interval, until = r.until)
        Freq.WEEKLY ->
            if (r.interval == 1 && r.byDay == listOf(0, 1, 2, 3, 4)) {
                RecurrenceState(RecurrenceFreq.Weekdays, 1, r.byDay, r.until)
            } else {
                RecurrenceState(RecurrenceFreq.Weekly, r.interval, r.byDay, r.until)
            }
        Freq.MONTHLY -> RecurrenceState(RecurrenceFreq.Monthly, r.interval, until = r.until, monthDay = r.byMonthDay.maxOrNull())
        Freq.YEARLY -> RecurrenceState(RecurrenceFreq.Yearly, r.interval, until = r.until)
    }
}

/**
 * Sesuaikan rule dengan tanggal jatuh tempo baru. Bila rule masih menghasilkan tanggal itu, rule
 * dibiarkan (rule akhir-bulan yang jatuh di 28 Feb tetap "akhir bulan"); bila tidak, dibangun ulang.
 */
fun retargetRule(rule: String?, anchor: String): String? {
    if (rule.isNullOrBlank()) return null
    if (occurrencesBetween(rule, anchor, anchor, anchor, 1).size == 1) return rule
    return buildRule(parseRule(rule).copy(monthDay = null), anchor)
}

/**
 * Semua kejadian berurutan mulai [dtstart]. Hanya tanggal ≥ dtstart yang cocok dengan rule
 * (sama dengan rrule.js: dtstart tidak otomatis ikut bila tidak cocok).
 */
private fun Rule.occurrences(dtstart: LocalDate): Sequence<LocalDate> = sequence {
    val r = clampToMonthEnd(this@occurrences)
    val untilDate = r.until?.let { LocalDate.parse(it) }
    var emitted = 0
    var period = 0L
    // Batas pengaman: 50 ribu periode (± 137 tahun harian).
    while (period < 50_000) {
        val candidates: List<LocalDate> = when (r.freq) {
            Freq.DAILY -> listOf(dtstart.plusDays(period * r.interval))
            Freq.WEEKLY -> {
                val weekStart = dtstart.minusDays((dtstart.dayOfWeek.value - 1).toLong()).plusWeeks(period * r.interval)
                r.byDay.ifEmpty { listOf(dtstart.dayOfWeek.value - 1) }.map { weekStart.plusDays(it.toLong()) }
            }
            Freq.MONTHLY -> {
                val ym = YearMonth.from(dtstart).plusMonths(period * r.interval)
                applySetPos(r.byMonthDay.ifEmpty { listOf(dtstart.dayOfMonth) }.mapNotNull { d -> dayIn(ym, d) }.distinct().sorted(), r.bySetPos)
            }
            Freq.YEARLY -> {
                val year = dtstart.year + period * r.interval
                val months = r.byMonth.ifEmpty { listOf(dtstart.monthValue) }
                val days = r.byMonthDay.ifEmpty { listOf(dtstart.dayOfMonth) }
                applySetPos(
                    months.flatMap { m -> days.mapNotNull { d -> dayIn(YearMonth.of(year.toInt(), m), d) } }.distinct().sorted(),
                    r.bySetPos,
                )
            }
        }
        for (date in candidates.sorted()) {
            if (date.isBefore(dtstart)) continue
            if (untilDate != null && date.isAfter(untilDate)) return@sequence
            yield(date)
            emitted++
            if (r.count != null && emitted >= r.count) return@sequence
        }
        period++
    }
}

private fun dayIn(ym: YearMonth, day: Int): LocalDate? = when {
    day in 1..ym.lengthOfMonth() -> ym.atDay(day)
    day < 0 && -day <= ym.lengthOfMonth() -> ym.atDay(ym.lengthOfMonth() + day + 1)
    else -> null
}

private fun applySetPos(set: List<LocalDate>, pos: List<Int>): List<LocalDate> {
    if (pos.isEmpty() || set.isEmpty()) return set
    return pos.mapNotNull { p -> if (p > 0) set.getOrNull(p - 1) else set.getOrNull(set.size + p) }.distinct().sorted()
}

/** Kejadian berikutnya SETELAH [after] (eksklusif). null = seri berakhir / rule tidak valid. */
fun nextOccurrence(rule: String, dtstart: String, after: String): String? {
    val r = parseRuleString(rule) ?: return null
    val a = Dates.parse(after)
    return r.occurrences(Dates.parse(dtstart)).firstOrNull { it.isAfter(a) }?.toString()
}

/**
 * Tanggal jatuh tempo berikutnya saat tugas berulang diselesaikan. Bila tugas sudah lama
 * terlambat, lompat ke kejadian pertama mulai hari ini (tidak menumpuk kejadian lampau).
 */
fun nextDueAfterCompletion(rule: String, dueDate: String, today: String): String? {
    val next = nextOccurrence(rule, dueDate, dueDate) ?: return null
    if (next >= today) return next
    return nextOccurrence(rule, dueDate, Dates.addDays(today, -1))
}

/** Semua kejadian dalam [from, to] (inklusif), dibatasi [limit]. */
fun occurrencesBetween(rule: String, dtstart: String, from: String, to: String, limit: Int = 400): List<String> {
    val r = parseRuleString(rule) ?: return emptyList()
    val f = Dates.parse(from)
    val t = Dates.parse(to)
    return r.occurrences(Dates.parse(dtstart))
        .dropWhile { it.isBefore(f) }
        .takeWhile { !it.isAfter(t) }
        .take(limit)
        .map { it.toString() }
        .toList()
}

/** Deskripsi bahasa Indonesia, mis. "Setiap minggu: Sen, Kam". */
fun describeRule(rule: String?, anchor: String? = null): String {
    val s = parseRule(rule)
    if (s.freq == RecurrenceFreq.None) return "Tidak berulang"
    fun every(unit: String) = if (s.interval > 1) "Setiap ${s.interval} $unit" else "Setiap $unit"
    val text = when (s.freq) {
        RecurrenceFreq.Daily -> every("hari")
        RecurrenceFreq.Weekdays -> "Setiap hari kerja"
        RecurrenceFreq.Weekly -> "${every("minggu")}: ${s.byDay.joinToString(", ") { WEEKDAY_SHORT[it] }}"
        RecurrenceFreq.Monthly -> {
            val day = s.monthDay ?: anchor?.let { Dates.parse(it).dayOfMonth }
            "${every("bulan")} tgl ${day ?: ""}" + if ((day ?: 0) > 28) " (atau hari terakhir bulan)" else ""
        }
        RecurrenceFreq.Yearly -> if (anchor != null) "${every("tahun")}, ${Dates.format(anchor, "d MMM")}" else every("tahun")
        RecurrenceFreq.None -> ""
    }
    return if (s.until != null) "$text · s.d. ${Dates.format(s.until, "d MMM yyyy")}" else text
}

