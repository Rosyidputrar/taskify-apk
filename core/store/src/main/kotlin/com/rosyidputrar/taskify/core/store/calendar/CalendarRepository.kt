package com.rosyidputrar.taskify.core.store.calendar

import com.powersync.PowerSyncDatabase
import com.rosyidputrar.taskify.core.domain.calendar.DayActivity
import com.rosyidputrar.taskify.core.domain.tasks.Task
import com.rosyidputrar.taskify.core.store.tasks.TASK_COLUMNS
import com.rosyidputrar.taskify.core.store.tasks.toTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.math.roundToInt

data class JournalItem(val id: String, val title: String, val mood: Int?, val time: String)
data class NoteItem(val id: String, val title: String, val pinned: Boolean)
data class TransactionItem(val id: String, val title: String, val amount: Long, val kind: String, val category: String?, val wallet: String?, val time: String)
data class VaultItem(val id: String, val title: String, val kind: String)

/** Isi panel satu hari (selain tugas). */
data class DayAgenda(
    val journal: List<JournalItem> = emptyList(),
    val notes: List<NoteItem> = emptyList(),
    val transactions: List<TransactionItem> = emptyList(),
    val vault: List<VaultItem> = emptyList(),
)

/** Modifier SQLite timestamp UTC → tanggal/jam WIB (WIB tetap UTC+7 tanpa DST). */
private const val WIB = "+7 hours"

/**
 * Query Kalender — port `useCalendarActivity` + panel hari webapp. Satu query `UNION ALL`
 * reaktif untuk semua modul non-tugas; tugas (termasuk kejadian virtual) digabung di domain.
 */
class CalendarRepository(private val db: PowerSyncDatabase) {

    fun watchActivity(from: String, to: String): Flow<Map<String, DayActivity>> = db.watch(
        """SELECT entry_date AS day, 'journal' AS kind, COUNT(*) AS count, AVG(mood) AS amount
             FROM journal_entries WHERE deleted_at IS NULL AND entry_date BETWEEN ?1 AND ?2 GROUP BY entry_date
           UNION ALL
           SELECT note_date, 'notes', COUNT(*), 0
             FROM notes WHERE deleted_at IS NULL AND note_date IS NOT NULL AND note_date BETWEEN ?1 AND ?2 GROUP BY note_date
           UNION ALL
           SELECT occurred_on, kind, COUNT(*), SUM(amount)
             FROM transactions WHERE deleted_at IS NULL AND debt_id IS NULL AND kind IN ('expense', 'income')
              AND occurred_on BETWEEN ?1 AND ?2 GROUP BY occurred_on, kind
           UNION ALL
           SELECT day, 'vault', COUNT(*), 0 FROM (
             SELECT date(created_at, ?3) AS day FROM snippets WHERE deleted_at IS NULL
             UNION ALL SELECT date(created_at, ?3) FROM code_projects WHERE deleted_at IS NULL
             UNION ALL SELECT date(created_at, ?3) FROM attachments WHERE deleted_at IS NULL AND entity_type = 'code_project'
           ) WHERE day BETWEEN ?1 AND ?2 GROUP BY day""",
        listOf(from, to, WIB),
    ) { c -> Row(c.getString(0)!!, c.getString(1)!!, c.getLong(2)?.toInt() ?: 0, c.getDouble(3)) }
        .map { rows ->
            val map = mutableMapOf<String, DayActivity>()
            rows.forEach { r ->
                val e = map[r.day] ?: DayActivity()
                map[r.day] = when (r.kind) {
                    "journal" -> e.copy(journal = e.journal + r.count, mood = r.amount?.roundToInt())
                    "notes" -> e.copy(notes = e.notes + r.count)
                    "expense" -> e.copy(expense = e.expense + (r.amount ?: 0.0).toLong(), transactions = e.transactions + r.count)
                    "income" -> e.copy(income = e.income + (r.amount ?: 0.0).toLong(), transactions = e.transactions + r.count)
                    "vault" -> e.copy(vault = e.vault + r.count)
                    else -> e
                }
            }
            map
        }

    private data class Row(val day: String, val kind: String, val count: Int, val amount: Double?)

    /** Tugas induk dengan due_date dalam rentang (tanpa yang dibatalkan). */
    fun watchTasksInRange(from: String, to: String): Flow<List<Task>> = db.watch(
        """SELECT $TASK_COLUMNS FROM tasks WHERE parent_id IS NULL AND deleted_at IS NULL AND status <> 'cancelled'
           AND due_date BETWEEN ? AND ? ORDER BY status = 'done', due_time IS NULL, due_time, priority DESC""",
        listOf(from, to),
    ) { it.toTask() }

    /** Tugas berulang aktif yang mulai sebelum/selama rentang — sumber kejadian virtual. */
    fun watchActiveRecurring(to: String): Flow<List<Task>> = db.watch(
        """SELECT $TASK_COLUMNS FROM tasks WHERE parent_id IS NULL AND deleted_at IS NULL AND status IN ('todo','in_progress')
           AND rrule IS NOT NULL AND due_date <= ?""",
        listOf(to),
    ) { it.toTask() }

    fun watchJournal(date: String): Flow<List<JournalItem>> = db.watch(
        """SELECT id, COALESCE(NULLIF(title, ''), substr(content, 1, 120)), mood, strftime('%H:%M', created_at, '$WIB')
           FROM journal_entries WHERE deleted_at IS NULL AND entry_date = ? ORDER BY is_pinned DESC, created_at""",
        listOf(date),
    ) { c -> JournalItem(c.getString(0)!!, clean(c.getString(1)), c.getLong(2)?.toInt(), c.getString(3) ?: "") }

    fun watchNotes(date: String): Flow<List<NoteItem>> = db.watch(
        """SELECT id, COALESCE(NULLIF(title, ''), substr(content, 1, 120)), is_pinned
           FROM notes WHERE deleted_at IS NULL AND note_date = ? ORDER BY is_pinned DESC, updated_at DESC""",
        listOf(date),
    ) { c -> NoteItem(c.getString(0)!!, clean(c.getString(1)), (c.getLong(2) ?: 0L) != 0L) }

    /** Semua transaksi hari itu (termasuk transfer & hutang — panel menampilkan riwayat lengkap). */
    fun watchTransactions(date: String): Flow<List<TransactionItem>> = db.watch(
        """SELECT t.id, COALESCE(NULLIF(t.title, ''), c.name, 'Transaksi'), t.amount, t.kind, c.name, w.name,
                  COALESCE(substr(t.occurred_time, 1, 5), '')
           FROM transactions t
           LEFT JOIN categories c ON c.id = t.category_id
           LEFT JOIN wallets w ON w.id = t.wallet_id
           WHERE t.deleted_at IS NULL AND t.occurred_on = ? ORDER BY t.occurred_time IS NULL, t.occurred_time, t.created_at""",
        listOf(date),
    ) { c -> TransactionItem(c.getString(0)!!, clean(c.getString(1)), c.getLong(2) ?: 0, c.getString(3) ?: "expense", c.getString(4), c.getString(5), c.getString(6) ?: "") }

    fun watchVault(date: String): Flow<List<VaultItem>> = db.watch(
        """SELECT id, title, 'snippet' FROM snippets WHERE deleted_at IS NULL AND date(created_at, ?2) = ?1
           UNION ALL SELECT id, name, 'project' FROM code_projects WHERE deleted_at IS NULL AND date(created_at, ?2) = ?1
           UNION ALL SELECT id, file_name, 'version' FROM attachments WHERE deleted_at IS NULL AND entity_type = 'code_project' AND date(created_at, ?2) = ?1""",
        listOf(date, WIB),
    ) { c -> VaultItem(c.getString(0)!!, clean(c.getString(1)), c.getString(2) ?: "snippet") }

    private fun clean(text: String?) = (text ?: "").replace(Regex("[#*_>`]+"), "").replace(Regex("\\s+"), " ").trim()
}
