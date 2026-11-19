package com.rosyidputrar.taskify

import com.rosyidputrar.taskify.feature.media.LoginCookieCaptureGate
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regresi jebakan retry login (PLAN §B.2 / §D.1): `lastCapturedCookie` di
 * `MyTubeLoginWebViewClient` dulu TIDAK PERNAH di-reset, jadi cookie yang
 * sama sesudah "Coba lagi" didiamkan dan `onCookiesCaptured` tidak pernah
 * dipanggil kedua kalinya. Gate ini diekstrak supaya perilakunya bisa
 * dites tanpa WebView (Robolectric + WebView asli tidak deterministik).
 */
class MusicLoginCookieCaptureGateTest {
    // Sesi PENUH (PLAN §C.2): LOGIN_INFO + SID + HSID + SSID + SAPISID.
    private val completeCookie = "LOGIN_INFO=abc123; SID=s1; HSID=h1; SSID=ss1; SAPISID=xyz789"
    // Sesi PARSIAL persis kondisi gagal tes HP 18 Nov 01:54: hanya
    // LOGIN_INFO + satu PAPISID (gate lama meneruskannya, server menolak
    // dengan "logged_in":"0") — gate sesi penuh HARUS menolaknya.
    private val partialCookie = "LOGIN_INFO=abc123; SAPISID=xyz789"

    @Test fun partialSessionIsRejected() {
        val gate = LoginCookieCaptureGate()
        assertFalse(gate.shouldCapture(partialCookie), "LOGIN_INFO + satu PAPISID saja bukan sesi penuh")
        assertFalse(gate.shouldCapture("LOGIN_INFO=abc123; __Secure-3PAPISID=xyz789"))
        // Parsial yang ditolak TIDAK dicatat: sesi penuh sesudahnya tetap diteruskan.
        assertTrue(gate.shouldCapture(completeCookie))
    }

    @Test fun fullSessionIsAccepted() {
        val gate = LoginCookieCaptureGate()
        assertTrue(gate.shouldCapture(completeCookie), "sesi penuh harus diteruskan")
    }

    @Test fun fullSessionTwiceWithoutResetIsDroppedSecondTime() {
        val gate = LoginCookieCaptureGate()
        assertTrue(gate.shouldCapture(completeCookie))
        assertFalse(gate.shouldCapture(completeCookie), "sesi penuh yang sama 2x tanpa reset: kedua kalinya dibuang")
    }

    @Test fun fullSessionAfterResetIsAcceptedAgain() {
        val gate = LoginCookieCaptureGate()
        assertTrue(gate.shouldCapture(completeCookie))
        assertFalse(gate.shouldCapture(completeCookie))
        gate.reset()
        assertTrue(gate.shouldCapture(completeCookie), "sesi penuh yang sama SESUDAH reset harus diteruskan lagi")
    }

    @Test fun fullSessionMinusHsidOrSsidIsRejected() {
        val gate = LoginCookieCaptureGate()
        assertFalse(gate.shouldCapture("LOGIN_INFO=abc123; SID=s1; SSID=ss1; SAPISID=xyz789"), "tanpa HSID bukan sesi penuh")
        assertFalse(gate.shouldCapture("LOGIN_INFO=abc123; SID=s1; HSID=h1; SAPISID=xyz789"), "tanpa SSID bukan sesi penuh")
        assertFalse(gate.shouldCapture("LOGIN_INFO=abc123; HSID=h1; SSID=ss1; SAPISID=xyz789"), "tanpa SID bukan sesi penuh")
        assertTrue(gate.shouldCapture(completeCookie))
    }

    @Test fun captureSameCookieTwiceWithResetInBetween() {
        val gate = LoginCookieCaptureGate()
        assertTrue(gate.shouldCapture(completeCookie), "tangkapan pertama harus diteruskan")
        assertFalse(gate.shouldCapture(completeCookie), "cookie sama tanpa reset harus dibuang (dedup)")
        gate.reset()
        assertTrue(
            gate.shouldCapture(completeCookie),
            "cookie sama SESUDAH reset (\"Coba lagi\") harus diteruskan lagi",
        )
    }

    @Test fun duplicateWithoutResetIsDropped() {
        val gate = LoginCookieCaptureGate()
        assertTrue(gate.shouldCapture(completeCookie))
        assertFalse(gate.shouldCapture(completeCookie))
        assertFalse(gate.shouldCapture(completeCookie))
    }

    @Test fun differentCookieAfterCaptureIsAccepted() {
        val gate = LoginCookieCaptureGate()
        assertTrue(gate.shouldCapture(completeCookie))
        assertTrue(gate.shouldCapture("LOGIN_INFO=abc123; SAPISID=berbeda; SID=s1; HSID=h1; SSID=ss1"))
    }

    @Test fun incompleteCookieIsDroppedAndNotRecorded() {
        val gate = LoginCookieCaptureGate()
        // Tanpa LOGIN_INFO -> tidak lengkap, dibuang dan tidak dicatat.
        assertFalse(gate.shouldCapture("SAPISID=xyz789; SID=s1; HSID=h1; SSID=ss1"))
        assertTrue(gate.shouldCapture(completeCookie))
        // Tanpa SAPISID/__Secure-*PAPISID -> tidak lengkap.
        assertFalse(gate.shouldCapture("LOGIN_INFO=abc123; SID=s1; HSID=h1; SSID=ss1"))
        assertFalse(gate.shouldCapture(null))
        assertFalse(gate.shouldCapture(""))
    }

    @Test fun resetWithoutPriorCaptureIsHarmless() {
        val gate = LoginCookieCaptureGate()
        gate.reset()
        assertTrue(gate.shouldCapture(completeCookie))
    }
}
