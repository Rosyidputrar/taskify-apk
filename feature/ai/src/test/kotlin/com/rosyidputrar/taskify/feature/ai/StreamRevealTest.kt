package com.rosyidputrar.taskify.feature.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamRevealTest {
    @Test fun commitsWholeSentencesOnlyWhileStreaming() {
        assertEquals("", StreamReveal.committed("Halo apa kab", final = false))
        assertEquals("Halo.", StreamReveal.committed("Halo. Apa kab", final = false))
        assertEquals("Halo. Apa kabar?", StreamReveal.committed("Halo. Apa kabar? Saya", final = false))
        // Angka desimal & nominal bukan akhir kalimat.
        assertEquals("", StreamReveal.committed("Harganya Rp1.500 dan 3.5 kg", final = false))
        // Baris lengkap selalu boleh tampil.
        assertEquals("- Beli susu\n", StreamReveal.committed("- Beli susu\n- Beli te", final = false))
        assertEquals("Halo apa kab", StreamReveal.committed("Halo apa kab", final = true))
    }

    @Test fun insideCodeOnlyFullLines() {
        val raw = "Contoh:\n```kotlin\nval a = 1. toString()\nval b"
        assertTrue(StreamReveal.openFence(raw))
        assertEquals("Contoh:\n```kotlin\nval a = 1. toString()\n", StreamReveal.committed(raw, final = false))
        assertFalse(StreamReveal.openFence("```\nx\n```\n"))
        assertTrue(StreamReveal.openFence("~~~\n```\n"))
    }

    @Test fun blocksSplitSafely() {
        val md = """
            # Judul

            Paragraf satu.
            Masih paragraf satu.

            - satu
            - dua

              lanjutan item dua (berindentasi)

            ```python
            def f():

                return 1
            ```

            | a | b |
            |---|---|
            | 1 | 2 |
        """.trimIndent()
        val blocks = StreamReveal.blocks(md)
        assertEquals(5, blocks.size)
        assertEquals("# Judul", blocks[0])
        assertEquals("Paragraf satu.\nMasih paragraf satu.", blocks[1])
        assertTrue(blocks[2].contains("lanjutan item dua")) // tidak terbelah
        assertTrue(blocks[3].startsWith("```python") && blocks[3].endsWith("```") && blocks[3].contains("\n\n")) // baris kosong di kode tetap
        assertTrue(blocks[4].startsWith("| a | b |"))
        assertEquals(emptyList(), StreamReveal.blocks("\n\n  \n"))
        // Tidak ada isi yang hilang: semua blok digabung = teks asli tanpa baris kosong pemisah.
        assertEquals(md.replace("\n", ""), blocks.joinToString("") { it.replace("\n", "") })
    }
}
