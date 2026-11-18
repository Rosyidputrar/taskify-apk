package com.rosyidputrar.taskify.core.media.youtube

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.innertube.InnerTube
import com.rosyidputrar.taskify.core.innertube.models.YouTubeClient
import com.rosyidputrar.taskify.core.media.MediaRequestHeaders
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D3 — Apakah URL stream diubah di jalan dari respons `player` ke request
 * media ExoPlayer? Tes ini MEMBUKTIKAN (bukan mengubah perilaku): output
 * resolver harus byte-identik dengan nilai `url` mentah di respons player,
 * termasuk semua parameter yang ditandatangani (`sig`/`sparams`/`lsig`/
 * `ip`/`expire`). Mengubah satu parameter saja bisa membuat googlevideo
 * menolak dengan 403 — jadi identitas byte adalah prasyarat diagnosis 403.
 *
 * URL fixture dimodelkan dari URL itag 251 ANDROID asli (probe VM 18 Nov
 * 2026, videoId `BLQPNaBklGA`): 42 nama parameter yang sama, nilai fiktif
 * tapi berstruktur sama — `sig`/`lsig` base64url berpading `%3D`, `ip`
 * IPv6 dokumentasi (`2001:db8::1`, titik-dua jadi `%3A`), `mime` dengan
 * `%2F`, daftar `sparams`/`lsparams` dengan `%2C`. Host fiktif
 * (`rr1---sn-xxxx`) seperti tes resolver lain; IP di sini IP dokumentasi
 * TEST-NET, bukan IP asli siapa pun.
 *
 * Konteks: 403 di HP sir (18 Nov 15:54 WIB) ternyata BUKAN karena URL
 * diubah — tes ini lolos — melainkan karena request media PERTAMA ExoPlayer
 * tidak membawa header `Range` (lihat tes terakhir di bawah) sementara
 * googlevideo menolak request tak-berbatas pada URL ANDROID ini (probe VM
 * C/C2/C4/C6: tanpa Range → 403, `bytes=0-` → 403, range kecil dari posisi
 * 0 → 206). Detail: `INVESTIGASI-B.md` di
 * `~/workspace/music-playback-request-compare/`.
 */
@RunWith(AndroidJUnit4::class)
class StreamUrlByteIdentityTest {

    companion object {
        /** URL itag 251 fiktif, bentuk sama seperti URL ANDROID asli. */
        const val RAW_URL_251 =
            "https://rr1---sn-xxxx.googlevideo.com/videoplayback?" +
                "expire=1795014429&ei=AE0s2JYwQkF-abcDEF1234567890abcdefghij&ip=2001%3Adb8%3A%3A1" +
                "&id=o-AC2_FM4fiktif1234567890abcdefghij&itag=251&source=youtube&requiressl=yes" +
                "&xpc=EgVo2xBlBw%3D%3D&pcm2=yes&gcr=id&bui=AbC123&spc=UWF-abcDEF1234567890abcdefghij" +
                "&vprv=1&svpuc=U2FsdGVkX1-abcDEF1234567890&mime=audio%2Fwebm&rms=AU1%2Cau2" +
                "&gir=yes&clen=5495333&dur=312.521&lmt=1720000000000000&mt=1795000000" +
                "&fvip=2&fexp=24012345%2C24012346&c=ANDROID&epbp=ABC123&rqh=ABC123&txp=5535432" +
                "&sparams=expire%2Cei%2Cip%2Cid%2Citag%2Csource%2Crequiressl%2Cxpc%2Cpcm2%2Cgcr%2Cbui%2Cspc%2Cvprv%2Csvpuc%2Cmime%2Crms%2Cgir%2Cclen%2Cdur%2Clmt" +
                "&lsparams=met%2Cmh%2Cmm%2Cmn%2Cms%2Cmv%2Cmvi%2Cpl%2Cinitcwndbps%2Cspc%2Cvprv%2Csvpuc" +
                "&lsig=AG3qM-oB-abcDEF1234567890abcdefghijABCDEFGHIJ0123456789-_abc%3D" +
                "&sig=AE0s2JYwQkF-abcDEF1234567890abcdefghijABCDEFGHIJ0123456789-_abcdefghijABCDEFGHIJ0123456789%3D" +
                "&met=123%2C456&mh=Ab&mm=31%2C29&mn=sn-xxxx%2Csn-yyyy&ms=au%2Crdu&mv=m&mvi=2&pl=24" +
                "&initcwndbps=1250000&keepalive=yes&txp=5535432&cps=264"

        /** 42 nama parameter yang sama seperti URL ANDROID asli (probe VM). */
        val EXPECTED_PARAM_NAMES = setOf(
            "bui", "c", "clen", "cps", "dur", "ei", "epbp", "expire", "fexp", "fvip", "gcr",
            "gir", "id", "initcwndbps", "ip", "itag", "keepalive", "lmt", "lsig", "lsparams",
            "met", "mh", "mime", "mm", "mn", "ms", "mt", "mv", "mvi", "pcm2", "pl",
            "requiressl", "rms", "rqh", "sig", "source", "sparams", "spc", "svpuc", "txp",
            "vprv", "xpc",
        )
    }

    private fun playerOkJson(url: String): String =
        """{"playabilityStatus":{"status":"OK"},"streamingData":{"expiresInSeconds":"21540","formats":[],"adaptiveFormats":[{"itag":251,"mimeType":"audio/webm; codecs=\"opus\"","bitrate":152514,"url":"$url"}]}}"""

    private fun resolverFor(handler: MockRequestHandler): DirectStreamResolver {
        val engine = MockEngine(handler)
        val tube = InnerTube(HttpClient(engine) { expectSuccess = true })
        return DirectStreamResolver(tube, listOf(YouTubeClient.ANDROID))
    }

    private fun MockRequestHandleScope.okJson(body: String) =
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

    @Test fun `resolver mengembalikan URL byte-identik dengan nilai url di respons player`() = runBlocking {
        val resolver = resolverFor({ _ -> okJson(playerOkJson(RAW_URL_251)) })
        val direct = resolver.resolve("BLQPNaBklGA").getOrThrow()
        assertEquals(RAW_URL_251, direct.url, "URL tidak boleh diubah satu byte pun oleh resolver")
        assertEquals(251, direct.itag)
        assertEquals(21540, direct.expiresInSeconds)
        assertEquals("ANDROID", direct.resolvedClient)
    }

    @Test fun `resolver mengembalikan URL byte-identik bila JSON memakai escape unicode u0026`() = runBlocking {
        // Encoder JSON kadang menulis '&' sebagai \u0026; hasil decode harus
        // tetap URL yang sama — itu decode JSON yang benar, bukan modifikasi.
        val escaped = RAW_URL_251.replace("&", "\\u0026")
        val resolver = resolverFor({ _ -> okJson(playerOkJson(escaped)) })
        val direct = resolver.resolve("BLQPNaBklGA").getOrThrow()
        assertEquals(RAW_URL_251, direct.url)
    }

    @Test fun `tidak ada parameter yang ditambah atau dibuang resolver`() = runBlocking {
        val resolver = resolverFor({ _ -> okJson(playerOkJson(RAW_URL_251)) })
        val direct = resolver.resolve("BLQPNaBklGA").getOrThrow()
        assertEquals(EXPECTED_PARAM_NAMES, streamUrlQueryParamNames(direct.url))
        // Nilai parameter yang ditandatangani harus utuh (termasuk padding %3D).
        val params = direct.url.substringAfter('?').split('&')
            .associate { it.substringBefore('=') to it.substringAfter('=', "") }
        assertEquals(EXPECTED_PARAM_NAMES, params.keys)
        assertEquals(
            "AE0s2JYwQkF-abcDEF1234567890abcdefghijABCDEFGHIJ0123456789-_abcdefghijABCDEFGHIJ0123456789%3D",
            params["sig"],
            "padding base64 sig (%3D) harus utuh",
        )
        assertEquals("2001%3Adb8%3A%3A1", params["ip"], "IPv6 ip (%3A) harus utuh")
        assertEquals("1795014429", params["expire"])
        assertEquals("251", params["itag"])
        assertEquals("audio%2Fwebm", params["mime"])
    }

    @Test fun `HttpUrl OkHttp mempertahankan byte URL stream`() {
        // OkHttpDataSource membangun request lewat HttpUrl.parse(uri.toString()).
        // Bila HttpUrl mengkanonikalisasi ulang (%2F -> /, %3D -> =, %3A -> :),
        // byte di wire berubah dan signature bisa patah.
        val parsed = RAW_URL_251.toHttpUrl()
        assertEquals(RAW_URL_251, parsed.toString(), "HttpUrl.parse tidak boleh mengubah byte URL")
        assertEquals("AE0s2JYwQkF-abcDEF1234567890abcdefghijABCDEFGHIJ0123456789-_abcdefghijABCDEFGHIJ0123456789=", parsed.queryParameter("sig"))
        assertEquals("2001:db8::1", parsed.queryParameter("ip"))
        assertEquals("audio/webm", parsed.queryParameter("mime"))
    }

    @Test fun `Uri parse mempertahankan byte URL stream`() {
        // MediaItem.Builder().setUri(String) -> Uri.parse; toString() harus
        // mengembalikan string yang sama persis.
        assertEquals(RAW_URL_251, Uri.parse(RAW_URL_251).toString())
    }

    @Test fun `MediaItem menyimpan Uri byte-identik`() {
        val item = MediaItem.Builder().setMediaId("BLQPNaBklGA").setUri(RAW_URL_251).build()
        assertEquals(RAW_URL_251, item.localConfiguration?.uri.toString())
    }

    @Test fun `request media pertama ExoPlayer memakai URL byte-identik tanpa header Range`() {
        // Inilah bentuk wire yang 403 di HP: DataSpec posisi 0 + panjang
        // UNSET -> HttpUtil.buildRangeRequestHeader mengembalikan null ->
        // OkHttpDataSource TIDAK mengirim header Range sama sekali (GET penuh).
        // Tes ini mengunci bentuk wire itu; perbaikan 403 (chunked Range)
        // harus mengubah perilaku ini secara sadar, bukan kebetulan.
        var capturedUrl: String? = null
        var capturedRange: String? = null
        var capturedOrigin: String? = null
        var capturedReferer: String? = null
        var capturedUa: String? = null
        var capturedAcceptEncoding: String? = null
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                capturedUrl = req.url.toString()
                capturedRange = req.header("Range")
                capturedOrigin = req.header("Origin")
                capturedReferer = req.header("Referer")
                capturedUa = req.header("User-Agent")
                capturedAcceptEncoding = req.header("Accept-Encoding")
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Content-Length", "15")
                    .body("ok-http-dipakai".toResponseBody("audio/webm".toMediaType()))
                    .build()
            }
            .build()
        val source = MediaRequestHeaders.createDataSourceFactory(client).createDataSource()
        try {
            val opened = source.open(DataSpec(Uri.parse(RAW_URL_251)))
            assertTrue(opened > 0, "open harus mengembalikan panjang isi")
        } finally {
            source.close()
        }
        assertEquals(RAW_URL_251, capturedUrl, "URL di wire harus byte-identik dengan URL resolver")
        assertNull(capturedRange, "request media pertama TIDAK membawa header Range (inilah yang 403 di googlevideo)")
        assertEquals("https://music.youtube.com", capturedOrigin)
        assertEquals("https://music.youtube.com/", capturedReferer)
        assertEquals(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0",
            capturedUa,
        )
        assertEquals("identity", capturedAcceptEncoding, "media3 mengirim Accept-Encoding: identity bila gzip tidak diizinkan")
    }
}
