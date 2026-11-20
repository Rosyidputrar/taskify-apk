package com.rosyidputrar.taskify.core.media.mytube

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
    /**
     * Boleh dicoba ulang langsung di klien yang sama: HTTP 429/403/5xx
     * (flaky sesaat) dan `LOGIN_REQUIRED` (flaky per permintaan).
     * Selain itu (Unplayable/NoFormat/Timeout/Network/InvalidResponse)
     * percobaan ulang di klien yang sama tidak ada gunanya.
     */
    val isRetryable: Boolean
        get() = (this is Http && (status == 429 || status == 403 || status >= 500)) ||
            this is LoginRequired

    /** Endpoint `player` menjawab `LOGIN_REQUIRED` (flaky per permintaan — di-retry terbatas, lihat `DirectStreamResolver`). */
    data class LoginRequired(val reason: String = "") : StreamFailure

    /**
     * Konteks login pemutaran tidak lengkap (C2-12, ArchiveTune
     * `InvalidPlaybackLoginContextException`): cookie login ada tapi
     * `dataSyncId` kosong — sesi setengah jalan harus DITOLAK, bukan
     * dipakai. Pemutar menampilkan prompt masuk lagi.
     */
    data object InvalidLoginContext : StreamFailure

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

    /**
     * YouTube meminta PoToken/BotGuard (Tahap 3/5, `YoutubeiFailureKind.PO_TOKEN`):
     * bukan sesi habis dan bukan format hilang — pemutaran butuh verifikasi
     * tambahan. Pemulihan penuh (mint token) ada di Tahap 5/7; sampai saat
     * itu pesan ini yang tampil, bukan "sesi berakhir" yang menyesatkan.
     */
    data class BotDetection(val message: String = "") : StreamFailure

    /**
     * Semua klien stream gagal / ter-blacklist sehingga tidak ada klien
     * yang bisa dicoba (Tahap 7, C2-27 — ArchiveTune
     * `BadStreamPlayerResponseException`): [failedClients] berisi kunci
     * `KLIEN@VERSI` yang gagal atau sedang di-blacklist. Pemulihan
     * lapis berikutnya: segarkan sesi (visitorData + PoToken baru) lalu
     * coba sekali lagi.
     */
    data class AllClientsFailed(val failedClients: Set<String> = emptySet()) : StreamFailure
}

/**
 * Pembawa [StreamFailure] di dalam `Result<DirectStream>` — `Result` hanya
 * membawa `Throwable`, jadi kegagalan terketik dibungkus exception ini.
 */
class StreamFailureException(val failure: StreamFailure) :
    Exception(failure.toString())
