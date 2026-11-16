package com.rosyidputrar.taskify.core.domain.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

/**
 * Verifikasi dua langkah WAJIB (sama dengan webapp): database lokal & sync baru dibuka setelah
 * sesi berlevel aal2. Status dihitung dari token tersimpan, jadi tetap bekerja saat offline.
 *
 *   Ok        sesi aal2 → boleh masuk
 *   Challenge punya authenticator, sesi masih aal1 → minta kode 6 digit
 *   Enroll    belum punya authenticator → wajib mendaftarkan dulu
 */
enum class MfaStatus { Ok, Challenge, Enroll }

/** Payload JWT tanpa verifikasi tanda tangan (token berasal dari Supabase Auth sendiri). */
fun decodeJwtPayload(token: String): JsonObject? {
    val part = token.split('.').getOrNull(1) ?: return null
    return runCatching {
        val json = String(Base64.getUrlDecoder().decode(part.trimEnd('=')), Charsets.UTF_8)
        Json.parseToJsonElement(json) as? JsonObject
    }.getOrNull()
}

fun jwtAal(token: String): String? = decodeJwtPayload(token)?.get("aal")?.jsonPrimitive?.contentOrNull

fun mfaStatus(accessToken: String, hasVerifiedFactor: Boolean): MfaStatus = when {
    jwtAal(accessToken) == "aal2" -> MfaStatus.Ok
    hasVerifiedFactor -> MfaStatus.Challenge
    else -> MfaStatus.Enroll
}

/** Kode TOTP: tepat 6 angka (spasi dari salin-tempel dibuang). */
fun normalizeTotpCode(raw: String): String = raw.filterNot { it.isWhitespace() }

fun isTotpCode(code: String): Boolean = code.length == 6 && code.all { it in '0'..'9' }

// ----------------------------------------------------------------------------- ingat perangkat

const val TRUST_DAYS = 30
const val TRUST_MILLIS = TRUST_DAYS * 86_400_000L

/** Status gerbang = status MFA server + konfirmasi ulang perangkat ("reverify"). */
enum class GateStatus { Ok, Challenge, Enroll, Reverify }

data class DeviceTrust(val userId: String, val untilMillis: Long)

fun trustValid(trust: DeviceTrust?, userId: String, now: Long): Boolean =
    trust != null && trust.userId == userId && now < trust.untilMillis

/**
 * Status gerbang, MURNI. [launchUser] = user yang sudah lolos gerbang sejak aplikasi dibuka:
 * sekali lolos tidak dikunci lagi di tengah pemakaian walau masa 30 hari habis saat itu.
 * Konfirmasi ulang hanya kunci sisi aplikasi (server sudah menerima sesi aal2), jadi saat
 * offline tidak memblokir — kode tidak bisa diperiksa tanpa internet.
 */
fun gateStatus(
    mfa: MfaStatus,
    userId: String,
    now: Long,
    online: Boolean,
    trust: DeviceTrust?,
    launchUser: String?,
): GateStatus = when {
    mfa == MfaStatus.Challenge -> GateStatus.Challenge
    mfa == MfaStatus.Enroll -> GateStatus.Enroll
    launchUser == userId -> GateStatus.Ok
    trustValid(trust, userId, now) -> GateStatus.Ok
    online -> GateStatus.Reverify
    else -> GateStatus.Ok
}

/** Pesan error MFA/login yang bisa dimengerti. */
object AuthMessages {
    const val OFFLINE_MFA = "Butuh koneksi internet untuk verifikasi dua langkah."
    const val OFFLINE_LOGIN = "Butuh koneksi internet untuk masuk."
    const val WRONG_CODE = "Kode salah atau sudah kedaluwarsa. Pakai kode terbaru dari aplikasi authenticator."
    const val RATE_LIMITED = "Terlalu banyak percobaan. Tunggu sebentar lalu coba lagi."
    const val WRONG_LOGIN = "Email atau password salah."
    const val MFA_DISABLED = "MFA TOTP dimatikan di server. Aktifkan di Supabase Dashboard → Authentication → Multi-Factor."
    const val INSUFFICIENT_AAL = "Sesi ini belum terverifikasi dua langkah. Masukkan kode authenticator dulu."
}

/** Klasifikasi error auth yang sudah dipetakan dari exception supabase-kt. */
data class AuthFailure(val network: Boolean, val status: Int?, val code: String?, val message: String?)

fun translateMfaError(f: AuthFailure): String = when {
    f.network -> AuthMessages.OFFLINE_MFA
    f.code == "mfa_verification_failed" || f.message.orEmpty().contains(Regex("invalid totp|invalid code", RegexOption.IGNORE_CASE)) -> AuthMessages.WRONG_CODE
    f.status == 429 || f.code == "over_request_rate_limit" -> AuthMessages.RATE_LIMITED
    f.code == "mfa_totp_enroll_not_enabled" || f.code == "mfa_totp_verify_not_enabled" -> AuthMessages.MFA_DISABLED
    f.code == "insufficient_aal" -> AuthMessages.INSUFFICIENT_AAL
    else -> f.message ?: "Terjadi kesalahan."
}

fun translateLoginError(f: AuthFailure): String = when {
    f.network -> AuthMessages.OFFLINE_LOGIN
    f.status == 429 || f.code == "over_request_rate_limit" -> AuthMessages.RATE_LIMITED
    f.code == "invalid_credentials" || f.status == 400 -> AuthMessages.WRONG_LOGIN
    else -> f.message ?: "Gagal masuk."
}
