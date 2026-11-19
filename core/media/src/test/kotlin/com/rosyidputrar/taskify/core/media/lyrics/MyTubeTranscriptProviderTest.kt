package com.rosyidputrar.taskify.core.media.lyrics

import com.rosyidputrar.taskify.core.media.lyrics.MyTubeFixtures.TRANSCRIPT_JSON
import com.rosyidputrar.taskify.core.media.lyrics.MyTubeFixtures.VIDEO_ID
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Transkrip `get_transcript`: cue groups -> teks LRC. */
class MyTubeTranscriptProviderTest {

    @Test fun `cue groups menjadi LRC`() = runTest {
        val myTube = myTubeWith { path ->
            when {
                path.endsWith("/get_transcript") -> TRANSCRIPT_JSON to HttpStatusCode.OK
                else -> "{}" to HttpStatusCode.NotFound
            }
        }
        val provider = MyTubeTranscriptProvider(myTube)
        val lrc = provider.getLyrics(VIDEO_ID, "Yellow", "Coldplay", null, 266).getOrThrow()
        assertEquals(
            "[00:00.000]Look at the stars\n[00:05.000]Look how they shine",
            lrc,
        )
        val lines = LrcParser.parseLrc(lrc)
        assertEquals(2, lines.size)
        assertEquals(0.0, lines[0].t)
        assertEquals(5.0, lines[1].t)
        assertEquals("Look how they shine", lines[1].text)
    }

    @Test fun `transkrip kosong lalu gagal`() = runTest {
        val myTube = myTubeWith { path ->
            when {
                path.endsWith("/get_transcript") -> """{"actions":[]}""" to HttpStatusCode.OK
                else -> "{}" to HttpStatusCode.NotFound
            }
        }
        val provider = MyTubeTranscriptProvider(myTube)
        assertTrue(provider.getLyrics(VIDEO_ID, "Yellow", "Coldplay", null, 266).isFailure)
    }

    @Test fun `transkrip 403 lalu gagal bukan throw`() = runTest {
        val myTube = myTubeWith { _ -> "{}" to HttpStatusCode.Forbidden }
        val provider = MyTubeTranscriptProvider(myTube)
        assertTrue(provider.getLyrics(VIDEO_ID, "Yellow", "Coldplay", null, 266).isFailure)
    }
}
