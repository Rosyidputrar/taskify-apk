package com.rosyidputrar.taskify.core.innertube.utils

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `scrubRawDetail`: nilai sensitif tidak pernah lolos, potongan tepat
 * 500 karakter, null/kosong → "respons kosong".
 */
class RawDetailTest {

    @Test
    fun `cookie SAPISID dan Authorization tidak pernah muncul di output`() {
        val raw = """{"responseContext":{"visitorData":"CgtSECRET123"},"actions":[]}""" +
            """Cookie: SAPISID=rahasia-sapisid-123; HSID=rahasia-hsid-456; SSID=rahasia-ssid-789; """ +
            """Authorization: SAPISIDHASH 1700000000_abcdef1234567890"""
        val scrubbed = scrubRawDetail(raw)
        assertFalse(scrubbed.contains("rahasia-sapisid-123"), scrubbed)
        assertFalse(scrubbed.contains("rahasia-hsid-456"), scrubbed)
        assertFalse(scrubbed.contains("rahasia-ssid-789"), scrubbed)
        assertFalse(scrubbed.contains("CgtSECRET123"), scrubbed)
        assertFalse(scrubbed.contains("abcdef1234567890"), scrubbed)
        assertFalse(scrubbed.contains("SAPISID"), scrubbed)
        assertFalse(scrubbed.contains("HSID"), scrubbed)
        assertFalse(scrubbed.contains("SSID"), scrubbed)
        assertFalse(scrubbed.contains("Authorization"), scrubbed)
        assertFalse(scrubbed.contains("SAPISIDHASH"), scrubbed)
        assertFalse(scrubbed.contains("VISITOR_DATA"), scrubbed)
        assertTrue(scrubbed.contains("[redacted]"), scrubbed)
    }

    @Test
    fun `SAPISIDHASH yang berdiri sendiri ikut dihapus`() {
        val raw = """{"error":{"message":"gagal"},"debug":"SAPISIDHASH 1700000000_deadbeefcafe"}"""
        val scrubbed = scrubRawDetail(raw)
        assertFalse(scrubbed.contains("deadbeefcafe"), scrubbed)
        assertFalse(scrubbed.contains("SAPISIDHASH"), scrubbed)
        assertTrue(scrubbed.contains("gagal"), scrubbed)
    }

    @Test
    fun `cookie sama-dengan ikut dihapus nilainya`() {
        val raw = """cookie=SAPISID=abc123; SID=xyz789; LOGIN_INFO=aman"""
        val scrubbed = scrubRawDetail(raw)
        assertFalse(scrubbed.contains("abc123"), scrubbed)
        assertFalse(scrubbed.contains("xyz789"), scrubbed)
        assertTrue(scrubbed.contains("LOGIN_INFO=aman"), scrubbed)
    }

    @Test
    fun `potongan tepat 500 karakter`() {
        val raw = "x".repeat(2000)
        val scrubbed = scrubRawDetail(raw)
        assertEquals(500, scrubbed.length)
        assertEquals("x".repeat(500), scrubbed)
    }

    @Test
    fun `teks pendek tanpa rahasia tampil apa adanya`() {
        val raw = """{"responseContext":{},"actions":[]}"""
        assertEquals(raw, scrubRawDetail(raw))
    }

    @Test
    fun `null dan kosong menjadi respons kosong`() {
        assertEquals("respons kosong", scrubRawDetail(null))
        assertEquals("respons kosong", scrubRawDetail(""))
        assertEquals("respons kosong", scrubRawDetail("   "))
    }

    @Test
    fun `badan kosong beserta status`() {
        assertEquals("respons kosong, status 200", emptyRawDetail(200))
    }
}
