/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

// Diadaptasi dari ArchiveTune `app/.../playback/ChunkedDataSource.kt`
// (v15.1.0, c7f0e24) — hanya paket yang diubah ke Taskify; `internal`
// dipertahankan.
// Lihat licenses/ARCHIVETUNE.md.
//
// DataSource ini memecah satu DataSpec menjadi chunk-chunk sepanjang
// [chunkLength] (ArchiveTune: 8 MB), supaya tiap request HTTP membawa
// header `Range` berbatas. Tanpa ini ExoPlayer membuka DataSpec dengan
// length UNSET → request pertama GET penuh tanpa `Range` → googlevideo
// menolak URL ANDROID dengan 403 0 byte (INVESTIGASI-B §2.3/§2.5, terbukti
// dari bytecode media3: `ProgressiveMediaPeriod` tidak memanggil
// `setLength`, `HttpUtil.buildRangeRequestHeader(0, -1)` mengembalikan null).

package com.rosyidputrar.taskify.core.media

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.EOFException

internal class ChunkedDataSource(
    private val upstream: DataSource,
    private val chunkLength: Long,
) : DataSource {
    private var request: DataSpec? = null
    private var bytesRead = 0L
    private var chunkBytesRemaining = C.LENGTH_UNSET.toLong()
    private var upstreamOpen = false

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        check(request == null)
        request = dataSpec
        bytesRead = 0L
        val resolvedLength = openNextChunk(dataSpec)
        return if (dataSpec.length == C.LENGTH_UNSET.toLong()) resolvedLength else dataSpec.length
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val dataSpec = checkNotNull(request)
        if (dataSpec.length != C.LENGTH_UNSET.toLong() && bytesRead == dataSpec.length) {
            return C.RESULT_END_OF_INPUT
        }
        if (chunkBytesRemaining == 0L) {
            closeUpstream()
            openNextChunk(dataSpec.subrange(bytesRead))
        }
        val readLength =
            if (chunkBytesRemaining == C.LENGTH_UNSET.toLong()) {
                length
            } else {
                minOf(length.toLong(), chunkBytesRemaining).toInt()
            }
        val count = upstream.read(buffer, offset, readLength)
        if (count == C.RESULT_END_OF_INPUT) {
            if (chunkBytesRemaining > 0L) {
                throw EOFException("Audio stream ended before the requested range was complete")
            }
            return count
        }
        bytesRead += count
        if (chunkBytesRemaining != C.LENGTH_UNSET.toLong()) {
            chunkBytesRemaining -= count
        }
        return count
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        request = null
        bytesRead = 0L
        chunkBytesRemaining = C.LENGTH_UNSET.toLong()
        closeUpstream()
    }

    private fun openNextChunk(dataSpec: DataSpec): Long {
        val length =
            if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
                null
            } else {
                resolveStreamChunkLength(
                    requestedLength = dataSpec.length,
                    position = dataSpec.position,
                    knownContentLength = null,
                    chunkLength = chunkLength,
                )
            }
        val chunkSpec = length?.let { dataSpec.subrange(0L, it) } ?: dataSpec
        chunkBytesRemaining = chunkSpec.length
        upstreamOpen = true
        return upstream.open(chunkSpec)
    }

    private fun closeUpstream() {
        if (!upstreamOpen) return
        upstreamOpen = false
        upstream.close()
    }
}
