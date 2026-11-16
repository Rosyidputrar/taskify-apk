package com.rosyidputrar.taskify.ui.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.rosyidputrar.taskify.core.domain.share.Share
import com.rosyidputrar.taskify.core.domain.share.SharedKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class SharedFile(val key: String, val file: File, val name: String, val mime: String, val size: Long) {
    val kind: SharedKind get() = Share.kind(name, mime)
}

/** [skipped] = berkas yang ditolak (sumber tidak aman, terlalu besar, atau melebihi batas jumlah). */
data class ShareContent(val title: String, val text: String, val url: String, val files: List<SharedFile>, val skipped: Int = 0) {
    val merged: String get() = Share.text(title, text, url)
    val isEmpty: Boolean get() = merged.isBlank() && files.isEmpty()
}

/**
 * Kotak masuk "Bagikan ke Taskify" (padanan service worker + cache webapp). Berkas langsung
 * disalin ke cache aplikasi saat diterima — izin baca `content://` dari aplikasi pengirim hanya
 * berlaku selama Activity penerima hidup.
 */
class ShareInbox(
    private val context: Context,
    private val maxFileBytes: Long = Share.MAX_FILE_BYTES,
    private val maxFiles: Int = Share.MAX_FILES,
    private val maxTotalBytes: Long = Share.MAX_TOTAL_BYTES,
) {
    private val _content = MutableStateFlow<ShareContent?>(null)
    val content: StateFlow<ShareContent?> = _content.asStateFlow()

    private val dir get() = File(context.cacheDir, "share").apply { mkdirs() }

    suspend fun receive(intent: Intent): Boolean = withContext(Dispatchers.IO) {
        val uris = buildList {
            if (intent.action == Intent.ACTION_SEND) intent.stream()?.let(::add)
            if (intent.action == Intent.ACTION_SEND_MULTIPLE) addAll(intent.streams())
        }
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        val title = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
        clearFiles()
        val files = mutableListOf<SharedFile>()
        var total = 0L
        for (uri in uris) {
            if (files.size >= maxFiles) break
            val file = copy(uri, maxBytes = minOf(maxFileBytes, maxTotalBytes - total)) ?: continue
            files += file
            total += file.size
        }
        // URL terpisah tidak dikirim Android (selalu di EXTRA_TEXT) — biarkan kosong.
        val content = ShareContent(title, text, "", files, skipped = uris.size - files.size)
        _content.value = content.takeUnless { it.isEmpty }
        !content.isEmpty
    }

    @Suppress("DEPRECATION")
    private fun Intent.stream(): Uri? = if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else getParcelableExtra(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun Intent.streams(): List<Uri> =
        (if (Build.VERSION.SDK_INT >= 33) getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) else getParcelableArrayListExtra(Intent.EXTRA_STREAM)).orEmpty()

    /** Authority content provider milik aplikasi ini (FileProvider lampiran, dll.). */
    private val ownAuthorities: Set<String> by lazy {
        runCatching {
            val flags = android.content.pm.PackageManager.GET_PROVIDERS
            val info = if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION") context.packageManager.getPackageInfo(context.packageName, flags)
            }
            info.providers.orEmpty().flatMap { it.authority.orEmpty().split(';') }.filter { it.isNotBlank() }.toSet()
        }.getOrDefault(emptySet())
    }

    /**
     * Salin satu berkas ke cache. null = ditolak: bukan `content://` milik aplikasi lain (lihat
     * [Share.isAcceptableSource]), tidak terbaca, atau lebih besar dari [maxBytes] (dihitung saat
     * menyalin — ukuran yang dilaporkan pengirim tidak dipercaya).
     */
    private fun copy(uri: Uri, maxBytes: Long): SharedFile? {
        if (!Share.isAcceptableSource(uri.scheme, uri.authority, ownAuthorities, context.packageName)) return null
        if (maxBytes <= 0) return null
        val key = UUID.randomUUID().toString()
        val target = File(dir, key)
        return runCatching {
            val resolver = context.contentResolver
            var name = uri.lastPathSegment?.substringAfterLast('/') ?: "berkas"
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0)?.let { name = it } }
            val mime = resolver.getType(uri) ?: "application/octet-stream"
            val copied = resolver.openInputStream(uri)?.use { input -> target.outputStream().use { out -> copyLimited(input, out, maxBytes) } }
            if (copied == null) {
                target.delete()
                null
            } else {
                SharedFile(key, target, name.replace('/', '_'), mime, copied)
            }
        }.getOrElse {
            target.delete()
            null
        }
    }

    private fun copyLimited(input: java.io.InputStream, out: java.io.OutputStream, maxBytes: Long): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) return total
            total += n
            if (total > maxBytes) throw java.io.IOException("Berkas terlalu besar")
            out.write(buffer, 0, n)
        }
    }

    /** Tandai satu item selesai (berkasnya dihapus dari cache). */
    fun done(key: String) {
        val c = _content.value ?: return
        if (key == TEXT_KEY) {
            _content.value = c.copy(title = "", text = "", url = "").takeUnless { it.isEmpty }
        } else {
            c.files.firstOrNull { it.key == key }?.file?.delete()
            _content.value = c.copy(files = c.files.filterNot { it.key == key }).takeUnless { it.isEmpty }
        }
    }

    fun clear() {
        _content.value = null
        clearFiles()
    }

    private fun clearFiles() { dir.listFiles()?.forEach { it.delete() } }

    companion object {
        const val TEXT_KEY = "text"
    }
}
