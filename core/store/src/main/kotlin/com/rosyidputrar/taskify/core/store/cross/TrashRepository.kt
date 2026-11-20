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
 * Lampiran milik entitas yang dihapus PERMANEN ikut dihapus (soft-delete, bukan DELETE mentah):
 * barisnya tersinkron ke server sehingga objek R2-nya bisa dibersihkan cron server — sama
 * seperti hapus lampiran biasa. DELETE mentah akan menghilangkan `storage_key` dan membuat
 * objek R2 yatim selamanya.
 */
internal suspend fun ConnectionContext.softDeleteAttachments(type: EntityType, ids: List<String>) {
    if (ids.isEmpty()) return
    val now = Timestamps.nowIso()
    ids.chunked(200).forEach { chunk ->
        val marks = chunk.joinToString(",") { "?" }
        executeAsync(
            "UPDATE attachments SET deleted_at = ?, updated_at = ? WHERE entity_type = ? AND entity_id IN ($marks) AND deleted_at IS NULL",
            listOf(now, now, type.value) + chunk,
        )
    }
}

/** Jenis item rahasia di Sampah (bukan [EntityType]: tidak punya tag, tautan, atau lampiran). */
enum class SecretTrashType(val table: String, val label: String) {
    PasswordItem("pw_items", "Item Sandi"),
    ConfigFile("config_files", "Konfigurasi"),
}

/**
 * Item Sandi / Konfigurasi di Sampah. Isi item Sandi terenkripsi (judulnya pun), jadi yang
 * ditampilkan hanya jenisnya; nama berkas konfigurasi tidak dienkripsi.
 */
data class SecretTrashRow(val type: SecretTrashType, val id: String, val title: String, val subtitle: String?, val deletedAt: String?)

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
           ORDER BY deleted_at DESC LIMIT 1000""",
    ) { it.entityRowOrNull() ?: NONE }.map { rows -> rows.filter { it !== NONE } }

    /** Item Sandi & Konfigurasi yang dihapus (terbaru dulu). */
    fun watchSecretTrash(): Flow<List<SecretTrashRow>> = db.watch(
        """SELECT 'pw', id, NULL, NULL, deleted_at FROM pw_items WHERE deleted_at IS NOT NULL
           UNION ALL
           SELECT 'config', id, name, NULLIF(environment, ''), deleted_at FROM config_files WHERE deleted_at IS NOT NULL
           ORDER BY deleted_at DESC""",
    ) { c ->
        val type = if (c.getString(0) == "pw") SecretTrashType.PasswordItem else SecretTrashType.ConfigFile
        SecretTrashRow(type, c.getString(1)!!, c.getString(2)?.takeIf { it.isNotBlank() } ?: type.label, c.getString(3), c.getString(4))
    }

    suspend fun restoreSecret(type: SecretTrashType, id: String) {
        db.execute("UPDATE ${type.table} SET deleted_at = NULL, updated_at = ? WHERE id = ?", listOf(Timestamps.nowIso(), id))
    }

    /** Hapus permanen (konfigurasi beserta semua versinya). */
    suspend fun destroySecret(type: SecretTrashType, ids: List<String>) {
        if (ids.isEmpty()) return
        db.writeTransactionAsync { tx -> destroySecretIn(tx, type, ids) }
    }

    private suspend fun destroySecretIn(tx: ConnectionContext, type: SecretTrashType, ids: List<String>) {
        ids.chunked(200).forEach { chunk ->
            val marks = chunk.joinToString(",") { "?" }
            if (type == SecretTrashType.ConfigFile) tx.executeAsync("DELETE FROM config_versions WHERE file_id IN ($marks)", chunk)
            tx.executeAsync("DELETE FROM ${type.table} WHERE id IN ($marks)", chunk)
        }
    }

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
        tx.softDeleteAttachments(type, all)
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
            for (type in SecretTrashType.entries) {
                val ids = tx.getAllAsync("SELECT id FROM ${type.table} WHERE deleted_at IS NOT NULL") { it.getString(0)!! }
                destroySecretIn(tx, type, ids)
            }
        }
    }

    /**
     * Hapus permanen isi Sampah > 30 hari + baris pendukung yang aman (sama dengan webapp):
     * anggaran, hutang, transaksi berulang, daftar tugas, tag, item Sandi, berkas konfigurasi.
     * Dompet & kategori sengaja tidak (diacu RESTRICT/CASCADE di server).
     * Daftar yang di-purge menolkan `list_id` tugasnya dulu (sama seperti hapus manual),
     * dan tag yang di-purge menghapus baris `taggings`-nya (sama seperti `TagRepository.deleteTag`).
     */
    suspend fun purgeExpired(nowMs: Long = System.currentTimeMillis()) {
        val cutoff = java.time.Instant.ofEpochMilli(nowMs - RETENTION_DAYS * 86_400_000L).toString()
        db.writeTransactionAsync { tx ->
            for (type in EntityType.entries) {
                // Bandingkan sebagai waktu (baris sinkron memakai spasi, lokal memakai "T").
                val ids = tx.getAllAsync("SELECT id FROM ${type.table} WHERE deleted_at IS NOT NULL AND datetime(deleted_at) < datetime(?)", listOf(cutoff)) { it.getString(0)!! }
                destroyIn(tx, type, ids)
            }
            // Daftar tugas yang di-purge: tugasnya pindah ke "Tanpa daftar" (sama seperti hapus manual).
            val listIds = tx.getAllAsync("SELECT id FROM task_lists WHERE deleted_at IS NOT NULL AND datetime(deleted_at) < datetime(?)", listOf(cutoff)) { it.getString(0)!! }
            if (listIds.isNotEmpty()) {
                val now = Timestamps.nowIso()
                listIds.chunked(200).forEach { chunk ->
                    val marks = chunk.joinToString(",") { "?" }
                    tx.executeAsync("UPDATE tasks SET list_id = NULL, updated_at = ? WHERE list_id IN ($marks)", listOf(now) + chunk)
                }
            }
            // Tag yang di-purge: baris taggings-nya ikut dihapus (sama seperti TagRepository.deleteTag).
            val tagIds = tx.getAllAsync("SELECT id FROM tags WHERE deleted_at IS NOT NULL AND datetime(deleted_at) < datetime(?)", listOf(cutoff)) { it.getString(0)!! }
            if (tagIds.isNotEmpty()) {
                tagIds.chunked(200).forEach { chunk ->
                    val marks = chunk.joinToString(",") { "?" }
                    tx.executeAsync("DELETE FROM taggings WHERE tag_id IN ($marks)", chunk)
                }
            }
            for (table in listOf("budgets", "debts", "recurring_transactions", "task_lists", "tags")) {
                tx.executeAsync("DELETE FROM $table WHERE deleted_at IS NOT NULL AND datetime(deleted_at) < datetime(?)", listOf(cutoff))
            }
            for (type in SecretTrashType.entries) {
                val ids = tx.getAllAsync("SELECT id FROM ${type.table} WHERE deleted_at IS NOT NULL AND datetime(deleted_at) < datetime(?)", listOf(cutoff)) { it.getString(0)!! }
                destroySecretIn(tx, type, ids)
            }
        }
    }

    companion object {
        const val RETENTION_DAYS = 30
        private val NONE = EntityRow(EntityType.Task, "", "", null)
    }
}
