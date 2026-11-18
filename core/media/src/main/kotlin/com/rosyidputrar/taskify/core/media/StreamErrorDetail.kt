package com.rosyidputrar.taskify.core.media

import com.rosyidputrar.taskify.core.domain.media.buildRawDetail
import com.rosyidputrar.taskify.core.domain.media.causeChainText
import com.rosyidputrar.taskify.core.media.youtube.StreamFailure

/**
 * Detail mentah kegagalan resolve stream + kegagalan muat (search, browse,
 * daftar, lirik) untuk ditampilkan di layar musik (perintah sir: error apa
 * pun tampil teks mentah).
 *
 * Memakai penyusun bersama Agen A ([buildRawDetail]): baris `label: nilai`,
 * tiap nilai dibatasi ~500 karakter. TIDAK PERNAH berisi URL lengkap
 * (query bisa berisi signature), cookie, atau token — URL apa pun di pesan
 * exception disembunyikan dulu ([scrubUrls], pola sama dengan
 * `PlaybackErrorDetail`). Sumber nilainya tidak membawa cookie/token
 * menurut konstruksinya (alasan `playabilityStatus` YouTube, pesan
 * `IOException`, pesan exception): resolver tidak pernah memasukkan
 * nilai sensitif ke sana.
 */

/** Ganti URL apa pun di teks bebas dengan penanda (query-nya bisa berisi signature). */
private val URL_IN_TEXT = Regex("https?://[^\\s\"']+")

private fun scrubUrls(text: String?): String? = text?.let { URL_IN_TEXT.replace(it, "<url disembunyikan>") }

/**
 * Detail mentah kegagalan resolve stream: jenis [StreamFailure] + status
 * HTTP bila ada + alasan/pesan bila ada + rantai penyebab. `null` bila
 * tidak ada failure maupun penyebab (jangan karang detail).
 */
fun streamFailureRawDetail(failure: StreamFailure?, cause: Throwable?): String? {
    if (failure == null && cause == null) return null
    val detail = buildRawDetail(
        "jenis" to when (failure) {
            is StreamFailure.LoginRequired -> "LoginRequired"
            StreamFailure.InvalidLoginContext -> "InvalidLoginContext"
            is StreamFailure.Unplayable -> "Unplayable"
            StreamFailure.NoFormat -> "NoFormat"
            is StreamFailure.Http -> "Http"
            StreamFailure.Timeout -> "Timeout"
            is StreamFailure.Network -> "Network"
            StreamFailure.InvalidResponse -> "InvalidResponse"
            is StreamFailure.BotDetection -> "BotDetection"
            is StreamFailure.AllClientsFailed -> "AllClientsFailed"
            null -> null
        },
        "status" to (failure as? StreamFailure.Http)?.status?.toString(),
        "alasan" to when (failure) {
            is StreamFailure.LoginRequired -> failure.reason
            is StreamFailure.Unplayable -> failure.reason
            is StreamFailure.BotDetection -> failure.message
            is StreamFailure.AllClientsFailed -> failure.failedClients.sorted().joinToString(", ").takeIf { it.isNotEmpty() }
            else -> null
        }?.let(::scrubUrls),
        "pesan" to (failure as? StreamFailure.Network)?.message?.let(::scrubUrls),
        "penyebab" to causeChainText(cause)?.let(::scrubUrls),
    )
    return detail.takeIf { it.isNotBlank() }
}

/**
 * Detail mentah kegagalan muat dari exception apa pun (search, browse,
 * daftar, lirik, resolve non-[StreamFailure]): nama jenis exception +
 * status HTTP bila [YouTubeMusicException] + pesan (URL disembunyikan) +
 * rantai penyebab. `null` bila exception null.
 */
fun musicErrorRawDetail(error: Throwable?): String? {
    if (error == null) return null
    val detail = buildRawDetail(
        "jenis" to (error::class.simpleName?.takeIf { it.isNotBlank() } ?: "Throwable"),
        "status" to (error as? YouTubeMusicException)?.status?.takeIf { it != 0 }?.toString(),
        "pesan" to error.message?.let(::scrubUrls),
        "penyebab" to causeChainText(error.cause)?.let(::scrubUrls),
    )
    return detail.takeIf { it.isNotBlank() }
}
