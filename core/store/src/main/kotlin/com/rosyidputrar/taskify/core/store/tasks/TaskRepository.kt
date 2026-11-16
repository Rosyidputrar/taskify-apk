package com.rosyidputrar.taskify.core.store.tasks

import com.powersync.db.SqlCursor
import com.powersync.db.internal.ConnectionContext
import com.rosyidputrar.taskify.core.common.Ids
import com.rosyidputrar.taskify.core.common.Limits
import com.rosyidputrar.taskify.core.common.Timestamps
import com.powersync.PowerSyncDatabase
import com.rosyidputrar.taskify.core.store.UserIdProvider
import com.rosyidputrar.taskify.core.store.require
import com.rosyidputrar.taskify.core.domain.tasks.Task
import com.rosyidputrar.taskify.core.domain.tasks.TaskStatus
import com.rosyidputrar.taskify.core.domain.tasks.nextDueAfterCompletion
import com.rosyidputrar.taskify.core.domain.tasks.shiftRemindAt
import com.rosyidputrar.taskify.core.domain.time.Dates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class SmartView(val label: String) {
    Today("Hari ini"), Upcoming("Mendatang"), Overdue("Terlambat"), All("Semua"), Done("Selesai"), Trash("Sampah")
}

/** Tampilan daftar tugas: tampilan pintar atau satu daftar (`task_lists`). */
sealed interface TaskView {
    data class Smart(val view: SmartView) : TaskView
    data class List(val listId: String) : TaskView
}

data class TaskList(val id: String, val name: String, val color: String?, val position: Double)

data class Progress(val done: Int, val total: Int) {
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
}

data class ViewCounts(val today: Int = 0, val upcoming: Int = 0, val overdue: Int = 0, val all: Int = 0, val trash: Int = 0)

data class NewTask(
    val title: String,
    val listId: String? = null,
    val parentId: String? = null,
    val dueDate: String? = null,
    val dueTime: String? = null,
    val priority: Int = 0,
    val notes: String? = null,
    val remindAt: String? = null,
    val rrule: String? = null,
)

/** Hasil centang tugas: pesan untuk snackbar + aksi Urungkan. */
data class ToggleOutcome(val message: String?, val undo: (suspend () -> Unit)?)

internal const val TASK_COLUMNS =
    "id, user_id, list_id, parent_id, series_id, title, notes, status, priority, due_date, due_time, remind_at, rrule, completed_at, position, created_at, updated_at, deleted_at"

internal fun SqlCursor.toTask() = Task(
    id = getString(0)!!,
    userId = getString(1) ?: "",
    listId = getString(2),
    parentId = getString(3),
    seriesId = getString(4),
    title = getString(5) ?: "",
    notes = getString(6),
    status = TaskStatus.from(getString(7)),
    priority = getLong(8)?.toInt() ?: 0,
    dueDate = getString(9),
    dueTime = getString(10),
    remindAt = getString(11),
    rrule = getString(12),
    completedAt = getString(13),
    position = getDouble(14) ?: 0.0,
    createdAt = getString(15) ?: "",
    updatedAt = getString(16) ?: "",
    deletedAt = getString(17),
)

private const val ACTIVE = "status IN ('todo','in_progress')"
private const val ACTIVE_ORDER = "due_date IS NULL, due_date, due_time IS NULL, due_time, priority DESC, position"

/**
 * Semua baca & tulis modul Tugas — port dari `task-queries.ts` + `task-actions.ts`.
 * Baca = query reaktif ke SQLite lokal; tulis = SQLite lokal → antrean sync. Operasi multi-baris
 * dibungkus satu transaksi (terunggah sebagai satu unit).
 */
class TaskRepository(private val db: PowerSyncDatabase, private val users: UserIdProvider) {

    // ------------------------------------------------------------------ baca

    fun watchView(view: TaskView, today: String): Flow<List<Task>> {
        val base = "SELECT $TASK_COLUMNS FROM tasks WHERE parent_id IS NULL"
        val (sql, params) = when (view) {
            is TaskView.List -> "$base AND deleted_at IS NULL AND $ACTIVE AND list_id = ? ORDER BY $ACTIVE_ORDER" to listOf(view.listId)
            is TaskView.Smart -> when (view.view) {
                SmartView.Today -> "$base AND deleted_at IS NULL AND status <> 'cancelled' AND due_date = ? ORDER BY status = 'done', $ACTIVE_ORDER" to listOf(today)
                SmartView.Upcoming -> "$base AND deleted_at IS NULL AND $ACTIVE AND due_date > ? ORDER BY $ACTIVE_ORDER" to listOf(today)
                SmartView.Overdue -> "$base AND deleted_at IS NULL AND $ACTIVE AND due_date < ? ORDER BY $ACTIVE_ORDER" to listOf(today)
                SmartView.All -> "$base AND deleted_at IS NULL AND $ACTIVE ORDER BY $ACTIVE_ORDER" to emptyList()
                SmartView.Done -> "$base AND deleted_at IS NULL AND status IN ('done','cancelled') ORDER BY COALESCE(completed_at, updated_at) DESC LIMIT 300" to emptyList()
                SmartView.Trash -> "$base AND deleted_at IS NOT NULL ORDER BY deleted_at DESC LIMIT 300" to emptyList()
            }
        }
        return db.watch(sql, params) { it.toTask() }
    }

    fun watchCounts(today: String): Flow<ViewCounts> = db.watch(
        """SELECT
             COALESCE(SUM(CASE WHEN deleted_at IS NULL AND $ACTIVE AND due_date = ?1 THEN 1 ELSE 0 END), 0),
             COALESCE(SUM(CASE WHEN deleted_at IS NULL AND $ACTIVE AND due_date > ?1 THEN 1 ELSE 0 END), 0),
             COALESCE(SUM(CASE WHEN deleted_at IS NULL AND $ACTIVE AND due_date < ?1 THEN 1 ELSE 0 END), 0),
             COALESCE(SUM(CASE WHEN deleted_at IS NULL AND $ACTIVE THEN 1 ELSE 0 END), 0),
             COALESCE(SUM(CASE WHEN deleted_at IS NOT NULL THEN 1 ELSE 0 END), 0)
           FROM tasks WHERE parent_id IS NULL""",
        listOf(today),
    ) { c -> ViewCounts(c.int(0), c.int(1), c.int(2), c.int(3), c.int(4)) }.map { it.firstOrNull() ?: ViewCounts() }

    fun watchLists(): Flow<List<TaskList>> = db.watch(
        "SELECT id, name, color, position FROM task_lists WHERE deleted_at IS NULL AND is_archived = 0 ORDER BY position, created_at",
    ) { c -> TaskList(c.getString(0)!!, c.getString(1) ?: "", c.getString(2), c.getDouble(3) ?: 0.0) }

    fun watchListCounts(): Flow<Map<String, Int>> = db.watch(
        """SELECT list_id, COUNT(*) FROM tasks WHERE deleted_at IS NULL AND parent_id IS NULL AND list_id IS NOT NULL
           AND $ACTIVE GROUP BY list_id""",
    ) { c -> c.getString(0)!! to c.int(1) }.map { it.toMap() }

    fun watchTask(id: String): Flow<Task?> =
        db.watch("SELECT $TASK_COLUMNS FROM tasks WHERE id = ? LIMIT 1", listOf(id)) { it.toTask() }.map { it.firstOrNull() }

    fun watchSubtasks(parentId: String): Flow<List<Task>> = db.watch(
        "SELECT $TASK_COLUMNS FROM tasks WHERE parent_id = ? AND deleted_at IS NULL ORDER BY position, created_at",
        listOf(parentId),
    ) { it.toTask() }

    /** Progres subtask per tugas induk. */
    fun watchSubtaskProgress(): Flow<Map<String, Progress>> = db.watch(
        """SELECT parent_id, COUNT(*), SUM(CASE WHEN status = 'done' THEN 1 ELSE 0 END)
           FROM tasks WHERE deleted_at IS NULL AND parent_id IS NOT NULL AND status <> 'cancelled' GROUP BY parent_id""",
    ) { c -> c.getString(0)!! to Progress(done = c.int(2), total = c.int(1)) }.map { it.toMap() }

    /** Fokus: tugas aktif jatuh tempo hari ini atau terlambat. */
    fun watchFocus(today: String, limit: Int = 7): Flow<List<Task>> = db.watch(
        """SELECT $TASK_COLUMNS FROM tasks WHERE parent_id IS NULL AND deleted_at IS NULL AND $ACTIVE AND due_date <= ?
           ORDER BY due_date, due_time IS NULL, due_time, priority DESC LIMIT ?""",
        listOf(today, limit),
    ) { it.toTask() }

    /** Tugas dengan pengingat yang belum lewat — sumber penjadwalan alarm lokal. */
    fun watchUpcomingReminders(fromIso: String): Flow<List<Task>> = db.watch(
        """SELECT $TASK_COLUMNS FROM tasks WHERE deleted_at IS NULL AND $ACTIVE AND remind_at IS NOT NULL
           AND replace(remind_at, ' ', 'T') >= ? ORDER BY remind_at LIMIT 400""",
        listOf(fromIso),
    ) { it.toTask() }

    /** Sekali baca: `id → remind_at` tugas aktif yang pengingatnya ≥ [fromIso] (urut paling dekat). */
    suspend fun upcomingReminders(fromIso: String): Map<String, String> = db.getAll(
        """SELECT id, remind_at FROM tasks WHERE deleted_at IS NULL AND $ACTIVE AND remind_at IS NOT NULL
           AND replace(remind_at, ' ', 'T') >= ? ORDER BY remind_at LIMIT 400""",
        listOf(fromIso),
    ) { it.getString(0)!! to it.getString(1)!! }.toMap()

    suspend fun get(id: String): Task? =
        db.getOptional("SELECT $TASK_COLUMNS FROM tasks WHERE id = ?", listOf(id)) { it.toTask() }

    // ------------------------------------------------------------------ tulis

    suspend fun create(input: NewTask): String {
        val userId = users.require()
        val id = Ids.newId()
        db.writeTransactionAsync { tx -> tx.insertTask(id, userId, input, seriesId = null, status = TaskStatus.Todo) }
        return id
    }

    /**
     * Perbarui kolom tugas. Aturan sama dengan webapp: judul dipotong, jam dinormalkan ke
     * `HH:MM:00`, dan tanpa tanggal → tidak boleh ada jam, pengulangan, atau pengingat.
     */
    suspend fun update(id: String, patch: Map<String, Any?>) {
        if (patch.isEmpty()) return
        val values = LinkedHashMap(patch)
        (values["title"] as? String)?.let { values["title"] = Limits.clip(it.trim(), Limits.TASK_TITLE) }
        if (values.containsKey("due_time")) values["due_time"] = Dates.toDbTime(values["due_time"] as String?)
        if (values.containsKey("due_date") && values["due_date"] == null) {
            values["due_time"] = null
            values["rrule"] = null
            values["remind_at"] = null
        }
        values["updated_at"] = Timestamps.nowIso()
        val columns = values.keys.joinToString(", ") { "$it = ?" }
        db.execute("UPDATE tasks SET $columns WHERE id = ?", values.values.toList() + id)
    }

    private suspend fun setStatus(id: String, status: TaskStatus) {
        val now = Timestamps.nowIso()
        db.execute(
            "UPDATE tasks SET status = ?, completed_at = ?, updated_at = ? WHERE id = ?",
            listOf(status.db, if (status == TaskStatus.Done) now else null, now, id),
        )
    }

    /** Centang / batal centang. Tugas berulang → buat kejadian berikutnya (model bergulir). */
    suspend fun toggle(task: Task, today: String = Dates.today()): ToggleOutcome {
        if (task.status == TaskStatus.Done || task.status == TaskStatus.Cancelled) {
            setStatus(task.id, TaskStatus.Todo)
            return ToggleOutcome(null, null)
        }
        if (task.isRecurring && task.dueDate != null && task.parentId == null) return completeRecurring(task, today)
        setStatus(task.id, TaskStatus.Done)
        return if (task.parentId == null) {
            ToggleOutcome("Tugas selesai") { setStatus(task.id, task.status) }
        } else {
            ToggleOutcome(null, null)
        }
    }

    /**
     * Baris ini → riwayat (done, rrule = null); baris baru → kejadian berikutnya. Pengingat digeser
     * sejumlah hari yang sama, subtask disalin dalam keadaan belum selesai.
     */
    private suspend fun completeRecurring(task: Task, today: String): ToggleOutcome {
        val userId = users.require()
        val now = Timestamps.nowIso()
        val seriesId = task.seriesId ?: task.id
        val next = nextDueAfterCompletion(task.rrule!!, task.dueDate!!, today)
        val nextId = if (next != null) Ids.newId() else null

        db.writeTransactionAsync { tx ->
            tx.executeAsync(
                "UPDATE tasks SET status = 'done', completed_at = ?, rrule = NULL, series_id = ?, updated_at = ? WHERE id = ?",
                listOf(now, seriesId, now, task.id),
            )
            if (next != null && nextId != null) {
                tx.insertTask(
                    nextId, userId,
                    NewTask(
                        title = task.title, listId = task.listId, parentId = task.parentId, dueDate = next, dueTime = task.dueTime,
                        priority = task.priority, notes = task.notes,
                        remindAt = shiftRemindAt(task.remindAt, Dates.diffDays(task.dueDate!!, next)), rrule = task.rrule,
                    ),
                    seriesId = seriesId, status = TaskStatus.Todo, position = task.position,
                )
                val subs = tx.getAllAsync(
                    "SELECT $TASK_COLUMNS FROM tasks WHERE parent_id = ? AND deleted_at IS NULL", listOf(task.id),
                ) { it.toTask() }
                subs.forEach { s ->
                    tx.insertTask(
                        Ids.newId(), userId,
                        NewTask(s.title, s.listId, nextId, null, s.dueTime, s.priority, s.notes),
                        seriesId = null, status = TaskStatus.Todo, position = s.position,
                    )
                }
            }
        }

        val undo: suspend () -> Unit = {
            db.writeTransactionAsync { tx ->
                if (nextId != null) {
                    tx.executeAsync("DELETE FROM tasks WHERE parent_id = ?", listOf(nextId))
                    tx.executeAsync("DELETE FROM tasks WHERE id = ?", listOf(nextId))
                }
                tx.executeAsync(
                    "UPDATE tasks SET status = ?, completed_at = NULL, rrule = ?, updated_at = ? WHERE id = ?",
                    listOf(task.status.db, task.rrule, Timestamps.nowIso(), task.id),
                )
            }
        }
        val message = if (next != null) "Selesai · berikutnya ${Dates.relativeDayLabel(next, today).lowercase()}" else "Selesai · seri berakhir"
        return ToggleOutcome(message, undo)
    }

    suspend fun cancel(task: Task): ToggleOutcome {
        val now = Timestamps.nowIso()
        db.execute("UPDATE tasks SET status = 'cancelled', completed_at = NULL, updated_at = ? WHERE id = ?", listOf(now, task.id))
        return ToggleOutcome("Tugas dibatalkan") { setStatus(task.id, task.status) }
    }

    /** Pindahkan ke Sampah beserta subtask-nya. */
    suspend fun remove(task: Task): ToggleOutcome {
        val now = Timestamps.nowIso()
        db.writeTransactionAsync { tx ->
            tx.executeAsync("UPDATE tasks SET deleted_at = ?, updated_at = ? WHERE id = ?", listOf(now, now, task.id))
            tx.executeAsync("UPDATE tasks SET deleted_at = ?, updated_at = ? WHERE parent_id = ? AND deleted_at IS NULL", listOf(now, now, task.id))
        }
        return if (task.parentId == null) ToggleOutcome("Dipindahkan ke Sampah") { restore(task.id) } else ToggleOutcome(null, null)
    }

    suspend fun restore(id: String) {
        val now = Timestamps.nowIso()
        db.writeTransactionAsync { tx ->
            tx.executeAsync("UPDATE tasks SET deleted_at = NULL, updated_at = ? WHERE id = ?", listOf(now, id))
            tx.executeAsync("UPDATE tasks SET deleted_at = NULL, updated_at = ? WHERE parent_id = ? AND deleted_at IS NOT NULL", listOf(now, id))
        }
    }

    /** Tunda pengingat N menit dari sekarang (aksi notifikasi). */
    suspend fun snoozeReminder(id: String, minutes: Int, nowMillis: Long = System.currentTimeMillis()) {
        update(id, mapOf("remind_at" to Dates.millisToIso(nowMillis + minutes * 60_000L)))
    }

    // ------------------------------------------------------------------ daftar

    suspend fun createList(name: String, color: String): String {
        val userId = users.require()
        val id = Ids.newId()
        val now = Timestamps.nowIso()
        db.execute(
            "INSERT INTO task_lists (id, user_id, created_at, updated_at, name, color, icon, position, is_archived) VALUES (?, ?, ?, ?, ?, ?, NULL, ?, 0)",
            listOf(id, userId, now, now, name.trim().take(80), color, System.currentTimeMillis().toDouble()),
        )
        return id
    }

    suspend fun updateList(id: String, name: String, color: String) {
        db.execute("UPDATE task_lists SET name = ?, color = ?, updated_at = ? WHERE id = ?", listOf(name.trim().take(80), color, Timestamps.nowIso(), id))
    }

    /** Hapus daftar; tugas di dalamnya pindah ke "Tanpa daftar". */
    suspend fun removeList(id: String) {
        val now = Timestamps.nowIso()
        db.writeTransactionAsync { tx ->
            tx.executeAsync("UPDATE tasks SET list_id = NULL, updated_at = ? WHERE list_id = ?", listOf(now, id))
            tx.executeAsync("UPDATE task_lists SET deleted_at = ?, updated_at = ? WHERE id = ?", listOf(now, now, id))
        }
    }

    private suspend fun ConnectionContext.insertTask(
        id: String,
        userId: String,
        input: NewTask,
        seriesId: String?,
        status: TaskStatus,
        position: Double = System.currentTimeMillis().toDouble(),
    ) {
        val now = Timestamps.nowIso()
        executeAsync(
            """INSERT INTO tasks (id, user_id, created_at, updated_at, list_id, parent_id, series_id, title, notes, status,
                 priority, due_date, due_time, remind_at, rrule, completed_at, position)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?)""",
            listOf(
                id, userId, now, now, input.listId, input.parentId, seriesId,
                Limits.clip(input.title.trim(), Limits.TASK_TITLE), input.notes, status.db, input.priority,
                input.dueDate, if (input.dueDate != null) Dates.toDbTime(input.dueTime) else null,
                if (input.dueDate != null) input.remindAt else null,
                if (input.dueDate != null) input.rrule else null,
                position,
            ),
        )
    }
}

internal fun SqlCursor.int(index: Int): Int = getLong(index)?.toInt() ?: 0
