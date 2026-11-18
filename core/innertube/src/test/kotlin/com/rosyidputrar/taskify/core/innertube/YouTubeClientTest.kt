package com.rosyidputrar.taskify.core.innertube

import com.rosyidputrar.taskify.core.innertube.models.YouTubeClient
import org.junit.Test
import kotlin.test.assertEquals

/** Katalog klien dari `youtube_clients.json`, termasuk profil ANDROID baru. */
class YouTubeClientTest {
    @Test
    fun profilAndroid2126364AdaDenganFieldYangBenar() {
        val c = YouTubeClient.ANDROID
        assertEquals("ANDROID", c.clientName)
        assertEquals("21.26.364", c.clientVersion)
        assertEquals("3", c.clientId)
        assertEquals("34", c.androidSdkVersion)
        assertEquals("https://www.youtube.com", c.requestOrigin())
        assertEquals("https://www.youtube.com/", c.requestReferer())
        assertEquals("https://www.youtube.com/youtubei/v1/player", c.requestApiUrl("player"))
    }

    @Test
    fun profilWebRemixSesuaiKatalogArchiveTune() {
        val c = YouTubeClient.WEB_REMIX
        assertEquals("WEB_REMIX", c.clientName)
        assertEquals("1.20260707.12.00", c.clientVersion)
        assertEquals("67", c.clientId)
        assertEquals("https://music.youtube.com", c.requestOrigin())
        assertEquals("https://music.youtube.com/", c.requestReferer())
    }

    @Test
    fun profilWebDanMwebPunyaOriginMasingMasing() {
        assertEquals("https://www.youtube.com", YouTubeClient.WEB.requestOrigin())
        assertEquals("https://m.youtube.com", YouTubeClient.MWEB.requestOrigin())
        assertEquals("https://m.youtube.com/", YouTubeClient.MWEB.requestReferer())
        assertEquals("1", YouTubeClient.WEB.clientId)
        assertEquals("2", YouTubeClient.MWEB.clientId)
    }

    @Test
    fun toContextMembawaLocaleDanVisitorData() {
        val ctx = YouTubeClient.ANDROID.toContext(
            com.rosyidputrar.taskify.core.innertube.models.YouTubeLocale(gl = "ID", hl = "id"),
            visitorData = "CgttZXN0",
            dataSyncId = null,
        )
        assertEquals("ANDROID", ctx.client.clientName)
        assertEquals("21.26.364", ctx.client.clientVersion)
        assertEquals("ID", ctx.client.gl)
        assertEquals("id", ctx.client.hl)
        assertEquals("CgttZXN0", ctx.client.visitorData)
        assertEquals("34", ctx.client.androidSdkVersion)
    }
}
