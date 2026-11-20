package com.rosyidputrar.taskify.feature.sandi

import com.rosyidputrar.taskify.core.domain.sandi.ImportException
import com.rosyidputrar.taskify.core.domain.sandi.ImportFormat
import com.rosyidputrar.taskify.core.domain.sandi.ImportParse
import com.rosyidputrar.taskify.core.domain.sandi.ItemPayload
import com.rosyidputrar.taskify.core.domain.sandi.SandiImport
import com.rosyidputrar.taskify.core.sandi.SandiService
import com.rosyidputrar.taskify.core.sandi.SandiUserException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Membaca / menghapus berkas dari pemilih berkas (Storage Access Framework). */
interface SandiDocuments {
    /** Isi mentah berkas (byte), supaya batas ukuran dihitung dari byte (D-14). @throws ImportException bila lebih dari [maxBytes]. */
    suspend fun read(uri: String, maxBytes: Int): ByteArray

    /** true = terhapus; false = penyedia tidak mengizinkan / gagal. */
    suspend fun delete(uri: String): Boolean
}

/** Membaca QR dari gambar yang dipilih (foto layar "Transfer akun" dari HP lain). */
fun interface QrImageReader {
    /** Isi semua QR di gambar; kosong = tidak ada QR. @throws ImportException bila bukan gambar. */
    suspend fun readQr(uri: String): List<String>
}

/** Ringkasan sebelum impor — hanya angka & judul contoh, tanpa sandi. */
data class ImportPreview(
    val format: ImportFormat,
    val add: Int,
    val withTotp: Int,
    val identical: Int,
    val duplicateInFile: Int,
    val differentPassword: Int,
    val noSecret: Int,
    val malformed: Int,
    val badTotp: Int,
    val sample: List<String>,
)

/** Hasil impor: ditawarkan menghapus berkas CSV (isinya sandi tanpa enkripsi). */
data class ImportDone(val added: Int, val tooLarge: List<String>)

/**
 * Alur impor CSV, terpisah dari ViewModel supaya bisa diuji tanpa database:
 *  1. pilih berkas → dibaca & diurai; isi berkas (teks biasa) hanya di memori kelas ini;
 *  2. pratinjau berisi angka saja; rencana DIHITUNG ULANG saat konfirmasi terhadap isi brankas
 *     terbaru (mungkin baru tersinkron), lalu disimpan dalam satu transaksi;
 *  3. tidak pernah menimpa item lama; selesai → tawarkan hapus berkas CSV.
 */
class SandiImporter(
    private val scope: CoroutineScope,
    private val documents: SandiDocuments,
    private val save: suspend (List<ItemPayload>) -> SandiService.ImportResult,
    private val show: (String) -> Unit,
    private val ttlMs: Long = TTL_MS,
) {
    /** [pending] = ada berkas terbaca yang menunggu konfirmasi (ringkasannya lewat [preview]). */
    data class State(val picking: Picking? = null, val pending: Long? = null, val done: ImportDone? = null)

    /** Pemilih berkas diminta; [launched] mencegah dibuka dua kali (rotasi layar). */
    data class Picking(val launched: Boolean = false)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private var parse: ImportParse? = null
    private var source: String? = null
    private var timeout: Job? = null

    fun pick() = _state.update { it.copy(picking = Picking(), done = null) }

    fun launched() = _state.update { s -> s.copy(picking = s.picking?.copy(launched = true)) }

    private var counter = 0L

    /** Hasil pemilih ([uri] null = batal). Galat berkas ditampilkan sebagai pesan, bukan dilempar. */
    suspend fun open(uri: String?) {
        _state.update { it.copy(picking = null) }
        if (uri == null) return
        forget()
        val parsed = try {
            // Byte dibaca sekali, dicek batasnya dalam byte, lalu dinolkan setelah diurai.
            val bytes = documents.read(uri, SandiImport.MAX_BYTES)
            try {
                SandiImport.readBytes(bytes)
            } finally {
                bytes.fill(0)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ImportException) {
            show(e.message ?: "Berkas tidak bisa diimpor.")
            return
        } catch (e: Exception) {
            show("Berkas tidak bisa dibaca: ${e.message ?: "akses ditolak"}")
            return
        }
        parse = parsed
        source = uri
        _state.update { it.copy(pending = ++counter) }
        timeout = scope.launch {
            delay(ttlMs)
            forget()
            _state.update { it.copy(pending = null) }
        }
    }

    /**
     * Ringkasan terhadap isi brankas [existing] SAAT INI — dihitung ulang setiap isi brankas berubah
     * (mis. brankas sempat terkunci selama pemilih berkas terbuka, atau ada sinkron baru).
     */
    fun preview(existing: List<ItemPayload>): ImportPreview? {
        val p = parse ?: return null
        val plan = SandiImport.plan(p, existing)
        return ImportPreview(
            format = p.format, add = plan.add.size, withTotp = plan.add.count { it.totp != null },
            identical = plan.identical, duplicateInFile = plan.duplicateInFile, differentPassword = plan.differentPassword,
            noSecret = p.noSecret, malformed = p.malformed, badTotp = p.badTotp,
            sample = plan.add.map { it.title }.distinct().take(SAMPLE),
        )
    }

    /** Simpan. @throws SandiUserException / galat simpan (pesan ditampilkan ViewModel). */
    suspend fun confirm(existing: List<ItemPayload>) {
        val p = parse ?: throw SandiUserException("Impor kedaluwarsa. Pilih berkasnya lagi.")
        val plan = SandiImport.plan(p, existing)
        if (plan.add.isEmpty()) {
            forget()
            _state.update { it.copy(pending = null) }
            show("Tidak ada sandi baru — semuanya sudah ada di brankas.")
            return
        }
        val result = save(plan.add)
        timeout?.cancel()
        parse = null // sumber tetap diingat untuk tawaran hapus berkas
        _state.update { it.copy(pending = null, done = ImportDone(result.added, result.tooLarge)) }
    }

    fun cancel() {
        forget()
        _state.update { it.copy(pending = null) }
    }

    /** Hapus berkas CSV yang baru diimpor. */
    suspend fun deleteSource() {
        val uri = source
        _state.update { it.copy(done = null) }
        source = null
        if (uri == null) return
        show(if (documents.delete(uri)) "Berkas CSV dihapus." else "Berkas tidak bisa dihapus dari sini. Hapus manual lewat aplikasi Berkas.")
    }

    fun dismissDone() {
        source = null
        _state.update { it.copy(done = null) }
    }

    /** ViewModel dibuang: lupakan semuanya. */
    fun clear() {
        forget()
        _state.value = State()
    }

    private fun forget() {
        timeout?.cancel()
        parse = null
        source = null
    }

    companion object {
        const val TTL_MS = 10 * 60_000L
        const val SAMPLE = 5
    }
}
