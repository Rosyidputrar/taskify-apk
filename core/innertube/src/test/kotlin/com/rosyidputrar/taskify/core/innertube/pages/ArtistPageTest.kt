package com.rosyidputrar.taskify.core.innertube.pages

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Parser halaman artis — cermin tes `parseArtist` Worker. */
class ArtistPageTest {

    @Test fun `nama dan pengikut terbaca dari header`() {
        val a = parseArtist(FixtureHelper.json("artist"), "UC_DHlXllTSMB8pTC38_leFg")
        assertNotNull(a)
        assertEquals("Tulus", a!!.name)
        assertEquals("UC_DHlXllTSMB8pTC38_leFg", a.browseId)
        assertTrue(a.subscribers.isNotEmpty())
        assertTrue(a.thumbnails.isNotEmpty())
    }

    @Test fun `lima lagu teratas tanpa durasi`() {
        val a = parseArtist(FixtureHelper.json("artist"), "UC_DHlXllTSMB8pTC38_leFg")!!
        assertEquals(5, a.topSongs.size)
        for (s in a.topSongs) {
            assertTrue(s.title.isNotEmpty())
            assertTrue(s.videoId.isNotEmpty())
            assertEquals("", s.duration)
            assertNull(s.durationSeconds)
        }
    }

    @Test fun `id playlist lagu teratas hanya yang berawalan VL`() {
        val a = parseArtist(FixtureHelper.json("artist"), "UC_DHlXllTSMB8pTC38_leFg")!!
        val pid = a.topSongsPlaylistId
        assertTrue(pid == null || pid.startsWith("VL"))
    }

    @Test fun `album dan single terbaca dengan tahun`() {
        val a = parseArtist(FixtureHelper.json("artist"), "UC_DHlXllTSMB8pTC38_leFg")!!
        assertTrue(a.albums.isNotEmpty())
        assertTrue(a.singles.isNotEmpty())
        for (album in a.albums + a.singles) {
            assertTrue(album.browseId.startsWith("MPREb"), album.browseId)
            assertTrue(album.title.isNotEmpty())
            assertEquals("Tulus", album.artist)
            assertTrue(album.year.isEmpty() || Regex("""^\d{4}$""").matches(album.year))
        }
    }

    @Test fun `artis terkait terbaca`() {
        val a = parseArtist(FixtureHelper.json("artist"), "UC_DHlXllTSMB8pTC38_leFg")!!
        assertTrue(a.related.isNotEmpty())
        for (r in a.related) {
            assertTrue(r.browseId.startsWith("UC"), r.browseId)
            assertTrue(r.name.isNotEmpty())
        }
    }

    @Test fun `artis tanpa nama menghasilkan null`() {
        assertNull(parseArtist(FixtureHelper.json("next-empty"), "UCx"))
    }
}
