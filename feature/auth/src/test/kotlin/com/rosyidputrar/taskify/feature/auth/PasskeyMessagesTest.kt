package com.rosyidputrar.taskify.feature.auth

import com.rosyidputrar.taskify.core.domain.auth.AuthFailure
import com.rosyidputrar.taskify.core.domain.auth.AuthMessages
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PasskeyMessagesTest {
    @Test fun serverErrors() {
        val disabled = passkeyErrorText(AuthFailure(false, 404, null, "Not Found"))
        assertTrue(disabled.startsWith("Passkey belum diaktifkan di server") && "HTTP 404" in disabled, disabled)
        assertTrue(passkeyErrorText(AuthFailure(false, 422, "passkey_disabled", "Passkeys are disabled")).startsWith("Passkey belum diaktifkan"))
        val unknown = passkeyErrorText(AuthFailure(false, 400, "webauthn_credential_not_found", "credential not found"))
        assertTrue(unknown.startsWith("Passkey ini tidak dikenali server"), unknown)
        assertEquals(AuthMessages.OFFLINE_LOGIN, passkeyErrorText(AuthFailure(true, null, null, "timeout")))
        assertEquals(AuthMessages.RATE_LIMITED, passkeyErrorText(AuthFailure(false, 429, null, null)))
        val other = passkeyErrorText(AuthFailure(false, 500, "unexpected_failure", "boom"))
        assertTrue(other.startsWith("Passkey gagal.") && "HTTP 500 · unexpected_failure · boom" in other, other)
    }

    @Test fun domErrorsAreClassified() {
        assertTrue(classifyDom("NotAllowedError", "User cancelled the request")("x") is PasskeyProblem.Cancelled)
        assertTrue(classifyDom("SecurityError", "RP ID cannot be validated")("x") is PasskeyProblem.NotLinked)
        assertTrue(classifyDom("NotAllowedError", "The incoming request cannot be validated: origin mismatch")("x") is PasskeyProblem.NotLinked)
        assertTrue(classifyDom("NotSupportedError", null)("x") is PasskeyProblem.Unsupported)
        assertTrue(classifyDom("UnknownError", "weird")("x") is PasskeyProblem.Other)
    }

    @Test fun userMessages() {
        assertNull(PasskeyProblem.Cancelled("dibatalkan").userMessage(), "dibatalkan = diam")
        assertTrue(PasskeyProblem.NoPasskey("x").userMessage()!!.contains("Pengaturan"))
        val linked = PasskeyProblem.NotLinked("SecurityError: RP ID").userMessage()!!
        assertTrue("assetlinks.json" in linked && "SecurityError: RP ID" in linked, linked)
    }

    @Test fun apkKeyHashOriginIsBase64UrlSha256() {
        val cert = "sertifikat-uji".toByteArray()
        val expected = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(cert))
        val origin = apkKeyHashOrigin(cert)
        assertEquals("android:apk-key-hash:$expected", origin)
        assertTrue(!origin.contains('=') && !origin.contains('+') && !origin.contains('/'), origin)
        assertEquals(43, origin.removePrefix("android:apk-key-hash:").length) // 32 byte → 43 char base64url
    }
}
