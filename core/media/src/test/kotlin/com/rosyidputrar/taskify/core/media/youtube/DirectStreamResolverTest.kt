package com.rosyidputrar.taskify.core.media.youtube

import com.rosyidputrar.taskify.core.domain.media.MusicQuality
import com.rosyidputrar.taskify.core.innertube.InnerTube
import com.rosyidputrar.taskify.core.innertube.PlaybackAuthState
import com.rosyidputrar.taskify.core.innertube.models.YouTubeClient
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
        clients: List<YouTubeClient> = DirectStreamResolver.DEFAULT_CLIENTS,
        attemptTimeoutMs: Long = DirectStreamResolver.DIRECT_TIMEOUT_MS,
    ): DirectStreamResolver {
        val engine = MockEngine(handler)
        val tube = InnerTube(HttpClient(engine) { expectSuccess = true })
        return DirectStreamResolver(tube, clients, attemptTimeoutMs)
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
        assertEquals("20.10.4", sentVersion)
        assertEquals("1RrF6Ee_io0", sentVideoId)
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
            clients = listOf(YouTubeClient.ANDROID),
        )
        val failure = failureOf(resolver.resolve("1RrF6Ee_io0"))
        assertEquals(StreamFailure.LoginRequired("Masuk dulu"), failure)
    }

    @Test fun `UNPLAYABLE jadi StreamFailure Unplayable`() = runBlocking {
        val resolver = resolverFor(
            { okJson("""{"playabilityStatus":{"status":"UNPLAYABLE","reason":"Halaman perlu dimuat ulang."}}""") },
            clients = listOf(YouTubeClient.ANDROID),
        )
        val failure = failureOf(resolver.resolve("1RrF6Ee_io0"))
        assertTrue(failure is StreamFailure.Unplayable)
    }

    @Test fun `tanpa streamingData jadi StreamFailure NoFormat`() = runBlocking {
        val resolver = resolverFor(
            { okJson("""{"playabilityStatus":{"status":"OK"}}""") },
            clients = listOf(YouTubeClient.ANDROID),
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
            clients = listOf(YouTubeClient.ANDROID),
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
            clients = listOf(YouTubeClient.ANDROID),
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
            clients = listOf(YouTubeClient.ANDROID),
        )
        assertEquals(251, resolver.resolve("1RrF6Ee_io0").getOrThrow().itag)
        assertEquals(2, calls)
    }

    @Test fun `respons bukan JSON jadi StreamFailure InvalidResponse`() = runBlocking {
        val resolver = resolverFor(
            { okJson("<html>consent</html>") },
            clients = listOf(YouTubeClient.ANDROID),
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
            clients = listOf(YouTubeClient.ANDROID),
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
        assertEquals(listOf("ANDROID", "WEB_REMIX", "MWEB"), seen)
    }

    @Test fun `gagal semua klien mengembalikan kegagalan klien utama`() = runBlocking {
        val resolver = resolverFor({ _ ->
            okJson("""{"playabilityStatus":{"status":"LOGIN_REQUIRED","reason":"Login"}}""")
        })
        // ANDROID + WEB_REMIX + MWEB semua LOGIN_REQUIRED → yang dilaporkan
        // kegagalan ANDROID (klien utama).
        assertEquals(StreamFailure.LoginRequired("Login"), failureOf(resolver.resolve("1RrF6Ee_io0")))
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
            clients = listOf(YouTubeClient.ANDROID),
        )
        assertNull(resolver.tryResolve("1RrF6Ee_io0"))
    }

    @Test fun `tryResolve mengembalikan null bila jaringan mati`() = runBlocking {
        val resolver = resolverFor(
            { throw IOException("putus") },
            clients = listOf(YouTubeClient.ANDROID),
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
            clients = listOf(YouTubeClient.ANDROID),
        )
        val failure = failureOf(resolver.resolve("1RrF6Ee_io0"))
        assertTrue(failure is StreamFailure.Network)
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
}
