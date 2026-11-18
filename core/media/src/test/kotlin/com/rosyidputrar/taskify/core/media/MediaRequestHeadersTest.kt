package com.rosyidputrar.taskify.core.media

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Header media request ExoPlayer ala ArchiveTune: Origin/Referer/User-Agent
 * web + Accept — TANPA cookie (ArchiveTune membuktikan cookie media tidak
 * wajib; jangan buka permukaan baru).
 */
@RunWith(AndroidJUnit4::class)
class MediaRequestHeadersTest {
    @Test fun `header media berisi origin referer user-agent web dan accept`() {
        val headers = MediaRequestHeaders.DEFAULT
        assertEquals("https://music.youtube.com", headers["Origin"])
        assertEquals("https://music.youtube.com/", headers["Referer"])
        assertEquals(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0",
            headers["User-Agent"],
            "UA Firefox sama dengan profil WEB_REMIX di ytClient",
        )
        assertEquals("*/*", headers["Accept"])
    }

    @Test fun `header media tidak membawa cookie atau token`() {
        val keys = MediaRequestHeaders.DEFAULT.keys.map { it.lowercase() }
        assertFalse("cookie" in keys)
        assertFalse(keys.any { "token" in it || "auth" in it })
    }

    @Test fun `factory datasource dengan header bisa dibuat`() {
        val factory = MediaRequestHeaders.createDataSourceFactory()
        assertNotNull(factory.createDataSource())
    }

    @Test fun `user-agent sama dengan konstanta profil WEB_REMIX`() {
        assertTrue(
            MediaRequestHeaders.DEFAULT["User-Agent"] ==
                com.rosyidputrar.taskify.core.innertube.models.YouTubeClient.USER_AGENT_WEB_REMIX,
            "satu sumber kebenaran, bukan salinan string lepas",
        )
    }
}
