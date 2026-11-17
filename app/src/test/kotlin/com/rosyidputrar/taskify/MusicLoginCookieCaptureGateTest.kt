package com.rosyidputrar.taskify

import com.rosyidputrar.taskify.feature.media.LoginCookieCaptureGate
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regresi jebakan retry login (PLAN §B.2 / §D.1): `lastCapturedCookie` di
 * `YouTubeLoginWebViewClient` dulu TIDAK PERNAH di-reset, jadi cookie yang
 * sama sesudah "Coba lagi" didiamkan dan `onCookiesCaptured` tidak pernah
 * dipanggil kedua kalinya. Gate ini diekstrak supaya perilakunya bisa
 * dites tanpa WebView (Robolectric + WebView asli tidak deterministik).
 */
class MusicLoginCookieCaptureGateTest {
    private val completeCookie = "LOGIN_INFO=abc123; SAPISID=xyz789; SID=s1"

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
        assertTrue(gate.shouldCapture("LOGIN_INFO=abc123; SAPISID=berbeda; SID=s1"))
    }

    @Test fun incompleteCookieIsDroppedAndNotRecorded() {
        val gate = LoginCookieCaptureGate()
        // Tanpa LOGIN_INFO -> tidak lengkap, dibuang dan tidak dicatat.
        assertFalse(gate.shouldCapture("SAPISID=xyz789; SID=s1"))
        assertTrue(gate.shouldCapture(completeCookie))
        // Tanpa SAPISID/__Secure-*PAPISID -> tidak lengkap.
        assertFalse(gate.shouldCapture("LOGIN_INFO=abc123; SID=s1"))
        assertFalse(gate.shouldCapture(null))
        assertFalse(gate.shouldCapture(""))
    }

    @Test fun resetWithoutPriorCaptureIsHarmless() {
        val gate = LoginCookieCaptureGate()
        gate.reset()
        assertTrue(gate.shouldCapture(completeCookie))
    }
}
