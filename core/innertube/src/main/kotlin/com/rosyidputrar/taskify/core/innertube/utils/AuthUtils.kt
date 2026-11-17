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

private val YOUTUBE_LOGIN_COOKIE_NAMES =
    listOf(
        "SAPISID",
        "__Secure-3PAPISID",
        "__Secure-1PAPISID",
    )
