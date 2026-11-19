package com.rosyidputrar.taskify.core.media.mytube

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Profil header media per-`c` (porting ArchiveTune `StreamClientUtils`,
 * Tahap 1): identitas media diturunkan dari param `c` URL, bukan satu
 * nilai tetap. Nilai UA di sini hanya nama profil, bukan URL/token.
 */
class StreamClientProfileTest {

    private val androidUrl = "https://rr1---sn-xxxx.googlevideo.com/videoplayback?c=ANDROID&cver=21.26.364&itag=251"
    private val webRemixUrl = "https://rr1---sn-xxxx.googlevideo.com/videoplayback?c=WEB_REMIX&cver=1.20260707.12.00&itag=251"
    private val webCreatorUrl = "https://rr1---sn-xxxx.googlevideo.com/videoplayback?c=WEB_CREATOR&cver=1.20260708.06.00&itag=251"
    private val mwebUrl = "https://rr1---sn-xxxx.googlevideo.com/videoplayback?c=MWEB&cver=2.20260708.05.00&itag=251"

    @Test fun `c ANDROID memakai UA aplikasi tanpa origin referer`() {
        val p = resolveStreamRequestProfile(androidUrl)!!
        assertEquals("ANDROID", p.resolvedClientFamily)
        assertEquals("com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip", p.userAgent)
        assertNull(p.origin)
        assertNull(p.referer)
        assertEquals("ANDROID@21.26.364", p.clientKey)
    }

    @Test fun `c WEB_REMIX memakai Firefox plus origin music`() {
        val p = resolveStreamRequestProfile(webRemixUrl)!!
        assertEquals("WEB_REMIX", p.resolvedClientFamily)
        assertTrue(p.userAgent.contains("Firefox/140.0"))
        assertEquals("https://music.youtube.com", p.origin)
        assertEquals("https://music.youtube.com/", p.referer)
    }

    @Test fun `c WEB_CREATOR memakai Chrome 141 plus origin www`() {
        val p = resolveStreamRequestProfile(webCreatorUrl)!!
        assertEquals("WEB_CREATOR", p.resolvedClientFamily)
        assertTrue(p.userAgent.contains("Chrome/141.0.0.0"))
        assertEquals("https://www.youtube.com", p.origin)
        assertEquals("https://www.youtube.com/", p.referer)
    }

    @Test fun `c MWEB memakai UA mobile plus origin m`() {
        val p = resolveStreamRequestProfile(mwebUrl)!!
        assertEquals("MWEB", p.resolvedClientFamily)
        assertTrue(p.userAgent.contains("Pixel 9 Pro"))
        assertEquals("https://m.youtube.com", p.origin)
        assertEquals("https://m.youtube.com/", p.referer)
    }

    @Test fun `URL tanpa c tidak punya profil`() {
        assertNull(resolveStreamRequestProfile("https://example.com/stream.mp3"))
        assertNull(streamRequestHeaders("https://example.com/stream.mp3"))
    }

    @Test fun `header per URL ANDROID tanpa origin referer`() {
        val h = streamRequestHeaders(androidUrl)!!
        assertEquals("com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip", h["User-Agent"])
        assertEquals("*/*", h["Accept"])
        assertTrue("Origin" !in h)
        assertTrue("Referer" !in h)
    }

    @Test fun `appendPoToken menempel pot sekali saja`() {
        val withPot = appendPoTokenToStreamUrl(androidUrl, "TOKEN_CONTOH")
        assertTrue(withPot.contains("pot=TOKEN_CONTOH"))
        assertEquals(withPot, appendPoTokenToStreamUrl(withPot, "TOKEN_LAIN"))
    }

    @Test fun `clientKey dari URL`() {
        assertEquals("ANDROID@21.26.364", streamUrlClientKey(androidUrl))
        assertNull(streamUrlClientKey("https://example.com/stream.mp3"))
    }
}
