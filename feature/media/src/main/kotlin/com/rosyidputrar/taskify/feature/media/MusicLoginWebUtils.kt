package com.rosyidputrar.taskify.feature.media

import com.rosyidputrar.taskify.core.innertube.utils.hasFullMyTubeLoginSession
import java.net.URI

/**
 * Helper URL + cookie untuk layar login, JVM-murni (tanpa API Android)
 * supaya bisa dites tanpa WebView — dipisah dari `LoginScreen` ArchiveTune
 * (`MyTubeLoginWebViewClient` + `mergeMyTubeCookies`).
 *
 * Perilaku persis ArchiveTune: cookie digabung dari origin halaman saat ini
 * dulu, lalu `music.youtube.com`, `www.youtube.com`, `youtube.com`; kunci
 * yang sama hanya diambil dari sumber pertama (pertama menang).
 */
val MYTUBE_COOKIE_URLS: List<String> = listOf(
    "https://music.youtube.com",
    "https://www.youtube.com",
    "https://youtube.com",
)

/** Nama paket YouTube Music di Play Store (listing-nya diblokir di WebView). */
const val MYTUBE_MUSIC_PACKAGE_NAME = "com.google.android.apps.youtube.music"

/**
 * Gerbang tangkapan cookie login (JVM-murni, bisa di-unit-test tanpa WebView).
 *
 * Keputusan dedup yang dulu tersembunyi di `MyTubeLoginWebViewClient`:
 * cookie gabungan yang lengkap tapi SAMA dengan tangkapan terakhir tidak
 * diteruskan lagi ke `onCookiesCaptured`. [reset] melupakan tangkapan
 * terakhir supaya cookie yang sama bisa ditangkap lagi — ini yang
 * dipanggil handler "Coba lagi" (PLAN §B.2/§D.1): tanpa reset, retry
 * sesudah login Google berhasil di WebView akan didiamkan selamanya.
 *
 * Syarat lengkap di sini adalah SESI PENUH ([hasFullMyTubeLoginSession],
 * PLAN §C.2/§C.4): `LOGIN_INFO` + satu SID + `HSID` + `SSID` + satu
 * APISID. Gate lama (`hasCompleteMyTubeLoginCookies`, hanya `LOGIN_INFO`
 * + satu PAPISID) TERLALU LEMAH: tes HP 18 Nov 01:54 membuktikan sesi
 * parsial itu ditolak server (`"logged_in":"0"` di `account_menu`)
 * padahal WebView login berhasil, jadi sesi parsial TIDAK BOLEH lagi
 * diteruskan ke verifikasi.
 */
class LoginCookieCaptureGate {
    private var lastCapturedCookie: String? = null

    /** true bila cookie berisi sesi penuh dan belum pernah ditangkap sejak reset terakhir; mencatatnya. */
    fun shouldCapture(cookie: String?): Boolean {
        if (!hasFullMyTubeLoginSession(cookie)) return false
        if (cookie == lastCapturedCookie) return false
        lastCapturedCookie = cookie
        return true
    }

    /** Lupakan tangkapan terakhir supaya cookie yang sama bisa ditangkap lagi. */
    fun reset() {
        lastCapturedCookie = null
    }
}

/**
 * Gabung string cookie (`"k=v; k2=v2"`) dari tiap URL kandidat menjadi satu
 * string, dedup per kunci (pertama menang). null bila tidak ada cookie.
 */
fun mergeMyTubeCookies(
    cookiesByUrl: Map<String, String>,
    currentUrl: String? = null,
): String? {
    val cookieParts = linkedMapOf<String, String>()
    val candidateUrls = linkedSetOf<String>()
    currentUrl.myTubeCookieOrigin()?.let(candidateUrls::add)
    candidateUrls.addAll(MYTUBE_COOKIE_URLS)
    candidateUrls.forEach { url ->
        cookiesByUrl[url]
            ?.split(";")
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.forEach { part ->
                val separatorIndex = part.indexOf('=')
                if (separatorIndex <= 0) return@forEach
                val key = part.substring(0, separatorIndex).trim()
                val value = part.substring(separatorIndex + 1).trim()
                if (key.isNotEmpty() && value.isNotEmpty()) {
                    cookieParts.putIfAbsent(key, value)
                }
            }
    }
    return cookieParts
        .takeIf { it.isNotEmpty() }
        ?.entries
        ?.joinToString(separator = "; ") { (key, value) -> "$key=$value" }
}

/** Origin `scheme://host` untuk URL YouTube, atau null bila bukan YouTube. */
fun String?.myTubeCookieOrigin(): String? {
    val parsed = this?.let { runCatching { URI(it) }.getOrNull() } ?: return null
    val host = parsed.host?.lowercase() ?: return null
    if (host != "youtube.com" && !host.endsWith(".youtube.com")) return null
    val scheme = parsed.scheme
        ?.takeIf { it.equals("https", ignoreCase = true) || it.equals("http", ignoreCase = true) }
        ?: "https"
    return "$scheme://$host"
}

/** true bila host URL adalah youtube.com atau subdomainnya. */
fun String?.isYouTubeUrl(): Boolean {
    val host = this?.let { runCatching { URI(it) }.getOrNull() }?.host?.lowercase() ?: return false
    return host == "youtube.com" || host.endsWith(".youtube.com")
}

/** Skema yang boleh dimuat WebView (persis ArchiveTune). */
fun String.isWebViewLoadableUrl(): Boolean {
    val scheme = runCatching { URI(this).scheme?.lowercase() }.getOrNull()
    return scheme == "http" || scheme == "https" || scheme == "javascript" ||
        scheme == "data" || scheme == "blob"
}

/** true bila URL adalah http(s). */
fun String.isHttpUrl(): Boolean {
    val scheme = runCatching { URI(this).scheme?.lowercase() }.getOrNull()
    return scheme == "http" || scheme == "https"
}

/** true bila URL adalah listing Play Store YouTube Music (diblokir). */
fun String.isYouTubeMusicStoreListingUrl(): Boolean {
    val uri = runCatching { URI(this) }.getOrNull() ?: return false
    if (!uri.host.equals("play.google.com", ignoreCase = true)) return false
    if (uri.path != "/store/apps/details") return false
    val id = uri.rawQuery
        ?.split("&")
        ?.firstOrNull { it.startsWith("id=") }
        ?.substringAfter("=")
    return id == MYTUBE_MUSIC_PACKAGE_NAME
}
