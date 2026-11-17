package com.rosyidputrar.taskify.core.innertube.pages

import com.rosyidputrar.taskify.core.domain.media.CollectionKind
import com.rosyidputrar.taskify.core.innertube.InnertubeJson
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Parser album/playlist — cermin tes `parseCollection` Worker. */
class CollectionPageTest {

    @Test fun `rowToSong tidak memakai durasi sebagai artis`() {
        // Pola bug Tahap 9 di jalur trek album/playlist: kolom artis
        // (flexColumns[1]) hanya berisi durasi bila hulu tidak menulis
        // artis — artisnya harus kosong (lalu diisi dari header koleksi
        // oleh collectTracks), bukan "3.33". Durasi tetap terbaca dari
        // fixedColumns.
        val dur = InnertubeJson.parseToJsonElement(
            """{"playlistItemData":{"videoId":"ROWSONG01AA"},
                "flexColumns":[
                  {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Lagu Baris Durasi"}]}}},
                  {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"3.33"}]}}}],
                "fixedColumns":[{"musicResponsiveListItemFixedColumnRenderer":{"text":{"runs":[{"text":"3.33"}]}}}]}""",
        )
        val song = rowToSong(dur)
        assertEquals("", song.artist)
        assertEquals("3.33", song.duration)
        assertEquals(213, song.durationSeconds)
        val normal = InnertubeJson.parseToJsonElement(
            """{"playlistItemData":{"videoId":"ROWSONG02BB"},
                "flexColumns":[
                  {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Lagu Baris Normal"}]}}},
                  {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Tulus"}]}}}],
                "fixedColumns":[{"musicResponsiveListItemFixedColumnRenderer":{"text":{"runs":[{"text":"4.02"}]}}}]}""",
        )
        assertEquals("Tulus", rowToSong(normal).artist)
        assertEquals(242, rowToSong(normal).durationSeconds)
    }

    @Test fun `album judul dan jumlah trek terbaca`() {
        val c = parseCollection(FixtureHelper.json("album"), "MPREb_x", CollectionKind.Album)
        assertTrue(c.title.isNotEmpty())
        assertTrue((c.trackCount ?: 0) > 0)
        assertTrue(c.tracks.isNotEmpty())
        // Subtitle album = "artis · tahun" (nama artis dari straplineTextOne).
        assertTrue(c.subtitle.contains("Tulus"), "subtitle: ${c.subtitle}")
        // `by` album: artis diwariskan ke tiap trek yang tidak punya artis sendiri.
        assertTrue(c.tracks.all { it.title.isNotEmpty() })
    }

    @Test fun `album memakai jumlah treknya sendiri dan durasi konsisten`() {
        val c = parseCollection(FixtureHelper.json("album"), "MPREb_x", CollectionKind.Album)
        assertTrue((c.trackCount ?: 0) >= c.tracks.size)
        assertTrue(c.tracks.any { it.durationSeconds != null })
        for (s in c.tracks) {
            assertEquals(parseDurationText(s.duration), s.durationSeconds, s.title)
        }
    }

    @Test fun `playlist membaca 100 trek dari halaman pertama`() {
        val c = parseCollection(FixtureHelper.json("playlist"), "VLPLx", CollectionKind.Playlist)
        assertEquals(100, c.tracks.size)
        assertTrue(c.title.isNotEmpty())
        assertTrue(c.tracks.all { it.title.isNotEmpty() })
    }

    @Test fun `trackCount playlist memakai jumlah yang diklaim hulu bukan jumlah halaman ini`() {
        val c = parseCollection(FixtureHelper.json("playlist"), "VLPLx", CollectionKind.Playlist)
        assertTrue((c.trackCount ?: 0) > c.tracks.size)
        assertTrue(c.tracks.any { it.durationSeconds != null })
        for (s in c.tracks) {
            assertEquals(parseDurationText(s.duration), s.durationSeconds, s.title)
        }
    }

    @Test fun `penanda lanjutan di akhir daftar tidak ikut jadi lagu`() {
        val c = parseCollection(FixtureHelper.json("playlist"), "VLPLx", CollectionKind.Playlist)
        assertTrue(c.tracks.all { it.videoId.isNotEmpty() })
    }

    @Test fun `kedua gaya token ada di respons playlist dan nilainya berbeda`() {
        val root = FixtureHelper.json("playlist")
        val lama = searchToken(root)
        val baru = collectionToken(root)
        assertTrue(lama.isNotEmpty())
        assertTrue(baru.isNotEmpty())
        assertTrue(baru != lama)
    }

    @Test fun `halaman lanjutan playlist terakhir tidak memberi token lagi`() {
        assertEquals("", collectionToken(FixtureHelper.json("playlist-cont")))
    }

    @Test fun `parseCollectionMore membaca trek dari bentuk onResponseReceivedActions`() {
        val more = parseCollectionMore(FixtureHelper.json("playlist-cont"))
        assertTrue(more.tracks.size > 10)
        assertTrue(more.tracks.all { it.title.isNotEmpty() })
    }

    @Test fun `trek yang benar-benar didapat bisa lebih sedikit dari yang diklaim`() {
        val first = parseCollection(FixtureHelper.json("playlist"), "VLPLx", CollectionKind.Playlist)
        val more = parseCollectionMore(FixtureHelper.json("playlist-cont"))
        val didapat = first.tracks.size + more.tracks.size
        val claimed = first.trackCount ?: 0
        assertTrue(didapat <= claimed)
        assertTrue(claimed - didapat < 10)
        assertTrue(didapat > 130)
    }

    @Test fun `respons tanpa aksi lanjutan menghasilkan daftar kosong`() {
        val more = parseCollectionMore(FixtureHelper.json("next-empty"))
        assertTrue(more.tracks.isEmpty())
        assertEquals("", more.token)
    }

    @Test fun `respons kosong tidak error hanya string kosong`() {
        val empty = FixtureHelper.json("next-empty")
        assertEquals("", searchToken(empty))
        assertEquals("", collectionToken(empty))
    }
}
