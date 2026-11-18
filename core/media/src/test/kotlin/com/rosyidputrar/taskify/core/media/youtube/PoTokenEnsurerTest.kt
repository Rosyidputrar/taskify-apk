/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.rosyidputrar.taskify.core.media.youtube

import com.rosyidputrar.taskify.core.innertube.PlaybackAuthState
import com.rosyidputrar.taskify.core.media.youtube.potoken.PoTokenResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tahap 5: ensure PoToken (tanpa WebView — mint dipalsukan). Mesin BotGuard
 * asli hanya jalan di perangkat (WebView sistem); pemetaan token ke
 * PlaybackAuthState + semantik best-effort diuji di sini.
 */
class PoTokenEnsurerTest {

    private fun authAnon(visitorData: String? = "visitor-abc") =
        PlaybackAuthState(visitorData = visitorData)

    private fun authLogin(dataSyncId: String = "delegated||user") =
        PlaybackAuthState(
            cookie = "SAPISID=x; __Secure-3PAPISID=y",
            dataSyncId = dataSyncId,
        )

    @Test
    fun `ensure mengisi semua field token saat mint sukses`() = runTest {
        var minted: Pair<String, String>? = null
        val ensurer = PoTokenEnsurer { videoId, sessionId ->
            minted = videoId to sessionId
            PoTokenResult(playerToken = "player-tok", sessionToken = "session-tok")
        }
        val out = ensurer.ensure("VID12345678", authAnon())
        assertEquals("VID12345678" to "visitor-abc", minted)
        assertEquals("player-tok", out.poTokenGvs)
        assertEquals("session-tok", out.poTokenGvsSession)
        assertEquals("VID12345678", out.poTokenGvsVideoId)
        assertEquals("player-tok", out.poTokenPlayer)
        assertEquals("VID12345678", out.poTokenPlayerVideoId)
        assertEquals("player-tok", out.poTokenSubs)
        assertEquals("VID12345678", out.poTokenSubsVideoId)
        assertTrue(out.webClientPoTokenEnabled)
    }

    @Test
    fun `ensure login memakai sessionId dari dataSyncId`() = runTest {
        var minted: Pair<String, String>? = null
        val ensurer = PoTokenEnsurer { videoId, sessionId ->
            minted = videoId to sessionId
            PoTokenResult(playerToken = "p", sessionToken = "s")
        }
        ensurer.ensure("VID12345678", authLogin())
        assertEquals("VID12345678" to "user", minted)
    }

    @Test
    fun `ensure memakai lagi token video yang sama tanpa mint ulang`() = runTest {
        var mintCalls = 0
        val ensurer = PoTokenEnsurer { _, _ ->
            mintCalls++
            PoTokenResult(playerToken = "p", sessionToken = "s")
        }
        val first = ensurer.ensure("VID12345678", authAnon())
        val second = ensurer.ensure("VID12345678", first)
        assertEquals(1, mintCalls)
        assertEquals(first, second)
    }

    @Test
    fun `ensure mint lagi untuk video lain`() = runTest {
        var mintCalls = 0
        val ensurer = PoTokenEnsurer { _, _ ->
            mintCalls++
            PoTokenResult(playerToken = "p", sessionToken = "s")
        }
        val first = ensurer.ensure("VID11111111", authAnon())
        val second = ensurer.ensure("VID22222222", first)
        assertEquals(2, mintCalls)
        assertEquals("VID22222222", second.poTokenGvsVideoId)
    }

    @Test
    fun `ensure kembalikan auth apa adanya bila mint gagal`() = runTest {
        val ensurer = PoTokenEnsurer { _, _ -> null }
        val auth = authAnon()
        val out = ensurer.ensure("VID12345678", auth)
        assertEquals(auth, out)
        assertNull(out.poTokenGvs)
    }

    @Test
    fun `ensure kembalikan auth apa adanya bila tanpa binding sesi`() = runTest {
        var mintCalls = 0
        val ensurer = PoTokenEnsurer { _, _ ->
            mintCalls++
            PoTokenResult(playerToken = "p", sessionToken = "s")
        }
        // Anonim tanpa visitorData = tidak ada binding -> mint tidak dipanggil.
        val out = ensurer.ensure("VID12345678", authAnon(visitorData = null))
        assertEquals(0, mintCalls)
        assertNull(out.poTokenGvs)
    }
}
