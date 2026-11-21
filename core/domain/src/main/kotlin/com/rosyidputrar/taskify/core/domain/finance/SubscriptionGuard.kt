package com.rosyidputrar.taskify.core.domain.finance

import com.rosyidputrar.taskify.core.common.Rupiah
import com.rosyidputrar.taskify.core.domain.tasks.MORNING
import com.rosyidputrar.taskify.core.domain.tasks.RecurrenceFreq
import com.rosyidputrar.taskify.core.domain.tasks.parseRule
import com.rosyidputrar.taskify.core.domain.time.Dates
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

/** Satu tagihan / langganan aktif yang akan segera jatuh tempo. */
data class UpcomingSubscription(
    val recurring: Recurring,
    val nextDate: String,
    val daysUntil: Long,
)

/** Ringkasan Penjaga Langganan: burn rate pengeluaran rutin & daftar jatuh tempo dekat. */
data class SubscriptionBurn(
    val monthlyExpense: Long = 0,
    val yearlyExpense: Long = 0,
    val activeExpenseCount: Int = 0,
    val upcoming: List<UpcomingSubscription> = emptyList(),
)

/** Rencana tugas + alarm untuk meninjau / membatalkan langganan sebelum auto-debit. */
data class CancelReminderPlan(
    val title: String,
    val dueDate: String,
    val remindDate: String,
    val remindAt: String,
    val notes: String,
)

/** Konversi nominal seri berulang menjadi estimasi biaya per bulan (Rp). */
fun monthlyEquivalent(amount: Long, rrule: String): Long {
    if (amount <= 0) return 0
    val state = parseRule(rrule)
    val interval = state.interval.coerceAtLeast(1).toDouble()
    val perMonth = when (state.freq) {
        RecurrenceFreq.Daily -> amount * (30.0 / interval)
        RecurrenceFreq.Weekdays -> amount * (21.67 / interval)
        RecurrenceFreq.Weekly -> amount * (4.3333 / interval)
        RecurrenceFreq.Monthly -> amount / interval
        RecurrenceFreq.Yearly -> amount / (12.0 * interval)
        RecurrenceFreq.None -> 0.0
    }
    return perMonth.roundToLong()
}

/**
 * Hitung burn rate pengeluaran rutin (bulanan & tahunan) serta daftar langganan/tagihan
 * yang akan jatuh tempo dalam [withinDays] hari ke depan.
 */
fun summarizeSubscriptions(
    items: List<Recurring>,
    today: String,
    withinDays: Long = 14,
): SubscriptionBurn {
    val activeExpenses = items.filter { it.active && it.kind == TxKind.Expense }
    if (activeExpenses.isEmpty()) return SubscriptionBurn()
    val monthly = activeExpenses.sumOf { monthlyEquivalent(it.amount, it.rrule) }
    val todayDate = Dates.parseOrNull(today)
    val upcoming = if (todayDate == null) {
        emptyList()
    } else {
        activeExpenses.mapNotNull { r ->
            // Tagihan manual yang sudah jatuh tempo hari ini/sebelumnya tampil di DueCard (di atasnya),
            // jadi jangan diduplikasi di daftar "Segera jatuh tempo".
            if (r.isDue(today)) return@mapNotNull null
            val next = r.nextRunOn ?: return@mapNotNull null
            val nextDate = Dates.parseOrNull(next) ?: return@mapNotNull null
            val days = ChronoUnit.DAYS.between(todayDate, nextDate)
            if (days in 0..withinDays) UpcomingSubscription(r, next, days) else null
        }.sortedWith(compareBy<UpcomingSubscription> { it.daysUntil }.thenByDescending { it.recurring.amount })
    }
    return SubscriptionBurn(
        monthlyExpense = monthly,
        yearlyExpense = monthly * 12,
        activeExpenseCount = activeExpenses.size,
        upcoming = upcoming,
    )
}

/**
 * Susun rencana Tugas + Alarm pengingat sebelum tanggal jatuh tempo/perpanjangan langganan.
 * Jika tanggal H-[daysBefore] sudah lewat dari [today], pengingat dipasang hari ini pukul 09:00.
 */
fun buildCancelReminderPlan(
    title: String,
    amount: Long,
    nextDate: String,
    daysBefore: Long,
    today: String,
    autoPost: Boolean = false,
): CancelReminderPlan? {
    if (Dates.parseOrNull(nextDate) == null || daysBefore < 0) return null
    val targetRemind = Dates.addDays(nextDate, -daysBefore)
    val clampedDate = if (targetRemind < today) today else targetRemind
    val remindAt = Dates.localDateTimeToIso(clampedDate, MORNING)
    val cleanTitle = title.trim().ifEmpty { "Langganan" }
    val lead = if (daysBefore == 0L) "hari jatuh tempo" else "H-$daysBefore sebelum jatuh tempo"
    val mode = if (autoPost) "catat otomatis aktif" else "tagihan manual"
    return CancelReminderPlan(
        title = "Tinjau / batalkan $cleanTitle (${Rupiah.format(amount)})",
        dueDate = nextDate,
        remindDate = clampedDate,
        remindAt = remindAt,
        notes = "Pengingat langganan ($lead, $mode). Jatuh tempo: ${Dates.format(nextDate, "d MMMM yyyy")}.",
    )
}
