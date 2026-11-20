package com.rosyidputrar.taskify.core.innertube.pages

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Parser pencarian — cermin `parseSearch terhadap respons asli` Worker. */
class SearchPageTest {

    private val videoId11 = Regex("""^[a-zA-Z0-9_-]{11}$""")

    @Test fun `menemukan lagu dengan videoId yang sah`() {
        val r = parseSearch(FixtureHelper.json("search"), "tulus monokrom")
        assertTrue(r.songs.isNotEmpty())
        for (s in r.songs) {
            assertTrue(videoId11.matches(s.videoId), s.videoId)
            assertEquals("https://music.youtube.com/watch?v=${s.videoId}", s.url)
            assertTrue(s.title.isNotEmpty(), s.videoId)
        }
    }

    @Test fun `durationSeconds konsisten dengan duration teks dan Monokrom 398 detik`() {
        val r = parseSearch(FixtureHelper.json("search"), "tulus monokrom")
        assertTrue(r.songs.any { it.durationSeconds != null })
        for (s in r.songs) {
            assertEquals(parseDurationText(s.duration), s.durationSeconds, s.title)
        }
        val monokrom = r.songs.firstOrNull { it.title == "Monokrom" }
        assertEquals(398, monokrom?.durationSeconds)
    }

    @Test fun `menemukan album dan browseId berawalan MPREb`() {
        val r = parseSearch(FixtureHelper.json("search"), "tulus monokrom")
        assertTrue(r.albums.isNotEmpty())
        for (a in r.albums) {
            assertTrue(a.browseId.startsWith("MPREb"), a.browseId)
            assertTrue(a.title.isNotEmpty())
        }
        val albumIds = r.albums.map { it.browseId }.toSet()
        for (s in r.songs) assertTrue(s.videoId !in albumIds)
    }

    @Test fun `hasil teratas terbaca dan thumbnail ada di sebagian lagu`() {
        val r = parseSearch(FixtureHelper.json("search"), "tulus monokrom")
        assertTrue(r.top != null)
        assertTrue(r.songs.any { it.thumbnails.isNotEmpty() })
    }

    @Test fun `cursor gaya lama terbaca dari halaman pencarian berfilter`() {
        // Fixture search polos TIDAK punya continuation (semua hasil muat di
        // satu halaman); yang berfilter lagu punya token gaya lama.
        assertEquals("", searchToken(FixtureHelper.json("search")))
        val songsToken = searchToken(FixtureHelper.json("search-songs"))
        assertTrue(songsToken.isNotEmpty())
    }

    @Test fun `pencarian berfilter lagu menemukan lagu dan token`() {
        val r = parseSearch(FixtureHelper.json("search-songs"), "tulus")
        assertTrue(r.songs.isNotEmpty())
        assertTrue(r.cursor != null)
    }

    @Test fun `klasifikasi filter lain tidak kosong`() {
        val albums = parseSearch(FixtureHelper.json("search-albums"), "tulus")
        assertTrue(albums.albums.isNotEmpty())
        val artists = parseSearch(FixtureHelper.json("search-artists"), "tulus")
        assertTrue(artists.artists.isNotEmpty())
        val playlists = parseSearch(FixtureHelper.json("search-playlists"), "tulus")
        assertTrue(playlists.playlists.isNotEmpty())
        val videos = parseSearch(FixtureHelper.json("search-videos"), "tulus")
        assertTrue(videos.songs.isNotEmpty())
    }

    @Test fun `klasifikasi podcast dan episode`() {
        val podcasts = parseSearch(FixtureHelper.json("search-podcasts"), "podcast")
        assertTrue(podcasts.podcasts.isNotEmpty())
        val episodes = parseSearch(FixtureHelper.json("search-episodes"), "podcast")
        assertTrue(episodes.episodes.isNotEmpty())
        // Episode dipetakan ke Song dengan artist "acara · tanggal".
        for (e in episodes.episodes) {
            assertTrue(e.videoId.isNotEmpty())
            val d = e.durationSeconds
            assertTrue(d == null || d > 0)
        }
        val podcast = parseSearch(FixtureHelper.json("search-podcast"), "podcast")
        assertTrue(podcast.podcasts.isNotEmpty() || podcast.episodes.isNotEmpty())
    }

    @Test fun `parseSearchMore membaca lagu dari bentuk continuationContents`() {
        val page = parseSearchMore(FixtureHelper.json("search-cont"))
        assertTrue(page.songs.size > 10)
        assertTrue(page.songs[0].title.isNotEmpty())
        assertTrue(page.songs.any { it.durationSeconds != null })
        for (s in page.songs) {
            assertEquals(parseDurationText(s.duration), s.durationSeconds, s.title)
        }
    }

    @Test fun `respons kosong menghasilkan hasil kosong bukan throw`() {
        val empty = parseSearch(FixtureHelper.json("next-empty"), "x")
        assertTrue(empty.songs.isEmpty() && empty.albums.isEmpty())
        assertEquals("", searchToken(FixtureHelper.json("next-empty")))
    }

    @Test fun `baris kartu tanpa artis memakai nama artis dari header kartu bukan durasi`() {
        // Regresi temuan Tahap 9 (live 2026-11-17, query "tulus"): baris lagu
        // kartu hasil teratas bersubtitle ['Lagu', ' • ', '3.33'] tanpa artis,
        // parser lama mengambil sub[1] sebagai artis ("3.33"). Fixture ini
        // mereplikasi bentuk itu + rak bawah dengan artis benar.
        val r = parseSearch(FixtureHelper.json("search-card-no-artist"), "tulus")
        assertEquals("Tulus", r.top?.title)
        // Duplikat Teh Hijau (kartu + rak bawah) hanya muncul sekali.
        assertEquals(4, r.songs.size)
        val tehHijau = r.songs.first { it.videoId == "5F28ye50-Kc" }
        assertEquals("Tulus", tehHijau.artist)
        assertEquals("3.33", tehHijau.duration)
        assertEquals(213, tehHijau.durationSeconds)
        assertEquals("Tulus", r.songs.first { it.videoId == "qrnJL6P-QqQ" }.artist)
        assertEquals(235, r.songs.first { it.videoId == "qrnJL6P-QqQ" }.durationSeconds)
        assertEquals("Tulus", r.songs.first { it.videoId == "E7kHvjvU6JY" }.artist)
        assertEquals(242, r.songs.first { it.videoId == "E7kHvjvU6JY" }.durationSeconds)
        assertEquals("Tulus", r.songs.first { it.videoId == "D7qDcurxu_o" }.artist)
        for (s in r.songs) {
            assertEquals(null, parseDurationText(s.artist), "${s.title}: artis tidak boleh berupa durasi")
            assertTrue(s.artist != s.duration, s.title)
        }
    }

    @Test fun `dedup mengisi artis kosong dari rak bawah dan mempertahankan durasi kartu`() {
        // Kartu BUKAN kartu artis (judul = judul video), jadi tidak ada nama
        // artis dari header: baris kartu artisnya kosong, lalu duplikat yang
        // sama di rak bawah membawa artis asli. Hasilnya satu lagu dengan
        // artis benar DAN durasi dari kartu.
        val r = parseSearch(FixtureHelper.json("search-card-video-no-artist"), "tulus")
        assertEquals(1, r.songs.size)
        val song = r.songs.single()
        assertEquals("Tulus", song.artist)
        assertEquals("3.33", song.duration)
        assertEquals(213, song.durationSeconds)
    }
}
