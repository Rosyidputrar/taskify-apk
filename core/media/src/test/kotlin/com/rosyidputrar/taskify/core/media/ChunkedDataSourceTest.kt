package com.rosyidputrar.taskify.core.media

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.ceil
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ChunkedDataSource sadar-bitrate (INVESTIGASI-A §7, F3): satu DataSpec
 * panjang dipecah jadi chunk-chunk ~6 detik media (dari `clen`/`dur` URL),
 * supaya tiap request HTTP membawa `Range` yang ujungnya tetap dalam
 * jendela throttle ~60 detik dari posisi putar. Chunk 8 MB buta (porting
 * ArchiveTune) DITOLAK server: 8 MB pada itag 251 ≈ 512 detik media.
 */
@RunWith(AndroidJUnit4::class)
class ChunkedDataSourceTest {

    /** Upstream palsu: melayani byte dari array, mencatat tiap DataSpec yang dibuka. */
    private class FakeUpstream(private val bytes: ByteArray) : DataSource {
        val opened = mutableListOf<DataSpec>()
        private var pos = 0L
        private var end = 0L
        private var lastPos = 0L
        private var lastEnd = 0L

        override fun addTransferListener(transferListener: TransferListener) = Unit
        override fun open(dataSpec: DataSpec): Long {
            opened += dataSpec
            pos = dataSpec.position
            end = if (dataSpec.length == C.LENGTH_UNSET.toLong()) bytes.size.toLong()
            else (dataSpec.position + dataSpec.length).coerceAtMost(bytes.size.toLong())
            lastPos = pos
            lastEnd = end
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
        override fun getResponseHeaders(): Map<String, List<String>> =
            if (lastPos >= bytes.size) mapOf("Content-Range" to listOf("bytes */${bytes.size}"))
            else mapOf("Content-Range" to listOf("bytes $lastPos-${lastEnd - 1}/${bytes.size}"))
        override fun close() = Unit
    }

    /** DataSpec googlevideo dengan panjang diketahui; [extraQuery] membawa `clen`/`dur`. */
    private fun spec(length: Long, extraQuery: String = "") =
        DataSpec(Uri.parse("https://rr1---sn-xxxx.googlevideo.com/videoplayback?c=ANDROID&itag=251$extraQuery"), 0, length)

    private fun specUnset(url: String) = DataSpec(Uri.parse(url))

    private fun readAll(source: ChunkedDataSource): Long {
        val buf = ByteArray(1024)
        var total = 0L
        while (true) {
            val n = source.read(buf, 0, buf.size)
            if (n == C.RESULT_END_OF_INPUT) break
            total += n
        }
        return total
    }

    @Test fun `length 20MB dipecah jadi chunk 6 detik media dari clen dur`() {
        // clen 20.000.000 / dur 2000 dtk = 10.000 B/dtk → chunk 6 dtk = 60.000 byte.
        val total = 20_000_000
        val upstream = FakeUpstream(ByteArray(total) { (it % 251).toByte() })
        val source = ChunkedDataSource(upstream, MediaRequestHeaders.CHUNK_LENGTH)
        assertEquals(total.toLong(), source.open(spec(total.toLong(), "&clen=20000000&dur=2000.0")))
        assertEquals(1, upstream.opened.size)
        assertEquals(60_000L, upstream.opened[0].length)

        // Baca chunk pertama sampai habis, lalu satu byte lagi → chunk kedua dibuka.
        val buf = ByteArray(1024)
        var read = 0L
        while (read < 60_000L) {
            val n = source.read(buf, 0, buf.size)
            assertTrue(n > 0)
            read += n
        }
        assertEquals(1, upstream.opened.size, "chunk kedua baru dibuka saat read berikutnya")
        assertTrue(source.read(buf, 0, 1) > 0)
        assertEquals(2, upstream.opened.size)
        assertEquals(60_000L, upstream.opened[1].position)
        assertEquals(60_000L, upstream.opened[1].length)
        source.close()
    }

    @Test fun `length kurang dari chunk dibuka sekali utuh`() {
        // 100.000 byte / dur 1 dtk = 100.000 B/dtk → chunk 6 dtk = 600.000 byte > isi.
        val total = 100_000
        val upstream = FakeUpstream(ByteArray(total) { 7 })
        val source = ChunkedDataSource(upstream, MediaRequestHeaders.CHUNK_LENGTH)
        assertEquals(total.toLong(), source.open(spec(total.toLong(), "&clen=100000&dur=1.0")))
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
        assertEquals(1, upstream.opened.size, "tidak ada chunk kedua untuk isi < 1 chunk")
        source.close()
    }

    @Test fun `length UNSET googlevideo dibatasi chunk fallback itag 251 dan open mengembalikan UNSET`() {
        // F2 (INVESTIGASI-A §5) + F3 (§7) + C S-13: tanpa panjang total DAN
        // tanpa `clen`/`dur`, chunk pertama googlevideo tetap dibatasi —
        // fallback per-itag (251 ≈ 160 kbps → 120 KB ≈ 6 detik), bukan 32 KB
        // buta; open() tidak mengklaim panjang chunk sebagai panjang total.
        val total = 1_000_000
        val upstream = FakeUpstream(ByteArray(total) { (it % 251).toByte() })
        val source = ChunkedDataSource(upstream, MediaRequestHeaders.CHUNK_LENGTH)
        assertEquals(
            C.LENGTH_UNSET.toLong(),
            source.open(specUnset("https://rr1---sn-yyyy.googlevideo.com/videoplayback?c=WEB_CREATOR&itag=251")),
        )
        assertEquals(1, upstream.opened.size)
        assertEquals(0L, upstream.opened[0].position)
        assertEquals(120_000L, upstream.opened[0].length)
        source.close()
    }

    @Test fun `length UNSET googlevideo dibaca penuh sampai habis bersih`() {
        // Total dipelajari dari Content-Range chunk pertama → chunk terakhir
        // eksak, tanpa probe di luar akhir, tanpa EOFException.
        // 1.000.000 byte / 120.000 = 8,33 → 9 chunk; terakhir di 960.000.
        val total = 1_000_000
        val upstream = FakeUpstream(ByteArray(total) { (it % 251).toByte() })
        val source = ChunkedDataSource(upstream, MediaRequestHeaders.CHUNK_LENGTH)
        source.open(specUnset("https://rr1---sn-yyyy.googlevideo.com/videoplayback?c=WEB_CREATOR&itag=251"))
        assertEquals(total.toLong(), readAll(source))
        assertEquals(9, upstream.opened.size)
        assertEquals(8 * 120_000L, upstream.opened[8].position)
        assertEquals(total - 8 * 120_000L, upstream.opened[8].length)
        source.close()
    }

    @Test fun `length UNSET googlevideo kelipatan chunk berakhir tanpa probe ekstra`() {
        val total = 2 * 120_000
        val upstream = FakeUpstream(ByteArray(total) { 7 })
        val source = ChunkedDataSource(upstream, MediaRequestHeaders.CHUNK_LENGTH)
        source.open(specUnset("https://rr1---sn-yyyy.googlevideo.com/videoplayback?c=WEB_CREATOR&itag=251"))
        assertEquals(total.toLong(), readAll(source))
        assertEquals(2, upstream.opened.size)
        source.close()
    }

    @Test fun `length UNSET non-googlevideo pass-through tanpa batas`() {
        // Siaran langsung: cap sintetis TIDAK berlaku, dibaca terbuka utuh.
        val total = 100_000
        val upstream = FakeUpstream(ByteArray(total) { 7 })
        val source = ChunkedDataSource(upstream, MediaRequestHeaders.CHUNK_LENGTH)
        assertEquals(total.toLong(), source.open(specUnset("https://radio.example.com/stream")))
        assertEquals(1, upstream.opened.size)
        assertEquals(C.LENGTH_UNSET.toLong(), upstream.opened[0].length)
        assertEquals(total.toLong(), readAll(source))
        assertEquals(1, upstream.opened.size)
        source.close()
    }

    @Test fun `resolveStreamChunkLength mengambil yang terkecil`() {
        assertEquals(8 * 1024 * 1024L, resolveStreamChunkLength(20_000_000L, 0, null, MediaRequestHeaders.CHUNK_LENGTH))
        assertEquals(5_495_333L, resolveStreamChunkLength(5_495_333L, 0, null, MediaRequestHeaders.CHUNK_LENGTH))
        assertEquals(4_000_000L, resolveStreamChunkLength(20_000_000L, 16_000_000L, 20_000_000L, MediaRequestHeaders.CHUNK_LENGTH))
        assertEquals(null, resolveStreamChunkLength(20_000_000L, 20_000_000L, 20_000_000L, MediaRequestHeaders.CHUNK_LENGTH))
    }

    @Test fun `resolveBitrateAwareChunkLength memakai 6 detik dari clen dur`() {
        // Video HP sir, itag 251: 3.371.685 byte / 205,7 dtk ≈ 16.391 B/dtk
        // → chunk 6 detik ≈ 98.347 byte (bukan 8 MB).
        val sevenX = Uri.parse("https://rr1---sn-x.googlevideo.com/videoplayback?c=WEB_CREATOR&itag=251&clen=3371685&dur=205.7")
        assertEquals((3_371_685.0 / 205.7 * 6).toLong(), resolveBitrateAwareChunkLength(sevenX))
        assertTrue(resolveBitrateAwareChunkLength(sevenX) < 100_000L)
        // Video HP sir lainnya: 5.495.333 / 312,5 dtk ≈ 17.585 B/dtk → ≈ 105.510 byte.
        val blqp = Uri.parse("https://rr1---sn-x.googlevideo.com/videoplayback?c=WEB_CREATOR&itag=251&clen=5495333&dur=312.5")
        assertEquals((5_495_333.0 / 312.5 * 6).toLong(), resolveBitrateAwareChunkLength(blqp))
    }

    @Test fun `resolveBitrateAwareChunkLength fallback per itag bila clen dur hilang`() {
        // C S-13: tanpa clen/dur, fallback memakai bitrate nominal itag
        // (251 → 160 kbps × 6 dtk / 8 = 120.000), bukan 32 KB untuk semua.
        val noParams = Uri.parse("https://rr1---sn-x.googlevideo.com/videoplayback?c=WEB_CREATOR&itag=251")
        assertEquals(120_000L, resolveBitrateAwareChunkLength(noParams))
        val noDur = Uri.parse("https://rr1---sn-x.googlevideo.com/videoplayback?c=WEB_CREATOR&itag=251&clen=3371685")
        assertEquals(120_000L, resolveBitrateAwareChunkLength(noDur))
        val durZero = Uri.parse("https://rr1---sn-x.googlevideo.com/videoplayback?c=WEB_CREATOR&itag=251&clen=3371685&dur=0")
        assertEquals(120_000L, resolveBitrateAwareChunkLength(durZero))
        // 140 → 128 kbps × 6 / 8 = 96.000; 139 → 48 kbps × 6 / 8 = 36.000.
        assertEquals(96_000L, resolveBitrateAwareChunkLength(Uri.parse("https://rr1---sn-x.googlevideo.com/videoplayback?itag=140")))
        assertEquals(36_000L, resolveBitrateAwareChunkLength(Uri.parse("https://rr1---sn-x.googlevideo.com/videoplayback?itag=139")))
        // 599 → 32 kbps × 6 / 8 = 24.000 → dijepit naik ke 32 KB.
        assertEquals(32 * 1024L, resolveBitrateAwareChunkLength(Uri.parse("https://rr1---sn-x.googlevideo.com/videoplayback?itag=599")))
        // Itag tak dikenal / tanpa itag → tetap 32 KB (tanpa regresi).
        assertEquals(32 * 1024L, resolveBitrateAwareChunkLength(Uri.parse("https://rr1---sn-x.googlevideo.com/videoplayback?itag=999")))
        assertEquals(32 * 1024L, resolveBitrateAwareChunkLength(Uri.parse("https://rr1---sn-x.googlevideo.com/videoplayback?c=WEB_CREATOR")))
    }

    @Test fun `resolveBitrateAwareChunkLength dijepit ke 32KB sampai 1MB`() {
        // Bitrate sangat rendah → hasil hitung < 32 KB → naik ke 32 KB.
        val low = Uri.parse("https://rr1---sn-x.googlevideo.com/videoplayback?c=WEB_CREATOR&itag=251&clen=100000&dur=100.0")
        assertEquals(32 * 1024L, resolveBitrateAwareChunkLength(low))
        // Bitrate tidak wajar tinggi → hasil hitung > 1 MB → turun ke 1 MB.
        val high = Uri.parse("https://rr1---sn-x.googlevideo.com/videoplayback?c=WEB_CREATOR&itag=251&clen=10000000&dur=1.0")
        assertEquals(1024 * 1024L, resolveBitrateAwareChunkLength(high))
    }

    @Test fun `chunk 6 detik di buffer default 50 detik ujungnya 56 detik masih dalam jendela 60 detik`() {
        // Invarian yang dijaga optimasi Agen A (A5): buffer default ExoPlayer
        // (DefaultLoadControl min=maks=50 detik) + chunk 6 detik → ujung Range
        // steady-state ≈ 56 detik < 60 detik jendela throttle. Bila default
        // media3 berubah, tes ini gagal supaya hitung jendela diperiksa ulang
        // sebelum chunk atau buffer diubah.
        assertEquals(50_000, DefaultLoadControl.DEFAULT_MIN_BUFFER_MS)
        assertEquals(50_000, DefaultLoadControl.DEFAULT_MAX_BUFFER_MS)
        val worstEndSeconds = DefaultLoadControl.DEFAULT_MAX_BUFFER_MS / 1000.0 + TARGET_CHUNK_MEDIA_SECONDS
        assertTrue(worstEndSeconds < 60.0, "ujung steady-state $worstEndSeconds detik harus < 60 detik")
        // Jumlah request per lagu pada chunk ini (angka pengukuran Agen A,
        // tidak berubah karena chunk dipertahankan 6 detik):
        // 7Xqon20TlH4: 3.371.685 byte / chunk ≈98.348 → 35 request;
        // BLQPNaBklGA: 5.495.333 byte / chunk ≈105.510 → 53 request.
        val sevenX = Uri.parse("https://rr1---sn-x.googlevideo.com/videoplayback?c=WEB_CREATOR&itag=251&clen=3371685&dur=205.7")
        assertEquals(35, ceil(3_371_685.0 / resolveBitrateAwareChunkLength(sevenX)).toInt())
        val blqp = Uri.parse("https://rr1---sn-x.googlevideo.com/videoplayback?c=WEB_CREATOR&itag=251&clen=5495333&dur=312.5")
        assertEquals(53, ceil(5_495_333.0 / resolveBitrateAwareChunkLength(blqp)).toInt())
    }
}
