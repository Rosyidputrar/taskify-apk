package com.rosyidputrar.taskify.core.ui.share

import android.content.Context
import com.rosyidputrar.taskify.core.domain.text.DocExport
import com.rosyidputrar.taskify.core.domain.text.ShareFormat
import com.rosyidputrar.taskify.core.domain.vault.SnippetExport
import com.rosyidputrar.taskify.core.domain.vault.SnippetFormat
import com.rosyidputrar.taskify.core.domain.vault.languageLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Snippet yang dibagikan. */
data class ShareCode(val title: String, val language: String, val code: String, val description: String?, val accent: Int = DocRender.TEAL)

/**
 * Bagikan & salin dokumen (catatan, jurnal) dan snippet kode. Fungsi gambar mengembalikan
 * `true` bila isinya terlalu panjang dan gambar dipotong (pemanggil memberi tahu pengguna).
 */
interface ShareService {
    fun shareText(doc: ShareDoc)
    suspend fun shareFile(doc: ShareDoc, format: ShareFormat): Boolean
    suspend fun copyImage(doc: ShareDoc): Boolean

    fun shareCodeText(code: ShareCode)
    suspend fun shareCode(code: ShareCode, format: SnippetFormat): Boolean
    suspend fun copyCodeImage(code: ShareCode): Boolean
}

class AndroidShareService(private val context: Context) : ShareService {
    override fun shareText(doc: ShareDoc) =
        ShareFiles.shareText(context, DocExport.plainText(doc.title, doc.markdown, doc.meta).trimEnd(), doc.title, "Bagikan ${doc.label.lowercase()}")

    override suspend fun shareFile(doc: ShareDoc, format: ShareFormat): Boolean {
        val name = DocExport.fileName(doc.title, doc.markdown, format)
        val folder = doc.label.lowercase()
        if (format == ShareFormat.Image) {
            val img = withContext(Dispatchers.Default) { DocRender.image(doc) }
            ShareFiles.shareImage(context, folder, name, img.bitmap, doc.title)
            return img.truncated
        }
        ShareFiles.shareFile(context, folder, name, format.mime, doc.title) { f ->
            when (format) {
                ShareFormat.Markdown -> f.writeText(DocExport.markdown(doc.title, doc.markdown, doc.meta))
                ShareFormat.Text -> f.writeText(DocExport.plainText(doc.title, doc.markdown, doc.meta))
                ShareFormat.Html -> f.writeText(DocExport.html(doc.title, doc.markdown, doc.meta))
                ShareFormat.Pdf -> f.outputStream().use { DocRender.writePdf(it, doc) }
                ShareFormat.Image -> Unit
            }
        }
        return false
    }

    override suspend fun copyImage(doc: ShareDoc): Boolean {
        val img = withContext(Dispatchers.Default) { DocRender.image(doc) }
        ShareFiles.copyImage(context, DocExport.safeName(doc.title ?: doc.label), img.bitmap)
        return img.truncated
    }

    override fun shareCodeText(code: ShareCode) =
        ShareFiles.shareText(context, SnippetExport.markdown(code.title, code.language, code.code, code.description).trimEnd(), code.title, "Bagikan snippet")

    override suspend fun shareCode(code: ShareCode, format: SnippetFormat): Boolean = when (format) {
        SnippetFormat.Source -> {
            // text/plain: lebih banyak aplikasi menerima daripada MIME khusus bahasa.
            ShareFiles.shareFile(context, "snippet", SnippetExport.fileName(code.title, code.language), "text/plain", code.title) { it.writeText(SnippetExport.source(code.code)) }
            false
        }
        SnippetFormat.Markdown -> {
            ShareFiles.shareFile(context, "snippet", SnippetExport.markdownFileName(code.title), "text/markdown", code.title) {
                it.writeText(SnippetExport.markdown(code.title, code.language, code.code, code.description))
            }
            false
        }
        SnippetFormat.Image -> {
            val img = withContext(Dispatchers.Default) { renderCode(code) }
            ShareFiles.shareImage(context, "snippet", DocExport.fileName(code.title, "", "png", "snippet"), img.bitmap, code.title)
            img.truncated
        }
    }

    override suspend fun copyCodeImage(code: ShareCode): Boolean {
        val img = withContext(Dispatchers.Default) { renderCode(code) }
        ShareFiles.copyImage(context, DocExport.safeName(code.title).ifEmpty { "snippet" }, img.bitmap)
        return img.truncated
    }

    private fun renderCode(code: ShareCode) = CodeImage.render(code.code, code.language, code.title, languageLabel(code.language), code.accent)
}
