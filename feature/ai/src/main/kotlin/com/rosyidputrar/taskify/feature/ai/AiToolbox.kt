package com.rosyidputrar.taskify.feature.ai

import com.rosyidputrar.taskify.core.ai.AiToolSpec

enum class ToolKind {
    /** Membaca data — langsung dijalankan. */
    Read,
    /** Membuat / mengubah data — selalu menunggu persetujuan pengguna. */
    Write,
}

/** Ringkasan panggilan alat untuk ditampilkan (kartu konfirmasi / baris aktivitas). */
data class ToolPreview(val title: String, val lines: List<Pair<String, String>> = emptyList())

/** Hasil alat: [text] dikirim ke model; [summary] ringkas untuk pengguna (mis. "5 tugas"). */
data class ToolResult(val text: String, val summary: String, val failed: Boolean = false)

/**
 * Alat yang boleh dipakai asisten. Hanya diberikan ke model bila sesi mengaktifkan "Akses data
 * Taskify". Sandi TIDAK PERNAH termasuk.
 */
interface AiToolbox {
    fun specs(): List<AiToolSpec>
    fun kind(name: String): ToolKind?
    /** Label aktivitas, mis. "Mencari catatan". */
    fun label(name: String): String
    fun preview(name: String, arguments: String): ToolPreview
    suspend fun run(name: String, arguments: String): ToolResult

    /** Tanpa alat (akses data mati / belum ada alat). */
    object None : AiToolbox {
        override fun specs() = emptyList<AiToolSpec>()
        override fun kind(name: String): ToolKind? = null
        override fun label(name: String) = name
        override fun preview(name: String, arguments: String) = ToolPreview(name)
        override suspend fun run(name: String, arguments: String) = ToolResult("Alat \"$name\" tidak tersedia.", "tidak tersedia", failed = true)
    }
}
