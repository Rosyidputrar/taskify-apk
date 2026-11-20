package com.rosyidputrar.taskify.core.innertube

import com.rosyidputrar.taskify.core.innertube.pages.FixtureHelper
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Fasad `MyTube` ujung-ke-ujung via MockEngine: transport + parser
 * bekerja bersama, tanpa jaringan sungguhan. Satu handler menjawab tiap
 * endpoint dengan fixture Tahap 0 berdasarkan path-nya.
 */
class MyTubeFacadeTest {

    private fun MockRequestHandleScope.respondFixture(name: String) =
        respond(
            content = FixtureHelper.text(name),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )

    private fun myTubeWith(nextStatus: HttpStatusCode = HttpStatusCode.OK): MyTube {
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/next") && nextStatus != HttpStatusCode.OK ->
                    respond(
                        content = """{"error":{"code":403,"message":"Forbidden"}}""",
                        status = nextStatus,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                request.url.encodedPath.endsWith("/search") -> respondFixture("search")
                request.url.encodedPath.endsWith("/browse") -> respondFixture("album")
                request.url.encodedPath.endsWith("/next") -> respondFixture("next")
                request.url.encodedPath.endsWith("/player") -> respondFixture("player")
                request.url.encodedPath.endsWith("/music/get_search_suggestions") -> respondFixture("suggest")
                else -> respond(
                    content = "{}",
                    status = HttpStatusCode.NotFound,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
        }
        // expectSuccess = true seperti tes Tahap 1: status non-2xx melempar
        // (ClientRequestException), jadi jalur fallback 403 di track() teruji.
        return MyTube(InnerTube(HttpClient(engine) { expectSuccess = true }))
    }

    @Test fun `search fasad mengembalikan SearchResults dari fixture`() = runTest {
        val result = myTubeWith().search("tulus monokrom")
        assertTrue(result.isSuccess)
        val r = result.getOrThrow()
        assertTrue(r.songs.isNotEmpty())
        assertEquals(398, r.songs.firstOrNull { it.title == "Monokrom" }?.durationSeconds)
        assertTrue(r.albums.isNotEmpty())
    }

    @Test fun `searchMore fasad memakai continuation`() = runTest {
        // Handler menjawab search apa pun dengan fixture search (halaman
        // pertama); yang diuji di sini adalah fasad memakai jalur continuation
        // tanpa melempar — isi lanjutan diuji di SearchPageTest.
        val result = myTubeWith().searchMore("4qmFsgI...")
        assertTrue(result.isSuccess)
    }

    @Test fun `suggest fasad mengembalikan 10 saran`() = runTest {
        val result = myTubeWith().suggest("tulus")
        assertTrue(result.isSuccess)
        assertEquals(10, result.getOrThrow().size)
    }

    /**
     * Tes track memakai `runBlocking` (waktu nyata), BUKAN `runTest`:
     * `fetchPlayerTrack` memakai `withTimeoutOrNull(5 detik)`; di bawah
     * `runTest`, jam virtual langsung melompat ke batas timeout begitu
     * MockEngine men-suspend, jadi probe player selalu mengembalikan null
     * sebelum permintaan sempat jalan (terbukti di sesi debug: 5 detik
     * maupun 60 detik sama-sama null, tanpa permintaan keluar). Di waktu
     * nyata (produksi dan `runBlocking`) probe itu selesai seketika.
     */
    @Test fun `track fasad menggabungkan next dan loudness player`() = runBlocking {
        val result = myTubeWith().track("1RrF6Ee_io0")
        assertTrue(result.isSuccess)
        val t = result.getOrThrow()
        assertEquals("1RrF6Ee_io0", t.videoId)
        assertEquals("Monokrom", t.song?.title)
        assertEquals(49, t.upNext.size)
        // Loudness dari fixture player (3.7700005) menempel di track next.
        assertEquals(3.77, t.loudnessDb!!, 0.001)
    }

    @Test fun `track fasad jatuh ke metadata player bila next 403`() = runBlocking {
        val result = myTubeWith(nextStatus = HttpStatusCode.Forbidden).track("QqJ-Vp8mvbk")
        assertTrue(result.isSuccess)
        val t = result.getOrThrow()
        assertTrue(t.song?.title?.isNotEmpty() == true)
        assertTrue(t.upNext.isEmpty())
        assertEquals(3.77, t.loudnessDb!!, 0.001)
    }

    @Test fun `radio fasad mengembalikan 50 lagu dari panel next`() = runTest {
        val result = myTubeWith().radio(videoId = "1RrF6Ee_io0")
        assertTrue(result.isSuccess)
        val mix = result.getOrThrow()
        assertEquals(50, mix.songs.size)
        assertEquals("1RrF6Ee_io0", mix.songs[0].videoId)
        assertTrue(mix.cursor != null)
    }

    @Test fun `album fasad mengembalikan koleksi dari fixture`() = runTest {
        val result = myTubeWith().album("MPREb_x")
        assertTrue(result.isSuccess)
        val c = result.getOrThrow()
        assertTrue(c.title.isNotEmpty())
        assertTrue(c.tracks.isNotEmpty())
    }

    @Test fun `lyrics fasad memakai browse dan mengembalikan teks`() = runTest {
        // Handler browse di sini menjawab fixture album; lirik diuji penuh
        // di NextPageTest — yang diuji di sini adalah fasad tidak melempar
        // pada respons browse apa pun.
        val result = myTubeWith().lyrics("MPLYt_xxx")
        assertTrue(result.isSuccess)
    }

    @Test fun `resolveUrl fasad murni tanpa jaringan`() {
        val yt = myTubeWith()
        val link = yt.resolveUrl("https://www.youtube.com/watch?v=1RrF6Ee_io0")
        assertEquals("video", link?.kind)
        assertEquals("1RrF6Ee_io0", link?.videoId)
        assertEquals(null, yt.resolveUrl("https://example.com/x"))
    }
}
