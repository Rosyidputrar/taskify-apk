package com.rosyidputrar.taskify.core.domain.capture

import com.rosyidputrar.taskify.core.common.Rupiah
import com.rosyidputrar.taskify.core.domain.tasks.parseQuickAdd
import com.rosyidputrar.taskify.core.domain.time.Dates

enum class CaptureType { Task, Expense, Income, Journal, Note }

/**
 * Hasil tangkapan satu kalimat (port 1:1 `parseCapture` webapp):
 *   "makan siang 25rb"            → pengeluaran Rp25.000 (kategori ditebak: makan)
 *   "+5jt gaji oktober"           → pemasukan
 *   "grab ke kantor 18rb kemarin" → pengeluaran kemarin
 *   "besok deploy jam 9 #kerja"   → tugas
 *   "j: …" → jurnal, "c: …" → catatan bebas
 * [rawText] = ketikan asli tanpa #tag (dipakai bila jenis diganti ke Catatan).
 */
data class Capture(
    val type: CaptureType,
    val title: String,
    val rawText: String,
    val tags: List<String>,
    val amount: Long? = null,
    val date: String? = null,
    val time: String? = null,
    val priority: Int? = null,
    val categoryHint: String? = null,
)

private val INCOME_WORDS = Regex("\\b(gaji|gajian|bonus|thr|terima|diterima|dapat|pemasukan|transferan|freelance|komisi|dividen|refund)\\b", RegexOption.IGNORE_CASE)

/** Kata kunci → awalan nama kategori. */
val CATEGORY_KEYWORDS: List<Pair<Regex, String>> = listOf(
    "makan|minum|kopi|sarapan|siang|malam|jajan|snack|resto|warteg|bakso|nasi|mie|cafe|kafe|gofood|grabfood" to "makan",
    "grab|gojek|gocar|goride|ojek|ojol|bensin|pertalite|parkir|tol|kereta|krl|mrt|busway|taksi|bus|transport" to "transport",
    "listrik|pln|air|pdam|internet|wifi|indihome|pulsa|kuota|token|tagihan|bpjs" to "tagihan",
    "netflix|spotify|youtube|langganan|icloud|disney|chatgpt|github" to "langganan",
    "belanja|shopee|tokopedia|lazada|indomaret|alfamart|supermarket|baju" to "belanja",
    "obat|dokter|apotek|klinik|rs|vitamin" to "kesehatan",
    "nonton|bioskop|game|steam|konser|hiburan" to "hiburan",
    "buku|kursus|kuliah|sekolah|ujian" to "pendidikan",
    "gaji|gajian" to "gaji",
    "freelance|proyek|project|klien" to "freelance",
    "bonus|thr" to "bonus",
).map { (words, hint) -> Regex("\\b($words)\\b", RegexOption.IGNORE_CASE) to hint }

private val MONEY = Regex(
    "(^|\\s)(\\+?)((?:rp\\.?\\s?)\\d[\\d.,]*(?:\\s?(?:rb|ribu|k|jt|juta))?|\\d+(?:[.,]\\d+)?\\s?(?:rb|ribu|k|jt|juta)|\\d{1,3}(?:\\.\\d{3})+|\\d{4,})(?=\\s|$)",
    RegexOption.IGNORE_CASE,
)

private val COUNT_UNITS = setOf(
    "kata", "karakter", "langkah", "kali", "orang", "halaman", "hal", "baris", "item", "pcs", "km", "kg", "gram",
    "meter", "liter", "mb", "gb", "kb", "kalori", "kkal", "step", "steps", "words",
)

private val TAG = Regex("\\s#([\\p{L}\\p{N}_-]+)")
private val JOURNAL = Regex("^\\s*(?:j|jurnal|journal)\\s*:\\s*(.*)$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val NOTE = Regex("^\\s*(?:c|catatan|note)\\s*:\\s*(.*)$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

/** Nominal pertama yang masuk akal: nomor telepon/ID & angka bersatuan hitung dilewati. */
private fun findMoney(text: String): MatchResult? {
    for (m in MONEY.findAll(text)) {
        val raw = m.groupValues[3]
        if (Regex("^[\\d.,]+$").matches(raw)) {
            if (Regex("^\\d+$").matches(raw) && (raw.startsWith("0") || raw.length > 9)) continue
            val next = text.substring(m.range.last + 1).trim().split(Regex("\\s+")).firstOrNull()?.lowercase()
            if (!next.isNullOrEmpty() && next in COUNT_UNITS) continue
        }
        return m
    }
    return null
}

fun parseCapture(input: String, today: String): Capture {
    var text = " ${input.trim()} "
    val tags = mutableListOf<String>()
    text = TAG.replace(text) { tags += it.groupValues[1].lowercase(); " " }
    val rawText = text.replace(Regex("\\s+"), " ").trim()

    JOURNAL.find(text)?.let {
        val body = it.groupValues[1].trim()
        return Capture(CaptureType.Journal, body, body, tags, date = today)
    }
    NOTE.find(text)?.let {
        val body = it.groupValues[1].replace(Regex("\\s+"), " ").trim()
        return Capture(CaptureType.Note, body, body, tags)
    }

    val money = findMoney(text)
    val rawMoney = money?.groupValues?.get(3)
    val looksLikeYear = rawMoney != null && Regex("^\\d{4}$").matches(rawMoney) && rawMoney.toInt() in 1900..2100
    if (money != null && !looksLikeYear) {
        val amount = Rupiah.parse(rawMoney!!)
        if (amount != null && amount > 0) {
            var rest = text.replaceFirst(money.value, " ")
            var date = today
            Regex("\\s(kemarin|kmrn)(?=\\s)", RegexOption.IGNORE_CASE).find(rest)?.let {
                date = Dates.addDays(today, -1)
                rest = rest.replaceRange(it.range, " ")
            }
            rest = Regex("\\s(hari ini)(?=\\s)", RegexOption.IGNORE_CASE).replaceFirst(rest, " ")
            val title = rest.replace(Regex("\\s+"), " ").trim()
            val income = money.groupValues[2] == "+" || INCOME_WORDS.containsMatchIn(title)
            val hint = CATEGORY_KEYWORDS.firstOrNull { it.first.containsMatchIn(title) }?.second
            return Capture(if (income) CaptureType.Income else CaptureType.Expense, title, rawText, tags, amount = amount, date = date, categoryHint = hint)
        }
    }

    val t = parseQuickAdd(text, today)
    return Capture(CaptureType.Task, t.title, rawText, tags, date = t.dueDate, time = t.dueTime, priority = t.priority)
}

/** Pilih kategori untuk petunjuk (awalan nama), atau kata di judul yang mengawali nama kategori. */
fun <T> matchCategory(categories: List<T>, kindOf: (T) -> String, nameOf: (T) -> String, kind: String, hint: String?, title: String): T? {
    val pool = categories.filter { kindOf(it) == kind }
    val lower = title.lowercase()
    return (hint?.let { h -> pool.firstOrNull { nameOf(it).lowercase().startsWith(h) } })
        ?: pool.firstOrNull { c -> lower.split(Regex("\\s+")).any { w -> w.length > 2 && nameOf(c).lowercase().startsWith(w) } }
}
