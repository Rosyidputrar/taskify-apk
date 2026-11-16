package com.rosyidputrar.taskify.core.store.home

import com.powersync.PowerSyncDatabase
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.domain.journal.journalStreak
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Ringkasan Beranda dalam satu query reaktif (port `useTodaySummary` webapp + mood & streak). */
data class TodaySummary(
    val tasksToday: Int = 0,
    val tasksTodayDone: Int = 0,
    val tasksOverdue: Int = 0,
    val expenseToday: Long = 0,
    val expenseMonth: Long = 0,
    val incomeMonth: Long = 0,
    val balance: Long = 0,
    val journalToday: Int = 0,
    val moodToday: Double? = null,
)

enum class ActivityKind { Task, Journal, Note, Transaction }

/** Satu baris lini waktu "Aktivitas hari ini". */
data class ActivityItem(
    val kind: ActivityKind,
    val id: String,
    val title: String,
    /** 'HH:MM' WIB, kosong bila tidak berjam. */
    val time: String,
    val done: Boolean = false,
    val amount: Long? = null,
    val isIncome: Boolean = false,
    val mood: Int? = null,
)

/**
 * Statistik mengabaikan transaksi hutang/piutang (`debt_id`) dan transfer, sama dengan webapp.
 * Saldo total = saldo awal semua dompet + pemasukan − pengeluaran.
 */
class HomeRepository(private val db: PowerSyncDatabase) {

    fun watchSummary(today: String): Flow<TodaySummary> = db.watch(
        """SELECT
            (SELECT COUNT(*) FROM tasks WHERE deleted_at IS NULL AND parent_id IS NULL AND due_date = ?1 AND status <> 'cancelled'),
            (SELECT COUNT(*) FROM tasks WHERE deleted_at IS NULL AND parent_id IS NULL AND due_date = ?1 AND status = 'done'),
            (SELECT COUNT(*) FROM tasks WHERE deleted_at IS NULL AND parent_id IS NULL AND due_date < ?1 AND status IN ('todo','in_progress')),
            (SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE deleted_at IS NULL AND debt_id IS NULL AND kind = 'expense' AND occurred_on = ?1),
            (SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE deleted_at IS NULL AND debt_id IS NULL AND kind = 'expense' AND occurred_on BETWEEN ?2 AND ?1),
            (SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE deleted_at IS NULL AND debt_id IS NULL AND kind = 'income' AND occurred_on BETWEEN ?2 AND ?1),
            (SELECT COALESCE(SUM(initial_balance), 0) FROM wallets WHERE deleted_at IS NULL)
              + (SELECT COALESCE(SUM(CASE kind WHEN 'income' THEN amount WHEN 'expense' THEN -amount ELSE 0 END), 0) FROM transactions WHERE deleted_at IS NULL),
            (SELECT COUNT(*) FROM journal_entries WHERE deleted_at IS NULL AND entry_date = ?1),
            (SELECT AVG(mood) FROM journal_entries WHERE deleted_at IS NULL AND entry_date = ?1 AND mood IS NOT NULL)""",
        listOf(today, Dates.monthStart(today)),
    ) { c ->
        TodaySummary(
            tasksToday = c.getLong(0)?.toInt() ?: 0,
            tasksTodayDone = c.getLong(1)?.toInt() ?: 0,
            tasksOverdue = c.getLong(2)?.toInt() ?: 0,
            expenseToday = c.getLong(3) ?: 0,
            expenseMonth = c.getLong(4) ?: 0,
            incomeMonth = c.getLong(5) ?: 0,
            balance = c.getLong(6) ?: 0,
            journalToday = c.getLong(7)?.toInt() ?: 0,
            moodToday = c.getDouble(8),
        )
    }.map { it.firstOrNull() ?: TodaySummary() }

    /** Tugas (jatuh tempo / selesai hari ini), jurnal, catatan bertanggal, dan transaksi hari ini. */
    fun watchActivity(today: String): Flow<List<ActivityItem>> = db.watch(
        """SELECT 'task', id, title, COALESCE(substr(due_time, 1, 5), ''), status = 'done', NULL, NULL
             FROM tasks WHERE parent_id IS NULL AND deleted_at IS NULL AND status <> 'cancelled'
              AND (due_date = ?1 OR (status = 'done' AND date(completed_at, '+7 hours') = ?1))
           UNION ALL
           SELECT 'journal', id, COALESCE(NULLIF(title, ''), substr(content, 1, 80)), strftime('%H:%M', created_at, '+7 hours'), 0, NULL, mood
             FROM journal_entries WHERE deleted_at IS NULL AND entry_date = ?1
           UNION ALL
           SELECT 'note', id, COALESCE(NULLIF(title, ''), substr(content, 1, 80)), strftime('%H:%M', created_at, '+7 hours'), 0, NULL, NULL
             FROM notes WHERE deleted_at IS NULL AND note_date = ?1
           UNION ALL
           SELECT 'transaction:' || kind, id, COALESCE(NULLIF(title, ''), 'Transaksi'), COALESCE(substr(occurred_time, 1, 5), strftime('%H:%M', created_at, '+7 hours')), 0, amount, NULL
             FROM transactions WHERE deleted_at IS NULL AND occurred_on = ?1 AND kind <> 'transfer'""",
        listOf(today),
    ) { c ->
        val kind = c.getString(0) ?: "task"
        ActivityItem(
            kind = when {
                kind == "task" -> ActivityKind.Task
                kind == "journal" -> ActivityKind.Journal
                kind == "note" -> ActivityKind.Note
                else -> ActivityKind.Transaction
            },
            id = c.getString(1)!!,
            title = (c.getString(2) ?: "").replace(Regex("\\s+"), " ").trim(),
            time = c.getString(3) ?: "",
            done = (c.getLong(4) ?: 0L) != 0L,
            amount = c.getLong(5),
            isIncome = kind == "transaction:income",
            mood = c.getLong(6)?.toInt(),
        )
    }.map { items -> items.sortedWith(compareBy({ it.time.isEmpty() }, { it.time })) }

    /** Streak jurnal: hari berturut-turut ada catatan; tidak putus sebelum sempat menulis hari ini. */
    fun watchJournalStreak(today: String): Flow<Int> = db.watch(
        "SELECT DISTINCT entry_date FROM journal_entries WHERE deleted_at IS NULL AND entry_date <= ? ORDER BY entry_date DESC LIMIT 400",
        listOf(today),
    ) { it.getString(0)!! }.map { dates -> journalStreak(dates.toSet(), today) }
}
