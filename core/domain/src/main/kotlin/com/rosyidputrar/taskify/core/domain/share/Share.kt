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

    /** Baris pertama sebagai judul tugas/snippet (maks. 200). */
    fun firstLine(title: String, text: String): String = (title.ifBlank { text.lineSequence().firstOrNull { it.isNotBlank() } ?: "" }).trim().ifEmpty { "Dibagikan" }.take(200)
}
