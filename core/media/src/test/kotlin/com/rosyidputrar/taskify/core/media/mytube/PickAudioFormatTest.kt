package com.rosyidputrar.taskify.core.media.mytube

import com.rosyidputrar.taskify.core.domain.media.MusicQuality
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pemilihan format audio — cermin `pickAudioFormat` + `pickAudioFormat
 * dengan quality` di `worker/direct.test.ts`, memakai daftar enam format
 * yang sama (bitrate terukur Worker 2026-11-16).
 */
class PickAudioFormatTest {

    private fun format(itag: Int, mimeType: String, bitrate: Int, url: String = "https://x/$itag") =
        PlayerAudioFormat(itag, mimeType, bitrate, url, null)

    /** Enam format audio probe Worker: 139/140 mp4a, 249/251/600 opus, 599 mp4a. */
    private val sixFormats = listOf(
        format(139, "audio/mp4; codecs=\"mp4a.40.5\"", 50341),
        format(140, "audio/mp4; codecs=\"mp4a.40.2\"", 131105),
        format(249, "audio/webm; codecs=\"opus\"", 67631),
        format(251, "audio/webm; codecs=\"opus\"", 169643),
        format(599, "audio/mp4; codecs=\"mp4a.40.5\"", 32340),
        format(600, "audio/webm; codecs=\"opus\"", 46007),
    )

    @Test fun `memilih opus bitrate tertinggi bila tersedia`() {
        val picked = pickAudioFormat(sixFormats)
        assertEquals(251, picked?.itag)
    }

    @Test fun `jatuh ke mp4a bitrate tertinggi bila tidak ada opus`() {
        val picked = pickAudioFormat(sixFormats.filter { "mp4a" in it.mimeType })
        // mp4a saja: 140 tertinggi — tapi median high di bawah memakai kelompok ini juga.
        assertEquals(140, picked?.itag)
    }

    @Test fun `melewati format signatureCipher-only tanpa url`() {
        val formats = listOf(
            PlayerAudioFormat(251, "audio/webm; codecs=\"opus\"", 169643, "", null),
            format(140, "audio/mp4; codecs=\"mp4a.40.2\"", 131105),
        )
        assertEquals(140, pickAudioFormat(formats)?.itag)
    }

    @Test fun `mengembalikan null bila tidak ada audio ber-URL langsung sama sekali`() {
        assertNull(pickAudioFormat(listOf(PlayerAudioFormat(251, "audio/webm; codecs=\"opus\"", 169643, "", null))))
        assertNull(pickAudioFormat(listOf(format(18, "video/mp4", 500000))))
        assertNull(pickAudioFormat(emptyList()))
    }

    @Test fun `low memilih bitrate terendah itag 599`() {
        assertEquals(599, pickAudioFormat(sixFormats, MusicQuality.Low)?.itag)
    }

    @Test fun `high memilih opus tengah itag 249`() {
        assertEquals(249, pickAudioFormat(sixFormats, MusicQuality.High)?.itag)
    }

    @Test fun `auto memilih opus bitrate tertinggi itag 251`() {
        assertEquals(251, pickAudioFormat(sixFormats, MusicQuality.Auto)?.itag)
        assertEquals(251, pickAudioFormat(sixFormats)?.itag)
    }

    @Test fun `satu format saja dipilih apa pun quality-nya`() {
        val only = listOf(sixFormats[3])
        for (q in MusicQuality.entries) {
            assertEquals(251, pickAudioFormat(only, q)?.itag)
        }
    }

    @Test fun `high tanpa opus memilih mp4a tengah`() {
        val mp4aOnly = sixFormats.filter { "mp4a" in it.mimeType }
        // mp4a terurut menurun: [140, 139, 599] → median indeks 1 = 139.
        assertEquals(139, pickAudioFormat(mp4aOnly, MusicQuality.High)?.itag)
    }

    @Test fun `low tetap melewati format cipher-only`() {
        val formats = listOf(PlayerAudioFormat(251, "audio/webm; codecs=\"opus\"", 169643, "", null))
        assertNull(pickAudioFormat(formats, MusicQuality.Low))
        assertNull(pickAudioFormat(formats, MusicQuality.High))
    }

    @Test fun `kosong tetap null untuk semua quality`() {
        for (q in MusicQuality.entries) {
            assertNull(pickAudioFormat(emptyList(), q))
        }
    }

    @Test fun `preferMp4a memilih mp4a tertinggi itag 140 walau ada opus`() {
        assertEquals(140, pickAudioFormat(sixFormats, MusicQuality.Auto, preferMp4a = true)?.itag)
    }

    @Test fun `preferMp4a high memilih median mp4a dan low memilih mp4a terendah`() {
        // mp4a terurut menurun: [140, 139, 599] → median indeks 1 = 139, terendah = 599.
        assertEquals(139, pickAudioFormat(sixFormats, MusicQuality.High, preferMp4a = true)?.itag)
        assertEquals(599, pickAudioFormat(sixFormats, MusicQuality.Low, preferMp4a = true)?.itag)
    }

    @Test fun `preferMp4a tanpa mp4a jatuh ke opus daripada gagal`() {
        val opusOnly = sixFormats.filter { "opus" in it.mimeType }
        assertEquals(251, pickAudioFormat(opusOnly, MusicQuality.Auto, preferMp4a = true)?.itag)
    }

    @Test fun `pinnedItag menang atas ranking kualitas`() {
        // Auto biasa memilih 251; pinned 249 harus menang.
        assertEquals(249, pickAudioFormat(sixFormats, MusicQuality.Auto, pinnedItag = 249)?.itag)
        // Low biasa memilih 599; pinned 140 harus menang.
        assertEquals(140, pickAudioFormat(sixFormats, MusicQuality.Low, pinnedItag = 140)?.itag)
        // preferMp4a biasa memilih 140; pinned opus 251 harus menang.
        assertEquals(251, pickAudioFormat(sixFormats, MusicQuality.Auto, preferMp4a = true, pinnedItag = 251)?.itag)
    }

    @Test fun `pinnedItag yang tidak ada jatuh ke ranking biasa`() {
        assertEquals(251, pickAudioFormat(sixFormats, MusicQuality.Auto, pinnedItag = 999)?.itag)
    }

    @Test fun `pinnedItag cipher-only tanpa url tidak dipilih`() {
        val formats = listOf(
            PlayerAudioFormat(251, "audio/webm; codecs=\"opus\"", 169643, "", null, cipher = "s=abc"),
            format(140, "audio/mp4; codecs=\"mp4a.40.2\"", 131105),
        )
        assertEquals(140, pickAudioFormat(formats, pinnedItag = 251)?.itag)
    }

    // ---- Tahap 8 (C2-23/24/25): Highest, preview, metered, tangga ----

    @Test fun `highest memilih bitrate tertinggi apa pun codecnya`() {
        // mp4a 140 (131 kbps) di bawah opus 251 (169 kbps) — Highest = 251.
        assertEquals(251, pickAudioFormat(sixFormats, MusicQuality.Highest)?.itag)
        // Bila mp4a paling tinggi, Highest memilih mp4a (beda dari Auto yang mengutamakan opus).
        val mp4aTertinggi = listOf(
            format(140, "audio/mp4; codecs=\"mp4a.40.2\"", 200000),
            format(251, "audio/webm; codecs=\"opus\"", 169643),
        )
        assertEquals(140, pickAudioFormat(mp4aTertinggi, MusicQuality.Highest)?.itag)
        // Auto pada daftar yang sama tetap mengutamakan opus.
        assertEquals(251, pickAudioFormat(mp4aTertinggi, MusicQuality.Auto)?.itag)
    }

    @Test fun `bitrate sama tiebreak codecRank lalu sampleRate`() {
        val tie = listOf(
            format(140, "audio/mp4; codecs=\"mp4a.40.2\"", 100000).copy(audioSampleRate = 44100),
            format(251, "audio/webm; codecs=\"opus\"", 100000).copy(audioSampleRate = 48000),
        )
        // Highest: bitrate sama → opus (rank 3) menang atas mp4a (rank 2).
        assertEquals(251, pickAudioFormat(tie, MusicQuality.Highest)?.itag)
        val sameCodec = listOf(
            format(140, "audio/mp4; codecs=\"mp4a.40.2\"", 100000).copy(audioSampleRate = 44100),
            format(139, "audio/mp4; codecs=\"mp4a.40.5\"", 100000).copy(audioSampleRate = 22050),
        )
        // Codec sama → sampleRate lebih tinggi menang.
        assertEquals(140, pickAudioFormat(sameCodec, MusicQuality.Highest)?.itag)
    }

    @Test fun `preview tersaring bila durasi lagu diketahui`() {
        val formats = listOf(
            // Preview 30 detik dari lagu 200 detik → tersaring.
            format(251, "audio/webm; codecs=\"opus\"", 169643).copy(approxDurationMs = 30000),
            format(140, "audio/mp4; codecs=\"mp4a.40.2\"", 131105).copy(approxDurationMs = 200000),
        )
        assertEquals(140, pickAudioFormat(formats, MusicQuality.Auto, expectedDurationMs = 200_000)?.itag)
        // Tanpa durasi lagu, preview TIDAK disaring.
        assertEquals(251, pickAudioFormat(formats, MusicQuality.Auto)?.itag)
        // Lagu < 90 detik: preview tidak pernah disaring.
        assertEquals(251, pickAudioFormat(formats, MusicQuality.Auto, expectedDurationMs = 60_000)?.itag)
        // Semua tersaring → daftar utuh dipakai lagi, lagu tidak gagal.
        val allPreview = listOf(
            format(251, "audio/webm; codecs=\"opus\"", 169643).copy(approxDurationMs = 30000),
        )
        assertEquals(251, pickAudioFormat(allPreview, MusicQuality.Auto, expectedDurationMs = 200_000)?.itag)
    }

    @Test fun `effectiveQuality metered menurunkan Auto ke High`() {
        assertEquals(MusicQuality.High, effectiveQuality(MusicQuality.Auto, networkMetered = true))
        assertEquals(MusicQuality.Auto, effectiveQuality(MusicQuality.Auto, networkMetered = false))
        assertEquals(MusicQuality.Highest, effectiveQuality(MusicQuality.Highest, networkMetered = true))
        assertEquals(MusicQuality.Low, effectiveQuality(MusicQuality.Low, networkMetered = true))
    }

    @Test fun `qualityLadder mengikuti tangga ArchiveTune`() {
        assertEquals(
            listOf(MusicQuality.Highest, MusicQuality.High, MusicQuality.Low),
            qualityLadder(MusicQuality.Highest),
        )
        assertEquals(listOf(MusicQuality.High, MusicQuality.Low), qualityLadder(MusicQuality.High))
        assertEquals(
            listOf(MusicQuality.Auto, MusicQuality.High, MusicQuality.Low),
            qualityLadder(MusicQuality.Auto),
        )
        assertEquals(
            listOf(MusicQuality.Low, MusicQuality.High, MusicQuality.Auto),
            qualityLadder(MusicQuality.Low),
        )
    }
}
