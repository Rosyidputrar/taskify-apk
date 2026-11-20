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

    @Test fun `URL ANDROID request pertama Range 6 detik dengan UA aplikasi tanpa origin`() {
        // clen 20.000.000 / dur 2000 dtk = 10.000 B/dtk → chunk 6 dtk = 60.000 byte
        // (INVESTIGASI-A §7: bukan 8 MB — ujung Range harus dalam jendela ~60 detik).
        val url = "https://rr1---sn-aaaa.googlevideo.com/videoplayback?c=ANDROID&cver=21.26.364&itag=251&clen=20000000&dur=2000.0"
        val cap = CapturingClient(20_000_000)
        val read = openAndReadAll(url, 20_000_000L, cap.client)
        assertEquals(20_000_000L, read)
        assertTrue(cap.requests.isNotEmpty())
        val first = cap.requests[0]
        assertEquals("bytes=0-59999", first.header("Range"), "chunk pertama = 6 detik media")
        assertEquals("com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip", first.header("User-Agent"))
        assertNull(first.header("Origin"), "klien native tanpa Origin")
        assertNull(first.header("Referer"), "klien native tanpa Referer")
        assertEquals(334, cap.requests.size, "20 MB / 60 KB = 334 chunk, nyatanya ${cap.requests.size}")
        assertEquals("bytes=60000-119999", cap.requests[1].header("Range"))
        assertEquals("bytes=19980000-19999999", cap.requests[333].header("Range"))
    }

    @Test fun `URL WEB_REMIX request pertama Range berbatas dengan Firefox dan origin music`() {
        // clen 100.000 / dur 1 dtk → chunk 6 dtk = 600.000 byte > isi → 1 chunk utuh.
        val url = "https://rr1---sn-bbbb.googlevideo.com/videoplayback?c=WEB_REMIX&cver=1.20260707.12.00&itag=251&clen=100000&dur=1.0"
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

    @Test fun `URL googlevideo tanpa contentLength tetap Range 120KB lewat cap ChunkedDataSource`() {
        // F2 (INVESTIGASI-A §5) + F3 (§7) + C S-13: tanpa registry DAN tanpa `clen`/`dur`,
        // request pertama googlevideo TETAP membawa Range berbatas —
        // ChunkedDataSource membatasi chunk pertama ke fallback per-itag 251 (120 KB).
        // Sebelumnya tes ini meng-assert tanpa Range (perilaku lama yang
        // menyebabkan 403), lalu sempat 8 MB (juga 403, lihat §7).
        val url = "https://rr1---sn-cccc.googlevideo.com/videoplayback?c=WEB_CREATOR&cver=1.20260708.06.00&itag=251"
        val cap = CapturingClient(100_000)
        val source = MediaRequestHeaders.createResolvingDataSourceFactory(cap.client).createDataSource()
        try {
            source.open(DataSpec(Uri.parse(url)))
        } finally {
            source.close()
        }
        assertEquals(1, cap.requests.size)
        val first = cap.requests[0]
        assertEquals("bytes=0-119999", first.header("Range"))
        assertTrue(first.header("User-Agent")!!.contains("Chrome/141.0.0.0"))
        assertEquals("https://www.youtube.com", first.header("Origin"))
    }

    @Test fun `URL googlevideo dengan clen tanpa registry tetap Range dari fallback clen`() {
        // F1 (INVESTIGASI-A §5): `resolvePlaybackDataSpec` jatuh ke `clen`.
        // Tanpa `dur`, chunk memakai fallback per-itag 251 (120 KB, C S-13)
        // → 100.000 byte = 1 chunk; Range dijepit oleh panjang DataSpec
        // (dari `clen`) sehingga permintaannya `bytes=0-99999`.
        val url = "https://rr1---sn-eeee.googlevideo.com/videoplayback?c=WEB_CREATOR&cver=1.20260708.06.00&itag=251&clen=100000"
        val cap = CapturingClient(100_000)
        val read = openAndReadAllWithoutRegistry(url, cap.client)
        assertEquals(100_000L, read)
        assertEquals(1, cap.requests.size)
        assertEquals("bytes=0-99999", cap.requests[0].header("Range"))
    }

    /** Seperti [openAndReadAll] tapi tanpa mendaftarkan URL ke registry. */
    private fun openAndReadAllWithoutRegistry(url: String, client: OkHttpClient): Long {
        val source = MediaRequestHeaders.createResolvingDataSourceFactory(client).createDataSource()
        try {
            source.open(DataSpec(Uri.parse(url)))
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

    @Test fun `resolvePlaybackDataSpec mengisi length dari registry dan header per-c`() {
        val url = "https://rr1---sn-dddd.googlevideo.com/videoplayback?c=ANDROID&cver=21.26.364&itag=251"
        ResolvedStreamRegistry.put(url, 5_495_333L)
        val resolved = MediaRequestHeaders.resolvePlaybackDataSpec(DataSpec(Uri.parse(url), 1_000_000, C.LENGTH_UNSET.toLong()))
        assertEquals(5_495_333L - 1_000_000L, resolved.length, "length = sisa dari posisi seek")
        assertEquals("com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip", resolved.httpRequestHeaders["User-Agent"])
        assertTrue("Origin" !in resolved.httpRequestHeaders)
    }
}
