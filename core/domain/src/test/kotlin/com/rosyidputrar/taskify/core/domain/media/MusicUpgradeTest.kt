package com.rosyidputrar.taskify.core.domain.media

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Fase 1–4 pemutar musik: fallback konverter, kedaluwarsa URL, seek aman, radio, katalog, lirik, SponsorBlock. */
class MusicUpgradeTest {
    private fun song(id: String, seconds: Int? = 185) = Song(id.padEnd(11, 'x'), "Lagu $id", "Artis", "3.05", emptyList(), durationSeconds = seconds)
    private val a = song("a"); private val b = song("b"); private val c = song("c")
    private fun playing(vararg songs: Song, pos: Int = 0) =
        MusicQueue.reduce(YtState(), YtEvent.SetQueue(songs.toList(), pos)).copy(status = YtStatus.Playing)

    private val direct = StreamSource(StreamSource.SOURCE_DIRECT, "audio/webm; codecs=\"opus\"", 251, expiresAt = 1_000_000L)
    private val converter = StreamSource(StreamSource.SOURCE_CONVERTER, "audio/mpeg", null, job = "job-1")

    // ------------------------------------------------------------------ durasi

    @Test fun durationSecondsWinsAndNullMeansUnknown() {
        assertEquals(398, Song("x", "t", "a", "6.38", emptyList(), durationSeconds = 398).seconds)
        assertEquals(185, Song("x", "t", "a", "3.05", emptyList()).seconds, "data lama tanpa durationSeconds tetap diurai dari teks")
        assertNull(Song("x", "t", "a", "", emptyList()).seconds)
        assertEquals("6:38", MusicQueue.formatDuration(Song("x", "t", "a", "6.38", emptyList(), durationSeconds = 398)))
        assertEquals("42 mnt", MusicQueue.formatDuration(Song("x", "t", "a", "42 mnt", emptyList())), "durasi episode podcast tampil apa adanya")
        assertEquals("LIVE", MusicQueue.formatDuration(RadioStation("s", "Drone", "https://x", "deep", "", "SomaFM").toSong()))
        val (total, complete) = MusicQueue.knownTotalSeconds(listOf(a, b, song("n", null).copy(duration = "")))
        assertEquals(370, total, "lagu tanpa durasi tidak dijumlahkan sebagai 0")
        assertFalse(complete)
    }

    // ------------------------------------------------------------------ seek & sumber audio

    @Test fun onlyDirectAndR2AreSeekable() {
        assertTrue(direct.seekable)
        assertTrue(StreamSource(StreamSource.SOURCE_R2, "audio/mpeg", null).seekable)
        assertFalse(converter.seekable, "URL konverter tidak mendukung Range")
        val s = playing(a).copy(streams = mapOf(a.videoId to converter))
        assertFalse(s.seekable)
        assertTrue(s.copy(streams = mapOf(a.videoId to direct)).seekable)
        assertFalse(playing(a).seekable, "asal audio belum diketahui → jangan izinkan seek")
        val archived = MusicQueue.reduce(s, YtEvent.Archived(a.videoId, "https://api/v1/audio/a/mp3?token=t", StreamSource(StreamSource.SOURCE_R2, "audio/mpeg", null)))
        assertTrue(archived.seekable, "setelah arsip R2 siap, lagu bisa digeser")
        assertEquals("https://api/v1/audio/a/mp3?token=t", archived.urls[a.videoId])
    }

    @Test fun directPlaybackFailureFallsBackToConverterOnce() {
        val s = playing(a).copy(urls = mapOf(a.videoId to "https://direct"), streams = mapOf(a.videoId to direct))
        val fb = MusicQueue.reduce(s, YtEvent.PlaybackFailed(a.videoId, "Audio gagal diputar."))
        assertEquals(YtStatus.Preparing, fb.status)
        assertEquals(listOf(a.videoId), fb.fallbacks)
        assertNull(fb.urls[a.videoId], "URL direct yang gagal dibuang")
        assertNull(fb.streams[a.videoId])
        // Konverter pun gagal → baru galat (tidak berputar terus).
        val again = MusicQueue.reduce(fb.copy(urls = mapOf(a.videoId to "https://conv"), streams = mapOf(a.videoId to converter), status = YtStatus.Playing),
            YtEvent.PlaybackFailed(a.videoId, "Audio gagal diputar."))
        assertEquals(YtStatus.Error, again.status)
        assertEquals("Audio gagal diputar.", again.error)
    }

    @Test fun expiredDirectUrlsAreDropped() {
        val s = playing(a, b).copy(urls = mapOf(a.videoId to "u1", b.videoId to "u2"),
            streams = mapOf(a.videoId to direct, b.videoId to converter))
        val stillFresh = MusicQueue.reduce(s, YtEvent.Expire(1_000_000L - MusicQueue.EXPIRY_MARGIN_MS - 1))
        assertEquals(2, stillFresh.urls.size)
        val expired = MusicQueue.reduce(s, YtEvent.Expire(1_000_000L - MusicQueue.EXPIRY_MARGIN_MS))
        assertNull(expired.urls[a.videoId], "kedaluwarsa (dengan margin 5 menit) → minta URL baru")
        assertEquals("u2", expired.urls[b.videoId], "URL tanpa batas waktu tidak dibuang")
    }

    // ------------------------------------------------------------------ live, simpan, radio

    @Test fun liveStationPlaysImmediatelyWithoutDetails() {
        val live = RadioStation("soma", "Drone Zone", "https://ice1.somafm.com/dronezone-128-mp3", "deep", "", "SomaFM").toSong()
        val s = MusicQueue.settle(MusicQueue.reduce(YtState(), YtEvent.SetQueue(listOf(live))))
        assertEquals(YtStatus.Playing, s.status, "URL stream sudah ada → langsung main")
        assertEquals(live.url, s.currentUrl)
        assertNull(MusicQueue.detailsTarget(s), "siaran langsung tanpa lirik/antrean lanjutan")
        assertFalse(s.seekable)
        assertEquals("Siaran langsung · SomaFM", MusicQueue.barStatus(s, true))
        val ended = MusicQueue.reduce(s, YtEvent.Ended)
        assertFalse(ended.awaitingMore)
    }

    @Test fun snapshotRestoresPausedQueueWithoutUrls() {
        val s = playing(a, b, c, pos = 1).copy(repeat = RepeatMode.All, urls = mapOf(b.videoId to "u"), radio = RadioSource("RDAMVMx", "cur", pending = true))
        val snap = MusicQueue.snapshot(s, 42_000)!!
        assertEquals(42_000, snap.positionMs)
        assertFalse(snap.radio!!.pending)
        val restored = MusicQueue.reduce(YtState(), YtEvent.Restore(snap))
        assertEquals(YtStatus.Paused, restored.status)
        assertEquals(b, restored.current)
        assertEquals(RepeatMode.All, restored.repeat)
        assertTrue(restored.urls.isEmpty(), "URL tidak disimpan lintas proses")
        assertNull(MusicQueue.snapshot(YtState(), 0), "antrean kosong tidak disimpan")
    }

    @Test fun radioContinuesWithCursorNearTheEnd() {
        val radio = RadioSource("RDAMVMa", "cursor-1")
        var s = MusicQueue.reduce(YtState(), YtEvent.SetQueue(listOf(a, b, c), 0, radio = radio)).copy(status = YtStatus.Playing)
        assertNull(MusicQueue.radioTarget(s), "masih jauh dari akhir")
        s = s.copy(position = 1)
        assertEquals("cursor-1", MusicQueue.radioTarget(s))
        s = MusicQueue.reduce(s, YtEvent.RadioStart)
        assertNull(MusicQueue.radioTarget(s), "sedang mengambil → tidak dobel")
        val d = song("d"); val e = song("e")
        s = MusicQueue.reduce(s, YtEvent.RadioPage(listOf(c, d, e), "cursor-2", Random(1)))
        assertEquals(listOf(a, b, c, d, e), s.ordered, "lagu yang sudah ada tidak diduplikasi")
        assertEquals("cursor-2", s.radio!!.cursor)
        // Radio punya lanjutan sendiri → antrean "lagu serupa" dari /v1/track tidak dipakai.
        val withDetails = s.copy(position = 4, details = mapOf(e.videoId to TrackDetails("", listOf(song("f")))))
        assertNull(MusicQueue.continuationSource(withDetails))
        // Halaman terakhir kosong tanpa cursor: berhenti menunggu, bukan galat.
        val waiting = s.copy(position = 4, status = YtStatus.Paused, awaitingMore = true)
        val last = MusicQueue.reduce(waiting, YtEvent.RadioPage(emptyList(), null))
        assertFalse(last.awaitingMore)
        assertNull(last.radio!!.cursor)
    }

    @Test fun newQueueResetsRadioAndFallbacks() {
        val s = MusicQueue.reduce(YtState(), YtEvent.SetQueue(listOf(a), radio = RadioSource("RD", "c"))).copy(fallbacks = listOf(a.videoId))
        val next = MusicQueue.reduce(s, YtEvent.SetQueue(listOf(b)))
        assertNull(next.radio)
        assertTrue(next.fallbacks.isEmpty())
    }

    // ------------------------------------------------------------------ katalog & link

    @Test fun browseIdsRouteToTheRightPage() {
        assertEquals(BrowseTarget.Podcast("MPSPPL9pPfr"), MusicCatalog.route("MPSPPL9pPfr"))
        assertEquals(BrowseTarget.Radio("RDCLAK5uy_abc"), MusicCatalog.route("VLRDCLAK5uy_abc"), "kartu mix radio VLRD… dibuka lewat /v1/radio")
        assertEquals(BrowseTarget.Album("MPREb_4TEB"), MusicCatalog.route("MPREb_4TEB"))
        assertEquals(BrowseTarget.Artist("UC_DHl"), MusicCatalog.route("UC_DHl"))
        assertEquals(BrowseTarget.Playlist("VLPL123"), MusicCatalog.route("VLPL123"))
        assertEquals(BrowseTarget.Playlist("VLPL123"), MusicCatalog.route("PL123"), "playlistId tanpa VL diberi awalan")
        assertNull(MusicCatalog.route(""))
    }

    @Test fun resolvedLinksFollowTheApiTable() {
        assertEquals(BrowseTarget.Video("1RrF6Ee_io0"), MusicCatalog.route(ResolvedLink("video", videoId = "1RrF6Ee_io0")))
        assertEquals(BrowseTarget.Radio("RDAMVM1RrF6Ee_io0"), MusicCatalog.route(ResolvedLink("playlist", playlistId = "RDAMVM1RrF6Ee_io0")))
        assertEquals(BrowseTarget.Playlist("VLPLabc"), MusicCatalog.route(ResolvedLink("playlist", playlistId = "PLabc")))
        assertEquals(BrowseTarget.Playlist("VLOLAK5uy_x"), MusicCatalog.route(ResolvedLink("album", browseId = "VLOLAK5uy_x", playlistId = "OLAK5uy_x")))
        assertEquals(BrowseTarget.Album("MPREb_x"), MusicCatalog.route(ResolvedLink("album", browseId = "MPREb_x")))
        assertEquals(BrowseTarget.Artist("UCx"), MusicCatalog.route(ResolvedLink("artist", browseId = "UCx")))
        assertEquals(BrowseTarget.Podcast("MPSPx"), MusicCatalog.route(ResolvedLink("podcast", browseId = "MPSPx")))
        assertNull(MusicCatalog.route(ResolvedLink("video")))
    }

    @Test fun youtubeLinksAreFoundInSharedText() {
        assertEquals("https://youtu.be/1RrF6Ee_io0?t=90", MusicCatalog.findLink("Dengar ini https://youtu.be/1RrF6Ee_io0?t=90."))
        assertEquals("https://music.youtube.com/watch?v=abc&list=RDx", MusicCatalog.findLink("lihat https://music.youtube.com/watch?v=abc&list=RDx"))
        assertEquals("m.youtube.com/shorts/1RrF6Ee_io0", MusicCatalog.findLink("m.youtube.com/shorts/1RrF6Ee_io0"))
        assertNull(MusicCatalog.findLink("https://www.tiktok.com/@a/video/1"))
        assertTrue(MusicCatalog.isLink("https://youtu.be/1RrF6Ee_io0"))
        assertFalse(MusicCatalog.isLink("tulus monokrom"))
        assertFalse(MusicCatalog.isLink("lagu dari https://youtu.be/1RrF6Ee_io0 enak"), "kalimat berisi link tetap dicari sebagai teks")
    }

    @Test fun searchPagesMergeWithoutDuplicates() {
        val p1 = SearchResults(top = TopResult("Monokrom", "", listOf("Album", "Tulus"), emptyList()), songs = listOf(a, b),
            albums = listOf(AlbumRef("MPREb_1", "Monokrom", "Tulus", "2016", emptyList())), cursor = "c1")
        val p2 = SearchResults(songs = listOf(b, c), albums = listOf(AlbumRef("MPREb_1", "Monokrom", "Tulus", "2016", emptyList()), AlbumRef("MPREb_2", "Gajah", "Tulus", "2014", emptyList())), cursor = null)
        val merged = p1.merge(p2)
        assertEquals(listOf(a, b, c), merged.songs)
        assertEquals(listOf("MPREb_1", "MPREb_2"), merged.albums.map { it.browseId })
        assertEquals("Monokrom", merged.top!!.title, "hasil teratas halaman pertama dipertahankan")
        assertNull(merged.cursor)
    }

    @Test fun topResultReadsSubtitlesCarefully() {
        val video = TopResult("Monokrom", "QqJ-Vp8mvbk", listOf("Video", "Tulus", "159 jt x ditonton", "3.39"), emptyList())
        assertEquals("Video", video.kind)
        assertEquals("Tulus · 159 jt x ditonton", video.byline)
        assertEquals(Song("QqJ-Vp8mvbk", "Monokrom", "Tulus", "3.39", emptyList(), "", 219), video.toSong())
        val artistFirst = TopResult("Monokrom", "QqJ-Vp8mvbk", listOf("Tulus", "Monokrom"), emptyList())
        assertNull(artistFirst.kind, "potongan pertama kadang langsung nama artis")
        assertEquals("Tulus · Monokrom", artistFirst.byline)
        assertNull(TopResult("Tulus", "", listOf("Artis", "55 jt"), emptyList()).toSong(), "videoId kosong = bukan lagu, jangan diputar")
    }

    @Test fun albumTracksBorrowAlbumArt() {
        val cover = listOf(Thumb("https://img/544", 544, 544))
        val tracks = MusicCatalog.albumTracks(listOf(a, b.copy(thumbnails = listOf(Thumb("own", 60, 60)))), cover)
        assertEquals("https://img/544", tracks[0].cover)
        assertEquals("own", tracks[1].cover)
        val col = MusicCollection(CollectionKind.Album, "MPREb_1", "Monokrom", "Tulus · 2016", cover, listOf(a, b), trackCount = 10)
        assertEquals("Tulus · 2016 · 10 lagu · 6 mnt", MusicCatalog.subtitle(col))
        assertEquals("1 j 05 mnt", MusicCatalog.formatLong(3_900))
    }

    // ------------------------------------------------------------------ lirik, SponsorBlock, volume, pewaktu

    @Test fun activeLyricLineFollowsPosition() {
        val lines = listOf(LyricLine(10.0, "satu"), LyricLine(12.5, "dua"), LyricLine(15.0, ""), LyricLine(20.0, "tiga"))
        assertEquals(-1, LyricsSync.activeIndex(lines, 3.0))
        assertEquals(0, LyricsSync.activeIndex(lines, 10.0))
        assertEquals(1, LyricsSync.activeIndex(lines, 14.5))
        assertEquals(2, LyricsSync.activeIndex(lines, 14.9), "toleransi 150 ms supaya baris tidak terasa telat")
        assertEquals(3, LyricsSync.activeIndex(lines, 200.0))
        assertTrue(Lyrics(emptyList(), "", "lrclib").instrumental)
        assertFalse(Lyrics(null, "teks", "youtube").hasSynced)
        assertTrue(Lyrics(null, "", "lrclib").empty)
    }

    @Test fun sponsorSkipJumpsToFurthestEndAndIgnoresStaleSegments() {
        val segs = listOf(
            SponsorSegment("music_offtopic", 0.0, 4.0),
            SponsorSegment("intro", 3.5, 9.0),
            SponsorSegment("sponsor", 60.0, 75.0),
            SponsorSegment("outro", 29_988.0, 30_003.0),
            SponsorSegment("poi_highlight", 40.0, 40.0),
            SponsorSegment("interaction", 100.0, 110.0, mute = true),
        )
        val first = SponsorBlock.skipTarget(segs, 0.2, 215.0)!!
        assertEquals(9.0, first.end, "segmen bertumpuk/berurutan → ujung terjauh")
        assertEquals("music_offtopic", first.category)
        assertEquals(75.0, SponsorBlock.skipTarget(segs, 61.0, 215.0)!!.end)
        assertNull(SponsorBlock.skipTarget(segs, 30.0, 215.0))
        assertNull(SponsorBlock.skipTarget(segs, 29_990.0, 215.0), "segmen melewati durasi lagu = basi")
        assertNull(SponsorBlock.skipTarget(segs, 40.0, 215.0), "titik POI bukan lompatan")
        assertNull(SponsorBlock.skipTarget(segs, 105.0, 215.0), "segmen mute tidak dilompati")
        assertTrue(SponsorBlock.muted(segs, 105.0))
    }

    @Test fun loudnessOnlyTurnsDown() {
        assertEquals(1f, Loudness.gain(null))
        assertEquals(1f, Loudness.gain(-6.0), "lagu pelan tidak dikeraskan (bisa pecah)")
        assertEquals(0.647f, Loudness.gain(3.78), 0.01f)
        assertEquals(0.1f, Loudness.gain(40.0))
    }

    @Test fun sleepTimerLabels() {
        assertEquals("Berhenti dalam 15 mnt", SleepTimer(endsAt = 15 * 60_000L).label(0))
        assertEquals("Berhenti dalam 1 mnt", SleepTimer(endsAt = 30_000L).label(0))
        assertEquals("Berhenti di akhir lagu", SleepTimer(endOfSong = true).label(0))
        assertEquals(MusicQuality.Low, MusicQuality.of("low"))
        assertEquals(MusicQuality.Auto, MusicQuality.of("aneh"))
    }

    @Test fun allSearchFiltersPage() {
        assertTrue(SearchFilter.entries.all { it.pages }, "pencarian tanpa filter kini punya cursor gabungan")
        assertEquals(SearchFilter.All, SearchFilter.entries.first())
        assertNull(SearchFilter.All.value)
    }
}
