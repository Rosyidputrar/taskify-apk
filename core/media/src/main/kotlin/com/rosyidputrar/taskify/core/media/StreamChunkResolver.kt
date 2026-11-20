/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

// Diadaptasi dari ArchiveTune `app/.../playback/StreamChunkResolver.kt`
// (v15.1.0, c7f0e24) — hanya paket yang diubah ke Taskify.
// Lihat licenses/ARCHIVETUNE.md.

package com.rosyidputrar.taskify.core.media

import android.net.Uri
import com.rosyidputrar.taskify.core.media.mytube.urlQueryParam

/**
 * Panjang chunk berikutnya untuk [ChunkedDataSource]: yang terkecil dari
 * [chunkLength], sisa [requestedLength], dan sisa [knownContentLength].
 * `null` = baca terbuka (tanpa batas) — dipakai bila panjang tidak
 * diketahui dan mime tidak butuh pembacaan terbuka.
 */
internal fun resolveStreamChunkLength(
    requestedLength: Long,
    position: Long,
    knownContentLength: Long?,
    chunkLength: Long,
    mimeType: String? = null,
): Long? {
    if (chunkLength <= 0L || position < 0L) return null
    if (knownContentLength != null && position >= knownContentLength) return null
    if (requestedLength <= 0L && mimeType.requiresOpenEndedRead()) return null

    val remainingLength = knownContentLength?.minus(position)?.takeIf { it > 0L }
    val resolvedLength =
        listOfNotNull(
            chunkLength,
            requestedLength.takeIf { it > 0L },
            remainingLength,
        ).minOrNull()

    return resolvedLength?.takeIf { it > 0L }
}

private fun String?.requiresOpenEndedRead(): Boolean {
    val normalizedMimeType =
        this
            ?.substringBefore(";")
            ?.trim()
            ?.lowercase()
            .orEmpty()
    return normalizedMimeType == "audio/mp4" ||
        normalizedMimeType == "video/mp4" ||
        normalizedMimeType == "application/mp4" ||
        normalizedMimeType == "audio/x-m4a" ||
        normalizedMimeType == "audio/webm" ||
        normalizedMimeType == "video/webm" ||
        normalizedMimeType == "application/webm" ||
        normalizedMimeType == "audio/ogg" ||
        normalizedMimeType == "application/ogg"
}

/**
 * Panjang chunk sadar-bitrate untuk satu URL googlevideo (INVESTIGASI-A §7,
 * F3): chunk menjangkau [TARGET_CHUNK_MEDIA_SECONDS] detik media, dihitung
 * dari bitrate URL itu sendiri (`clen`/`dur` — keduanya ada di semua URL
 * googlevideo yang teramati, termasuk di `sparams`).
 *
 * Kenapa bukan 8 MB buta: server menolak request yang ujung Range-nya
 * melewati ~60 detik media dari posisi putar (INVESTIGASI-B §0: Range
 * `0-983279` → 206, `0-999999` → 403; jendela bergeser dengan waktu). 8 MB
 * pada itag 251 ≈ 512 detik media → 403 sejak request pertama. Dengan buffer
 * default ExoPlayer 50 detik, chunk berikutnya diminta dari posisi-putar +
 * ~50 detik, jadi span chunk harus ≤ ~10 detik — 6 detik memberi margin
 * ~4 detik di steady state (dan ~6 detik di fase isi awal) terhadap jendela
 * 60 detik, termasuk variasi VBR.
 *
 * Dijepit ke [MIN_BITRATE_CHUNK_BYTES, MAX_BITRATE_CHUNK_BYTES]; bila
 * `clen`/`dur` hilang, pakai bitrate nominal itag dari param `itag` (C S-13),
 * dan bila itag pun tak dikenal pakai [FALLBACK_BITRATE_CHUNK_BYTES] (32 KB ≈
 * 5,3 detik pada bitrate audio terendah ~48 kbps). Murni (tanpa jaringan)
 * supaya bisa diuji di JVM.
 */
internal const val TARGET_CHUNK_MEDIA_SECONDS = 6.0
internal const val MIN_BITRATE_CHUNK_BYTES = 32L * 1024L
internal const val MAX_BITRATE_CHUNK_BYTES = 1024L * 1024L
internal const val FALLBACK_BITRATE_CHUNK_BYTES = 32L * 1024L

/**
 * Bitrate nominal (bps) per itag audio, untuk fallback chunk bila `clen`/`dur`
 * tidak tersedia. Angka dari format YouTube yang umum (opus/AAC); dipakai
 * HANYA untuk ukuran chunk ~6 detik, bukan untuk memilih format.
 */
private val ITAG_BITRATES_BPS = mapOf(
    139 to 48_000L, // AAC 48 kbps
    140 to 128_000L, // AAC 128 kbps
    141 to 256_000L, // AAC 256 kbps
    171 to 128_000L, // Vorbis 128 kbps
    172 to 256_000L, // Vorbis 256 kbps
    249 to 50_000L, // Opus ~50 kbps
    250 to 70_000L, // Opus ~70 kbps
    251 to 160_000L, // Opus ~160 kbps
    256 to 192_000L, // AAC 192 kbps
    258 to 384_000L, // AAC 384 kbps
    599 to 32_000L, // AAC 32 kbps
    600 to 48_000L, // Opus ~48 kbps
)

internal fun resolveBitrateAwareChunkLength(uri: Uri): Long {
    val url = uri.toString()
    val clen = urlQueryParam(url, "clen")?.toLongOrNull()?.takeIf { it > 0 }
    val durSeconds = urlQueryParam(url, "dur")?.toDoubleOrNull()?.takeIf { it > 0.0 }
    val computed = if (clen != null && durSeconds != null) {
        (clen / durSeconds * TARGET_CHUNK_MEDIA_SECONDS).toLong()
    } else {
        // C S-13: fallback lama 32 KB untuk SEMUA format membuang-buang
        // request pada itag bitrate tinggi (140/251 butuh ~120 KB per 6
        // detik — 32 KB = chunk ~1,5 detik, 4x lebih banyak request + 4x
        // peluang 403 di tengah lagu). Fallback baru memakai bitrate nominal
        // itag dari param `itag` URL (selalu ada di URL googlevideo),
        // dijepit seperti jalur utama; itag tak dikenal tetap 32 KB.
        val itag = urlQueryParam(url, "itag")?.toIntOrNull()
        val bitrate = itag?.let(ITAG_BITRATES_BPS::get)
        if (bitrate != null) bitrate * TARGET_CHUNK_MEDIA_SECONDS.toLong() / 8
        else FALLBACK_BITRATE_CHUNK_BYTES
    }
    return computed.coerceIn(MIN_BITRATE_CHUNK_BYTES, MAX_BITRATE_CHUNK_BYTES)
}
