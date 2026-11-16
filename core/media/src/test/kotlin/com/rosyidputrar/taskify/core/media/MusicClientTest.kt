package com.rosyidputrar.taskify.core.media

import com.rosyidputrar.taskify.core.domain.media.SearchFilter
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regresi item 4 (parse amplop error Worker), item 9 (retry 429 ikut Retry-After),
 * dan item 10-klien (mimeType/source/itag dari init). HTTP dipalsukan MockEngine.
 */
class MusicClientTest {
    private fun client(handler: MockRequestHandler) =
        MusicClient("https://music.test", { "token" }, HttpClient(MockEngine) {
            expectSuccess = false
            engine { addHandler(handler) }
        })

    private fun jsonHeaders(extra: List<Pair<String, String>> = emptyList()): Headers {
        val b = HeadersBuilder()
        b.append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        extra.forEach { (k, v) -> b.append(k, v) }
        return b.build()
    }

    @Test fun errorObjectParsesCodeMessageAndRetryable() = runTest {
        val c = client {
            respond("""{"error":{"message":"Parameter \"q\" tidak sah","code":"VALIDATION","retryable":false}}""", HttpStatusCode.BadRequest, jsonHeaders())
        }
        val e = assertFailsWith<MusicException> { c.search("x", SearchFilter.Songs) }
        assertEquals("Parameter \"q\" tidak sah", e.message)
        assertEquals("VALIDATION", e.code)
        assertEquals(400, e.status)
        assertFalse(e.retryable)
        assertNull(e.retryAfterSeconds)
    }

    @Test fun rateLimit429KeepsSpecificMessage() = runTest {
        val c = client {
            respond("""{"error":{"message":"Batas stream/poll (60/menit) tercapai. Coba lagi dalam 60 detik.","code":"RATE_LIMITED","retryable":true,"retryAfterSeconds":60}}""", HttpStatusCode.TooManyRequests, jsonHeaders())
        }
        // retryAfter 60 dtk > batas retry otomatis 30 dtk → langsung dilempar apa adanya.
        val e = assertFailsWith<MusicException> { c.resolveStream("vid12345678") }
        assertEquals("Batas stream/poll (60/menit) tercapai. Coba lagi dalam 60 detik.", e.message)
        assertEquals("RATE_LIMITED", e.code)
        assertTrue(e.retryable)
        assertEquals(60, e.retryAfterSeconds)
        assertEquals(429, e.status)
    }

    @Test fun legacyStringErrorStillParsed() = runTest {
        val c = client {
            respond("""{"error":"Server sibuk"}""", HttpStatusCode.InternalServerError, jsonHeaders())
        }
        val e = assertFailsWith<MusicException> { c.search("x", SearchFilter.Songs) }
        assertEquals("Server sibuk", e.message)
        assertEquals(500, e.status)
    }

    @Test fun retryAfterSecondsTriggersRetryThenSuccess() = runTest {
        var calls = 0
        val c = client { request ->
            calls++
            if (request.url.encodedPath == "/v1/stream/init" && calls == 1) {
                respond("""{"error":{"message":"Batas stream/init (10/menit) tercapai. Coba lagi dalam 1 detik.","code":"RATE_LIMITED","retryable":true,"retryAfterSeconds":1}}""", HttpStatusCode.TooManyRequests, jsonHeaders())
            } else {
                respond("""{"url":"https://audio.test/x.mp3","source":"direct","mimeType":"audio/mpeg","itag":251}""", HttpStatusCode.OK, jsonHeaders())
            }
        }
        assertEquals("https://audio.test/x.mp3", c.resolveStream("vid12345678"))
        assertEquals(2, calls)
    }

    @Test fun retryAfterHeaderUsedWhenBodyLacksIt() = runTest {
        var calls = 0
        val c = client {
            calls++
            if (calls == 1) {
                respond("""{"error":{"message":"Coba lagi dalam 1 detik.","code":"RATE_LIMITED","retryable":true}}""", HttpStatusCode.TooManyRequests, jsonHeaders(listOf("Retry-After" to "1")))
            } else {
                respond("""{"url":"https://audio.test/x.mp3","source":"direct","mimeType":"audio/mpeg"}""", HttpStatusCode.OK, jsonHeaders())
            }
        }
        assertEquals("https://audio.test/x.mp3", c.resolveStream("vid12345678"))
        assertEquals(2, calls)
    }

    @Test fun longRetryAfterIsNotRetried() = runTest {
        var calls = 0
        val c = client {
            calls++
            respond("""{"error":{"message":"Kuota harian tercapai. Coba lagi dalam 1 jam.","code":"RATE_LIMITED","retryable":true,"retryAfterSeconds":3600}}""", HttpStatusCode.TooManyRequests, jsonHeaders())
        }
        val e = assertFailsWith<MusicException> { c.resolveStream("vid12345678") }
        assertEquals(3600, e.retryAfterSeconds)
        assertEquals(1, calls)
    }

    @Test fun poll429IsRetried() = runTest {
        var polls = 0
        val c = client { request ->
            when (request.url.encodedPath) {
                "/v1/stream/init" -> respond("""{"job":"job1","source":"converter"}""", HttpStatusCode.OK, jsonHeaders())
                "/v1/stream/poll" -> {
                    polls++
                    if (polls == 1) respond("""{"error":{"message":"Batas stream/poll (60/menit) tercapai. Coba lagi dalam 1 detik.","code":"RATE_LIMITED","retryable":true,"retryAfterSeconds":1}}""", HttpStatusCode.TooManyRequests, jsonHeaders())
                    else respond("""{"progress":100,"url":"https://audio.test/c.mp3","source":"converter"}""", HttpStatusCode.OK, jsonHeaders())
                }
                else -> respond("{}", HttpStatusCode.NotFound, headersOf())
            }
        }
        assertEquals("https://audio.test/c.mp3", c.resolveStream("vid12345678"))
        assertEquals(2, polls)
    }

    @Test fun directInitRecordsMimeTypeSourceAndItag() = runTest {
        val c = client {
            respond("""{"url":"https://audio.test/x.webm","source":"direct","mimeType":"audio/webm; codecs=opus","itag":251,"bitrate":160000}""", HttpStatusCode.OK, jsonHeaders())
        }
        val info = c.resolveStreamInfo("vid12345678")
        assertEquals("https://audio.test/x.webm", info.url)
        assertEquals("direct", info.source)
        assertEquals("audio/webm; codecs=opus", info.mimeType)
        assertEquals(251, info.itag)
        assertTrue(info.isOpus)
        assertEquals(info, c.lastStreamInfo)
    }

    @Test fun converterInitPollsAndRecordsMp3() = runTest {
        val c = client { request ->
            when (request.url.encodedPath) {
                "/v1/stream/init" -> respond("""{"job":"job1","source":"converter"}""", HttpStatusCode.OK, jsonHeaders())
                "/v1/stream/poll" -> respond("""{"progress":100,"url":"https://audio.test/c.mp3","source":"converter"}""", HttpStatusCode.OK, jsonHeaders())
                else -> respond("{}", HttpStatusCode.NotFound, headersOf())
            }
        }
        val info = c.resolveStreamInfo("vid12345678")
        assertEquals("https://audio.test/c.mp3", info.url)
        assertEquals("converter", info.source)
        assertEquals("audio/mpeg", info.mimeType)
        assertFalse(info.isOpus)
    }

    @Test fun forceConverterSendsDirectFalseOnlyWhenAsked() = runTest {
        val bodies = mutableListOf<String>()
        val c = client { request ->
            bodies += ((request.body as? TextContent)?.text ?: "")
            respond("""{"url":"https://audio.test/x.mp3","source":"direct","mimeType":"audio/mpeg"}""", HttpStatusCode.OK, jsonHeaders())
        }
        c.resolveStreamInfo("vid12345678")
        c.resolveStreamInfo("vid12345678", forceConverter = true)
        assertEquals(2, bodies.size)
        assertFalse(bodies[0].contains("\"direct\""))
        assertTrue(bodies[1].contains("\"direct\":false"))
    }
}
