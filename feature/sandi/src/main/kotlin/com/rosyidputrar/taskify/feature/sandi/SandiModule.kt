package com.rosyidputrar.taskify.feature.sandi

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
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
    single<QrImageReader> { get<ContentResolverFiles>() }
    viewModel { SandiViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
}

/** Berkas lewat pemilih sistem (SAF): tulis ekspor, baca & hapus berkas impor. */
internal class ContentResolverFiles(private val context: Context) : SandiFileWriter, SandiDocuments, QrImageReader {
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

    /**
     * D-14: ukuran dicek DUA kali dalam byte — dulu lewat `OpenableColumns.SIZE` (menolak tanpa
     * membaca isi bila penyedia melaporkannya), lalu selama streaming. Mengembalikan byte mentah
     * supaya [SandiImport.readBytes] yang menghitung batasnya, bukan `String.length`.
     */
    override suspend fun read(uri: String, maxBytes: Int): ByteArray = withContext(Dispatchers.IO) {
        val target = Uri.parse(uri)
        val reported = runCatching {
            context.contentResolver.query(target, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else null
            }
        }.getOrNull()
        if (reported != null && reported > maxBytes) throw ImportException("Berkas terlalu besar (maks. ${maxBytes / (1024 * 1024)} MB).")
        val input = context.contentResolver.openInputStream(target) ?: throw IOException("berkas tidak bisa dibuka")
        input.use {
            val out = ByteArrayOutputStream()
            val chunk = ByteArray(64 * 1024)
            var total = 0
            try {
                while (true) {
                    val n = it.read(chunk)
                    if (n < 0) break
                    total += n
                    if (total > maxBytes) throw ImportException("Berkas terlalu besar (maks. ${maxBytes / (1024 * 1024)} MB).")
                    out.write(chunk, 0, n)
                }
                out.toByteArray()
            } finally {
                chunk.fill(0)
            }
        }
    }

    /** Gambar diperkecil sampai sisi terpanjang ≤ 2048 px (cukup untuk QR, hemat memori). */
    override suspend fun readQr(uri: String): List<String> = withContext(Dispatchers.IO) {
        val target = Uri.parse(uri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // Jangan rangkai `?: throw` setelah `.use { BitmapFactory.decodeStream(..., inJustDecodeBounds = true) }`:
        // decodeStream dengan inJustDecodeBounds = true SELALU mengembalikan null.
        val boundsStream = context.contentResolver.openInputStream(target) ?: throw IOException("Gambar tidak bisa dibuka.")
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw ImportException("Berkas ini bukan gambar.")
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
        val decodeStream = context.contentResolver.openInputStream(target) ?: throw IOException("Gambar tidak bisa dibuka.")
        val bitmap = decodeStream.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 })
        } ?: throw ImportException("Gambar tidak bisa dibaca.")
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            try {
                withContext(Dispatchers.Default) { QrDecode.decode(pixels, bitmap.width, bitmap.height) }
            } finally {
                pixels.fill(0)
            }
        } finally {
            bitmap.recycle()
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
