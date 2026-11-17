package com.rosyidputrar.taskify.core.innertube

import com.rosyidputrar.taskify.core.innertube.pages.FixtureHelper
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `YouTube.visitorData()` (Tahap 5) via MockEngine: parsing `sw.js_data`
 * (prefix XSSI + array JSON + regex), tanpa jaringan sungguhan.
 * Bentuk badan palsu meniru struktur asli: `[0][2]` berisi daftar string,
 * salah satunya `visitorData`.
 */
class YouTubeVisitorDataTest {

    private fun youTubeWith(handler: MockRequestHandler): YouTube {
        val engine = MockEngine(handler)
        return YouTube(InnerTube(HttpClient(engine) { expectSuccess = true }))
    }

    private fun MockRequestHandleScope.okText(body: String) =
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

    /** Badan `sw.js_data` palsu: `)]}'` + `[[ "s1", "s2", [ ...string... ] ]]`. */
    private fun swJsData(vararg inner: String): String {
        val items = inner.joinToString(",") { "\"$it\"" }
        return ")]}'\n[[\"s1\",\"s2\",[$items]]]"
    }

    @Test fun `visitorData sukses dari sw_js_data`() = runTest {
        var capturedUrl = ""
        var capturedMethod = ""
        val yt = youTubeWith { request ->
            capturedUrl = request.url.toString()
            capturedMethod = request.method.value
            okText(swJsData("noise", "CgttZXN0dmVzdA", "lain"))
        }
        val result = yt.visitorData()
        assertTrue(result.isSuccess)
        assertEquals("CgttZXN0dmVzdA", result.getOrThrow())
        assertEquals("GET", capturedMethod)
        assertEquals("https://music.youtube.com/sw.js_data", capturedUrl)
    }

    @Test fun `visitorData mengambil yang pertama cocok regex`() = runTest {
        val yt = youTubeWith { okText(swJsData("Cgspertama", "Cgttkedua")) }
        assertEquals("Cgspertama", yt.visitorData().getOrThrow())
    }

    @Test fun `visitorData gagal bila pola tidak ketemu`() = runTest {
        val yt = youTubeWith { okText(swJsData("noise", "other", "Cgx-bukan")) }
        assertTrue(yt.visitorData().isFailure)
    }

    @Test fun `visitorData gagal bila badan bukan JSON array`() = runTest {
        val yt = youTubeWith { okText(")]}'\n{\"bukan\":\"array\"}") }
        assertTrue(yt.visitorData().isFailure)
    }

    @Test fun `visitorData gagal bila badan lebih pendek dari prefix XSSI`() = runTest {
        val yt = youTubeWith { okText(")]}") }
        assertTrue(yt.visitorData().isFailure)
    }

    @Test fun `visitorData gagal bila jaringan mati dan hanya satu percobaan`() = runTest {
        var calls = 0
        val yt = youTubeWith {
            calls++
            throw IOException("putus")
        }
        assertTrue(yt.visitorData().isFailure)
        assertEquals(1, calls)
    }

    @Test fun `fasad meneruskan authState eksplisit ke transport`() = runTest {
        var visitorHeader: String? = null
        val yt = youTubeWith { request ->
            visitorHeader = request.headers["X-Goog-Visitor-Id"]
            respond(
                content = FixtureHelper.text("search"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val result = yt.search("tulus", authState = PlaybackAuthState(visitorData = "Cgtexplicit"))
        assertTrue(result.isSuccess)
        assertEquals("Cgtexplicit", visitorHeader)
    }

    @Test fun `applyAuthState membuat pemanggilan tanpa parameter memakai visitorData`() = runTest {
        var visitorHeader: String? = null
        val yt = youTubeWith { request ->
            visitorHeader = request.headers["X-Goog-Visitor-Id"]
            respond(
                content = FixtureHelper.text("search"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        yt.applyAuthState(PlaybackAuthState(visitorData = "Cgtterapan"))
        assertTrue(yt.search("tulus").isSuccess)
        assertEquals("Cgtterapan", visitorHeader)
        assertEquals("Cgtterapan", yt.currentAuthState().visitorData)
    }
}
