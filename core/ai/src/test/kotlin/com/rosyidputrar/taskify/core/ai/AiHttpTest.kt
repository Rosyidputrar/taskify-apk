package com.rosyidputrar.taskify.core.ai

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AiHttpTest {
    @Test fun sharedClientIsOneInstanceUntilClosed() {
        val first = AiHttp.shared()
        assertSame(first, AiHttp.shared())
        AiHttp.closeShared()
        val second = AiHttp.shared()
        assertNotSame(first, second)
        AiHttp.closeShared()
    }

    @Test fun retryAfterHeaderIsParsedAndCapped() = runBlocking {
        val c = OpenAiCompatClient(
            "https://api.example.com/v1", "sk",
            io.ktor.client.HttpClient(MockEngine {
                respond(
                    """{"error":{"message":"slow down","type":"rate_limit"}}""",
                    HttpStatusCode.TooManyRequests,
                    headersOf(HttpHeaders.RetryAfter, "5"),
                )
            }),
        )
        val e = assertFailsWith<AiException> { c.models() }
        assertEquals(AiErrorKind.RateLimit, e.kind)
        assertEquals(5_000L, e.retryAfterMs)
        assertTrue(e.isRetryable)
        assertEquals(5_000L, e.retryDelayMs(1))
        // Tanpa Retry-After: backoff 1s, 2s, 4s.
        val plain = AiException(AiErrorKind.Server, "x")
        assertEquals(1_000L, plain.retryDelayMs(1))
        assertEquals(2_000L, plain.retryDelayMs(2))
        assertEquals(4_000L, plain.retryDelayMs(3))
        assertTrue(plain.isRetryable)
        assertTrue(!AiException(AiErrorKind.Auth, "x").isRetryable)
        assertTrue(!AiException(AiErrorKind.Config, "x").isRetryable)
    }

    @Test fun contextTooLongIsRecognized() = runBlocking {
        val c = OpenAiCompatClient(
            "https://api.example.com/v1", "sk",
            io.ktor.client.HttpClient(MockEngine {
                respond(
                    """{"error":{"message":"This model's maximum context length is 8192 tokens","type":"invalid_request_error","code":"context_length_exceeded"}}""",
                    HttpStatusCode.BadRequest,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }),
        )
        val e = assertFailsWith<AiException> { c.models() }
        assertEquals(AiErrorKind.Config, e.kind)
        assertTrue(e.message!!.contains("Riwayat terlalu panjang"))
        assertTrue(e.message!!.contains("Mulai percakapan baru"))
        assertTrue(AiHttp.isContextTooLong("""{"error":{"code":"context_length_exceeded"}}"""))
        assertTrue(!AiHttp.isContextTooLong("""{"error":{"message":"bad key"}}"""))
    }

    @Test fun retryAfterDateHeaderIsIgnoredNotMisread() = runBlocking {
        val c = OpenAiCompatClient(
            "https://api.example.com/v1", "sk",
            io.ktor.client.HttpClient(MockEngine {
                respond(
                    """{"error":{"message":"slow","type":"rate_limit"}}""",
                    HttpStatusCode.TooManyRequests,
                    headersOf(HttpHeaders.RetryAfter, "Wed, 21 Oct 2026 07:28:00 GMT"),
                )
            }),
        )
        val e = assertFailsWith<AiException> { c.models() }
        // Tanggal HTTP tidak diparse (hanya detik) → backoff bawaan yang dipakai.
        assertEquals(null, e.retryAfterMs)
        assertEquals(1_000L, e.retryDelayMs(1))
    }
}
