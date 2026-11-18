package com.rosyidputrar.taskify.core.domain

import com.rosyidputrar.taskify.core.domain.media.MusicQueue.reduce
import com.rosyidputrar.taskify.core.domain.media.Song
import com.rosyidputrar.taskify.core.domain.media.StreamSource
import com.rosyidputrar.taskify.core.domain.media.YtEvent
import com.rosyidputrar.taskify.core.domain.media.YtState
import com.rosyidputrar.taskify.core.domain.media.YtStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Error pemutar membawa detail mentah sampai ke state (Agen A):
 * `PlaybackFailed` menyimpan `rawDetail`, transisi keluar dari error
 * membersihkannya, dan galat lain tidak mewarisi detail basi.
 */
class MusicPlaybackErrorTest {
    private fun song(id: String) = Song(id.padEnd(11, 'x'), "Lagu $id", "Artis", "3.05", emptyList())
    private val a = song("a")

    private fun playingState(): YtState {
        val queued = reduce(YtState(), YtEvent.SetQueue(listOf(a), 0))
        return queued.copy(
            status = YtStatus.Playing,
            urls = mapOf(a.videoId to "https://rr1---sn-xxx.googlevideo.com/videoplayback?sig=RAHASIA"),
            streams = mapOf(a.videoId to StreamSource(StreamSource.SOURCE_DIRECT, "audio/webm; codecs=\"opus\"", 251)),
        )
    }

    @Test fun playbackFailedStoresRawDetailAndDropsStream() {
        val detail = "videoId: ${a.videoId}\nerrorCodeName: ERROR_CODE_IO_NETWORK_CONNECTION_FAILED"
        val failed = reduce(playingState(), YtEvent.PlaybackFailed(a.videoId, "Audio gagal diputar. Coba lagi.", detail))
        assertEquals(YtStatus.Error, failed.status)
        assertEquals("Audio gagal diputar. Coba lagi.", failed.error)
        assertEquals(detail, failed.errorDetail)
        // URL/stream yang gagal dibuang supaya diminta ulang; detailnya tetap tersimpan.
        assertTrue(failed.urls.isEmpty())
        assertTrue(failed.streams.isEmpty())
    }

    @Test fun playbackFailedWithoutRawDetailLeavesItNull() {
        val failed = reduce(playingState(), YtEvent.PlaybackFailed(a.videoId, "Audio gagal diputar. Coba lagi."))
        assertEquals(YtStatus.Error, failed.status)
        assertNull(failed.errorDetail)
    }

    @Test fun playbackFailedKeepsLiveUrlButStoresDetail() {
        val live = song("live").copy(live = true, url = "https://radio.example/stream")
        val queued = reduce(YtState(), YtEvent.SetQueue(listOf(live), 0)).copy(status = YtStatus.Playing)
        val failed = reduce(queued, YtEvent.PlaybackFailed(live.videoId, "Siaran tidak bisa diputar sekarang.", "errorCode: 1002001"))
        assertEquals(YtStatus.Error, failed.status)
        assertEquals("errorCode: 1002001", failed.errorDetail)
        assertEquals("https://radio.example/stream", failed.urls[live.videoId])
    }

    @Test fun preparingClearsErrorDetail() {
        val failed = reduce(playingState(), YtEvent.PlaybackFailed(a.videoId, "Audio gagal diputar. Coba lagi.", "errorCode: 1"))
        val preparing = reduce(failed, YtEvent.Preparing)
        assertEquals(YtStatus.Preparing, preparing.status)
        assertEquals("", preparing.error)
        assertNull(preparing.errorDetail)
    }

    @Test fun playingClearsErrorDetail() {
        val failed = reduce(playingState(), YtEvent.PlaybackFailed(a.videoId, "Audio gagal diputar. Coba lagi.", "errorCode: 1"))
        val playing = reduce(failed, YtEvent.Playing)
        assertNull(playing.errorDetail)
    }

    @Test fun otherErrorDoesNotInheritStaleDetail() {
        val failed = reduce(playingState(), YtEvent.PlaybackFailed(a.videoId, "Audio gagal diputar. Coba lagi.", "errorCode: 1"))
        val other = reduce(failed, YtEvent.Error("Gagal memuat audio."))
        assertEquals(YtStatus.Error, other.status)
        assertEquals("Gagal memuat audio.", other.error)
        assertNull(other.errorDetail)
    }

    @Test fun resolveErrorStoresRawDetail() {
        val failed = reduce(playingState(), YtEvent.Error("Audio tidak bisa dimuat dari YouTube.", "jenis: Http\nstatus: 429"))
        assertEquals(YtStatus.Error, failed.status)
        assertEquals("Audio tidak bisa dimuat dari YouTube.", failed.error)
        assertEquals("jenis: Http\nstatus: 429", failed.errorDetail)
    }
}
