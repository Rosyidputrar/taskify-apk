package com.rosyidputrar.taskify.core.media

import com.rosyidputrar.taskify.core.media.mytube.StreamFailure
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pemetaan detail mentah resolve/muat (Agen B): jenis + status selalu ada,
 * URL lengkap / cookie / token tidak pernah ikut.
 */
class StreamErrorDetailTest {

    @Test fun httpCarriesTypeAndStatus() {
        val detail = streamFailureRawDetail(StreamFailure.Http(429), null)
        assertEquals("jenis: Http\nstatus: 429", detail)
    }

    @Test fun loginRequiredCarriesReason() {
        val detail = streamFailureRawDetail(StreamFailure.LoginRequired("Video ini butuh login"), null)
        assertEquals("jenis: LoginRequired\nalasan: Video ini butuh login", detail)
    }

    @Test fun unplayableCarriesReason() {
        val detail = streamFailureRawDetail(StreamFailure.Unplayable("Video tidak tersedia"), null)
        assertTrue(detail!!.contains("jenis: Unplayable"))
        assertTrue(detail.contains("alasan: Video tidak tersedia"))
    }

    @Test fun noFormatTimeoutInvalidResponseCarryTypeOnly() {
        assertEquals("jenis: NoFormat", streamFailureRawDetail(StreamFailure.NoFormat, null))
        assertEquals("jenis: Timeout", streamFailureRawDetail(StreamFailure.Timeout, null))
        assertEquals("jenis: InvalidResponse", streamFailureRawDetail(StreamFailure.InvalidResponse, null))
    }

    @Test fun networkMessageHasNoFullUrl() {
        val url = "https://music.youtube.com/youtubei/v1/player?key=RAHASIA&sig=ABC"
        val detail = streamFailureRawDetail(StreamFailure.Network("gagal hubungi $url"), null)
        assertTrue(detail!!.contains("jenis: Network"))
        assertFalse(detail.contains("https://"), "URL lengkap tidak boleh ikut: $detail")
        assertFalse(detail.contains("RAHASIA"), "query URL tidak boleh ikut: $detail")
        assertTrue(detail.contains("<url disembunyikan>"))
    }

    @Test fun causeChainIsIncludedWithoutUrl() {
        val cause = IOException("timeout hubungi https://example.com/player?x=1")
        val detail = streamFailureRawDetail(StreamFailure.Timeout, cause)
        assertTrue(detail!!.contains("jenis: Timeout"))
        assertTrue(detail.contains("penyebab: IOException:"))
        assertFalse(detail.contains("https://example.com"), "URL di cause tidak boleh ikut: $detail")
    }

    @Test fun nullFailureAndCauseIsNull() {
        assertNull(streamFailureRawDetail(null, null))
    }

    @Test fun musicExceptionCarriesTypeStatusAndMessage() {
        val detail = musicErrorRawDetail(MyTubeMusicException("YouTube membatasi permintaan.", 429))
        assertEquals("jenis: MyTubeMusicException\nstatus: 429\npesan: YouTube membatasi permintaan.", detail)
    }

    @Test fun musicExceptionWithoutStatusOmitsStatusLine() {
        val detail = musicErrorRawDetail(MyTubeMusicException("Tidak bisa menghubungi YouTube Music."))
        assertEquals("jenis: MyTubeMusicException\npesan: Tidak bisa menghubungi YouTube Music.", detail)
    }

    @Test fun genericExceptionMessageHasNoFullUrl() {
        val detail = musicErrorRawDetail(IllegalStateException("gagal buka https://music.youtube.com/watch?v=abc&sig=RAHASIA"))
        assertTrue(detail!!.contains("jenis: IllegalStateException"))
        assertFalse(detail.contains("https://"), "URL lengkap tidak boleh ikut: $detail")
        assertFalse(detail.contains("RAHASIA"), "query URL tidak boleh ikut: $detail")
    }

    @Test fun genericExceptionCauseChainIsIncluded() {
        val cause = IOException("jaringan mati")
        val detail = musicErrorRawDetail(IllegalStateException("gagal memuat", cause))
        assertTrue(detail!!.contains("jenis: IllegalStateException"))
        assertTrue(detail.contains("penyebab: IOException: jaringan mati"))
    }

    @Test fun nullErrorIsNull() {
        assertNull(musicErrorRawDetail(null))
    }
}
