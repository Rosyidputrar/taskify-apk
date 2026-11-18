package com.rosyidputrar.taskify.core.media

import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import com.rosyidputrar.taskify.core.innertube.models.YouTubeClient

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

    /** Factory `HttpDataSource` ExoPlayer dengan header di atas. */
    fun createDataSourceFactory(): HttpDataSource.Factory =
        DefaultHttpDataSource.Factory()
            .setUserAgent(YouTubeClient.USER_AGENT_WEB_REMIX)
            .setDefaultRequestProperties(DEFAULT)
}
