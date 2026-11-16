package com.rosyidputrar.taskify.core.store.focus

import com.powersync.PowerSyncDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/** Ringkasan waktu fokus dalam satu rentang tanggal. */
data class FocusStats(val minutes: Long = 0, val sessions: Int = 0, val topTask: String? = null, val topTaskMinutes: Long = 0)

/** Riwayat Mode Fokus (tabel lokal `focus_sessions`). */
class FocusRepository(private val db: PowerSyncDatabase) {

    suspend fun record(taskId: String?, taskTitle: String?, day: String, startedAt: String, endedAt: String, minutes: Long) {
        if (minutes <= 0) return
        db.execute(
            "INSERT INTO focus_sessions (id, task_id, task_title, day, started_at, ended_at, minutes) VALUES (?, ?, ?, ?, ?, ?, ?)",
            listOf(UUID.randomUUID().toString(), taskId, taskTitle, day, startedAt, endedAt, minutes),
        )
    }

    fun watchStats(from: String, to: String): Flow<FocusStats> = db.watch(
        """SELECT
             (SELECT COALESCE(SUM(minutes), 0) FROM focus_sessions WHERE day BETWEEN ?1 AND ?2),
             (SELECT COUNT(*) FROM focus_sessions WHERE day BETWEEN ?1 AND ?2),
             (SELECT task_title FROM focus_sessions WHERE day BETWEEN ?1 AND ?2 AND task_title IS NOT NULL
                GROUP BY task_id ORDER BY SUM(minutes) DESC LIMIT 1),
             (SELECT SUM(minutes) FROM focus_sessions WHERE day BETWEEN ?1 AND ?2 AND task_title IS NOT NULL
                GROUP BY task_id ORDER BY SUM(minutes) DESC LIMIT 1)""",
        listOf(from, to),
    ) { c -> FocusStats(c.getLong(0) ?: 0, c.getLong(1)?.toInt() ?: 0, c.getString(2), c.getLong(3) ?: 0) }
        .map { it.firstOrNull() ?: FocusStats() }

    /** Menit fokus per tugas (untuk ditampilkan di detail tugas). */
    fun watchTaskMinutes(taskId: String): Flow<Long> = db.watch(
        "SELECT COALESCE(SUM(minutes), 0) FROM focus_sessions WHERE task_id = ?",
        listOf(taskId),
    ) { c -> c.getLong(0) ?: 0 }.map { it.firstOrNull() ?: 0 }
}
