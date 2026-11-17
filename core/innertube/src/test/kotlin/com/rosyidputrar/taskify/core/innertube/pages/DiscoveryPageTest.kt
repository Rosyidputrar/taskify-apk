package com.rosyidputrar.taskify.core.innertube.pages

import com.rosyidputrar.taskify.core.domain.media.DiscoverySection
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Parser discovery — cermin tes `parseHome`/`parseCharts`/`parseNewReleases`/`parseMoodCategories` Worker. */
class DiscoveryPageTest {

    private fun songsOf(sections: List<DiscoverySection>): Int =
        sections.filterIsInstance<DiscoverySection.Songs>().sumOf { it.items.size }

    @Test fun `home dua rak lagu rak tastebuilder minta login dilewati`() {
        val sections = parseHome(FixtureHelper.json("home"))
        assertEquals(2, sections.size)
        assertEquals(28, songsOf(sections))
        for (s in sections.filterIsInstance<DiscoverySection.Songs>()) {
            for (song in s.items) {
                assertTrue(song.title.isNotEmpty())
                assertTrue(song.videoId.isNotEmpty())
            }
        }
    }

    @Test fun `home mix rak kartu playlist ikut dikembalikan bukan dibuang`() {
        val sections = parseHome(FixtureHelper.json("home-mix"))
        assertEquals(2, sections.size)
        val playlists = sections.filterIsInstance<DiscoverySection.Playlists>()
        assertTrue(playlists.isNotEmpty())
        assertEquals(20, sections[0].let {
            when (it) {
                is DiscoverySection.Songs -> it.items.size
                is DiscoverySection.Albums -> it.items.size
                is DiscoverySection.Artists -> it.items.size
                is DiscoverySection.Playlists -> it.items.size
            }
        })
    }

    @Test fun `charts rak pemilih negara tanpa isi dilewati 5 seksi tersisa`() {
        val sections = parseCharts(FixtureHelper.json("charts"))
        assertEquals(5, sections.size)
    }

    @Test fun `charts video dan genre berupa kartu playlist`() {
        val sections = parseCharts(FixtureHelper.json("charts"))
        val byTitle = sections.associateBy { it.title }
        val videos = byTitle.values.firstOrNull { it is DiscoverySection.Playlists && it.items.size == 4 }
        assertTrue(videos is DiscoverySection.Playlists)
        val genres = byTitle.values.firstOrNull { it is DiscoverySection.Playlists && it.items.size == 8 }
        assertTrue(genres is DiscoverySection.Playlists)
        for (p in (videos as DiscoverySection.Playlists).items) {
            assertEquals(p.browseId.removePrefix("VL"), p.playlistId)
            assertTrue(p.title.isNotEmpty())
        }
    }

    @Test fun `charts album teratas 50 album dengan artis dan tahun`() {
        val sections = parseCharts(FixtureHelper.json("charts"))
        val albums = sections.filterIsInstance<DiscoverySection.Albums>().first()
        assertEquals(50, albums.items.size)
        for (a in albums.items) {
            assertTrue(a.browseId.startsWith("MPREb"))
            assertTrue(a.title.isNotEmpty())
            assertTrue(a.artist.isNotEmpty())
            assertTrue(a.year.isEmpty() || Regex("""^\d{4}$""").matches(a.year))
        }
    }

    @Test fun `charts artis teratas 40 artis dengan jumlah subscriber`() {
        val sections = parseCharts(FixtureHelper.json("charts"))
        val artists = sections.filterIsInstance<DiscoverySection.Artists>().first()
        assertEquals(40, artists.items.size)
        assertEquals("YoungBoy Never Broke Again", artists.items[0].name)
        for (a in artists.items) {
            assertTrue(a.browseId.startsWith("UC"))
            assertTrue(a.subscribers.isNotEmpty())
        }
    }

    @Test fun `charts podcast berupa referensi playlist dengan browseId MPSP`() {
        val sections = parseCharts(FixtureHelper.json("charts"))
        val podcasts = sections.filterIsInstance<DiscoverySection.Playlists>()
            .first { it.items.any { p -> p.browseId.startsWith("MPSP") } }
        assertEquals(50, podcasts.items.size)
        for (p in podcasts.items) {
            assertEquals(p.browseId, p.playlistId)
        }
    }

    @Test fun `rilisan baru tiga seksi mix album dan single video musik`() {
        val sections = parseNewReleases(FixtureHelper.json("new-releases"))
        assertEquals(3, sections.size)
        val mix = sections.filterIsInstance<DiscoverySection.Playlists>().first()
        assertEquals(1, mix.items.size)
        assertEquals("Rilis Baru", mix.items[0].title)
        assertEquals("YouTube Music", mix.items[0].author)
        val albums = sections.filterIsInstance<DiscoverySection.Albums>().first()
        assertEquals(24, albums.items.size)
        val videos = sections.filterIsInstance<DiscoverySection.Songs>().first()
        assertEquals(24, videos.items.size)
        for (v in videos.items) {
            assertTrue(v.videoId.isNotEmpty())
            assertTrue(v.artist.isNotEmpty())
            assertEquals("", v.duration)
        }
    }

    @Test fun `kategori mood dua seksi 12 mood dan 27 genre`() {
        val cats = parseMoodCategories(FixtureHelper.json("moods"))
        assertEquals(2, cats.size)
        assertEquals(12, cats[0].items.size)
        assertEquals(27, cats[1].items.size)
        for (section in cats) {
            for (item in section.items) {
                assertTrue(item.title.isNotEmpty())
                assertTrue(item.params.isNotEmpty())
            }
        }
    }

    @Test fun `findMoodCategory tidak peduli huruf besar kecil dan spasi`() {
        val cats = parseMoodCategories(FixtureHelper.json("moods"))
        assertEquals("Fokus", findMoodCategory(cats, "fokus")?.title)
        assertEquals("Fokus", findMoodCategory(cats, "  FOKUS  ")?.title)
        assertEquals(null, findMoodCategory(cats, "tidak ada mood ini"))
        assertEquals(null, findMoodCategory(cats, ""))
    }

    @Test fun `halaman kategori mood 8 carousel kartu playlist 148 kartu`() {
        val sections = parseDiscoverySections(FixtureHelper.json("mood-fokus"))
        assertEquals(8, sections.size)
        assertTrue(sections.all { it is DiscoverySection.Playlists })
        assertEquals(148, sections.sumOf { (it as DiscoverySection.Playlists).items.size })
    }

    @Test fun `kartu dan baris discovery tanpa artis tidak memakai durasi atau tahun sebagai artis`() {
        // Sapuan pola bug Tahap 9: di discovery, artis/author juga diambil
        // dari posisi subtitle tanpa guard — kartu album ['Single', '2014']
        // (bentuk nyata di fixture artist), kartu video ['3.33'], MPED
        // [durasi, artis] yang terbalik, dan baris daftar album/lagu/
        // playlist yang slot artisnya diisi durasi/tahun. Fixture ini
        // mereplikasi tiap bentuk itu + kartu normal sebagai regresi.
        val sections = parseHome(FixtureHelper.json("discovery-no-artist"))
        assertEquals(6, sections.size)
        val byTitle = sections.associateBy { it.title }

        val albums = (byTitle["Album tanpa artis"] as DiscoverySection.Albums).items
        assertEquals(3, albums.size)
        assertEquals("", albums.first { it.title == "Single Tanpa Artis" }.artist)
        assertEquals("", albums.first { it.title == "Album Durasi" }.artist)
        assertEquals("Tulus", albums.first { it.title == "Album Normal" }.artist)

        val songs = (byTitle["Video tanpa artis"] as DiscoverySection.Songs).items
        assertEquals(4, songs.size)
        assertEquals("", songs.first { it.videoId == "NOARTIST01A" }.artist)
        assertEquals("Tulus", songs.first { it.videoId == "VIDNORMAL01" }.artist)
        assertEquals("Dj Vini", songs.first { it.videoId == "MPEDNORM01A" }.artist)
        assertEquals("", songs.first { it.videoId == "MPEDREV01AA" }.artist)

        val playlists = (byTitle["Playlist tanpa author"] as DiscoverySection.Playlists).items
        assertEquals(2, playlists.size)
        assertEquals("", playlists.first { it.title == "Playlist Durasi" }.author)
        assertEquals("YouTube Music", playlists.first { it.title == "Playlist Normal" }.author)

        val albumRows = (byTitle["Daftar album"] as DiscoverySection.Albums).items
        val albumNoArtist = albumRows.first { it.title == "Album Baris Tanpa Artis" }
        assertEquals("", albumNoArtist.artist)
        assertEquals("2026", albumNoArtist.year)
        val albumNormal = albumRows.first { it.title == "Album Baris Normal" }
        assertEquals("Tulus", albumNormal.artist)
        assertEquals("2026", albumNormal.year)

        val songRows = (byTitle["Daftar lagu"] as DiscoverySection.Songs).items
        val songDur = songRows.first { it.videoId == "ROWSONG01AA" }
        assertEquals("", songDur.artist)
        assertEquals("3.33", songDur.duration)
        assertEquals(213, songDur.durationSeconds)
        assertEquals("Tulus", songRows.first { it.videoId == "ROWSONG02BB" }.artist)

        val plRows = (byTitle["Daftar playlist"] as DiscoverySection.Playlists).items
        assertEquals("", plRows.first { it.title == "Playlist Baris Durasi" }.author)
        assertEquals("YouTube Music", plRows.first { it.title == "Playlist Baris Normal" }.author)

        for (s in sections.filterIsInstance<DiscoverySection.Songs>().flatMap { it.items }) {
            assertEquals(null, parseDurationText(s.artist), "${s.title}: artis tidak boleh berupa durasi")
        }
        for (a in sections.filterIsInstance<DiscoverySection.Albums>().flatMap { it.items }) {
            assertEquals(null, parseDurationText(a.artist), "${a.title}: artis tidak boleh berupa durasi")
            assertTrue(a.artist != "2014" && a.artist != "2026", a.title)
        }
    }

    @Test fun `respons kosong atau sampah menghasilkan seksi kosong bukan throw`() {
        assertTrue(parseHome(FixtureHelper.json("next-empty")).isEmpty())
        assertTrue(parseCharts(FixtureHelper.json("next-empty")).isEmpty())
        assertTrue(parseMoodCategories(FixtureHelper.json("next-empty")).isEmpty())
    }
}
