package com.rosyidputrar.taskify.core.innertube

import com.rosyidputrar.taskify.core.innertube.models.MyTubeClient
import com.rosyidputrar.taskify.core.innertube.models.MyTubeLocale
import com.rosyidputrar.taskify.core.innertube.models.body.PlayerBody
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
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
        tube.search(MyTubeClient.WEB_REMIX, query = "tulus", params = "EgWKAQIIAWoKEAkQBRAKEAMQBA==")

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
        tube.search(MyTubeClient.WEB_REMIX, query = "x")
        assertEquals("CgttZXN0", visitorHeader)
    }

    @Test
    fun playerAndroidMemakaiOriginMyTubeDanBodyPlayer() = runTest {
        var capturedUrl = ""
        var capturedBody = ""
        val capturedHeaders = mutableMapOf<String, String>()
        val tube = InnerTube(mockClient {
            capturedUrl = it.url.toString()
            capturedBody = (it.body as TextContent).text
            it.headers.forEach { k, v -> capturedHeaders[k] = v.joinToString() }
            okJson()
        })
        tube.player(MyTubeClient.ANDROID, "QqJ-Vp8mvbk")

        assertEquals("https://www.youtube.com/youtubei/v1/player?prettyPrint=false", capturedUrl)
        assertEquals("3", capturedHeaders["X-YouTube-Client-Name"])
        assertEquals("21.26.364", capturedHeaders["X-YouTube-Client-Version"])
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
        tube.browse(MyTubeClient.WEB_REMIX, browseId = "FEmusic_home")
        tube.next(MyTubeClient.WEB_REMIX, videoId = "QqJ-Vp8mvbk", playlistId = "RDAMVMQqJ-Vp8mvbk")
        tube.getSearchSuggestions(MyTubeClient.WEB_REMIX, "tulus")
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
        val result = tube.search(MyTubeClient.WEB_REMIX, query = "x")
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
        assertFailsWith<IOException> { tube.search(MyTubeClient.WEB_REMIX, query = "x") }
        assertEquals(3, calls)
    }

    @Test
    fun http400TidakDiRetry() = runTest {
        var calls = 0
        val tube = InnerTube(mockClient {
            calls++
            respond("""{"error":"bad"}""", HttpStatusCode.BadRequest, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        })
        assertFailsWith<ClientRequestException> { tube.search(MyTubeClient.WEB_REMIX, query = "x") }
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
            MyTubeClient.WEB_REMIX,
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
            MyTubeClient.WEB_REMIX,
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
            MyTubeClient.WEB_REMIX,
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
        tube.search(MyTubeClient.WEB_REMIX, query = "x")
        tube.accountMenu(MyTubeClient.WEB_REMIX)
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
            MyTubeClient.WEB_REMIX,
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
            MyTubeClient.WEB_REMIX,
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
            MyTubeClient.WEB_REMIX,
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
            MyTubeClient.WEB_REMIX,
            "QqJ-Vp8mvbk",
            authState = PlaybackAuthState(cookie = "SAPISID=abc123; SID=def"),
        )
        assertEquals("SAPISID=abc123; SID=def", cookieHeader)
        assertTrue(authHeader?.startsWith("SAPISIDHASH ") == true)
    }

    @Test
    fun player400BukanInvalidArgumentTidakDiRetry() = runTest {
        var calls = 0
        val tube = InnerTube(mockClient {
            calls++
            respond(
                """{"error":{"code":400,"message":"Permintaan buruk"}}""",
                HttpStatusCode.BadRequest,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        })
        assertFailsWith<ClientRequestException> {
            tube.player(
                MyTubeClient.WEB_REMIX, "QqJ-Vp8mvbk",
                authState = PlaybackAuthState(
                    cookie = "SAPISID=dummy-sapisid; LOGIN_INFO=dummy-login",
                    dataSyncId = "delegatedDummy||userDummy",
                ),
            )
        }
        assertEquals(1, calls)
    }

    @Test
    fun playerRetryTanpaDataSyncIdBila400InvalidArgument() = runTest {
        var calls = 0
        val bodies = mutableListOf<String>()
        val pageIds = mutableListOf<String?>()
        val tube = InnerTube(mockClient {
            calls++
            bodies += (it.body as TextContent).text
            pageIds += it.headers["X-Goog-PageId"]
            if (calls == 1) {
                respond(
                    """{"error":{"code":400,"message":"Request contains an invalid argument.","errors":[{"reason":"INVALID_ARGUMENT"}]}}""",
                    HttpStatusCode.BadRequest,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            } else {
                okJson("""{"playabilityStatus":{"status":"OK"}}""")
            }
        })
        val result = tube.player(
            MyTubeClient.WEB_REMIX, "QqJ-Vp8mvbk",
            authState = PlaybackAuthState(
                cookie = "SAPISID=dummy-sapisid; LOGIN_INFO=dummy-login",
                dataSyncId = "delegatedDummy||userDummy",
            ),
        )
        assertTrue(result.contains("OK"))
        assertEquals(2, calls, "percobaan pertama 400 INVALID_ARGUMENT, kedua tanpa dataSyncId")
        fun onBehalfOfUser(body: String): String? =
            json.parseToJsonElement(body).jsonObject["context"]?.jsonObject
                ?.get("user")?.jsonObject?.get("onBehalfOfUser")?.jsonPrimitive?.contentOrNull
        assertEquals("delegatedDummy", onBehalfOfUser(bodies[0]))
        assertEquals("delegatedDummy", pageIds[0])
        assertNull(onBehalfOfUser(bodies[1]), "retry tanpa dataSyncId di badan")
        assertNull(pageIds[1], "retry tanpa header X-Goog-PageId")
    }

    @Test
    fun player400InvalidArgumentDenganOriginMismatchTidakDiRetry() = runTest {
        var calls = 0
        val tube = InnerTube(mockClient {
            calls++
            respond(
                """{"error":{"message":"Origin doesn't match Host. INVALID_ARGUMENT"}}""",
                HttpStatusCode.BadRequest,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        })
        assertFailsWith<ClientRequestException> {
            tube.player(
                MyTubeClient.WEB_REMIX, "QqJ-Vp8mvbk",
                authState = PlaybackAuthState(
                    cookie = "SAPISID=dummy-sapisid; LOGIN_INFO=dummy-login",
                    dataSyncId = "delegatedDummy||userDummy",
                ),
            )
        }
        assertEquals(1, calls, "pesan Origin mismatch dikecualikan dari retry")
    }

    @Test
    fun cookieTanpaSapisidDisintesisDariSecure3Papisid() = runTest {
        var cookieHeader: String? = null
        var authHeader: String? = null
        val tube = InnerTube(mockClient {
            cookieHeader = it.headers["cookie"]
            authHeader = it.headers["Authorization"]
            okJson()
        })
        tube.player(
            MyTubeClient.WEB_REMIX, "QqJ-Vp8mvbk",
            authState = PlaybackAuthState(cookie = "__Secure-3PAPISID=xyz789; LOGIN_INFO=abc"),
        )
        assertEquals("__Secure-3PAPISID=xyz789; LOGIN_INFO=abc; SAPISID=xyz789", cookieHeader)
        assertTrue(authHeader?.startsWith("SAPISIDHASH ") == true)
    }

    @Test
    fun playerBodyMembawaContentPlaybackContextLengkap() {
        val body = PlayerBody(
            context = MyTubeClient.WEB_REMIX.toContext(MyTubeLocale(gl = "ID", hl = "id"), null, null),
            videoId = "QqJ-Vp8mvbk",
            playbackContext = PlayerBody.PlaybackContext(
                PlayerBody.PlaybackContext.ContentPlaybackContext(19283),
            ),
        )
        val encoded = InnertubeJson.encodeToString(body)
        assertTrue(encoded.contains("\"signatureTimestamp\":19283"))
        assertTrue(encoded.contains("\"html5Preference\":\"HTML5_PREF_WANTS\""))
        assertTrue(encoded.contains("\"vis\":0"))
        assertTrue(encoded.contains("\"splay\":false"))
        assertTrue(encoded.contains("\"lactMilliseconds\":\"-1\""))
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
    fun registerPlaybackMengirimParamTelemetri() = runTest {
        var capturedUrl = ""
        val capturedHeaders = mutableMapOf<String, String>()
        val tube = InnerTube(mockClient {
            capturedUrl = it.url.toString()
            it.headers.forEach { k, v -> capturedHeaders[k] = v.joinToString() }
            okJson()
        })
        tube.registerPlayback(
            url = "https://www.youtube.com/api/stats/watchtime?ns=yt",
            cpn = "Ab3_xYz012345678",
        )
        assertTrue(capturedUrl.startsWith("https://www.youtube.com/api/stats/watchtime?"))
        assertTrue("ver=2" in capturedUrl)
        assertTrue("c=WEB_REMIX" in capturedUrl)
        assertTrue("cpn=Ab3_xYz012345678" in capturedUrl)
        assertTrue("prettyPrint=false" in capturedUrl)
        assertEquals("en-US,en;q=0.9", capturedHeaders["Accept-Language"])
        assertEquals("no-cache", capturedHeaders["Cache-Control"])
        // Header ini berisi `clientId` numerik (67 = WEB_REMIX), bukan nama —
        // sama seperti `ytClient` biasa dan kode AT (`client.clientId`).
        assertEquals("67", capturedHeaders["X-YouTube-Client-Name"])
    }

    @Test
    fun registerPlaybackDenganPlaylistMenambahListDanReferrer() = runTest {
        var capturedUrl = ""
        val tube = InnerTube(mockClient {
            capturedUrl = it.url.toString()
            okJson()
        })
        tube.registerPlayback(
            url = "https://www.youtube.com/api/stats/watchtime?ns=yt",
            cpn = "Ab3_xYz012345678",
            playlistId = "PL123",
        )
        assertTrue("list=PL123" in capturedUrl)
        assertTrue("referrer=https%3A%2F%2Fmusic.youtube.com%2Fplaylist%3Flist%3DPL123" in capturedUrl)
    }

    @Test
    fun getSwJsDataMemakaiGetKeMusicMyTube() = runTest {
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
