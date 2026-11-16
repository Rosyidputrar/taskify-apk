package com.rosyidputrar.taskify.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RupiahTest {
    @Test fun formatsThousands() {
        assertEquals("Rp 1.250.000", Rupiah.format(1_250_000))
        assertEquals("Rp 0", Rupiah.format(0))
        assertEquals("−Rp 5.000", Rupiah.format(-5_000))
        assertEquals("999", Rupiah.format(999, withSymbol = false))
    }

    @Test fun compactFormat() {
        assertEquals("1,3 jt", Rupiah.compact(1_250_000))
        assertEquals("350 rb", Rupiah.compact(350_000))
        assertEquals("2 M", Rupiah.compact(2_000_000_000))
        assertEquals("750", Rupiah.compact(750))
    }

    @Test fun parsesIndonesianInput() {
        assertEquals(25_000, Rupiah.parse("25rb"))
        assertEquals(1_500_000, Rupiah.parse("1,5jt"))
        assertEquals(2_500_000, Rupiah.parse("2.5 juta"))
        assertEquals(50_000, Rupiah.parse("Rp 50.000"))
        assertEquals(75_000, Rupiah.parse("75000"))
        assertEquals(1_250, Rupiah.parse("1.250 rb")) // sama dengan webapp: titik terakhir = desimal
        assertEquals(2_000_000, Rupiah.parse("2m"))
        assertEquals(25_000, Rupiah.parse("25k"))
        assertEquals(1_000_000_000, Rupiah.parse("1 miliar"))
        assertEquals(25_000, Rupiah.parse("Rp. 25.000"))
        assertNull(Rupiah.parse("25xy"))
        assertTrue(Rupiah.isIncomplete("25r"))
        assertTrue(Rupiah.isIncomplete("1,5ju"))
        assertFalse(Rupiah.isIncomplete("25rb"))
        assertNull(Rupiah.parse("abc"))
        assertNull(Rupiah.parse(""))
    }
}
