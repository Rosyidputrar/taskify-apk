package com.rosyidputrar.taskify.core.innertube.pages

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Parser next/track/radio/lyrics — cermin tes `parseNext`/`parseRadio` Worker. */
class NextPageTest {

    @Test fun `judul dan videoId terbaca dari respons next`() {
        val t = parseNext(FixtureHelper.json("next"), "QqJ-Vp8mvbk")
        assertEquals("QqJ-Vp8mvbk", t.videoId)
        assertTrue(t.song?.title?.isNotEmpty() == true)
        assertEquals("https://music.youtube.com/watch?v=QqJ-Vp8mvbk", t.song?.url)
    }

    @Test fun `durationSeconds lagu dan antrean konsisten dengan duration`() {
        val t = parseNext(FixtureHelper.json("next"), "QqJ-Vp8mvbk")
        assertEquals(parseDurationText(t.song?.duration ?: ""), t.durationSeconds)
        assertTrue((t.durationSeconds ?: 0) > 0)
        assertTrue(t.upNext.any { it.durationSeconds != null })
        for (s in t.upNext) {
            assertEquals(parseDurationText(s.duration), s.durationSeconds, s.title)
        }
    }

    @Test fun `selalu ada thumbnail, jatuh ke i dot ytimg bila respons tidak memberi`() {
        val t = parseNext(FixtureHelper.json("next"), "QqJ-Vp8mvbk")
        val thumbs = t.song?.thumbnails.orEmpty()
        assertTrue(thumbs.isNotEmpty())
        assertTrue(thumbs[0].url.startsWith("https://"))
    }

    @Test fun `id lirik terbaca bila lagunya punya lirik`() {
        val t = parseNext(FixtureHelper.json("next"), "QqJ-Vp8mvbk")
        assertTrue(t.lyricsId.isEmpty() || t.lyricsId.startsWith("MPLY"))
    }

    @Test fun `antrean berisi videoId sah dan tidak memuat lagu sekarang`() {
        // Fixture next diambil untuk seed 1RrF6Ee_io0 (Monokrom): baris pertama
        // panel adalah lagu itu sendiri, jadi antrean tidak boleh memuatnya.
        val t = parseNext(FixtureHelper.json("next"), "1RrF6Ee_io0")
        assertEquals(49, t.upNext.size)
        assertTrue(t.upNext.none { it.videoId == t.videoId })
        val ids = t.upNext.map { it.videoId }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun `plafon antrean bawaannya 49 lagu`() {
        val t = parseNext(FixtureHelper.json("next"), "QqJ-Vp8mvbk")
        assertEquals(49, t.upNext.size)
        assertEquals(5, parseNext(FixtureHelper.json("next"), "x", 5).upNext.size)
        assertEquals(49, parseNext(FixtureHelper.json("next"), "x", 999).upNext.size)
        assertEquals(1, parseNext(FixtureHelper.json("next"), "x", 0).upNext.size)
        assertEquals(1, parseNext(FixtureHelper.json("next"), "x", -3).upNext.size)
    }

    @Test fun `panel kosong menghasilkan track kosong bukan throw`() {
        val t = parseNext(FixtureHelper.json("next-empty"), "QqJ-Vp8mvbk")
        assertEquals("QqJ-Vp8mvbk", t.videoId)
        assertTrue(t.upNext.isEmpty())
    }

    @Test fun `parseRadio mengembalikan semua baris panel termasuk lagu seed`() {
        val mix = parseRadio(FixtureHelper.json("next"))
        assertEquals(50, mix.songs.size)
        assertEquals("1RrF6Ee_io0", mix.songs[0].videoId)
        assertEquals("Monokrom", mix.songs[0].title)
        assertEquals(50, mix.songs.map { it.videoId }.toSet().size)
        assertTrue(mix.token.length > 20)
        assertTrue(mix.songs.any { it.durationSeconds != null })
        for (s in mix.songs) {
            assertEquals(parseDurationText(s.duration), s.durationSeconds, s.title)
        }
    }

    @Test fun `parseRadio dari playlist saja tidak menaruh seed di posisi pertama`() {
        val mix = parseRadio(FixtureHelper.json("next-radio-plonly"))
        assertEquals(50, mix.songs.size)
        assertTrue(mix.songs[0].videoId != "1RrF6Ee_io0")
        assertEquals("Teh Hijau", mix.songs[0].title)
        assertTrue(mix.token.length > 20)
    }

    @Test fun `seed yang tidak dikenal menghasilkan mix kosong bukan error`() {
        val mix = parseRadio(FixtureHelper.json("next-empty"))
        assertTrue(mix.songs.isEmpty())
        assertEquals("", mix.token)
    }

    @Test fun `parseRadioMore membaca 49 lagu dari continuationContents`() {
        val mix = parseRadioMore(FixtureHelper.json("next-radio-cont"))
        assertEquals(49, mix.songs.size)
        assertEquals(49, mix.songs.map { it.videoId }.toSet().size)
        assertTrue(mix.token.length > 20)
    }

    @Test fun `parseLyrics membaca teks lirik dari fixture`() {
        val text = parseLyrics(FixtureHelper.json("lyrics"))
        assertTrue(text.length > 100, "lirik hanya ${text.length} karakter")
    }

    @Test fun `parseLyrics respons kosong menghasilkan string kosong`() {
        assertEquals("", parseLyrics(FixtureHelper.json("next-empty")))
    }
}
