package com.rosyidputrar.taskify.core.store.journal

import com.powersync.PowerSyncDatabase
import com.powersync.db.SqlCursor
import com.rosyidputrar.taskify.core.common.Ids
import com.rosyidputrar.taskify.core.common.Limits
import com.rosyidputrar.taskify.core.common.Timestamps
import com.rosyidputrar.taskify.core.domain.journal.JournalEntry
import com.rosyidputrar.taskify.core.domain.journal.MoodPoint
import com.rosyidputrar.taskify.core.domain.journal.monthAgo
import com.rosyidputrar.taskify.core.domain.journal.moodSeries
import com.rosyidputrar.taskify.core.domain.notes.escapeLike
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.store.UserIdProvider
import com.rosyidputrar.taskify.core.store.require
import com.rosyidputrar.taskify.core.store.tasks.int
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val COLUMNS = "id, entry_date, title, content, mood, is_pinned, created_at, updated_at, deleted_at"

private fun SqlCursor.toEntry() = JournalEntry(
    id = getString(0)!!,
    entryDate = getString(1) ?: "",
    title = getString(2),
    content = getString(3) ?: "",
    mood = getLong(4)?.toInt(),
    pinned = int(5) == 1,
    createdAt = getString(6) ?: "",
    updatedAt = getString(7) ?: "",
    deletedAt = getString(8),
)

/** Judul bersih: dipangkas, dipotong ke batas CHECK Postgres; kosong → NULL. */
internal fun cleanTitle(title: String?, max: Int): String? = title?.trim()?.takeIf { it.isNotEmpty() }?.let { Limits.clip(it, max) }

/**
 * Baca & tulis Jurnal — port `journal-queries.ts` + `journal-actions.ts` webapp.
 * Hapus = soft delete (masuk Sampah, bisa dipulihkan).
 */
class JournalRepository(private val db: PowerSyncDatabase, private val users: UserIdProvider) {

    /** Daftar entri: disematkan dulu, lalu tanggal terbaru. Cari di judul & isi; filter mood. */
    fun watchEntries(search: String = "", mood: Int? = null, trash: Boolean = false): Flow<List<JournalEntry>> = db.watch(
        """SELECT $COLUMNS FROM journal_entries
            WHERE (CASE WHEN ?1 = 1 THEN deleted_at IS NOT NULL ELSE deleted_at IS NULL END)
              AND (?2 IS NULL OR mood = ?2)
              AND (?3 = '' OR LOWER(COALESCE(title, '') || ' ' || content) LIKE '%' || ?3 || '%' ESCAPE '\')
            ORDER BY is_pinned DESC, entry_date DESC, created_at DESC
            LIMIT 1000""",
        listOf(if (trash) 1 else 0, mood, escapeLike(search.trim().lowercase())),
    ) { it.toEntry() }

    fun watchEntry(id: String): Flow<JournalEntry?> =
        db.watch("SELECT $COLUMNS FROM journal_entries WHERE id = ?", listOf(id)) { it.toEntry() }.map { it.firstOrNull() }

    suspend fun get(id: String): JournalEntry? =
        db.getOptional("SELECT $COLUMNS FROM journal_entries WHERE id = ?", listOf(id)) { it.toEntry() }

    fun watchOnDate(date: String): Flow<List<JournalEntry>> = db.watch(
        "SELECT $COLUMNS FROM journal_entries WHERE deleted_at IS NULL AND entry_date = ? ORDER BY created_at",
        listOf(date),
    ) { it.toEntry() }

    /** Mood rata-rata per hari selama [days] hari terakhir (hari tanpa jurnal = null). */
    fun watchMoodSeries(today: String, days: Int): Flow<List<MoodPoint>> {
        val from = Dates.addDays(today, -(days - 1).toLong())
        return db.watch(
            """SELECT entry_date, AVG(mood), COUNT(*) FROM journal_entries
                WHERE deleted_at IS NULL AND entry_date BETWEEN ? AND ? GROUP BY entry_date""",
            listOf(from, today),
        ) { c -> MoodPoint(c.getString(0)!!, c.getDouble(1), c.int(2)) }.map { moodSeries(it, today, days) }
    }

    /** Tanggal unik yang punya jurnal (streak). */
    fun watchDays(): Flow<Set<String>> = db.watch(
        "SELECT DISTINCT entry_date FROM journal_entries WHERE deleted_at IS NULL ORDER BY entry_date DESC LIMIT 1000",
    ) { it.getString(0)!! }.map { it.toSet() }

    /** "Hari ini di masa lalu": tanggal & bulan sama di tahun-tahun sebelumnya + tepat 1 bulan lalu. */
    fun watchOnThisDay(today: String): Flow<List<JournalEntry>> = db.watch(
        """SELECT $COLUMNS FROM journal_entries
            WHERE deleted_at IS NULL AND entry_date < ?1
              AND (substr(entry_date, 6) = substr(?1, 6) OR entry_date = ?2)
            ORDER BY entry_date DESC LIMIT 5""",
        listOf(today, monthAgo(today) ?: ""),
    ) { it.toEntry() }

    suspend fun create(date: String, title: String? = null, content: String = "", mood: Int? = null, pinned: Boolean = false): String {
        val id = Ids.newId()
        val now = Timestamps.nowIso()
        db.execute(
            """INSERT INTO journal_entries (id, user_id, created_at, updated_at, entry_date, title, content, mood, is_pinned)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            listOf(id, users.require(), now, now, date, cleanTitle(title, Limits.JOURNAL_TITLE), content, mood?.coerceIn(1, 5), if (pinned) 1 else 0),
        )
        return id
    }

    /** Perbarui kolom yang diizinkan: entry_date, title, content, mood, is_pinned. */
    suspend fun update(id: String, patch: Map<String, Any?>) {
        val values = LinkedHashMap<String, Any?>()
        patch.forEach { (key, value) ->
            when (key) {
                "entry_date", "content" -> values[key] = value
                "title" -> values[key] = cleanTitle(value as String?, Limits.JOURNAL_TITLE)
                "mood" -> values[key] = (value as Number?)?.toInt()?.coerceIn(1, 5)
                "is_pinned" -> values[key] = if (value == true || value == 1) 1 else 0
                else -> throw IllegalArgumentException("Kolom jurnal tidak dikenal: $key")
            }
        }
        if (values.isEmpty()) return
        values["updated_at"] = Timestamps.nowIso()
        db.execute(
            "UPDATE journal_entries SET ${values.keys.joinToString { "$it = ?" }} WHERE id = ?",
            values.values.toList() + id,
        )
    }

    suspend fun remove(id: String) {
        val now = Timestamps.nowIso()
        db.execute("UPDATE journal_entries SET deleted_at = ?, updated_at = ? WHERE id = ?", listOf(now, now, id))
    }

    suspend fun restore(id: String) {
        db.execute("UPDATE journal_entries SET deleted_at = NULL, updated_at = ? WHERE id = ?", listOf(Timestamps.nowIso(), id))
    }
}
