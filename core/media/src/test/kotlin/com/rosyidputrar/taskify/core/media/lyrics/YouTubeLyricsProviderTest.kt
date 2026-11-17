package com.rosyidputrar.taskify.core.media.lyrics

import com.rosyidputrar.taskify.core.media.lyrics.YouTubeFixtures.BROWSE_JSON
import com.rosyidputrar.taskify.core.media.lyrics.YouTubeFixtures.LYRICS_TEXT
import com.rosyidputrar.taskify.core.media.lyrics.YouTubeFixtures.NEXT_JSON
import com.rosyidputrar.taskify.core.media.lyrics.YouTubeFixtures.VIDEO_ID
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Lirik teks YouTube via fasad `:core:innertube` (MockEngine). */
class YouTubeLyricsProviderTest {

    @Test fun `lirik terambil dari browse MPLYt via next`() = runTest {
        val requested = mutableListOf<String>()
        val youTube = youtubeWith { path ->
            requested.add(path)
            when {
                path.endsWith("/next") -> NEXT_JSON to HttpStatusCode.OK
                path.endsWith("/browse") -> BROWSE_JSON to HttpStatusCode.OK
                else -> "{}" to HttpStatusCode.NotFound
            }
        }
        val provider = YouTubeLyricsProvider(youTube)
        val text = provider.getLyrics(VIDEO_ID, "Yellow", "Coldplay", null, 266).getOrThrow()
        assertEquals(LYRICS_TEXT, text)
        assertTrue(requested.any { it.endsWith("/next") })
        assertTrue(requested.any { it.endsWith("/browse") })
    }

    @Test fun `getLyricsById langsung browse tanpa next`() = runTest {
        val requested = mutableListOf<String>()
        val youTube = youtubeWith { path ->
            requested.add(path)
            when {
                path.endsWith("/browse") -> BROWSE_JSON to HttpStatusCode.OK
                else -> "{}" to HttpStatusCode.NotFound
            }
        }
        val provider = YouTubeLyricsProvider(youTube)
        assertEquals(LYRICS_TEXT, provider.getLyricsById("MPLYt_abc123").getOrThrow())
        assertTrue(requested.none { it.endsWith("/next") })
    }

    @Test fun `next tanpa lyricsId lalu gagal`() = runTest {
        val youTube = youtubeWith { path ->
            when {
                path.endsWith("/next") -> """{"contents":{}}""" to HttpStatusCode.OK
                else -> "{}" to HttpStatusCode.NotFound
            }
        }
        val provider = YouTubeLyricsProvider(youTube)
        assertTrue(provider.getLyrics(VIDEO_ID, "Yellow", "Coldplay", null, 266).isFailure)
    }

    @Test fun `browse kosong lalu gagal`() = runTest {
        val youTube = youtubeWith { path ->
            when {
                path.endsWith("/browse") -> """{"contents":{}}""" to HttpStatusCode.OK
                else -> "{}" to HttpStatusCode.NotFound
            }
        }
        val provider = YouTubeLyricsProvider(youTube)
        assertTrue(provider.getLyricsById("MPLYt_abc123").isFailure)
    }
}
