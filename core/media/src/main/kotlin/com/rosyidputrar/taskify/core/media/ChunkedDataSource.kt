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
//
// PENTING (INVESTIGASI-A §7, F3): untuk host googlevideo, [chunkLength]
// HANYA batas atas — panjang chunk efektif dihitung SADAR-BITRATE
// ([resolveBitrateAwareChunkLength], ~6 detik media dari param `clen`/`dur`
// URL). 8 MB buta adalah bagian dari masalah: server menolak request yang
// ujung Range-nya melewati ~60 detik media dari posisi putar, dan 8 MB pada
// itag 251 ≈ 512 detik media → 403 sejak request pertama. Chunk 6 detik
// berakhir di posisi-putar + ~56 detik di steady state (buffer default
// ExoPlayer 50 detik), masih di dalam jendela 60 detik.
//
// Jaring pengaman (INVESTIGASI-A §5 F2, 18 Nov 2026): bila length TETAP
// UNSET saat dibuka (registry kosong dan URL tanpa `clen`) DAN host-nya
// `*.googlevideo.com`, chunk tetap dibatasi ke [chunkLength] — request
// pertama selalu `Range` berbatas untuk googlevideo. Panjang total lalu
// dipelajari dari `Content-Range` respons 206 supaya chunk terakhir eksak;
// bila respons berakhir bersih lebih awal (mis. server memotong Range ke
// akhir isi), itu akhir stream yang sah, bukan pemotongan. Host
// non-googlevideo (mis. siaran langsung) tetap dibaca terbuka.

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
    /**
     * Chunk saat ini dibatasi sintetis (panjang total tidak diketahui,
     * URL googlevideo — lihat [openNextChunk]). Dipakai [open] supaya
     * panjang chunk tidak dilaporkan sebagai panjang total, dan [read]
     * supaya akhir stream yang bersih tidak dianggap pemotongan.
     */
    private var currentChunkSynthetic = false

    /**
     * Panjang total yang dipelajari dari `Content-Range` respons 206
     * (`bytes <s>-<e>/<total>`) chunk sintetis pertama; dipakai membatasi
     * chunk terakhir secara eksak supaya tidak ada probe 416 yang sia-sia
     * dan `read()` tahu kapan stream berakhir.
     */
    private var learnedTotalLength: Long? = null

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        check(request == null)
        request = dataSpec
        bytesRead = 0L
        val resolvedLength = openNextChunk(dataSpec)
        return if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            // Chunk sintetis (panjang total tidak diketahui): total tetap
            // tidak diketahui — jangan laporkan panjang chunk sebagai total.
            if (currentChunkSynthetic) C.LENGTH_UNSET.toLong() else resolvedLength
        } else {
            dataSpec.length
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val dataSpec = checkNotNull(request)
        if ((dataSpec.length != C.LENGTH_UNSET.toLong() && bytesRead == dataSpec.length) ||
            (learnedTotalLength != null && bytesRead == learnedTotalLength)
        ) {
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
            // Chunk sintetis: tidak ada janji panjang yang dilanggar —
            // respons HTTP yang berakhir bersih (mis. chunk terakhir parsial
            // karena server memotong Range ke akhir isi, atau server
            // mengabaikan Range dan mengembalikan isi penuh yang lebih
            // pendek) adalah akhir stream yang sah, bukan pemotongan.
            // Koneksi yang benar-benar putus di tengah jalan melempar
            // IOException dari OkHttp, bukan END_OF_INPUT.
            if (chunkBytesRemaining > 0L && !currentChunkSynthetic) {
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
        currentChunkSynthetic = false
        learnedTotalLength = null
        closeUpstream()
    }

    private fun openNextChunk(dataSpec: DataSpec): Long {
        // Panjang total tidak diketahui: untuk URL googlevideo, batasi chunk
        // supaya request pertama SELALU membawa `Range` berbatas
        // (INVESTIGASI-A §5 F2 — jaring pengaman bila registry kosong DAN
        // URL tanpa `clen`; tanpa ini request pertama GET penuh tanpa Range
        // → googlevideo 403). Host lain (mis. siaran langsung) tetap dibaca
        // terbuka seperti sebelumnya: servernya belum tentu mendukung Range.
        //
        // Panjang chunk efektif untuk googlevideo SADAR-BITRATE (F3, §7):
        // ~6 detik media dari `clen`/`dur` URL, bukan [chunkLength] 8 MB
        // buta — ujung Range harus tetap dalam jendela throttle ~60 detik
        // dari posisi putar. [chunkLength] tinggal batas atas.
        val isGoogleVideo = dataSpec.uri.host?.endsWith("googlevideo.com") == true
        val effectiveChunkLength = if (isGoogleVideo) {
            minOf(chunkLength, resolveBitrateAwareChunkLength(dataSpec.uri))
        } else {
            chunkLength
        }
        val synthetic = dataSpec.length == C.LENGTH_UNSET.toLong() && isGoogleVideo
        val length =
            if (synthetic) {
                // Bila total sudah diketahui (dari Content-Range chunk
                // sebelumnya), chunk terakhir eksak (sisa isi); bila belum,
                // batasi ke chunk efektif dan pelajari total dari responsnya
                // (lihat learnTotalLengthFromResponse). Posisi di sini
                // selalu < total yang dipelajari (read() sudah memeriksa
                // akhir stream), jadi resolve tidak mengembalikan null.
                learnedTotalLength?.let {
                    resolveStreamChunkLength(
                        requestedLength = C.LENGTH_UNSET.toLong(),
                        position = dataSpec.position,
                        knownContentLength = it,
                        chunkLength = effectiveChunkLength,
                    )
                } ?: effectiveChunkLength
            } else if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
                null
            } else {
                resolveStreamChunkLength(
                    requestedLength = dataSpec.length,
                    position = dataSpec.position,
                    knownContentLength = null,
                    chunkLength = effectiveChunkLength,
                )
            }
        currentChunkSynthetic = synthetic
        val chunkSpec = length?.let { dataSpec.subrange(0L, it) } ?: dataSpec
        chunkBytesRemaining = chunkSpec.length
        upstreamOpen = true
        val opened = upstream.open(chunkSpec)
        if (synthetic) learnTotalLengthFromResponse()
        return opened
    }

    /** Pelajari panjang total dari `Content-Range: bytes <s>-<e>/<total>` respons terakhir. */
    private fun learnTotalLengthFromResponse() {
        if (learnedTotalLength != null) return
        val values = upstream.responseHeaders.entries
            .firstOrNull { it.key.equals("Content-Range", ignoreCase = true) }?.value
            ?: return
        for (value in values) {
            val trimmed = value.trim()
            if (trimmed.startsWith("bytes */", ignoreCase = true)) continue
            val total = trimmed.substringAfter("/", "").toLongOrNull()?.takeIf { it > 0 }
            if (total != null) {
                learnedTotalLength = total
                return
            }
        }
    }

    private fun closeUpstream() {
        if (!upstreamOpen) return
        upstreamOpen = false
        upstream.close()
    }
}
