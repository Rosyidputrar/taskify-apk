package com.rosyidputrar.taskify.core.store

import com.powersync.PowerSyncDatabase
import com.powersync.inMemory
import com.rosyidputrar.taskify.core.domain.files.EntityType
import com.rosyidputrar.taskify.core.domain.search.Search
import com.rosyidputrar.taskify.core.store.search.SearchRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchRepositoryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = PowerSyncDatabase.inMemory(TaskifySchema.schema, scope)
    private val repo = SearchRepository(db)

    @After fun tearDown() = runBlocking {
        db.close()
        scope.cancel()
    }

    private suspend fun task(id: String, title: String, notes: String = "", deleted: String? = null) = db.execute(
        "INSERT INTO tasks (id, user_id, title, notes, status, priority, deleted_at, created_at, updated_at, position) VALUES (?, 'u', ?, ?, 'todo', 0, ?, '2026-11-01T00:00:00Z', '2026-11-01T00:00:00Z', 0)",
        listOf(id, title, notes, deleted),
    )

    @Test fun ftsQuery() {
        assertEquals("\"bayar\"* \"listrik\"*", Search.toFtsQuery("Bayar  listrik!"))
        assertEquals("\"kafe\"*", Search.toFtsQuery("Kafé"))
        assertEquals(null, Search.toFtsQuery("  ?! "))
        assertEquals(listOf("ba", "yar", " x"), Search.splitSnippet("ba⟪yar⟫ x").map { it.text })
    }

    @Test fun ftsIndexFollowsWritesAndSoftDelete() = runBlocking {
        task("old", "Rapat lama")   // ada sebelum indeks dipasang → dibangun ulang saat setup
        assertTrue(repo.setup(), "FTS5 harus tersedia di SQLite PowerSync")
        task("a", "Bayar listrik", "token PLN bulan ini")
        db.execute(
            "INSERT INTO notes (id, user_id, title, content, created_at, updated_at) VALUES ('n', 'u', '', 'Ide: bayar cicilan lebih awal', '2026-11-01T00:00:00Z', '2026-11-01T00:00:00Z')",
        )
        val hits = repo.search("bayar")
        assertEquals(setOf("a", "n"), hits.map { it.id }.toSet())
        assertEquals(EntityType.Task, hits.first().type) // judul berbobot lebih tinggi
        assertTrue(repo.search("pln")[0].snippet!!.contains("⟪PLN⟫"))
        assertEquals(listOf("old"), repo.search("rapat").map { it.id })
        // soft delete → keluar dari indeks; pulihkan → kembali
        db.execute("UPDATE tasks SET deleted_at = '2026-11-02T00:00:00Z' WHERE id = 'a'")
        assertEquals(listOf("n"), repo.search("bayar").map { it.id })
        db.execute("UPDATE tasks SET deleted_at = NULL, title = 'Bayar air' WHERE id = 'a'")
        assertEquals(listOf("a"), repo.search("air").map { it.id })
        // idempoten: setup kedua tidak menggandakan baris
        repo.setup()
        assertEquals(2, repo.search("bayar").size)
    }

    @Test fun tagsAndTransactions() = runBlocking {
        repo.setup()
        db.execute("INSERT INTO tags (id, user_id, name, color, created_at, updated_at) VALUES ('t', 'u', 'kerja', 'sky', '2026-11-01T00:00:00Z', '2026-11-01T00:00:00Z')")
        assertEquals("kerja", repo.searchTags("#ker").single().name)
        db.execute("INSERT INTO transactions (id, user_id, occurred_on, kind, amount, title, created_at, updated_at) VALUES ('x', 'u', '2026-11-03', 'expense', 25000, 'Kopi susu', '2026-11-01T00:00:00Z', '2026-11-01T00:00:00Z')")
        val hit = repo.search("kopi").single()
        assertEquals(Search.TxSubtitle("2026-11-03", "expense", 25000), Search.txSubtitle(hit.subtitle))
    }
}
