package com.rosyidputrar.taskify.feature.sandi

import com.rosyidputrar.taskify.core.designsystem.component.launchSafe
import com.rosyidputrar.taskify.core.domain.sandi.ExportKind
import com.rosyidputrar.taskify.core.domain.sandi.GoogleAuthPlan
import com.rosyidputrar.taskify.core.domain.sandi.ItemPayload
import com.rosyidputrar.taskify.core.domain.sandi.SandiExport
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.sandi.SandiUserException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Berkas ekspor yang menunggu lokasi simpan. Isinya disimpan di [SandiExporter], bukan di state UI. */
data class PendingExport(val kind: ExportKind, val fileName: String, val mimeType: String, val count: Int, val launched: Boolean = false)

/** Menulis berkas ke URI dari pemilih lokasi (Storage Access Framework). */
fun interface SandiFileWriter {
    suspend fun write(uri: String, bytes: ByteArray)
}

/**
 * Alur ekspor Sandi, terpisah dari ViewModel supaya bisa diuji tanpa database:
 *  1. master password dicek ULANG ([verify]) — brankas yang terbuka lewat sidik jari tidak cukup;
 *  2. berkas: isi dibuat sekali lalu disimpan di memori sampai pemilih lokasi kembali (bisa saja
 *     brankas terkunci otomatis selama pemilih terbuka), dibuang setelah ditulis / dibatalkan /
 *     [ttlMs] berlalu;
 *  3. QR Google Authenticator: hanya selama dialog terbuka dan brankas tetap terbuka ([onLocked]).
 */
class SandiExporter(
    private val scope: CoroutineScope,
    private val verify: suspend (String) -> Boolean,
    private val writer: SandiFileWriter,
    private val show: (String) -> Unit,
    private val today: () -> String = { Dates.today() },
    private val batchId: () -> Int = { Random.nextInt(1, Int.MAX_VALUE) },
    private val ttlMs: Long = TTL_MS,
) {
    data class State(val migration: GoogleAuthPlan? = null, val pending: PendingExport? = null)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    /** Isi berkas (teks biasa) — tidak pernah masuk state UI / saved state. */
    private var content: String? = null
    private var timeout: Job? = null

    /** @throws SandiUserException pesan siap tampil (sandi salah, tidak ada isi). */
    suspend fun start(kind: ExportKind, password: String, items: List<ItemPayload>) {
        if (password.isEmpty()) throw SandiUserException("Masukkan master password.")
        if (!verify(password)) throw SandiUserException("Master password salah.")
        when (kind) {
            ExportKind.GoogleAuthenticator -> {
                val plan = SandiExport.googleMigration(items, batchId())
                if (plan.codes.isEmpty()) throw SandiUserException("Tidak ada kunci 2FA yang bisa dipindahkan ke Google Authenticator.")
                _state.update { it.copy(migration = plan) }
            }
            ExportKind.GoogleCsv, ExportKind.OtpauthList -> {
                val csv = kind == ExportKind.GoogleCsv
                val count = if (csv) SandiExport.csvSummary(items).rows else SandiExport.withTotp(items).size
                if (count == 0) throw SandiUserException(if (csv) "Tidak ada sandi untuk diekspor." else "Tidak ada kunci 2FA untuk diekspor.")
                content = if (csv) SandiExport.passwordsCsv(items) else SandiExport.otpauthList(items)
                _state.update { it.copy(pending = PendingExport(kind, SandiExport.fileName(kind, today()), SandiExport.mimeType(kind), count)) }
                timeout?.cancel()
                timeout = scope.launch {
                    delay(ttlMs)
                    content = null
                    _state.update { it.copy(pending = null) }
                }
            }
        }
    }

    /** Pemilih lokasi sudah dibuka (jangan dibuka lagi setelah rotasi layar). */
    fun launched() = _state.update { s -> s.copy(pending = s.pending?.copy(launched = true)) }

    /** Hasil pemilih lokasi ([uri] null = batal): tulis lalu buang isinya. */
    fun finish(uri: String?) {
        val pending = _state.value.pending
        val text = content
        content = null
        timeout?.cancel()
        _state.update { it.copy(pending = null) }
        if (uri == null) return
        if (text == null || pending == null) {
            show("Ekspor kedaluwarsa. Ulangi dari Pengaturan brankas.")
            return
        }
        scope.launchSafe {
            val bytes = text.toByteArray(Charsets.UTF_8)
            try {
                writer.write(uri, bytes)
                show(if (pending.kind == ExportKind.GoogleCsv) "${pending.count} sandi diekspor. Hapus berkasnya setelah diimpor." else "${pending.count} kunci 2FA diekspor. Hapus berkasnya setelah diimpor.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                show("Berkas tidak bisa ditulis: ${e.message ?: "lokasi menolak"}")
            } finally {
                bytes.fill(0)
            }
        }
    }

    fun closeMigration() = _state.update { it.copy(migration = null) }

    /** Brankas terkunci → QR dibuang. Berkas yang menunggu lokasi tetap (pemilih bisa masih terbuka). */
    fun onLocked() = closeMigration()

    /** ViewModel dibuang. */
    fun clear() {
        content = null
        timeout?.cancel()
        _state.value = State()
    }

    companion object {
        /** Berkas ekspor yang belum diberi lokasi dibuang setelah 10 menit. */
        const val TTL_MS = 10 * 60_000L
    }
}
