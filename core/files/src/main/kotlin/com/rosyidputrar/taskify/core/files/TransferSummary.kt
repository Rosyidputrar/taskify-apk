package com.rosyidputrar.taskify.core.files

/** Satu unduhan Berkas yang sedang berjalan (unggahan memakai [UploadJob]). */
data class DownloadJob(val key: String, val name: String, val progress: Float? = null)

/** Isi notifikasi transfer; null = tidak ada transfer aktif (layanan boleh berhenti). */
data class TransferSummary(val title: String, val text: String, val progress: Float?, val count: Int)

/**
 * Ringkas transfer aktif untuk notifikasi layanan latar. Progres gabungan dihitung tertimbang
 * ukuran unggahan; bila ada yang belum diketahui progresnya → tak tentu (null).
 */
fun summarizeTransfers(uploads: List<UploadJob>, downloads: List<DownloadJob>): TransferSummary? {
    val active = uploads.filter { it.phase.active }
    val count = active.size + downloads.size
    if (count == 0) return null
    val title = when {
        downloads.isEmpty() -> if (active.size == 1) "Mengunggah ${active[0].name}" else "Mengunggah ${active.size} berkas"
        active.isEmpty() -> if (downloads.size == 1) "Mengunduh ${downloads[0].name}" else "Mengunduh ${downloads.size} berkas"
        else -> "$count transfer berjalan"
    }
    val first = active.firstOrNull()
    val text = when {
        count > 1 -> "Tetap berjalan walau Taskify ditutup"
        first != null -> first.phase.label + if (first.phase == UploadPhase.Uploading) " ${(first.progress * 100).toInt()}%" else ""
        else -> downloads[0].progress?.let { "${(it * 100).toInt()}%" } ?: "Menyiapkan…"
    }
    val progress = if (downloads.any { it.progress == null } || active.any { it.phase != UploadPhase.Uploading && it.phase != UploadPhase.Verifying }) {
        null
    } else {
        val weights = active.map { it.size.coerceAtLeast(1L).toDouble() to it.progress.toDouble() } + downloads.map { 1.0 to (it.progress ?: 0f).toDouble() }
        val total = weights.sumOf { it.first }
        (weights.sumOf { it.first * it.second } / total).toFloat().coerceIn(0f, 1f)
    }
    return TransferSummary(title, text, progress, count)
}
