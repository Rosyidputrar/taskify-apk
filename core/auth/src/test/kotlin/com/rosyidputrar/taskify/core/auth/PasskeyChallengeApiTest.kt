package com.rosyidputrar.taskify.core.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Perbaikan 21 Nov 2026: server mengirim `expires_at` sebagai ANGKA epoch detik, yang
 * membuat parser supabase-kt 3.9.0 gagal. Tes ini memakai bentuk respons server yang asli.
 */
class PasskeyChallengeApiTest {
    private val serverJson = """{"challenge_id":"chal-1","options":{"rp":{"name":"Taskify","id":"xyz.supabase.co"},"user":{"id":"user-1","name":"a@b.c","displayName":"a@b.c"},"challenge":"abc123","pubKeyCredParams":[{"type":"public-key","alg":-7}],"attestation":"none"},"expires_at":1795234689}"""

    private fun api(engine: MockEngine) =
        PasskeyChallengeApi(HttpClient(engine), "https://xyz.supabase.co", "test-key")

    @Test fun registrationOptionsParsesNumericExpiresAt() = runBlocking {
        var seenUrl = ""
        var seenAuth: String? = null
        var seenApiKey: String? = null
        val engine = MockEngine { request ->
            seenUrl = request.url.toString()
            seenAuth = request.headers[HttpHeaders.Authorization]
            seenApiKey = request.headers["apikey"]
            respond(
                content = ByteReadChannel(serverJson),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val challenge = api(engine).registrationOptions("tok-akses-rahasia")
        assertEquals("chal-1", challenge.challengeId)
        assertTrue(challenge.optionsJson.contains("\"attestation\":\"none\""), "options diteruskan apa adanya")
        assertEquals("https://xyz.supabase.co/auth/v1/passkeys/registration/options", seenUrl)
        assertEquals("Bearer tok-akses-rahasia", seenAuth)
        assertEquals("test-key", seenApiKey)
        Unit
    }

    @Test fun registrationOptionsSurfacesServerError() = runBlocking {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel("""{"error":"passkeys_disabled","msg":"Passkeys are disabled"}"""),
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val e = runCatching { api(engine).registrationOptions("tok") }.exceptionOrNull()
        assertTrue(e is IllegalStateException, "HTTP 400 harus jadi error yang jelas")
        assertTrue(e.message!!.contains("400"), "pesan membawa status server")
        Unit
    }

    @Test fun registrationOptionsRejectsMalformedResponse() = runBlocking {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel("""{"tidak_ada_challenge":true}"""),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val e = runCatching { api(engine).registrationOptions("tok") }.exceptionOrNull()
        assertTrue(e is IllegalStateException, "respons tanpa challenge_id harus gagal jelas")
        Unit
    }
}
