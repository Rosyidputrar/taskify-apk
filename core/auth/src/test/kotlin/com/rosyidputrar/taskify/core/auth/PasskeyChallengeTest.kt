package com.rosyidputrar.taskify.core.auth

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.data.DevicePrefs
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.CodeVerifierCache
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

@RunWith(AndroidJUnit4::class)
class PasskeyChallengeTest {
    private val registrationPayloadUnixExpires = """
        {
          "challenge_id": "chg-reg-123",
          "options": {
            "rp": {"name": "Taskify", "id": "taskify.example.com"},
            "user": {"id": "dXNlci0x", "name": "user@example.com", "displayName": "User"},
            "challenge": "Y2hhbGxlbmdl",
            "pubKeyCredParams": [{"type": "public-key", "alg": -7}],
            "attestation": "none"
          },
          "expires_at": 1795234689
        }
    """.trimIndent()

    private val authPayloadUnixExpires = """
        {
          "challenge_id": "chg-auth-456",
          "options": {
            "challenge": "Y2hhbGxlbmdl",
            "rpId": "taskify.example.com",
            "userVerification": "preferred"
          },
          "expires_at": 1795234689
        }
    """.trimIndent()

    private class MemorySessionManager(private var saved: UserSession? = null) : SessionManager {
        override suspend fun saveSession(session: UserSession) { saved = session }
        override suspend fun loadSession(): UserSession = saved ?: throw NoSessionFoundException()
        override suspend fun deleteSession() { saved = null }
    }

    private class MemoryCodeVerifierCache : CodeVerifierCache {
        private var verifier: String? = null
        override suspend fun saveCodeVerifier(codeVerifier: String) { verifier = codeVerifier }
        override suspend fun loadCodeVerifier(): String? = verifier
        override suspend fun deleteCodeVerifier() { verifier = null }
    }

    private fun testRepo(engine: MockEngine, session: UserSession? = null): Pair<AuthRepository, CoroutineScope> {
        val supabase = createSupabaseClient(supabaseUrl = "https://demo.supabase.co", supabaseKey = "sb_publishable_test") {
            httpEngine = engine
            install(Auth) {
                alwaysAutoRefresh = false
                autoLoadFromStorage = false
                sessionManager = MemorySessionManager(session)
                codeVerifierCache = MemoryCodeVerifierCache()
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repo = AuthRepository(
            supabase = supabase,
            trustStore = MfaTrustStore(DevicePrefs(ctx)),
            network = NetworkMonitor(ctx),
            scope = scope,
        )
        if (session != null) {
            runBlocking { supabase.auth.importSession(session, autoRefresh = false) }
        }
        return repo to scope
    }

    private fun activeSession() = UserSession(
        accessToken = "tok-akses-123",
        refreshToken = "tok-refresh-123",
        expiresIn = 3600,
        tokenType = "bearer",
        user = null,
        expiresAt = Clock.System.now() + 1.hours,
    )

    @Test fun parsePasskeyChallengeAcceptsUnixIntegerIsoStringAndMissingExpiresAt() {
        val fromUnix = parsePasskeyChallengeResponse(registrationPayloadUnixExpires)
        assertEquals("chg-reg-123", fromUnix.challengeId)
        assertTrue(fromUnix.optionsJson.contains(""""attestation":"none""""))

        val fromIso = parsePasskeyChallengeResponse(
            """{"challenge_id":"c-iso","options":{"challenge":"abc"},"expires_at":"2026-11-21T10:00:00Z"}""",
        )
        assertEquals("c-iso", fromIso.challengeId)
        assertEquals("""{"challenge":"abc"}""", fromIso.optionsJson)

        val withoutExpiry = parsePasskeyChallengeResponse(
            """{"challenge_id":"c-noexp","options":{"challenge":"xyz"}}""",
        )
        assertEquals("c-noexp", withoutExpiry.challengeId)
    }

    @Test fun parsePasskeyChallengeRejectsMalformedPayloads() {
        assertFailsWith<IllegalStateException> { parsePasskeyChallengeResponse("""{"options":{}}""") }
        assertFailsWith<IllegalStateException> { parsePasskeyChallengeResponse("""{"challenge_id":"   ","options":{}}""") }
        assertFailsWith<IllegalStateException> { parsePasskeyChallengeResponse("""{"challenge_id":123,"options":{}}""") }
        assertFailsWith<IllegalStateException> { parsePasskeyChallengeResponse("""{"challenge_id":"c1"}""") }
        assertFailsWith<IllegalStateException> { parsePasskeyChallengeResponse("""[]""") }
    }

    @Test fun passkeyRegistrationChallengeHandlesUnixExpiresAtAndAuthHeader() = runBlocking {
        var seenMethod: HttpMethod? = null
        var seenUrl: String? = null
        var seenAuth: String? = null
        val engine = MockEngine { request ->
            seenMethod = request.method
            seenUrl = request.url.toString()
            seenAuth = request.headers[HttpHeaders.Authorization]
            respond(
                content = registrationPayloadUnixExpires,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val (repo, scope) = testRepo(engine, activeSession())
        try {
            val challenge = repo.passkeyRegistrationChallenge()
            assertEquals(HttpMethod.Post, seenMethod)
            assertEquals("https://demo.supabase.co/auth/v1/passkeys/registration/options", seenUrl)
            assertEquals("Bearer tok-akses-123", seenAuth)
            assertEquals("chg-reg-123", challenge.challengeId)
            assertTrue(challenge.optionsJson.contains(""""attestation":"none""""))
        } finally {
            scope.cancel()
        }
    }

    @Test fun passkeyRegistrationChallengeMapsHttpErrorToAuthError() = runBlocking {
        val engine = MockEngine {
            respond(
                content = """{"code":404,"error_code":"passkey_disabled","msg":"Passkey authentication is not enabled"}""",
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val (repo, scope) = testRepo(engine, activeSession())
        try {
            val err = assertFailsWith<AuthError> { repo.passkeyRegistrationChallenge() }
            assertEquals(404, err.failure.status)
            assertEquals("passkey_disabled", err.failure.code)
        } finally {
            scope.cancel()
        }
    }

    @Test fun passkeyRegistrationChallengeRequiresSession() = runBlocking {
        val engine = MockEngine { error("tidak boleh memanggil jaringan tanpa sesi") }
        val (repo, scope) = testRepo(engine, session = null)
        try {
            val err = assertFailsWith<AuthError> { repo.passkeyRegistrationChallenge() }
            assertEquals(401, err.failure.status)
            assertEquals("no_session", err.failure.code)
        } finally {
            scope.cancel()
        }
    }

    @Test fun passkeySignInChallengeHandlesUnixExpiresAt() = runBlocking {
        val engine = MockEngine {
            respond(
                content = authPayloadUnixExpires,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val (repo, scope) = testRepo(engine, session = null)
        try {
            val challenge = repo.passkeySignInChallenge()
            assertEquals("chg-auth-456", challenge.challengeId)
            assertTrue(challenge.optionsJson.contains(""""rpId":"taskify.example.com""""))
        } finally {
            scope.cancel()
        }
    }
}
