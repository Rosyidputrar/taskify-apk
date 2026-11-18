package com.rosyidputrar.taskify.core.media

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Factory resolving ArchiveTune (Tahap 1) di wire: request media pertama
 * membawa `Range` berbatas + header per-`c` dari URL. Interceptor
 * membalas sintetis tanpa jaringan, seperti `MediaRequestHeadersTest`.
 */
@RunWith(AndroidJUnit4::class)
class ResolvingMediaDataSourceTest {

    private class CapturingClient(val bodySize: Int) {
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                requests += request
                val range = request.header("Range") ?: ""
                val end = range.substringAfter("-", "").substringBefore(" ").toLongOrNull() ?: 0L
                val start = range.substringAfter("bytes=").substringBefore("-").toLongOrNull() ?: 0L
                val size = (end - start + 1).coerceAtLeast(0)
                val body = ByteArray(size.toInt()) { 0x41 }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(206)
                    .message("Partial Content")
                    .header("Content-Range", "bytes $start-$end/$bodySize")
                    .header("Content-Length", "$size")
                    .body(body.toResponseBody("audio/webm".toMediaType()))
                    .build()
            }
            .build()
    }

    private fun openAndReadAll(url: String, contentLength: Long, client: OkHttpClient): Long {
        ResolvedStreamRegistry.put(url, contentLength)
        val source = MediaRequestHeaders.createResolvingDataSourceFactory(client).createDataSource()
        try {
            val opened = source.open(DataSpec(Uri.parse(url)))
            assertTrue(opened > 0)
            val buf = ByteArray(8192)
            var total = 0L
            while (true) {
                val n = source.read(buf, 0, buf.size)
                if (n == C.RESULT_END_OF_INPUT) break
                total += n
            }
            return total
        } finally {
            source.close()
        }
    }

    @Test fun `URL ANDROID request pertama Range 8MB dengan UA aplikasi tanpa origin`() {
        val url = "https://rr1---sn-aaaa.googlevideo.com/videoplayback?c=ANDROID&cver=21.26.364&itag=251&clen=20000000"
        val cap = CapturingClient(20_000_000)
        val read = openAndReadAll(url, 20_000_000L, cap.client)
        assertEquals(20_000_000L, read)
        assertTrue(cap.requests.isNotEmpty())
        val first = cap.requests[0]
        assertEquals("bytes=0-8388607", first.header("Range"), "chunk pertama ArchiveTune = 8 MB")
        assertEquals("com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip", first.header("User-Agent"))
        assertNull(first.header("Origin"), "klien native tanpa Origin")
        assertNull(first.header("Referer"), "klien native tanpa Referer")
        assertTrue(cap.requests.size >= 3, "20 MB = 3 chunk, nyatanya ${cap.requests.size}")
        assertEquals("bytes=8388608-16777215", cap.requests[1].header("Range"))
        assertEquals("bytes=16777216-19999999", cap.requests[2].header("Range"))
    }

    @Test fun `URL WEB_REMIX request pertama Range berbatas dengan Firefox dan origin music`() {
        val url = "https://rr1---sn-bbbb.googlevideo.com/videoplayback?c=WEB_REMIX&cver=1.20260707.12.00&itag=251&clen=100000"
        val cap = CapturingClient(100_000)
        val read = openAndReadAll(url, 100_000L, cap.client)
        assertEquals(100_000L, read)
        assertEquals(1, cap.requests.size)
        val first = cap.requests[0]
        assertEquals("bytes=0-99999", first.header("Range"))
        assertTrue(first.header("User-Agent")!!.contains("Firefox/140.0"))
        assertEquals("https://music.youtube.com", first.header("Origin"))
        assertEquals("https://music.youtube.com/", first.header("Referer"))
    }

    @Test fun `URL tanpa contentLength terdaftar tetap dapat header per-c tanpa Range`() {
        val url = "https://rr1---sn-cccc.googlevideo.com/videoplayback?c=WEB_CREATOR&cver=1.20260708.06.00&itag=251"
        val cap = CapturingClient(100_000)
        // Tanpa put() ke registry: length UNSET → tanpa Range (perilaku lama),
        // tapi header tetap per-c.
        val source = MediaRequestHeaders.createResolvingDataSourceFactory(cap.client).createDataSource()
        try {
            source.open(DataSpec(Uri.parse(url)))
        } finally {
            source.close()
        }
        assertEquals(1, cap.requests.size)
        val first = cap.requests[0]
        assertNull(first.header("Range"))
        assertTrue(first.header("User-Agent")!!.contains("Chrome/141.0.0.0"))
        assertEquals("https://www.youtube.com", first.header("Origin"))
    }

    @Test fun `resolvePlaybackDataSpec mengisi length dari registry dan header per-c`() {
        val url = "https://rr1---sn-dddd.googlevideo.com/videoplayback?c=ANDROID&cver=21.26.364&itag=251"
        ResolvedStreamRegistry.put(url, 5_495_333L)
        val resolved = MediaRequestHeaders.resolvePlaybackDataSpec(DataSpec(Uri.parse(url), 1_000_000, C.LENGTH_UNSET.toLong()))
        assertEquals(5_495_333L - 1_000_000L, resolved.length, "length = sisa dari posisi seek")
        assertEquals("com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip", resolved.httpRequestHeaders["User-Agent"])
        assertTrue("Origin" !in resolved.httpRequestHeaders)
    }
}
