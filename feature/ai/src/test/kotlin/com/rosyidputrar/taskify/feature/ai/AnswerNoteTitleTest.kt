package com.rosyidputrar.taskify.feature.ai

import kotlin.test.Test
import kotlin.test.assertEquals

class AnswerNoteTitleTest {
    @Test fun firstLineWithoutMarkdownMarkers() {
        assertEquals("Rencana minggu ini", answerNoteTitle("## Rencana **minggu** ini:\n\n- Senin: rapat"))
        assertEquals("Bayar listrik", answerNoteTitle("\n\n1. `Bayar` listrik\n2. Belanja"))
        assertEquals("Kutipan", answerNoteTitle("> Kutipan"))
    }

    @Test fun skipsCodeFenceAndEmptyMarkerLines() {
        assertEquals("val x = 1", answerNoteTitle("```kotlin\nval x = 1\n```"))
        assertEquals("Isi", answerNoteTitle("#\n---\nIsi"))
    }

    @Test fun longLineIsCutAtWordBoundary() {
        val t = answerNoteTitle("Ini adalah jawaban yang sangat panjang sekali sehingga judul catatannya perlu dipotong rapi")
        assertEquals("Ini adalah jawaban yang sangat panjang sekali sehingga…", t)
    }

    @Test fun blankFallsBack() {
        assertEquals("Jawaban Asisten", answerNoteTitle("   \n```\n```"))
    }
}
