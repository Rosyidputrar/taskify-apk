package com.rosyidputrar.taskify.core.innertube.pages

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Parser respons `player` — cermin `loudnessFromPlayer`/`fetchPlayerTrack` Worker. */
class PlayerPageTest {

    private val videoId = "QqJ-Vp8mvbk"

    @Test fun `loudness dari audioConfig respons player`() {
        val loudness = loudnessFromPlayer(FixtureHelper.json("player"))
        assertNotNull(loudness)
        // Fixture berisi 3.7700005 (artefak float32 dari YouTube); toleransi 0,001.
        assertEquals(3.77, loudness.loudnessDb!!, 0.001)
    }

    @Test fun `loudness fallback ke format pertama bila audioConfig kosong`() {
        val raw = com.rosyidputrar.taskify.core.innertube.InnertubeJson.parseToJsonElement(
            """{"playerConfig":{},"streamingData":{"adaptiveFormats":[{"loudnessDb":-2.5,"mimeType":"audio/webm"}]}}""",
        )
        val loudness = loudnessFromPlayer(raw)
        assertEquals(-2.5, loudness?.loudnessDb)
        assertNull(loudness?.perceptualLoudnessDb)
    }

    @Test fun `tanpa sumber loudness menghasilkan null`() {
        val raw = com.rosyidputrar.taskify.core.innertube.InnertubeJson.parseToJsonElement(
            """{"playerConfig":{},"streamingData":{"adaptiveFormats":[{"mimeType":"audio/webm"}]}}""",
        )
        assertNull(loudnessFromPlayer(raw))
        assertNull(loudnessFromPlayer(null))
    }

    @Test fun `parsePlayerTrack metadata dari videoDetails`() {
        val track = parsePlayerTrack(FixtureHelper.json("player"), videoId)
        assertNotNull(track)
        assertEquals(videoId, track.videoId)
        assertEquals(3.77, track.loudnessDb!!, 0.001)
        val song = track.song
        assertNotNull(song)
        assertTrue(song.title.isNotEmpty())
        assertTrue(song.artist.isNotEmpty())
        assertTrue((song.durationSeconds ?: 0) > 0)
        assertEquals(formatDuration(song.durationSeconds!!), song.duration)
        assertTrue(song.thumbnails.isNotEmpty())
        assertEquals("https://music.youtube.com/watch?v=$videoId", song.url)
    }

    @Test fun `playabilityStatus bukan OK menghasilkan null`() {
        val raw = com.rosyidputrar.taskify.core.innertube.InnertubeJson.parseToJsonElement(
            """{"playabilityStatus":{"status":"LOGIN_REQUIRED","reason":"Masuk dulu"},"videoDetails":{"title":"x"}}""",
        )
        assertNull(parsePlayerTrack(raw, videoId))
        val unplayable = com.rosyidputrar.taskify.core.innertube.InnertubeJson.parseToJsonElement(
            """{"playabilityStatus":{"status":"UNPLAYABLE"}}""",
        )
        assertNull(parsePlayerTrack(unplayable, videoId))
    }

    @Test fun `tanpa videoDetails tetap mengembalikan track dengan song null bila loudness ada`() {
        val raw = com.rosyidputrar.taskify.core.innertube.InnertubeJson.parseToJsonElement(
            """{"playabilityStatus":{"status":"OK"},"playerConfig":{"audioConfig":{"loudnessDb":1.5}}}""",
        )
        val track = parsePlayerTrack(raw, videoId)
        assertNotNull(track)
        assertNull(track.song)
        assertEquals(1.5, track.loudnessDb)
    }
}
