package com.rosyidputrar.taskify.core.domain.sandi

import com.nulabinc.zxcvbn.StandardDictionaries
import com.nulabinc.zxcvbn.StandardKeyboards
import com.nulabinc.zxcvbn.Zxcvbn
import com.nulabinc.zxcvbn.ZxcvbnBuilder
import com.nulabinc.zxcvbn.matchers.Dictionary
import java.text.Normalizer
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Gerbang master password — padanan `master-password.ts`. Mesinnya zxcvbn (port Java zxcvbn4j)
 * dengan kamus bawaan Inggris + kamus Indonesia resmi zxcvbn-ts (kata umum, nama, Wikipedia,
 * deret kata) + kata khas aplikasi. Aturannya sama: min. 12 karakter, ≥ 50 bit, frasa kata-kata
 * dibatasi 17 bit per kata BERBEDA. Gagal memuat mesin = gagal tertutup (ditolak).
 */
object MasterPassword {
    const val MIN_LENGTH = 12
    const val MIN_BITS = 50.0
    private const val MAX_CHECKED_LENGTH = 128
    private const val MAX_BITS_PER_DISTINCT_WORD = 17.0
    const val ENGINE_LOAD_ERROR = "Pemeriksa kekuatan sandi gagal dimuat. Coba tutup lalu buka lagi aplikasinya."
    const val ADVICE = "Pakai frasa 5 kata, atau sandi acak minimal 16 karakter."
    private val APP_WORDS = listOf("taskify", "sandi", "katasandi", "kata sandi", "rahasia", "brankas", "master")

    @Volatile private var engine: Zxcvbn? = null

    private fun resourceWords(path: String): List<String> =
        MasterPassword::class.java.getResourceAsStream(path)!!.bufferedReader().readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }

    /** Muat mesin (sekali; ±1 dtk karena kamus). Melempar bila gagal. */
    @Synchronized
    fun ensureEngine(): Zxcvbn {
        engine?.let { return it }
        val dictionaries = StandardDictionaries.loadAllDictionaries().toMutableList()
        dictionaries += Dictionary("commonWords-id", resourceWords("/sandi/id/commonWords.txt"))
        dictionaries += Dictionary("names-id", resourceWords("/sandi/id/names.txt"))
        dictionaries += Dictionary("wikipedia-id", resourceWords("/sandi/id/wikipedia.txt"))
        dictionaries += Dictionary("wordSequences-id", resourceWords("/sandi/id/wordSequences.txt"))
        dictionaries += Dictionary("app", APP_WORDS)
        return ZxcvbnBuilder().dictionaries(dictionaries).keyboards(StandardKeyboards.loadAllKeyboards()).build().also { engine = it }
    }

    val isReady: Boolean get() = engine != null

    /** Potongan email yang tidak boleh menjadi bagian sandi. */
    fun userInputsFromEmail(email: String?): List<String> {
        if (email.isNullOrBlank()) return emptyList()
        val local = email.substringBefore('@')
        return (listOf(local) + local.split(Regex("[^\\p{L}\\p{N}]+"))).filter { it.length >= 3 }
    }

    /** Penilaian; WAJIB [ensureEngine] dulu. */
    fun evaluate(password: String, userInputs: List<String> = emptyList()): Strength {
        val z = engine ?: throw IllegalStateException("Pemeriksa kekuatan sandi belum dimuat.")
        val normalized = Normalizer.normalize(password, Normalizer.Form.NFKC)
        val pw = normalized.take(MAX_CHECKED_LENGTH)
        if (pw.isEmpty()) return strengthFromBits(0.0)
        val r = z.measure(pw, userInputs)
        var bits = ln(max(r.guesses, 1.0)) / ln(2.0)
        val words = pw.split(Regex("[\\s\\-._]+")).filter { it.isNotEmpty() }
        if (words.size >= 3 && words.all { w -> w.all { it.isLetter() } }) {
            val distinct = words.map { it.lowercase() }.toSet().size
            bits = min(bits, distinct * MAX_BITS_PER_DISTINCT_WORD)
        }
        val patterns = r.sequence.map { it.pattern.name.lowercase() }.toSet()
        r.wipe()
        val issues = mutableListOf<String>()
        if (bits < 60) {
            if ("dictionary" in patterns) issues += "Mengandung kata atau nama yang umum dipakai (juga dengan huruf diganti angka)."
            if ("date" in patterns || "regex" in patterns) issues += "Mengandung tahun atau tanggal. Itu mudah ditebak."
            if ("sequence" in patterns || "spatial" in patterns) issues += "Mengandung urutan mudah ditebak (abcd, 1234, qwer)."
            if ("repeat" in patterns) issues += "Mengandung pengulangan karakter."
        }
        if (pw.codePointCount(0, pw.length) < MIN_LENGTH) issues += "Minimal $MIN_LENGTH karakter."
        return strengthFromBits(bits, issues)
    }

    /** Alasan ditolak, atau null bila boleh dipakai (gagal tertutup bila mesin tidak bisa dimuat). */
    fun problem(password: String, userInputs: List<String> = emptyList()): String? {
        if (password.codePointCount(0, password.length) < MIN_LENGTH) return "Minimal $MIN_LENGTH karakter."
        try { ensureEngine() } catch (_: Throwable) { return ENGINE_LOAD_ERROR }
        val s = evaluate(password, userInputs)
        return if (s.bits < MIN_BITS) "${s.issues.firstOrNull() ?: "Terlalu mudah ditebak."} $ADVICE" else null
    }
}
