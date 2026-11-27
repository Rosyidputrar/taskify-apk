package com.rosyidputrar.taskify.core.store.files

import com.powersync.PowerSyncDatabase
import com.rosyidputrar.taskify.core.domain.files.TempFile
import com.rosyidputrar.taskify.core.domain.files.TempFileStatus
import kotlinx.coroutines.flow.Flow

/**
 * Berkas publik (`temp_files`) — HANYA BACA. Baris ditulis server (Edge Function `tempfiles`);
 * klien tidak punya grant tulis, jadi perubahan lokal hanya akan berakhir di upload_errors.
 */
class TempFileRepository(private val db: PowerSyncDatabase) {
    fun watchFiles(): Flow<List<TempFile>> = db.watch(
        """SELECT id, file_name, mime_type, size_bytes, status, share_token, is_public, expires_at, download_count,
                  last_download_at, created_at, updated_at, deleted_at, slug
             FROM temp_files WHERE deleted_at IS NULL
            ORDER BY datetime(created_at) DESC, id DESC LIMIT 1000""",
    ) { c ->
        TempFile(
            id = c.getString(0)!!, fileName = c.getString(1) ?: "berkas", mimeType = c.getString(2) ?: "application/octet-stream",
            sizeBytes = c.getLong(3) ?: 0, status = TempFileStatus.from(c.getString(4)), shareToken = c.getString(5) ?: "",
            isPublic = (c.getLong(6) ?: 0L) != 0L, expiresAt = c.getString(7), downloadCount = (c.getLong(8) ?: 0L).toInt(),
            lastDownloadAt = c.getString(9), createdAt = c.getString(10) ?: "", updatedAt = c.getString(11) ?: "", deletedAt = c.getString(12),
            slug = c.getString(13),
        )
    }
}
