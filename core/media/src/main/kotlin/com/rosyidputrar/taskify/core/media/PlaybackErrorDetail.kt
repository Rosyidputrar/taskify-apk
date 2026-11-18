package com.rosyidputrar.taskify.core.media

import android.net.Uri
import androidx.media3.common.PlaybackException
import com.rosyidputrar.taskify.core.domain.media.StreamSource
import com.rosyidputrar.taskify.core.domain.media.buildRawDetail
import com.rosyidputrar.taskify.core.domain.media.causeChainText

/**
 * Detail mentah error pemutar ExoPlayer untuk ditampilkan di layar musik
 * (perintah sir: error apa pun tampil teks mentah).
 *
 * Isi: `videoId`, format yang dipakai (itag + mime dari [StreamSource]
 * yang sedang dipakai), host stream, lalu `errorCodeName`, `errorCode`,
 * `message`, dan rantai `cause` dari [PlaybackException]. Tiap nilai
 * dibatasi ~500 karakter oleh [buildRawDetail].
 *
 * TIDAK PERNAH berisi URL stream lengkap (ada signature di query),
 * cookie, atau token — dari URL hanya host-nya yang diambil. Bila format
 * atau host tidak diketahui saat error, tertulis "tidak diketahui"
 * (jangan ditebak).
 */
fun PlaybackException.toRawDetail(videoId: String, stream: StreamSource?, streamUrl: String?): String =
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
    )

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
