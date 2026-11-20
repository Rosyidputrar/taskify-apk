package com.rosyidputrar.taskify.core.domain.vault

import com.rosyidputrar.taskify.core.domain.text.DocExport

/** Cara membagikan snippet. */
enum class SnippetFormat(val label: String, val hint: String) {
    Source("Berkas kode", "Ekstensi sesuai bahasa — langsung bisa dibuka di editor"),
    Markdown("Markdown", "Blok kode berpagar + judul & deskripsi"),
    Image("Gambar kode", "Kartu kode berwarna — pas untuk chat & dokumentasi"),
}

/** Ekspor snippet — murni & teruji JVM. */
object SnippetExport {
    /** Ekstensi berkas untuk [language] (mis. kotlin → kt); tak dikenal → txt. */
    fun extension(language: String): String = LANGUAGES.firstOrNull { it.value == language }?.ext?.firstOrNull() ?: "txt"

    /** Nama pagar Markdown (```kotlin); teks biasa tanpa nama. */
    fun fence(language: String): String = if (language == "plaintext") "" else language

    fun fileName(title: String, language: String): String = DocExport.fileName(title, "", extension(language), fallback = "snippet")

    fun markdownFileName(title: String): String = DocExport.fileName(title, "", "md", fallback = "snippet")

    /** Judul, deskripsi, lalu kode dalam pagar yang tidak bentrok dengan ``` di dalam kode. */
    fun markdown(title: String, language: String, code: String, description: String?): String {
        val longest = Regex("`{3,}").findAll(code).maxOfOrNull { it.value.length } ?: 0
        val ticks = "`".repeat(maxOf(3, longest + 1))
        return buildString {
            title.trim().takeIf { it.isNotEmpty() }?.let { append("# ").append(it).append("\n\n") }
            description?.trim()?.takeIf { it.isNotEmpty() }?.let { append(it).append("\n\n") }
            append(ticks).append(fence(language)).append('\n').append(code.trimEnd('\n')).append('\n').append(ticks).append('\n')
        }
    }

    /** Isi berkas sumber: kode apa adanya, diakhiri satu baris baru. */
    fun source(code: String): String = code.trimEnd('\n') + "\n"
}
