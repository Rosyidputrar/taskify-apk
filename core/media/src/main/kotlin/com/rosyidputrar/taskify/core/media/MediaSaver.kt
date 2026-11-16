package com.rosyidputrar.taskify.core.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStream

/** Berkas tersimpan: [uri] bisa dibuka / dibagikan. */
data class SavedMedia(val uri: Uri, val name: String, val location: String)

/**
 * Simpan unduhan ke folder Download/Taskify. Android 10+: MediaStore (tanpa izin); Android 8–9:
 * folder Download milik aplikasi (tanpa izin penyimpanan), dibuka lewat FileProvider.
 */
class MediaSaver(private val context: Context) {
    /** Buka tujuan penulisan; panggil [Pending.finish] setelah selesai menulis. */
    fun create(name: String, mime: String): Pending {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Taskify")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Tidak bisa membuat berkas di Download")
            return object : Pending {
                override fun open(): OutputStream = resolver.openOutputStream(uri) ?: error("Tidak bisa menulis berkas")
                override fun finish(finalName: String): SavedMedia {
                    resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0); put(MediaStore.Downloads.DISPLAY_NAME, finalName) }, null, null)
                    return SavedMedia(uri, finalName, "Download/Taskify")
                }
                override fun abort() { resolver.delete(uri, null, null) }
            }
        }
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Taskify").apply { mkdirs() }
        val tmp = File(dir, "$name.part")
        return object : Pending {
            override fun open(): OutputStream = tmp.outputStream()
            override fun finish(finalName: String): SavedMedia {
                val target = File(dir, finalName.replace('/', '_'))
                tmp.renameTo(target)
                return SavedMedia(FileProvider.getUriForFile(context, "${context.packageName}.files", target), target.name, "folder Download aplikasi")
            }
            override fun abort() { tmp.delete() }
        }
    }

    interface Pending {
        fun open(): OutputStream
        fun finish(finalName: String): SavedMedia
        fun abort()
    }
}
