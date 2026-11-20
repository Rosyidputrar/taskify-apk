package com.rosyidputrar.taskify.core.youtubei

import okhttp3.Dns
import okhttp3.OkHttpClient
import org.junit.Test
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * C M-1: klien HTTP youtubei.js harus berbagi jalur jaringan dengan
 * InnerTube/media — connection pool, DNS, dan socket factory yang sama
 * dengan klien dasar ([SharedHttpClient] di produksi) — supaya request
 * `player` youtubei dan request media keluar lewat IP yang sama
 * (perbaikan 403 ikatan IP). Yang tetap khas youtubei: tanpa proxy,
 * redirect manual, dan timeout 15/20/20 + call 30 dtk.
 */
class YoutubeiHttpClientTest {
    private fun config(base: OkHttpClient? = null) =
        YoutubeiNetworkConfiguration(
            proxy = null,
            proxyUsername = null,
            proxyPassword = null,
            dns = Dns.SYSTEM,
            baseClient = base,
        )

    @Test fun `buildClient berbagi pool DNS dan socket factory dengan klien dasar`() {
        val base = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
        val client = YoutubeiHttpClient({ config(base) }, {}).buildClient(config(base))
        assertSame(base.connectionPool, client.connectionPool, "pool harus sama dengan klien dasar")
        assertSame(base.dns, client.dns, "DNS harus sama dengan klien dasar")
        assertSame(base.socketFactory, client.socketFactory, "socket factory harus sama dengan klien dasar")
    }

    @Test fun `buildClient mempertahankan perilaku youtubei yang terbukti jalan`() {
        val base = OkHttpClient.Builder().build()
        val client = YoutubeiHttpClient({ config(base) }, {}).buildClient(config(base))
        assertEquals(Proxy.NO_PROXY, client.proxy, "youtubei tetap tanpa proxy")
        assertEquals(15_000, client.connectTimeoutMillis)
        assertEquals(20_000, client.readTimeoutMillis)
        assertEquals(20_000, client.writeTimeoutMillis)
        assertEquals(30_000, client.callTimeoutMillis)
        assertFalse(client.followRedirects, "redirect ditangani manual oleh bridge")
        assertFalse(client.followSslRedirects)
    }

    @Test fun `buildClient memakai dispatcher sendiri supaya cancelAll tidak menyentuh klien dasar`() {
        val base = OkHttpClient.Builder().build()
        val client = YoutubeiHttpClient({ config(base) }, {}).buildClient(config(base))
        assertNotSame(base.dispatcher, client.dispatcher)
    }

    @Test fun `buildClient tanpa klien dasar tetap jalan dengan NO_PROXY dan DNS sistem`() {
        val client = YoutubeiHttpClient({ config() }, {}).buildClient(config())
        assertEquals(Proxy.NO_PROXY, client.proxy)
        assertSame(Dns.SYSTEM, client.dns)
    }
}
