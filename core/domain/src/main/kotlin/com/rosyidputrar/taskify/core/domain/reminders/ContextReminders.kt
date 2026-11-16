package com.rosyidputrar.taskify.core.domain.reminders

import com.rosyidputrar.taskify.core.domain.finance.BudgetLevel
import com.rosyidputrar.taskify.core.domain.finance.BudgetStatus
import com.rosyidputrar.taskify.core.domain.finance.Recurring
import com.rosyidputrar.taskify.core.domain.time.Dates
import java.time.Instant
import java.time.LocalTime

/**
 * Pengingat kontekstual di luar tugas: tagihan berulang yang jatuh tempo, anggaran yang hampir /
 * sudah habis, dan streak jurnal yang hampir putus. Semua keputusan di sini murni (teruji JVM);
 * worker hanya membaca data, memanggil fungsi ini, lalu menampilkan notifikasi.
 */
object ContextReminders {
    /** Kunci "sudah diberitahukan" untuk tagihan: satu kali per kejadian (seri + tanggal). */
    fun billKey(r: Recurring): String = "${r.id}|${r.nextRunOn}"

    /** Tagihan manual yang jatuh tempo dan BELUM pernah diberitahukan untuk kejadian ini. */
    fun billsToNotify(recurring: List<Recurring>, today: String, notified: Set<String>): List<Recurring> =
        recurring.filter { it.isDue(today) && billKey(it) !in notified }

    /** Kunci anggaran: per bulan & per level, jadi "hampir habis" lalu "terlampaui" masing-masing sekali. */
    fun budgetKey(b: BudgetStatus, month: String, level: BudgetLevel): String = "${b.id}|$month|${level.name}"

    /** Anggaran yang baru naik ke level Warn/Over bulan ini (yang sudah diberitahukan dilewati). */
    fun budgetAlerts(budgets: List<BudgetStatus>, month: String, notified: Set<String>): List<BudgetStatus> =
        budgets.filter { b ->
            b.amount > 0 && b.level != BudgetLevel.Ok && budgetKey(b, month, b.level) !in notified
        }

    /**
     * Ingatkan jurnal hanya bila ada streak yang akan putus (kemarin menulis, hari ini belum).
     * Tanpa streak tidak ada yang "hilang" — tidak perlu mengganggu.
     */
    fun shouldNudgeJournal(streak: Int, wroteToday: Boolean): Boolean = streak >= 1 && !wroteToday

    /** Jeda (ms) sampai jam [time] WIB berikutnya ("HH:mm"); bila sudah lewat hari ini → besok. */
    fun delayUntil(time: String, nowMillis: Long): Long {
        val t = runCatching { LocalTime.parse(time) }.getOrDefault(LocalTime.of(20, 0))
        val now = Instant.ofEpochMilli(nowMillis).atOffset(Dates.WIB)
        var next = now.toLocalDate().atTime(t).atOffset(Dates.WIB)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next.toInstant().toEpochMilli() - nowMillis
    }

    /** Hapus kunci lama (> 62 hari) supaya catatan "sudah diberitahukan" tidak tumbuh tanpa batas. */
    fun pruneKeys(keys: Set<String>, today: String): Set<String> {
        val cutoff = Dates.addDays(today, -62L)
        return keys.filterTo(mutableSetOf()) { key ->
            val date = Regex("\\d{4}-\\d{2}-\\d{2}").find(key)?.value
            date == null || date >= cutoff
        }
    }
}
