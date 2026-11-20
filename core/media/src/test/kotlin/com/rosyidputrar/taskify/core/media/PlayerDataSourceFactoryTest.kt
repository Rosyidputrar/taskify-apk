package com.rosyidputrar.taskify.core.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlin.math.ceil
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Wire test factory PERSIS pemutar (INVESTIGASI-A §5/§7): `CacheDataSource` +
 * `SimpleCache` sungguhan → `ResolvingDataSource` → `ChunkedDataSource`
 * sadar-bitrate (~6 detik media) → `OkHttpDataSource` di atas OkHttpClient
 * interceptor yang meniru googlevideo: tanpa `Range` → 403 0 byte
 * (INVESTIGASI-B C6a), `Range` berbatas → 206, `Range` di luar akhir → 416.
 *
 * Dengan [GoogleVideoLikeClient.enforceThrottle] server tiruan juga meniru
 * throttle 60-detik yang terukur dari VM (INVESTIGASI-B §0): Range yang
 * ujungnya melewati posisi-putar + 60 detik media → 403. Posisi putar
 * dimajukan manual oleh tes ([GoogleVideoLikeClient.playheadSeconds])
 * meniru waktu putar nyata — sengaja TIDAK otomatis mengikuti byte yang
 * terbaca, supaya tes tetap sensitif terhadap ukuran chunk (chunk 30 detik
 * harus gagal di steady state, lihat tes throttle steady-state).
 *
 * Tes-tes lama hanya memakai `createResolvingDataSourceFactory` TANPA
 * `CacheDataSource`, sehingga tidak bisa membuktikan apa yang benar-benar
 * dikirim pemutar. Tes di sini memakai
 * [MediaRequestHeaders.createPlayerDataSourceFactory] — wiring yang sama
 * dengan `MusicPlayer.player` — supaya tidak ada salinan yang melenceng.
 */
@RunWith(AndroidJUnit4::class)
class PlayerDataSourceFactoryTest {

    /** Server googlevideo tiruan: mencatat request, melayani byte sintetis. */
    private class GoogleVideoLikeClient(
        val totalBytes: Int,
        val sendContentRange: Boolean = true,
        val enforceThrottle: Boolean = false,
    ) {
        val requests = mutableListOf<Request>()

        /** Posisi putar (detik media) untuk aturan throttle; tes yang memajukan. */
        var playheadSeconds: Double = 0.0

        /** Jendela throttle terukur (INVESTIGASI-B §0): ~60 detik media. */
        val throttleWindowSeconds: Double = 60.0

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                requests += request
                val host = request.url.host
                val range = request.header("Range")
                if (!host.endsWith("googlevideo.com")) {
                    // Bukan googlevideo (mis. siaran langsung): layani penuh 200.
                    val body = ByteArray(totalBytes) { 0x41 }
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .header("Content-Length", "$totalBytes")
                        .body(body.toResponseBody("audio/mpeg".toMediaType()))
                        .build()
                } else if (range == null) {
                    // googlevideo tanpa Range → 403 0 byte (INVESTIGASI-B C6a).
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(403)
                        .message("Forbidden")
                        .body(ByteArray(0).toResponseBody("text/plain".toMediaType()))
                        .build()
                } else {
                    val start = range.substringAfter("bytes=").substringBefore("-").toLongOrNull() ?: 0L
                    val endRaw = range.substringAfter("-", "").toLongOrNull()
                    val end = minOf(endRaw ?: (totalBytes - 1).toLong(), (totalBytes - 1).toLong())
                    if (start >= totalBytes) {
                        Response.Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(416)
                            .message("Range Not Satisfiable")
                            .header("Content-Range", "bytes */$totalBytes")
                            .body(ByteArray(0).toResponseBody("text/plain".toMediaType()))
                            .build()
                    } else if (enforceThrottle && isBeyondThrottleWindow(request, endRaw ?: (totalBytes - 1).toLong())) {
                        // Ujung Range melewati posisi-putar + 60 detik media → 403.
                        Response.Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(403)
                            .message("Forbidden")
                            .body(ByteArray(0).toResponseBody("text/plain".toMediaType()))
                            .build()
                    } else {
                        val size = (end - start + 1).toInt()
                        val body = ByteArray(size) { 0x41 }
                        val builder = Response.Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(206)
                            .message("Partial Content")
                            .header("Content-Length", "$size")
                        if (sendContentRange) builder.header("Content-Range", "bytes $start-$end/$totalBytes")
                        builder.body(body.toResponseBody("audio/webm".toMediaType())).build()
                    }
                }
            }
            .build()

        /** Ujung Range (byte) melewati [playheadSeconds] + jendela throttle? */
        private fun isBeyondThrottleWindow(request: Request, requestedEnd: Long): Boolean {
            val clen = request.url.queryParameter("clen")?.toLongOrNull()?.takeIf { it > 0 }
                ?: return false
            val dur = request.url.queryParameter("dur")?.toDoubleOrNull()?.takeIf { it > 0 }
                ?: return false
            val bytesPerSecond = clen / dur
            val endMediaSeconds = (requestedEnd + 1) / bytesPerSecond
            return endMediaSeconds > playheadSeconds + throttleWindowSeconds
        }
    }

    private lateinit var cacheDir: File
    private var cache: SimpleCache? = null

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        ResolvedStreamRegistry.clear()
        cacheDir = File(context().cacheDir, "player-ds-test-${System.nanoTime()}")
        cacheDir.deleteRecursively()
        cache = SimpleCache(
            cacheDir,
            LeastRecentlyUsedCacheEvictor(100L * 1024L * 1024L),
            StandaloneDatabaseProvider(context()),
        )
    }

    @After fun tearDown() {
        runCatching { cache?.release() }
        cache = null
        cacheDir.deleteRecursively()
        ResolvedStreamRegistry.clear()
    }

    private fun factory(client: OkHttpClient): DataSource.Factory =
        MediaRequestHeaders.createPlayerDataSourceFactory(requireNotNull(cache), client)

    /** Buka DataSpec seperti ExoPlayer (posisi 0, length UNSET) lalu baca sampai habis. */
    private fun openAndReadAll(url: String, sourceFactory: DataSource.Factory): Long {
        val source = sourceFactory.createDataSource()
        try {
            source.open(DataSpec(Uri.parse(url)))
            val buf = ByteArray(8192)
            var total = 0L
            while (true) {
                val n = source.read(buf, 0, buf.size)
                if (n == C.RESULT_END_OF_INPUT) break
                total += n
            }
            return total
        } finally {
            source.close()
        }
    }

    /** Buka DataSpec (boleh seek) lalu baca maksimal [maxBytes] byte. */
    private fun openAndReadUpTo(
        url: String,
        sourceFactory: DataSource.Factory,
        maxBytes: Long,
        position: Long = 0L,
        length: Long = C.LENGTH_UNSET.toLong(),
    ): Long {
        val source = sourceFactory.createDataSource()
        try {
            source.open(DataSpec(Uri.parse(url), position, length))
            val buf = ByteArray(8192)
            var total = 0L
            while (total < maxBytes) {
                val n = source.read(buf, 0, minOf(buf.size.toLong(), maxBytes - total).toInt())
                if (n == C.RESULT_END_OF_INPUT) break
                total += n
            }
            return total
        } finally {
            source.close()
        }
    }

    /** Kirim satu request Range mentah lewat klien server tiruan; kembalikan kode HTTP. */
    private fun rawRangeCode(client: OkHttpClient, url: String, range: String): Int {
        val request = Request.Builder().url(url).header("Range", range).build()
        client.newCall(request).execute().use { return it.code }
    }

    /** Detik media yang dicakup ujung Range (inklusif) pada bitrate [bytesPerSecond]. */
    private fun rangeEndMediaSeconds(range: String?, bytesPerSecond: Double): Double {
        val end = range?.substringAfter("-", "")?.toLongOrNull() ?: return Double.MAX_VALUE
        return (end + 1) / bytesPerSecond
    }

    @Test fun `registry terisi request pertama Range 6 detik lewat factory pemutar`() {
        // Video HP sir 7Xqon20TlH4, itag 251: clen 3.371.685 / dur 205,7 dtk
        // ≈ 16.391 B/dtk → chunk 6 detik ≈ 98.347 byte (bukan 8 MB, lihat §7).
        val url = "https://rr1---sn-factory1.googlevideo.com/videoplayback?c=ANDROID&cver=21.26.364&itag=251&clen=3371685&dur=205.7"
        val total = 3_371_685
        val chunk = (3_371_685.0 / 205.7 * 6).toLong()
        ResolvedStreamRegistry.put(url, total.toLong())
        val server = GoogleVideoLikeClient(total)
        val read = openAndReadAll(url, factory(server.client))
        assertEquals(total.toLong(), read)
        assertEquals(ceil(total.toDouble() / chunk).toInt(), server.requests.size)
        assertEquals("bytes=0-${chunk - 1}", server.requests[0].header("Range"), "request pertama = 6 detik media")
        assertEquals("bytes=$chunk-${2 * chunk - 1}", server.requests[1].header("Range"))
        assertEquals("com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip", server.requests[0].header("User-Agent"))
        assertNull(server.requests[0].header("Origin"), "klien native tanpa Origin")
    }

    @Test fun `registry kosong tapi URL membawa clen request pertama tetap Range 6 detik`() {
        // F1 (INVESTIGASI-A §5): jalur youtubei yang contentLength-nya 0 —
        // registry kosong — tetap membawa Range lewat fallback `clen` URL.
        // SEBELUM perbaikan: open() melempar 403 (tanpa Range).
        // Video HP sir BLQPNaBklGA, itag 251: 5.495.333 / 312,5 dtk → chunk ≈ 105.510 byte.
        val url = "https://rr1---sn-factory2.googlevideo.com/videoplayback?c=WEB_CREATOR&cver=1.20260708.06.00&itag=251&clen=5495333&dur=312.5"
        val total = 5_495_333
        val chunk = (5_495_333.0 / 312.5 * 6).toLong()
        val server = GoogleVideoLikeClient(total)
        val read = openAndReadAll(url, factory(server.client))
        assertEquals(total.toLong(), read)
        assertEquals(ceil(total.toDouble() / chunk).toInt(), server.requests.size)
        assertEquals("bytes=0-${chunk - 1}", server.requests[0].header("Range"))
        assertTrue(server.requests[0].header("User-Agent")!!.contains("Chrome/141.0.0.0"))
        assertEquals("https://www.youtube.com", server.requests[0].header("Origin"))
    }

    @Test fun `registry kosong dan URL tanpa clen cap sintetis googlevideo akhir bersih`() {
        // F2 (INVESTIGASI-A §5) + F3 (§7) + C S-13: tanpa registry DAN tanpa `clen`/`dur`,
        // chunk pertama tetap dibatasi (fallback per-itag 251 → 120 KB ≈ 6 detik);
        // total dipelajari dari Content-Range chunk pertama sehingga chunk terakhir
        // eksak (tanpa probe 416). SEBELUM perbaikan: 403 tanpa Range.
        val url = "https://rr1---sn-factory3.googlevideo.com/videoplayback?c=WEB_CREATOR&cver=1.20260708.06.00&itag=251"
        val total = 1_000_000
        val server = GoogleVideoLikeClient(total)
        val read = openAndReadAll(url, factory(server.client))
        assertEquals(total.toLong(), read, "semua byte terbaca, tanpa exception")
        assertEquals(9, server.requests.size, "1.000.000 / 120.000 = 9 chunk; total dipelajari dari Content-Range")
        assertEquals("bytes=0-119999", server.requests[0].header("Range"))
        assertEquals("bytes=120000-239999", server.requests[1].header("Range"))
        assertEquals("bytes=960000-999999", server.requests[8].header("Range"))
    }

    @Test fun `registry kosong dan total kelipatan chunk berakhir tanpa probe ekstra`() {
        // Total pas 2 x 120 KB (fallback per-itag 251, C S-13): sesudah chunk
        // kedua habis, read() berhenti lewat total yang dipelajari — tidak ada request ke-3.
        val url = "https://rr1---sn-factory5.googlevideo.com/videoplayback?c=WEB_CREATOR&cver=1.20260708.06.00&itag=251"
        val total = 2 * 120_000
        val server = GoogleVideoLikeClient(total)
        val read = openAndReadAll(url, factory(server.client))
        assertEquals(total.toLong(), read)
        assertEquals(2, server.requests.size)
        assertEquals("bytes=0-119999", server.requests[0].header("Range"))
        assertEquals("bytes=120000-239999", server.requests[1].header("Range"))
    }

    @Test fun `tanpa Content-Range total kelipatan chunk berakhir lewat 416 bersih`() {
        // Server tanpa Content-Range di 206: total tidak pernah dipelajari;
        // chunk ke-3 di luar akhir (416) tetap berakhir bersih, bukan galat.
        // Ini melatih jalur 416 OkHttpDataSource yang sesungguhnya.
        val url = "https://rr1---sn-factory8.googlevideo.com/videoplayback?c=WEB_CREATOR&cver=1.20260708.06.00&itag=251"
        val total = 2 * 120_000
        val server = GoogleVideoLikeClient(total, sendContentRange = false)
        val read = openAndReadAll(url, factory(server.client))
        assertEquals(total.toLong(), read)
        assertEquals(3, server.requests.size, "2 chunk isi + 1 probe 416 di luar akhir")
        assertEquals("bytes=240000-359999", server.requests[2].header("Range"))
    }

    @Test fun `putar ulang dari cache tanpa request upstream`() {
        // Cache tetap berfungsi (offline / putar ulang): isi cache sekali,
        // lalu buka lagi dari factory yang sama — tidak ada request baru.
        val url = "https://rr1---sn-factory4.googlevideo.com/videoplayback?c=ANDROID&cver=21.26.364&itag=251&clen=100000&dur=1.0"
        val total = 100_000
        ResolvedStreamRegistry.put(url, total.toLong())
        val server = GoogleVideoLikeClient(total)
        val sourceFactory = factory(server.client)
        assertEquals(total.toLong(), openAndReadAll(url, sourceFactory))
        assertEquals(1, server.requests.size)
        assertEquals(total.toLong(), openAndReadAll(url, sourceFactory))
        assertEquals(1, server.requests.size, "putar ulang dilayani cache, tanpa request upstream")
    }

    @Test fun `putar ulang setelah resolve baru pakai cache yang sama`() {
        // Optimasi Agen A (A3): URL baru tiap resolve (`expire`/`sig` baru)
        // membuat kunci cache berbasis URL selalu miss. Kunci stabil
        // `videoId:itag` membuat putar ulang setelah resolve ulang dilayani
        // cache — 0 request upstream (sebelum perbaikan: unduh ulang penuh,
        // 2 request untuk 100.000 byte ini).
        val videoId = "aaaaaaaaaaa"
        val url1 = "https://rr1---sn-cacheA.googlevideo.com/videoplayback?c=WEB_CREATOR&itag=251&clen=100000&dur=10.0&expire=111111&sig=AAAA"
        val url2 = "https://rr1---sn-cacheA.googlevideo.com/videoplayback?c=WEB_CREATOR&itag=251&clen=100000&dur=10.0&expire=222222&sig=BBBB"
        val total = 100_000
        val server = GoogleVideoLikeClient(total)
        val sourceFactory = factory(server.client)
        ResolvedStreamRegistry.put(url1, total.toLong())
        ResolvedStreamRegistry.putIdentity(url1, videoId, 251)
        assertEquals(total.toLong(), openAndReadAll(url1, sourceFactory))
        assertEquals(2, server.requests.size, "100.000 byte / chunk 60.000 = 2 request")
        ResolvedStreamRegistry.put(url2, total.toLong())
        ResolvedStreamRegistry.putIdentity(url2, videoId, 251)
        assertEquals(total.toLong(), openAndReadAll(url2, sourceFactory))
        assertEquals(2, server.requests.size, "URL baru, lagu sama: dilayani cache, tanpa request upstream")
    }

    @Test fun `video dan itag berbeda tidak berbagi cache`() {
        // Kunci stabil harus membedakan video (videoId) dan format (itag):
        // video lain dengan itag sama, dan video sama dengan itag lain, harus
        // tetap mengunduh — bukan menyajikan audio yang salah.
        val url1 = "https://rr1---sn-cacheB.googlevideo.com/videoplayback?c=WEB_CREATOR&itag=251&clen=100000&dur=10.0&expire=111111&sig=AAAA"
        val urlOtherVideo = "https://rr1---sn-cacheB.googlevideo.com/videoplayback?c=WEB_CREATOR&itag=251&clen=100000&dur=10.0&expire=222222&sig=BBBB"
        val urlOtherItag = "https://rr1---sn-cacheB.googlevideo.com/videoplayback?c=WEB_CREATOR&itag=140&clen=100000&dur=10.0&expire=333333&sig=CCCC"
        val total = 100_000
        val server = GoogleVideoLikeClient(total)
        val sourceFactory = factory(server.client)
        ResolvedStreamRegistry.put(url1, total.toLong())
        ResolvedStreamRegistry.putIdentity(url1, "aaaaaaaaaaa", 251)
        assertEquals(total.toLong(), openAndReadAll(url1, sourceFactory))
        assertEquals(2, server.requests.size)
        ResolvedStreamRegistry.put(urlOtherVideo, total.toLong())
        ResolvedStreamRegistry.putIdentity(urlOtherVideo, "bbbbbbbbbbb", 251)
        assertEquals(total.toLong(), openAndReadAll(urlOtherVideo, sourceFactory))
        assertEquals(4, server.requests.size, "video berbeda: cache miss, unduh ulang")
        ResolvedStreamRegistry.put(urlOtherItag, total.toLong())
        ResolvedStreamRegistry.putIdentity(urlOtherItag, "aaaaaaaaaaa", 140)
        assertEquals(total.toLong(), openAndReadAll(urlOtherItag, sourceFactory))
        assertEquals(6, server.requests.size, "itag berbeda: cache miss, unduh ulang")
    }

    @Test fun `URL non-googlevideo tanpa panjang pass-through tanpa Range`() {
        // Siaran langsung (bukan googlevideo): cap sintetis TIDAK berlaku,
        // dibaca penuh 200 seperti sebelumnya.
        val url = "https://radio.example.com/stream"
        val total = 100_000
        val server = GoogleVideoLikeClient(total)
        val read = openAndReadAll(url, factory(server.client))
        assertEquals(total.toLong(), read)
        assertEquals(1, server.requests.size)
        assertNull(server.requests[0].header("Range"))
    }

    @Test fun `resolvePlaybackDataSpec jatuh ke clen bila registry kosong`() {
        val url = "https://rr1---sn-factory6.googlevideo.com/videoplayback?c=WEB_CREATOR&cver=1.20260708.06.00&itag=251&clen=5495333"
        val resolved = MediaRequestHeaders.resolvePlaybackDataSpec(DataSpec(Uri.parse(url)))
        assertEquals(5_495_333L, resolved.length)
        val noClen = "https://rr1---sn-factory7.googlevideo.com/videoplayback?c=WEB_CREATOR&cver=1.20260708.06.00&itag=251"
        assertEquals(C.LENGTH_UNSET.toLong(), MediaRequestHeaders.resolvePlaybackDataSpec(DataSpec(Uri.parse(noClen))).length)
    }

    @Test fun `throttle 60 detik request pertama Range kecil lolos dan Range 8MB ditolak`() {
        // INVESTIGASI-A §7 + INVESTIGASI-B §0: server menolak ujung Range yang
        // melewati posisi-putar + ~60 detik media. Fase isi awal (posisi-putar 0):
        // chunk 6 detik berakhir di ~54 detik → 206 semua; Range 8 MB (ujung
        // ~512 detik) dan Range 0-999999 (ujung ~61 detik) → 403.
        val url = "https://rr1---sn-factory9.googlevideo.com/videoplayback?c=WEB_CREATOR&cver=1.20260708.06.00&itag=251&clen=3371685&dur=205.7"
        val total = 3_371_685
        val bytesPerSecond = 3_371_685.0 / 205.7
        val chunk = (bytesPerSecond * 6).toLong()
        ResolvedStreamRegistry.put(url, total.toLong())
        val server = GoogleVideoLikeClient(total, enforceThrottle = true)
        val sourceFactory = factory(server.client)
        // Baca 800.000 byte ≈ 48,8 detik media (fase isi awal, posisi-putar 0).
        val read = openAndReadUpTo(url, sourceFactory, 800_000L)
        assertEquals(800_000L, read)
        assertEquals(ceil(800_000.0 / chunk).toInt(), server.requests.size)
        assertEquals("bytes=0-${chunk - 1}", server.requests[0].header("Range"))
        for (request in server.requests) {
            assertTrue(
                rangeEndMediaSeconds(request.header("Range"), bytesPerSecond) <= 60.0,
                "ujung Range ${request.header("Range")} melewati jendela 60 detik",
            )
        }
        // Kontrol: server tiruan yang sama menolak Range besar pada URL yang sama.
        val control = GoogleVideoLikeClient(total, enforceThrottle = true)
        assertEquals(403, rawRangeCode(control.client, url, "bytes=0-8388607"), "Range 8 MB (perilaku lama) harus 403")
        assertEquals(403, rawRangeCode(control.client, url, "bytes=0-999999"), "ujung ~61 detik harus 403 (ambang terukur)")
        assertEquals(206, rawRangeCode(control.client, url, "bytes=0-983279"), "ujung ~60 detik harus 206 (ambang terukur)")
    }

    @Test fun `throttle 60 detik chunk steady-state mengikuti posisi putar`() {
        // INVESTIGASI-A §7.2: loader ExoPlayer (buffer default 50 detik) meminta
        // chunk berikutnya dari posisi-putar + ~50 detik. Pada posisi-putar 100
        // detik, chunk berikutnya dimulai dari ~150 detik media: chunk 6 detik
        // berakhir di ~156 detik (≤ 160) → 206, sedangkan chunk 30 detik berakhir
        // di ~180 detik (> 160) → 403. Tes ini membuktikan tes sensitif terhadap
        // ukuran chunk — chunk 30 detik (saran brief awal) HARUS gagal di sini.
        val url = "https://rr1---sn-factory10.googlevideo.com/videoplayback?c=WEB_CREATOR&cver=1.20260708.06.00&itag=251&clen=3371685&dur=205.7"
        val total = 3_371_685
        val bytesPerSecond = 3_371_685.0 / 205.7
        val chunk = (bytesPerSecond * 6).toLong()
        ResolvedStreamRegistry.put(url, total.toLong())
        val server = GoogleVideoLikeClient(total, enforceThrottle = true)
        val sourceFactory = factory(server.client)
        // Fase isi awal: baca 800.000 byte ≈ 48,8 detik media, posisi-putar 0.
        assertEquals(800_000L, openAndReadUpTo(url, sourceFactory, 800_000L))
        val fillRequests = server.requests.size
        // 100 detik putar berlalu; loader lanjut dari ujung buffer (~150 detik media).
        server.playheadSeconds = 100.0
        val seekPosition = (150 * bytesPerSecond).toLong()
        val remaining = total - seekPosition
        val read = openAndReadUpTo(url, sourceFactory, chunk, position = seekPosition, length = remaining.toLong())
        assertEquals(chunk, read)
        assertEquals(fillRequests + 1, server.requests.size)
        val steadyRequest = server.requests.last()
        assertEquals("bytes=$seekPosition-${seekPosition + chunk - 1}", steadyRequest.header("Range"))
        assertTrue(
            rangeEndMediaSeconds(steadyRequest.header("Range"), bytesPerSecond) <= 160.0,
            "chunk steady-state harus berakhir dalam jendela posisi-putar + 60 detik",
        )
        // Kontrol: chunk 30 detik dari posisi yang sama harus 403.
        val thirtySecondEnd = seekPosition + (30 * bytesPerSecond).toLong() - 1
        assertEquals(
            403,
            rawRangeCode(server.client, url, "bytes=$seekPosition-$thirtySecondEnd"),
            "chunk 30 detik di steady state harus 403 (ujung ~180 detik > 160)",
        )
    }
}
