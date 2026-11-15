package com.rosyidputrar.taskify.core.domain.finance

import com.rosyidputrar.taskify.core.common.Hashes
import com.rosyidputrar.taskify.core.domain.tasks.nextOccurrence
import com.rosyidputrar.taskify.core.domain.tasks.occurrencesBetween

/**
 * Jadwal transaksi berulang (port `features/finance/recurring.ts`). `rrule` memakai format yang
 * sama dengan tugas berulang dengan DTSTART = start_on; `next_run_on` = kejadian berikutnya yang
 * belum dicatat (null = seri selesai).
 */
data class RecurringSchedule(val rrule: String, val startOn: String, val endOn: String?, val nextRunOn: String? = null)

/** Kejadian pertama mulai [from] (inklusif), dibatasi end_on. */
fun firstOccurrenceFrom(s: RecurringSchedule, from: String): String? =
    occurrencesBetween(s.rrule, s.startOn, from, s.endOn ?: "9999-12-31", 1).firstOrNull()

/** Kejadian setelah [after], menghormati end_on. */
fun occurrenceAfter(s: RecurringSchedule, after: String): String? {
    val next = nextOccurrence(s.rrule, s.startOn, after)
    return next?.takeIf { s.endOn == null || it <= s.endOn }
}

data class DueOccurrences(val dates: List<String>, val next: String?)

/** Semua kejadian jatuh tempo (≤ today) yang belum dicatat, + next_run_on baru. */
fun dueOccurrences(s: RecurringSchedule, today: String, max: Int = 60): DueOccurrences {
    val nextRun = s.nextRunOn
    if (nextRun == null || nextRun > today) return DueOccurrences(emptyList(), nextRun)
    val until = if (s.endOn != null && s.endOn < today) s.endOn else today
    val dates = occurrencesBetween(s.rrule, s.startOn, nextRun, until, max)
    val last = dates.lastOrNull() ?: nextRun
    return DueOccurrences(dates, occurrenceAfter(s, last))
}

/**
 * UUID deterministik (format v8) dari string via SHA-256 — byte-identik dengan webapp, sehingga
 * dua perangkat yang sama-sama memproses saat offline menghasilkan baris yang sama (tidak dobel).
 */
fun deterministicUuid(input: String): String {
    val b = Hashes.sha256(input.toByteArray(Charsets.UTF_8)).copyOf(16)
    b[6] = ((b[6].toInt() and 0x0f) or 0x80).toByte()
    b[8] = ((b[8].toInt() and 0x3f) or 0x80).toByte()
    val hex = Hashes.hex(b)
    return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
}

fun recurringTxId(recurringId: String, date: String) = deterministicUuid("taskify:recurring:$recurringId:$date")
fun debtOpenTxId(debtId: String) = deterministicUuid("taskify:debt-open:$debtId")
fun debtSettleTxId(debtId: String) = deterministicUuid("taskify:debt-settle:$debtId")
