package com.rosyidputrar.taskify.core.store.review

import com.powersync.PowerSyncDatabase
import com.rosyidputrar.taskify.core.domain.review.DayPoint
import com.rosyidputrar.taskify.core.domain.review.HeatDay
import com.rosyidputrar.taskify.core.domain.review.PeriodStats
import com.rosyidputrar.taskify.core.domain.review.Range
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Pengeluaran terbesar dalam periode. */
data class BigExpense(val title: String, val amount: Long, val day: String)

/** Entri jurnal dengan mood tertinggi dalam periode. */
data class BestMood(val id: String, val title: String?, val day: String, val mood: Int)

/** Modifier SQLite timestamp UTC → tanggal WIB (sama dengan Kalender). */
private const val WIB = "+7 hours"

/**
 * Query Review & heatmap — SQL disalin dari `review-queries.ts` webapp supaya angka satu hari
 * sama di Kalender, Review, dan heatmap: tugas = induk saja; transaksi tanpa transfer & hutang;
 * vault = snippet + project + versi file project.
 */
class ReviewRepository(private val db: PowerSyncDatabase) {

    fun watchStats(r: Range): Flow<PeriodStats> = db.watch(STATS_SQL, listOf(r.from, r.to, WIB)) { c ->
        PeriodStats(
            tasksDone = c.getLong(0)?.toInt() ?: 0, tasksCreated = c.getLong(1)?.toInt() ?: 0,
            tasksDue = c.getLong(2)?.toInt() ?: 0, tasksDueDone = c.getLong(3)?.toInt() ?: 0,
            income = c.getLong(4) ?: 0, expense = c.getLong(5) ?: 0, txCount = c.getLong(6)?.toInt() ?: 0,
            journalEntries = c.getLong(7)?.toInt() ?: 0, journalDays = c.getLong(8)?.toInt() ?: 0, moodAvg = c.getDouble(9),
            snippetsNew = c.getLong(10)?.toInt() ?: 0, versionsNew = c.getLong(11)?.toInt() ?: 0,
        )
    }.map { it.firstOrNull() ?: PeriodStats() }

    /** Ringkasan per hari (tugas selesai, pengeluaran, jurnal, mood); hanya hari yang ada datanya. */
    fun watchDaily(r: Range): Flow<Map<String, DayPoint>> = db.watch(
        """SELECT date(completed_at, ?3) AS day, 'done' AS kind, COUNT(*) AS n, NULL AS v FROM tasks
            WHERE deleted_at IS NULL AND parent_id IS NULL AND status = 'done' AND date(completed_at, ?3) BETWEEN ?1 AND ?2
            GROUP BY day
           UNION ALL
           SELECT occurred_on, 'expense', COUNT(*), SUM(amount) FROM transactions
            WHERE deleted_at IS NULL AND debt_id IS NULL AND kind = 'expense' AND occurred_on BETWEEN ?1 AND ?2
            GROUP BY occurred_on
           UNION ALL
           SELECT entry_date, 'journal', COUNT(*), AVG(mood) FROM journal_entries
            WHERE deleted_at IS NULL AND entry_date BETWEEN ?1 AND ?2 GROUP BY entry_date""",
        listOf(r.from, r.to, WIB),
    ) { c -> Row(c.getString(0) ?: "", c.getString(1) ?: "", c.getLong(2)?.toInt() ?: 0, c.getDouble(3)) }
        .map { rows ->
            val map = mutableMapOf<String, DayPoint>()
            rows.forEach { row ->
                val p = map[row.day] ?: DayPoint(row.day)
                map[row.day] = when (row.kind) {
                    "done" -> p.copy(tasksDone = row.n)
                    "expense" -> p.copy(expense = (row.v ?: 0.0).toLong())
                    "journal" -> p.copy(journal = row.n, mood = row.v)
                    else -> p
                }
            }
            map
        }

    fun watchHeatmap(r: Range): Flow<Map<String, HeatDay>> = db.watch(HEATMAP_SQL, listOf(r.from, r.to, WIB)) { c ->
        Row(c.getString(0) ?: "", c.getString(1) ?: "", c.getLong(2)?.toInt() ?: 0, null)
    }.map { rows ->
        val map = mutableMapOf<String, HeatDay>()
        rows.forEach { row ->
            val d = map[row.day] ?: HeatDay(row.day)
            map[row.day] = when (row.kind) {
                "tasks" -> d.copy(tasks = d.tasks + row.n)
                "journal" -> d.copy(journal = d.journal + row.n)
                "notes" -> d.copy(notes = d.notes + row.n)
                "transactions" -> d.copy(transactions = d.transactions + row.n)
                "vault" -> d.copy(vault = d.vault + row.n)
                else -> d
            }
        }
        map
    }

    fun watchBigExpense(r: Range): Flow<BigExpense?> = db.watch(
        """SELECT COALESCE(NULLIF(t.title, ''), c.name, 'Pengeluaran'), t.amount, t.occurred_on FROM transactions t
             LEFT JOIN categories c ON c.id = t.category_id
            WHERE t.deleted_at IS NULL AND t.debt_id IS NULL AND t.kind = 'expense' AND t.occurred_on BETWEEN ?1 AND ?2
            ORDER BY t.amount DESC LIMIT 1""",
        listOf(r.from, r.to),
    ) { c -> BigExpense(c.getString(0) ?: "Pengeluaran", c.getLong(1) ?: 0, c.getString(2) ?: r.from) }.map { it.firstOrNull() }

    fun watchBestMood(r: Range): Flow<BestMood?> = db.watch(
        """SELECT id, title, entry_date, mood FROM journal_entries
            WHERE deleted_at IS NULL AND mood IS NOT NULL AND entry_date BETWEEN ?1 AND ?2
            ORDER BY mood DESC, entry_date DESC LIMIT 1""",
        listOf(r.from, r.to),
    ) { c -> BestMood(c.getString(0)!!, c.getString(1)?.takeIf { it.isNotBlank() }, c.getString(2) ?: r.from, c.getLong(3)?.toInt() ?: 0) }.map { it.firstOrNull() }

    private data class Row(val day: String, val kind: String, val n: Int, val v: Double?)

    companion object {
        const val STATS_SQL = """
  SELECT
    (SELECT COUNT(*) FROM tasks WHERE deleted_at IS NULL AND parent_id IS NULL AND status = 'done'
        AND date(completed_at, ?3) BETWEEN ?1 AND ?2)                                     AS tasks_done,
    (SELECT COUNT(*) FROM tasks WHERE deleted_at IS NULL AND parent_id IS NULL
        AND date(created_at, ?3) BETWEEN ?1 AND ?2)                                       AS tasks_created,
    (SELECT COUNT(*) FROM tasks WHERE deleted_at IS NULL AND parent_id IS NULL AND status <> 'cancelled'
        AND due_date BETWEEN ?1 AND ?2)                                                   AS tasks_due,
    (SELECT COUNT(*) FROM tasks WHERE deleted_at IS NULL AND parent_id IS NULL AND status = 'done'
        AND due_date BETWEEN ?1 AND ?2)                                                   AS tasks_due_done,
    (SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE deleted_at IS NULL AND debt_id IS NULL
        AND kind = 'income' AND occurred_on BETWEEN ?1 AND ?2)                            AS income,
    (SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE deleted_at IS NULL AND debt_id IS NULL
        AND kind = 'expense' AND occurred_on BETWEEN ?1 AND ?2)                           AS expense,
    (SELECT COUNT(*) FROM transactions WHERE deleted_at IS NULL AND debt_id IS NULL
        AND kind <> 'transfer' AND occurred_on BETWEEN ?1 AND ?2)                         AS tx_count,
    (SELECT COUNT(*) FROM journal_entries WHERE deleted_at IS NULL AND entry_date BETWEEN ?1 AND ?2) AS journal_entries,
    (SELECT COUNT(DISTINCT entry_date) FROM journal_entries WHERE deleted_at IS NULL
        AND entry_date BETWEEN ?1 AND ?2)                                                 AS journal_days,
    (SELECT AVG(mood) FROM journal_entries WHERE deleted_at IS NULL AND mood IS NOT NULL
        AND entry_date BETWEEN ?1 AND ?2)                                                 AS mood_avg,
    (SELECT COUNT(*) FROM snippets WHERE deleted_at IS NULL AND date(created_at, ?3) BETWEEN ?1 AND ?2) AS snippets_new,
    (SELECT COUNT(*) FROM attachments WHERE deleted_at IS NULL AND entity_type = 'code_project'
        AND date(created_at, ?3) BETWEEN ?1 AND ?2)                                       AS versions_new
"""

        const val HEATMAP_SQL = """SELECT date(completed_at, ?3) AS day, 'tasks' AS kind, COUNT(*) AS n FROM tasks
      WHERE deleted_at IS NULL AND parent_id IS NULL AND status = 'done'
        AND date(completed_at, ?3) BETWEEN ?1 AND ?2 GROUP BY day
     UNION ALL
     SELECT entry_date, 'journal', COUNT(*) FROM journal_entries
      WHERE deleted_at IS NULL AND entry_date BETWEEN ?1 AND ?2 GROUP BY entry_date
     UNION ALL
     SELECT note_date, 'notes', COUNT(*) FROM notes
      WHERE deleted_at IS NULL AND note_date IS NOT NULL AND note_date BETWEEN ?1 AND ?2
      GROUP BY note_date
     UNION ALL
     SELECT occurred_on, 'transactions', COUNT(*) FROM transactions
      WHERE deleted_at IS NULL AND debt_id IS NULL AND kind <> 'transfer'
        AND occurred_on BETWEEN ?1 AND ?2 GROUP BY occurred_on
     UNION ALL
     SELECT day, 'vault', COUNT(*) FROM (
       SELECT date(created_at, ?3) AS day FROM snippets WHERE deleted_at IS NULL
       UNION ALL SELECT date(created_at, ?3) FROM code_projects WHERE deleted_at IS NULL
       UNION ALL SELECT date(created_at, ?3) FROM attachments
        WHERE deleted_at IS NULL AND entity_type = 'code_project')
      WHERE day BETWEEN ?1 AND ?2 GROUP BY day"""
    }
}
