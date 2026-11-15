package com.rosyidputrar.taskify.core.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.rosyidputrar.taskify.core.common.Hashes
import com.rosyidputrar.taskify.core.domain.files.COMPRESS_MAX_SIDE
import com.rosyidputrar.taskify.core.domain.files.COMPRESS_QUALITY
import com.rosyidputrar.taskify.core.domain.files.COMPRESS_THRESHOLD_BYTES
import com.rosyidputrar.taskify.core.domain.files.ZipSummary
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** File yang sudah disalin ke perangkat & dianalisis, siap diunggah (atau dibatalkan). */
data class PreparedFile(
    val file: File,
    val name: String,
    val mimeType: String,
    val size: Long,
    val sha256: String,
    val zip: ZipSummary? = null,
)

internal object FilePrep {
    /** Salin isi [uri] (SAF / bagikan) ke folder sementara aplikasi. */
    fun copy(context: Context, uri: Uri, scratch: File): Triple<File, String, String> {
        val resolver = context.contentResolver
        var name = "file"
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { name = it }
        }
        val mime = resolver.getType(uri) ?: guessMime(name)
        val target = File(scratch, UUID.randomUUID().toString())
        resolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
            ?: throw FileTransferException("File tidak bisa dibaca")
        return Triple(target, name, mime)
    }

    /**
     * Perkecil foto (struk, dll.) sebelum upload: sisi terpanjang maks. 2000 px, JPEG 0,85 — hanya
     * JPEG/PNG/WebP > 1,5 MB, dan hanya bila hasilnya lebih kecil (aturan sama dengan webapp).
     */
    fun compressIfLarge(file: File, name: String, mime: String): Triple<File, String, String> {
        if (mime !in setOf("image/jpeg", "image/png", "image/webp") || file.length() < COMPRESS_THRESHOLD_BYTES) return Triple(file, name, mime)
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            if (longest <= 0) return Triple(file, name, mime)
            var sample = 1
            while (longest / (sample * 2) >= COMPRESS_MAX_SIDE) sample *= 2
            val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return Triple(file, name, mime)
            val scale = minOf(1f, COMPRESS_MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
            val bitmap = if (scale < 1f) Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true) else decoded
            val out = File(file.parentFile, file.name + ".jpg")
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESS_QUALITY, it) }
            if (out.length() >= file.length()) {
                out.delete()
                Triple(file, name, mime)
            } else {
                file.delete()
                Triple(out, name.replace(Regex("\\.(png|webp|jpe?g)$", RegexOption.IGNORE_CASE), "") + ".jpg", "image/jpeg")
            }
        } catch (_: Throwable) {
            Triple(file, name, mime)
        }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                digest.update(buffer, 0, n)
            }
        }
        return Hashes.hex(digest.digest())
    }

    fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "pdf" -> "application/pdf"
        "zip" -> "application/zip"
        "txt", "md" -> "text/plain"
        "json" -> "application/json"
        else -> "application/octet-stream"
    }
}
