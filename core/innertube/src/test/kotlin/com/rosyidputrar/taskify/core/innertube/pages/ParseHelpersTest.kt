package com.rosyidputrar.taskify.core.innertube.pages

import com.rosyidputrar.taskify.core.innertube.InnertubeJson
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Helper jalur JSON + durasi — cermin `helper jalur JSON` dan `parseDurationText` Worker. */
class ParseHelpersTest {

    private val data = InnertubeJson.parseToJsonElement(
        """{"a":[{"b":"x"}],"n":5,"s":"teks","runs":[{"text":"A"},{"text":" • "},{"text":"B"},{"text":""}]}""",
    )

    @Test fun `pick menelusuri objek dan indeks array`() {
        assertEquals("x", pickStr(data, "a.0.b"))
        assertEquals("x", (pick(data, "a.0.b") as JsonPrimitive).content)
        assertEquals(5, (pick(data, "n") as JsonPrimitive).content.toInt())
    }

    @Test fun `jalur tidak ada menghasilkan null dan pickStr string kosong`() {
        assertNull(pick(data, "tidak.ada"))
        assertEquals("", pickStr(data, "tidak.ada"))
        assertEquals("", pickStr(data, "n")) // bukan string
        assertEquals("", pickStr(data, "s.x")) // menembus primitif tidak melempar
    }

    @Test fun `runTexts membuang kosong dan pemisah bulat`() {
        val runs = pickArr(data, "runs")
        assertEquals(listOf("A", "B"), runTexts(runs))
        // runsText sebaliknya menyambung SEMUA run apa adanya, termasuk " • ".
        assertEquals("A • B", runsText(runs))
    }

    @Test fun `thumbs mengabaikan entri tanpa url dan memberi nilai bawaan ukuran`() {
        val el = InnertubeJson.parseToJsonElement(
            """[{"url":"https://x/1.jpg","width":60,"height":60},{"width":10},{"url":"https://x/2.jpg"}]""",
        )
        val list = thumbs(el.jsonArray)
        assertEquals(2, list.size)
        assertEquals(60, list[0].width)
        assertEquals(0, list[1].width)
    }

    @Test fun `deepFind menemukan kunci sedalam apa pun termasuk di dalam array`() {
        val el = InnertubeJson.parseToJsonElement("""{"a":{"b":[{"c":"x"}]},"t":1}""")
        assertEquals("x", (deepFind(el, "c") as? JsonPrimitive)?.content)
        assertEquals(1, (deepFind(el, "t") as? JsonPrimitive)?.content?.toIntOrNull())
        assertTrue(deepFind(el, "z") == null)
        assertTrue(deepFind(JsonNull, "z") == null)
        assertTrue(deepFind(JsonPrimitive("teks"), "z") == null)
    }

    @Test fun `parseDurationText angka polos dibaca sebagai detik`() {
        assertEquals(45, parseDurationText("45"))
        assertEquals(215, parseDurationText("215"))
        assertEquals(0, parseDurationText("0"))
    }

    @Test fun `parseDurationText m colon ss dengan pemisah titik dua maupun titik`() {
        assertEquals(225, parseDurationText("3:45"))
        assertEquals(225, parseDurationText("3.45"))
        assertEquals(398, parseDurationText("6.38"))
        assertEquals(0, parseDurationText("0:00"))
        assertEquals(605, parseDurationText("10:05"))
    }

    @Test fun `parseDurationText h colon mm colon ss dengan kedua pemisah`() {
        assertEquals(3723, parseDurationText("1:02:03"))
        assertEquals(3723, parseDurationText("1.02.03"))
        assertEquals(7200, parseDurationText("2:00:00"))
    }

    @Test fun `parseDurationText spasi ekstra dibuang dulu`() {
        assertEquals(225, parseDurationText("  3:45  "))
        assertEquals(225, parseDurationText("3 : 45"))
        assertEquals(3723, parseDurationText("1 : 02 : 03"))
        assertEquals(215, parseDurationText("\t3.35\n"))
    }

    @Test fun `parseDurationText siaran langsung dan teks rusak menghasilkan null bukan 0`() {
        assertNull(parseDurationText("LIVE"))
        assertNull(parseDurationText("abc"))
        assertNull(parseDurationText(""))
        assertNull(parseDurationText("3:60")) // detik 60 tidak sah
        assertNull(parseDurationText("3:45:60"))
        assertNull(parseDurationText("1:2:3:4"))
        assertNull(parseDurationText("3:45.12")) // pemisah campuran
        assertNull(parseDurationText("-3:45"))
        assertNull(parseDurationText("99999999999999999999:00")) // overflow aman
    }

    @Test fun `parseEpisodeDurationText membaca bentuk Indonesia dan m colon ss`() {
        // Sama persis dengan tes Worker: hanya bentuk Indonesia yang didukung,
        // sisanya jatuh ke parseDurationText ("m:ss").
        assertEquals(2520, parseEpisodeDurationText("42 mnt"))
        assertEquals(3900, parseEpisodeDurationText("1 j 5 mnt"))
        assertEquals(3600, parseEpisodeDurationText("1 j"))
        assertEquals(3720, parseEpisodeDurationText("1 jam 2 mnt"))
        assertEquals(30, parseEpisodeDurationText("30 dtk"))
        assertEquals(3630, parseEpisodeDurationText("1 j 0 mnt 30 dtk"))
        assertEquals(215, parseEpisodeDurationText("3:35"))
        assertEquals(3723, parseEpisodeDurationText("1:02:03"))
        assertNull(parseEpisodeDurationText("LIVE"))
        assertNull(parseEpisodeDurationText(""))
    }

    @Test fun `formatDuration membulatkan ke bawah dan memakai m colon ss`() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("3:45", formatDuration(225))
        assertEquals("1:02:03", formatDuration(3723))
        assertEquals("6:38", formatDuration(398))
    }

    @Test fun `isYearText hanya menerima empat digit`() {
        assertTrue(isYearText("2022"))
        assertTrue(!isYearText("22"))
        assertTrue(!isYearText("20222"))
        assertTrue(!isYearText("Tahun 2022"))
    }

    @Test fun `pickStr hanya membaca teks string bukan primitif angka`() {
        // Sama seperti Worker: pickStr untuk angka menghasilkan string kosong.
        val el = buildJsonObject { put("n", 42) }
        assertEquals("", pickStr(el, "n"))
        val obj = JsonObject(mapOf("x" to JsonObject(emptyMap())))
        assertEquals("", pickStr(obj, "x")) // objek bukan string
    }
}
