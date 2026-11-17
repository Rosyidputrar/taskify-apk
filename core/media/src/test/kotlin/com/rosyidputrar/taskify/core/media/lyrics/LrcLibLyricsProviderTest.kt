package com.rosyidputrar.taskify.core.media.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * LRCLib via MockEngine — aturan Worker (`worker/synced-lyrics.ts`):
 * `/api/get` dulu, lalu `/api/search` bila perlu; toleransi durasi
 * 10 dtk (`get`) / 15 dtk (`search`); kegagalan hulu -> null, bukan throw.
 */
class LrcLibLyricsProviderTest {

    companion object {
        const val SYNCED = "[00:00.00]Look at the stars\n[00:05.00]Look how they shine for you"
        const val PLAIN = "Look at the stars\nLook how they shine for you"
    }

    private fun trackJson(
        trackName: String = "Yellow",
        artistName: String = "Coldplay",
        duration: Double? = 266.0,
        synced: String? = SYNCED,
        plain: String? = PLAIN,
        instrumental: Boolean = false,
    ): String = buildJsonObject {
        put("trackName", trackName)
        put("artistName", artistName)
        put("albumName", "Parachutes")
        duration?.let { put("duration", it) }
        put("instrumental", instrumental)
        plain?.let { put("plainLyrics", it) }
        synced?.let { put("syncedLyrics", it) }
    }.toString()

    private fun searchJson(vararg tracks: String): String =
        "[${tracks.joinToString(",")}]"

    private fun provider(
        getStatus: HttpStatusCode = HttpStatusCode.NotFound,
        getBody: String = "{}",
        searchStatus: HttpStatusCode = HttpStatusCode.NotFound,
        searchBody: String = "[]",
        requested: MutableList<String>? = null,
    ): LrcLibLyricsProvider {
        val engine = MockEngine { request ->
            requested?.add(request.url.encodedPath)
            when {
                request.url.encodedPath.endsWith("/api/get") ->
                    respond(getBody, getStatus, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                request.url.encodedPath.endsWith("/api/search") ->
                    respond(searchBody, searchStatus, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                else -> respond("{}", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
            }
        }
        return LrcLibLyricsProvider(HttpClient(engine) { expectSuccess = false })
    }

    @Test fun `get sukses synced terisi`() = runTest {
        val p = provider(getStatus = HttpStatusCode.OK, getBody = trackJson())
        val result = p.fetchLyrics("Yellow", "Coldplay", "Parachutes", 266)
        assertTrue(result != null)
        assertEquals(2, result.synced?.size)
        assertEquals(0.0, result.synced?.first()?.t)
        assertEquals("Look at the stars", result.synced?.first()?.text)
        assertEquals(5.0, result.synced?.last()?.t)
        assertEquals(PLAIN, result.plain)
        assertFalse(result.instrumental)
        val text = p.getLyrics("x", "Yellow", "Coldplay", "Parachutes", 266).getOrThrow()
        assertTrue(text.contains("[00:00.000]Look at the stars"))
    }

    @Test fun `get kosong lalu search`() = runTest {
        val requested = mutableListOf<String>()
        val search = searchJson(
            trackJson(duration = 260.0),
            trackJson(duration = 266.0),
        )
        val p = provider(
            getStatus = HttpStatusCode.NotFound,
            searchStatus = HttpStatusCode.OK,
            searchBody = search,
            requested = requested,
        )
        val result = p.fetchLyrics("Yellow", "Coldplay", null, 266)
        assertTrue(result != null)
        assertEquals(2, result.synced?.size)
        assertEquals(listOf("/api/get", "/api/search"), requested)
    }

    @Test fun `search kosong lalu null`() = runTest {
        val p = provider(
            getStatus = HttpStatusCode.NotFound,
            searchStatus = HttpStatusCode.OK,
            searchBody = "[]",
        )
        assertNull(p.fetchLyrics("Yellow", "Coldplay", null, 266))
        assertTrue(p.getLyrics("x", "Yellow", "Coldplay", null, 266).isFailure)
    }

    @Test fun `instrumental synced kosong`() = runTest {
        val p = provider(
            getStatus = HttpStatusCode.OK,
            getBody = trackJson(synced = null, plain = null, instrumental = true),
        )
        val result = p.fetchLyrics("Yellow", "Coldplay", null, 266)
        assertTrue(result != null)
        assertEquals(emptyList(), result.synced)
        assertTrue(result.instrumental)
        assertEquals("", result.plain)
    }

    @Test fun `get durasi meleset lalu search`() = runTest {
        val requested = mutableListOf<String>()
        val p = provider(
            getStatus = HttpStatusCode.OK,
            getBody = trackJson(duration = 100.0),
            searchStatus = HttpStatusCode.OK,
            searchBody = searchJson(trackJson(duration = 266.0)),
            requested = requested,
        )
        val result = p.fetchLyrics("Yellow", "Coldplay", null, 266)
        assertTrue(result != null)
        assertEquals(2, result.synced?.size)
        assertTrue(requested.contains("/api/search"))
    }

    @Test fun `get tanpa lirik lalu search`() = runTest {
        val requested = mutableListOf<String>()
        val p = provider(
            getStatus = HttpStatusCode.OK,
            getBody = trackJson(synced = null, plain = null),
            searchStatus = HttpStatusCode.OK,
            searchBody = searchJson(trackJson()),
            requested = requested,
        )
        assertTrue(p.fetchLyrics("Yellow", "Coldplay", null, 266) != null)
        assertTrue(requested.contains("/api/search"))
    }

    @Test fun `search kandidat terlalu jauh lalu null`() = runTest {
        val p = provider(
            getStatus = HttpStatusCode.NotFound,
            searchStatus = HttpStatusCode.OK,
            searchBody = searchJson(trackJson(duration = 100.0)),
        )
        assertNull(p.fetchLyrics("Yellow", "Coldplay", null, 266))
    }

    @Test fun `lrclib mati lalu null bukan throw`() = runTest {
        val p = provider(getStatus = HttpStatusCode.InternalServerError)
        assertNull(p.fetchLyrics("Yellow", "Coldplay", null, 266))
        assertTrue(p.getLyrics("x", "Yellow", "Coldplay", null, 266).isFailure)
    }

    @Test fun `hanya teks polos lalu synced null`() = runTest {
        val p = provider(
            getStatus = HttpStatusCode.OK,
            getBody = trackJson(synced = null, plain = PLAIN),
        )
        val result = p.fetchLyrics("Yellow", "Coldplay", null, 266)
        assertTrue(result != null)
        assertNull(result.synced)
        assertEquals(PLAIN, result.plain)
        assertEquals(PLAIN, p.getLyrics("x", "Yellow", "Coldplay", null, 266).getOrThrow())
    }

    @Test fun `judul atau artis kosong lalu null`() = runTest {
        val p = provider(getStatus = HttpStatusCode.OK, getBody = trackJson())
        assertNull(p.fetchLyrics("", "Coldplay", null, 266))
        assertNull(p.fetchLyrics("Yellow", "", null, 266))
    }
}
