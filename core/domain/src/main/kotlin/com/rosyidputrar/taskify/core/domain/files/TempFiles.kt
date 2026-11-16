package com.rosyidputrar.taskify.core.domain.files

import com.rosyidputrar.taskify.core.domain.time.Dates
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Berkas publik (tempfile) — port `tempfile-utils.ts` + `tempfile-limits.ts` webapp. Server
 * (Edge Function `tempfiles` + migrasi …_temp_files.sql) adalah sumber kebenaran; angka di sini
 * HARUS sama, dipakai untuk tampilan dan pemeriksaan awal yang ramah.
 */
object TempFileRules {
    /** 1 GB untuk SEMUA berkas satu akun. */
    const val QUOTA_BYTES: Long = 1024L * 1024 * 1024
    /** Pesanan unggah (pending) ikut kuota selama ini; sama dengan temp_files_used_bytes(). */
    const val PENDING_HOLD_MS: Long = 2 * 3600_000L
    const val MAX_NAME = 255
    const val TOKEN_LENGTH = 43
}

enum class ExpiryOption(val value: String, val label: String) {
    Hour("1h", "1 jam"), Day("1d", "1 hari"), Week("7d", "7 hari"), Month("30d", "30 hari"), Never("never", "Tanpa batas");

    companion object {
        val DEFAULT = Week
    }
}

enum class TempFileStatus(val value: String) {
    Pending("pending"), Ready("ready");

    companion object {
        fun from(v: String?) = if (v == "ready") Ready else Pending
    }
}

/** Baris `temp_files` (lokal atau dari respons server). Hanya server yang menulisnya. */
data class TempFile(
    val id: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val status: TempFileStatus,
    val shareToken: String,
    val isPublic: Boolean,
    val expiresAt: String?,
    val downloadCount: Int,
    val lastDownloadAt: String?,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String? = null,
)

enum class TempFileState { Ready, Pending, Expired }

enum class FileKind { Image, Video, Audio, Archive, Sheet, Code, Document, Other }

private fun ms(ts: String): Long = Dates.parseTimestampMillis(ts) ?: 0L

object TempFiles {
    fun isExpired(expiresAt: String?, nowMs: Long) = expiresAt != null && ms(expiresAt) <= nowMs

    /** Masih memakan kuota? Aturan SAMA dengan public.temp_files_used_bytes(). */
    fun countsTowardQuota(f: TempFile, nowMs: Long): Boolean {
        if (f.deletedAt != null || isExpired(f.expiresAt, nowMs)) return false
        return f.status == TempFileStatus.Ready || nowMs - ms(f.createdAt) < TempFileRules.PENDING_HOLD_MS
    }

    fun usedBytes(rows: List<TempFile>, nowMs: Long): Long = rows.filter { countsTowardQuota(it, nowMs) }.sumOf { it.sizeBytes }

    fun state(f: TempFile, nowMs: Long): TempFileState = when {
        isExpired(f.expiresAt, nowMs) -> TempFileState.Expired
        f.status == TempFileStatus.Ready -> TempFileState.Ready
        else -> TempFileState.Pending
    }

    /** Tautan bisa dibagikan sekarang? */
    fun isShareable(f: TempFile, nowMs: Long) = f.isPublic && state(f, nowMs) == TempFileState.Ready

    /** Halaman publik webapp: `/unduh/<token>` (juga dibuka aplikasi ini lewat App Link). */
    fun shareUrl(origin: String, token: String) = "${origin.trimEnd('/')}/unduh/$token"

    /** URL unduhan langsung (Edge Function, tanpa login). */
    fun directDownloadUrl(supabaseUrl: String, token: String) = "${supabaseUrl.trimEnd('/')}/functions/v1/tempfiles/d/$token"

    private val TOKEN_PATH = Regex("^/unduh/([A-Za-z0-9_-]{43})/?$")

    /** Token dari path; null bila bukan halaman unduh / token terpotong. */
    fun shareTokenFromPath(path: String): String? = TOKEN_PATH.find(path)?.groupValues?.get(1)

    /** Semua path di bawah `/unduh` adalah halaman publik — termasuk tautan yang rusak saat disalin. */
    fun isPublicDownloadPath(path: String) = Regex("^/unduh(/|$)").containsMatchIn(path)

    /** "3 hari" / "5 jam" / "12 menit"; null = tanpa batas; "" = sudah lewat. Pembulatan sama dengan webapp. */
    private fun timeLeft(expiresAt: String?, nowMs: Long): String? {
        if (expiresAt == null) return null
        val left = ms(expiresAt) - nowMs
        if (left <= 0) return ""
        return when {
            left < 59.5 * 60_000 -> "${max(1L, ceil(left / 60_000.0).toLong())} menit"
            left < 47.5 * 3600_000 -> "${jsRound(left / 3600_000.0)} jam"
            else -> "${jsRound(left / 86_400_000.0)} hari"
        }
    }

    /** Math.round JavaScript (setengah ke atas). */
    private fun jsRound(x: Double): Long = kotlin.math.floor(x + 0.5).roundToLong()

    fun expiryLabel(expiresAt: String?, nowMs: Long): String {
        val left = timeLeft(expiresAt, nowMs) ?: return "Tanpa batas waktu"
        return if (left.isEmpty()) "Kedaluwarsa" else "Kedaluwarsa dalam $left"
    }

    fun expiryShort(expiresAt: String?, nowMs: Long): String {
        val left = timeLeft(expiresAt, nowMs) ?: return "Tanpa batas"
        return if (left.isEmpty()) "Kedaluwarsa" else "Sisa $left"
    }

    /** Pemeriksaan awal sebelum memesan tempat. [pending] = byte berkas lain di antrean yang sama. */
    fun quotaProblem(size: Long, rows: List<TempFile>, nowMs: Long, pending: Long = 0): String? = when {
        size <= 0 -> "Berkas kosong tidak bisa diunggah."
        size > TempFileRules.QUOTA_BYTES -> "Ukuran berkas melebihi 1 GB."
        usedBytes(rows, nowMs) + pending + size > TempFileRules.QUOTA_BYTES -> "Kuota 1 GB tidak cukup. Hapus berkas lain dulu."
        else -> null
    }

    /**
     * Hasil server lebih baru daripada salinan lokal sampai sinkron menyusul. Nilai overlay
     * null = dihapus di server.
     */
    fun mergeOverlay(local: List<TempFile>, overlay: Map<String, TempFile?>): List<TempFile> = local.mapNotNull { row ->
        if (!overlay.containsKey(row.id)) return@mapNotNull row
        val o = overlay[row.id] ?: return@mapNotNull null
        if (ms(o.updatedAt) > ms(row.updatedAt)) o else row
    }

    /** Overlay yang sudah tersusul sinkron (boleh dibuang). */
    fun settledOverlayIds(local: List<TempFile>, overlay: Map<String, TempFile?>): List<String> {
        val byId = local.associateBy { it.id }
        return overlay.filter { (id, o) ->
            val row = byId[id]
            if (o == null) row == null else row != null && ms(row.updatedAt) >= ms(o.updatedAt)
        }.keys.toList()
    }

    /** Daftar akhir: berkas baru dari overlay (belum tersinkron) di atas, lalu gabungan. */
    fun withOverlay(local: List<TempFile>, overlay: Map<String, TempFile?>): List<TempFile> {
        val known = local.map { it.id }.toSet()
        val fresh = overlay.values.filterNotNull().filter { it.id !in known && it.deletedAt == null }
        return fresh + mergeOverlay(local, overlay)
    }

    private val ERROR_MESSAGE = mapOf(
        "TF001" to "Kuota 1 GB penuh. Hapus berkas lain dulu.",
        "TF002" to "Berkas tidak ditemukan (mungkin sudah dihapus).",
        "TF003" to "Berkas sudah kedaluwarsa.",
        "TF004" to "Unggahan tidak lengkap. Coba unggah ulang.",
        "TF005" to "Permintaan tidak valid.",
    )

    fun errorMessage(code: String?, fallback: String) = code?.let { ERROR_MESSAGE[it] } ?: fallback

    fun kind(mime: String, name: String): FileKind {
        val ext = if ('.' in name) name.substringAfterLast('.').lowercase() else ""
        return when {
            mime.startsWith("image/") -> FileKind.Image
            mime.startsWith("video/") -> FileKind.Video
            mime.startsWith("audio/") -> FileKind.Audio
            Regex("zip|rar|7z|tar|gzip").containsMatchIn(mime) || ext in setOf("zip", "rar", "7z", "gz", "tgz") -> FileKind.Archive
            Regex("sheet|excel|csv").containsMatchIn(mime) || ext in setOf("xlsx", "xls", "csv", "ods") -> FileKind.Sheet
            ext in setOf("js", "ts", "tsx", "json", "py", "sql", "sh", "html", "css", "md") -> FileKind.Code
            mime.startsWith("text/") || mime == "application/pdf" || ext in setOf("doc", "docx") -> FileKind.Document
            else -> FileKind.Other
        }
    }

    /** Ekstensi program yang bisa dijalankan: halaman publik memberi peringatan. */
    fun isExecutableName(name: String) =
        Regex("\\.(exe|msi|bat|cmd|com|scr|apk|appimage|dmg|pkg|sh|ps1|vbs|js|jar|lnk)$", RegexOption.IGNORE_CASE).containsMatchIn(name.trim())
}
