package com.rosyidputrar.taskify.core.media.mytube

import com.rosyidputrar.taskify.core.domain.media.MusicQuality
import com.rosyidputrar.taskify.core.innertube.InnerTube
import com.rosyidputrar.taskify.core.innertube.PlaybackAuthState
import com.rosyidputrar.taskify.core.innertube.models.MyTubeClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Resolve stream direct — cermin `resolveDirectStream`/`tryDirectStream`
 * di `worker/direct.test.ts`, dengan MockEngine sebagai pengganti `fetch`.
 *
 * Tes resolve memakai `runBlocking` (waktu nyata), BUKAN `runTest`:
 * `DirectStreamResolver` memakai `withTimeoutOrNull(10 detik)`; di bawah
 * `runTest`, jam virtual langsung melompat ke batas timeout begitu
 * MockEngine men-suspend, jadi percobaan selalu gagal Timeout sebelum
 * permintaan sempat jalan (pelajaran Tahap 2). `pickAudioFormat` murni
 * diuji terpisah di `PickAudioFormatTest`.
 */
class DirectStreamResolverTest {

    private val testJson = Json { ignoreUnknownKeys = true }

    private val url251 = "https://rr1---sn-xxxx.googlevideo.com/videoplayback?itag=251&x=y"
    private val url249 = "https://rr1---sn-xxxx.googlevideo.com/videoplayback?itag=249&x=y"
    private val url140 = "https://rr1---sn-xxxx.googlevideo.com/videoplayback?itag=140&x=y"
    private val url139 = "https://rr1---sn-xxxx.googlevideo.com/videoplayback?itag=139&x=y"

    /** Enam format audio yang sama seperti `sixFormats` Worker. */
    private val sixFormatsJson = """
        {"itag":139,"mimeType":"audio/mp4; codecs=\"mp4a.40.5\"","bitrate":50341,"url":"$url139"},
        {"itag":140,"mimeType":"audio/mp4; codecs=\"mp4a.40.2\"","bitrate":131105,"url":"$url140"},
        {"itag":249,"mimeType":"audio/webm; codecs=\"opus\"","bitrate":67631,"url":"$url249"},
        {"itag":251,"mimeType":"audio/webm; codecs=\"opus\"","bitrate":169643,"url":"$url251"},
        {"itag":599,"mimeType":"audio/mp4; codecs=\"mp4a.40.5\"","bitrate":32340,"url":"https://rr1---sn-xxxx.googlevideo.com/videoplayback?itag=599"},
        {"itag":600,"mimeType":"audio/webm; codecs=\"opus\"","bitrate":46007,"url":"https://rr1---sn-xxxx.googlevideo.com/videoplayback?itag=600"}
    """.trimIndent()

    private fun playerOk(formatsJson: String, audioConfigJson: String? = null): String {
        val config = audioConfigJson?.let { ""","playerConfig":{"audioConfig":$it}""" } ?: ""
        return """{"playabilityStatus":{"status":"OK"}$config,"streamingData":{"expiresInSeconds":"21540","formats":[],"adaptiveFormats":[$formatsJson]}}"""
    }

    private fun MockRequestHandleScope.okJson(body: String) =
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

    private fun clientNameOf(request: io.ktor.client.request.HttpRequestData): String {
        val text = (request.body as? TextContent)?.text ?: return ""
        return testJson.parseToJsonElement(text).jsonObject["context"]
            ?.jsonObject?.get("client")?.jsonObject?.get("clientName")
            ?.jsonPrimitive?.content ?: ""
    }

    private fun resolverFor(
        handler: MockRequestHandler,
        clients: List<MyTubeClient> = DirectStreamResolver.DEFAULT_CLIENTS,
        attemptTimeoutMs: Long = DirectStreamResolver.DIRECT_TIMEOUT_MS,
        signatureTimestampProvider: (suspend (MyTubeClient, String) -> Int?)? = null,
        ensureVisitorData: (suspend () -> String?)? = null,
        failedStreamClients: FailedStreamClients? = null,
    ): DirectStreamResolver {
        val engine = MockEngine(handler)
        val tube = InnerTube(HttpClient(engine) { expectSuccess = true })
        return DirectStreamResolver(
            tube, clients, attemptTimeoutMs, signatureTimestampProvider, ensureVisitorData,
            failedStreamClients = failedStreamClients,
        )
    }

    private fun failureOf(result: Result<DirectStream>): StreamFailure? =
        (result.exceptionOrNull() as? StreamFailureException)?.failure

    @Test fun `memanggil player sebagai klien ANDROID dan memilih opus 251`() = runBlocking {
        var sentClient = ""
        var sentVersion = ""
        var sentVideoId = ""
        val resolver = resolverFor({ request ->
            val body = testJson.parseToJsonElement((request.body as TextContent).text).jsonObject
            val client = body["context"]?.jsonObject?.get("client")?.jsonObject
            sentClient = client?.get("clientName")?.jsonPrimitive?.content ?: ""
            sentVersion = client?.get("clientVersion")?.jsonPrimitive?.content ?: ""
            sentVideoId = body["videoId"]?.jsonPrimitive?.content ?: ""
            assertTrue(request.url.toString().contains("/youtubei/v1/player"))
            okJson(playerOk(sixFormatsJson))
        })
        val direct = resolver.resolve("1RrF6Ee_io0").getOrThrow()
        assertEquals(url251, direct.url)
        assertTrue(direct.mimeType.contains("opus"))
        assertEquals(251, direct.itag)
        assertEquals(169643, direct.bitrate)
        assertEquals(21540, direct.expiresInSeconds)
        assertEquals("ANDROID", sentClient)
        assertEquals("21.26.364", sentVersion)
        assertEquals("1RrF6Ee_io0", sentVideoId)
    }

    @Test fun `contentLength dari format dipakai DirectStream`() = runBlocking {
        val url = "https://rr1---sn-xxxx.googlevideo.com/videoplayback?c=ANDROID&itag=251&clen=5495333"
        val formats = """{"itag":251,"mimeType":"audio/webm; codecs=\"opus\"","bitrate":169643,"contentLength":"5495333","url":"$url"}"""
        val resolver = resolverFor({ _ -> okJson(playerOk(formats)) })
        val direct = resolver.resolve("1RrF6Ee_io0").getOrThrow()
        assertEquals(5_495_333L, direct.contentLength)
    }

    @Test fun `clen URL dipakai bila format tanpa contentLength`() = runBlocking {
        val url = "https://rr1---sn-xxxx.googlevideo.com/videoplayback?c=ANDROID&itag=251&clen=2898086"
        val formats = """{"itag":251,"mimeType":"audio/webm; codecs=\"opus\"","bitrate":169643,"url":"$url"}"""
        val resolver = resolverFor({ _ -> okJson(playerOk(formats)) })
        val direct = resolver.resolve("1RrF6Ee_io0").getOrThrow()
        assertEquals(2_898_086L, direct.contentLength)
    }

    @Test fun `format cipher-only tidak dipilih tapi tidak menggagalkan resolve`() = runBlocking {
        val formats = """
            {"itag":251,"mimeType":"audio/webm; codecs=\"opus\"","bitrate":169643,"url":"$url251"},
            {"itag":140,"mimeType":"audio/mp4; codecs=\"mp4a.40.2\"","bitrate":131105,"signatureCipher":"s=abc&url=https%3A%2F%2Fexample.com%2Fx"}
        """.trimIndent()
        val resolver = resolverFor({ _ -> okJson(playerOk(formats)) })
        val direct = resolver.resolve("1RrF6Ee_io0").getOrThrow()
        assertEquals(url251, direct.url, "format ber-URL langsung tetap dipilih")
    }

    @Test fun `login penuh memakai WEB_CREATOR dulu`() = runBlocking {
        var sentClient = ""
        val resolver = DirectStreamResolver(
            InnerTube(HttpClient(MockEngine({ request ->
                sentClient = clientNameOf(request)
                okJson(playerOk(sixFormatsJson))
            })) { expectSuccess = true }),
        )
        val auth = PlaybackAuthState(
            cookie = "SAPISID=dummy; LOGIN_INFO=dummy",
            dataSyncId = "delegatedDummy||userDummy",
        )
        val direct = resolver.resolve("1RrF6Ee_io0", authState = auth).getOrThrow()
        assertEquals("WEB_CREATOR", sentClient)
        assertEquals(url251, direct.url)
    }

    @Test fun `cookie tanpa dataSyncId ditolak tanpa memanggil player`() = runBlocking {
        var calls = 0
        val resolver = resolverFor({ _ ->
            calls++
            okJson(playerOk(sixFormatsJson))
        })
        val auth = PlaybackAuthState(cookie = "SAPISID=dummy; LOGIN_INFO=dummy")
        val result = resolver.resolve("1RrF6Ee_io0", authState = auth)
        assertEquals(StreamFailure.InvalidLoginContext, failureOf(result))
        assertEquals(0, calls, "gate C2-12: player tidak pernah dipanggil")
    }

    @Test fun `PoToken ditempel ke player dan pot ke URL`() = runBlocking {
        var sentBody = ""
        val resolver = DirectStreamResolver(
            InnerTube(HttpClient(MockEngine({ request ->
                sentBody = (request.body as TextContent).text
                okJson(playerOk(sixFormatsJson))
            })) { expectSuccess = true }),
        )
        val auth = PlaybackAuthState(
            cookie = "SAPISID=dummy; LOGIN_INFO=dummy",
            dataSyncId = "delegatedDummy||userDummy",
            poTokenPlayer = "PTOK_VIDEO",
            poTokenPlayerVideoId = "1RrF6Ee_io0",
            poTokenGvs = "GTOK_VIDEO",
            poTokenGvsVideoId = "1RrF6Ee_io0",
            webClientPoTokenEnabled = true,
        )
        val direct = resolver.resolve("1RrF6Ee_io0", authState = auth).getOrThrow()
        val body = testJson.parseToJsonElement(sentBody).jsonObject
        assertEquals(
            "PTOK_VIDEO",
            body["serviceIntegrityDimensions"]?.jsonObject?.get("poToken")?.jsonPrimitive?.content,
        )
        assertTrue(direct.url.contains("pot=GTOK_VIDEO"), "pot ditempel ke URL stream")
    }

    @Test fun `signatureTimestamp diteruskan ke player bila klien mendukung`() = runBlocking {
        var sentBody = ""
        val resolver = resolverFor(
            { request ->
                sentBody = (request.body as TextContent).text
                okJson(playerOk(sixFormatsJson))
            },
            clients = listOf(MyTubeClient.ANDROID),
            signatureTimestampProvider = { _, _ -> 19283 },
        )
        resolver.resolve("1RrF6Ee_io0").getOrThrow()
        val body = testJson.parseToJsonElement(sentBody).jsonObject
        assertEquals(
            19283,
            body["playbackContext"]?.jsonObject?.get("contentPlaybackContext")
                ?.jsonObject?.get("signatureTimestamp")?.jsonPrimitive?.content?.toInt(),
        )
    }

    @Test fun `visitorData dipastikan sebelum resolve anonim`() = runBlocking {
        var sentVisitorId: String? = null
        val resolver = resolverFor(
            { request ->
                sentVisitorId = request.headers["X-Goog-Visitor-Id"]
                okJson(playerOk(sixFormatsJson))
            },
            ensureVisitorData = { "VD_TEST_123" },
        )
        resolver.resolve("1RrF6Ee_io0", authState = PlaybackAuthState()).getOrThrow()
        assertEquals("VD_TEST_123", sentVisitorId)
    }

    @Test fun `menggabungkan formats dan adaptiveFormats`() = runBlocking {
        val resolver = resolverFor({ _ ->
            okJson(
                """{"playabilityStatus":{"status":"OK"},"streamingData":{"expiresInSeconds":"21540","formats":[{"itag":140,"mimeType":"audio/mp4; codecs=\"mp4a.40.2\"","bitrate":131105,"url":"$url140"}],"adaptiveFormats":[{"itag":251,"mimeType":"audio/webm; codecs=\"opus\"","bitrate":169643,"url":"$url251"}]}}""",
            )
        })
        assertEquals(251, resolver.resolve("1RrF6Ee_io0").getOrThrow().itag)
    }

    @Test fun `LOGIN_REQUIRED jadi StreamFailure LoginRequired`() = runBlocking {
        val resolver = resolverFor(
            { okJson("""{"playabilityStatus":{"status":"LOGIN_REQUIRED","reason":"Masuk dulu"}}""") },
            clients = listOf(MyTubeClient.ANDROID),
        )
        val failure = failureOf(resolver.resolve("1RrF6Ee_io0"))
        assertEquals(StreamFailure.LoginRequired("Masuk dulu"), failure)
    }

    @Test fun `UNPLAYABLE jadi StreamFailure Unplayable`() = runBlocking {
        val resolver = resolverFor(
            { okJson("""{"playabilityStatus":{"status":"UNPLAYABLE","reason":"Halaman perlu dimuat ulang."}}""") },
            clients = listOf(MyTubeClient.ANDROID),
        )
        val failure = failureOf(resolver.resolve("1RrF6Ee_io0"))
        assertTrue(failure is StreamFailure.Unplayable)
    }

    @Test fun `tanpa streamingData jadi StreamFailure NoFormat`() = runBlocking {
        val resolver = resolverFor(
            { okJson("""{"playabilityStatus":{"status":"OK"}}""") },
            clients = listOf(MyTubeClient.ANDROID),
        )
        assertEquals(StreamFailure.NoFormat, failureOf(resolver.resolve("1RrF6Ee_io0")))
    }

    @Test fun `semua format cipher-only jadi StreamFailure NoFormat`() = runBlocking {
        val resolver = resolverFor(
            { _ ->
                okJson(
                    """{"playabilityStatus":{"status":"OK"},"streamingData":{"formats":[{"itag":251,"mimeType":"audio/webm; codecs=\"opus\"","bitrate":169643,"signatureCipher":"s=abc"}],"adaptiveFormats":[]}}""",
                )
            },
            clients = listOf(MyTubeClient.ANDROID),
        )
        assertEquals(StreamFailure.NoFormat, failureOf(resolver.resolve("1RrF6Ee_io0")))
    }

    @Test fun `player balas 429 di-retry sekali lalu tetap Http 429`() = runBlocking {
        var calls = 0
        val resolver = resolverFor(
            {
                calls++
                respond("terlalu banyak", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
            },
            clients = listOf(MyTubeClient.ANDROID),
        )
        assertEquals(StreamFailure.Http(429), failureOf(resolver.resolve("1RrF6Ee_io0")))
        assertEquals(2, calls) // percobaan awal + satu retry langsung
    }

    @Test fun `player balas 429 sekali lalu sukses pada retry`() = runBlocking {
        var calls = 0
        val resolver = resolverFor(
            {
                calls++
                if (calls == 1) {
                    respond("terlalu banyak", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                } else {
                    okJson(playerOk(sixFormatsJson))
                }
            },
            clients = listOf(MyTubeClient.ANDROID),
        )
        assertEquals(251, resolver.resolve("1RrF6Ee_io0").getOrThrow().itag)
        assertEquals(2, calls)
    }

    @Test fun `respons bukan JSON jadi StreamFailure InvalidResponse`() = runBlocking {
        val resolver = resolverFor(
            { okJson("<html>consent</html>") },
            clients = listOf(MyTubeClient.ANDROID),
        )
        assertEquals(StreamFailure.InvalidResponse, failureOf(resolver.resolve("1RrF6Ee_io0")))
    }

    @Test fun `timeout 10 detik adalah batas yang dipakai resolver`() {
        assertEquals(10_000L, DirectStreamResolver.DIRECT_TIMEOUT_MS)
    }

    @Test fun `player menggantung melewati batas menjadi StreamFailure Timeout`() = runBlocking {
        val resolver = resolverFor(
            {
                delay(500)
                okJson(playerOk(sixFormatsJson))
            },
            clients = listOf(MyTubeClient.ANDROID),
            attemptTimeoutMs = 50,
        )
        assertEquals(StreamFailure.Timeout, failureOf(resolver.resolve("1RrF6Ee_io0")))
    }

    @Test fun `rantai fallback ANDROID LOGIN_REQUIRED WEB_REMIX UNPLAYABLE MWEB OK`() = runBlocking {
        val seen = mutableListOf<String>()
        val resolver = resolverFor({ request ->
            val client = clientNameOf(request)
            seen.add(client)
            when (client) {
                "ANDROID" -> okJson("""{"playabilityStatus":{"status":"LOGIN_REQUIRED","reason":"Login"}}""")
                "WEB_REMIX" -> okJson("""{"playabilityStatus":{"status":"UNPLAYABLE","reason":"Tidak bisa diputar"}}""")
                else -> okJson(playerOk(sixFormatsJson))
            }
        })
        val direct = resolver.resolve("1RrF6Ee_io0").getOrThrow()
        assertEquals(251, direct.itag)
        // ANDROID LOGIN_REQUIRED di-retry 2x dulu (flaky per permintaan)
        // sebelum jatuh ke klien berikut; WEB_REMIX UNPLAYABLE tidak di-retry.
        assertEquals(listOf("ANDROID", "ANDROID", "ANDROID", "WEB_REMIX", "MWEB"), seen)
        assertEquals("MWEB", direct.resolvedClient)
    }

    @Test fun `gagal semua klien mengembalikan kegagalan klien utama`() = runBlocking {
        val resolver = resolverFor({ _ ->
            okJson("""{"playabilityStatus":{"status":"LOGIN_REQUIRED","reason":"Login"}}""")
        })
        // ANDROID + WEB_REMIX + MWEB semua LOGIN_REQUIRED → yang dilaporkan
        // kegagalan ANDROID (klien utama).
        assertEquals(StreamFailure.LoginRequired("Login"), failureOf(resolver.resolve("1RrF6Ee_io0")))
    }

    @Test fun `klien ter-blacklist dilewati, resolve pindah ke klien berikut`() = runBlocking {
        val fingerprint = PlaybackAuthState().streamCacheFingerprint
        val blacklist = FailedStreamClients()
        blacklist.markFailed("1RrF6Ee_io0", FailedStreamClients.clientKey(MyTubeClient.ANDROID), 403, fingerprint)
        val seen = mutableListOf<String>()
        val resolver = resolverFor({ request ->
            seen += clientNameOf(request)
            okJson(playerOk(sixFormatsJson))
        }, failedStreamClients = blacklist)
        val direct = resolver.resolve("1RrF6Ee_io0").getOrThrow()
        assertEquals(listOf("WEB_REMIX"), seen, "ANDROID ter-blacklist dilewati")
        assertEquals("WEB_REMIX", direct.resolvedClient)
        assertEquals(251, direct.itag)
    }

    @Test fun `semua klien ter-blacklist mengembalikan AllClientsFailed tanpa memanggil player`() = runBlocking {
        val fingerprint = PlaybackAuthState().streamCacheFingerprint
        val blacklist = FailedStreamClients()
        for (client in DirectStreamResolver.DEFAULT_CLIENTS) {
            blacklist.markFailed("1RrF6Ee_io0", FailedStreamClients.clientKey(client), 403, fingerprint)
        }
        var calls = 0
        val resolver = resolverFor({ _ ->
            calls++
            okJson(playerOk(sixFormatsJson))
        }, failedStreamClients = blacklist)
        val failure = failureOf(resolver.resolve("1RrF6Ee_io0"))
        assertTrue(failure is StreamFailure.AllClientsFailed, "harus AllClientsFailed, bukan ${failure?.javaClass?.simpleName}")
        assertEquals(0, calls, "tidak ada klien yang dicoba")
        assertEquals(
            DirectStreamResolver.DEFAULT_CLIENTS.map { FailedStreamClients.clientKey(it) }.toSet(),
            (failure as StreamFailure.AllClientsFailed).failedClients,
        )
    }

    @Test fun `quality low high auto memilih itag yang benar dari respons player`() = runBlocking {
        val resolver = resolverFor({ _ ->
            okJson(
                playerOk(
                    sixFormatsJson,
                    """{"loudnessDb":3.7799997,"perceptualLoudnessDb":-10.22}""",
                ),
            )
        })
        assertEquals(599, resolver.resolve("1RrF6Ee_io0", MusicQuality.Low).getOrThrow().itag)
        assertEquals(249, resolver.resolve("1RrF6Ee_io0", MusicQuality.High).getOrThrow().itag)
        assertEquals(251, resolver.resolve("1RrF6Ee_io0", MusicQuality.Auto).getOrThrow().itag)
    }

    @Test fun `loudnessDb dan perceptualLoudnessDb diteruskan dari audioConfig`() = runBlocking {
        val resolver = resolverFor({ _ ->
            okJson(
                playerOk(
                    sixFormatsJson,
                    """{"loudnessDb":3.7799997,"perceptualLoudnessDb":-10.22,"enablePerFormatLoudness":true}""",
                ),
            )
        })
        val direct = resolver.resolve("1RrF6Ee_io0").getOrThrow()
        assertEquals(3.78, direct.loudnessDb!!, 0.001)
        assertEquals(-10.22, direct.perceptualLoudnessDb!!, 0.001)
    }

    @Test fun `loudnessDb jatuh ke per-format bila audioConfig tidak memberinya`() = runBlocking {
        val formatsWithLoudness = sixFormatsJson.replace(
            "\"bitrate\":",
            "\"loudnessDb\":3.7799997,\"bitrate\":",
        )
        val resolver = resolverFor({ _ ->
            okJson(playerOk(formatsWithLoudness, """{"perceptualLoudnessDb":-10.22}"""))
        })
        val direct = resolver.resolve("1RrF6Ee_io0").getOrThrow()
        assertEquals(3.78, direct.loudnessDb!!, 0.001)
        assertEquals(-10.22, direct.perceptualLoudnessDb!!, 0.001)
    }

    @Test fun `field loudness hilang bukan 0 bila respons tidak memuatnya`() = runBlocking {
        val resolver = resolverFor({ _ -> okJson(playerOk(sixFormatsJson)) })
        val direct = resolver.resolve("1RrF6Ee_io0").getOrThrow()
        assertNull(direct.loudnessDb)
        assertNull(direct.perceptualLoudnessDb)
    }

    @Test fun `tryResolve mengembalikan hasil bila direct sukses`() = runBlocking {
        val resolver = resolverFor({ _ -> okJson(playerOk(sixFormatsJson)) })
        assertEquals(251, resolver.tryResolve("1RrF6Ee_io0")?.itag)
    }

    @Test fun `tryResolve mengembalikan null bukan melempar bila direct gagal`() = runBlocking {
        val resolver = resolverFor(
            { okJson("""{"playabilityStatus":{"status":"LOGIN_REQUIRED"}}""") },
            clients = listOf(MyTubeClient.ANDROID),
        )
        assertNull(resolver.tryResolve("1RrF6Ee_io0"))
    }

    @Test fun `tryResolve mengembalikan null bila jaringan mati`() = runBlocking {
        val resolver = resolverFor(
            { throw IOException("putus") },
            clients = listOf(MyTubeClient.ANDROID),
        )
        assertNull(resolver.tryResolve("1RrF6Ee_io0"))
    }

    @Test fun `authState eksplisit dikirim sebagai header visitorData`() = runBlocking {
        var visitorHeader: String? = null
        val resolver = resolverFor({ request ->
            visitorHeader = request.headers["X-Goog-Visitor-Id"]
            okJson(playerOk(sixFormatsJson))
        })
        val direct = resolver.resolve(
            "1RrF6Ee_io0",
            authState = PlaybackAuthState(visitorData = "Cgtexplicit"),
        ).getOrThrow()
        assertEquals(251, direct.itag)
        assertEquals("Cgtexplicit", visitorHeader)
    }

    @Test fun `jaringan mati menjadi StreamFailure Network`() = runBlocking {
        val resolver = resolverFor(
            { throw IOException("putus") },
            clients = listOf(MyTubeClient.ANDROID),
        )
        val failure = failureOf(resolver.resolve("1RrF6Ee_io0"))
        assertTrue(failure is StreamFailure.Network)
    }

    @Test fun `LOGIN_REQUIRED sekali lalu sukses pada retry`() = runBlocking {
        var calls = 0
        val resolver = resolverFor(
            {
                calls++
                if (calls == 1) {
                    okJson("""{"playabilityStatus":{"status":"LOGIN_REQUIRED","reason":"Login"}}""")
                } else {
                    okJson(playerOk(sixFormatsJson))
                }
            },
            clients = listOf(MyTubeClient.ANDROID),
        )
        val direct = resolver.resolve("1RrF6Ee_io0").getOrThrow()
        assertEquals(251, direct.itag)
        assertEquals(2, calls, "percobaan awal + satu retry LOGIN_REQUIRED")
        assertEquals("ANDROID", direct.resolvedClient)
    }

    @Test fun `LOGIN_REQUIRED dua kali lalu sukses pada retry kedua`() = runBlocking {
        var calls = 0
        val resolver = resolverFor(
            {
                calls++
                if (calls <= 2) {
                    okJson("""{"playabilityStatus":{"status":"LOGIN_REQUIRED","reason":"Login"}}""")
                } else {
                    okJson(playerOk(sixFormatsJson))
                }
            },
            clients = listOf(MyTubeClient.ANDROID),
        )
        assertEquals(251, resolver.resolve("1RrF6Ee_io0").getOrThrow().itag)
        assertEquals(3, calls, "percobaan awal + dua retry LOGIN_REQUIRED")
    }

    @Test fun `LOGIN_REQUIRED tiga kali berturut-turut jadi kegagalan tanpa percobaan keempat`() = runBlocking {
        var calls = 0
        val resolver = resolverFor(
            {
                calls++
                okJson("""{"playabilityStatus":{"status":"LOGIN_REQUIRED","reason":"Login"}}""")
            },
            clients = listOf(MyTubeClient.ANDROID),
        )
        assertEquals(StreamFailure.LoginRequired("Login"), failureOf(resolver.resolve("1RrF6Ee_io0")))
        assertEquals(3, calls, "batas retry LOGIN_REQUIRED: 1 awal + 2 ulang")
    }

    @Test fun `klien yang menghasilkan URL dicatat di resolvedClient`() = runBlocking {
        val resolver = resolverFor({ _ -> okJson(playerOk(sixFormatsJson)) })
        assertEquals("ANDROID", resolver.resolve("1RrF6Ee_io0").getOrThrow().resolvedClient)
    }

    @Test fun `preferMp4a memilih itag 140 dari respons player yang sama`() = runBlocking {
        val resolver = resolverFor({ _ -> okJson(playerOk(sixFormatsJson)) })
        val direct = resolver.resolve("1RrF6Ee_io0", preferMp4a = true).getOrThrow()
        assertEquals(140, direct.itag)
        assertTrue("mp4a" in direct.mimeType)
        // Tanpa flag, respons yang sama memberi opus 251.
        val biasa = resolver.resolve("1RrF6Ee_io0").getOrThrow()
        assertEquals(251, biasa.itag)
    }

    // ---- Tahap 8 (C2-23/25): tangga kualitas + metered ----

    @Test fun `tangga kualitas turun bila tier Auto gagal Unplayable lalu High sukses`() = runBlocking {
        var calls = 0
        val resolver = resolverFor(
            {
                calls++
                if (calls <= 3) {
                    // Tier Auto: ketiga klien menjawab UNPLAYABLE.
                    okJson("""{"playabilityStatus":{"status":"UNPLAYABLE","reason":"tidak bisa"}}""")
                } else {
                    okJson(playerOk(sixFormatsJson))
                }
            },
        )
        val direct = resolver.resolve("1RrF6Ee_io0", MusicQuality.Auto).getOrThrow()
        assertEquals(249, direct.itag, "tier High memilih opus tengah 249")
        assertEquals(4, calls, "3 panggilan tier Auto + 1 panggilan tier High")
    }

    @Test fun `tangga berhenti bila semua tier gagal kembalikan kegagalan terakhir`() = runBlocking {
        var calls = 0
        val resolver = resolverFor(
            {
                calls++
                okJson("""{"playabilityStatus":{"status":"UNPLAYABLE","reason":"tidak bisa"}}""")
            },
            clients = listOf(MyTubeClient.ANDROID),
        )
        assertTrue(failureOf(resolver.resolve("1RrF6Ee_io0", MusicQuality.Auto)) is StreamFailure.Unplayable)
        // 3 tier (Auto, High, Low) x 1 klien = 3 panggilan, tanpa retry.
        assertEquals(3, calls)
    }

    @Test fun `LOGIN_REQUIRED tidak menuruni tangga kualitas`() = runBlocking {
        var calls = 0
        val resolver = resolverFor(
            {
                calls++
                okJson("""{"playabilityStatus":{"status":"LOGIN_REQUIRED","reason":"Login"}}""")
            },
            clients = listOf(MyTubeClient.ANDROID),
        )
        assertTrue(failureOf(resolver.resolve("1RrF6Ee_io0", MusicQuality.Auto)) is StreamFailure.LoginRequired)
        // Hanya retry LOGIN_REQUIRED (3x), tanpa percobaan tier High/Low.
        assertEquals(3, calls)
    }

    @Test fun `metered Auto langsung memilih tier High`() = runBlocking {
        var calls = 0
        val resolver = resolverFor(
            { _ ->
                calls++
                okJson(playerOk(sixFormatsJson))
            },
            clients = listOf(MyTubeClient.ANDROID),
        )
        val direct = resolver.resolve("1RrF6Ee_io0", MusicQuality.Auto, networkMetered = true).getOrThrow()
        assertEquals(249, direct.itag, "Auto metered = High, opus tengah 249")
        assertEquals(1, calls, "langsung tier High, tanpa percobaan Auto")
    }

    @Test fun `playbackTrackingUrl diekstrak dari respons player`() = runBlocking {
        val tracking = "https://www.youtube.com/api/stats/watchtime?ns=yt&ver=2"
        val body = """{"playabilityStatus":{"status":"OK"},"playbackTracking":{"videostatsPlaybackUrl":{"baseUrl":"$tracking"}},"streamingData":{"expiresInSeconds":"21540","formats":[],"adaptiveFormats":[$sixFormatsJson]}}"""
        val resolver = resolverFor(
            { _ -> okJson(body) },
            clients = listOf(MyTubeClient.ANDROID),
        )
        assertEquals(tracking, resolver.resolve("1RrF6Ee_io0").getOrThrow().playbackTrackingUrl)
    }

    @Test fun `playbackTrackingUrl null bila respons tidak memuatnya`() = runBlocking {
        val resolver = resolverFor(
            { _ -> okJson(playerOk(sixFormatsJson)) },
            clients = listOf(MyTubeClient.ANDROID),
        )
        assertNull(resolver.resolve("1RrF6Ee_io0").getOrThrow().playbackTrackingUrl)
    }
}
