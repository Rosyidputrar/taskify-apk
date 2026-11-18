package com.rosyidputrar.taskify.core.media

import android.net.Uri
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import com.rosyidputrar.taskify.core.domain.media.StreamSource
import com.rosyidputrar.taskify.core.domain.media.buildRawDetail
import com.rosyidputrar.taskify.core.domain.media.causeChainText
import com.rosyidputrar.taskify.core.media.youtube.streamUrlParamPresence

/**
 * Detail mentah error pemutar ExoPlayer untuk ditampilkan di layar musik
 * (perintah sir: error apa pun tampil teks mentah).
 *
 * Isi: `videoId`, format yang dipakai (itag + mime dari [StreamSource]
 * yang sedang dipakai), host stream, lalu `errorCodeName`, `errorCode`,
 * `message`, dan rantai `cause` dari [PlaybackException]. Tiap nilai
 * dibatasi ~500 karakter oleh [buildRawDetail].
 *
 * Diagnostik 403 (tanpa nilai rahasia): `klien` (klien InnerTube yang
 * menghasilkan URL), `paramUrl` (keberadaan NAMA parameter `n`/`pot`/`sig`/
 * `expire`/`ip` di URL — bukan nilainya), `jedaResolveKePutarMs` (jeda resolve
 * URL sampai lagu mulai diputar), `expiresInSeconds` (umur URL dari hulu
 * vs ada/tidaknya param `expire` di URL). Empat baris ini yang membedakan
 * hipotesis 403 di HP: URL ANDROID tanpa `n` vs URL WEB_REMIX ber-`n`,
 * dan URL kedaluwarsa vs segar.
 *
 * TIDAK PERNAH berisi URL stream lengkap (ada signature di query),
 * cookie, atau token — dari URL hanya host-nya yang diambil. Bila format
 * atau host tidak diketahui saat error, tertulis "tidak diketahui"
 * (jangan ditebak).
 *
 * Diagnostik percobaan terakhir (Agen C, INVESTIGASI-C): bila stream/URL
 * yang sedang dipakai sudah tidak ada di state saat galat datang (mis.
 * galat kedua untuk item yang sama setelah `PlaybackFailed` membuang
 * URL/stream lagu itu), tiap field di atas jatuh ke [LastStreamAttempt]
 * — diagnostik stream terakhir yang gagal diputar — dan baris
 * `diagnostikDari: percobaan terakhir` menandai asal fallback itu. Tanpa
 * `lastAttempt`, perilaku lama persis (semua "tidak diketahui").
 */
fun PlaybackException.toRawDetail(
    videoId: String,
    stream: StreamSource?,
    streamUrl: String?,
    /** Jeda (ms) resolve URL → lagu mulai diputar; `null` = tidak dicatat. */
    resolveToPlayMs: Long? = null,
    /**
     * Diagnostik stream terakhir yang gagal diputar untuk lagu ini (Agen C);
     * dipakai sebagai fallback tiap field yang tidak diketahui dari
     * [stream]/[streamUrl] saat ini. `null` = tanpa fallback (perilaku lama).
     * Entri untuk lagu lain diabaikan (dicocokkan `videoId`).
     */
    lastAttempt: LastStreamAttempt? = null,
    /**
     * Kunci klien yang sedang di-blacklist untuk lagu ini (`KLIEN@VERSI`,
     * dari `FailedStreamClients`); tampil sebagai baris `klienGagal` bila
     * tidak kosong — ringkasan singkat klien yang sudah gagal, tanpa nilai
     * sensitif. Kosong = baris tidak tampil.
     */
    failedClients: Set<String> = emptySet(),
): String {
    // Fallback hanya bila stream/URL saat ini tidak memberi nilai:
    // stream hidup selalu menang atas percobaan terakhir.
    val attempt = lastAttempt?.takeIf { it.videoId == videoId }
    val usedFallback = stream == null && streamUrl.isNullOrBlank() && attempt != null
    val paramUrl = if (streamUrl.isNullOrBlank()) attempt?.paramUrl else streamUrlParamPresence(streamUrl)
    val jedaMs = resolveToPlayMs ?: attempt?.resolveToPlayMs
    val expires = stream?.expiresInSeconds ?: attempt?.expiresInSeconds
    return buildRawDetail(
        "videoId" to videoId,
        "format" to formatText(stream, attempt),
        "host" to (streamHostOrNull(streamUrl) ?: attempt?.host ?: "tidak diketahui"),
        "errorCodeName" to errorCodeName,
        "errorCode" to errorCode.toString(),
        // Pesan ExoPlayer bisa mengandung URL (mis. dari data source):
        // URL apa pun disembunyikan sebelum tampil/disalin.
        "message" to scrubUrls(message),
        "cause" to causeChainText(cause)?.let(::scrubUrls),
        "klien" to (stream?.resolvedClient ?: attempt?.resolvedClient ?: "tidak diketahui"),
        "paramUrl" to (paramUrl ?: "tidak diketahui"),
        "jedaResolveKePutarMs" to (jedaMs?.toString() ?: "tidak diketahui"),
        "expiresInSeconds" to (expires?.toString() ?: "tidak diketahui"),
        "klienGagal" to failedClients.sorted().joinToString(", ").takeIf { it.isNotEmpty() },
        "diagnostikDari" to if (usedFallback) "percobaan terakhir" else null,
    )
}

/**
 * Diagnostik stream terakhir yang gagal diputar, untuk satu lagu (Agen C,
 * INVESTIGASI-C): dicatat pemutar selagi stream/URL masih ada di state,
 * dipakai sebagai fallback bila galat berikut datang setelah state
 * dibersihkan. Semua nilai sudah bersih — hanya host (tanpa path/query),
 * keberadaan NAMA param URL (tanpa nilai), itag/mime/klien/angka. Tidak
 * pernah berisi URL lengkap, cookie, token, atau IP mentah.
 */
data class LastStreamAttempt(
    val videoId: String,
    val itag: Int? = null,
    val mimeType: String? = null,
    val resolvedClient: String? = null,
    val expiresInSeconds: Int? = null,
    val resolveToPlayMs: Long? = null,
    val host: String? = null,
    val paramUrl: String? = null,
) {
    companion object {
        /**
         * Bangun dari stream + URL yang sedang dipakai saat galat; `null`
         * bila keduanya kosong (tidak ada yang bisa dicatat).
         */
        fun from(
            videoId: String,
            stream: StreamSource?,
            streamUrl: String?,
            resolveToPlayMs: Long? = null,
        ): LastStreamAttempt? {
            if (stream == null && streamUrl.isNullOrBlank()) return null
            return LastStreamAttempt(
                videoId = videoId,
                itag = stream?.itag,
                mimeType = stream?.mimeType,
                resolvedClient = stream?.resolvedClient,
                expiresInSeconds = stream?.expiresInSeconds,
                resolveToPlayMs = resolveToPlayMs,
                host = streamHostOrNull(streamUrl),
                paramUrl = if (streamUrl.isNullOrBlank()) null else streamUrlParamPresence(streamUrl),
            )
        }
    }
}

/**
 * Blok diagnostik percobaan terakhir untuk ditempel ke detail galat resolve
 * (Agen C, INVESTIGASI-C Jalur B): galat resolve (`AllClientsFailed` dkk.)
 * dibangun tanpa `DirectStream`, jadi detailnya tidak punya baris
 * diagnostik stream — blok ini menambahkan format/host/klien/paramUrl/
 * jeda/expiresInSeconds dari percobaan terakhir lagu itu. `null` bila
 * tidak ada percobaan terakhir. Baris `diagnostikDari` menandai blok ini
 * berasal dari percobaan terakhir, bukan dari galat resolve itu sendiri.
 */
fun formatLastAttemptDiagnostics(attempt: LastStreamAttempt?): String? {
    if (attempt == null) return null
    return buildRawDetail(
        "format" to formatText(null, attempt),
        "host" to (attempt.host ?: "tidak diketahui"),
        "klien" to (attempt.resolvedClient ?: "tidak diketahui"),
        "paramUrl" to (attempt.paramUrl ?: "tidak diketahui"),
        "jedaResolveKePutarMs" to (attempt.resolveToPlayMs?.toString() ?: "tidak diketahui"),
        "expiresInSeconds" to (attempt.expiresInSeconds?.toString() ?: "tidak diketahui"),
        "diagnostikDari" to "percobaan terakhir",
    ).takeIf { it.isNotBlank() }
}

/**
 * Kode status HTTP dari rantai `cause`, bila error ini berasal dari respons
 * HTTP yang gagal (mis. `InvalidResponseCodeException` ExoPlayer dengan
 * `responseCode` 403). `null` bila bukan galat HTTP — jangan ditebak dari
 * pesan teks.
 */
fun PlaybackException.httpStatusCode(): Int? {
    var current: Throwable? = cause
    val seen = HashSet<Throwable>()
    while (current != null && seen.add(current)) {
        if (current is HttpDataSource.InvalidResponseCodeException) return current.responseCode
        current = current.cause
    }
    return null
}

/** Ganti URL apa pun di teks bebas dengan penanda (query-nya bisa berisi signature). */
private val URL_IN_TEXT = Regex("https?://[^\\s\"']+")

private fun scrubUrls(text: String?): String? = text?.let { URL_IN_TEXT.replace(it, "<url disembunyikan>") }

private fun formatText(stream: StreamSource?, attempt: LastStreamAttempt? = null): String {
    val itag = stream?.itag ?: attempt?.itag
    val mime = stream?.mimeType ?: attempt?.mimeType
    if (stream == null && attempt == null) return "tidak diketahui"
    val itagText = itag?.toString() ?: "tidak diketahui"
    val mimeText = mime?.takeIf { it.isNotBlank() }
    return if (mimeText != null) "itag $itagText ($mimeText)" else "itag $itagText"
}

/** Hanya host dari URL stream; query (signature) tidak pernah ikut. */
private fun streamHost(streamUrl: String?): String = streamHostOrNull(streamUrl) ?: "tidak diketahui"

/** Host dari URL stream, atau `null` bila tidak diketahui (untuk fallback percobaan terakhir). */
private fun streamHostOrNull(streamUrl: String?): String? {
    return streamUrl?.takeIf { it.isNotBlank() }
        ?.let { runCatching { Uri.parse(it).host }.getOrNull() }
        ?.takeIf { it.isNotBlank() }
}
