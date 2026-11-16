package com.rosyidputrar.taskify.core.store.cross

import com.powersync.PowerSyncDatabase
import com.powersync.db.internal.ConnectionContext
import com.rosyidputrar.taskify.core.common.Timestamps
import com.rosyidputrar.taskify.core.domain.files.EntityType
import com.rosyidputrar.taskify.core.domain.search.EntityRow
import com.rosyidputrar.taskify.core.store.search.entityRowOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Tabel tiap jenis entitas di Sampah. */
internal val EntityType.table: String
    get() = when (this) {
        EntityType.Task -> "tasks"
        EntityType.JournalEntry -> "journal_entries"
        EntityType.Note -> "notes"
        EntityType.Transaction -> "transactions"
        EntityType.Snippet -> "snippets"
        EntityType.CodeProject -> "code_projects"
    }

/** Hapus tag & tautan milik entitas yang dihapus PERMANEN (tanpa FK di tabel polimorfik). */
internal suspend fun ConnectionContext.deleteEntityRefs(type: EntityType, ids: List<String>) {
    if (ids.isEmpty()) return
    val marks = ids.joinToString(",") { "?" }
    executeAsync("DELETE FROM taggings WHERE entity_type = ? AND entity_id IN ($marks)", listOf(type.value) + ids)
    executeAsync(
        "DELETE FROM entity_links WHERE (source_type = ? AND source_id IN ($marks)) OR (target_type = ? AND target_id IN ($marks))",
        listOf(type.value) + ids + listOf(type.value) + ids,
    )
}

/**
 * Sampah lintas modul (port `trash-actions.ts`): daftar, pulihkan, hapus permanen (+ subtask,
 * tag, tautan), dan pembersihan otomatis setelah [RETENTION_DAYS] hari.
 */
class TrashRepository(private val db: PowerSyncDatabase) {

    fun watchTrash(): Flow<List<EntityRow>> = db.watch(
        """SELECT 'task' AS type, id, title, due_date AS subtitle, deleted_at FROM tasks
            WHERE deleted_at IS NOT NULL AND parent_id IS NULL
           UNION ALL
           SELECT 'journal_entry', id, COALESCE(NULLIF(title, ''), substr(content, 1, 80), 'Jurnal'), entry_date, deleted_at
             FROM journal_entries WHERE deleted_at IS NOT NULL
           UNION ALL
           SELECT 'note', id, COALESCE(NULLIF(title, ''), NULLIF(substr(content, 1, 80), ''), 'Catatan'), note_date, deleted_at
             FROM notes WHERE deleted_at IS NOT NULL
           UNION ALL
           SELECT 'transaction', t.id, COALESCE(NULLIF(t.title, ''), c.name, 'Transaksi'),
                  t.occurred_on || '|' || t.kind || '|' || t.amount, t.deleted_at
             FROM transactions t LEFT JOIN categories c ON c.id = t.category_id WHERE t.deleted_at IS NOT NULL
           UNION ALL
           SELECT 'snippet', id, title, language, deleted_at FROM snippets WHERE deleted_at IS NOT NULL
           UNION ALL
           SELECT 'code_project', id, name, NULL, deleted_at FROM code_projects WHERE deleted_at IS NOT NULL
           ORDER BY deleted_at DESC""",
    ) { it.entityRowOrNull() ?: NONE }.map { rows -> rows.filter { it !== NONE } }

    /** Pulihkan; tugas ikut memulihkan subtask-nya yang terhapus. */
    suspend fun restore(type: EntityType, id: String) {
        val now = Timestamps.nowIso()
        db.writeTransactionAsync { tx ->
            tx.executeAsync("UPDATE ${type.table} SET deleted_at = NULL, updated_at = ? WHERE id = ?", listOf(now, id))
            if (type == EntityType.Task) tx.executeAsync("UPDATE tasks SET deleted_at = NULL, updated_at = ? WHERE parent_id = ? AND deleted_at IS NOT NULL", listOf(now, id))
        }
    }

    /** Hapus permanen (+ subtask untuk tugas, + tag & tautan). */
    suspend fun destroy(type: EntityType, ids: List<String>) {
        if (ids.isEmpty()) return
        db.writeTransactionAsync { tx -> destroyIn(tx, type, ids) }
    }

    private suspend fun destroyIn(tx: ConnectionContext, type: EntityType, ids: List<String>) {
        val all = if (type == EntityType.Task) {
            val marks = ids.joinToString(",") { "?" }
            ids + tx.getAllAsync("SELECT id FROM tasks WHERE parent_id IN ($marks)", ids) { it.getString(0)!! }
        } else ids
        tx.deleteEntityRefs(type, all)
        all.chunked(200).forEach { chunk ->
            tx.executeAsync("DELETE FROM ${type.table} WHERE id IN (${chunk.joinToString(",") { "?" }})", chunk)
        }
    }

    /** Kosongkan Sampah. */
    suspend fun emptyTrash() {
        db.writeTransactionAsync { tx ->
            for (type in EntityType.entries) {
                val extra = if (type == EntityType.Task) " AND parent_id IS NULL" else ""
                val ids = tx.getAllAsync("SELECT id FROM ${type.table} WHERE deleted_at IS NOT NULL$extra") { it.getString(0)!! }
                destroyIn(tx, type, ids)
            }
        }
    }

    /**
     * Hapus permanen isi Sampah > 30 hari + baris pendukung yang aman (sama dengan webapp):
     * anggaran, hutang, transaksi berulang, daftar tugas, tag, item Sandi, berkas konfigurasi.
     * Dompet & kategori sengaja tidak (diacu RESTRICT/CASCADE di server).
     */
    suspend fun purgeExpired(nowMs: Long = System.currentTimeMillis()) {
        val cutoff = java.time.Instant.ofEpochMilli(nowMs - RETENTION_DAYS * 86_400_000L).toString()
        db.writeTransactionAsync { tx ->
            for (type in EntityType.entries) {
                // Bandingkan sebagai waktu (baris sinkron memakai spasi, lokal memakai "T").
                val ids = tx.getAllAsync("SELECT id FROM ${type.table} WHERE deleted_at IS NOT NULL AND datetime(deleted_at) < datetime(?)", listOf(cutoff)) { it.getString(0)!! }
                destroyIn(tx, type, ids)
            }
            for (table in listOf("budgets", "debts", "recurring_transactions", "task_lists", "tags", "pw_items")) {
                tx.executeAsync("DELETE FROM $table WHERE deleted_at IS NOT NULL AND datetime(deleted_at) < datetime(?)", listOf(cutoff))
            }
            val files = tx.getAllAsync("SELECT id FROM config_files WHERE deleted_at IS NOT NULL AND datetime(deleted_at) < datetime(?)", listOf(cutoff)) { it.getString(0)!! }
            files.forEach { id ->
                tx.executeAsync("DELETE FROM config_versions WHERE file_id = ?", listOf(id))
                tx.executeAsync("DELETE FROM config_files WHERE id = ?", listOf(id))
            }
        }
    }

    companion object {
        const val RETENTION_DAYS = 30
        private val NONE = EntityRow(EntityType.Task, "", "", null)
    }
}
