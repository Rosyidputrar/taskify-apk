package com.rosyidputrar.taskify.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CommonUtilsTest {
    @Test fun isoMatchesJavascriptToIsoString() {
        assertEquals("2026-11-15T11:13:11.000Z", Timestamps.format(1_794_741_191_000))
        assertEquals("1970-01-01T00:00:00.007Z", Timestamps.format(7))
        assertEquals(1_794_741_191_123, Timestamps.parseMillis("2026-11-15T11:13:11.123Z"))
        assertNull(Timestamps.parseMillis("bukan tanggal"))
    }

    @Test fun clipCountsCodePointsNotUtf16Units() {
        assertEquals("abc", Limits.clip("abcdef", 3))
        assertEquals("😀😀", Limits.clip("😀😀😀", 2)) // tidak membelah surrogate
        assertEquals("😀😀", Limits.clip("😀😀", 2))
        assertEquals("pendek", Limits.clip("pendek", 60))
    }

    @Test fun sha256Hex16MatchesKnownVector() {
        // SHA-256("abc") = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
        assertEquals("ba7816bf8f01cfea", Hashes.sha256Hex16("abc"))
        assertEquals("e3b0c44298fc1c14", Hashes.sha256Hex16(""))
    }
}
