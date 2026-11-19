/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

// Diadaptasi dari ArchiveTune `app/.../utils/StreamClientUtils.kt`
// (v15.1.0, c7f0e24) — hanya paket yang diubah ke Taskify; logika
// `resolveRequestProfile` / `resolveOriginReferer` / `appendPoToken`
// dipertahankan, dipangkas ke yang dipakai jalur pemutaran Taskify
// (tanpa `Request.Builder`, tanpa `HttpUrl` — parsing query murni JVM
// supaya bisa diuji tanpa Android).
// Lihat licenses/ARCHIVETUNE.md.
//
// Aturan ArchiveTune yang di-porting di sini (INVESTIGASI-A §1):
// identitas media (User-Agent / Origin / Referer) SELALU diturunkan dari
// param `c` di URL stream itu sendiri — tidak pernah satu identitas tetap
// untuk semua URL. Klien native (ANDROID dkk.) TIDAK mendapat
// Origin/Referer; klien web mendapat Origin/Referer asal kliennya.
// Sebelum porting ini Taskify selalu mengirim UA Firefox + Origin
// `music.youtube.com` di atas URL `c=ANDROID` — identitas yang tidak
// konsisten dengan URL, kandidat terkuat 403 googlevideo.

package com.rosyidputrar.taskify.core.media.mytube

import com.rosyidputrar.taskify.core.innertube.models.MyTubeClient
import java.util.Locale

/**
 * Profil header media untuk satu URL stream — hasil
 * [resolveStreamRequestProfile].
 */
data class StreamRequestProfile(
    val requestedClientName: String,
    val requestedClientVersion: String,
    val resolvedClientFamily: String,
    val resolvedClientVersion: String,
    val userAgent: String,
    val origin: String?,
    val referer: String?,
) {
    /** Kunci klien untuk blacklist pemulihan 403 (`FAMILY@version`, huruf besar). */
    val clientKey: String get() = "$resolvedClientFamily@$resolvedClientVersion".uppercase(Locale.US)
}

/**
 * Profil header media dari param `c`/`cver` di [url].
 * `null` bila URL tidak membawa param `c` (mis. siaran langsung) — pemanggil
 * memakai header bawaan ([com.rosyidputrar.taskify.core.media.MediaRequestHeaders.DEFAULT]).
 */
fun resolveStreamRequestProfile(url: String): StreamRequestProfile? {
    val params = urlQueryParams(url)
    val clientParam = params["c"]?.trim().orEmpty()
    if (clientParam.isEmpty()) return null
    val clientVersion = params["cver"]?.trim().orEmpty()
    val client = resolveStreamClient(clientParam, clientVersion)
    val (origin, referer) = resolveOriginReferer(client)
    return StreamRequestProfile(
        requestedClientName = clientParam.ifEmpty { client.clientName },
        requestedClientVersion = clientVersion.ifEmpty { client.clientVersion },
        resolvedClientFamily = client.clientName,
        resolvedClientVersion = client.clientVersion,
        userAgent = client.userAgent,
        origin = origin,
        referer = referer,
    )
}

/** Header media (`User-Agent`/`Origin`/`Referer`/`Accept`) untuk [url], atau `null` bila tanpa param `c`. */
fun streamRequestHeaders(url: String): Map<String, String>? {
    val profile = resolveStreamRequestProfile(url) ?: return null
    val headers = mutableMapOf("User-Agent" to profile.userAgent, "Accept" to "*/*")
    profile.origin?.let { headers["Origin"] = it }
    profile.referer?.let { headers["Referer"] = it }
    return headers
}

/**
 * Tempel PoToken ke URL stream sebagai param `pot` (ArchiveTune
 * `StreamClientUtils.appendPoToken` / `stream-url.js applyGvsPoToken`).
 * Bila URL sudah membawa `pot`, dibiarkan apa adanya. Token TIDAK pernah
 * di-log; fungsi ini hanya menggabungkan string.
 */
fun appendPoTokenToStreamUrl(url: String, poToken: String): String {
    if (urlQueryParams(url).containsKey("pot")) return url
    val separator = if ('?' in url) "&" else "?"
    return "$url${separator}pot=$poToken"
}

/** Kunci klien (`FAMILY@version`) dari param `c`/`cver` URL, atau `null` bila tanpa `c`. */
fun streamUrlClientKey(url: String): String? = resolveStreamRequestProfile(url)?.clientKey

/** Nama-nama parameter query di [url] (huruf kecil dipertahankan apa adanya di map). */
fun urlQueryParams(url: String): Map<String, String> {
    val query = url.substringAfter('?', missingDelimiterValue = "").substringBefore('#')
    if (query.isBlank()) return emptyMap()
    val out = LinkedHashMap<String, String>()
    for (part in query.split('&')) {
        if (part.isEmpty()) continue
        val eq = part.indexOf('=')
        if (eq < 0) out[part] = "" else out[part.substring(0, eq)] = part.substring(eq + 1)
    }
    return out
}

/** Nilai satu parameter query [name] di [url], atau `null` bila tidak ada. */
fun urlQueryParam(url: String, name: String): String? = urlQueryParams(url)[name]

private fun resolveStreamClient(requestedClientName: String, requestedClientVersion: String): MyTubeClient {
    val name = requestedClientName.uppercase(Locale.US)
    return when {
        name == "WEB_REMIX" -> MyTubeClient.WEB_REMIX
        name == "WEB" -> MyTubeClient.WEB
        name == "WEB_CREATOR" -> MyTubeClient.WEB_CREATOR
        name == "MWEB" -> MyTubeClient.MWEB
        name == "WEB_EMBEDDED_PLAYER" || name == "WEB_EMBEDDED" -> MyTubeClient.WEB_EMBEDDED
        name == "TVHTML5" -> if (requestedClientVersion == MyTubeClient.TVHTML5_DOWNGRADED.clientVersion) MyTubeClient.TVHTML5_DOWNGRADED else MyTubeClient.TVHTML5
        name == "TVHTML5_SIMPLY" -> MyTubeClient.TVHTML5_SIMPLY
        name == "TVHTML5_SIMPLY_EMBEDDED_PLAYER" -> MyTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER
        name == "IOS_MUSIC" -> MyTubeClient.IOS_MUSIC
        name.startsWith("IOS") -> if (requestedClientVersion == MyTubeClient.IPADOS.clientVersion) MyTubeClient.IPADOS else MyTubeClient.IOS
        name == "ANDROID_MUSIC" -> MyTubeClient.ANDROID_MUSIC
        name == "ANDROID_TESTSUITE" -> MyTubeClient.ANDROID_TESTSUITE
        name == "ANDROID_UNPLUGGED" -> MyTubeClient.ANDROID_UNPLUGGED
        name.startsWith("ANDROID_CREATOR") -> MyTubeClient.ANDROID_CREATOR
        name.startsWith("ANDROID_VR") -> when (requestedClientVersion) {
            MyTubeClient.ANDROID_VR_NO_AUTH.clientVersion -> MyTubeClient.ANDROID_VR_NO_AUTH
            MyTubeClient.ANDROID_VR_1_65_10.clientVersion -> MyTubeClient.ANDROID_VR_1_65_10
            MyTubeClient.ANDROID_VR_1_61_48.clientVersion -> MyTubeClient.ANDROID_VR_1_61_48
            MyTubeClient.ANDROID_VR_1_43_32.clientVersion -> MyTubeClient.ANDROID_VR_1_43_32
            else -> MyTubeClient.ANDROID_VR_1_65_10
        }
        // Semua ANDROID* lain (termasuk `ANDROID` polos) = profil MOBILE
        // ArchiveTune: UA aplikasi `com.google.android.youtube/... gzip`.
        name.startsWith("ANDROID") -> MyTubeClient.MOBILE
        name.startsWith("VISIONOS") -> MyTubeClient.VISIONOS
        else -> MyTubeClient.ANDROID_VR_1_65_10
    }
}

private fun resolveOriginReferer(client: MyTubeClient): Pair<String?, String?> {
    val name = client.clientName.uppercase(Locale.US)
    return when {
        name == "TVHTML5" || name == "TVHTML5_SIMPLY_EMBEDDED_PLAYER" || name == "TVHTML5_SIMPLY" ->
            MyTubeClient.ORIGIN_MYTUBE to MyTubeClient.REFERER_MYTUBE_TV
        name == "MWEB" -> MyTubeClient.ORIGIN_MYTUBE_MOBILE to MyTubeClient.REFERER_MYTUBE_MOBILE
        name == "WEB_REMIX" -> MyTubeClient.ORIGIN_MYTUBE_MUSIC to MyTubeClient.REFERER_MYTUBE_MUSIC
        name == "WEB" || name == "WEB_CREATOR" || name == "WEB_EMBEDDED_PLAYER" ->
            MyTubeClient.ORIGIN_MYTUBE to MyTubeClient.REFERER_MYTUBE
        else -> null to null
    }
}
