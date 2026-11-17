package com.rosyidputrar.taskify.core.domain

import com.rosyidputrar.taskify.core.domain.media.MusicQueue
import com.rosyidputrar.taskify.core.domain.media.MusicQueue.reduce
import com.rosyidputrar.taskify.core.domain.media.RepeatMode
import com.rosyidputrar.taskify.core.domain.media.Song
import com.rosyidputrar.taskify.core.domain.media.StreamSource
import com.rosyidputrar.taskify.core.domain.media.TikTok
import com.rosyidputrar.taskify.core.domain.media.TrackDetails
import com.rosyidputrar.taskify.core.domain.media.YtEvent
import com.rosyidputrar.taskify.core.domain.media.YtState
import com.rosyidputrar.taskify.core.domain.media.YtStatus
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Kasus dipilih dari queue-core.test.ts, music-view.test.ts, tiktok-link.test.ts webapp. */
class MediaTest {
    private fun song(id: String) = Song(id.padEnd(11, 'x'), "Lagu $id", "Artis", "3.05", emptyList())
    private val a = song("a"); private val b = song("b"); private val c = song("c")

    @Test fun setQueueDedupesAndStartsPreparing() {
        val s = reduce(YtState(), YtEvent.SetQueue(listOf(a, b, a, c), startAt = 1))
        assertEquals(listOf(a, b, c), s.queue)
        assertEquals(b, s.current)
        assertEquals(YtStatus.Preparing, s.status)
        assertEquals(YtState(urls = s.urls), reduce(s.copy(urls = emptyMap()), YtEvent.SetQueue(emptyList())).copy())
    }

    @Test fun endedFollowsRepeatModes() {
        var s = reduce(YtState(), YtEvent.SetQueue(listOf(a, b)))
        s = reduce(reduce(s, YtEvent.Ready(a.videoId, "u1")), YtEvent.Playing)
        s = reduce(s, YtEvent.Ended)
        assertEquals(b, s.current); assertEquals(YtStatus.Preparing, s.status)
        // akhir antrean tanpa ulang → jeda & menunggu lanjutan
        s = reduce(reduce(s, YtEvent.Playing), YtEvent.Ended)
        assertEquals(YtStatus.Paused, s.status); assertEquals(true, s.awaitingMore)
        // ulang semua → kembali ke awal
        val all = reduce(reduce(s.copy(awaitingMore = false), YtEvent.ToggleRepeat), YtEvent.Ended)
        assertEquals(RepeatMode.All, all.repeat); assertEquals(a, all.current)
        // ulang satu: lagu yang sama langsung main lagi (URL sudah ada)
        val one = reduce(YtState(queue = listOf(a, b), order = listOf(0, 1), status = YtStatus.Playing, urls = mapOf(a.videoId to "u"), repeat = RepeatMode.One), YtEvent.Ended)
        assertEquals(a, one.current); assertEquals(YtStatus.Playing, one.status)
        // tapi "berikutnya" manual tetap pindah lagu
        assertEquals(1, MusicQueue.nextPosition(one, manual = true))
    }

    @Test fun shuffleKeepsCurrentFirstAndRestores() {
        val s0 = YtState(queue = listOf(a, b, c), order = listOf(0, 1, 2), position = 1, status = YtStatus.Playing)
        val sh = reduce(s0, YtEvent.ToggleShuffle(Random(7)))
        assertEquals(b, sh.current); assertEquals(0, sh.position); assertEquals(setOf(0, 1, 2), sh.order.toSet())
        val back = reduce(sh, YtEvent.ToggleShuffle())
        assertEquals(listOf(0, 1, 2), back.order); assertEquals(b, back.current)
    }

    @Test fun prefetchDetailsAndContinuation() {
        var s = YtState(queue = listOf(a, b), order = listOf(0, 1), position = 0, status = YtStatus.Playing)
        assertEquals(b.videoId, MusicQueue.prefetchTarget(s))
        assertNull(MusicQueue.prefetchTarget(reduce(s, YtEvent.PrefetchStart(b.videoId))))
        assertEquals(a.videoId, MusicQueue.detailsTarget(s))
        s = s.copy(position = 1)
        s = reduce(s, YtEvent.DetailsDone(b.videoId, TrackDetails("L1", listOf(a, c))))
        assertEquals(b.videoId to listOf(c), MusicQueue.continuationSource(s))
        assertEquals("L1", s.lyricsId)
        val settled = MusicQueue.settle(s.copy(awaitingMore = true))
        assertEquals(listOf(a, b, c), settled.queue); assertEquals(c, settled.current); assertEquals(YtStatus.Preparing, settled.status)
        assertNull(MusicQueue.continuationSource(settled))
    }

    @Test fun viewHelpers() {
        assertEquals(398, MusicQueue.parseDuration("6.38"))
        assertEquals(3723, MusicQueue.parseDuration("1:02:03"))
        assertNull(MusicQueue.parseDuration("6.3"))
        assertEquals("6:38", MusicQueue.formatDuration("6.38"))
        assertEquals("1:02:03", MusicQueue.formatClock(3723))
        assertEquals("Menyiapkan audio… 40%", MusicQueue.progressLabel(40))
        assertEquals("Hampir siap…", MusicQueue.progressLabel(95))
        assertEquals("Tidak ada koneksi", MusicQueue.barStatus(YtState(status = YtStatus.Preparing), online = false))
        assertEquals("Artis · 2/3", MusicQueue.barStatus(YtState(queue = listOf(a, b, c), order = listOf(0, 1, 2), position = 1, status = YtStatus.Playing), true))
        assertEquals(listOf(a, b, c), MusicQueue.mergePages(listOf(a, b), listOf(b, c)))
    }

    @Test fun tiktokLinks() {
        assertEquals("https://vt.tiktok.com/ZSabc123/", TikTok.findLink("Lihat video ini! https://vt.tiktok.com/ZSabc123/ keren"))
        assertEquals("https://www.tiktok.com/@budi/video/7312345678901234567", TikTok.findLink("www.tiktok.com/@budi/video/7312345678901234567?is_from_webapp=1".substringBefore("?")))
        assertEquals("https://www.tiktok.com/t/ZT8abc/", TikTok.findLink("https://www.tiktok.com/t/ZT8abc/."))
        assertNull(TikTok.findLink("https://www.tiktok.com/@budi"))
        assertNull(TikTok.findLink("tanpa link"))
    }

    @Test fun prefetchCancelledClearsPrefetchingWithoutFailing() {
        val s = reduce(YtState(), YtEvent.PrefetchStart(b.videoId))
        assertEquals(listOf(b.videoId), s.prefetching)
        val s2 = reduce(s, YtEvent.PrefetchCancelled(b.videoId))
        assertEquals(emptyList(), s2.prefetching)
        assertEquals(emptyMap(), s2.urls)
    }

    @Test fun streamInfoStoredPerSong() {
        val s = reduce(YtState(), YtEvent.StreamInfo(a.videoId, StreamSource("direct", "audio/webm; codecs=opus", 251)))
        assertEquals("direct", s.streams[a.videoId]?.source)
        assertEquals("audio/webm; codecs=opus", s.streams[a.videoId]?.mimeType)
        assertEquals(251, s.streams[a.videoId]?.itag)
        assertEquals(true, s.streams[a.videoId]?.isOpus)
        val s2 = reduce(s, YtEvent.StreamInfo(b.videoId, StreamSource("converter", "audio/mpeg", null)))
        assertEquals("audio/mpeg", s2.streams[b.videoId]?.mimeType)
        assertEquals(false, s2.streams[b.videoId]?.isOpus)
        assertEquals("direct", s2.streams[a.videoId]?.source)
    }

    @Test fun dispositionNames() {
        assertEquals("a b.mp4", TikTok.fileNameFromDisposition("attachment; filename=\"x.mp4\"; filename*=UTF-8''a%20b.mp4", "f.mp4"))
        assertEquals("x.mp4", TikTok.fileNameFromDisposition("attachment; filename=\"x.mp4\"", "f.mp4"))
        assertEquals("a_b.jpg", TikTok.fileNameFromDisposition("attachment; filename=a/b.jpg", "f"))
        assertEquals("f.mp4", TikTok.fileNameFromDisposition(null, "f.mp4"))
        assertEquals("1,2 jt", TikTok.compactCount(1_234_567))
        assertEquals("15,3 rb", TikTok.compactCount(15_300))
        assertEquals("2 rb", TikTok.compactCount(2_000))
    }
}
