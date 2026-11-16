package com.rosyidputrar.taskify.core.domain.capture

/** Tujuan tangkapan teks selain tugas yang dikenali dari awalan eksplisit. */
enum class CaptureTarget { Journal, Note }

data class PrefixedCapture(val target: CaptureTarget, val body: String, val tags: List<String>)

private val TAG = Regex("(^|\\s)#([\\p{L}\\p{N}_-]+)")
private val JOURNAL = Regex("^\\s*(?:j|jurnal|journal)\\s*:\\s*(.*)$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val NOTE = Regex("^\\s*(?:c|catatan|note)\\s*:\\s*(.*)$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

/** Pisahkan #tag dari teks (huruf kecil); baris baru di teks tetap dipertahankan. */
fun extractTags(input: String): Pair<String, List<String>> {
    val tags = TAG.findAll(input).map { it.groupValues[2].lowercase() }.distinct().toList()
    val text = TAG.replace(input) { it.groupValues[1] }.lines().joinToString("\n") { it.replace(Regex(" {2,}"), " ").trimEnd() }.trim()
    return text to tags
}

/**
 * Awalan eksplisit dari `parseCapture` webapp: "j: …" → jurnal hari ini, "c: …" → catatan bebas.
 * Null bila tidak berawalan (diteruskan ke parser tugas/transaksi).
 */
fun parseCapturePrefix(input: String): PrefixedCapture? {
    val (text, tags) = extractTags(input)
    JOURNAL.find(text)?.let { return PrefixedCapture(CaptureTarget.Journal, it.groupValues[1].trim(), tags) }
    NOTE.find(text)?.let { return PrefixedCapture(CaptureTarget.Note, it.groupValues[1].replace(Regex("\\s+"), " ").trim(), tags) }
    return null
}
