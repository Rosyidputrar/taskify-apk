package com.rosyidputrar.taskify.core.media.youtube

import com.rosyidputrar.taskify.core.domain.media.MusicQuality

/**
 * Satu format audio dari `streamingData` — hanya yang `mimeType`-nya
 * berawalan `audio/` DAN punya `url` langsung yang sampai ke sini; format
 * `signatureCipher`-only dilewati di pengurai (decipher tidak
 * diimplementasikan, lihat catatan `DirectStreamResolver`).
 */
data class PlayerAudioFormat(
    val itag: Int,
    val mimeType: String,
    val bitrate: Int,
    val url: String,
    /** Kekerasan format ini dalam dB — sama di semua format audio satu lagu. */
    val loudnessDb: Double?,
)

/**
 * Pilih satu format audio-only — port `pickAudioFormat` dari
 * `worker/direct.ts`, aturannya sama persis:
 *
 * 1. hanya yang `mimeType`-nya berawalan `audio/` DAN punya `url` langsung
 *    (diperiksa ulang di sini walau pengurai sudah menyaring).
 * 2. `low`: bitrate terendah dari semua audio (terukur: itag 599, ~32 kbps).
 * 3. `high`: tingkat menengah — median opus bila ada opus, median mp4a bila
 *    hanya ada mp4a, median semua audio bila tidak ada keduanya. Terukur:
 *    opus [251, 249, 600] → 249 (~60–68 kbps).
 * 4. `auto`: utamakan opus/webm bitrate tertinggi; kalau tidak ada opus,
 *    mp4a bitrate tertinggi; kalau tidak ada keduanya, audio apa pun
 *    bitrate tertinggi.
 *
 * `MusicQuality` Taskify hanya punya Auto/High/Low (`Auto` = `highest`/`auto`
 * Worker). `null` bila tidak ada audio ber-URL langsung sama sekali.
 *
 * [preferMp4a] ("Utamakan MP4A", Tahap 7 — pengganti `forceMp3`): bila true,
 * kandidat dibatasi ke format mp4a (bila ada) dan aturan kualitas yang sama
 * diterapkan di dalam kelompok itu (auto = mp4a tertinggi, itag 140; high =
 * median mp4a; low = mp4a terendah). Bila tidak ada mp4a sama sekali, jatuh
 * ke aturan biasa (opus) daripada gagal total — ini preferensi, bukan
 * larangan opus.
 */
fun pickAudioFormat(
    formats: List<PlayerAudioFormat>,
    quality: MusicQuality = MusicQuality.Auto,
    preferMp4a: Boolean = false,
): PlayerAudioFormat? {
    val audio = formats.filter { it.mimeType.startsWith("audio/") && it.url.isNotEmpty() }
    if (audio.isEmpty()) return null
    val byBitrateDesc = compareByDescending<PlayerAudioFormat> { it.bitrate }
    val candidates = if (preferMp4a) {
        audio.filter { "mp4a" in it.mimeType }.takeIf { it.isNotEmpty() } ?: audio
    } else audio

    if (quality == MusicQuality.Low) {
        return candidates.minByOrNull { it.bitrate }
    }
    if (quality == MusicQuality.High) {
        // Median dari kelompok yang diutamakan (opus dulu, seperti `auto`).
        val opus = candidates.filter { "opus" in it.mimeType }.sortedWith(byBitrateDesc)
        val mp4a = candidates.filter { "mp4a" in it.mimeType }.sortedWith(byBitrateDesc)
        val group = when {
            opus.isNotEmpty() -> opus
            mp4a.isNotEmpty() -> mp4a
            else -> candidates.sortedWith(byBitrateDesc)
        }
        return group[group.size / 2]
    }

    val opus = candidates.filter { "opus" in it.mimeType }.sortedWith(byBitrateDesc)
    if (opus.isNotEmpty()) return opus[0]
    val mp4a = candidates.filter { "mp4a" in it.mimeType }.sortedWith(byBitrateDesc)
    if (mp4a.isNotEmpty()) return mp4a[0]
    return candidates.sortedWith(byBitrateDesc)[0]
}
