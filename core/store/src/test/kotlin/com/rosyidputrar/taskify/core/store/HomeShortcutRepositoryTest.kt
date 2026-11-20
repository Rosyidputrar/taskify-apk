package com.rosyidputrar.taskify.core.store

import com.powersync.PowerSyncDatabase
import com.powersync.inMemory
import com.rosyidputrar.taskify.core.domain.home.HomeShortcuts
import com.rosyidputrar.taskify.core.domain.home.QuickView
import com.rosyidputrar.taskify.core.domain.home.ShortcutRef
import com.rosyidputrar.taskify.core.domain.home.ShortcutType
import com.rosyidputrar.taskify.core.store.home.HomeShortcutRepository
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeShortcutRepositoryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = PowerSyncDatabase.inMemory(TaskifySchema.schema, scope)
    private var now = 1_000L
    private val repo = HomeShortcutRepository(db) { now++ }

    @After fun tearDown() = runBlocking {
        db.close()
        scope.cancel()
    }

    private suspend fun note(id: String, title: String, content: String = "", updated: String, deleted: String? = null) = db.execute(
        "INSERT INTO notes (id, user_id, title, content, deleted_at, created_at, updated_at) VALUES (?, 'u', ?, ?, ?, ?, ?)",
        listOf(id, title, content, deleted, updated, updated),
    )

    private val n1 = ShortcutRef(ShortcutType.Note, "n1")
    private val overdue = ShortcutRef.view(QuickView.TasksOverdue)

    @Test fun pinnedTitlesAreLiveAndDeletedTargetsAreFlagged() = runBlocking {
        note("n1", "Ide lama", updated = "2026-11-01T00:00:00.000Z")
        assertTrue(repo.pin(n1, "Ide lama"))
        assertTrue(repo.pin(overdue, QuickView.TasksOverdue.label))
        assertTrue(repo.pin(ShortcutRef(ShortcutType.Snippet, "hilang"), "Snippet lama"))

        db.execute("UPDATE notes SET title = 'Ide produk' WHERE id = 'n1'")
        val pinned = repo.watchPinned().first()
        assertEquals(listOf("note:n1", "view:TasksOverdue", "snippet:hilang"), pinned.map { it.ref.key })
        assertEquals("Ide produk", pinned[0].title, "judul diambil hidup dari catatan")
        assertTrue(pinned[0].available && pinned[1].available)
        assertFalse(pinned[2].available, "snippet tidak ada → yatim")
        assertEquals("Snippet lama", pinned[2].title, "judul cadangan dari saat disematkan")

        // Catatan ke Sampah → tidak tersedia (tetap tersemat; muncul lagi bila dipulihkan).
        db.execute("UPDATE notes SET deleted_at = '2026-11-02T00:00:00Z' WHERE id = 'n1'")
        assertFalse(repo.watchPinned().first()[0].available)
        repo.removeUnavailable(listOf("snippet:hilang"))
        assertEquals(listOf("note:n1", "view:TasksOverdue"), repo.watchPinned().first().map { it.ref.key })
    }

    @Test fun pinIsIdempotentCappedAndToggles() = runBlocking {
        assertTrue(repo.pin(overdue, "x"))
        assertTrue(repo.pin(overdue, "x"))
        assertEquals(1, repo.watchPinned().first().size)
        assertEquals(false, repo.toggle(overdue, "x"))
        assertFalse(repo.watchIsPinned(overdue).first())
        assertEquals(true, repo.toggle(overdue, "x"))
        assertTrue(repo.watchIsPinned(overdue).first())

        (1 until HomeShortcuts.MAX_PINNED).forEach { assertTrue(repo.pin(ShortcutRef(ShortcutType.Note, "n$it"), "n$it")) }
        assertFalse(repo.pin(ShortcutRef(ShortcutType.Note, "penuh"), "x"), "sudah ${HomeShortcuts.MAX_PINNED}")
        assertNull(repo.toggle(ShortcutRef(ShortcutType.Note, "penuh"), "x"), "toggle melapor penuh")
    }

    @Test fun moveReordersAndClamps() = runBlocking {
        val a = ShortcutRef.view(QuickView.TasksToday)
        val b = ShortcutRef.view(QuickView.FinanceBudget)
        val c = ShortcutRef.view(QuickView.SandiCodes)
        listOf(a, b, c).forEach { repo.pin(it, it.target) }
        repo.move(c, -1)
        assertEquals(listOf(a, c, b), repo.watchPinned().first().map { it.ref })
        repo.move(a, 99)
        assertEquals(listOf(c, b, a), repo.watchPinned().first().map { it.ref })
        repo.move(c, -5)
        assertEquals(listOf(c, b, a), repo.watchPinned().first().map { it.ref })
        // Disematkan setelah diurutkan → tetap di akhir.
        val d = ShortcutRef.view(QuickView.Review)
        repo.pin(d, "d")
        assertEquals(d, repo.watchPinned().first().last().ref)
    }

    @Test fun recentMixesModulesNewestFirstAndSkipsTrash() = runBlocking {
        note("old", "Catatan lama", updated = "2026-11-01T08:00:00.000Z")
        note("new", "", content = "  Isi tanpa judul\nbaris dua", updated = "2026-11-20T09:00:00.000Z")
        note("trash", "Dibuang", updated = "2026-11-20T10:00:00.000Z", deleted = "2026-11-20T10:00:00.000Z")
        db.execute(
            "INSERT INTO snippets (id, user_id, title, language, code, created_at, updated_at) VALUES ('s1', 'u', 'Retry fetch', 'kotlin', 'x', ?, ?)",
            listOf("2026-11-10T00:00:00Z", "2026-11-10T00:00:00Z"),
        )
        // Sesi AI memakai milidetik lokal → dikonversi ke ISO dan diurutkan bersama.
        val ms = 1_795_597_200_000L // 2026-11-25T03:00:00Z
        db.execute(
            "INSERT INTO ai_sessions (id, title, provider, model, system_prompt, data_access, created_ms, updated_ms) VALUES ('a1', 'Rencana liburan', 'p', 'gpt', '', 0, ?, ?)",
            listOf(ms, ms),
        )
        val recent = repo.watchRecent().first()
        assertEquals(listOf("ai_session:a1", "note:new", "snippet:s1", "note:old"), recent.map { it.ref.key })
        assertEquals("Isi tanpa judul baris dua", recent[1].title)
        assertEquals("kotlin", recent[2].subtitle)
        assertEquals(ms, HomeShortcuts.parseIsoMs(recent[0].updatedAt))
    }

    @Test fun collectionsListListsWalletsAndTags() = runBlocking {
        db.execute("INSERT INTO task_lists (id, user_id, name, position, is_archived, created_at, updated_at) VALUES ('l1', 'u', 'Kerja', 0, 0, 'x', 'x')")
        db.execute("INSERT INTO task_lists (id, user_id, name, position, is_archived, created_at, updated_at) VALUES ('l2', 'u', 'Arsip', 1, 1, 'x', 'x')")
        db.execute("INSERT INTO wallets (id, user_id, name, type, position, is_archived, created_at, updated_at) VALUES ('w1', 'u', 'BCA', 'bank', 0, 0, 'x', 'x')")
        db.execute("INSERT INTO tags (id, user_id, name, created_at, updated_at) VALUES ('t1', 'u', 'kerja', 'x', 'x')")
        val all = repo.watchCollections().first()
        assertEquals(setOf("task_list:l1", "wallet:w1", "tag:t1"), all.map { it.ref.key }.toSet())
        assertEquals("#kerja", all.first { it.ref.type == ShortcutType.Tag }.title)

        repo.pin(ShortcutRef(ShortcutType.Wallet, "w1"), "BCA")
        db.execute("UPDATE wallets SET name = 'BCA Utama' WHERE id = 'w1'")
        assertEquals("BCA Utama", repo.watchPinned().first().single().title)
    }

    @Test fun openingAnOlderItemBringsItForward() = runBlocking {
        note("a", "Lama", updated = "2026-11-01T00:00:00.000Z")
        note("b", "Baru", updated = "2026-11-20T00:00:00.000Z")
        assertEquals(listOf("note:b", "note:a"), repo.watchRecent().first().map { it.ref.key })
        now = HomeShortcuts.parseIsoMs("2026-11-21T08:00:00Z")!!
        repo.recordOpen("note", "a")
        val recent = repo.watchRecent().first()
        assertEquals(listOf("note:a", "note:b"), recent.map { it.ref.key }, "dibuka (baca saja) juga dihitung")
        assertEquals(now - 1, HomeShortcuts.parseIsoMs(recent[0].updatedAt)) // clock() dipanggil sekali
        // Jenis tak dikenal & tampilan diabaikan; dibuka di Sampah tidak muncul.
        repo.recordOpen("planet", "x")
        repo.recordOpen("view", "Review")
        db.execute("UPDATE notes SET deleted_at = 'x' WHERE id = 'a'")
        assertEquals(listOf("note:b"), repo.watchRecent().first().map { it.ref.key })
        assertEquals(1L, db.get("SELECT COUNT(*) FROM home_recent") { it.getLong(0)!! })
    }

    @Test fun openLogIsPruned() = runBlocking {
        repeat(HomeShortcutRepository.RECENT_KEEP + 15) { repo.recordOpen("note", "n$it") }
        assertEquals(HomeShortcutRepository.RECENT_KEEP.toLong(), db.get("SELECT COUNT(*) FROM home_recent") { it.getLong(0)!! })
        assertEquals(0L, db.get("SELECT COUNT(*) FROM home_recent WHERE id = 'note:n0'") { it.getLong(0)!! }, "yang tertua dibuang")
    }
}
