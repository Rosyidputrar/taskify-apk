package com.rosyidputrar.taskify.core.domain.sandi

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import java.net.URI
import java.net.URLDecoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs
import kotlin.math.roundToLong

enum class TotpAlgorithm(val mac: String) { SHA1("HmacSHA1"), SHA256("HmacSHA256"), SHA512("HmacSHA512") }

/** Konfigurasi TOTP di dalam item Sandi (ikut terenkripsi). [secret] = base32 RFC 4648 huruf besar. */
data class TotpConfig(val secret: String, val digits: Int = 6, val period: Int = 30, val algorithm: TotpAlgorithm = TotpAlgorithm.SHA1) {
    fun toJson(): JsonObject = buildJsonObject {
        put("secret", secret); put("digits", digits); put("period", period); put("algorithm", algorithm.name)
    }
}

data class TotpWindow(val code: String, val next: String, val remainingMs: Long, val period: Int)

data class TotpInput(val config: TotpConfig, val name: String, val username: String)

/** TOTP (RFC 6238) — port `totp.ts`. Taskify sebagai authenticator sendiri. */
object Totp {
    val DIGITS = listOf(6, 8)
    const val PERIOD_MIN = 5
    const val PERIOD_MAX = 300
    const val DEFAULT_PERIOD = 30
    const val MIN_SECRET_BYTES = 8
    private const val BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private val IMPOSSIBLE_REMAINDER = setOf(1, 3, 6)

    /** base32 RFC 4648 → byte; toleran huruf kecil, spasi, tanda hubung, padding. null = tidak valid. */
    fun decodeBase32(text: String): ByteArray? {
        val clean = text.uppercase().replace(Regex("[\\s-]"), "").replace(Regex("=+$"), "")
        if (clean.isEmpty() || (clean.length % 8) in IMPOSSIBLE_REMAINDER) return null
        val out = java.io.ByteArrayOutputStream()
        var value = 0
        var bits = 0
        for (ch in clean) {
            val idx = BASE32.indexOf(ch)
            if (idx < 0) return null
            value = (value shl 5) or idx
            bits += 5
            if (bits >= 8) {
                out.write((value ushr (bits - 8)) and 0xff)
                bits -= 8
                value = value and ((1 shl bits) - 1)
            }
        }
        return out.toByteArray()
    }

    fun encodeBase32(bytes: ByteArray): String {
        val out = StringBuilder()
        var value = 0
        var bits = 0
        for (b in bytes) {
            value = (value shl 8) or (b.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                out.append(BASE32[(value ushr (bits - 5)) and 31])
                bits -= 5
            }
            value = value and ((1 shl bits) - 1)
        }
        if (bits > 0) out.append(BASE32[(value shl (5 - bits)) and 31])
        return out.toString()
    }

    /** "JBSWY3DPEHPK3PXP" → "JBSW Y3DP EHPK 3PXP". */
    fun formatSecret(secret: String): String = secret.uppercase().replace(Regex("[\\s-]"), "").chunked(4).joinToString(" ")

    /** HOTP (RFC 4226). */
    fun hotp(secret: ByteArray, counter: Long, digits: Int, algorithm: TotpAlgorithm): String {
        val mac = Mac.getInstance(algorithm.mac)
        mac.init(SecretKeySpec(secret, algorithm.mac))
        val msg = ByteArray(8) { i -> (counter ushr (8 * (7 - i))).toByte() }
        val h = mac.doFinal(msg)
        val offset = h[h.size - 1].toInt() and 0x0f
        val binary = ((h[offset].toInt() and 0x7f) shl 24) or ((h[offset + 1].toInt() and 0xff) shl 16) or
            ((h[offset + 2].toInt() and 0xff) shl 8) or (h[offset + 3].toInt() and 0xff)
        var mod = 1L
        repeat(digits) { mod *= 10 }
        return (binary % mod).toString().padStart(digits, '0')
    }

    fun counterAt(atMs: Long, period: Int): Long = Math.floorDiv(atMs, 1000L * period)

    fun clampPeriod(period: Number?): Int {
        val p = period?.toDouble() ?: return DEFAULT_PERIOD
        if (p.isNaN() || p.isInfinite()) return DEFAULT_PERIOD
        return p.toLong().toInt().coerceIn(PERIOD_MIN, PERIOD_MAX)
    }

    /** Kode sekarang & berikutnya + sisa waktu. null = kunci tidak bisa diurai. */
    fun window(config: TotpConfig, atMs: Long): TotpWindow? {
        val secret = decodeBase32(config.secret) ?: return null
        if (secret.size < MIN_SECRET_BYTES) return null
        val period = clampPeriod(config.period)
        val counter = counterAt(atMs, period)
        val code = hotp(secret, counter, config.digits, config.algorithm)
        val next = hotp(secret, counter + 1, config.digits, config.algorithm)
        secret.fill(0)
        return TotpWindow(code, next, (counter + 1) * period * 1000 - atMs, period)
    }

    /** Validasi `totp` hasil dekripsi; bentuk aneh → null (item tetap terbuka). */
    fun parseConfig(value: JsonElement?): TotpConfig? {
        val o = value as? JsonObject ?: return null
        val raw = (o["secret"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        val secret = raw.uppercase().replace(Regex("[\\s-]"), "")
        val bytes = decodeBase32(secret)
        if (bytes == null || bytes.size < MIN_SECRET_BYTES) return null
        val digits = (o["digits"] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.takeIf { it == 6.0 || it == 8.0 }?.toInt() ?: 6
        val period = clampPeriod((o["period"] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull ?: DEFAULT_PERIOD.toDouble())
        val algorithm = (o["algorithm"] as? JsonPrimitive)?.content?.let { a -> TotpAlgorithm.entries.firstOrNull { it.name == a } } ?: TotpAlgorithm.SHA1
        return TotpConfig(secret, digits, period, algorithm)
    }

    private fun configOf(secret: String, digits: Int?, period: Double?, algorithm: String?): TotpConfig? = parseConfig(
        buildJsonObject {
            put("secret", secret)
            if (digits != null) put("digits", digits)
            if (period != null) put("period", period)
            if (algorithm != null) put("algorithm", algorithm)
        },
    )

    /**
     * URI `otpauth://totp/Penerbit:akun?secret=…` (format Key URI Google Authenticator) — untuk
     * memindahkan kunci ke aplikasi authenticator lain lewat QR. Kebalikan dari [parseInput].
     */
    fun otpauthUri(config: TotpConfig, issuer: String, account: String): String {
        fun enc(text: String) = java.net.URLEncoder.encode(text, "UTF-8").replace("+", "%20")
        val i = issuer.trim().replace(":", "")
        val a = account.trim()
        val label = when {
            i.isNotEmpty() && a.isNotEmpty() -> enc(i) + ":" + enc(a)
            i.isNotEmpty() -> enc(i)
            else -> enc(a.ifEmpty { "Taskify" })
        }
        val secret = config.secret.uppercase().replace(Regex("[\\s=-]"), "")
        val query = buildList {
            add("secret=$secret")
            if (i.isNotEmpty()) add("issuer=" + enc(i))
            add("algorithm=${config.algorithm.name}")
            add("digits=${config.digits}")
            add("period=${config.period}")
        }
        return "otpauth://totp/$label?" + query.joinToString("&")
    }

    /** Kunci base32 saja, atau URI `otpauth://totp/Issuer:akun?secret=…`. */
    fun parseInput(text: String): TotpInput? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.lowercase().startsWith("otpauth:")) return parseOtpauth(trimmed)
        return configOf(trimmed, null, null, null)?.let { TotpInput(it, "", "") }
    }

    /** Seperti `decodeURIComponent` (untuk label path): "+" tetap "+". Gagal → teks apa adanya. */
    private fun decodeComponent(text: String) = try { URLDecoder.decode(text.replace("+", "%2B"), "UTF-8") } catch (_: Exception) { text }

    /** Seperti `URLSearchParams` (untuk query): "+" berarti spasi. */
    private fun decodeQuery(text: String) = try { URLDecoder.decode(text, "UTF-8") } catch (_: Exception) { text }

    private fun parseOtpauth(uri: String): TotpInput? {
        val u = try { URI(uri) } catch (_: Exception) { return null }
        if (!u.host.orEmpty().equals("totp", ignoreCase = true)) return null
        val query = (u.rawQuery ?: "").split('&').filter { it.isNotEmpty() }.associate { part ->
            val k = part.substringBefore('=')
            decodeQuery(k) to decodeQuery(part.substringAfter('=', ""))
        }
        val config = configOf(
            query["secret"] ?: "",
            query["digits"]?.toIntOrNull() ?: 0,
            query["period"]?.toDoubleOrNull() ?: DEFAULT_PERIOD.toDouble(),
            (query["algorithm"] ?: "SHA1").uppercase(),
        ) ?: return null
        val label = decodeComponent((u.rawPath ?: "").removePrefix("/"))
        val sep = label.indexOf(':')
        val labelIssuer = if (sep >= 0) label.substring(0, sep).trim() else ""
        val account = (if (sep >= 0) label.substring(sep + 1) else label).trim()
        return TotpInput(config, (query["issuer"] ?: labelIssuer).trim(), account)
    }

    /** Alasan tempelan ditolak, atau null bila bisa dipakai. */
    fun problem(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return "Tempel kunci rahasia atau URI otpauth://."
        if (trimmed.lowercase().startsWith("otpauth:")) {
            if (!Regex("^otpauth://totp/", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)) return "Hanya URI otpauth://totp/… yang didukung (hotp berbasis penghitung tidak)."
            return if (parseOtpauth(trimmed) != null) null else "URI otpauth:// tidak bisa dibaca atau kuncinya tidak valid."
        }
        val clean = trimmed.uppercase().replace(Regex("[\\s-]"), "").replace(Regex("=+$"), "")
        val bytes = decodeBase32(clean) ?: return "Kunci rahasia harus base32: hanya huruf A–Z dan angka 2–7 (tanpa 0, 1, dan 8)."
        if (bytes.size < MIN_SECRET_BYTES) return "Kunci rahasia terlalu pendek. Periksa lagi."
        return null
    }

    // ------------------------------------------------------------------ koreksi jam

    const val SKEW_WARN_MS = 10_000L
    private const val MAX_PLAUSIBLE_OFFSET_MS = 86_400_000L

    /** Selisih (server − perangkat) dengan kompensasi separuh waktu pulang-pergi; null = tidak masuk akal. */
    fun offsetFromSample(serverMs: Long, sentAtMs: Long, receivedAtMs: Long): Long? {
        if (receivedAtMs < sentAtMs) return null
        val local = (sentAtMs + receivedAtMs) / 2.0
        val offset = serverMs - local
        if (abs(offset) > MAX_PLAUSIBLE_OFFSET_MS) return null
        return offset.roundToLong()
    }

    fun isClockSkewed(offsetMs: Long) = abs(offsetMs) >= SKEW_WARN_MS
}
