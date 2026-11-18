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
 * `expire` di URL — bukan nilainya), `jedaResolveKePutarMs` (jeda resolve
 * URL sampai lagu mulai diputar), `expiresInSeconds` (umur URL dari hulu
 * vs ada/tidaknya param `expire` di URL). Empat baris ini yang membedakan
 * hipotesis 403 di HP: URL ANDROID tanpa `n` vs URL WEB_REMIX ber-`n`,
 * dan URL kedaluwarsa vs segar.
 *
 * TIDAK PERNAH berisi URL stream lengkap (ada signature di query),
 * cookie, atau token — dari URL hanya host-nya yang diambil. Bila format
 * atau host tidak diketahui saat error, tertulis "tidak diketahui"
 * (jangan ditebak).
 */
fun PlaybackException.toRawDetail(
    videoId: String,
    stream: StreamSource?,
    streamUrl: String?,
    /** Jeda (ms) resolve URL → lagu mulai diputar; `null` = tidak dicatat. */
    resolveToPlayMs: Long? = null,
): String =
    buildRawDetail(
        "videoId" to videoId,
        "format" to formatText(stream),
        "host" to streamHost(streamUrl),
        "errorCodeName" to errorCodeName,
        "errorCode" to errorCode.toString(),
        // Pesan ExoPlayer bisa mengandung URL (mis. dari data source):
        // URL apa pun disembunyikan sebelum tampil/disalin.
        "message" to scrubUrls(message),
        "cause" to causeChainText(cause)?.let(::scrubUrls),
        "klien" to (stream?.resolvedClient ?: "tidak diketahui"),
        "paramUrl" to streamUrlParamPresence(streamUrl),
        "jedaResolveKePutarMs" to (resolveToPlayMs?.toString() ?: "tidak diketahui"),
        "expiresInSeconds" to (stream?.expiresInSeconds?.toString() ?: "tidak diketahui"),
    )

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

private fun formatText(stream: StreamSource?): String {
    if (stream == null) return "tidak diketahui"
    val itag = stream.itag?.toString() ?: "tidak diketahui"
    val mime = stream.mimeType?.takeIf { it.isNotBlank() }
    return if (mime != null) "itag $itag ($mime)" else "itag $itag"
}

/** Hanya host dari URL stream; query (signature) tidak pernah ikut. */
private fun streamHost(streamUrl: String?): String {
    val host = streamUrl?.takeIf { it.isNotBlank() }
        ?.let { runCatching { Uri.parse(it).host }.getOrNull() }
        ?.takeIf { it.isNotBlank() }
    return host ?: "tidak diketahui"
}
