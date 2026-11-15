package com.rosyidputrar.taskify.core.files

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.prepareGet
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import java.io.File

/** PUT ke presigned URL R2 dan GET dari URL unduhan, dengan progres. */
class Transfers {
    private val http = HttpClient(OkHttp) {
        install(HttpTimeout) {
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 120_000
            requestTimeoutMillis = 30 * 60_000
        }
        expectSuccess = false
    }

    suspend fun put(url: String, file: File, contentType: String, onProgress: (Float) -> Unit) {
        val response = http.put(url) {
            setBody(FileContent(file, ContentType.parse(contentType)))
            onUpload { sent, total -> if (total != null && total > 0) onProgress(sent.toFloat() / total) }
        }
        if (!response.status.isSuccess()) throw FileTransferException("Upload gagal (${response.status.value})")
    }

    suspend fun download(url: String, target: File) {
        val tmp = File(target.parentFile, target.name + ".part")
        http.prepareGet(url).execute { response ->
            if (!response.status.isSuccess()) throw FileTransferException("Gagal mengunduh (${response.status.value})")
            response.bodyAsChannel().copyAndClose(tmp.writeChannel())
        }
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    /** Isi file dengan Content-Length pasti (ikut ditandatangani presign R2). */
    private class FileContent(private val file: File, override val contentType: ContentType) : OutgoingContent.WriteChannelContent() {
        override val contentLength: Long get() = file.length()
        override suspend fun writeTo(channel: ByteWriteChannel) {
            val buffer = ByteArray(64 * 1024)
            file.inputStream().use { input ->
                while (true) {
                    val n = input.read(buffer)
                    if (n <= 0) break
                    channel.writeFully(buffer, 0, n)
                }
            }
        }
    }
}

