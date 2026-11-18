package com.rosyidputrar.taskify.core.media

import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.innertube.SharedHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
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

    @Test fun `factory datasource adalah OkHttpDataSource bukan DefaultHttpDataSource`() {
        // Perbaikan 403 ikatan IP (18 Nov 2026): media harus lewat OkHttp
        // dengan klien yang sama seperti InnerTube, bukan HttpURLConnection.
        val source = MediaRequestHeaders.createDataSourceFactory().createDataSource()
        assertTrue(
            source is OkHttpDataSource,
            "media harus lewat OkHttpDataSource, bukan ${source.javaClass.name}",
        )
    }

    @Test fun `factory bawaan memakai OkHttpClient bersama InnerTube`() {
        // Satu-satunya cara mengamati klien yang dipakai factory: field
        // `callFactory` di OkHttpDataSource.Factory (media3 1.11.1).
        // Bila media3 mengubah nama field ini, tes ini yang memberi tahu.
        val factory = MediaRequestHeaders.createDataSourceFactory()
        assertTrue(factory is OkHttpDataSource.Factory)
        val field = factory.javaClass.getDeclaredField("callFactory").apply { isAccessible = true }
        assertSame(
            SharedHttpClient.client,
            field.get(factory),
            "factory bawaan harus memakai klien bersama, bukan klien baru",
        )
    }

    @Test fun `factory memakai OkHttpClient yang diberikan untuk request media`() {
        // Interceptor membalas sintetis tanpa jaringan: bila request media
        // benar-benar lewat klien ini, interceptor terpanggil tepat sekali
        // dan isinya terbaca dari DataSource.
        var intercepted = 0
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                intercepted++
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Content-Length", "15")
                    .body("ok-http-dipakai".toResponseBody("audio/webm".toMediaType()))
                    .build()
            }
            .build()
        val source = MediaRequestHeaders.createDataSourceFactory(client).createDataSource()
        try {
            val opened = source.open(DataSpec(Uri.parse("https://rr.test/videoplayback?itag=251")))
            assertTrue(opened > 0, "open harus mengembalikan panjang isi")
            val buf = ByteArray(64)
            val read = source.read(buf, 0, buf.size)
            assertEquals("ok-http-dipakai", String(buf, 0, read, Charsets.UTF_8))
            assertEquals(1, intercepted, "request media harus lewat klien yang diberikan")
        } finally {
            source.close()
        }
    }

    @Test fun `user-agent sama dengan konstanta profil WEB_REMIX`() {
        assertTrue(
            MediaRequestHeaders.DEFAULT["User-Agent"] ==
                com.rosyidputrar.taskify.core.innertube.models.YouTubeClient.USER_AGENT_WEB_REMIX,
            "satu sumber kebenaran, bukan salinan string lepas",
        )
    }
}
