package com.rosyidputrar.taskify.core.store

import com.powersync.PowerSyncDatabase
import com.powersync.inMemory
import com.rosyidputrar.taskify.core.domain.files.TempFileStatus
import com.rosyidputrar.taskify.core.store.files.TempFileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TempFileRepositoryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = PowerSyncDatabase.inMemory(TaskifySchema.schema, scope)
    private val repo = TempFileRepository(db)

    @After fun tearDown() = runBlocking {
        db.close()
        scope.cancel()
    }

    /** Meniru baris yang datang lewat sinkron (format timestamp PowerSync memakai spasi). */
    private suspend fun synced(id: String, created: String, deleted: String? = null, status: String = "ready", public: Int = 1) = db.execute(
        """INSERT INTO temp_files (id, user_id, created_at, updated_at, deleted_at, file_name, mime_type, size_bytes, storage_key, status,
             etag, share_token, is_public, expires_at, download_count, last_download_at)
           VALUES (?, 'u', ?, ?, ?, ?, 'application/pdf', 2048, ?, ?, NULL, ?, ?, NULL, 3, NULL)""",
        listOf(id, created, created, deleted, "$id.pdf", "temp/$id", status, "t".repeat(43), public),
    )

    @Test fun newestFirstWithoutDeleted() = runBlocking {
        synced("a", "2026-11-01 08:00:00Z")
        synced("b", "2026-11-01T09:00:00.000Z", status = "pending", public = 0) // tulisan lokal format ISO "T"
        synced("c", "2026-11-01 10:00:00Z", deleted = "2026-11-01 10:05:00Z")
        val files = repo.watchFiles().first()
        assertEquals(listOf("b", "a"), files.map { it.id })
        assertEquals(TempFileStatus.Pending, files[0].status)
        assertFalse(files[0].isPublic)
        assertTrue(files[1].isPublic)
        assertEquals(2048, files[1].sizeBytes)
        assertEquals(3, files[1].downloadCount)
    }
}
