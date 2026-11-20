package com.rosyidputrar.taskify.core.store.files

import com.powersync.PowerSyncDatabase
import com.powersync.db.SqlCursor
import com.rosyidputrar.taskify.core.common.Ids
import com.rosyidputrar.taskify.core.common.Timestamps
import com.rosyidputrar.taskify.core.domain.files.Attachment
import com.rosyidputrar.taskify.core.domain.files.AttachmentStatus
import com.rosyidputrar.taskify.core.domain.files.EntityType
import com.rosyidputrar.taskify.core.domain.files.storageKey
import com.rosyidputrar.taskify.core.store.UserIdProvider
import com.rosyidputrar.taskify.core.store.require
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val COLUMNS =
    "id, entity_type, entity_id, storage_key, file_name, mime_type, size_bytes, sha256, version_label, note, status, uploaded_at, created_at"

internal fun SqlCursor.toAttachment() = Attachment(
    id = getString(0)!!, entityType = getString(1) ?: "", entityId = getString(2) ?: "", storageKey = getString(3) ?: "",
    fileName = getString(4) ?: "file", mimeType = getString(5), sizeBytes = getLong(6) ?: 0, sha256 = getString(7),
    versionLabel = getString(8), note = getString(9), status = AttachmentStatus.from(getString(10)), uploadedAt = getString(11),
    createdAt = getString(12) ?: "",
)

data class NewAttachment(
    val entityType: EntityType,
    val entityId: String,
    val fileName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val sha256: String,
    val versionLabel: String? = null,
    val note: String? = null,
)

data class DuplicateInfo(val fileName: String, val versionLabel: String?, val entityType: String, val entityId: String)

data class StorageUsage(val bytes: Long = 0, val files: Int = 0)

/**
 * Metadata lampiran (`attachments`, tersinkron). Isi file ada di R2; status 'pending' → 'uploaded'
 * atau 'failed' (port `attachment-actions.ts`). Hapus = soft delete; objek R2 dibersihkan
 * terpisah (best effort + cron server).
 */
class AttachmentRepository(private val db: PowerSyncDatabase, private val users: UserIdProvider) {

    fun watch(entityType: EntityType, entityId: String): Flow<List<Attachment>> = db.watch(
        "SELECT $COLUMNS FROM attachments WHERE entity_type = ? AND entity_id = ? AND deleted_at IS NULL ORDER BY created_at DESC",
        listOf(entityType.value, entityId),
    ) { it.toAttachment() }

    suspend fun get(id: String): Attachment? = db.getOptional("SELECT $COLUMNS FROM attachments WHERE id = ?", listOf(id)) { it.toAttachment() }

    fun watchOne(id: String): Flow<Attachment?> = db.watch("SELECT $COLUMNS FROM attachments WHERE id = ?", listOf(id)) { it.toAttachment() }.map { it.firstOrNull() }

    /** Lampiran yang belum terunggah (outbox), terlama dulu. Dibatasi 500: outbox memproses maks. 5 per putaran. */
    suspend fun pending(): List<Attachment> =
        db.getAll("SELECT $COLUMNS FROM attachments WHERE deleted_at IS NULL AND status <> 'uploaded' ORDER BY created_at LIMIT 500", emptyList()) { it.toAttachment() }

    suspend fun isLive(id: String): Boolean =
        db.getOptional("SELECT 1 FROM attachments WHERE id = ? AND deleted_at IS NULL", listOf(id)) { 1 } != null

    /** Cari file identik (SHA-256) yang masih ada. */
    /** Label versi yang sudah dipakai project (untuk label berikutnya). */
    suspend fun versionLabels(projectId: String): List<String?> = db.getAll(
        "SELECT COALESCE(version_label, '') FROM attachments WHERE entity_type = 'code_project' AND entity_id = ? AND deleted_at IS NULL",
        listOf(projectId),
    ) { it.getString(0) ?: "" }.map { it.ifEmpty { null } }

    suspend fun findDuplicate(sha: String): DuplicateInfo? = db.getOptional(
        "SELECT file_name, version_label, entity_type, entity_id FROM attachments WHERE sha256 = ? AND deleted_at IS NULL LIMIT 1",
        listOf(sha),
    ) { c -> DuplicateInfo(c.getString(0) ?: "", c.getString(1), c.getString(2) ?: "", c.getString(3) ?: "") }

    fun watchUsage(): Flow<StorageUsage> = db.watch(
        "SELECT COALESCE(SUM(size_bytes), 0), COUNT(*) FROM attachments WHERE deleted_at IS NULL",
    ) { c -> StorageUsage(c.getLong(0) ?: 0, (c.getLong(1) ?: 0).toInt()) }.map { it.firstOrNull() ?: StorageUsage() }

    /** Buat baris 'pending' (ikut antrean sync) dan kembalikan lampirannya (dengan storage key). */
    suspend fun create(input: NewAttachment): Attachment {
        val id = Ids.newId()
        val userId = users.require()
        val now = Timestamps.nowIso()
        val name = input.fileName.take(255).ifEmpty { "file" }
        val key = storageKey(userId, id, name)
        db.execute(
            """INSERT INTO attachments (id, user_id, created_at, updated_at, entity_type, entity_id, storage_key, file_name, mime_type,
                      size_bytes, sha256, version_label, note, status, uploaded_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending', NULL)""",
            listOf(id, userId, now, now, input.entityType.value, input.entityId, key, name, input.mimeType, input.sizeBytes, input.sha256,
                input.versionLabel?.trim()?.ifEmpty { null }, input.note?.trim()?.ifEmpty { null }),
        )
        return get(id)!!
    }

    suspend fun setStatus(id: String, status: AttachmentStatus) {
        val now = Timestamps.nowIso()
        if (status == AttachmentStatus.Uploaded) {
            db.execute("UPDATE attachments SET status = 'uploaded', uploaded_at = ?, updated_at = ? WHERE id = ?", listOf(now, now, id))
        } else {
            // Jangan turunkan lampiran yang sudah terunggah (mis. oleh perangkat lain) ke pending/failed.
            db.execute("UPDATE attachments SET status = ?, updated_at = ? WHERE id = ? AND status <> 'uploaded'", listOf(status.value, now, id))
        }
    }

    suspend fun update(id: String, versionLabel: String?, note: String?) {
        db.execute(
            "UPDATE attachments SET version_label = ?, note = ?, updated_at = ? WHERE id = ? AND deleted_at IS NULL",
            listOf(versionLabel?.trim()?.ifEmpty { null }, note?.trim()?.ifEmpty { null }, Timestamps.nowIso(), id),
        )
    }

    suspend fun remove(id: String) {
        val now = Timestamps.nowIso()
        db.execute("UPDATE attachments SET deleted_at = ?, updated_at = ? WHERE id = ?", listOf(now, now, id))
    }
}
