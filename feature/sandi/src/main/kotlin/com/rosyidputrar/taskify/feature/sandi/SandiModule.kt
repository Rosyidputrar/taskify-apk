package com.rosyidputrar.taskify.feature.sandi

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import java.io.FileNotFoundException
import java.io.IOException

val sandiFeatureModule = module {
    single<SandiFileWriter> { ContentResolverFileWriter(androidContext()) }
    viewModel { SandiViewModel(get(), get(), get(), get(), get(), get()) }
}

/** Tulis ke URI dari pemilih lokasi. Mode "wt" memangkas isi lama bila berkas yang ada ditimpa. */
internal class ContentResolverFileWriter(private val context: Context) : SandiFileWriter {
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
}
