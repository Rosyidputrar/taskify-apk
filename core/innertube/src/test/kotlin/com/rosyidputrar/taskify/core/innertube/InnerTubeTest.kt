package com.rosyidputrar.taskify.core.innertube

import com.rosyidputrar.taskify.core.innertube.models.YouTubeClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Transport InnerTube via MockEngine: header + badan + retry transient.
 * Tidak ada panggilan jaringan sungguhan di sini.
 */
class InnerTubeTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun mockClient(handler: MockRequestHandler): HttpClient =
        HttpClient(MockEngine) {
            expectSuccess = true
            engine { addHandler(handler) }
            defaultRequest { url("https://music.youtube.com/youtubei/v1/") }
        }

    private fun MockRequestHandleScope.okJson(body: String = """{"ok":true}""") =
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

    @Test
    fun searchMengirimHeaderDanBadanWebRemix() = runTest {
        var capturedUrl = ""
        var capturedBody = ""
        val capturedHeaders = mutableMapOf<String, String>()
        val tube = InnerTube(mockClient {
            capturedUrl = it.url.toString()
            capturedBody = (it.body as TextContent).text
            it.headers.forEach { k, v -> capturedHeaders[k] = v.joinToString() }
            okJson()
        })
        tube.search(YouTubeClient.WEB_REMIX, query = "tulus", params = "EgWKAQIIAWoKEAkQBRAKEAMQBA==")

        assertEquals("https://music.youtube.com/youtubei/v1/search?prettyPrint=false", capturedUrl)
        assertEquals("67", capturedHeaders["X-YouTube-Client-Name"])
        assertEquals("1.20260707.12.00", capturedHeaders["X-YouTube-Client-Version"])
        assertEquals("https://music.youtube.com", capturedHeaders["X-Origin"])
        assertEquals("https://music.youtube.com/", capturedHeaders["Referer"])
        assertEquals("1", capturedHeaders["X-Goog-Api-Format-Version"])
        assertEquals("0", capturedHeaders["X-Goog-AuthUser"])
        assertTrue(capturedHeaders["User-Agent"]?.contains("Firefox/140.0") == true)
        assertNull(capturedHeaders["X-Goog-Visitor-Id"])

        val body = json.parseToJsonElement(capturedBody).jsonObject
        assertEquals("tulus", body["query"]?.jsonPrimitive?.content)
        assertEquals("EgWKAQIIAWoKEAkQBRAKEAMQBA==", body["params"]?.jsonPrimitive?.content)
        val ctx = body["context"]?.jsonObject?.get("client")?.jsonObject
        assertEquals("WEB_REMIX", ctx?.get("clientName")?.jsonPrimitive?.content)
        assertEquals("id", ctx?.get("hl")?.jsonPrimitive?.content)
        assertEquals("ID", ctx?.get("gl")?.jsonPrimitive?.content)
    }

    @Test
    fun visitorDataDikirimBilaAda() = runTest {
        var visitorHeader: String? = null
        val tube = InnerTube(mockClient {
            visitorHeader = it.headers["X-Goog-Visitor-Id"]
            okJson()
        })
        tube.visitorData = "CgttZXN0"
        tube.search(YouTubeClient.WEB_REMIX, query = "x")
        assertEquals("CgttZXN0", visitorHeader)
    }

    @Test
    fun playerAndroidMemakaiOriginYoutubeDanBodyPlayer() = runTest {
        var capturedUrl = ""
        var capturedBody = ""
        val capturedHeaders = mutableMapOf<String, String>()
        val tube = InnerTube(mockClient {
            capturedUrl = it.url.toString()
            capturedBody = (it.body as TextContent).text
            it.headers.forEach { k, v -> capturedHeaders[k] = v.joinToString() }
            okJson()
        })
        tube.player(YouTubeClient.ANDROID, "QqJ-Vp8mvbk")

        assertEquals("https://www.youtube.com/youtubei/v1/player?prettyPrint=false", capturedUrl)
        assertEquals("3", capturedHeaders["X-YouTube-Client-Name"])
        assertEquals("20.10.4", capturedHeaders["X-YouTube-Client-Version"])
        assertEquals("https://www.youtube.com", capturedHeaders["X-Origin"])
        val body = json.parseToJsonElement(capturedBody).jsonObject
        assertEquals("QqJ-Vp8mvbk", body["videoId"]?.jsonPrimitive?.content)
        assertEquals("true", body["racyCheckOk"]?.jsonPrimitive?.content)
        assertEquals("true", body["contentCheckOk"]?.jsonPrimitive?.content)
        val ctx = body["context"]?.jsonObject?.get("client")?.jsonObject
        assertEquals("ANDROID", ctx?.get("clientName")?.jsonPrimitive?.content)
        assertEquals("34", ctx?.get("androidSdkVersion")?.jsonPrimitive?.content)
    }

    @Test
    fun browseDanNextDanSuggestMemakaiPathYangBenar() = runTest {
        val paths = mutableListOf<String>()
        val tube = InnerTube(mockClient {
            paths += it.url.encodedPath
            okJson()
        })
        tube.browse(YouTubeClient.WEB_REMIX, browseId = "FEmusic_home")
        tube.next(YouTubeClient.WEB_REMIX, videoId = "QqJ-Vp8mvbk", playlistId = "RDAMVMQqJ-Vp8mvbk")
        tube.getSearchSuggestions(YouTubeClient.WEB_REMIX, "tulus")
        assertEquals(
            listOf(
                "/youtubei/v1/browse",
                "/youtubei/v1/next",
                "/youtubei/v1/music/get_search_suggestions",
            ),
            paths,
        )
    }

    @Test
    fun retryTigaKaliUntukIOExceptionLaluBerhasil() = runTest {
        var calls = 0
        val tube = InnerTube(mockClient {
            calls++
            if (calls < 3) throw IOException("putus")
            okJson()
        })
        val result = tube.search(YouTubeClient.WEB_REMIX, query = "x")
        assertEquals("""{"ok":true}""", result)
        assertEquals(3, calls)
    }

    @Test
    fun retryMenyerahSetelahTigaKali() = runTest {
        var calls = 0
        val tube = InnerTube(mockClient {
            calls++
            throw IOException("putus terus")
        })
        assertFailsWith<IOException> { tube.search(YouTubeClient.WEB_REMIX, query = "x") }
        assertEquals(3, calls)
    }

    @Test
    fun http400TidakDiRetry() = runTest {
        var calls = 0
        val tube = InnerTube(mockClient {
            calls++
            respond("""{"error":"bad"}""", HttpStatusCode.BadRequest, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        })
        assertFailsWith<ClientRequestException> { tube.search(YouTubeClient.WEB_REMIX, query = "x") }
        assertEquals(1, calls)
    }

    @Test
    fun authStateEksplisitMengalahkanStateInternal() = runTest {
        var visitorHeader: String? = null
        var contextVisitor: String? = null
        val tube = InnerTube(mockClient {
            visitorHeader = it.headers["X-Goog-Visitor-Id"]
            val body = json.parseToJsonElement((it.body as TextContent).text).jsonObject
            contextVisitor = body["context"]?.jsonObject?.get("client")?.jsonObject
                ?.get("visitorData")?.jsonPrimitive?.content
            okJson()
        })
        tube.visitorData = "Cgtinternal"
        tube.search(
            YouTubeClient.WEB_REMIX,
            query = "x",
            authState = PlaybackAuthState(visitorData = "Cgtexplicit"),
        )
        assertEquals("Cgtexplicit", visitorHeader)
        assertEquals("Cgtexplicit", contextVisitor)
    }

    @Test
    fun searchMengirimCookieSaatLogin() = runTest {
        var cookieHeader: String? = null
        var authHeader: String? = null
        var pageIdHeader: String? = null
        val tube = InnerTube(mockClient {
            cookieHeader = it.headers["cookie"]
            authHeader = it.headers["Authorization"]
            pageIdHeader = it.headers["X-Goog-PageId"]
            okJson()
        })
        // PLAN G.3: useLoginForBrowse = true — search berkirim cookie +
        // SAPISIDHASH + X-Goog-PageId bila authState membawa sesi login.
        tube.search(
            YouTubeClient.WEB_REMIX,
            query = "x",
            authState = PlaybackAuthState(
                cookie = "SAPISID=dummy-sapisid; LOGIN_INFO=dummy-login",
                dataSyncId = "delegatedDummy||userDummy",
            ),
        )
        assertEquals("SAPISID=dummy-sapisid; LOGIN_INFO=dummy-login", cookieHeader)
        assertTrue(authHeader?.startsWith("SAPISIDHASH ") == true)
        assertEquals("delegatedDummy", pageIdHeader)
    }

    @Test
    fun browseMengirimCookieSaatLogin() = runTest {
        var cookieHeader: String? = null
        var authHeader: String? = null
        val tube = InnerTube(mockClient {
            cookieHeader = it.headers["cookie"]
            authHeader = it.headers["Authorization"]
            okJson()
        })
        // PLAN G.3: browse ikut useLoginForBrowse — tanpa setLogin eksplisit
        // pun cookie terkirim bila ada sesi login.
        tube.browse(
            YouTubeClient.WEB_REMIX,
            browseId = "FEmusic_home",
            authState = PlaybackAuthState(cookie = "SAPISID=dummy-sapisid; LOGIN_INFO=dummy-login"),
        )
        assertEquals("SAPISID=dummy-sapisid; LOGIN_INFO=dummy-login", cookieHeader)
        assertTrue(authHeader?.startsWith("SAPISIDHASH ") == true)
    }

    @Test
    fun tanpaCookieTidakAdaHeaderLogin() = runTest {
        val loginHeaders = mutableMapOf<String, String?>()
        val tube = InnerTube(mockClient {
            loginHeaders["cookie"] = it.headers["cookie"]
            loginHeaders["Authorization"] = it.headers["Authorization"]
            loginHeaders["X-Goog-PageId"] = it.headers["X-Goog-PageId"]
            okJson()
        })
        tube.search(YouTubeClient.WEB_REMIX, query = "x")
        tube.accountMenu(YouTubeClient.WEB_REMIX)
        assertNull(loginHeaders["cookie"])
        assertNull(loginHeaders["Authorization"])
        assertNull(loginHeaders["X-Goog-PageId"])
    }

    @Test
    fun accountMenuMengirimCookieSapisidhashDanPageId() = runTest {
        var capturedUrl = ""
        var capturedBody = ""
        val capturedHeaders = mutableMapOf<String, String>()
        val tube = InnerTube(mockClient {
            capturedUrl = it.url.toString()
            capturedBody = (it.body as TextContent).text
            it.headers.forEach { k, v -> capturedHeaders[k] = v.joinToString() }
            okJson()
        })
        tube.accountMenu(
            YouTubeClient.WEB_REMIX,
            authState = PlaybackAuthState(
                cookie = "SAPISID=dummy-sapisid; LOGIN_INFO=dummy-login",
                visitorData = "CgtDummyVisitor",
                dataSyncId = "delegatedDummy||userDummy",
            ),
        )
        assertEquals("https://music.youtube.com/youtubei/v1/account/account_menu?prettyPrint=false", capturedUrl)
        assertEquals("SAPISID=dummy-sapisid; LOGIN_INFO=dummy-login", capturedHeaders["cookie"])
        assertTrue(capturedHeaders["Authorization"]?.startsWith("SAPISIDHASH ") == true)
        assertEquals("delegatedDummy", capturedHeaders["X-Goog-PageId"])
        assertEquals("CgtDummyVisitor", capturedHeaders["X-Goog-Visitor-Id"])
        val body = json.parseToJsonElement(capturedBody).jsonObject
        val ctx = body["context"]?.jsonObject
        assertEquals("WEB_REMIX", ctx?.get("client")?.jsonObject?.get("clientName")?.jsonPrimitive?.content)
        // onBehalfOfUser = sisi delegated dari dataSyncId.
        assertEquals("delegatedDummy", ctx?.get("user")?.jsonObject?.get("onBehalfOfUser")?.jsonPrimitive?.content)
    }

    @Test
    fun accountChannelsMengirimCookieSapisidhashDanPageId() = runTest {
        var capturedUrl = ""
        val capturedHeaders = mutableMapOf<String, String>()
        val tube = InnerTube(mockClient {
            capturedUrl = it.url.toString()
            it.headers.forEach { k, v -> capturedHeaders[k] = v.joinToString() }
            okJson()
        })
        tube.accountChannels(
            YouTubeClient.WEB_REMIX,
            authState = PlaybackAuthState(
                cookie = "SAPISID=dummy-sapisid; LOGIN_INFO=dummy-login",
                dataSyncId = "delegatedDummy||userDummy",
            ),
        )
        assertEquals("https://music.youtube.com/youtubei/v1/account/accounts_list?prettyPrint=false", capturedUrl)
        assertEquals("SAPISID=dummy-sapisid; LOGIN_INFO=dummy-login", capturedHeaders["cookie"])
        assertTrue(capturedHeaders["Authorization"]?.startsWith("SAPISIDHASH ") == true)
        assertEquals("delegatedDummy", capturedHeaders["X-Goog-PageId"])
    }

    @Test
    fun playerDenganCookieMengirimPageIdDariDataSyncId() = runTest {
        var pageIdHeader: String? = null
        var cookieHeader: String? = null
        val tube = InnerTube(mockClient {
            pageIdHeader = it.headers["X-Goog-PageId"]
            cookieHeader = it.headers["cookie"]
            okJson()
        })
        tube.player(
            YouTubeClient.WEB_REMIX,
            "QqJ-Vp8mvbk",
            authState = PlaybackAuthState(
                cookie = "SAPISID=dummy-sapisid; LOGIN_INFO=dummy-login",
                dataSyncId = "delegatedDummy||userDummy",
            ),
        )
        assertEquals("SAPISID=dummy-sapisid; LOGIN_INFO=dummy-login", cookieHeader)
        assertEquals("delegatedDummy", pageIdHeader)
    }

    @Test
    fun playerDenganCookieMengirimSapisidhash() = runTest {
        var cookieHeader: String? = null
        var authHeader: String? = null
        val tube = InnerTube(mockClient {
            cookieHeader = it.headers["cookie"]
            authHeader = it.headers["Authorization"]
            okJson()
        })
        // Aturan "hanya bila login": player (setLogin = true) mengirim
        // cookie + SAPISIDHASH bila cookie login memang ada.
        tube.player(
            YouTubeClient.WEB_REMIX,
            "QqJ-Vp8mvbk",
            authState = PlaybackAuthState(cookie = "SAPISID=abc123; SID=def"),
        )
        assertEquals("SAPISID=abc123; SID=def", cookieHeader)
        assertTrue(authHeader?.startsWith("SAPISIDHASH ") == true)
    }

    @Test
    fun getSwJsDataTanpaRetryHanyaSekaliBilaGagal() = runTest {
        var calls = 0
        val tube = InnerTube(mockClient {
            calls++
            throw IOException("putus")
        })
        assertFailsWith<IOException> { tube.getSwJsData(withRetry = false) }
        assertEquals(1, calls)
    }

    @Test
    fun getSwJsDataMemakaiGetKeMusicYoutube() = runTest {
        var capturedUrl = ""
        var capturedMethod = ""
        val tube = InnerTube(mockClient {
            capturedUrl = it.url.toString()
            capturedMethod = it.method.value
            okJson()
        })
        tube.getSwJsData()
        assertEquals("GET", capturedMethod)
        assertEquals("https://music.youtube.com/sw.js_data", capturedUrl)
    }
}
