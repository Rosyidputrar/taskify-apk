package com.rosyidputrar.taskify.core.media.youtube

/**
 * Kegagalan resolve stream direct, terketik — pemanggil (Tahap 6-7) jatuh
 * ke konverter untuk SEMUA kasus ini, tapi alasan yang terketik berguna
 * untuk log/telemetri dan untuk membedakan "coba lagi" dari "percuma".
 *
 * Lima pertama adalah yang diminta PLAN D.3; dua terakhir ditambahkan
 * karena hasil nyata yang tidak bisa diungkapkan lima itu: jaringan mati
 * dan respons bukan JSON. Tanpa keduanya, resolver harus melempar exception
 * mentah dan kontrak terketiknya bocor.
 */
sealed interface StreamFailure {
    /** Endpoint `player` menjawab `LOGIN_REQUIRED` (flaky per permintaan; jangan di-retry). */
    data class LoginRequired(val reason: String = "") : StreamFailure

    /** `playabilityStatus` bukan OK dan bukan `LOGIN_REQUIRED`. */
    data class Unplayable(val reason: String) : StreamFailure

    /** Respons OK tapi tidak ada format audio ber-URL langsung (semua cipher-only atau tidak ada audio). */
    data object NoFormat : StreamFailure

    /** HTTP non-2xx dari endpoint `player`. */
    data class Http(val status: Int) : StreamFailure

    /** Satu percobaan melewati batas [DirectStreamResolver.DIRECT_TIMEOUT_MS]. */
    data object Timeout : StreamFailure

    /** Jaringan mati / I/O lain (bukan timeout, bukan HTTP). */
    data class Network(val message: String) : StreamFailure

    /** Respons `player` bukan JSON atau bukan objek. */
    data object InvalidResponse : StreamFailure
}

/**
 * Pembawa [StreamFailure] di dalam `Result<DirectStream>` — `Result` hanya
 * membawa `Throwable`, jadi kegagalan terketik dibungkus exception ini.
 */
class StreamFailureException(val failure: StreamFailure) :
    Exception(failure.toString())
