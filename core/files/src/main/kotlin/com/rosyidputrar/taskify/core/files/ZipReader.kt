package com.rosyidputrar.taskify.core.files

import com.rosyidputrar.taskify.core.domain.files.ZipEntryInfo
import java.io.File
import java.util.zip.ZipFile

/** Baca isi arsip ZIP lokal (padanan JSZip di webapp). */
object ZipReader {
    const val MAX_TEXT_BYTES = 512 * 1024

    fun entries(file: File): List<ZipEntryInfo> = ZipFile(file).use { zip ->
        zip.entries().asSequence().map { ZipEntryInfo(it.name, it.size.coerceAtLeast(0), it.isDirectory) }.toList()
    }

    /** Isi teks satu file (dipotong di [MAX_TEXT_BYTES]); null bila tidak ada. */
    fun readText(file: File, path: String): Pair<String, Boolean>? = ZipFile(file).use { zip ->
        val entry = zip.getEntry(path) ?: return null
        zip.getInputStream(entry).use { input ->
            val bytes = input.readNBytesCompat(MAX_TEXT_BYTES + 1)
            val truncated = bytes.size > MAX_TEXT_BYTES
            String(if (truncated) bytes.copyOf(MAX_TEXT_BYTES) else bytes, Charsets.UTF_8) to truncated
        }
    }

    fun readBytes(file: File, path: String, max: Int = 20 * 1024 * 1024): ByteArray? = ZipFile(file).use { zip ->
        val entry = zip.getEntry(path) ?: return null
        zip.getInputStream(entry).use { it.readNBytesCompat(max) }
    }

    /** Salin satu entri ke [target] (untuk disimpan/dibagikan). */
    fun extract(file: File, path: String, target: File): Boolean = ZipFile(file).use { zip ->
        val entry = zip.getEntry(path) ?: return false
        zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
        true
    }

    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(32 * 1024)
        var total = 0
        while (total < max) {
            val n = read(buffer, 0, minOf(buffer.size, max - total))
            if (n <= 0) break
            out.write(buffer, 0, n)
            total += n
        }
        return out.toByteArray()
    }
}
