package com.rosyidputrar.taskify.core.store.notes

import com.powersync.PowerSyncDatabase
import com.powersync.db.SqlCursor
import com.rosyidputrar.taskify.core.common.Ids
import com.rosyidputrar.taskify.core.common.Limits
import com.rosyidputrar.taskify.core.common.Timestamps
import com.rosyidputrar.taskify.core.domain.notes.Note
import com.rosyidputrar.taskify.core.domain.notes.NoteScope
import com.rosyidputrar.taskify.core.domain.notes.escapeLike
import com.rosyidputrar.taskify.core.store.UserIdProvider
import com.rosyidputrar.taskify.core.store.journal.cleanTitle
import com.rosyidputrar.taskify.core.store.require
import com.rosyidputrar.taskify.core.store.tasks.int
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val COLUMNS = "id, note_date, title, content, is_pinned, created_at, updated_at, deleted_at"

private fun SqlCursor.toNote() = Note(
    id = getString(0)!!,
    noteDate = getString(1),
    title = getString(2),
    content = getString(3) ?: "",
    pinned = int(4) == 1,
    createdAt = getString(5) ?: "",
    updatedAt = getString(6) ?: "",
    deletedAt = getString(7),
)

data class NoteCounts(val total: Int = 0, val dated: Int = 0, val pinned: Int = 0)

/**
 * Baca & tulis Catatan — port `note-queries.ts` + `note-actions.ts` webapp (SQL sama persis).
 * Catatan bebas (`note_date` NULL) diurutkan di bawah catatan bertanggal.
 */
class NoteRepository(private val db: PowerSyncDatabase, private val users: UserIdProvider) {

    fun watchNotes(scope: NoteScope = NoteScope.All, search: String = "", trash: Boolean = false): Flow<List<Note>> = db.watch(
        """SELECT $COLUMNS FROM notes
            WHERE (CASE WHEN ?1 = 1 THEN deleted_at IS NOT NULL ELSE deleted_at IS NULL END)
              AND (?2 = 'all' OR (?2 = 'dated' AND note_date IS NOT NULL) OR (?2 = 'free' AND note_date IS NULL))
              AND (?3 = '' OR LOWER(COALESCE(title, '') || ' ' || content) LIKE '%' || ?3 || '%' ESCAPE '\')
            ORDER BY is_pinned DESC, (note_date IS NULL), note_date DESC, created_at DESC
            LIMIT 1000""",
        listOf(if (trash) 1 else 0, scope.value, escapeLike(search.trim().lowercase())),
    ) { it.toNote() }

    fun watchNote(id: String): Flow<Note?> =
        db.watch("SELECT $COLUMNS FROM notes WHERE id = ?", listOf(id)) { it.toNote() }.map { it.firstOrNull() }

    suspend fun get(id: String): Note? = db.getOptional("SELECT $COLUMNS FROM notes WHERE id = ?", listOf(id)) { it.toNote() }

    fun watchOnDate(date: String): Flow<List<Note>> = db.watch(
        "SELECT $COLUMNS FROM notes WHERE deleted_at IS NULL AND note_date = ? ORDER BY is_pinned DESC, created_at",
        listOf(date),
    ) { it.toNote() }

    fun watchCounts(): Flow<NoteCounts> = db.watch(
        """SELECT COUNT(*), COALESCE(SUM(note_date IS NOT NULL), 0), COALESCE(SUM(is_pinned), 0)
             FROM notes WHERE deleted_at IS NULL""",
    ) { c -> NoteCounts(c.int(0), c.int(1), c.int(2)) }.map { it.firstOrNull() ?: NoteCounts() }

    suspend fun create(date: String? = null, title: String? = null, content: String = "", pinned: Boolean = false): String {
        val id = Ids.newId()
        val now = Timestamps.nowIso()
        db.execute(
            """INSERT INTO notes (id, user_id, created_at, updated_at, note_date, title, content, is_pinned)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
            listOf(id, users.require(), now, now, date, cleanTitle(title, Limits.NOTE_TITLE), content, if (pinned) 1 else 0),
        )
        return id
    }

    /** Kolom yang diizinkan: note_date (null = lepas dari tanggal), title, content, is_pinned. */
    suspend fun update(id: String, patch: Map<String, Any?>) {
        val values = LinkedHashMap<String, Any?>()
        patch.forEach { (key, value) ->
            when (key) {
                "note_date", "content" -> values[key] = value
                "title" -> values[key] = cleanTitle(value as String?, Limits.NOTE_TITLE)
                "is_pinned" -> values[key] = if (value == true || value == 1) 1 else 0
                else -> throw IllegalArgumentException("Kolom catatan tidak dikenal: $key")
            }
        }
        if (values.isEmpty()) return
        values["updated_at"] = Timestamps.nowIso()
        db.execute("UPDATE notes SET ${values.keys.joinToString { "$it = ?" }} WHERE id = ?", values.values.toList() + id)
    }

    suspend fun remove(id: String) {
        val now = Timestamps.nowIso()
        db.execute("UPDATE notes SET deleted_at = ?, updated_at = ? WHERE id = ?", listOf(now, now, id))
    }

    suspend fun restore(id: String) {
        db.execute("UPDATE notes SET deleted_at = NULL, updated_at = ? WHERE id = ?", listOf(Timestamps.nowIso(), id))
    }
}
