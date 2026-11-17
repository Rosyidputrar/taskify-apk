package com.rosyidputrar.taskify.core.innertube.utils

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `hasFullYouTubeLoginSession` (gate sesi penuh, PLAN §C.2) dan
 * `cookieNames` (daftar nama cookie tanpa nilai).
 *
 * Sesi parsial = kondisi gagal tes HP 18 Nov 01:54: hanya `LOGIN_INFO` +
 * satu PAPISID, server menyatakan `"logged_in":"0"`.
 */
class AuthUtilsTest {

    private val partialSession =
        "LOGIN_INFO=AFmmF5...; __Secure-1PAPISID=abc123; VISITOR_INFO=xyz"

    private val fullSession =
        "LOGIN_INFO=AFmmF5...; SID=RAHASIA_SID; HSID=RAHASIA_HSID; " +
            "SSID=RAHASIA_SSID; SAPISID=RAHASIA_SAPISID"

    private val fullSessionSecure =
        "LOGIN_INFO=AFmmF5...; __Secure-3PSID=RAHASIA_PSID; HSID=RAHASIA_HSID; " +
            "SSID=RAHASIA_SSID; __Secure-3PAPISID=RAHASIA_PAPISID"

    @Test
    fun `sesi parsial LOGIN_INFO plus satu PAPISID bukan sesi penuh`() {
        assertFalse(hasFullYouTubeLoginSession(partialSession))
    }

    @Test
    fun `sesi penuh semua kelompok terpenuhi`() {
        assertTrue(hasFullYouTubeLoginSession(fullSession))
    }

    @Test
    fun `sesi penuh varian Secure PSID dan PAPISID`() {
        assertTrue(hasFullYouTubeLoginSession(fullSessionSecure))
    }

    @Test
    fun `sesi penuh minus HSID bukan sesi penuh`() {
        val cookie = "LOGIN_INFO=x; SID=y; SSID=z; SAPISID=w"
        assertFalse(hasFullYouTubeLoginSession(cookie))
    }

    @Test
    fun `sesi penuh minus SSID bukan sesi penuh`() {
        val cookie = "LOGIN_INFO=x; SID=y; HSID=z; SAPISID=w"
        assertFalse(hasFullYouTubeLoginSession(cookie))
    }

    @Test
    fun `sesi penuh minus LOGIN_INFO bukan sesi penuh`() {
        val cookie = "SID=y; HSID=z; SSID=a; SAPISID=w"
        assertFalse(hasFullYouTubeLoginSession(cookie))
    }

    @Test
    fun `sesi penuh minus SID bukan sesi penuh`() {
        val cookie = "LOGIN_INFO=x; HSID=z; SSID=a; SAPISID=w"
        assertFalse(hasFullYouTubeLoginSession(cookie))
    }

    @Test
    fun `sesi penuh minus APISID bukan sesi penuh`() {
        val cookie = "LOGIN_INFO=x; SID=y; HSID=z; SSID=a"
        assertFalse(hasFullYouTubeLoginSession(cookie))
    }

    @Test
    fun `cookie null dan kosong bukan sesi penuh`() {
        assertFalse(hasFullYouTubeLoginSession(null))
        assertFalse(hasFullYouTubeLoginSession(""))
        assertFalse(hasFullYouTubeLoginSession("   "))
    }

    @Test
    fun `nilai kosong atau spasi dianggap tidak ada`() {
        val cookie = "LOGIN_INFO= ; SID=y; HSID=z; SSID=a; SAPISID=w"
        assertFalse(hasFullYouTubeLoginSession(cookie))
    }

    @Test
    fun `PSIDTS saja tanpa SID bukan bukti SID`() {
        val cookie = "LOGIN_INFO=x; __Secure-3PSIDTS=123; HSID=z; SSID=a; SAPISID=w"
        assertFalse(hasFullYouTubeLoginSession(cookie))
    }

    @Test
    fun `cookieNames hanya nama terurut tanpa duplikat tanpa nilai`() {
        val cookie = "SID=RAHASIA123; LOGIN_INFO=RAHASIA456; SID=RAHASIA789; HSID=RAHASIA000"
        val names = cookieNames(cookie)
        assertEquals(listOf("HSID", "LOGIN_INFO", "SID"), names)
        assertFalse(names.any { it.contains("RAHASIA") })
        assertFalse(names.joinToString().contains("RAHASIA123"))
        assertFalse(names.joinToString().contains("RAHASIA456"))
    }

    @Test
    fun `cookieNames null dan kosong menjadi list kosong`() {
        assertEquals(emptyList(), cookieNames(null))
        assertEquals(emptyList(), cookieNames(""))
        assertEquals(emptyList(), cookieNames("   "))
    }

    @Test
    fun `gate lama tetap menerima sesi parsial untuk kompatibilitas`() {
        assertTrue(hasCompleteYouTubeLoginCookies(partialSession))
    }
}
