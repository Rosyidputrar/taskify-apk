package com.rosyidputrar.taskify.core.store

import com.powersync.PowerSyncDatabase
import com.powersync.inMemory
import com.rosyidputrar.taskify.core.domain.notes.NoteScope
import com.rosyidputrar.taskify.core.store.journal.JournalRepository
import com.rosyidputrar.taskify.core.store.notes.NoteCounts
import com.rosyidputrar.taskify.core.store.notes.NoteRepository
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
import kotlin.test.assertTrue

class JournalNotesRepositoryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = PowerSyncDatabase.inMemory(TaskifySchema.schema, scope)
    private val journal = JournalRepository(db) { "u" }
    private val notes = NoteRepository(db) { "u" }

    @After fun tearDown() = runBlocking {
        db.close()
        scope.cancel()
    }

    @Test fun journalCrudSearchAndTrash() = runBlocking {
        val a = journal.create("2026-10-06", title = "  Hari tenang  ", content = "jalan **pagi** 100%", mood = 4)
        val b = journal.create("2026-10-07", content = "rapat panjang", mood = 2)
        journal.create("2026-10-05", content = "biasa saja")
        assertEquals("Hari tenang", journal.get(a)!!.title)

        assertEquals(listOf(b, a), journal.watchEntries().first().take(2).map { it.id })
        journal.update(a, mapOf("is_pinned" to true, "title" to "   "))
        assertEquals(a, journal.watchEntries().first().first().id) // disematkan naik ke atas
        assertNull(journal.get(a)!!.title)

        assertEquals(listOf(a), journal.watchEntries(search = "PAGI").first().map { it.id })
        assertEquals(listOf(a), journal.watchEntries(search = "100%").first().map { it.id })
        assertEquals(0, journal.watchEntries(search = "_").first().size) // `_` dibaca apa adanya
        assertEquals(listOf(b), journal.watchEntries(mood = 2).first().map { it.id })

        journal.remove(b)
        assertEquals(2, journal.watchEntries().first().size)
        assertEquals(listOf(b), journal.watchEntries(trash = true).first().map { it.id })
        journal.restore(b)
        assertEquals(3, journal.watchEntries().first().size)

        // Pengguna lain tidak terlibat: user_id terisi dari penyedia.
        assertEquals("u", db.get("SELECT user_id FROM journal_entries WHERE id = ?", listOf(a)) { it.getString(0)!! })
    }

    @Test fun journalInsights() = runBlocking {
        journal.create("2026-10-07", content = "a", mood = 4)
        journal.create("2026-10-07", content = "b", mood = 5)
        journal.create("2026-10-06", content = "c")
        journal.create("2025-10-07", content = "setahun lalu", mood = 3)
        journal.create("2026-09-07", content = "sebulan lalu")
        journal.create("2026-09-08", content = "bukan")

        val series = journal.watchMoodSeries("2026-10-07", 30).first()
        assertEquals(30, series.size)
        assertEquals(4.5, series.last().mood)
        assertEquals(2, series.last().entries)
        assertNull(series[series.size - 2].mood)
        assertEquals(1, series[series.size - 2].entries)

        assertTrue("2026-10-06" in journal.watchDays().first())
        assertEquals(listOf("2026-09-07", "2025-10-07"), journal.watchOnThisDay("2026-10-07").first().map { it.entryDate })
        assertEquals(2, journal.watchOnDate("2026-10-07").first().size)
    }

    @Test fun notesScopesOrderingAndCounts() = runBlocking {
        val free = notes.create(content = "ide fitur ekspor")
        val dated = notes.create("2026-10-07", title = "Rapat", content = "- [ ] notulen")
        val older = notes.create("2026-09-30", content = "lama")
        val pinned = notes.create(content = "penting", pinned = true)

        assertEquals(listOf(pinned, dated, older, free), notes.watchNotes().first().map { it.id })
        assertEquals(listOf(dated, older), notes.watchNotes(NoteScope.Dated).first().map { it.id })
        assertEquals(listOf(pinned, free), notes.watchNotes(NoteScope.Free).first().map { it.id })
        assertEquals(listOf(dated), notes.watchNotes(search = "rapat").first().map { it.id })
        assertEquals(NoteCounts(total = 4, dated = 2, pinned = 1), notes.watchCounts().first())

        // Lepas dari tanggal → jadi catatan bebas; tempel kembali.
        notes.update(dated, mapOf("note_date" to null))
        assertEquals(0, notes.watchOnDate("2026-10-07").first().size)
        notes.update(dated, mapOf("note_date" to "2026-10-08", "is_pinned" to 1))
        assertEquals(listOf(dated), notes.watchOnDate("2026-10-08").first().map { it.id })

        notes.remove(free)
        assertEquals(listOf(free), notes.watchNotes(trash = true).first().map { it.id })
        assertEquals(3, notes.watchCounts().first().total)
        notes.restore(free)
        assertEquals(4, notes.watchCounts().first().total)
    }
}
