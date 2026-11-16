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
