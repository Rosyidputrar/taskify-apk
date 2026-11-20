package com.rosyidputrar.taskify.feature.notes.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.rosyidputrar.taskify.core.domain.notes.NoteExport
import com.rosyidputrar.taskify.core.domain.notes.NoteFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Isi catatan yang dibagikan. [subtitle] mis. "Ditempel ke Jumat, 20 November 2026". */
data class NoteShareContent(val title: String?, val content: String, val subtitle: String? = null)

/** Membagikan catatan lewat lembar bagikan Android. */
interface NoteSharer {
    /** Kirim isi sebagai teks biasa (chat, surel) — tanpa berkas. */
    fun shareText(note: NoteShareContent)

    /** Buat berkas [format] lalu bagikan. */
    suspend fun shareFile(note: NoteShareContent, format: NoteFormat)
}

class AndroidNoteSharer(private val context: Context) : NoteSharer {
    override fun shareText(note: NoteShareContent) {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, NoteExport.plainText(note.title, note.content).trimEnd())
        note.title?.takeIf { it.isNotBlank() }?.let { intent.putExtra(Intent.EXTRA_SUBJECT, it) }
        start(intent)
    }

    override suspend fun shareFile(note: NoteShareContent, format: NoteFormat) {
        val file = withContext(Dispatchers.IO) {
            // Satu berkas bagikan saja yang disimpan di cache — yang lama dibersihkan.
            val dir = File(context.cacheDir, "shared/notes").apply { deleteRecursively(); mkdirs() }
            File(dir, NoteExport.fileName(note.title, note.content, format)).also { f ->
                when (format) {
                    NoteFormat.Markdown -> f.writeText(NoteExport.markdown(note.title, note.content))
                    NoteFormat.Text -> f.writeText(NoteExport.plainText(note.title, note.content))
                    NoteFormat.Html -> f.writeText(NoteExport.html(note.title, note.content, note.subtitle))
                    NoteFormat.Pdf -> f.outputStream().use { NotePdf.write(it, note.title, note.content, note.subtitle) }
                }
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).setType(format.mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // ClipData → izin baca ikut ke aplikasi tujuan & pratinjau di lembar bagikan.
        intent.clipData = ClipData.newRawUri(file.name, uri)
        note.title?.takeIf { it.isNotBlank() }?.let { intent.putExtra(Intent.EXTRA_SUBJECT, it) }
        start(intent, file.name)
    }

    private fun start(intent: Intent, title: String = "Bagikan catatan") {
        context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
