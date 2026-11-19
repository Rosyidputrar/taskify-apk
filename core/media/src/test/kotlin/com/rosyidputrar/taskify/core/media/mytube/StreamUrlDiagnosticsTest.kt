package com.rosyidputrar.taskify.core.media.mytube

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Diagnostik URL stream 403: hanya NAMA parameter yang dilaporkan,
 * tidak pernah nilainya (query berisi signature/IP).
 */
class StreamUrlDiagnosticsTest {
    private val url =
        "https://rr2---sn-a5mekn7z.googlevideo.com/videoplayback?expire=1793000000&ip=203.0.113.7&sig=RAHASIA&sparams=expire,ip,sig&itag=251"

    @Test fun `nama parameter query dikenali`() {
        val names = streamUrlQueryParamNames(url)
        assertTrue("expire" in names)
        assertTrue("sig" in names)
        assertTrue("itag" in names)
        assertFalse("n" in names, "URL ANDROID tanpa n")
        assertFalse("pot" in names, "tanpa PoToken")
    }

    @Test fun `keberadaan n pot sig expire ip dilaporkan sebagai teks`() {
        assertEquals(
            "n: tidak ada, pot: tidak ada, sig: ada, expire: ada, ip: ada",
            streamUrlParamPresence(url),
        )
        assertEquals(
            "n: ada, pot: tidak ada, sig: tidak ada, expire: ada, ip: tidak ada",
            streamUrlParamPresence("https://rr.test/videoplayback?n=ABC123&expire=1793000000"),
        )
    }

    @Test fun `ip dilaporkan ada bila ada dan tidak ada bila tidak ada, tanpa nilai`() {
        val presence = streamUrlParamPresence(url)
        assertTrue(presence.contains("ip: ada"), presence)
        assertFalse(presence.contains("203.0.113.7"), presence)
        assertTrue(
            streamUrlParamPresence("https://rr.test/videoplayback?expire=1793000000")
                .contains("ip: tidak ada"),
        )
    }

    @Test fun `nilai parameter tidak pernah ikut dalam teks diagnostik`() {
        val presence = streamUrlParamPresence(url)
        assertFalse(presence.contains("RAHASIA"), presence)
        assertFalse(presence.contains("203.0.113.7"), presence)
        assertFalse(presence.contains("1793000000"), presence)
        assertFalse(presence.contains("videoplayback"), presence)
    }

    @Test fun `url tanpa query dan url kosong jadi tidak diketahui`() {
        assertEquals(
            "n: tidak ada, pot: tidak ada, sig: tidak ada, expire: tidak ada, ip: tidak ada",
            streamUrlParamPresence("https://rr.test/videoplayback"),
        )
        assertEquals("tidak diketahui", streamUrlParamPresence(null))
        assertEquals("tidak diketahui", streamUrlParamPresence("  "))
    }
}
