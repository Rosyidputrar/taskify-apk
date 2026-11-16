package com.rosyidputrar.taskify.core.store

import com.powersync.PowerSyncDatabase
import com.powersync.inMemory
import com.rosyidputrar.taskify.core.domain.review.Range
import com.rosyidputrar.taskify.core.store.review.ReviewRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Kasus disalin dari review-queries.test.ts webapp, dijalankan di PowerSync sungguhan. */
class ReviewRepositoryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = PowerSyncDatabase.inMemory(TaskifySchema.schema, scope)
    private val repo = ReviewRepository(db)
    private val oct = Range("2026-10-01", "2026-10-31")

    @After fun tearDown() = runBlocking {
        db.close()
        scope.cancel()
    }

    private suspend fun sql(q: String, vararg args: Any?) = db.execute(q, args.toList())
    private suspend fun task(id: String, parent: String?, status: String, completed: String?, deleted: String? = null, due: String? = null, created: String = "2026-09-01T00:00:00Z") =
        sql("INSERT INTO tasks (id, user_id, parent_id, title, status, priority, completed_at, deleted_at, due_date, created_at, updated_at, position) VALUES (?, 'u', ?, 'x', ?, 0, ?, ?, ?, ?, ?, 0)",
            id, parent, status, completed, deleted, due, created, created)
    private suspend fun tx(id: String, day: String, kind: String, amount: Long, debt: String? = null, deleted: String? = null, title: String? = null) =
        sql("INSERT INTO transactions (id, user_id, occurred_on, kind, amount, debt_id, deleted_at, title, created_at, updated_at) VALUES (?, 'u', ?, ?, ?, ?, ?, ?, '2026-10-01T00:00:00Z', '2026-10-01T00:00:00Z')",
            id, day, kind, amount, debt, deleted, title)

    private suspend fun heat() = repo.watchHeatmap(oct).first().mapValues { it.value }

    @Test fun parentTasksOnly() = runBlocking {
        task("p", null, "done", "2026-10-07T03:00:00Z")
        task("s1", "p", "done", "2026-10-07T03:00:00Z")
        task("s2", "p", "done", "2026-10-07T03:00:00Z")
        val h = heat()
        assertEquals(1, h["2026-10-07"]!!.tasks)
        assertEquals(1, h["2026-10-07"]!!.score)
    }

    @Test fun vaultCountsSnippetProjectVersion() = runBlocking {
        sql("INSERT INTO snippets (id, user_id, title, language, code, created_at, updated_at) VALUES ('a', 'u', 's', 'bash', '', '2026-10-07T03:00:00Z', '2026-10-07T03:00:00Z')")
        sql("INSERT INTO code_projects (id, user_id, name, created_at, updated_at) VALUES ('b', 'u', 'p', '2026-10-07T04:00:00Z', '2026-10-07T04:00:00Z')")
        sql("INSERT INTO attachments (id, user_id, entity_type, entity_id, storage_key, file_name, size_bytes, status, created_at, updated_at) VALUES ('c', 'u', 'code_project', 'b', 'k', 'f.zip', 1, 'uploaded', '2026-10-07T05:00:00Z', '2026-10-07T05:00:00Z')")
        sql("INSERT INTO attachments (id, user_id, entity_type, entity_id, storage_key, file_name, size_bytes, status, created_at, updated_at) VALUES ('d', 'u', 'transaction', 'x', 'k', 'f.jpg', 1, 'uploaded', '2026-10-07T05:00:00Z', '2026-10-07T05:00:00Z')")
        assertEquals(3, heat()["2026-10-07"]!!.vault)
    }

    @Test fun notesOnlyDatedAndNotTrashed() = runBlocking {
        for ((id, date, del) in listOf(Triple("a", "2026-10-07", null), Triple("b", "2026-10-07", null), Triple("bebas", null, null), Triple("sampah", "2026-10-07", "2026-10-08T00:00:00Z"), Triple("luar", "2026-11-02", null)))
            sql("INSERT INTO notes (id, user_id, title, content, note_date, deleted_at, created_at, updated_at) VALUES (?, 'u', '', '', ?, ?, '2026-10-01T00:00:00Z', '2026-10-01T00:00:00Z')", id, date, del)
        val h = heat()
        assertEquals(setOf("2026-10-07"), h.keys)
        assertEquals(2, h["2026-10-07"]!!.notes)
    }

    @Test fun wibConversionAndTrash() = runBlocking {
        sql("INSERT INTO snippets (id, user_id, title, language, code, created_at, updated_at) VALUES ('a', 'u', 's', 'bash', '', '2026-10-07T17:30:00Z', '2026-10-07T17:30:00Z')")
        task("p", null, "done", "2026-10-07T03:00:00Z", deleted = "2026-10-08T00:00:00Z")
        val h = heat()
        assertEquals(setOf("2026-10-08"), h.keys)
        assertEquals(1, h["2026-10-08"]!!.vault)
    }

    @Test fun transactionsAndStats() = runBlocking {
        tx("makan", "2026-10-07", "expense", 25_000, title = "Makan siang")
        tx("gaji", "2026-10-07", "income", 5_000_000)
        tx("pindah", "2026-10-07", "transfer", 70_000)
        tx("pinjamkan", "2026-10-07", "expense", 100_000, debt = "h1")
        tx("pelunasan", "2026-10-08", "income", 100_000, debt = "h1")
        tx("sampah", "2026-10-07", "expense", 9_000_000, deleted = "2026-10-08T00:00:00Z")
        assertEquals(2, heat()["2026-10-07"]!!.transactions)
        val s = repo.watchStats(oct).first()
        assertEquals(5_000_000, s.income)
        assertEquals(25_000, s.expense)
        assertEquals(2, s.txCount)
        val big = repo.watchBigExpense(oct).first()!!
        assertEquals("Makan siang", big.title)
        assertEquals(25_000, repo.watchDaily(oct).first()["2026-10-07"]!!.expense)
    }

    @Test fun taskStatsAndMood() = runBlocking {
        task("a", null, "done", "2026-10-07T03:00:00Z", due = "2026-10-07", created = "2026-10-02T03:00:00Z")
        task("b", null, "todo", null, due = "2026-10-09", created = "2026-10-02T03:00:00Z")
        task("c", null, "cancelled", null, due = "2026-10-09")
        for ((id, date, mood) in listOf(Triple("j1", "2026-10-07", 4), Triple("j2", "2026-10-07", 2), Triple("j3", "2026-10-09", 5)))
            sql("INSERT INTO journal_entries (id, user_id, entry_date, title, content, mood, created_at, updated_at) VALUES (?, 'u', ?, '', '', ?, '2026-10-01T00:00:00Z', '2026-10-01T00:00:00Z')", id, date, mood)
        val s = repo.watchStats(oct).first()
        assertEquals(1, s.tasksDone)
        assertEquals(2, s.tasksCreated)
        assertEquals(2, s.tasksDue)
        assertEquals(1, s.tasksDueDone)
        assertEquals(3, s.journalEntries)
        assertEquals(2, s.journalDays)
        assertEquals(11.0 / 3, s.moodAvg!!, 1e-9)
        assertEquals("j3", repo.watchBestMood(oct).first()!!.id)
        val day = repo.watchDaily(oct).first()["2026-10-07"]!!
        assertEquals(1, day.tasksDone); assertEquals(2, day.journal); assertEquals(3.0, day.mood)
        assertNull(repo.watchBigExpense(oct).first())
    }
}
