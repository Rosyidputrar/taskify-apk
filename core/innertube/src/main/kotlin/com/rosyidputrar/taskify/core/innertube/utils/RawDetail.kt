package com.rosyidputrar.taskify.core.innertube.utils

/**
 * Cuplikan badan respons mentah yang aman ditampilkan di layar error
 * (PLAN music-login-raw-error): SATU-SATUNYA tempat scrub — UI menampilkan
 * apa adanya dari hasil fungsi ini.
 *
 * Menghapus nilai sensitif (bukan hanya kuncinya, supaya kata kuncinya pun
 * tidak pernah lolos ke layar/log):
 * - pasangan `kunci=nilai` cookie yang kuncinya berakhiran `SID`
 *   (`SAPISID`, `HSID`, `SSID`, `APISID`, `__Secure-1PSID`, ...),
 * - `cookie=...`,
 * - header `Authorization: ...` (termasuk bentuk `SAPISIDHASH <hash>`),
 * - `SAPISIDHASH <hash>` yang berdiri sendiri,
 * - nilai `visitorData` / `VISITOR_DATA`.
 *
 * Lalu dipotong maks [MAX_LENGTH] karakter. Bila raw null/kosong →
 * `"respons kosong"`. JVM-murni (regex `java.util.regex` via Kotlin
 * `Regex`), tanpa dependensi Android/Ktor.
 */
fun scrubRawDetail(raw: String?): String {
    if (raw.isNullOrBlank()) return EMPTY_RAW_DETAIL
    var scrubbed = raw
    // Header Authorization: <nilai> — nilai bisa mengandung spasi
    // ("SAPISIDHASH <timestamp>_<hash>"), jadi makan sampai akhir baris
    // atau penutup kutip JSON.
    scrubbed = AUTHORIZATION_PATTERN.replace(scrubbed, REDACTED)
    // SAPISIDHASH <hash> yang berdiri sendiri (di luar header Authorization).
    scrubbed = SAPISIDHASH_PATTERN.replace(scrubbed, REDACTED)
    // Pasangan cookie kunci=nilai untuk kunci berakhiran SID.
    scrubbed = SID_COOKIE_PATTERN.replace(scrubbed, REDACTED)
    // cookie=<nilai> (bentuk apa pun).
    scrubbed = COOKIE_VALUE_PATTERN.replace(scrubbed, REDACTED)
    // visitorData / VISITOR_DATA beserta nilainya.
    scrubbed = VISITOR_DATA_PATTERN.replace(scrubbed, REDACTED)
    return scrubbed.take(MAX_LENGTH)
}

const val MAX_RAW_DETAIL_LENGTH = 500

const val EMPTY_RAW_DETAIL = "respons kosong"

/** Cuplikan untuk badan kosong beserta status HTTP yang diketahui. */
fun emptyRawDetail(status: Int): String = "respons kosong, status $status"

private const val REDACTED = "[redacted]"
private const val MAX_LENGTH = MAX_RAW_DETAIL_LENGTH

private val AUTHORIZATION_PATTERN =
    Regex("""Authorization\s*[:=]\s*[^"\r\n,;]+""", RegexOption.IGNORE_CASE)

private val SAPISIDHASH_PATTERN =
    Regex("""SAPISIDHASH\s+[^\s"',;]+""", RegexOption.IGNORE_CASE)

private val SID_COOKIE_PATTERN =
    Regex("""[A-Za-z0-9_.\-]*SID\s*=\s*(?:"[^"]*"|[^;,\s"']+)""", RegexOption.IGNORE_CASE)

private val COOKIE_VALUE_PATTERN =
    Regex("""\bcookie\s*=\s*(?:"[^"]*"|[^;,\s"']+)""", RegexOption.IGNORE_CASE)

private val VISITOR_DATA_PATTERN =
    Regex("""visitor[_]?data\s*["']?\s*[:=]\s*["']?[^"'\s,;]+["']?""", RegexOption.IGNORE_CASE)
