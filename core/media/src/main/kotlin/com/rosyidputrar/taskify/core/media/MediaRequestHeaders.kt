package com.rosyidputrar.taskify.core.media

import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.rosyidputrar.taskify.core.innertube.SharedHttpClient
import com.rosyidputrar.taskify.core.innertube.models.YouTubeClient
import okhttp3.OkHttpClient

/**
 * Header HTTP untuk permintaan media ExoPlayer ke `*.googlevideo.com`,
 * disamakan dengan ArchiveTune (`morideobfuscator/javascript/src/index.js`,
 * header media request): `Origin` + `Referer` + `User-Agent` web + `Accept`.
 *
 * Sebelumnya ExoPlayer memakai `DefaultHttpDataSource` bawaan tanpa header
 * apa pun (User-Agent `ExoPlayerLib/...`, tanpa `Origin`/`Referer`) — satu
 * dari sedikit perbedaan nyata dengan ArchiveTune pada jalur yang sama.
 *
 * TANPA cookie: ArchiveTune membuktikan cookie media tidak wajib (URL
 * di-resolve ber-cookie, diambil tanpa cookie, tetap jalan). Jangan tambah
 * cookie di sini — itu permukaan baru tanpa bukti.
 *
 * `User-Agent` memakai konstanta yang sama dengan profil `WEB_REMIX`
 * ([YouTubeClient.USER_AGENT_WEB_REMIX], Firefox 140) — satu sumber
 * kebenaran dengan `ytClient`, bukan salinan string lepas.
 *
 * Transport media = OkHttp, klien yang SAMA dengan InnerTube (perbaikan
 * 403 ikatan IP, 18 Nov 2026): [createDataSourceFactory] memakai
 * [SharedHttpClient.client] — klien yang sama dengan yang dipakai engine
 * Ktor `InnerTube` — lewat [OkHttpDataSource.Factory]. Sebelumnya media
 * memakai `DefaultHttpDataSource` (`HttpURLConnection`), stack yang
 * berbeda total dari Ktor/OkHttp: DNS, connection pool, dan keluarga IP
 * sumber bisa berbeda antara request `player` (yang mengikat URL ke IP
 * lewat param `ip`) dan request media, sehingga googlevideo menolak
 * dengan 403 di ponsel dual-stack + CGNAT. Satu klien bersama menutup
 * celah itu (pola ArchiveTune, yang memakai `OkHttpDataSource` untuk media).
 */
object MediaRequestHeaders {
    const val ORIGIN = "https://music.youtube.com"
    const val REFERER = "https://music.youtube.com/"

    /** Header media request — tanpa `Cookie` (sengaja, lihat KDoc di atas). */
    val DEFAULT: Map<String, String> = mapOf(
        "Origin" to ORIGIN,
        "Referer" to REFERER,
        "User-Agent" to YouTubeClient.USER_AGENT_WEB_REMIX,
        "Accept" to "*/*",
    )

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
            .setUserAgent(YouTubeClient.USER_AGENT_WEB_REMIX)
            .setDefaultRequestProperties(DEFAULT)
}
