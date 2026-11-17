package com.rosyidputrar.taskify.core.innertube.pages

import com.rosyidputrar.taskify.core.domain.media.ResolvedLink
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pengenal URL YouTube — cermin tes `resolveYoutubeUrl` Worker. */
class UrlResolveTest {

    private val vid = "1RrF6Ee_io0"
    private val pl = "PL1234567890abcdef1234567890ab"
    private val olak = "OLAK5uy_abcdef1234567890abcdef"
    private val uc = "UC_DHlXllTSMB8pTC38_leFg"

    @Test fun `video watch di semua host v menang atas list`() {
        for (host in listOf("www.youtube.com", "youtube.com", "m.youtube.com", "music.youtube.com")) {
            assertEquals(
                ResolvedLink("video", videoId = vid, url = "https://music.youtube.com/watch?v=$vid"),
                resolveYoutubeUrl("https://$host/watch?v=$vid"),
            )
        }
        assertEquals(
            ResolvedLink("video", videoId = vid, url = "https://music.youtube.com/watch?v=$vid"),
            resolveYoutubeUrl("https://www.youtube.com/watch?v=$vid&list=$pl&si=abc"),
        )
    }

    @Test fun `video youtu be dengan timestamp shorts embed v`() {
        val want = ResolvedLink("video", videoId = vid, url = "https://music.youtube.com/watch?v=$vid")
        assertEquals(want, resolveYoutubeUrl("https://youtu.be/$vid"))
        assertEquals(want, resolveYoutubeUrl("https://youtu.be/$vid?t=90&si=abc"))
        assertEquals(want, resolveYoutubeUrl("https://www.youtube.com/shorts/$vid"))
        assertEquals(want, resolveYoutubeUrl("https://www.youtube.com/embed/$vid"))
        assertEquals(want, resolveYoutubeUrl("https://www.youtube.com/v/$vid"))
    }

    @Test fun `playlist list PL VL dibuang parameter tambahan diabaikan`() {
        assertEquals(
            ResolvedLink("playlist", playlistId = pl, url = "https://music.youtube.com/playlist?list=$pl"),
            resolveYoutubeUrl("https://music.youtube.com/playlist?list=$pl&si=abc&feature=shared"),
        )
        assertEquals(
            ResolvedLink("playlist", playlistId = pl, url = "https://music.youtube.com/playlist?list=VL$pl"),
            resolveYoutubeUrl("https://www.youtube.com/playlist?list=VL$pl"),
        )
    }

    @Test fun `playlist mix radio RD tetap playlist`() {
        val rd = "RDAMVM$vid"
        assertEquals(
            ResolvedLink("playlist", playlistId = rd, url = "https://music.youtube.com/playlist?list=$rd"),
            resolveYoutubeUrl("https://music.youtube.com/playlist?list=$rd"),
        )
    }

    @Test fun `album browse MPREb dan list OLAK5uy browseId VL`() {
        assertEquals(
            ResolvedLink("album", browseId = "MPREb_abcdef123456", url = "https://music.youtube.com/browse/MPREb_abcdef123456"),
            resolveYoutubeUrl("https://music.youtube.com/browse/MPREb_abcdef123456"),
        )
        assertEquals(
            ResolvedLink(
                kind = "album",
                browseId = "VL$olak",
                playlistId = olak,
                url = "https://music.youtube.com/playlist?list=$olak",
            ),
            resolveYoutubeUrl("https://music.youtube.com/playlist?list=$olak"),
        )
    }

    @Test fun `artis channel UC dan browse UC`() {
        assertEquals(
            ResolvedLink("artist", browseId = uc, url = "https://music.youtube.com/channel/$uc"),
            resolveYoutubeUrl("https://www.youtube.com/channel/$uc"),
        )
        assertEquals(
            ResolvedLink("artist", browseId = uc, url = "https://music.youtube.com/channel/$uc"),
            resolveYoutubeUrl("https://music.youtube.com/browse/$uc"),
        )
    }

    @Test fun `podcast browse MPSP`() {
        val mpsp = "MPSPPL9pPfrcZbI5QiYJjqTg6Zh2OZIOgZGDme"
        assertEquals(
            ResolvedLink("podcast", browseId = mpsp, url = "https://music.youtube.com/browse/$mpsp"),
            resolveYoutubeUrl("https://music.youtube.com/browse/$mpsp"),
        )
    }

    @Test fun `host bukan YouTube URL sampah dan path tak dikenal menghasilkan null`() {
        assertTrue(isYoutubeHost("www.youtube.com"))
        assertTrue(isYoutubeHost("youtu.be"))
        assertTrue(!isYoutubeHost("www.youtu.be"))
        assertTrue(!isYoutubeHost("youtube-nocookie.com"))
        assertNull(resolveYoutubeUrl("https://www.youtube.com/"))
        assertNull(resolveYoutubeUrl("https://www.youtube.com/watch"))
        assertNull(resolveYoutubeUrl("https://www.youtube.com/watch?v=pendek"))
        assertNull(resolveYoutubeUrl("https://www.youtube.com/playlist?list=X"))
        assertNull(resolveYoutubeUrl("https://www.youtube.com/@tulus"))
        assertNull(resolveYoutubeUrl("https://www.youtube.com/results?search_query=tulus"))
        assertNull(resolveYoutubeUrl("https://example.com/watch?v=$vid"))
        assertNull(resolveYoutubeUrl("bukan url"))
    }

    @Test fun `ekstraktor id berdiri sendiri`() {
        assertEquals(vid, extractVideoId(vid))
        assertEquals(vid, extractVideoId("https://youtu.be/$vid"))
        assertEquals(pl, extractPlaylistId("VL$pl"))
        assertEquals("MPREb_abcdef123456", extractAlbumId("https://music.youtube.com/browse/MPREb_abcdef123456"))
        assertEquals("VL$olak", extractAlbumId(olak))
        assertEquals(uc, extractChannelId("https://www.youtube.com/channel/$uc"))
    }
}
