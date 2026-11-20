package com.rosyidputrar.taskify.core.ui.share

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Bagikan & salin lewat sistem Android, tanpa izin penyimpanan: berkas ditulis ke
 * `cache/shared/<folder>/` lalu diberikan lewat FileProvider (`<paket>.files`). Tiap folder hanya
 * menyimpan berkas terakhir — yang lama dibersihkan.
 */
object ShareFiles {
    private fun authority(context: Context) = "${context.packageName}.files"

    /** Kirim teks (chat, surel) lewat lembar bagikan. */
    fun shareText(context: Context, text: String, subject: String? = null, chooserTitle: String = "Bagikan") {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        subject?.takeIf { it.isNotBlank() }?.let { intent.putExtra(Intent.EXTRA_SUBJECT, it) }
        start(context, intent, chooserTitle)
    }

    /** Tulis berkas [name] di folder [folder] dengan [write], lalu buka lembar bagikan. */
    suspend fun shareFile(context: Context, folder: String, name: String, mime: String, subject: String? = null, write: (File) -> Unit) {
        val file = withContext(Dispatchers.IO) { fresh(context, folder, name).also(write) }
        val uri = FileProvider.getUriForFile(context, authority(context), file)
        val intent = Intent(Intent.ACTION_SEND).setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // ClipData → izin baca ikut ke aplikasi tujuan & pratinjau di lembar bagikan.
        intent.clipData = ClipData.newRawUri(file.name, uri)
        subject?.takeIf { it.isNotBlank() }?.let { intent.putExtra(Intent.EXTRA_SUBJECT, it) }
        start(context, intent, file.name)
    }

    /** Bagikan gambar PNG. Bitmap didaur ulang setelah ditulis. */
    suspend fun shareImage(context: Context, folder: String, name: String, bitmap: Bitmap, subject: String? = null) =
        shareFile(context, folder, name, "image/png", subject) { f -> writePng(bitmap, f) }

    /** Salin teks; [sensitive] = Android 13+ menyembunyikan pratinjaunya. */
    fun copyText(context: Context, label: String, text: String, sensitive: Boolean = false) {
        val clip = ClipData.newPlainText(label, text)
        if (sensitive && Build.VERSION.SDK_INT >= 33) {
            clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        }
        clipboard(context).setPrimaryClip(clip)
    }

    /**
     * Salin gambar ke papan klip (bisa ditempel di WhatsApp, Gmail, Docs, keyboard yang mendukung).
     * Isinya lewat URI FileProvider — aplikasi penempel diberi izin baca oleh sistem.
     */
    suspend fun copyImage(context: Context, label: String, bitmap: Bitmap): Uri {
        val file = withContext(Dispatchers.IO) { fresh(context, "clipboard", "${label.ifBlank { "gambar" }.take(40)}.png").also { writePng(bitmap, it) } }
        val uri = FileProvider.getUriForFile(context, authority(context), file)
        val clip = ClipData(ClipDescription(label, arrayOf("image/png")), ClipData.Item(uri))
        clipboard(context).setPrimaryClip(clip)
        return uri
    }

    private fun clipboard(context: Context) = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private fun fresh(context: Context, folder: String, name: String): File {
        val dir = File(context.cacheDir, "shared/$folder").apply { deleteRecursively(); mkdirs() }
        return File(dir, name.replace('/', ' '))
    }

    private fun writePng(bitmap: Bitmap, file: File) {
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
    }

    private fun start(context: Context, intent: Intent, title: String) {
        context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
