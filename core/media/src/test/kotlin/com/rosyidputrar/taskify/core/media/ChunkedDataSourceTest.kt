package com.rosyidputrar.taskify.core.media

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ChunkedDataSource 8 MB (porting ArchiveTune, Tahap 1): satu DataSpec
 * panjang dipecah jadi chunk-chunk berbatas, supaya tiap request HTTP
 * membawa `Range`.
 */
@RunWith(AndroidJUnit4::class)
class ChunkedDataSourceTest {

    /** Upstream palsu: melayani byte dari array, mencatat tiap DataSpec yang dibuka. */
    private class FakeUpstream(private val bytes: ByteArray) : DataSource {
        val opened = mutableListOf<DataSpec>()
        private var pos = 0L
        private var end = 0L

        override fun addTransferListener(transferListener: TransferListener) = Unit
        override fun open(dataSpec: DataSpec): Long {
            opened += dataSpec
            pos = dataSpec.position
            end = if (dataSpec.length == C.LENGTH_UNSET.toLong()) bytes.size.toLong()
            else (dataSpec.position + dataSpec.length).coerceAtMost(bytes.size.toLong())
            return end - pos
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (pos >= end) return C.RESULT_END_OF_INPUT
            val n = minOf(length.toLong(), end - pos).toInt()
            bytes.copyInto(buffer, offset, pos.toInt(), (pos + n).toInt())
            pos += n
            return n
        }

        override fun getUri(): Uri? = null
        override fun getResponseHeaders(): Map<String, List<String>> = emptyMap()
        override fun close() = Unit
    }

    private fun spec(length: Long) =
        DataSpec(Uri.parse("https://rr1---sn-xxxx.googlevideo.com/videoplayback?c=ANDROID&itag=251"), 0, length)

    @Test fun `length 20MB dipecah jadi chunk 8MB`() {
        val total = 20_000_000
        val upstream = FakeUpstream(ByteArray(total) { (it % 251).toByte() })
        val source = ChunkedDataSource(upstream, MediaRequestHeaders.CHUNK_LENGTH)
        assertEquals(total.toLong(), source.open(spec(total.toLong())))
        assertEquals(1, upstream.opened.size)
        assertEquals(8 * 1024 * 1024L, upstream.opened[0].length)

        // Baca chunk pertama sampai habis, lalu satu byte lagi → chunk kedua dibuka.
        val buf = ByteArray(1024)
        var read = 0L
        while (read < 8 * 1024 * 1024L) {
            val n = source.read(buf, 0, buf.size)
            assertTrue(n > 0)
            read += n
        }
        assertEquals(1, upstream.opened.size, "chunk kedua baru dibuka saat read berikutnya")
        assertTrue(source.read(buf, 0, 1) > 0)
        assertEquals(2, upstream.opened.size)
        assertEquals(8 * 1024 * 1024L, upstream.opened[1].position)
        assertEquals(8 * 1024 * 1024L, upstream.opened[1].length)
        source.close()
    }

    @Test fun `length kurang dari chunk dibuka sekali utuh`() {
        val total = 5_495_333
        val upstream = FakeUpstream(ByteArray(total) { 7 })
        val source = ChunkedDataSource(upstream, MediaRequestHeaders.CHUNK_LENGTH)
        assertEquals(total.toLong(), source.open(spec(total.toLong())))
        assertEquals(1, upstream.opened.size)
        assertEquals(total.toLong(), upstream.opened[0].length)
        val buf = ByteArray(1024)
        var read = 0L
        while (true) {
            val n = source.read(buf, 0, buf.size)
            if (n == C.RESULT_END_OF_INPUT) break
            read += n
        }
        assertEquals(total.toLong(), read)
        assertEquals(1, upstream.opened.size, "tidak ada chunk kedua untuk isi < 8 MB")
        source.close()
    }

    @Test fun `resolveStreamChunkLength mengambil yang terkecil`() {
        assertEquals(8 * 1024 * 1024L, resolveStreamChunkLength(20_000_000L, 0, null, MediaRequestHeaders.CHUNK_LENGTH))
        assertEquals(5_495_333L, resolveStreamChunkLength(5_495_333L, 0, null, MediaRequestHeaders.CHUNK_LENGTH))
        assertEquals(4_000_000L, resolveStreamChunkLength(20_000_000L, 16_000_000L, 20_000_000L, MediaRequestHeaders.CHUNK_LENGTH))
        assertEquals(null, resolveStreamChunkLength(20_000_000L, 20_000_000L, 20_000_000L, MediaRequestHeaders.CHUNK_LENGTH))
    }
}
