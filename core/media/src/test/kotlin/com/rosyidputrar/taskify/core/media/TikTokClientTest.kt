package com.rosyidputrar.taskify.core.media

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * C S-11: badan error dibaca dengan batas 256 KiB, bukan `bodyAsText()` tanpa batas.
 *
 * Tes pertama membedakan batas vs tanpa batas: JSON error yang objek `error`-nya
 * baru muncul SETELAH 256 KiB padding harus gagal dibaca (fallback UNKNOWN),
 * karena tanpa batas objek itu akan ter-parse (code X).
 */
class TikTokClientTest {

    private fun client(body: String, status: HttpStatusCode = HttpStatusCode.BadRequest): TikTokClient {
        val engine = MockEngine {
            respond(body, status, headersOf("Content-Type", ContentType.Application.Json.toString()))
        }
        return TikTokClient("https://tt.test", "https://taskify.test", HttpClient(engine) { expectSuccess = false })
    }

    @Test fun errorKecilTetapTerparse() = runBlocking {
        val e = assertFailsWith<TikTokException> {
            client("""{"error":{"code":"ORIGIN_NOT_ALLOWED","message":"asal ditolak"}}""").extract("https://tiktok.com/@x/video/1")
        }
        assertEquals("ORIGIN_NOT_ALLOWED", e.code)
        assertEquals(400, e.status)
        assertTrue(e.message!!.contains("ALLOWED_ORIGINS"), e.message!!)
    }

    @Test fun errorRaksasaDipotongSebelumObjekError() = runBlocking {
        // Padding 300 KiB sebelum objek error: tanpa batas, code X ter-parse;
        // dengan batas 256 KiB, badan terpotong di tengah padding → parse gagal.
        val pad = " ".repeat(300 * 1024)
        val body = """{"pad":"$pad","error":{"code":"X","message":"M"}}"""
        val e = assertFailsWith<TikTokException> {
            client(body).extract("https://tiktok.com/@x/video/1")
        }
        assertEquals("UNKNOWN", e.code, "badan error di atas 256 KiB harus terpotong, bukan dibaca penuh")
        assertEquals(400, e.status)
        assertTrue(e.message!!.contains("HTTP 400"), e.message!!)
    }

    @Test fun errorBukanJsonMemakaiPesanBawaan() = runBlocking {
        val e = assertFailsWith<TikTokException> {
            client("bukan json sama sekali").extract("https://tiktok.com/@x/video/1")
        }
        assertEquals("UNKNOWN", e.code)
        assertTrue(e.message!!.contains("HTTP 400"), e.message!!)
    }
}
