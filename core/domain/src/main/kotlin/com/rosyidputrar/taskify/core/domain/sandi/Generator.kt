package com.rosyidputrar.taskify.core.domain.sandi

import java.security.SecureRandom
import java.text.Normalizer
import kotlin.math.ln
import kotlin.math.min

/** Sumber acak yang bisa diganti di tes (selalu CSPRNG di aplikasi). */
fun interface RandomSource {
    fun fill(bytes: ByteArray)
}

private val secure = SecureRandom()
val SecureSource = RandomSource { secure.nextBytes(it) }

private const val TWO_32 = 0x1_0000_0000L

/** Bilangan bulat seragam di [0, max) — rejection sampling, tanpa bias modulo (port `randomInt`). */
fun randomInt(max: Int, rng: RandomSource = SecureSource): Int {
    require(max in 1..Int.MAX_VALUE)
    if (max == 1) return 0
    val limit = TWO_32 - (TWO_32 % max)
    val buf = ByteArray(4)
    while (true) {
        rng.fill(buf)
        val x = ((buf[0].toLong() and 0xff) shl 24) or ((buf[1].toLong() and 0xff) shl 16) or ((buf[2].toLong() and 0xff) shl 8) or (buf[3].toLong() and 0xff)
        if (x < limit) return (x % max).toInt()
    }
}

data class PasswordOptions(
    val length: Int = Generator.LENGTH_DEFAULT,
    val lower: Boolean = true,
    val upper: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    val avoidAmbiguous: Boolean = false,
)

data class PassphraseOptions(
    val words: Int = Generator.WORDS_DEFAULT,
    val separator: String = "-",
    val capitalize: Boolean = false,
    val includeNumber: Boolean = false,
)

enum class StrengthLabel(val label: String) { Weak("Lemah"), Fair("Cukup"), Strong("Kuat"), VeryStrong("Sangat kuat") }

data class Strength(val bits: Double, val label: StrengthLabel, val score: Int, val issues: List<String> = emptyList())

fun strengthFromBits(bits: Double, issues: List<String> = emptyList()): Strength = when {
    bits < 40 -> Strength(bits, StrengthLabel.Weak, if (bits < 28) 0 else 1, issues)
    bits < 60 -> Strength(bits, StrengthLabel.Fair, 2, issues)
    bits < 80 -> Strength(bits, StrengthLabel.Strong, 3, issues)
    else -> Strength(bits, StrengthLabel.VeryStrong, 4, issues)
}

/** Generator sandi & frasa — port `generator.ts` (keacakan hanya dari CSPRNG). */
object Generator {
    const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    const val DIGITS = "0123456789"
    /** Tanpa kutip, backslash, spasi, <>|~ — sering ditolak situs atau merusak penempelan. */
    const val SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?"
    const val AMBIGUOUS = "Il1O0o"
    const val LENGTH_MIN = 8
    const val LENGTH_MAX = 128
    const val LENGTH_DEFAULT = 20
    const val WORDS_MIN = 4
    const val WORDS_MAX = 10
    const val WORDS_DEFAULT = 5
    val SEPARATORS = listOf("-", " ", ".", "_", "")

    /** Daftar kata EFF Large (7.776 kata, CC BY 3.0 US © EFF). */
    val wordlist: List<String> by lazy {
        Generator::class.java.getResourceAsStream("/sandi/eff_large_wordlist.txt")!!.bufferedReader().readLines().filter { it.isNotBlank() }
    }

    fun charClasses(o: PasswordOptions): List<String> {
        fun strip(s: String) = if (o.avoidAmbiguous) s.filterNot { it in AMBIGUOUS } else s
        return buildList {
            if (o.lower) add(strip(LOWER))
            if (o.upper) add(strip(UPPER))
            if (o.digits) add(strip(DIGITS))
            if (o.symbols) add(strip(SYMBOLS))
        }
    }

    fun clampLength(n: Int) = n.coerceIn(LENGTH_MIN, LENGTH_MAX)
    fun clampWords(n: Int) = n.coerceIn(WORDS_MIN, WORDS_MAX)

    /** Setiap kelompok terpilih DIJAMIN muncul minimal sekali; sisanya dari gabungan, lalu diacak. */
    fun password(opts: PasswordOptions, rng: RandomSource = SecureSource): String {
        val classes = charClasses(opts)
        require(classes.isNotEmpty()) { "Pilih minimal satu jenis karakter." }
        val length = clampLength(opts.length)
        val pool = classes.joinToString("")
        val chars = classes.map { it[randomInt(it.length, rng)] }.toMutableList()
        while (chars.size < length) chars += pool[randomInt(pool.length, rng)]
        for (i in chars.indices.reversed()) {
            if (i == 0) break
            val j = randomInt(i + 1, rng)
            val t = chars[i]; chars[i] = chars[j]; chars[j] = t
        }
        return chars.joinToString("")
    }

    fun passphrase(opts: PassphraseOptions, words: List<String> = wordlist, rng: RandomSource = SecureSource): String {
        require(words.size >= 2) { "Daftar kata kosong." }
        val count = clampWords(opts.words)
        val out = MutableList(count) {
            val w = words[randomInt(words.size, rng)]
            if (opts.capitalize) w.replaceFirstChar { it.uppercase() } else w
        }
        if (opts.includeNumber) {
            val at = randomInt(count, rng)
            out[at] = out[at] + randomInt(10, rng)
        }
        return out.joinToString(opts.separator)
    }

    private fun log2(n: Double) = ln(n) / ln(2.0)

    fun passwordEntropy(o: PasswordOptions): Double {
        val pool = charClasses(o).joinToString("").length
        return if (pool < 2) 0.0 else clampLength(o.length) * log2(pool.toDouble())
    }

    fun passphraseEntropy(o: PassphraseOptions, size: Int = wordlist.size): Double {
        val words = clampWords(o.words)
        var bits = words * log2(size.toDouble())
        if (o.includeNumber) bits += log2(words.toDouble()) + log2(10.0)
        return bits
    }

    private val COMMON = setOf(
        "password", "password1", "password123", "passw0rd", "qwerty", "qwerty123", "qwertyuiop", "123456", "12345678", "123456789",
        "1234567890", "111111", "000000", "abc123", "iloveyou", "admin", "admin123", "welcome", "letmein", "monkey", "dragon", "sandi",
        "kata sandi", "katasandi", "rahasia", "bismillah", "indonesia", "sayangku", "taskify",
    )
    private val SEQUENCES = listOf("abcdefghijklmnopqrstuvwxyz", "0123456789", "qwertyuiopasdfghjklzxcvbnm")

    private fun hasSequence(lower: String, run: Int = 4): Boolean = SEQUENCES.any { seq ->
        listOf(seq, seq.reversed()).any { s -> (0..s.length - run).any { i -> lower.contains(s.substring(i, i + run)) } }
    }

    fun charPoolSize(password: String): Int {
        val pw = Normalizer.normalize(password, Normalizer.Form.NFKC)
        var pool = 0
        if (pw.any { it.isLowerCase() }) pool += 26
        if (pw.any { it.isUpperCase() }) pool += 26
        if (pw.any { it.isDigit() }) pool += 10
        if (pw.any { !it.isLetter() && !it.isDigit() }) pool += 33
        return maxOf(pool, 10)
    }

    /**
     * Perkiraan CEPAT kekuatan sandi buatan manusia (meter informatif untuk sandi situs). BUKAN
     * gerbang master password — itu [MasterPassword] (zxcvbn + kamus).
     */
    fun estimate(password: String): Strength {
        val pw = Normalizer.normalize(password, Normalizer.Form.NFKC)
        if (pw.isEmpty()) return strengthFromBits(0.0)
        val lower = pw.lowercase()
        if (lower in COMMON) return strengthFromBits(8.0, listOf("Terlalu umum dipakai."))
        val issues = mutableListOf<String>()
        val parts = pw.split(Regex("[\\s\\-._]+")).filter { it.isNotEmpty() }
        if (parts.size >= 3 && parts.all { p -> p.all { it.isLetter() } }) {
            val dup = parts.map { it.lowercase() }.toSet().size < parts.size
            val bits = parts.size * 11.0 - if (dup) 11 else 0
            if (parts.size < 4) issues += "Pakai minimal 4 kata."
            return strengthFromBits(bits, issues)
        }
        val pool = charPoolSize(pw)
        val unique = lower.toSet().size
        val length = pw.codePointCount(0, pw.length)
        val effective = min(length.toDouble(), unique * 1.5)
        var bits = effective * log2(pool.toDouble())
        if (hasSequence(lower)) {
            issues += "Mengandung urutan mudah ditebak (abcd, 1234, qwer)."
            bits *= 0.6
        }
        if (unique <= 3 && pw.length > 4) issues += "Terlalu banyak karakter berulang."
        if (Regex("^\\p{L}+\\d{1,4}$").matches(pw)) {
            issues += "Kata diikuti angka adalah pola yang paling sering ditebak."
            bits = min(bits, 30.0)
        }
        if (length < 12) issues += "Minimal 12 karakter."
        return strengthFromBits(bits, issues)
    }

    // ------------------------------------------------------------------ pilihan generator (per perangkat)

    enum class Mode { Password, Passphrase }

    data class Prefs(val mode: Mode = Mode.Password, val password: PasswordOptions = PasswordOptions(), val passphrase: PassphraseOptions = PassphraseOptions())

    /** Nilai tersimpan apa pun → pilihan valid (minimal satu jenis karakter aktif). */
    fun sanitize(p: Prefs): Prefs {
        var pw = p.password.copy(length = clampLength(p.password.length))
        if (!pw.lower && !pw.upper && !pw.digits && !pw.symbols) pw = pw.copy(lower = true)
        val ph = p.passphrase.copy(words = clampWords(p.passphrase.words), separator = p.passphrase.separator.takeIf { it in SEPARATORS } ?: "-")
        return Prefs(p.mode, pw, ph)
    }

}
