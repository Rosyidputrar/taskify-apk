package com.rosyidputrar.taskify.core.innertube

import okhttp3.OkHttpClient
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Satu `OkHttpClient` bersama untuk request `player` (Ktor) dan request
 * media ExoPlayer (perbaikan 403 ikatan IP, 18 Nov 2026).
 *
 * Yang dibuktikan di sini (tanpa jaringan):
 * - singleton: semua pemanggil mendapat instance yang sama;
 * - timeout sama dengan `InnerTube` (connect 10 dtk, read/write 15 dtk);
 * - turunan `newBuilder()` berbagi connection pool + DNS dengan klien asal
 *   — langkah PERSIS yang dilakukan engine Ktor `OkHttp` pada
 *   `preconfigured` (terverifikasi dari bytecode `ktor-client-okhttp`
 *   3.6.1), jadi klien Ktor turunan dan klien media ini benar-benar satu
 *   pool/DNS, bukan dua klien yang kebetulan sama konfigurasinya.
 *
 * Yang TIDAK dibuktikan di sini: bahwa engine Ktor benar-benar memakai
 * klien ini (itu satu baris `engine { preconfigured = ... }` di
 * `InnerTube.createDefaultClient`, diverifikasi lewat review + bytecode
 * di atas, bukan lewat API publik Ktor — engine tidak mengekspos kliennya).
 */
class SharedHttpClientTest {
    @Test fun `klien bersama adalah singleton`() {
        assertSame(SharedHttpClient.client, SharedHttpClient.client)
    }

    @Test fun `timeout sama dengan InnerTube`() {
        val client = SharedHttpClient.client
        assertEquals(10_000, client.connectTimeoutMillis)
        assertEquals(15_000, client.readTimeoutMillis)
        assertEquals(15_000, client.writeTimeoutMillis)
    }

    @Test fun `turunan newBuilder berbagi connection pool dan dns`() {
        val client = SharedHttpClient.client
        val derived = client.newBuilder().build()
        assertSame(client.connectionPool, derived.connectionPool)
        assertSame(client.dispatcher, derived.dispatcher)
        assertSame(client.dns, derived.dns)
        assertSame(client.socketFactory, derived.socketFactory)
    }

    @Test fun `createDefaultClient memakai klien yang diberikan dan bisa dibuat`() {
        val custom = OkHttpClient.Builder().build()
        InnerTube.createDefaultClient(custom).close()
        // Bawaan = klien bersama; konstruksi saja sudah menjalankan engine
        // OkHttp dengan `preconfigured` (gagal di sini = wiring putus).
        InnerTube.createDefaultClient().close()
    }

    @Test fun `klien bersama tanpa proxy kustom`() {
        assertTrue(SharedHttpClient.client.proxy == null)
    }
}
