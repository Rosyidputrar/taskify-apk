package com.rosyidputrar.taskify.core.media

import androidx.media3.common.PlaybackException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.domain.media.RAW_DETAIL_MAX_CHARS
import com.rosyidputrar.taskify.core.domain.media.StreamSource
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pemetaan `PlaybackException` → teks mentah (Agen A): kode error, pesan,
 * rantai cause, dan konteks diagnosis — tanpa URL lengkap, cookie, atau token.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackErrorDetailTest {
    private val videoId = "dQw4w9WgXcQ"
    private val opusStream = StreamSource(StreamSource.SOURCE_DIRECT, "audio/webm; codecs=\"opus\"", 251)
    private val streamUrl =
        "https://rr2---sn-a5mekn7z.googlevideo.com/videoplayback?expire=1793000000&ip=203.0.113.7&sig=RAHASIA&sparams=expire,ip,sig"

    private fun exception(code: Int, message: String?, cause: Throwable? = null) =
        PlaybackException(message, cause, code)

    @Test fun networkErrorCarriesCodeNameCodeAndMessage() {
        val detail = exception(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            "Gagal tersambung ke server.",
        ).toRawDetail(videoId, opusStream, streamUrl)
        assertTrue(detail.contains("errorCodeName: ERROR_CODE_IO_NETWORK_CONNECTION_FAILED"), detail)
        assertTrue(detail.contains("errorCode: ${PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED}"), detail)
        assertTrue(detail.contains("message: Gagal tersambung ke server."), detail)
        assertTrue(detail.contains("videoId: $videoId"), detail)
    }

    @Test fun decoderErrorCarriesItsOwnCodeName() {
        val detail = exception(
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            "Decoder tidak bisa dimulai.",
        ).toRawDetail(videoId, opusStream, streamUrl)
        assertTrue(detail.contains("errorCodeName: ERROR_CODE_DECODER_INIT_FAILED"), detail)
        assertTrue(detail.contains("errorCode: ${PlaybackException.ERROR_CODE_DECODER_INIT_FAILED}"), detail)
    }

    @Test fun formatAndHostFromStreamWithoutFullUrl() {
        val detail = exception(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            "Gagal tersambung.",
        ).toRawDetail(videoId, opusStream, streamUrl)
        assertTrue(detail.contains("format: itag 251 (audio/webm; codecs=\"opus\")"), detail)
        assertTrue(detail.contains("host: rr2---sn-a5mekn7z.googlevideo.com"), detail)
        // URL lengkap TIDAK ikut: tanpa path, query, signature, dan IP.
        assertFalse(detail.contains("videoplayback"), detail)
        assertFalse(detail.contains("sig="), detail)
        assertFalse(detail.contains("RAHASIA"), detail)
        assertFalse(detail.contains("203.0.113.7"), detail)
        assertFalse(detail.contains(streamUrl), detail)
    }

    @Test fun unknownStreamShowsTidakDiketahui() {
        val detail = exception(PlaybackException.ERROR_CODE_UNSPECIFIED, "Tidak diketahui.")
            .toRawDetail(videoId, null, null)
        assertTrue(detail.contains("format: tidak diketahui"), detail)
        assertTrue(detail.contains("host: tidak diketahui"), detail)
    }

    @Test fun unknownItagShowsTidakDiketahui() {
        val noItag = StreamSource(StreamSource.SOURCE_DIRECT, "audio/mp4", null)
        val detail = exception(PlaybackException.ERROR_CODE_UNSPECIFIED, "x").toRawDetail(videoId, noItag, streamUrl)
        assertTrue(detail.contains("format: itag tidak diketahui (audio/mp4)"), detail)
    }

    @Test fun causeChainIsCarriedAndTruncated() {
        val longMessage = "m".repeat(RAW_DETAIL_MAX_CHARS + 200)
        val cause = java.io.IOException(longMessage, IllegalStateException("akar"))
        val detail = exception(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            "Gagal.",
            cause,
        ).toRawDetail(videoId, opusStream, streamUrl)
        assertTrue(detail.contains("cause: IOException:"), detail)
        assertTrue(detail.contains("IllegalStateException: akar"), detail)
        assertFalse(detail.contains("m".repeat(RAW_DETAIL_MAX_CHARS + 200)), detail)
        assertTrue(detail.contains("… (dipotong)"), detail)
    }

    @Test fun urlInsideMessageIsScrubbed() {
        val detail = exception(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            "Gagal membuka $streamUrl",
        ).toRawDetail(videoId, opusStream, streamUrl)
        assertFalse(detail.contains("RAHASIA"), detail)
        assertFalse(detail.contains("videoplayback"), detail)
        assertTrue(detail.contains("<url disembunyikan>"), detail)
    }

    @Test fun detailContainsNoCookieOrToken() {
        val detail = exception(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            "Gagal tersambung.",
            java.io.IOException("timeout"),
        ).toRawDetail(videoId, opusStream, streamUrl)
        listOf("SAPISID", "SID=", "cookie", "Cookie", "token", "Token", "Authorization").forEach { banned ->
            assertFalse(detail.contains(banned), "detail mengandung '$banned':\n$detail")
        }
    }

    @Test fun errorCodeNameFallsBackToCodeNumber() {
        // Kode tak dikenal tetap tampil sebagai angka, bukan ditebak namanya.
        val detail = exception(987654, "Aneh.").toRawDetail(videoId, opusStream, streamUrl)
        assertTrue(detail.contains("errorCode: 987654"), detail)
        assertTrue(detail.contains("errorCodeName: "), detail)
    }

    @Test fun linesAreInStableOrder() {
        val detail = exception(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            "Gagal.",
        ).toRawDetail(videoId, opusStream, streamUrl)
        val labels = detail.lines().map { it.substringBefore(':') }
        assertEquals(listOf("videoId", "format", "host", "errorCodeName", "errorCode", "message"), labels)
    }
}
