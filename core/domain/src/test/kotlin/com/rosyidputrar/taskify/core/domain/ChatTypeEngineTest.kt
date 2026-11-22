package com.rosyidputrar.taskify.core.domain

import com.rosyidputrar.taskify.core.domain.tools.CharState
import com.rosyidputrar.taskify.core.domain.tools.ChatTypeEngine
import com.rosyidputrar.taskify.core.domain.tools.ChatVisualTheme
import com.rosyidputrar.taskify.core.domain.tools.TypingDifficulty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChatTypeEngineTest {

    @Test fun characterDiffMarksCorrectWrongCursorAndPending() {
        val d = ChatTypeEngine.diff(target = "Halo", typed = "Hx")
        assertEquals(
            listOf(
                'H' to CharState.Correct,
                'a' to CharState.Wrong,
                'l' to CharState.Cursor,
                'o' to CharState.Pending,
            ),
            d.map { it.char to it.state },
        )
    }

    @Test fun typoTriggersErrorAndBackspaceDoesNotEraseAccuracyPenalty() {
        val target = "Siap kirim"
        // Ketik "Si" benar -> 2 ketukan, 0 error, combo 2
        val s1 = ChatTypeEngine.processInputChange(target, "", "Si", 0, 0, 0, 0, emptyMap())
        assertEquals(2, s1.totalKeystrokes)
        assertEquals(0, s1.errorKeystrokes)
        assertEquals(2, s1.combo)
        assertFalse(s1.typoTriggered)

        // Salah ketik "Sik" (seharusnya 'a') -> typoTriggered = true, combo reset ke 0, 'a' tercatat di missedChars
        val s2 = ChatTypeEngine.processInputChange(target, "Si", "Sik", s1.totalKeystrokes, s1.errorKeystrokes, s1.combo, s1.maxCombo, s1.missedChars)
        assertTrue(s2.typoTriggered)
        assertEquals(3, s2.totalKeystrokes)
        assertEquals(1, s2.errorKeystrokes)
        assertEquals(0, s2.combo)
        assertEquals(1, s2.missedChars['a'])

        // Hapus dengan backspace ke "Si" -> errorKeystrokes tetap 1 (anti-curang)
        val s3 = ChatTypeEngine.processInputChange(target, "Sik", "Si", s2.totalKeystrokes, s2.errorKeystrokes, s2.combo, s2.maxCombo, s2.missedChars)
        assertFalse(s3.typoTriggered)
        assertEquals(3, s3.totalKeystrokes)
        assertEquals(1, s3.errorKeystrokes)

        // Ketik sisa "Siap kirim" dengan benar -> completed = true
        val s4 = ChatTypeEngine.processInputChange(target, "Si", "Siap kirim", s3.totalKeystrokes, s3.errorKeystrokes, s3.combo, s3.maxCombo, s3.missedChars)
        assertTrue(s4.completed)
        assertEquals(11, s4.totalKeystrokes)
        assertEquals(1, s4.errorKeystrokes)
        assertEquals(8, s4.combo)
    }

    @Test fun comboMilestoneFiresAtMultiplesOf25() {
        val target = "a".repeat(30)
        val s24 = ChatTypeEngine.processInputChange(target, "", "a".repeat(24), 0, 0, 0, 0, emptyMap())
        assertFalse(s24.comboMilestone)
        val s25 = ChatTypeEngine.processInputChange(target, "a".repeat(24), "a".repeat(25), s24.totalKeystrokes, 0, s24.combo, s24.maxCombo, emptyMap())
        assertTrue(s25.comboMilestone)
        assertEquals(25, s25.combo)
    }

    @Test fun wpmAccuracyAndSessionSummary() {
        // 50 karakter (= 10 kata) dalam 10 detik (= 1/6 menit) -> 60 WPM, 300 CPM
        val m1 = ChatTypeEngine.computeTurnMetrics(targetLength = 50, elapsedMs = 10_000L, totalKeystrokes = 52, errorKeystrokes = 2)
        assertEquals(60, m1.wpm)
        assertEquals(300, m1.cpm)
        assertEquals(96, m1.accuracyPct)

        val m2 = ChatTypeEngine.computeTurnMetrics(targetLength = 50, elapsedMs = 5_000L, totalKeystrokes = 50, errorKeystrokes = 0)
        assertEquals(120, m2.wpm)

        val summary = ChatTypeEngine.summarizeSession(
            turns = listOf(m1, m2),
            maxCombo = 42,
            missedChars = mapOf(',' to 3, 'e' to 1),
            previousBestWpm = 75,
        )
        assertEquals(90, summary.averageWpm)
        assertEquals(120, summary.peakWpm)
        assertTrue(summary.isNewRecord)
        assertEquals(listOf("koma (,) (3×)", "'e' (1×)"), summary.topMissedLabels)
    }

    @Test fun aiStoryJsonParserHandlesMarkdownFence() {
        val raw = """
            Berikut skenarionya:
            ```json
            {
              "title": "Diskusi Arsitektur Kotlin",
              "category": "Tech",
              "partnerName": "Arif",
              "partnerRole": "Tech Lead",
              "suggestedTheme": "Terminal",
              "turns": [
                { "partner": "Sudah cek modul baru?", "reply": "Sudah, semua unit test di modul domain lulus tanpa kendala." },
                { "partner": "Gimana performa UI-nya?", "reply": "Recomposition sudah diminimalkan dan animasi berjalan mulus 60 fps." }
              ]
            }
            ```
        """.trimIndent()
        val story = ChatTypeEngine.parseAiStoryJson(raw, TypingDifficulty.Standard, "Kotlin")
        assertNotNull(story)
        assertEquals("Diskusi Arsitektur Kotlin", story.title)
        assertEquals("Arif", story.partnerName)
        assertEquals(ChatVisualTheme.Terminal, story.suggestedTheme)
        assertEquals(2, story.turns.size)
        assertTrue(story.isAiGenerated)
    }
}
