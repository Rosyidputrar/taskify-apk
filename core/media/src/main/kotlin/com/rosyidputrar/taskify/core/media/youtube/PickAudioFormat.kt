package com.rosyidputrar.taskify.core.media.youtube

import com.rosyidputrar.taskify.core.domain.media.MusicQuality

/**
 * Satu format audio dari `streamingData` — yang `mimeType`-nya
 * berawalan `audio/` DAN punya `url` langsung, atau punya
 * `signatureCipher` (dikumpulkan dengan `cipher`-nya, tapi tidak dipilih
 * sampai decipher diimplementasikan, lihat `DirectStreamResolver`).
 */
data class PlayerAudioFormat(
    val itag: Int,
    val mimeType: String,
    val bitrate: Int,
    val url: String,
    /** Kekerasan format ini dalam dB — sama di semua format audio satu lagu. */
    val loudnessDb: Double?,
    /** Panjang isi dalam byte (`contentLength` format, jatuh ke param `clen` URL); `null` = tidak diketahui. */
    val contentLength: Long? = null,
    /** Nilai `signatureCipher`/`cipher` mentah bila format hanya tersedia ter-cipher (decipher belum diimplementasikan, lihat `DirectStreamResolver`). */
    val cipher: String? = null,
    /** Perkiraan durasi format dalam ms (`approxDurationMs`); dipakai menyaring preview (Tahap 8). */
    val approxDurationMs: Long? = null,
    /** Sample rate audio (`audioSampleRate`); tiebreak peringkat (Tahap 8). */
    val audioSampleRate: Int? = null,
)

/**
 * Pilih satu format audio-only.
 *
 * Aturan dasar = port `pickAudioFormat` dari `worker/direct.ts` (tetap
 * dipertahankan untuk Auto/High/Low karena sudah dites + terverifikasi
 * Worker):
 * 1. hanya yang `mimeType`-nya berawalan `audio/` DAN punya `url` langsung
 *    DAN `bitrate` > 0 (diperiksa ulang di sini walau pengurai sudah menyaring).
 * 2. `low`: bitrate terendah dari semua audio (terukur: itag 599, ~32 kbps).
 * 3. `high`: tingkat menengah — median opus bila ada opus, median mp4a bila
 *    hanya ada mp4a, median semua audio bila tidak ada keduanya. Terukur:
 *    opus [251, 249, 600] → 249 (~60–68 kbps).
 * 4. `auto`: utamakan opus/webm bitrate tertinggi; kalau tidak ada opus,
 *    mp4a bitrate tertinggi; kalau tidak ada keduanya, audio apa pun
 *    bitrate tertinggi.
 *
 * Tambahan Tahap 8 (C2-23/24/25 — ArchiveTune `YTPlayerUtils` +
 * `index.js`):
 * - `highest`: bitrate tertinggi apa pun codec-nya (target 320 kbps,
 *   `preferHigher`: bitrate desc, codec desc, sampleRate desc) — beda
 *   dari `auto` yang mengutamakan opus walau bitrate-nya lebih rendah.
 * - Tiebreak codec (ArchiveTune `codecRank`): opus (3) > mp4a (2) >
 *   lain (1), dipakai di semua pengurutan bila bitrate sama.
 * - Preview disaring (ArchiveTune `isLikelyPreview`) bila
 *   [expectedDurationMs] diketahui: format yang durasinya 1 ms sampai
 *   min(90 detik, 90% durasi lagu) dan durasi lagu ≥ 90 detik dianggap
 *   cuplikan, bukan lagu penuh. Bila penyaringan mengosongkan daftar,
 *   daftar utuh dipakai lagi (jangan gagalkan lagu hanya karena semua
 *   format tampak pendek).
 * - `preferMp4a` ("Utamakan MP4A"): kandidat dibatasi ke format mp4a
 *   (bila ada) dan aturan kualitas yang sama diterapkan di dalam
 *   kelompok itu. Bila tidak ada mp4a sama sekali, jatuh ke aturan
 *   biasa (opus) daripada gagal total — ini preferensi, bukan larangan opus.
 *
 * [pinnedItag] (Tahap 6, C2-17): itag yang dipakai terakhir untuk lagu ini —
 * bila ada di antara kandidat ber-URL, itulah yang dipakai apa pun kualitas/
 * preferensinya (format yang sama dipertahankan antar pemutaran). Bila tidak
 * ada, aturan biasa berlaku.
 *
 * `null` bila tidak ada audio ber-URL langsung sama sekali.
 */
fun pickAudioFormat(
    formats: List<PlayerAudioFormat>,
    quality: MusicQuality = MusicQuality.Auto,
    preferMp4a: Boolean = false,
    pinnedItag: Int? = null,
    /** Durasi lagu yang diharapkan dalam ms (`videoDetails.lengthSeconds`); `null` = tanpa saring preview. */
    expectedDurationMs: Long? = null,
): PlayerAudioFormat? {
    val audio = formats.filter { it.mimeType.startsWith("audio/") && it.url.isNotEmpty() && it.bitrate > 0 }
    if (audio.isEmpty()) return null
    pinnedItag?.let { pinned -> audio.firstOrNull { it.itag == pinned }?.let { return it } }
    val byBitrateDesc = compareByDescending<PlayerAudioFormat> { it.bitrate }
        .thenByDescending { codecRank(it.mimeType) }
        .thenByDescending { it.audioSampleRate ?: 0 }
    val byBitrateAsc = compareBy<PlayerAudioFormat> { it.bitrate }
        .thenByDescending { codecRank(it.mimeType) }
        .thenByDescending { it.audioSampleRate ?: 0 }
    val candidates = if (preferMp4a) {
        audio.filter { "mp4a" in it.mimeType }.takeIf { it.isNotEmpty() } ?: audio
    } else audio
    // Saring cuplikan (preview) bila durasi lagu diketahui; bila habis,
    // pakai daftar utuh (lihat KDoc).
    val withoutPreviews = expectedDurationMs?.let { expected ->
        candidates.filterNot { isLikelyPreview(it, expected) }.takeIf { it.isNotEmpty() }
    } ?: candidates

    if (quality == MusicQuality.Low) {
        return withoutPreviews.sortedWith(byBitrateAsc).firstOrNull()
    }
    if (quality == MusicQuality.High) {
        // Median dari kelompok yang diutamakan (opus dulu, seperti `auto`).
        val opus = withoutPreviews.filter { "opus" in it.mimeType }.sortedWith(byBitrateDesc)
        val mp4a = withoutPreviews.filter { "mp4a" in it.mimeType }.sortedWith(byBitrateDesc)
        val group = when {
            opus.isNotEmpty() -> opus
            mp4a.isNotEmpty() -> mp4a
            else -> withoutPreviews.sortedWith(byBitrateDesc)
        }
        return group[group.size / 2]
    }
    if (quality == MusicQuality.Highest) {
        return withoutPreviews.sortedWith(byBitrateDesc).firstOrNull()
    }

    val opus = withoutPreviews.filter { "opus" in it.mimeType }.sortedWith(byBitrateDesc)
    if (opus.isNotEmpty()) return opus[0]
    val mp4a = withoutPreviews.filter { "mp4a" in it.mimeType }.sortedWith(byBitrateDesc)
    if (mp4a.isNotEmpty()) return mp4a[0]
    return withoutPreviews.sortedWith(byBitrateDesc).firstOrNull()
}

/** Peringkat codec (ArchiveTune `codecRank`): opus 3, mp4a 2, lain 1, kosong 0. */
internal fun codecRank(mimeType: String): Int {
    val codec = Regex("""codecs="([^"]+)"""").find(mimeType)
        ?.groupValues?.getOrNull(1)?.split(",")?.firstOrNull()?.trim().orEmpty()
    return when {
        codec.isBlank() -> 0
        codec.contains("opus", ignoreCase = true) -> 3
        codec.contains("mp4a", ignoreCase = true) -> 2
        else -> 1
    }
}

/**
 * `true` bila format ini kemungkinan cuplikan/preview, bukan lagu penuh
 * (ArchiveTune `isLikelyPreview`, verbatim): durasi perkiraan 1 ms sampai
 * min(90 detik, 90% durasi lagu), dan hanya bila durasi lagu ≥ 90 detik.
 */
internal fun isLikelyPreview(format: PlayerAudioFormat, expectedDurationMs: Long): Boolean {
    val approx = format.approxDurationMs ?: return false
    if (expectedDurationMs < 90_000L) return false
    return approx in 1L..minOf(90_000L, (expectedDurationMs * 9L) / 10L)
}

/**
 * Kualitas efektif (Tahap 8, C2-25 — ArchiveTune
 * `selectAudioFormatCandidates` + `index.js preferredFormat`): `Auto` di
 * jaringan metered turun ke `High`; tier lain tidak berubah.
 */
internal fun effectiveQuality(quality: MusicQuality, networkMetered: Boolean): MusicQuality =
    if (quality == MusicQuality.Auto && networkMetered) MusicQuality.High else quality

/**
 * Tangga turun kualitas bila resolve gagal (Tahap 8, C2-23 — ArchiveTune
 * `resolvePlaybackData`): tiap tier dicoba penuh (semua klien) sebelum
 * turun ke tier berikut.
 */
internal fun qualityLadder(quality: MusicQuality): List<MusicQuality> =
    when (quality) {
        MusicQuality.Highest -> listOf(MusicQuality.Highest, MusicQuality.High, MusicQuality.Low)
        MusicQuality.High -> listOf(MusicQuality.High, MusicQuality.Low)
        MusicQuality.Auto -> listOf(MusicQuality.Auto, MusicQuality.High, MusicQuality.Low)
        MusicQuality.Low -> listOf(MusicQuality.Low, MusicQuality.High, MusicQuality.Auto)
    }.distinct()
