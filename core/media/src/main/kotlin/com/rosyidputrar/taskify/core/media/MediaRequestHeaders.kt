package com.rosyidputrar.taskify.core.media

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.rosyidputrar.taskify.core.innertube.SharedHttpClient
import com.rosyidputrar.taskify.core.innertube.models.MyTubeClient
import com.rosyidputrar.taskify.core.media.mytube.streamRequestHeaders
import com.rosyidputrar.taskify.core.media.mytube.urlQueryParam
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap

/**
 * Header HTTP untuk permintaan media ExoPlayer ke `*.googlevideo.com`,
 * disamakan dengan ArchiveTune (`morideobfuscator/javascript/src/index.js`,
 * header media request): `Origin` + `Referer` + `User-Agent` web + `Accept`.
 *
 * Sejak porting penuh ArchiveTune (Tahap 1, PLAN.md §C.1) header TIDAK
 * lagi satu nilai tetap untuk semua URL: [headersForUrl] menurunkan
 * User-Agent/Origin/Referer dari param `c` di URL stream itu sendiri
 * (ArchiveTune `StreamClientUtils.resolveRequestProfile`). URL `c=ANDROID`
 * mendapat UA aplikasi `com.google.android.youtube/... gzip` TANPA
 * Origin/Referer; URL `c=WEB_REMIX` mendapat Firefox + Origin
 * `music.youtube.com`; URL `c=WEB_CREATOR`/`WEB` mendapat Chrome 141 +
 * Origin `www.youtube.com`. [DEFAULT] dipertahankan sebagai fallback untuk
 * URL tanpa param `c` (mis. siaran langsung) dan untuk kompatibilitas tes.
 *
 * TANPA cookie: ArchiveTune membuktikan cookie media tidak wajib (URL
 * di-resolve ber-cookie, diambil tanpa cookie, tetap jalan). Jangan tambah
 * cookie di sini — itu permukaan baru tanpa bukti.
 *
 * Transport media = OkHttp, klien yang SAMA dengan InnerTube (perbaikan
 * 403 ikatan IP, 18 Nov 2026): factory memakai [SharedHttpClient.client]
 * — klien yang sama dengan yang dipakai engine Ktor `InnerTube` — lewat
 * [OkHttpDataSource.Factory]. Sebelumnya media memakai
 * `DefaultHttpDataSource` (`HttpURLConnection`), stack yang berbeda total
 * dari Ktor/OkHttp: DNS, connection pool, dan keluarga IP sumber bisa
 * berbeda antara request `player` (yang mengikat URL ke IP lewat param
 * `ip`) dan request media, sehingga googlevideo menolak dengan 403 di
 * ponsel dual-stack + CGNAT. Satu klien bersama menutup celah itu (pola
 * ArchiveTune, yang memakai `OkHttpDataSource` untuk media).
 *
 * Request pertama SELALU membawa `Range` berbatas (ArchiveTune
 * `ChunkedDataSource`, chunk sadar-bitrate ~6 detik media — INVESTIGASI-A
 * §7: 8 MB buta ditolak server karena ujung Range-nya melewati jendela
 * throttle ~60 detik dari posisi putar): [createResolvingDataSourceFactory]
 * membungkus OkHttpDataSource dengan [ChunkedDataSource] di dalam
 * [ResolvingDataSource] yang mengisi `length` DataSpec dari
 * `contentLength` hasil resolve ([ResolvedStreamRegistry]) dan header
 * per-URL. Tanpa length, `ProgressiveMediaPeriod` membuka DataSpec dengan
 * length UNSET → `HttpUtil.buildRangeRequestHeader(0, -1)` null → GET
 * penuh tanpa `Range` → googlevideo 403 0 byte pada URL ANDROID
 * (INVESTIGASI-B §2.5, terbukti dari bytecode media3 1.11.1).
 */
object MediaRequestHeaders {
    const val ORIGIN = "https://music.youtube.com"
    const val REFERER = "https://music.youtube.com/"

    /** Batas atas byte per chunk media ArchiveTune (`MusicService.CHUNK_LENGTH`): 8 MB.
     * Chunk efektif untuk googlevideo lebih kecil dan sadar-bitrate
     * (~6 detik media dari `clen`/`dur` URL — lihat
     * [resolveBitrateAwareChunkLength], INVESTIGASI-A §7); 8 MB di sini
     * hanya batas atas yang tidak pernah mengikat untuk audio. */
    const val CHUNK_LENGTH = 8 * 1024 * 1024L

    /** Header media request — fallback untuk URL tanpa param `c`; tanpa `Cookie` (sengaja, lihat KDoc di atas). */
    val DEFAULT: Map<String, String> = mapOf(
        "Origin" to ORIGIN,
        "Referer" to REFERER,
        "User-Agent" to MyTubeClient.USER_AGENT_WEB_REMIX,
        "Accept" to "*/*",
    )

    /** Header media untuk satu URL stream: per-`c` bila ada, [DEFAULT] bila URL tanpa param `c`. */
    fun headersForUrl(url: String): Map<String, String> = streamRequestHeaders(url) ?: DEFAULT

    /**
     * Factory `HttpDataSource` ExoPlayer dengan header di atas, di atas
     * [OkHttpDataSource] dengan [okHttpClient] yang diberikan.
     *
     * Bawaan = [SharedHttpClient.client], klien yang sama dengan engine
     * Ktor `InnerTube` — JANGAN ganti bawaannya dengan `OkHttpClient`
     * baru, karena justru kesamaan klien inilah perbaikannya. Parameter
     * hanya untuk tes (membuktikan factory memakai klien yang diberikan).
     */
    fun createDataSourceFactory(okHttpClient: OkHttpClient = SharedHttpClient.client): HttpDataSource.Factory =
        OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(MyTubeClient.USER_AGENT_WEB_REMIX)
            .setDefaultRequestProperties(DEFAULT)

    /**
     * Factory DataSource pemutar (yang dipakai [MusicPlayer]): lapisan
     * ArchiveTune penuh — [ResolvingDataSource] (isi `length` + header
     * per-URL saat `open()`) → [ChunkedDataSource] (pecah jadi chunk
     * sadar-bitrate ~6 detik media, tiap chunk satu request `Range`
     * berbatas) → [OkHttpDataSource] di atas klien bersama.
     *
     * OkHttp lapis dalam sengaja TANPA `setUserAgent`/default properties:
     * UA/Origin/Referer datang dari DataSpec hasil resolve (per-URL),
     * supaya tidak ada dua nilai UA yang saling menimpa di wire.
     * `contentLength` dibaca dari [ResolvedStreamRegistry] yang diisi
     * pemutar sebelum `setMediaItem`; URL yang tidak terdaftar tetap
     * mendapat header per-`c` tapi tanpa length (perilaku lama).
     */
    fun createResolvingDataSourceFactory(
        okHttpClient: OkHttpClient = SharedHttpClient.client,
    ): DataSource.Factory {
        val httpFactory = OkHttpDataSource.Factory(okHttpClient)
            .setDefaultRequestProperties(mapOf("Accept" to "*/*"))
        val chunkedFactory = DataSource.Factory { ChunkedDataSource(httpFactory.createDataSource(), CHUNK_LENGTH) }
        return ResolvingDataSource.Factory(chunkedFactory, ResolvingDataSource.Resolver { dataSpec ->
            resolvePlaybackDataSpec(dataSpec)
        })
    }

    /**
     * Factory DataSource PERSIS pemutar (yang dipakai [MusicPlayer]): lapisan
     * ArchiveTune penuh di atas [cache] media (lihat
     * [createResolvingDataSourceFactory]) — `CacheDataSource` → Resolving →
     * Chunked → OkHttp. Dipisah dari `MusicPlayer` supaya wire test memakai
     * wiring produksi yang sama persis, bukan salinan yang bisa melenceng.
     *
     * Kunci cache media = identitas isi stabil `"videoId:itag"` bila dikenal
     * (optimasi Agen A, A3 — [ResolvedStreamRegistry.cacheKeyFor]), bukan URL:
     * URL baru tiap resolve membuat kunci URL selalu miss, sehingga lagu yang
     * sama diputar lagi setelah resolve ulang mengunduh ulang semua byte.
     * URL yang tidak dikenal (siaran langsung) tetap dikunci URL-nya.
     */
    fun createPlayerDataSourceFactory(
        cache: Cache,
        okHttpClient: OkHttpClient = SharedHttpClient.client,
    ): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache)
            .setCacheKeyFactory { dataSpec -> ResolvedStreamRegistry.cacheKeyFor(dataSpec.uri.toString()) }
            .setUpstreamDataSourceFactory(createResolvingDataSourceFactory(okHttpClient))

    /**
     * Resolve satu DataSpec media ala ArchiveTune
     * (`MusicService.resolvePlaybackDataSpec`): header dari profil `c` URL
     * + `length` = sisa `contentLength` dari posisi buka. Murni (tanpa
     * jaringan) supaya bisa diuji di JVM.
     *
     * Panjang diambil dari [ResolvedStreamRegistry] dulu; bila URL itu tidak
     * terdaftar (mis. jalur youtubei yang `contentLength`-nya 0 karena JSON
     * `player` tidak membawa `contentLength` — INVESTIGASI-A §3), jatuh ke
     * param `clen` URL itu sendiri (fallback yang sama dengan yang dipakai
     * `DirectStreamResolver` untuk `DirectStream.contentLength`). Tanpa
     * salah satu dari keduanya, `length` tetap UNSET dan `ChunkedDataSource`
     * yang menutupnya dengan cap sintetis untuk host googlevideo.
     */
    fun resolvePlaybackDataSpec(dataSpec: DataSpec): DataSpec {
        val url = dataSpec.uri.toString()
        // Header dari resolver (youtubei.js) lebih utama daripada profil `c`
        // URL; tanpa itu, header profil `c` (Tahap 1).
        val headers = ResolvedStreamRegistry.headers(url) ?: headersForUrl(url)
        val contentLength = ResolvedStreamRegistry.contentLength(url)
            ?: urlQueryParam(url, "clen")?.toLongOrNull()?.takeIf { it > 0 }
        val length = contentLength
            ?.let { it - dataSpec.position }
            ?.takeIf { it > 0 }
            ?: dataSpec.length
        return dataSpec.buildUpon()
            .setHttpRequestHeaders(headers)
            .setLength(length)
            .build()
    }
}

/**
 * `contentLength` hasil resolve per URL stream (in-memory, per sesi).
 * Diisi pemutar sebelum `setMediaItem` supaya [MediaRequestHeaders]
 * bisa mengisi `length` DataSpec — tanpa ini request pertama tidak
 * membawa `Range`. Kunci = URL lengkap; nilai = panjang isi dalam byte.
 * Entri tidak dihapus otomatis (URL berumur ~6 jam; jumlah lagu per sesi
 * kecil) — [clear] dipakai saat pemutar di-`wipe()`.
 */
object ResolvedStreamRegistry {
    private val lengths = ConcurrentHashMap<String, Long>()
    private val headers = ConcurrentHashMap<String, Map<String, String>>()
    /**
     * Identitas isi stabil per URL stream: `"videoId:itag"` (optimasi Agen A,
     * A3). URL baru tiap resolve (`expire`/`sig` baru) membuat kunci cache
     * berbasis URL selalu miss — lagu yang sama diputar lagi setelah resolve
     * ulang mengunduh ulang semua byte. Itag yang sama untuk video yang sama
     * = berkas yang sama, jadi identitas ini dipakai kunci cache media
     * ([cacheKeyFor]) supaya byte yang sudah diunduh dipakai ulang walau
     * URL-nya sudah berganti. Kualitas berbeda → itag berbeda → kunci
     * berbeda; video berbeda → videoId berbeda. Risiko yang diterima:
     * bila YouTube meng-encode ulang itag yang sama untuk video yang sama
     * (tidak pernah teramati untuk video terbit), span cache lama tetap
     * disajikan sampai LRU 100 MB mengusirnya.
     */
    private val identities = ConcurrentHashMap<String, String>()

    fun put(url: String, contentLength: Long) {
        if (url.isNotBlank() && contentLength > 0) lengths[url] = contentLength
    }

    /** Daftarkan identitas isi stabil untuk satu URL (lihat [identities]). */
    fun putIdentity(url: String, videoId: String, itag: Int?) {
        if (url.isNotBlank() && videoId.isNotBlank() && itag != null) identities[url] = "$videoId:$itag"
    }

    /**
     * Kunci cache media untuk satu URL: identitas stabil bila dikenal,
     * URL itu sendiri bila tidak (mis. siaran langsung / URL yang tidak
     * lewat `setMedia`). Dipakai `CacheKeyFactory` di
     * [MediaRequestHeaders.createPlayerDataSourceFactory].
     */
    fun cacheKeyFor(url: String): String = identities[url] ?: url

    /**
     * Daftarkan contentLength + header media dari resolver (Tahap 3):
     * header dari youtubei.js lebih utama daripada profil `c` URL, karena
     * resolver-lah yang tahu konteks (klien/cookie/PoToken) yang menghasilkan
     * URL itu (ala ArchiveTune `resolvePlaybackDataSpec`).
     */
    fun put(url: String, contentLength: Long, requestHeaders: Map<String, String>) {
        put(url, contentLength)
        if (url.isNotBlank() && requestHeaders.isNotEmpty()) headers[url] = requestHeaders
    }

    fun contentLength(url: String): Long? = lengths[url]

    fun headers(url: String): Map<String, String>? = headers[url]

    fun clear() {
        lengths.clear()
        headers.clear()
        identities.clear()
    }
}
