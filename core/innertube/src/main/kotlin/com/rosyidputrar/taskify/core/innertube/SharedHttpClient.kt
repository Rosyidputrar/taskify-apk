package com.rosyidputrar.taskify.core.innertube

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Satu `OkHttpClient` bersama untuk SEMUA jalur jaringan musik — request
 * `player` InnerTube (lewat Ktor, lihat [InnerTube.createDefaultClient])
 * dan request media ExoPlayer (lewat `OkHttpDataSource`, lihat
 * `MediaRequestHeaders.createDataSourceFactory` di `:core:media`).
 *
 * Latar: pemutaran 403 googlevideo 18 Nov 2026 (videoId `BLQPNaBklGA`).
 * Sebelumnya kedua jalur memakai stack HTTP yang berbeda total — Ktor +
 * OkHttp untuk `player`, `DefaultHttpDataSource` (`HttpURLConnection`)
 * untuk media — tanpa satu pun konfigurasi jaringan bersama, sehingga
 * tidak ada yang menjamin request `player` (yang mengikat URL ke IP sumber
 * lewat param `ip`) dan request media keluar lewat alamat/keluarga IP yang
 * sama di ponsel dual-stack + CGNAT. Satu klien bersama menutup celah itu:
 * DNS, connection pool, dan socket factory yang sama untuk kedua jalur.
 *
 * Mekanisme berbagi (bukan asumsi): Ktor `OkHttp` engine memakai
 * `preconfigured.newBuilder()` (terverifikasi dari bytecode
 * `ktor-client-okhttp` 3.6.1), dan `OkHttpClient.newBuilder()` berbagi
 * `connectionPool`, `dispatcher`, `dns`, dan `socketFactory` dengan klien
 * asal — jadi klien turunan Ktor dan klien media ini benar-benar satu pool.
 * `ExoPlayer` memakai klien ini langsung (tanpa turunan).
 *
 * Timeout disamakan dengan `InnerTube` (`HttpTimeout` 15/10/15 dtk):
 * connect 10 dtk, read/write 15 dtk. Tanpa proxy/DNS kustom — sama seperti
 * sebelumnya di kedua jalur (ArchiveTune juga jalan tanpa pemaksaan IP;
 * pemaksaan IPv4 sengaja TIDAK dilakukan di sini, lihat PLAN §E).
 */
object SharedHttpClient {
    /** Satu instance untuk seluruh proses; jangan buat `OkHttpClient` baru untuk musik. */
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
