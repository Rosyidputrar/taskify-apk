package com.rosyidputrar.taskify.core.media.lyrics

import com.rosyidputrar.taskify.core.innertube.InnerTube
import com.rosyidputrar.taskify.core.innertube.YouTube
import com.rosyidputrar.taskify.core.media.lyrics.YouTubeFixtures.BROWSE_JSON
import com.rosyidputrar.taskify.core.media.lyrics.YouTubeFixtures.LYRICS_ID
import com.rosyidputrar.taskify.core.media.lyrics.YouTubeFixtures.LYRICS_TEXT
import com.rosyidputrar.taskify.core.media.lyrics.YouTubeFixtures.NEXT_JSON
import com.rosyidputrar.taskify.core.media.lyrics.YouTubeFixtures.TRANSCRIPT_JSON
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Urutan fallback repository (MockEngine): LRCLib -> teks YouTube ->
 * transkrip -> null. LRCLib dan YouTube berbagi satu handler yang
 * merutekan berdasarkan host + path, dengan dua klien (expectSuccess
 * berbeda) seperti pemakaian nyata.
 */
class LyricsRepositoryTest {

    private class Scenario(
        var getStatus: HttpStatusCode = HttpStatusCode.NotFound,
        var getBody: String = "{}",
        var searchStatus: HttpStatusCode = HttpStatusCode.NotFound,
        var searchBody: String = "[]",
        var nextBody: String? = NEXT_JSON,
        var browseBody: String? = BROWSE_JSON,
        var transcriptBody: String? = TRANSCRIPT_JSON,
    ) {
        val requested = mutableListOf<String>()

        fun repository(): LyricsRepository {
            val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            val handler: suspend (String, String) -> Pair<String, HttpStatusCode> = { host, path ->
                requested.add("$host$path")
                when {
                    host == "lrclib.net" && path.endsWith("/api/get") -> getBody to getStatus
                    host == "lrclib.net" && path.endsWith("/api/search") -> searchBody to searchStatus
                    path.endsWith("/next") -> (nextBody ?: "{}") to if (nextBody == null) HttpStatusCode.NotFound else HttpStatusCode.OK
                    path.endsWith("/browse") -> (browseBody ?: "{}") to if (browseBody == null) HttpStatusCode.NotFound else HttpStatusCode.OK
                    path.endsWith("/get_transcript") -> (transcriptBody ?: "{}") to if (transcriptBody == null) HttpStatusCode.NotFound else HttpStatusCode.OK
                    else -> "{}" to HttpStatusCode.NotFound
                }
            }
            val lrcLibEngine = MockEngine { request ->
                val (body, status) = handler(request.url.host, request.url.encodedPath)
                respond(body, status, json)
            }
            val tubeEngine = MockEngine { request ->
                val (body, status) = handler(request.url.host, request.url.encodedPath)
                respond(body, status, json)
            }
            val lrcLib = LrcLibLyricsProvider(HttpClient(lrcLibEngine) { expectSuccess = false })
            val youTube = YouTube(InnerTube(HttpClient(tubeEngine) { expectSuccess = true }))
            return LyricsRepository(lrcLib, youTube)
        }
    }

    @Test fun `lrclib sukses maka youtube tidak dipanggil`() = runTest {
        val scenario = Scenario(
            getStatus = HttpStatusCode.OK,
            getBody = """{"trackName":"Yellow","artistName":"Coldplay","duration":266,"instrumental":false,"plainLyrics":"Look at the stars","syncedLyrics":"[00:00.00]Look at the stars\n[00:05.00]Look how they shine"}""",
        )
        val lyrics = scenario.repository().getLyrics("dQw4w9WgXcQ", "Yellow", "Coldplay", "Parachutes", 266, LYRICS_ID)
        assertTrue(lyrics != null)
        assertEquals("lrclib", lyrics.source)
        assertEquals(2, lyrics.synced?.size)
        assertTrue(scenario.requested.none { it.contains("/browse") || it.contains("/get_transcript") })
    }

    @Test fun `lrclib gagal maka teks youtube dipakai`() = runTest {
        val scenario = Scenario(getStatus = HttpStatusCode.InternalServerError)
        val lyrics = scenario.repository().getLyrics("dQw4w9WgXcQ", "Yellow", "Coldplay", null, 266, LYRICS_ID)
        assertTrue(lyrics != null)
        assertEquals("youtube", lyrics.source)
        assertEquals(null, lyrics.synced)
        assertEquals(LYRICS_TEXT, lyrics.plain)
        assertTrue(scenario.requested.none { it.contains("/get_transcript") })
    }

    @Test fun `lrclib dan teks gagal maka transkrip dipakai`() = runTest {
        val scenario = Scenario(
            getStatus = HttpStatusCode.NotFound,
            searchStatus = HttpStatusCode.OK,
            searchBody = "[]",
            browseBody = """{"contents":{}}""",
        )
        val lyrics = scenario.repository().getLyrics("dQw4w9WgXcQ", "Yellow", "Coldplay", null, 266, LYRICS_ID)
        assertTrue(lyrics != null)
        assertEquals("youtube", lyrics.source)
        assertEquals(2, lyrics.synced?.size)
        assertEquals(5.0, lyrics.synced?.last()?.t)
    }

    @Test fun `semua gagal maka null bukan throw`() = runTest {
        val scenario = Scenario(
            getStatus = HttpStatusCode.NotFound,
            searchStatus = HttpStatusCode.OK,
            searchBody = "[]",
            nextBody = null,
            browseBody = """{"contents":{}}""",
            transcriptBody = null,
        )
        val lyrics = scenario.repository().getLyrics("dQw4w9WgXcQ", "Yellow", "Coldplay", null, 266, LYRICS_ID)
        assertNull(lyrics)
    }

    @Test fun `instrumental dari lrclib maka synced kosong`() = runTest {
        val scenario = Scenario(
            getStatus = HttpStatusCode.OK,
            getBody = """{"trackName":"Yellow","artistName":"Coldplay","duration":266,"instrumental":true}""",
        )
        val lyrics = scenario.repository().getLyrics("dQw4w9WgXcQ", "Yellow", "Coldplay", null, 266, LYRICS_ID)
        assertTrue(lyrics != null)
        assertEquals("lrclib", lyrics.source)
        assertEquals(emptyList(), lyrics.synced)
        assertTrue(lyrics.instrumental)
        assertTrue(scenario.requested.none { it.contains("/browse") })
    }

    @Test fun `tanpa lyricsId maka teks dicari lewat next`() = runTest {
        val scenario = Scenario(getStatus = HttpStatusCode.InternalServerError)
        val lyrics = scenario.repository().getLyrics("dQw4w9WgXcQ", "Yellow", "Coldplay", null, 266, null)
        assertTrue(lyrics != null)
        assertEquals(LYRICS_TEXT, lyrics.plain)
        assertTrue(scenario.requested.any { it.contains("/next") })
        assertTrue(scenario.requested.any { it.contains("/browse") })
    }
}
