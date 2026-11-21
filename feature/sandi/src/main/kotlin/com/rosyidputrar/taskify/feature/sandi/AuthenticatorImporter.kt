package com.rosyidputrar.taskify.feature.sandi

import com.rosyidputrar.taskify.core.domain.sandi.AuthScan
import com.rosyidputrar.taskify.core.domain.sandi.AuthenticatorImport
import com.rosyidputrar.taskify.core.domain.sandi.ImportException
import com.rosyidputrar.taskify.core.domain.sandi.ItemPayload
import com.rosyidputrar.taskify.core.sandi.SandiItem
import com.rosyidputrar.taskify.core.sandi.SandiService
import com.rosyidputrar.taskify.core.sandi.SandiUserException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ringkasan impor 2FA untuk UI — label & angka saja, tanpa kunci rahasia. */
data class AuthImportUi(
    /** Jumlah QR / teks yang sudah terbaca. */
    val scanned: Int = 0,
    /** Nomor QR (mulai 1) yang belum dipindai dari ekspor bertahap Google. */
    val missing: List<Int> = emptyList(),
    val batchSize: Int = 0,
    val add: Int = 0,
    /** Label sandi lama yang akan dipasangi kunci 2FA. */
    val attach: List<String> = emptyList(),
    val identical: Int = 0,
    val duplicates: Int = 0,
    val unsupported: List<Pair<String, String>> = emptyList(),
    /** Ada sandi lama yang cocok → sakelar "Pasang ke sandi yang cocok" ditampilkan. */
    val attachEnabled: Boolean = false,
    /** Posisi sakelar. */
    val attachOn: Boolean = true,
    /** D-10: label "Penerbit (akun)" hasil pindai (maks. 10) — ditampilkan sebelum konfirmasi. */
    val scannedLabels: List<String> = emptyList(),
) {
    val total get() = add + attach.size
}

/**
 * Alur impor 2FA dari Google Authenticator: kumpulkan QR (pindai / foto / tempel) → ringkasan →
 * simpan dalam satu transaksi. Kunci rahasia hanya di memori kelas ini; dibuang saat ditutup,
 * selesai, atau [ttlMs] tanpa aktivitas. Rencana dihitung ulang terhadap isi brankas terkini.
 */
class AuthenticatorImporter(
    private val scope: CoroutineScope,
    private val save: suspend (List<ItemPayload>, List<Pair<String, ItemPayload>>) -> SandiService.ImportResult,
    private val show: (String) -> Unit,
    private val ttlMs: Long = TTL_MS,
    private val onError: (String) -> Unit = show,
) {
    /** [version] berubah setiap ada QR baru (memicu hitung ulang ringkasan). */
    data class State(val open: Boolean = false, val attach: Boolean = true, val version: Int = 0)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    private val scans = mutableListOf<AuthScan>()
    private var timeout: Job? = null

    fun open() {
        if (!_state.value.open) scans.clear()
        _state.update { it.copy(open = true) }
        touch()
    }

    /** Tambah hasil pindai / tempelan. false = ditolak (pesan sudah ditampilkan). */
    fun add(text: String): Boolean {
        val parsed = try {
            AuthenticatorImport.parse(text)
        } catch (e: ImportException) {
            onError(e.message ?: "QR tidak bisa dibaca.")
            return false
        }
        // D-10: batas akun per sesi — satu sesi impor tidak boleh membanjiri brankas.
        val totalAfter = scans.sumOf { it.accounts.size } + parsed.sumOf { it.accounts.size }
        if (totalAfter > AuthenticatorImport.MAX_ACCOUNTS_PER_SESSION) {
            onError("Terlalu banyak akun (maks. ${AuthenticatorImport.MAX_ACCOUNTS_PER_SESSION} per sesi impor). Selesaikan dulu sesi ini, lalu impor sisanya.")
            return false
        }
        var fresh = 0
        parsed.forEach { s ->
            val repeat = s.batchId != null && scans.any { it.batchId == s.batchId && it.batchIndex == s.batchIndex }
            if (!repeat) { scans += s; fresh++ }
        }
        if (fresh == 0) {
            onError("QR ini sudah dipindai.")
            return false
        }
        _state.update { it.copy(open = true, version = it.version + 1) }
        touch()
        val missing = AuthenticatorImport.missing(scans)
        val accounts = parsed.sumOf { it.accounts.size }
        val read = if (accounts > 0) "$accounts akun terbaca." else "QR terbaca, tapi akunnya tidak didukung Taskify."
        show(if (missing.isEmpty()) read else "$read Masih ada ${missing.size} QR lagi.")
        return true
    }

    fun setAttach(on: Boolean) = _state.update { it.copy(attach = on) }

    /** Ringkasan terhadap isi brankas [existing] saat ini; null bila lembar impor tertutup. */
    fun summary(existing: List<ItemPayload>): AuthImportUi? {
        val s = _state.value
        if (!s.open) return null
        val plan = AuthenticatorImport.plan(scans, existing, s.attach)
        val withAttach = if (s.attach) plan else AuthenticatorImport.plan(scans, existing, attach = true)
        return AuthImportUi(
            scanned = scans.size,
            missing = AuthenticatorImport.missing(scans).map { it + 1 },
            batchSize = scans.filter { it.batchId != null }.maxOfOrNull { it.batchSize } ?: 0,
            add = plan.add.size,
            attach = plan.attach.map { (i, _) -> existing[i].let { p -> listOf(p.title, p.username).filter { x -> x.isNotBlank() }.joinToString(" · ") } },
            identical = plan.identical,
            duplicates = plan.duplicates,
            unsupported = scans.flatMap { it.unsupported },
            // Sakelar hanya ditawarkan bila memang ada sandi yang cocok.
            attachEnabled = withAttach.attach.isNotEmpty(),
            attachOn = s.attach,
            scannedLabels = scans.flatMap { it.accounts }.map { it.label }.distinct().take(10),
        )
    }

    /** Simpan. [existing] = isi brankas terkini (id dibutuhkan untuk memasang 2FA ke item lama). */
    suspend fun confirm(existing: List<SandiItem>) {
        if (!_state.value.open || scans.isEmpty()) throw SandiUserException("Belum ada QR yang dipindai.")
        val plan = AuthenticatorImport.plan(scans, existing.map { it.payload }, _state.value.attach)
        if (plan.add.isEmpty() && plan.attach.isEmpty()) {
            close()
            show("Tidak ada kunci 2FA baru — semuanya sudah ada di brankas.")
            return
        }
        val result = save(plan.add, plan.attach.map { (i, p) -> existing[i].id to p })
        close()
        show(
            buildString {
                append("${result.added + result.updated} akun 2FA diimpor")
                if (result.updated > 0) append(" (${result.updated} dipasang ke sandi yang sudah ada)")
                append(".")
                if (result.tooLarge.isNotEmpty()) append(" ${result.tooLarge.size} dilewati karena terlalu besar: ${result.tooLarge.take(3).joinToString(", ")}.")
                append(" Cocokkan satu kode dengan Google Authenticator sebelum menghapus akun di sana.")
            },
        )
    }

    fun close() {
        timeout?.cancel()
        scans.clear()
        _state.value = State()
    }

    private fun touch() {
        timeout?.cancel()
        timeout = scope.launch {
            delay(ttlMs)
            close()
        }
    }

    companion object {
        const val TTL_MS = 10 * 60_000L
    }
}
