package com.rosyidputrar.taskify.core.innertube

import com.rosyidputrar.taskify.core.innertube.models.YouTubeClient
import com.rosyidputrar.taskify.core.innertube.utils.sha1
import com.rosyidputrar.taskify.core.innertube.utils.youtubeLoginCookieValue
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.defaultRequest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * EKSPERIMEN PENENTU (Agen A, PLAN §B.2) — apakah header `Cookie` +
 * `Authorization: SAPISIDHASH ...` + `X-Goog-PageId` yang dipasang
 * `InnerTube.ytClient` benar-benar keluar dari client secara utuh?
 *
 * Metode: server HTTP mentah di 127.0.0.1 berbasis `java.net.ServerSocket`
 * (java.base saja) yang merekam baris request persis seperti diterima di
 * wire, lalu `InnerTube.accountMenu(WEB_REMIX)` dipanggil dengan cookie
 * dummy sesi penuh. Client yang dipakai adalah salinan VERBATIM
 * `InnerTube.createDefaultClient()` (InnerTube.kt:426-443: OkHttp,
 * expectSuccess, ContentEncoding gzip/deflate, HttpTimeout 15/10/15) —
 * SATU-SATUNYA perbedaan adalah `defaultRequest` diarahkan ke server
 * lokal, bukan `https://music.youtube.com/youtubei/v1/`, supaya request
 * tidak keluar ke internet. BUKAN MockEngine: MockEngine melewati engine
 * asli dan tidak bisa membuktikan apa yang keluar di wire.
 *
 * SEMUA nilai cookie di sini DUMMY (awalan DUMMY_), bukan nilai produksi.
 * Jangan pernah mengganti dengan cookie asli.
 *
 * Dijalankan dengan `runBlocking`, bukan `runTest`: server menunggu di
 * thread terpisah dan `CountDownLatch.await` akan deadlock dengan
 * dispatcher virtual `runTest`.
 *
 * HASIL EKSPERIMEN (18 Nov 2026, Agen A): Cookie + Authorization +
 * X-Goog-PageId + X-Origin + Referer SAMPAI UTUH (assert di bawah lolos
 * untuk semuanya); SATU cacat wire ditemukan: `X-Goog-AuthUser` sampai
 * sebagai SATU header bernilai `"0,0"` karena `ytClient` memasang
 * header itu DUA kali (InnerTube.kt:140 di luar blok login + :151 di
 * dalam blok login). Tes ini saat ini MERAH tepat di assertion
 * `X-Goog-AuthUser` ("0" vs "0,0") — itulah regression test untuk
 * perbaikan Agen B: hapus salah satu append, tes ini harus hijau.
 * Capture mentah: `~/workspace/music-login-cookie-wire/logs/wire-capture-raw.txt`.
 */
class WireHeaderCaptureTest {

    /**
     * Server HTTP/1.1 minimal: terima satu koneksi, baca request head
     * mentah (sampai CRLF CRLF) + body sesuai Content-Length, balas
     * `{"ok":true}`, simpan head mentah untuk diperiksa tes.
     */
    private class RawCaptureServer {
        private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val port: Int = socket.localPort
        private val headRef = AtomicReference<String?>(null)
        private val done = CountDownLatch(1)

        init {
            Thread({
                try {
                    socket.accept().use { client ->
                        handle(client)
                    }
                } catch (_: Exception) {
                    // Socket ditutup dari close(): abaikan.
                } finally {
                    done.countDown()
                }
            }, "wire-capture-server").apply { isDaemon = true }.start()
        }

        private fun handle(client: Socket) {
            val input = client.getInputStream()
            val headBytes = ByteArrayOutputStream()
            val tail = ByteArray(4)
            var tailLen = 0
            // Baca byte demi byte sampai CRLF CRLF (akhir head).
            while (true) {
                val b = input.read()
                if (b == -1) break
                headBytes.write(b)
                tail[tailLen % 4] = b.toByte()
                tailLen++
                if (tailLen >= 4) {
                    val last4 = ByteArray(4) { tail[(tailLen - 4 + it) % 4] }
                    if (last4.contentEquals(byteArrayOf(13, 10, 13, 10))) break
                }
            }
            val head = headBytes.toString(Charsets.ISO_8859_1)
            // Baca body sesuai Content-Length (kalau ada) supaya OkHttp
            // tidak menggantung; isinya tidak dipakai.
            val contentLength = Regex("(?im)^Content-Length:\\s*(\\d+)")
                .find(head)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            var remaining = contentLength
            val buf = ByteArray(4096)
            while (remaining > 0) {
                val read = input.read(buf, 0, minOf(buf.size, remaining))
                if (read == -1) break
                remaining -= read
            }
            headRef.set(head)
            val body = """{"ok":true}""".toByteArray(Charsets.UTF_8)
            val responseHead =
                "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                    "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n"
            client.getOutputStream().apply {
                write(responseHead.toByteArray(Charsets.ISO_8859_1))
                write(body)
                flush()
            }
        }

        fun awaitHead(): String {
            check(done.await(15, TimeUnit.SECONDS)) { "server lokal tidak menerima request" }
            return checkNotNull(headRef.get()) { "head request tidak terekam" }
        }

        fun close() {
            runCatching { socket.close() }
        }
    }

    /**
     * Salinan VERBATIM `InnerTube.createDefaultClient()` (InnerTube.kt:426-443),
     * kecuali URL `defaultRequest` diganti ke server lokal. Bila builder
     * produksi berubah, salinan ini HARUS ikut diubah — lihat komentar di
     * atas kelas. Satu-satunya perbedaan lain: `preconfigured` memakai
     * klien lokal baru (bukan [SharedHttpClient.client]) supaya tes ini
     * tidak menyentuh singleton proses.
     */
    private fun localClient(port: Int): HttpClient =
        HttpClient(OkHttp) {
            expectSuccess = true

            engine {
                preconfigured = OkHttpClient.Builder().build()
            }

            install(ContentEncoding) {
                gzip(0.9F)
                deflate(0.8F)
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }

            defaultRequest {
                url("http://127.0.0.1:$port/youtubei/v1/")
            }
        }

    @Test
    fun accountMenuMengirimCookieDanSapisidhashUtuhKeWire() {
        val server = RawCaptureServer()
        try {
            val tube = InnerTube(localClient(server.port))
            val auth = PlaybackAuthState(
                cookie = DUMMY_COOKIE,
                visitorData = "DUMMY_VISITOR_DATA",
                dataSyncId = "DUMMY_DELEGATED||DUMMY_USER",
            )
            val body = runBlocking {
                withTimeout(30_000) {
                    tube.accountMenu(YouTubeClient.WEB_REMIX, auth)
                }
            }
            assertEquals("""{"ok":true}""", body)

            val head = server.awaitHead()
            // Cetak head mentah ke stdout supaya masuk log Gradle
            // (nilai DUMMY semua, aman).
            println("WIRE-CAPTURE-BEGIN")
            println(head)
            println("WIRE-CAPTURE-END")

            val lines = head.split("\r\n")
            assertTrue(lines[0].startsWith("POST /youtubei/v1/account/account_menu?prettyPrint=false "),
                "request line tak terduga: ${lines[0]}")
            val headers = mutableMapOf<String, MutableList<String>>()
            for (line in lines.drop(1)) {
                if (line.isBlank()) break
                val colon = line.indexOf(':')
                check(colon > 0) { "baris header tanpa ':' : $line" }
                val name = line.substring(0, colon).trim().lowercase()
                val value = line.substring(colon + 1).trim()
                headers.getOrPut(name) { mutableListOf() }.add(value)
            }
            fun single(name: String): String? {
                val values = headers[name]
                check(values == null || values.size == 1) {
                    "header $name muncul ${values?.size} kali: $values"
                }
                return values?.singleOrNull()
            }

            // 1. Cookie: SATU header, nilai PERSIS sama dengan yang dikirim.
            assertEquals(DUMMY_COOKIE, single("cookie"), "header Cookie tidak utuh di wire")

            // 2. Authorization: SAPISIDHASH dari SAPISID (urutan pertama),
            //    origin https://music.youtube.com, timestamp di-embed di header.
            val authorization = single("authorization")
            assertTrue(authorization?.startsWith("SAPISIDHASH ") == true,
                "header Authorization hilang/bukan SAPISIDHASH: $authorization")
            val authValue = checkNotNull(authorization)
            val timestamp = authValue.removePrefix("SAPISIDHASH ").substringBefore("_")
            assertTrue(timestamp.all { it.isDigit() }, "timestamp SAPISIDHASH bukan angka: $timestamp")
            val expectedAuth =
                "SAPISIDHASH ${timestamp}_${sha1("$timestamp DUMMY_SAPISID https://music.youtube.com")}"
            assertEquals(expectedAuth, authorization, "nilai hash SAPISIDHASH salah")

            // 3. Header pendukung login.
            assertEquals("DUMMY_DELEGATED", single("x-goog-pageid"))
            assertEquals("https://music.youtube.com", single("x-origin"))
            assertEquals("https://music.youtube.com/", single("referer"))
            assertEquals("0", single("x-goog-authuser"))
            assertEquals("DUMMY_VISITOR_DATA", single("x-goog-visitor-id"))
            assertEquals("67", single("x-youtube-client-name"))
        } finally {
            server.close()
        }
    }

    @Test
    fun sha1VektorUjiAbc() {
        // Vektor uji SHA-1 yang diketahui (FIPS 180-1).
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", sha1("abc"))
    }

    @Test
    fun youtubeLoginCookieValueMemakaiSapisidBilaAdaDan3papisid() {
        // Bila merged cookie berisi SAPISID DAN __Secure-3PAPISID dengan
        // nilai berbeda, hash harus dihitung dari SAPISID (urutan pertama).
        val cookie = "__Secure-3PAPISID=DUMMY_3PAPISID; SAPISID=DUMMY_SAPISID; " +
            "__Secure-1PAPISID=DUMMY_1PAPISID"
        assertEquals("DUMMY_SAPISID", youtubeLoginCookieValue(cookie))
    }

    companion object {
        /** Cookie dummy sesi penuh (semua nilai DUMMY_, bukan produksi). */
        const val DUMMY_COOKIE =
            "APISID=DUMMY_APISID; HSID=DUMMY_HSID; LOGIN_INFO=DUMMY_LOGIN_INFO; " +
                "SAPISID=DUMMY_SAPISID; SID=DUMMY_SID; SIDCC=DUMMY_SIDCC; " +
                "SSID=DUMMY_SSID; VISITOR_INFO1_LIVE=DUMMY_VISITOR; YSC=DUMMY_YSC; " +
                "__Secure-1PAPISID=DUMMY_1PAPISID; __Secure-1PSID=DUMMY_1PSID; " +
                "__Secure-1PSIDCC=DUMMY_1PSIDCC; __Secure-1PSIDTS=DUMMY_1PSIDTS; " +
                "__Secure-3PAPISID=DUMMY_3PAPISID; __Secure-3PSID=DUMMY_3PSID; " +
                "__Secure-3PSIDCC=DUMMY_3PSIDCC; __Secure-3PSIDTS=DUMMY_3PSIDTS"
    }
}
