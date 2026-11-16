package com.rosyidputrar.taskify.core.domain

import com.rosyidputrar.taskify.core.domain.auth.AuthFailure
import com.rosyidputrar.taskify.core.domain.auth.AuthMessages
import com.rosyidputrar.taskify.core.domain.auth.DeviceTrust
import com.rosyidputrar.taskify.core.domain.auth.GateStatus
import com.rosyidputrar.taskify.core.domain.auth.MfaStatus
import com.rosyidputrar.taskify.core.domain.auth.TRUST_MILLIS
import com.rosyidputrar.taskify.core.domain.auth.gateStatus
import com.rosyidputrar.taskify.core.domain.auth.isTotpCode
import com.rosyidputrar.taskify.core.domain.auth.mfaStatus
import com.rosyidputrar.taskify.core.domain.auth.normalizeTotpCode
import com.rosyidputrar.taskify.core.domain.auth.translateLoginError
import com.rosyidputrar.taskify.core.domain.auth.translateMfaError
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MfaGateTest {
    private fun jwt(payload: String): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        return enc.encodeToString("{\"alg\":\"HS256\"}".toByteArray()) + "." + enc.encodeToString(payload.toByteArray()) + ".sig"
    }

    @Test fun mfaStatusFromToken() {
        assertEquals(MfaStatus.Ok, mfaStatus(jwt("""{"aal":"aal2","sub":"u"}"""), hasVerifiedFactor = true))
        assertEquals(MfaStatus.Challenge, mfaStatus(jwt("""{"aal":"aal1"}"""), hasVerifiedFactor = true))
        assertEquals(MfaStatus.Enroll, mfaStatus(jwt("""{"aal":"aal1"}"""), hasVerifiedFactor = false))
        assertEquals(MfaStatus.Enroll, mfaStatus("bukan-jwt", hasVerifiedFactor = false))
    }

    @Test fun tokenWithUnicodeAndUrlSafeAlphabetDecodes() {
        // Nama ber-aksen menghasilkan karakter '-' / '_' di base64url.
        assertEquals(MfaStatus.Ok, mfaStatus(jwt("""{"aal":"aal2","name":"Rosyid ü???>>"}"""), hasVerifiedFactor = false))
    }

    private fun gate(mfa: MfaStatus, trust: DeviceTrust? = null, online: Boolean = true, launch: String? = null) =
        gateStatus(mfa, "u", now = 1_000 * 86_400_000L, online = online, trust = trust, launchUser = launch)

    @Test fun serverStatusAlwaysWins() {
        val trust = DeviceTrust("u", Long.MAX_VALUE)
        assertEquals(GateStatus.Challenge, gate(MfaStatus.Challenge, trust))
        assertEquals(GateStatus.Enroll, gate(MfaStatus.Enroll, trust, launch = "u"))
    }

    @Test fun trustedWithin30DaysEntersDirectly() {
        assertEquals(GateStatus.Ok, gate(MfaStatus.Ok, DeviceTrust("u", 1_000 * 86_400_000L + 1)))
    }

    @Test fun noTrustExpiredOrOtherUserMustReverify() {
        assertEquals(GateStatus.Reverify, gate(MfaStatus.Ok))
        assertEquals(GateStatus.Reverify, gate(MfaStatus.Ok, DeviceTrust("u", 1_000 * 86_400_000L)))
        assertEquals(GateStatus.Reverify, gate(MfaStatus.Ok, DeviceTrust("lain", Long.MAX_VALUE)))
    }

    @Test fun alreadyPassedThisLaunchOrOffline() {
        assertEquals(GateStatus.Ok, gate(MfaStatus.Ok, launch = "u"))
        assertEquals(GateStatus.Ok, gate(MfaStatus.Ok, online = false))
    }

    @Test fun trustSpans30Days() = assertEquals(30L * 86_400_000L, TRUST_MILLIS)

    @Test fun totpCodeNormalisation() {
        assertEquals("123456", normalizeTotpCode(" 123 456 "))
        assertTrue(isTotpCode("123456"))
        assertFalse(isTotpCode("12345"))
        assertFalse(isTotpCode("12345a"))
    }

    @Test fun errorTranslation() {
        assertEquals(AuthMessages.OFFLINE_MFA, translateMfaError(AuthFailure(true, null, null, null)))
        assertEquals(AuthMessages.WRONG_CODE, translateMfaError(AuthFailure(false, 422, "mfa_verification_failed", null)))
        assertEquals(AuthMessages.RATE_LIMITED, translateMfaError(AuthFailure(false, 429, null, null)))
        assertEquals(AuthMessages.WRONG_LOGIN, translateLoginError(AuthFailure(false, 400, "invalid_credentials", null)))
        assertEquals(AuthMessages.OFFLINE_LOGIN, translateLoginError(AuthFailure(true, null, null, null)))
    }
}
