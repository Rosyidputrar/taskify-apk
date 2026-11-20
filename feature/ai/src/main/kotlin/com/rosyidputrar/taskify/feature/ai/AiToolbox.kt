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

/** Hasil pemeriksaan alat tulis sebelum ditampilkan ke pengguna. */
sealed interface Prepared {
    /** Argumen sudah dinormalkan (mis. nama dompet → id) dan siap dikonfirmasi. */
    data class Ready(val arguments: String) : Prepared
    /** Tidak valid — dikembalikan langsung ke model tanpa meminta konfirmasi pengguna. */
    data class Invalid(val result: ToolResult) : Prepared
}

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

    /**
     * Periksa & lengkapi argumen alat TULIS sebelum kartu konfirmasi tampil, supaya yang disetujui
     * pengguna sama persis dengan yang disimpan (dompet/kategori/daftar sudah ditemukan, tanggal valid).
     */
    suspend fun prepare(name: String, arguments: String): Prepared = Prepared.Ready(arguments)

    /** Tanpa alat (akses data mati / belum ada alat). */
    object None : AiToolbox {
        override fun specs() = emptyList<AiToolSpec>()
        override fun kind(name: String): ToolKind? = null
        override fun label(name: String) = name
        override fun preview(name: String, arguments: String) = ToolPreview(name)
        override suspend fun run(name: String, arguments: String) = ToolResult("Alat \"$name\" tidak tersedia.", "tidak tersedia", failed = true)
    }
}
