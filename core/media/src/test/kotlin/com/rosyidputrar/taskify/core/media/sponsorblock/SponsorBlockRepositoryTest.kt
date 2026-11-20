package com.rosyidputrar.taskify.core.media.sponsorblock

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** SponsorBlock langsung via MockEngine — aturan ArchiveTune + Worker. */
class SponsorBlockRepositoryTest {

    companion object {
        const val VIDEO_ID = "dQw4w9WgXcQ"

        /** Respons bentuk hash-prefix: array video, segmen di dalamnya. */
        const val SEGMENTS_JSON = """[
          {"videoID":"other_video","segments":[{"segment":[1.0,2.0],"category":"sponsor","actionType":"skip"}]},
          {"videoID":"dQw4w9WgXcQ","segments":[
            {"segment":[10.0,25.5],"category":"sponsor","actionType":"skip","UUID":"uuid-1"},
            {"segment":[0.0,5.0],"category":"intro","actionType":"mute","UUID":"uuid-2"},
            {"segment":[30.0,20.0],"category":"sponsor","actionType":"skip"},
            {"segment":[40.0,50.0],"category":"kategori_tak_dikenal","actionType":"skip"}
          ]}
        ]"""

        fun sha256Prefix(videoId: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(videoId.toByteArray(Charsets.UTF_8))
            return digest.take(2).joinToString("") { "%02x".format(it) }
        }
    }

    private fun repository(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = SEGMENTS_JSON,
        requested: MutableList<String>? = null,
    ): SponsorBlockRepository {
        val engine = MockEngine { request ->
            requested?.add(request.url.encodedPath + "?" + request.url.encodedQuery)
            respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        return SponsorBlockRepository(HttpClient(engine) { expectSuccess = false })
    }

    @Test fun `200 berisi segmen dipetakan dan diurutkan`() = runTest {
        val requested = mutableListOf<String>()
        val repo = repository(requested = requested)
        val segments = repo.segments(VIDEO_ID)
        assertEquals(2, segments.size)
        assertEquals("intro", segments[0].category)
        assertEquals(0.0, segments[0].start)
        assertEquals(5.0, segments[0].end)
        assertTrue(segments[0].mute)
        assertEquals("sponsor", segments[1].category)
        assertEquals(10.0, segments[1].start)
        assertEquals(25.5, segments[1].end)
        assertTrue(!segments[1].mute)
        // Hash prefix privasi: videoId tidak dikirim utuh.
        val expectedPrefix = sha256Prefix(VIDEO_ID)
        assertTrue(requested.single().startsWith("/api/skipSegments/$expectedPrefix?"))
        assertTrue(requested.single().contains("categories="))
        assertTrue(requested.single().contains("sponsor"))
    }

    @Test fun `404 menjadi daftar kosong bukan error`() = runTest {
        val repo = repository(status = HttpStatusCode.NotFound, body = "Not Found")
        assertEquals(emptyList(), repo.segments(VIDEO_ID))
    }

    @Test fun `kategori tak dikenal di respons dibuang`() = runTest {
        val repo = repository()
        val segments = repo.segments(VIDEO_ID)
        assertTrue(segments.none { it.category == "kategori_tak_dikenal" })
        assertTrue(segments.none { it.start > it.end })
    }

    @Test fun `kategori tak dikenal di permintaan ditolak`() = runTest {
        val repo = repository()
        val error = assertFailsWith<IllegalArgumentException> {
            repo.segments(VIDEO_ID, listOf("sponsor", "bukan_kategori"))
        }
        assertTrue(error.message!!.contains("bukan_kategori"))
    }

    @Test fun `502 layanan mati dilempar`() = runTest {
        val repo = repository(status = HttpStatusCode.BadGateway, body = "Bad Gateway")
        val error = assertFailsWith<SponsorBlockApiException> { repo.segments(VIDEO_ID) }
        assertEquals(502, error.statusCode)
    }

    @Test fun `cache memori panggilan kedua tanpa jaringan`() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            respond(SEGMENTS_JSON, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        val repo = SponsorBlockRepository(HttpClient(engine) { expectSuccess = false })
        val first = repo.segments(VIDEO_ID)
        val second = repo.segments(VIDEO_ID)
        assertEquals(first, second)
        assertEquals(1, calls)
    }

    @Test fun `404 ikut di-cache`() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            respond("Not Found", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        val repo = SponsorBlockRepository(HttpClient(engine) { expectSuccess = false })
        assertEquals(emptyList(), repo.segments(VIDEO_ID))
        assertEquals(emptyList(), repo.segments(VIDEO_ID))
        assertEquals(1, calls)
    }

    @Test fun `kategori bawaan sama dengan domain`() = runTest {
        val requested = mutableListOf<String>()
        val repo = repository(requested = requested)
        repo.segments(VIDEO_ID)
        val query = requested.single()
        for (category in listOf("sponsor", "intro", "outro", "interaction", "selfpromo", "music_offtopic")) {
            assertTrue(query.contains(category), "kategori bawaan $category tidak diminta: $query")
        }
    }
}
