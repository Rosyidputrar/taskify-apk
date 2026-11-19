package com.rosyidputrar.taskify.feature.sandi

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.rosyidputrar.taskify.core.domain.sandi.ImportException
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import java.io.FileNotFoundException
import java.io.IOException

val sandiFeatureModule = module {
    single { ContentResolverFiles(androidContext()) }
    single<SandiFileWriter> { get<ContentResolverFiles>() }
    single<SandiDocuments> { get<ContentResolverFiles>() }
    viewModel { SandiViewModel(get(), get(), get(), get(), get(), get(), get()) }
}

/** Berkas lewat pemilih sistem (SAF): tulis ekspor, baca & hapus berkas impor. */
internal class ContentResolverFiles(private val context: Context) : SandiFileWriter, SandiDocuments {
    /** Mode "wt" memangkas isi lama bila berkas yang ada ditimpa. */
    override suspend fun write(uri: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val target = Uri.parse(uri)
        val stream = try {
            context.contentResolver.openOutputStream(target, "wt")
        } catch (_: FileNotFoundException) {
            null
        } catch (_: IllegalArgumentException) {
            null // penyedia yang tidak mengenal "wt"
        } ?: context.contentResolver.openOutputStream(target, "w") ?: throw IOException("lokasi tidak bisa ditulis")
        stream.use { it.write(bytes); it.flush() }
    }

    override suspend fun read(uri: String, maxBytes: Int): String = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(Uri.parse(uri)) ?: throw IOException("berkas tidak bisa dibuka")
        input.use {
            val out = ByteArrayOutputStream()
            val chunk = ByteArray(64 * 1024)
            var total = 0
            while (true) {
                val n = it.read(chunk)
                if (n < 0) break
                total += n
                if (total > maxBytes) throw ImportException("Berkas terlalu besar (maks. ${maxBytes / (1024 * 1024)} MB).")
                out.write(chunk, 0, n)
            }
            val bytes = out.toByteArray()
            try { String(bytes, Charsets.UTF_8) } finally { bytes.fill(0); chunk.fill(0) }
        }
    }

    override suspend fun delete(uri: String): Boolean = withContext(Dispatchers.IO) {
        try {
            DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(uri))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            false // penyedia tidak mendukung hapus / izin tulis tidak diberikan
        }
    }
}
