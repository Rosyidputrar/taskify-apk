package com.rosyidputrar.taskify.core.domain.files

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Jenis entitas yang bisa punya lampiran (kolom `attachments.entity_type`, sama dengan webapp). */
enum class EntityType(val value: String) {
    Task("task"), JournalEntry("journal_entry"), Transaction("transaction"), Snippet("snippet"), CodeProject("code_project"), Note("note");

    companion object {
        fun from(value: String?) = entries.firstOrNull { it.value == value }
    }
}

enum class AttachmentStatus(val value: String) {
    Pending("pending"), Uploaded("uploaded"), Failed("failed");

    companion object {
        fun from(value: String?) = entries.firstOrNull { it.value == value } ?: Pending
    }
}

data class Attachment(
    val id: String,
    val entityType: String,
    val entityId: String,
    val storageKey: String,
    val fileName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val sha256: String?,
    val versionLabel: String?,
    val note: String?,
    val status: AttachmentStatus,
    val uploadedAt: String?,
    val createdAt: String,
) {
    val isImage: Boolean get() = mimeType?.startsWith("image/") == true || isImagePath(fileName)
    val isPdf: Boolean get() = mimeType == "application/pdf" || fileName.endsWith(".pdf", ignoreCase = true)
    val isZip: Boolean get() = mimeType in ZIP_MIMES || fileName.endsWith(".zip", ignoreCase = true)
}

private val ZIP_MIMES = setOf("application/zip", "application/x-zip-compressed", "application/x-zip")

/** Batas ukuran per file (ditegakkan Edge Function `files` lewat Content-Length bertanda tangan). */
const val MAX_FILE_BYTES = 100L * 1024 * 1024

/** Foto lebih besar dari ini diperkecil (sisi terpanjang 2000 px, JPEG 0,85) — sama dengan webapp. */
const val COMPRESS_THRESHOLD_BYTES = (1.5 * 1024 * 1024).toLong()
const val COMPRESS_MAX_SIDE = 2000
const val COMPRESS_QUALITY = 85

/**
 * "<user>/<lampiran>/<nama aman>" — format yang divalidasi Edge Function (port `storageKey`):
 * pemisah path & karakter kontrol diganti "_", nama tidak boleh diawali titik, maks. 200 karakter
 * terakhir.
 */
fun storageKey(userId: String, attachmentId: String, fileName: String): String {
    val cleaned = buildString {
        fileName.forEach { c -> append(if (c == '/' || c == '\\' || c.code < 32) '_' else c) }
    }.replace(Regex("^\\.+"), "_")
    val safe = cleaned.takeLast(200).ifEmpty { "file" }
    return "$userId/$attachmentId/$safe"
}

/** 1536 → "1,5 KB" (format id-ID, sama dengan `formatBytes`). */
fun formatBytes(bytes: Long?): String {
    if (bytes == null) return "–"
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    val fmt = DecimalFormat(if (unit == 0) "#,##0" else "#,##0.#", DecimalFormatSymbols(Locale.forLanguageTag("id-ID")))
    return "${fmt.format(value)} ${units[unit]}"
}

// ------------------------------------------------------------------ kebijakan unggah ulang

const val MAX_AUTO_RETRIES = 3
const val MAX_PER_RUN = 5
const val MIN_RETRY_GAP_MS = 60_000L

data class AutoAttempt(val n: Int, val at: Long)

/** Maks. 3 percobaan otomatis per lampiran, jeda ≥ 1 menit (port `canAutoRetry`). */
fun canAutoRetry(prev: AutoAttempt?, now: Long): Boolean = prev == null || (prev.n < MAX_AUTO_RETRIES && now - prev.at >= MIN_RETRY_GAP_MS)

/** Label lampiran per jenis entitas (judul bagian di layar). */
fun attachmentLabel(entityType: EntityType): String = when (entityType) {
    EntityType.JournalEntry -> "Foto & lampiran"
    EntityType.Transaction -> "Struk & lampiran"
    EntityType.CodeProject -> "Versi"
    else -> "Lampiran"
}
