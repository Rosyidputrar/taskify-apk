package com.rosyidputrar.taskify.core.store

import com.powersync.PowerSyncDatabase
import com.powersync.inMemory
import com.rosyidputrar.taskify.core.domain.files.EntityType
import com.rosyidputrar.taskify.core.store.cross.LinkRepository
import com.rosyidputrar.taskify.core.store.cross.SecretTrashType
import com.rosyidputrar.taskify.core.store.cross.TrashRepository
import com.rosyidputrar.taskify.core.store.tasks.TagRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrossModuleRepositoryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = PowerSyncDatabase.inMemory(TaskifySchema.schema, scope)
    private val user = "11111111-1111-1111-1111-111111111111"
    private val trash = TrashRepository(db)
    private val links = LinkRepository(db) { user }
    private val tags = TagRepository(db) { user }

    @After fun tearDown() = runBlocking {
        db.close()
        scope.cancel()
    }

    private suspend fun count(sql: String) = db.get(sql) { it.getLong(0) ?: 0L }
    private suspend fun task(id: String, parent: String? = null, deleted: String? = null) = db.execute(
        "INSERT INTO tasks (id, user_id, parent_id, title, status, priority, deleted_at, created_at, updated_at, position) VALUES (?, ?, ?, ?, 'todo', 0, ?, '2026-11-01T00:00:00Z', '2026-11-01T00:00:00Z', 0)",
        listOf(id, user, parent, "Tugas $id", deleted),
    )
    private suspend fun note(id: String, deleted: String? = null) = db.execute(
        "INSERT INTO notes (id, user_id, title, content, deleted_at, created_at, updated_at) VALUES (?, ?, ?, '', ?, '2026-11-01T00:00:00Z', '2026-11-02T00:00:00Z')",
        listOf(id, user, "Catatan $id", deleted),
    )

    @Test fun trashListRestoreAndDestroyWithRefs() = runBlocking {
        task("p", deleted = "2026-11-10T00:00:00Z")
        task("s", parent = "p", deleted = "2026-11-10T00:00:00Z")
        note("n", deleted = "2026-11-11 00:00:00Z")
        note("hidup")
        // subtask tidak tampil sendiri; terbaru dulu
        assertEquals(listOf("n", "p"), trash.watchTrash().first().map { it.id })
        trash.restore(EntityType.Task, "p")
        assertEquals(0, count("SELECT count(*) FROM tasks WHERE deleted_at IS NOT NULL"))

        tags.addByName("note", "n", listOf("ide"))
        links.link(EntityType.Note, "n", EntityType.Task, "p")
        trash.destroy(EntityType.Note, listOf("n"))
        assertEquals(0, count("SELECT count(*) FROM notes WHERE id = 'n'"))
        assertEquals(0, count("SELECT count(*) FROM taggings"))
        assertEquals(0, count("SELECT count(*) FROM entity_links"))

        db.execute("UPDATE tasks SET deleted_at = '2026-11-12T00:00:00Z' WHERE id IN ('p', 's')")
        trash.emptyTrash()
        assertEquals(0, count("SELECT count(*) FROM tasks"))
        assertEquals(1, count("SELECT count(*) FROM notes"))
    }

    @Test fun purgeOlderThan30Days() = runBlocking {
        val now = Instant.parse("2026-12-20T00:00:00Z").toEpochMilli()
        note("lama", deleted = "2026-11-01 00:00:00Z")   // > 30 hari, format sinkron
        note("baru", deleted = "2026-12-10T00:00:00.000Z")
        db.execute("INSERT INTO tags (id, user_id, name, deleted_at, created_at, updated_at) VALUES ('t', ?, 'x', '2026-10-01T00:00:00Z', '2026-10-01T00:00:00Z', '2026-10-01T00:00:00Z')", listOf(user))
        db.execute("INSERT INTO config_files (id, user_id, name, format, deleted_at, created_at, updated_at) VALUES ('c', ?, '.env', 'env', '2026-10-01T00:00:00Z', '2026-10-01T00:00:00Z', '2026-10-01T00:00:00Z')", listOf(user))
        db.execute("INSERT INTO config_versions (id, user_id, file_id, enc, v, created_at, updated_at) VALUES ('v', ?, 'c', 'x', 1, '2026-10-01T00:00:00Z', '2026-10-01T00:00:00Z')", listOf(user))
        trash.purgeExpired(now)
        assertEquals(listOf("baru"), db.getAll("SELECT id FROM notes") { it.getString(0)!! })
        assertEquals(0, count("SELECT count(*) FROM tags"))
        assertEquals(0, count("SELECT count(*) FROM config_versions"))
        assertEquals(0, count("SELECT count(*) FROM config_files"))
    }

    @Test fun secretItemsAppearInTrashAndCanBeRestoredOrDestroyed() = runBlocking {
        db.execute("INSERT INTO pw_items (id, user_id, enc, v, deleted_at, created_at, updated_at) VALUES ('pw', ?, 'k.x', 1, '2026-11-12T00:00:00Z', '2026-11-01T00:00:00Z', '2026-11-01T00:00:00Z')", listOf(user))
        db.execute("INSERT INTO pw_items (id, user_id, enc, v, created_at, updated_at) VALUES ('pw-hidup', ?, 'k.x', 1, '2026-11-01T00:00:00Z', '2026-11-01T00:00:00Z')", listOf(user))
        db.execute("INSERT INTO config_files (id, user_id, name, format, environment, deleted_at, created_at, updated_at) VALUES ('c', ?, '.env', 'env', 'production', '2026-11-13 00:00:00Z', '2026-11-01T00:00:00Z', '2026-11-01T00:00:00Z')", listOf(user))
        db.execute("INSERT INTO config_versions (id, user_id, file_id, enc, v, created_at, updated_at) VALUES ('v', ?, 'c', 'x', 1, '2026-11-01T00:00:00Z', '2026-11-01T00:00:00Z')", listOf(user))

        // Terbaru dulu; judul Sandi tidak bisa dibaca (terenkripsi) → nama jenisnya.
        val rows = trash.watchSecretTrash().first()
        assertEquals(listOf(SecretTrashType.ConfigFile to "c", SecretTrashType.PasswordItem to "pw"), rows.map { it.type to it.id })
        assertEquals(listOf(".env", "Item Sandi"), rows.map { it.title })
        assertEquals("production", rows.first().subtitle)
        // Item rahasia tidak bercampur dengan daftar entitas biasa.
        assertTrue(trash.watchTrash().first().isEmpty())

        trash.restoreSecret(SecretTrashType.PasswordItem, "pw")
        assertEquals(0, count("SELECT count(*) FROM pw_items WHERE deleted_at IS NOT NULL"))
        assertEquals(listOf("c"), trash.watchSecretTrash().first().map { it.id })

        trash.destroySecret(SecretTrashType.ConfigFile, listOf("c"))
        assertEquals(0, count("SELECT count(*) FROM config_files"))
        assertEquals(0, count("SELECT count(*) FROM config_versions"), "versi ikut terhapus")
        assertEquals(2, count("SELECT count(*) FROM pw_items"))
    }

    @Test fun emptyTrashIncludesSecretItems() = runBlocking {
        db.execute("INSERT INTO pw_items (id, user_id, enc, v, deleted_at, created_at, updated_at) VALUES ('pw', ?, 'k.x', 1, '2026-11-12T00:00:00Z', '2026-11-01T00:00:00Z', '2026-11-01T00:00:00Z')", listOf(user))
        db.execute("INSERT INTO pw_items (id, user_id, enc, v, created_at, updated_at) VALUES ('pw-hidup', ?, 'k.x', 1, '2026-11-01T00:00:00Z', '2026-11-01T00:00:00Z')", listOf(user))
        trash.emptyTrash()
        assertEquals(listOf("pw-hidup"), db.getAll("SELECT id FROM pw_items") { it.getString(0)!! })
    }

    @Test fun linksBothDirections() = runBlocking {
        task("a"); note("b")
        assertTrue(links.link(EntityType.Task, "a", EntityType.Note, "b"))
        assertFalse(links.link(EntityType.Note, "b", EntityType.Task, "a"), "arah sebaliknya = tautan yang sama")
        assertFalse(links.link(EntityType.Task, "a", EntityType.Task, "a"))
        assertEquals(listOf("b"), links.watchLinks(EntityType.Task, "a").first().map { it.entity.id })
        val back = links.watchLinks(EntityType.Note, "b").first().single()
        assertEquals(EntityType.Task, back.entity.type)
        links.unlink(back.linkId)
        assertTrue(links.watchLinks(EntityType.Task, "a").first().isEmpty())
    }

    @Test fun tagPageActions() = runBlocking {
        task("a"); note("b")
        tags.addByName("task", "a", listOf("#Kerja Penting"))
        tags.addByName("note", "b", listOf("kerja-penting", "rumah"))
        val kerja = tags.watchAll().first().first { it.name == "kerja-penting" }
        assertEquals(2, kerja.count)
        assertEquals(setOf("a", "b"), tags.watchTagged(kerja.id).first().map { it.id }.toSet())
        assertFalse(tags.rename(kerja.id, "Rumah"), "nama yang sudah dipakai ditolak")
        assertTrue(tags.rename(kerja.id, "Kantor"))
        assertEquals("kantor", tags.watchTag(kerja.id).first()!!.name)
        tags.deleteTag(kerja.id)
        assertNull(tags.watchTag(kerja.id).first())
        assertEquals(1, count("SELECT count(*) FROM taggings"))
    }
}
