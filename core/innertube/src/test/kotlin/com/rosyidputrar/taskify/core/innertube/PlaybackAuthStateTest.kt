package com.rosyidputrar.taskify.core.innertube

import com.rosyidputrar.taskify.core.innertube.models.YouTubeClient
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Normalisasi, fingerprint, dan resolver PoToken `PlaybackAuthState`. */
class PlaybackAuthStateTest {
    @Test
    fun normalizedMembuangSpasiDanStringNull() {
        val s = PlaybackAuthState(
            cookie = "  SAPISID=abc  ",
            visitorData = "null",
            dataSyncId = "  ",
            poToken = "",
        ).normalized()
        assertEquals("SAPISID=abc", s.cookie)
        assertNull(s.visitorData)
        assertNull(s.dataSyncId)
        assertNull(s.poToken)
    }

    @Test
    fun normalizedDataSyncIdMembukaPercentEscapeDanMerapatkanPemisah() {
        assertEquals(
            "delegated id||user",
            PlaybackAuthState(dataSyncId = "delegated%20id||user").normalized().dataSyncId,
        )
        assertEquals("user", PlaybackAuthState(dataSyncId = "||user").normalized().dataSyncId)
        assertEquals("delegated", PlaybackAuthState(dataSyncId = "delegated||").normalized().dataSyncId)
        assertEquals("solo", PlaybackAuthState(dataSyncId = "solo").normalized().dataSyncId)
    }

    @Test
    fun normalizedMembuangNullDenganKapitalApaPun() {
        assertNull(PlaybackAuthState(visitorData = "NULL").normalized().visitorData)
        assertNull(PlaybackAuthState(visitorData = "Null").normalized().visitorData)
        assertNull(PlaybackAuthState(cookie = " null ").normalized().cookie)
    }

    @Test
    fun fingerprintStabilSetelahNormalisasi() {
        val raw = PlaybackAuthState(visitorData = "  CgttZXN0  ", dataSyncId = "d%20x||u")
        val clean = PlaybackAuthState(visitorData = "CgttZXN0", dataSyncId = "d x||u")
        assertEquals(clean.fingerprint, raw.normalized().fingerprint)
        assertEquals(clean.streamCacheFingerprint, raw.normalized().streamCacheFingerprint)
        assertNotEquals(clean.fingerprint, raw.fingerprint)
    }

    @Test
    fun fingerprintDeterministikDanBerubahBilaFieldBerubah() {
        val a = PlaybackAuthState.EMPTY
        val b = PlaybackAuthState.EMPTY
        assertEquals(a.fingerprint, b.fingerprint)
        assertNotEquals(a.fingerprint, a.copy(visitorData = "x").fingerprint)
        assertEquals(a.streamCacheFingerprint, b.streamCacheFingerprint)
    }

    @Test
    fun hasLoginCookieHanyaBilaCookieSapisidAda() {
        assertFalse(PlaybackAuthState.EMPTY.hasLoginCookie)
        assertFalse(PlaybackAuthState(cookie = "SID=abc; HSID=def").hasLoginCookie)
        assertTrue(PlaybackAuthState(cookie = "SID=abc; SAPISID=xyz").hasLoginCookie)
        assertTrue(PlaybackAuthState(cookie = "__Secure-3PAPISID=xyz").hasLoginCookie)
    }

    @Test
    fun hasPlaybackLoginContextButuhCookieDanDataSyncId() {
        assertFalse(PlaybackAuthState(cookie = "SAPISID=x").hasPlaybackLoginContext)
        assertFalse(PlaybackAuthState(dataSyncId = "d||u").hasPlaybackLoginContext)
        assertTrue(PlaybackAuthState(cookie = "SAPISID=x", dataSyncId = "d||u").hasPlaybackLoginContext)
    }

    @Test
    fun sessionIdMemakaiUserSessionBilaLoginKalauTidakVisitorData() {
        assertEquals("u", PlaybackAuthState(cookie = "SAPISID=x", dataSyncId = "d||u").sessionId)
        assertEquals("CgttZXN0", PlaybackAuthState(visitorData = "CgttZXN0").sessionId)
        assertNull(PlaybackAuthState.EMPTY.sessionId)
    }

    @Test
    fun poTokenSelaluNullDiTahap1KarenaFiturMati() {
        val s = PlaybackAuthState(poToken = "tok", poTokenPlayer = "ptok", poTokenPlayerVideoId = "v1")
        assertNull(s.resolvePlayerPoToken(YouTubeClient.WEB_REMIX, videoId = "v1"))
        assertNull(s.resolveGvsPoToken(YouTubeClient.WEB_REMIX, videoId = "v1"))
        assertNull(s.resolveSubsPoToken(YouTubeClient.WEB_REMIX, "v1"))
    }
}
