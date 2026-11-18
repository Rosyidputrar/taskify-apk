/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.rosyidputrar.taskify.core.media.youtube

import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.domain.media.MusicQuality
import com.rosyidputrar.taskify.core.media.MediaRequestHeaders
import com.rosyidputrar.taskify.core.media.ResolvedStreamRegistry
import com.rosyidputrar.taskify.core.youtubei.YoutubeiAudioQuality
import com.rosyidputrar.taskify.core.youtubei.YoutubeiException
import com.rosyidputrar.taskify.core.youtubei.YoutubeiFailureKind
import com.rosyidputrar.taskify.core.youtubei.YoutubeiResolvedStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tahap 3: pemetaan murni fasad youtubei.js → DirectStream/StreamFailure
 * (tanpa QuickJS/jaringan) + header resolver lebih utama di DataSpec.
 * Runner Robolectric diperlukan untuk `Uri.parse` (tes tanpa runner
 * mengembalikan null karena `isReturnDefaultValues`).
 */
@RunWith(AndroidJUnit4::class)
class YoutubeiStreamResolverTest {

    @After
    fun tearDown() {
        ResolvedStreamRegistry.clear()
    }

    private fun resolved(
        expiresAtMs: Long = 1_000_000L,
        contentLength: Long = 5_000_000L,
    ) = YoutubeiResolvedStream(
        url = "https://rr1---sn-abc.googlevideo.com/videoplayback?expire=1&n=abc&sig=xyz&pot=tok",
        requestHeaders = mapOf(
            "User-Agent" to "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip",
            "Origin" to "https://www.youtube.com",
        ),
        formatId = 251,
        mimeType = "audio/webm; codecs=\"opus\"",
        codecs = "opus",
        bitrate = 160_000,
        sampleRate = 48_000,
        contentLength = contentLength,
        expiresAtMs = expiresAtMs,
        runtimeVersion = "18.0.0",
        title = "Lagu Tes",
        durationSeconds = 200,
        thumbnailUrl = null,
        loudnessDb = -7.5,
        perceptualLoudnessDb = -8.0,
        playbackTrackingUrl = null,
    )

    @Test
    fun `toDirectStream memetakan semua field`() {
        val direct = resolved(expiresAtMs = 1_000_000L).toDirectStream("WEB_CREATOR", nowMs = 400_000L)
        assertEquals("https://rr1---sn-abc.googlevideo.com/videoplayback?expire=1&n=abc&sig=xyz&pot=tok", direct.url)
        assertEquals(251, direct.itag)
        assertEquals(160_000, direct.bitrate)
        assertEquals(600, direct.expiresInSeconds)
        assertEquals(5_000_000L, direct.contentLength)
        assertEquals("WEB_CREATOR", direct.resolvedClient)
        assertEquals(-7.5, direct.loudnessDb)
        assertEquals(-8.0, direct.perceptualLoudnessDb)
    }

    @Test
    fun `expiresInSeconds null bila sudah kedaluwarsa dan contentLength 0 jadi null`() {
        val direct = resolved(expiresAtMs = 100L, contentLength = 0L).toDirectStream("MWEB", nowMs = 1_000_000L)
        assertNull(direct.expiresInSeconds)
        assertNull(direct.contentLength)
    }

    @Test
    fun `toDirectStream memetakan playbackTrackingUrl`() {
        val tracking = "https://www.youtube.com/api/stats/watchtime?ns=yt&ver=2"
        val direct = resolved().copy(playbackTrackingUrl = tracking).toDirectStream("WEB_CREATOR", nowMs = 400_000L)
        assertEquals(tracking, direct.playbackTrackingUrl)
        assertNull(resolved().toDirectStream("WEB_CREATOR", nowMs = 400_000L).playbackTrackingUrl)
    }

    @Test
    fun `kualitas dipetakan ke tier youtubei`() {
        assertEquals(YoutubeiAudioQuality.AUTO, MusicQuality.Auto.toYoutubeiQuality())
        assertEquals(YoutubeiAudioQuality.HIGH, MusicQuality.High.toYoutubeiQuality())
        assertEquals(YoutubeiAudioQuality.LOW, MusicQuality.Low.toYoutubeiQuality())
    }

    @Test
    fun `YoutubeiException dipetakan ke StreamFailure`() {
        assertEquals(
            StreamFailure.BotDetection("butuh potoken"),
            YoutubeiException(YoutubeiFailureKind.PO_TOKEN, "butuh potoken").toStreamFailure(),
        )
        assertEquals(
            StreamFailure.LoginRequired("login"),
            YoutubeiException(YoutubeiFailureKind.LOGIN_REQUIRED, "login").toStreamFailure(),
        )
        assertEquals(
            StreamFailure.NoFormat,
            YoutubeiException(YoutubeiFailureKind.NO_FORMAT, "x").toStreamFailure(),
        )
        assertEquals(
            StreamFailure.NoFormat,
            YoutubeiException(YoutubeiFailureKind.DECIPHER, "x").toStreamFailure(),
        )
        assertEquals(
            StreamFailure.Unplayable("tidak bisa"),
            YoutubeiException(YoutubeiFailureKind.UNAVAILABLE, "tidak bisa").toStreamFailure(),
        )
        assertEquals(
            StreamFailure.Http(429),
            YoutubeiException(YoutubeiFailureKind.HTTP, "x", httpStatus = 429).toStreamFailure(),
        )
        assertEquals(
            StreamFailure.Timeout,
            YoutubeiException(YoutubeiFailureKind.TIMEOUT, "x").toStreamFailure(),
        )
        assertEquals(
            StreamFailure.InvalidResponse,
            YoutubeiException(YoutubeiFailureKind.INVALID_RESPONSE, "x").toStreamFailure(),
        )
    }

    @Test
    fun `header resolver lebih utama daripada profil c di DataSpec`() {
        val r = resolved()
        ResolvedStreamRegistry.put(r.url, r.contentLength, r.requestHeaders)
        val dataSpec = MediaRequestHeaders.resolvePlaybackDataSpec(
            DataSpec(
                android.net.Uri.parse(r.url),
                0L,
                C.LENGTH_UNSET.toLong(),
            ),
        )
        // Header resolver (UA aplikasi ANDROID + Origin youtube) dipakai,
        // bukan header profil `c` generik.
        assertEquals(
            "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip",
            dataSpec.httpRequestHeaders["User-Agent"],
        )
        assertEquals("https://www.youtube.com", dataSpec.httpRequestHeaders["Origin"])
        // length = contentLength dari posisi 0 (Range chunk pertama).
        assertEquals(5_000_000L, dataSpec.length)
        assertTrue(ResolvedStreamRegistry.headers(r.url)?.containsKey("Origin") == true)
    }
}
