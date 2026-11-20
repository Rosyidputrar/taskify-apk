package com.rosyidputrar.taskify.core.innertube.pages

import com.rosyidputrar.taskify.core.domain.media.CollectionKind
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Parser podcast — cermin tes `parsePodcast` Worker. */
class PodcastPageTest {

    @Test fun `podcast judul penulis dan episode terbaca`() {
        val c = parsePodcast(FixtureHelper.json("browse-podcast"), "MPSPx")
        assertNotNull(c)
        assertTrue(c!!.title.isNotEmpty())
        assertTrue(c.tracks.isNotEmpty())
        assertEquals(CollectionKind.Podcast, c.kind)
    }

    @Test fun `episode dipetakan ke Song dengan artist acara tanggal`() {
        val c = parsePodcast(FixtureHelper.json("browse-podcast"), "MPSPx")!!
        for (e in c.tracks) {
            assertTrue(e.videoId.isNotEmpty())
            assertTrue(e.title.isNotEmpty())
            // durasi episode "N mnt" terurai ke detik; tanpa durasi = null.
            val d = e.durationSeconds
            assertTrue(d == null || d > 0)
            assertTrue(e.duration.isNotEmpty() || e.durationSeconds == null)
        }
        assertTrue(c.tracks.any { it.durationSeconds != null })
    }

    @Test fun `token gaya lama terbaca dari halaman podcast`() {
        assertTrue(podcastToken(FixtureHelper.json("browse-podcast")).isNotEmpty())
    }

    @Test fun `parsePodcastMore membaca episode lanjutan`() {
        val more = parsePodcastMore(FixtureHelper.json("browse-podcast-cont"), "Acara Contoh")
        assertTrue(more.tracks.isNotEmpty())
        for (e in more.tracks) {
            assertTrue(e.videoId.isNotEmpty())
            assertTrue(e.artist.contains("Acara Contoh"))
        }
    }

    @Test fun `podcast tanpa judul menghasilkan null`() {
        assertEquals(null, parsePodcast(FixtureHelper.json("next-empty"), "MPSPx"))
    }

    @Test fun `respons kosong menghasilkan episode kosong bukan throw`() {
        val more = parsePodcastMore(FixtureHelper.json("next-empty"), "Acara Contoh")
        assertTrue(more.tracks.isEmpty())
        assertEquals("", more.token)
    }
}
