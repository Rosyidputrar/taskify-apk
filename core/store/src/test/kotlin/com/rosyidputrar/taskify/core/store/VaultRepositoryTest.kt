package com.rosyidputrar.taskify.core.store

import com.powersync.PowerSyncDatabase
import com.powersync.inMemory
import com.rosyidputrar.taskify.core.domain.files.AttachmentStatus
import com.rosyidputrar.taskify.core.domain.files.EntityType
import com.rosyidputrar.taskify.core.store.files.AttachmentRepository
import com.rosyidputrar.taskify.core.store.files.NewAttachment
import com.rosyidputrar.taskify.core.store.vault.VaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VaultRepositoryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = PowerSyncDatabase.inMemory(TaskifySchema.schema, scope)
    private val user = "11111111-1111-1111-1111-111111111111"
    private val vault = VaultRepository(db) { user }
    private val files = AttachmentRepository(db) { user }

    @After fun tearDown() = runBlocking {
        db.close()
        scope.cancel()
    }

    @Test fun pendingOutboxIsCapped() = runBlocking {
        repeat(505) { i ->
            db.execute(
                "INSERT INTO attachments (id, user_id, entity_type, entity_id, storage_key, file_name, status, created_at, updated_at) VALUES (?, ?, 'note', 'n', ?, 'f.png', 'pending', '2026-11-01T00:00:00Z', '2026-11-01T00:00:00Z')",
                listOf("a$i", user, "k$i"),
            )
        }
        assertEquals(500, files.pending().size)
    }

    @Test fun getSnippetReturnsNullForUnknownId() = runBlocking {
        // Perilaku yang dipakai SnippetEditorViewModel untuk state "Snippet tidak ditemukan"
        // (S-3): id yang tidak ada di DB harus null, bukan draf baru.
        assertNull(vault.getSnippet("tidak-ada"))
    }

    @Test fun snippetsSearchFilterFavorite() = runBlocking {
        val a = vault.saveSnippet(null, "  Regex email  ", "javascript", "const re = /.+@.+/", "validasi")
        val b = vault.saveSnippet(null, "Docker run", "bash", "docker run -p {{port}}:80 {{image}}", null)
        assertEquals("Regex email", vault.getSnippet(a)!!.title)
        vault.setSnippetFavorite(b, true)
        assertEquals(listOf(b, a), vault.watchSnippets().first().map { it.id })
        assertEquals(listOf(a), vault.watchSnippets(search = "VALIDASI").first().map { it.id })
        assertEquals(listOf(b), vault.watchSnippets(search = "{{port}}").first().map { it.id })
        assertEquals(listOf(a), vault.watchSnippets(language = "javascript").first().map { it.id })
        assertEquals(listOf(b), vault.watchSnippets(favorites = true).first().map { it.id })
        assertEquals(2, vault.watchLanguages().first().size)
        vault.removeSnippet(a)
        assertEquals(1, vault.watchSnippets().first().size)
        vault.restoreSnippet(a)
        assertEquals(2, vault.watchSnippets().first().size)
    }

    @Test fun removeProjectSoftDeletesVersionsAndRestoreBringsBackOnlyThose() = runBlocking {
        val p = vault.saveProject(null, "Taskify", "Aplikasi produktivitas", "https://github.com/x/y")
        val v1 = files.create(NewAttachment(EntityType.CodeProject, p, "taskify.zip", "application/zip", 1_000, "aaa", versionLabel = "v1"))
        files.create(NewAttachment(EntityType.CodeProject, p, "taskify.zip", "application/zip", 2_000, "bbb", versionLabel = "v2"))
        // v1 dihapus sendiri SEBELUM project masuk Sampah (deleted_at lama, bukan milik penghapusan project).
        files.remove(v1.id)
        db.execute("UPDATE attachments SET deleted_at = '2026-01-01T00:00:00Z' WHERE id = ?", listOf(v1.id))

        vault.removeProject(p)
        assertTrue(vault.watchProjects().first().isEmpty(), "project masuk Sampah")
        assertEquals(0, files.watchUsage().first().bytes, "versi tidak lagi terhitung di pemakaian penyimpanan")

        vault.restoreProject(p)
        val project = vault.watchProject(p).first()!!
        assertEquals(1, project.versions, "hanya v2 yang kembali; v1 yang dihapus sendiri tetap terhapus")
        assertEquals(2_000, project.totalSize)
        assertEquals("v2", project.latestLabel)
    }

    @Test fun versionsCreatedBackToBackKeepTheirOrder() = runBlocking {
        // Unggahan beruntun bisa jatuh di milidetik yang sama: urutan & "versi terbaru" harus tetap pasti.
        val p = vault.saveProject(null, "Burst", null, null)
        val labels = (1..30).map { "v$it" }
        labels.forEach { files.create(NewAttachment(EntityType.CodeProject, p, "b.zip", "application/zip", 10, "sha-$it", versionLabel = it)) }
        assertEquals("v30", vault.watchProject(p).first()!!.latestLabel)
        assertEquals(labels.reversed(), files.watch(EntityType.CodeProject, p).first().map { it.versionLabel })
    }

    @Test fun projectsWithVersionsAndAttachmentLifecycle() = runBlocking {
        val p = vault.saveProject(null, "Taskify", "Aplikasi produktivitas", "https://github.com/x/y")
        val v1 = files.create(NewAttachment(EntityType.CodeProject, p, "taskify.zip", "application/zip", 1_000, "aaa", versionLabel = "v1"))
        assertEquals("$user/${v1.id}/taskify.zip", v1.storageKey)
        assertEquals(AttachmentStatus.Pending, v1.status)
        files.create(NewAttachment(EntityType.CodeProject, p, "taskify.zip", "application/zip", 2_000, "bbb", versionLabel = "v2"))

        val project = vault.watchProjects().first().single()
        assertEquals(2, project.versions)
        assertEquals(3_000, project.totalSize)
        assertEquals("v2", project.latestLabel)
        assertEquals(1, vault.watchProjects(search = "produktivitas").first().size)

        assertEquals(2, files.pending().size)
        files.setStatus(v1.id, AttachmentStatus.Uploaded)
        assertNotNull(files.get(v1.id)!!.uploadedAt)
        files.setStatus(v1.id, AttachmentStatus.Failed) // tidak boleh menurunkan yang sudah terunggah
        assertEquals(AttachmentStatus.Uploaded, files.get(v1.id)!!.status)
        assertEquals(1, files.pending().size)

        assertEquals("taskify.zip", files.findDuplicate("aaa")!!.fileName)
        files.remove(v1.id)
        assertNull(files.findDuplicate("aaa"))
        assertEquals(1, vault.watchProject(p).first()!!.versions)
        assertEquals(2_000, files.watchUsage().first().bytes)
        assertTrue(files.watch(EntityType.CodeProject, p).first().all { it.versionLabel == "v2" })
    }
}
