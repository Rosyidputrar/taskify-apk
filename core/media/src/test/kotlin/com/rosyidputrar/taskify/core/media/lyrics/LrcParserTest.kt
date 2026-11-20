package com.rosyidputrar.taskify.core.media.lyrics

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Port `worker/lrc.ts`: penanda waktu, multi-penanda, meta, offset. */
class LrcParserTest {

    @Test fun `baris dasar terurai`() {
        val lines = LrcParser.parseLrc("[00:10.00]Hello\n[01:02.50]World\n[00:05]No frac")
        assertEquals(3, lines.size)
        assertEquals(5.0, lines[0].t)
        assertEquals("No frac", lines[0].text)
        assertEquals(10.0, lines[1].t)
        assertEquals(62.5, lines[2].t)
    }

    @Test fun `format jam ikut terurai`() {
        val lines = LrcParser.parseLrc("[01:02:03.45]Late")
        assertEquals(1, lines.size)
        assertEquals(3723.45, lines[0].t)
    }

    @Test fun `satu baris banyak penanda diduplikasi`() {
        val lines = LrcParser.parseLrc("[00:10.00][00:20.00]Chorus")
        assertEquals(2, lines.size)
        assertEquals(10.0, lines[0].t)
        assertEquals(20.0, lines[1].t)
        assertEquals("Chorus", lines[1].text)
    }

    @Test fun `tag metadata dilewati`() {
        val lines = LrcParser.parseLrc("[ti:Yellow]\n[ar:Coldplay]\n[length:04:26]\n[00:01.00]Start")
        assertEquals(1, lines.size)
        assertEquals("Start", lines[0].text)
    }

    @Test fun `offset menggeser semua penanda`() {
        val lines = LrcParser.parseLrc("[offset: -1000]\n[00:10.00]Late\n[00:00.50]Clamped")
        assertEquals(2, lines.size)
        assertEquals(0.0, lines[0].t)
        assertEquals("Clamped", lines[0].text)
        assertEquals(9.0, lines[1].t)
    }

    @Test fun `baris rusak dilewati tanpa menggagalkan`() {
        val lines = LrcParser.parseLrc("[00:10.00]Good\njust text\n[xx:yy]Bad\n[00:20.00]Also good")
        assertEquals(2, lines.size)
        assertEquals("Good", lines[0].text)
        assertEquals("Also good", lines[1].text)
    }

    @Test fun `duplikat persis dibuang dan hasil terurut`() {
        val lines = LrcParser.parseLrc("[00:20.00]B\n[00:10.00]A\n[00:10.00]A")
        assertEquals(2, lines.size)
        assertEquals("A", lines[0].text)
        assertEquals("B", lines[1].text)
    }

    @Test fun `penanda per kata dibuang teks dipertahankan`() {
        val lines = LrcParser.parseLrc("[00:35.66] <00:35.66> Look <00:38.10> at")
        assertEquals(1, lines.size)
        assertEquals(35.66, lines[0].t)
        assertEquals("Look  at", lines[0].text)
    }

    @Test fun `formatLrc menulis ulang baris`() {
        val text = LrcParser.formatLrc(LrcParser.parseLrc("[00:10.00]Hello\n[01:02.50]World"))
        assertEquals("[00:10.000]Hello\n[01:02.500]World", text)
    }

    @Test fun `teks kosong menghasilkan kosong`() {
        assertTrue(LrcParser.parseLrc("").isEmpty())
        assertTrue(LrcParser.parseLrc("[ti:Only meta]").isEmpty())
    }
}
