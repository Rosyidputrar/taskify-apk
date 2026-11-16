package com.rosyidputrar.taskify.core.domain.calendar

import com.rosyidputrar.taskify.core.common.Rupiah
import com.rosyidputrar.taskify.core.domain.journal.moodLabel
import com.rosyidputrar.taskify.core.domain.tasks.Task
import com.rosyidputrar.taskify.core.domain.tasks.TaskStatus
import com.rosyidputrar.taskify.core.domain.tasks.occurrencesBetween
import com.rosyidputrar.taskify.core.domain.time.Dates
import java.time.YearMonth

/**
 * Kalender = pusat aplikasi. Aturan hitung SAMA dengan webapp (Kalender, Review, heatmap):
 * hanya tugas induk; transaksi hanya pemasukan/pengeluaran biasa (tanpa transfer & hutang);
 * catatan hanya yang ditempel ke tanggal; vault = snippet + project + versi file baru.
 */
data class DayActivity(
    val tasks: Int = 0,
    val tasksDone: Int = 0,
    val journal: Int = 0,
    val notes: Int = 0,
    /** Rata-rata mood jurnal hari itu (1–5), dibulatkan. */
    val mood: Int? = null,
    val expense: Long = 0,
    val income: Long = 0,
    val transactions: Int = 0,
    val vault: Int = 0,
) {
    val isEmpty: Boolean get() = tasks == 0 && journal == 0 && notes == 0 && transactions == 0 && vault == 0
}

data class MonthCell(val date: String, val day: Int, val inMonth: Boolean)

/** Grid bulan mulai Senin, 35 atau 42 sel. [month] = tanggal mana pun di bulan itu. */
fun buildMonthGrid(month: String): List<MonthCell> {
    val ym = YearMonth.from(Dates.parse(month))
    val first = ym.atDay(1)
    val start = first.minusDays((first.dayOfWeek.value - 1).toLong())
    val last = ym.atEndOfMonth()
    val end = last.plusDays((7 - last.dayOfWeek.value).toLong())
    return generateSequence(start) { it.plusDays(1) }
        .takeWhile { !it.isAfter(end) }
        .map { MonthCell(it.toString(), it.dayOfMonth, YearMonth.from(it) == ym) }
        .toList()
}

/** 7 hari mulai Senin pada minggu [day]. */
fun buildWeek(day: String): List<String> {
    val d = Dates.parse(day)
    val monday = d.minusDays((d.dayOfWeek.value - 1).toLong())
    return (0L..6L).map { monday.plusDays(it).toString() }
}

fun firstOfMonth(day: String): String = Dates.monthStart(day)

fun shiftMonth(month: String, amount: Long): String = YearMonth.from(Dates.parse(month)).plusMonths(amount).atDay(1).toString()

val WEEKDAY_LABELS = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")

/** Satu tugas di kalender: baris nyata, atau kejadian mendatang tugas berulang (virtual). */
data class CalendarTaskItem(val task: Task, val date: String, val virtual: Boolean)

/**
 * Tugas per hari dalam [from, to]: baris nyata + kejadian berulang VIRTUAL (dihitung dari rrule,
 * tidak disimpan). Kejadian virtual dimulai setelah due_date baris aktif dan tidak di masa lalu.
 * Urutan per hari: belum selesai → berjam (urut jam) → prioritas.
 */
fun calendarTasks(
    real: List<Task>,
    recurring: List<Task>,
    from: String,
    to: String,
    today: String,
): Map<String, List<CalendarTaskItem>> {
    val map = sortedMapOf<String, MutableList<CalendarTaskItem>>()
    fun add(item: CalendarTaskItem) { map.getOrPut(item.date) { mutableListOf() } += item }
    real.forEach { t -> t.dueDate?.let { add(CalendarTaskItem(t, it, virtual = false)) } }
    recurring.forEach { t ->
        val due = t.dueDate ?: return@forEach
        val rule = t.rrule ?: return@forEach
        val start = maxOf(Dates.addDays(due, 1), from, today)
        if (start > to) return@forEach
        occurrencesBetween(rule, due, start, to, 100).forEach { add(CalendarTaskItem(t, it, virtual = true)) }
    }
    map.values.forEach { items ->
        items.sortWith(
            compareBy<CalendarTaskItem>({ if (it.task.status == TaskStatus.Done) 1 else 0 }, { it.task.dueTime ?: "99" }, { -it.task.priority }),
        )
    }
    return map
}

/** Gabungkan aktivitas non-tugas (dari SQL) dengan jumlah tugas per hari. */
fun mergeActivity(other: Map<String, DayActivity>, tasks: Map<String, List<CalendarTaskItem>>): Map<String, DayActivity> {
    val result = other.toMutableMap()
    tasks.forEach { (day, items) ->
        val entry = result[day] ?: DayActivity()
        result[day] = entry.copy(tasks = items.size, tasksDone = items.count { it.task.status == TaskStatus.Done })
    }
    return result
}

/** Ringkasan teks untuk pembaca layar, mis. "3 tugas (1 selesai), jurnal (mood Baik), 2 transaksi". */
fun describeActivity(a: DayActivity): String = buildList {
    if (a.tasks > 0) add("${a.tasks} tugas" + if (a.tasksDone > 0) " (${a.tasksDone} selesai)" else "")
    if (a.journal > 0) add(moodLabel(a.mood)?.let { "jurnal (mood $it)" } ?: "jurnal")
    if (a.notes > 0) add("${a.notes} catatan")
    if (a.transactions > 0) add("${a.transactions} transaksi" + if (a.expense > 0) ", pengeluaran ${Rupiah.format(a.expense)}" else "")
    if (a.vault > 0) add("vault")
}.joinToString(", ")
