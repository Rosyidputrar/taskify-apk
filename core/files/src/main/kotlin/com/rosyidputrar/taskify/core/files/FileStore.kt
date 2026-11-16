package com.rosyidputrar.taskify.core.files

import android.content.Context
import java.io.File
import java.io.InputStream

/**
 * Salinan isi lampiran di perangkat (padanan Cache Storage webapp): file disimpan DULU sebelum
 * diunggah supaya tidak hilang saat offline/aplikasi ditutup, dan file yang pernah dibuka bisa
 * dibuka lagi tanpa internet. Dihapus semua saat keluar akun.
 */
class FileStore(context: Context) {
    private val dir = File(context.filesDir, "attachments").apply { mkdirs() }
    private val shareDir = File(context.cacheDir, "shared").apply { mkdirs() }
    val scratchDir: File = File(context.cacheDir, "incoming").apply { mkdirs() }

    fun file(id: String): File = File(dir, id)
    fun has(id: String): Boolean = file(id).let { it.isFile && it.length() > 0 }

    fun put(id: String, source: File): File {
        val target = file(id)
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
            source.delete()
        }
        return target
    }

    fun put(id: String, input: InputStream): File {
        val tmp = File(dir, "$id.part")
        tmp.outputStream().use { input.copyTo(it) }
        val target = file(id)
        tmp.renameTo(target)
        return target
    }

    fun delete(id: String) {
        file(id).delete()
    }

    /** Salinan bernama asli untuk dibuka aplikasi lain (nama file terlihat di penampil). */
    fun shareCopy(id: String, fileName: String): File {
        val safe = fileName.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").ifBlank { "file" }
        val target = File(File(shareDir, id).apply { mkdirs() }, safe)
        if (!target.exists() || target.length() != file(id).length()) file(id).copyTo(target, overwrite = true)
        return target
    }

    fun usedBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0

    fun clearAll() {
        dir.listFiles()?.forEach { it.delete() }
        shareDir.deleteRecursively()
        shareDir.mkdirs()
        scratchDir.listFiles()?.forEach { it.delete() }
    }
}
