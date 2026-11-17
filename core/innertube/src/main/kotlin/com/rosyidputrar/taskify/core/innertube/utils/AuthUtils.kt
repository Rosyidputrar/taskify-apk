/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

// Diadaptasi dari ArchiveTune `core/.../innertube/utils/Utils.kt`
// (v15.1.0, c7f0e24) — hanya fungsi yang dipakai `PlaybackAuthState` yang
// dibawa (sha1, cookie login); helper penyelesaian playlist/library dan
// `parseTime` tidak dibawa karena tidak dipakai modul ini.
// Lihat licenses/ARCHIVETUNE.md.

package com.rosyidputrar.taskify.core.innertube.utils

import java.security.MessageDigest

fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

fun sha1(str: String): String = MessageDigest.getInstance("SHA-1").digest(str.toByteArray()).toHex()

fun parseCookieString(cookie: String): Map<String, String> =
    cookie
        .split(";")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull { part ->
            val splitIndex = part.indexOf('=')
            if (splitIndex == -1) {
                null
            } else {
                val key = part.substring(0, splitIndex).trim()
                if (key.isEmpty()) null else key to part.substring(splitIndex + 1).trim()
            }
        }.toMap()

fun hasYouTubeLoginCookie(cookie: String?): Boolean = youtubeLoginCookieValue(cookie) != null

fun hasCompleteYouTubeLoginCookies(cookie: String?): Boolean {
    val cookieMap = cookie?.let(::parseCookieString).orEmpty()
    return !cookieMap["LOGIN_INFO"].isNullOrBlank() &&
        YOUTUBE_LOGIN_COOKIE_NAMES.any { cookieName -> !cookieMap[cookieName].isNullOrBlank() }
}

fun youtubeLoginCookieValue(cookie: String?): String? {
    val cookieMap = cookie?.let(::parseCookieString).orEmpty()
    return YOUTUBE_LOGIN_COOKIE_NAMES.firstNotNullOfOrNull { cookieName ->
        cookieMap[cookieName]?.takeIf(String::isNotBlank)
    }
}

/**
 * True bila cookie berisi SATU SET SESI PENUH (PLAN §A/§B): `LOGIN_INFO`,
 * minimal satu SID (`SID` / `__Secure-1PSID` / `__Secure-3PSID`), `HSID`,
 * `SSID`, dan minimal satu APISID (`APISID` / `SAPISID` /
 * `__Secure-1PAPISID` / `__Secure-3PAPISID`).
 *
 * Gate lama [hasCompleteYouTubeLoginCookies] hanya menuntut `LOGIN_INFO` +
 * satu PAPISID, terlalu lemah: tes HP 18 Nov 01:54 membuktikan sesi
 * parsial itu ditolak server (`"logged_in":"0"`, `"yt_li":"0"` di
 * `account_menu`) padahal WebView login berhasil. Gate ini sengaja lebih
 * ketat dari ArchiveTune, yang gate-nya identik dengan gate lama.
 * `__Secure-1PSIDTS` / `__Secure-3PSIDTS` (timestamp) tidak dituntut.
 * Nilai kosong/diisi spasi dianggap tidak ada.
 */
fun hasFullYouTubeLoginSession(cookie: String?): Boolean {
    val cookieMap = cookie?.let(::parseCookieString).orEmpty()
    return !cookieMap["LOGIN_INFO"].isNullOrBlank() &&
        FULL_SESSION_SID_COOKIE_NAMES.any { cookieName -> !cookieMap[cookieName].isNullOrBlank() } &&
        !cookieMap["HSID"].isNullOrBlank() &&
        !cookieMap["SSID"].isNullOrBlank() &&
        FULL_SESSION_APISID_COOKIE_NAMES.any { cookieName -> !cookieMap[cookieName].isNullOrBlank() }
}

/**
 * Daftar NAMA cookie dari string cookie, diurutkan alfabetis, tanpa
 * duplikat, TANPA nilai. Dipakai untuk diagnostik (rawDetail/log) supaya
 * nama cookie bisa dicatat tanpa pernah membocorkan nilainya.
 * Null/kosong → list kosong.
 */
fun cookieNames(cookie: String?): List<String> =
    cookie?.let(::parseCookieString)?.keys?.sorted().orEmpty()

private val YOUTUBE_LOGIN_COOKIE_NAMES =
    listOf(
        "SAPISID",
        "__Secure-3PAPISID",
        "__Secure-1PAPISID",
    )

/** Varian SID yang diterima sebagai bukti sesi penuh (PLAN §C.2 butir 2). */
private val FULL_SESSION_SID_COOKIE_NAMES =
    listOf(
        "SID",
        "__Secure-1PSID",
        "__Secure-3PSID",
    )

/** Varian APISID yang diterima sebagai bukti sesi penuh (PLAN §C.2 butir 5). */
private val FULL_SESSION_APISID_COOKIE_NAMES =
    listOf(
        "APISID",
        "SAPISID",
        "__Secure-1PAPISID",
        "__Secure-3PAPISID",
    )
