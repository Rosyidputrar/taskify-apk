package com.rosyidputrar.taskify.core.domain.media

/**
 * Penyusun teks mentah error bersama (dipakai semua layar musik).
 *
 * Formatnya sama dengan `rawDetail` error login: baris `label: nilai`
 * dipisah `\n`, nilai kosong dilewati. Teks ini tampil langsung di layar
 * DAN disalin utuh lewat tombol "Salin detail", jadi isinya HANYA boleh
 * data diagnosis tanpa nilai sensitif: tidak ada URL lengkap (bisa
 * mengandung signature di query), cookie, token, atau kata sandi.
 * Pemanggil yang bertanggung jawab tidak memasukkan nilai sensitif;
 * fungsi di sini hanya membatasi panjang tiap baris supaya blok detail
 * tidak membengkak.
 */

/** Batas tiap baris nilai dalam blok detail mentah (~500 karakter). */
const val RAW_DETAIL_MAX_CHARS = 500

/** Batas jumlah entri rantai `cause` yang ikut ditampilkan. */
const val RAW_DETAIL_MAX_CAUSES = 5

/**
 * Potong [value] sampai [maxChars] karakter; bila dipotong, tandai dengan
 * `"… (dipotong)"` supaya jelas teksnya tidak utuh. Nilai kosong → `""`.
 */
fun truncateRawDetail(value: String?, maxChars: Int = RAW_DETAIL_MAX_CHARS): String {
    val trimmed = value?.trim().orEmpty()
    if (trimmed.length <= maxChars) return trimmed
    return trimmed.take(maxChars) + "… (dipotong)"
}

/**
 * Susun blok detail mentah dari pasangan `(label, nilai)`. Nilai kosong
 * (null/blank) dilewati — label tanpa nilai tidak tampil. Tiap BARIS nilai
 * dibatasi [RAW_DETAIL_MAX_CHARS] karakter, jadi nilai berbaris banyak
 * (mis. rantai `cause`) tetap terbawa baris demi baris, bukan terpotong
 * total di 500 karakter pertama.
 */
fun buildRawDetail(vararg sections: Pair<String, String?>): String =
    sections.mapNotNull { (label, value) ->
        val text = value
            ?.split('\n')
            ?.joinToString("\n") { truncateRawDetail(it) }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return@mapNotNull null
        "$label: $text"
    }.joinToString("\n")

/**
 * Rantai `cause` sebuah [Throwable] sebagai teks berbaris:
 * `"SimpleClassName: pesan"` per tingkat, masing-masing pesan dibatasi
 * [RAW_DETAIL_MAX_CHARS] karakter, maksimal [RAW_DETAIL_MAX_CAUSES]
 * tingkat. `null` bila tidak ada cause. Siklus rantai (cause menunjuk
 * dirinya sendiri) dihentikan supaya tidak berputar tanpa akhir.
 */
fun causeChainText(cause: Throwable?, maxCauses: Int = RAW_DETAIL_MAX_CAUSES): String? {
    if (cause == null) return null
    val lines = mutableListOf<String>()
    val seen = HashSet<Throwable>()
    var current: Throwable? = cause
    while (current != null && lines.size < maxCauses && seen.add(current)) {
        val name = current.javaClass.simpleName.ifEmpty { current.javaClass.name.substringAfterLast('.') }
        val message = truncateRawDetail(current.message)
        lines += if (message.isEmpty()) name else "$name: $message"
        current = current.cause
    }
    return lines.joinToString("\n").takeIf { it.isNotEmpty() }
}
