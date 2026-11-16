package com.rosyidputrar.taskify.core.domain.share

/** Jenis berkas yang dibagikan dari aplikasi lain (menentukan tujuan bawaan). Port `share-inbox.ts`. */
enum class SharedKind { Zip, Image, Text, Other }

object Share {
    private val TEXT_EXT = Regex("\\.(md|json|ts|tsx|js|jsx|py|sql|sh|ya?ml|toml|go|rs|php|java|kt|css|html)$", RegexOption.IGNORE_CASE)

    fun kind(name: String, mime: String): SharedKind = when {
        mime.contains("zip") || name.endsWith(".zip", ignoreCase = true) -> SharedKind.Zip
        mime.startsWith("image/") || mime == "application/pdf" -> SharedKind.Image
        mime.startsWith("text/") || TEXT_EXT.containsMatchIn(name) -> SharedKind.Text
        else -> SharedKind.Other
    }

    /** Gabungkan judul/teks/URL bersama jadi satu teks rapi (tanpa duplikat & tanpa bagian yang termuat di bagian lain). */
    fun text(title: String, text: String, url: String): String {
        val parts = listOf(title, text, url).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        return parts.filterIndexed { i, p -> parts.withIndex().none { (j, o) -> j != i && o.length > p.length && o.contains(p) } }.joinToString("\n")
    }

    /** Batas satu berkas yang diterima = batas Berkas publik (tujuan terbesar). */
    const val MAX_FILE_BYTES: Long = 1024L * 1024 * 1024

    /** Batas jumlah berkas per sekali bagikan dan total ukurannya (melindungi penyimpanan HP). */
    const val MAX_FILES = 20
    const val MAX_TOTAL_BYTES: Long = 2L * 1024 * 1024 * 1024

    /**
     * URI dari aplikasi lain boleh disalin? Hanya `content://` milik aplikasi LAIN.
     *
     * Taskify membuka URI dengan identitasnya sendiri, jadi `file://` atau `content://` milik
     * paket sendiri bisa dipakai aplikasi jahat untuk membuat Taskify membaca berkas privatnya
     * (database, token sesi, kunci brankas) lalu mengunggahnya (pola "Dirty Stream").
     *
     * @param authority authority URI (`null` bila tidak ada).
     * @param ownAuthorities authority content provider milik aplikasi ini.
     * @param ownPackage nama paket aplikasi ini.
     */
    fun isAcceptableSource(scheme: String?, authority: String?, ownAuthorities: Set<String>, ownPackage: String): Boolean {
        if (!scheme.equals("content", ignoreCase = true)) return false
        val a = authority?.trim()?.lowercase()?.substringAfterLast('@') ?: return false
        if (a.isEmpty()) return false
        val pkg = ownPackage.lowercase()
        if (a in ownAuthorities.map { it.lowercase() }) return false
        // Semua authority yang diawali nama paket sendiri (`<paket>.files`, `<paket>.androidx-startup`, …).
        if (a == pkg || a.startsWith("$pkg.")) return false
        return true
    }

    /** Baris pertama sebagai judul tugas/snippet (maks. 200). */
    fun firstLine(title: String, text: String): String = (title.ifBlank { text.lineSequence().firstOrNull { it.isNotBlank() } ?: "" }).trim().ifEmpty { "Dibagikan" }.take(200)
}
