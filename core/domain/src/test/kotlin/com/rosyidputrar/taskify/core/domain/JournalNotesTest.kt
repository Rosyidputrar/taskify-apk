package com.rosyidputrar.taskify.core.domain

import com.rosyidputrar.taskify.core.domain.journal.JournalEntry
import com.rosyidputrar.taskify.core.domain.journal.MoodPoint
import com.rosyidputrar.taskify.core.domain.journal.ReflectionKind
import com.rosyidputrar.taskify.core.domain.journal.agoLabel
import com.rosyidputrar.taskify.core.domain.journal.journalToMarkdown
import com.rosyidputrar.taskify.core.domain.journal.monthAgo
import com.rosyidputrar.taskify.core.domain.journal.moodSeries
import com.rosyidputrar.taskify.core.domain.journal.moodStats
import com.rosyidputrar.taskify.core.domain.journal.reflectionTemplate
import com.rosyidputrar.taskify.core.domain.notes.FREE_GROUP
import com.rosyidputrar.taskify.core.domain.notes.Note
import com.rosyidputrar.taskify.core.domain.notes.escapeLike
import com.rosyidputrar.taskify.core.domain.notes.groupLabel
import com.rosyidputrar.taskify.core.domain.notes.groupNotes
import com.rosyidputrar.taskify.core.domain.notes.noteDateLabel
import com.rosyidputrar.taskify.core.domain.notes.notesToMarkdown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JournalNotesTest {
    @Test fun reflectionTemplates() {
        val week = reflectionTemplate(ReflectionKind.Week, "2026-10-05", "2026-10-11")
        assertEquals("Refleksi minggu 5 Okt – 11 Okt 2026", week.title)
        assertTrue(week.content.startsWith("### Yang berjalan baik\n- \n\n"))
        assertTrue(week.content.contains("### Fokus minggu depan\n- [ ] "))
        assertEquals("Refleksi bulan Oktober 2026", reflectionTemplate(ReflectionKind.Month, "2026-10-01", "2026-10-31").title)
    }

    @Test fun seriesAndStats() {
        val rows = listOf(MoodPoint("2026-10-05", 4.0, 1), MoodPoint("2026-10-06", 2.5, 2), MoodPoint("2026-10-07", null, 1))
        val series = moodSeries(rows, "2026-10-07", 7)
        assertEquals(7, series.size)
        assertEquals("2026-10-01", series.first().day)
        assertEquals(0, series.first().entries)
        val stats = moodStats(series)
        assertEquals(3.25, stats.average)
        assertEquals(3, stats.daysWritten)
        assertEquals(2, stats.daysWithMood)
        assertEquals(listOf(0, 0, 1, 1, 0), stats.distribution) // 2,5 dibulatkan ke 3
        assertEquals(4.0, stats.byWeekday[0]) // 5 Okt 2026 = Senin
        assertNull(stats.byWeekday[3])
    }

    @Test fun onThisDayHelpers() {
        assertEquals("2026-09-07", monthAgo("2026-10-07"))
        assertNull(monthAgo("2026-03-31"))
        assertEquals("2 tahun lalu", agoLabel("2024-10-07", "2026-10-07"))
        assertEquals("1 bulan lalu", agoLabel("2026-09-07", "2026-10-07"))
    }

    private fun note(id: String, date: String?, pinned: Boolean = false) = Note(id, date, id, "", pinned, "", "")

    @Test fun notesGrouping() {
        val g = groupNotes(listOf(note("p", "2026-10-01", true), note("a", "2026-10-07"), note("b", "2026-09-30"), note("c", null), note("d", "2026-10-02")), trash = false)
        assertEquals(listOf("p"), g.pinned.map { it.id })
        assertEquals(listOf("2026-10", "2026-09", FREE_GROUP), g.groups.map { it.first })
        assertEquals(listOf("a", "d"), g.groups[0].second.map { it.id })
        assertTrue(groupNotes(listOf(note("p", null, true)), trash = true).pinned.isEmpty())
        assertEquals("Oktober 2026", groupLabel("2026-10"))
        assertEquals("Tanpa tanggal", groupLabel(FREE_GROUP))
        assertEquals("Bebas", noteDateLabel(null, "2026-10-07"))
        assertEquals("Hari ini", noteDateLabel("2026-10-07", "2026-10-07"))
    }

    @Test fun escapeAndExport() {
        assertEquals("100\\%\\_a\\\\", escapeLike("100%_a\\"))
        assertEquals("## Tanpa judul\n\n_2026-10-07_\n\nisi\n", notesToMarkdown(listOf(Note("1", "2026-10-07", " ", " isi ", false, "", ""))))
        val md = journalToMarkdown(listOf(JournalEntry("1", "2026-10-07", "Hari", "isi", 4, false, "", ""), JournalEntry("2", "2026-10-06", null, "dua", null, false, "", "")))
        assertEquals("## 2026-10-07 — Hari\n\n_Mood: Baik_\n\nisi\n\n---\n\n## 2026-10-06\n\ndua\n", md)
    }
}

class CapturePrefixTest {
    @Test fun prefixes() {
        val j = com.rosyidputrar.taskify.core.domain.capture.parseCapturePrefix("j: hari ini capek tapi puas #refleksi")!!
        assertEquals(com.rosyidputrar.taskify.core.domain.capture.CaptureTarget.Journal, j.target)
        assertEquals("hari ini capek tapi puas", j.body)
        assertEquals(listOf("refleksi"), j.tags)
        val c = com.rosyidputrar.taskify.core.domain.capture.parseCapturePrefix("Catatan:  ide fitur   export #App")!!
        assertEquals(com.rosyidputrar.taskify.core.domain.capture.CaptureTarget.Note, c.target)
        assertEquals("ide fitur export", c.body)
        assertEquals(listOf("app"), c.tags)
        assertNull(com.rosyidputrar.taskify.core.domain.capture.parseCapturePrefix("besok rapat jam 9"))
        assertEquals("baris satu\nbaris dua" to listOf("x"), com.rosyidputrar.taskify.core.domain.capture.extractTags("baris satu #x\nbaris dua"))
    }
}
