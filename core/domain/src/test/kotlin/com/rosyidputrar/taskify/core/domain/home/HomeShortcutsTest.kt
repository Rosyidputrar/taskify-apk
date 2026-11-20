package com.rosyidputrar.taskify.core.domain.home

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeShortcutsTest {
    private fun sc(type: ShortcutType, id: String, pinned: Boolean = false, available: Boolean = true) =
        HomeShortcut(ShortcutRef(type, id), id, pinned = pinned, available = available)

    @Test fun refKeyRoundTripsAndRejectsJunk() {
        val r = ShortcutRef(ShortcutType.Note, "abc-123")
        assertEquals("note:abc-123", r.key)
        assertEquals(r, ShortcutRef.parse(r.key))
        assertEquals(QuickView.TasksOverdue, ShortcutRef.parse("view:TasksOverdue")?.view)
        // id boleh mengandung ':' (hanya pemisah pertama yang dipakai).
        assertEquals("a:b", ShortcutRef.parse("snippet:a:b")?.target)
        assertNull(ShortcutRef.parse("view:Ngawur"))
        assertNull(ShortcutRef.parse("planet:1"))
        assertNull(ShortcutRef.parse("note:"))
        assertNull(ShortcutRef.parse(":x"))
    }

    @Test fun rowShowsPinnedFirstThenRecentWithoutDuplicates() {
        val pinned = listOf(sc(ShortcutType.Note, "n1", pinned = true), sc(ShortcutType.Snippet, "gone", pinned = true, available = false), sc(ShortcutType.View, "Review", pinned = true))
        val recent = listOf(sc(ShortcutType.Note, "n1"), sc(ShortcutType.Journal, "j1"), sc(ShortcutType.Journal, "j1"), sc(ShortcutType.Snippet, "s1"))
        val row = HomeShortcuts.row(pinned, recent, showRecent = true).map { it.ref.key }
        assertEquals(listOf("note:n1", "view:Review", "journal_entry:j1", "snippet:s1"), row)
        assertEquals(listOf("note:n1", "view:Review"), HomeShortcuts.row(pinned, recent, showRecent = false).map { it.ref.key })
    }

    @Test fun recentIsCapped() {
        val recent = (1..20).map { sc(ShortcutType.Note, "n$it") }
        assertEquals(HomeShortcuts.MAX_RECENT, HomeShortcuts.row(emptyList(), recent, true).size)
    }

    @Test fun isoParsingMatchesKnownEpochs() {
        assertEquals(0L, HomeShortcuts.parseIsoMs("1970-01-01T00:00:00Z"))
        assertEquals(1_700_000_000_123L, HomeShortcuts.parseIsoMs("2023-11-14T22:13:20.123Z"))
        assertEquals(1_700_000_000_000L, HomeShortcuts.parseIsoMs("2023-11-14 22:13:20"))
        assertEquals(951_782_400_000L, HomeShortcuts.parseIsoMs("2000-02-29T00:00:00.000Z"))
        assertNull(HomeShortcuts.parseIsoMs("kemarin"))
        assertNull(HomeShortcuts.parseIsoMs(null))
    }

    @Test fun agoLabels() {
        val now = HomeShortcuts.parseIsoMs("2026-11-20T12:00:00Z")!!
        assertEquals("baru saja", HomeShortcuts.agoLabel("2026-11-20T11:59:40Z", now))
        assertEquals("5 mnt lalu", HomeShortcuts.agoLabel("2026-11-20T11:55:00Z", now))
        assertEquals("3 jam lalu", HomeShortcuts.agoLabel("2026-11-20T09:00:00Z", now))
        assertEquals("kemarin", HomeShortcuts.agoLabel("2026-11-19T08:00:00Z", now))
        assertEquals("4 hari lalu", HomeShortcuts.agoLabel("2026-11-16T12:00:00Z", now))
        assertNull(HomeShortcuts.agoLabel("2026-10-01T12:00:00Z", now))
        // Jam perangkat mundur: tidak pernah "-3 mnt lalu".
        assertEquals("baru saja", HomeShortcuts.agoLabel("2026-11-20T12:03:00Z", now))
    }
}
