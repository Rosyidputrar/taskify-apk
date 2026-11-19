/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.rosyidputrar.taskify.core.media.mytube.potoken

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tahap 5: parser challenge BotGuard — murni JVM, tanpa WebView/jaringan.
 * (Fungsi-fungsi ini mem-parse HTML/JSON dari `youtube.com` + `GenerateIT`.)
 * Runner Robolectric diperlukan karena `org.json` adalah stub android.jar
 * (tanpa runner semua metodenya mengembalikan null).
 */
@RunWith(AndroidJUnit4::class)
class ChallengeParserTest {

    @Test
    fun `parseIntegrityToken mengembalikan Uint8Array dan lifetime`() {
        // Token base64 "AQID" = byte [1, 2, 3]; lifetime 3600 detik.
        val (u8, lifetime) = parseIntegrityToken("""["AQID", 3600]""")
        assertEquals("new Uint8Array([1,2,3])", u8)
        assertEquals(3600L, lifetime)
    }

    @Test
    fun `parseIntegrityToken melempar bila respons bukan array`() {
        assertThrows(IllegalArgumentException::class.java) {
            parseIntegrityToken("""{"a": 1}""")
        }
    }

    @Test
    fun `stringToJsUint8Array mengkode identifier UTF-8`() {
        assertEquals("new Uint8Array([65,66])", stringToJsUint8Array("AB"))
        assertEquals("new Uint8Array([118,105,100])", stringToJsUint8Array("vid"))
    }

    @Test
    fun `commaSeparatedBytesToBase64 memakai base64url tanpa padding berubah`() {
        // "Hello" = byte 72,101,108,108,111 -> "SGVsbG8=" (tanpa + atau /).
        assertEquals("SGVsbG8=", commaSeparatedBytesToBase64("72,101,108,108,111"))
    }

    @Test
    fun `parsePageChallenge membaca EVENT_ID dan bgChallenge`() {
        val html = """
            <html><head><script>
            ytcfg.set({"EVENT_ID":"evt-1","DELEGATED_SESSION_ID":"ds"});
            window.ytAtN({"R":{"bgChallenge":{
              "program":"prog-1",
              "globalName":"gn-1",
              "interpreterUrl":{"privateDoNotAccessOrElseTrustedResourceUrlWrappedValue":"//www.youtube.com/s/player/abc/base.js"}
            }}});
            </script></head></html>
        """.trimIndent()
        val page = parsePageChallenge(html)
        assertEquals("evt-1", page.configuration.getString("EVENT_ID"))
        val challenge = page.challenge
        assertEquals("prog-1", challenge.getString("program"))
        assertEquals("gn-1", challenge.getString("globalName"))
    }

    @Test
    fun `parsePageChallenge melempar bila EVENT_ID hilang`() {
        val html = """ytcfg.set({"X":1}); window.ytAtN({"R":{"bgChallenge":{"program":"p","globalName":"g"}}});"""
        assertThrows(PoTokenException::class.java) { parsePageChallenge(html) }
    }

    @Test
    fun `parsePageChallenge melempar bila challenge hilang`() {
        val html = """ytcfg.set({"EVENT_ID":"e"});"""
        assertThrows(PoTokenException::class.java) { parsePageChallenge(html) }
    }
}
