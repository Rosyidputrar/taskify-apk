package com.rosyidputrar.taskify.feature.sandi

import com.rosyidputrar.taskify.core.designsystem.component.launchSafe
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.domain.sandi.ExportKind
import com.rosyidputrar.taskify.core.domain.sandi.Generator
import com.rosyidputrar.taskify.core.domain.sandi.GoogleAuthPlan
import com.rosyidputrar.taskify.core.domain.sandi.ItemPayload
import com.rosyidputrar.taskify.core.domain.sandi.MasterPassword
import com.rosyidputrar.taskify.core.domain.sandi.Strength
import com.rosyidputrar.taskify.core.domain.sandi.TotpConfig
import com.rosyidputrar.taskify.core.domain.sandi.skippedMessage
import com.rosyidputrar.taskify.core.domain.sandi.trustDaysLeft
import com.rosyidputrar.taskify.core.sandi.DecryptedItems
import com.rosyidputrar.taskify.core.sandi.SandiItem
import com.rosyidputrar.taskify.core.sandi.SandiService
import com.rosyidputrar.taskify.core.sandi.SandiUserException
import com.rosyidputrar.taskify.core.sandi.SecureClipboard
import com.rosyidputrar.taskify.core.sandi.SandiPrefs
import com.rosyidputrar.taskify.core.sandi.TotpLogItem
import com.rosyidputrar.taskify.core.sandi.VaultStatus
import com.rosyidputrar.taskify.core.sandi.DeviceAuthException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SandiTab(val label: String) { Vault("Brankas"), Codes("Kode"), Generator("Generator") }

/** Email akun (untuk menolak master password yang memuat nama/email). */
fun interface AccountEmail {
    fun get(): String?
}

data class SandiUiState(
    val status: VaultStatus = VaultStatus.Loading,
    val tab: SandiTab = SandiTab.Vault,
    val search: String = "",
    val items: DecryptedItems = DecryptedItems(),
    val log: List<TotpLogItem> = emptyList(),
    val now: Long = System.currentTimeMillis(),
    val busy: String? = null,
    val error: String? = null,
    /** Kunci pemulihan BARU yang wajib ditampilkan sekali (setelah buat brankas / ganti kunci). */
    val recoveryToShow: String? = null,
    val rejectedKeyCreation: Boolean = false,
    val rejectedRotation: String? = null,
    val staleWrites: Int = 0,
    val trustDaysLeft: Int? = null,
    val autoLock: Int = 5,
    val clockOffsetMs: Long = 0,
    val generator: Generator.Prefs = Generator.Prefs(),
    val generated: String = "",
    val email: String? = null,
    /** HP punya kunci layar → "ingat perangkat" bisa dipakai. */
    val trustAvailable: Boolean = true,
    /** QR Google Authenticator yang sedang ditampilkan (rahasia; hilang saat ditutup atau brankas terkunci). */
    val migration: GoogleAuthPlan? = null,
    /** Berkas ekspor siap disimpan: UI membuka pemilih lokasi, lalu memanggil [SandiActions.finishExport]. */
    val pendingExport: PendingExport? = null,
    /** Pemilih berkas impor diminta (dibuka sekali oleh UI). */
    val importPicking: SandiImporter.Picking? = null,
    /** Ringkasan impor menunggu konfirmasi (hanya angka & judul contoh). */
    val importPreview: ImportPreview? = null,
    /** Impor selesai: tampilkan hasil + tawaran hapus berkas CSV. */
    val importDone: ImportDone? = null,
) {
    /** Dihitung sekali per state (bukan setiap kali dibaca). State hanya berganti saat isi/pencarian berubah. */
    val filtered: List<SandiItem> by lazy(LazyThreadSafetyMode.NONE) {
        val q = search.trim().lowercase()
        val list = items.items.filter { q.isEmpty() || listOf(it.payload.title, it.payload.username, it.payload.url, it.payload.notes).any { f -> f.lowercase().contains(q) } }
        list.sortedWith(compareByDescending<SandiItem> { it.payload.favorite }.thenBy { it.payload.title.lowercase() })
    }
    val withTotp: List<SandiItem> by lazy(LazyThreadSafetyMode.NONE) { filtered.filter { it.payload.totp != null } }
}

interface SandiActions {
    fun setTab(tab: SandiTab)
    fun setSearch(text: String)
    fun clearError()
    fun touch()
    fun tryTrusted()
    fun unlock(password: String, remember: Boolean)
    suspend fun evaluateMaster(password: String): Pair<Strength?, String?>
    fun createVault(password: String)
    fun recover(recoveryKey: String, newPassword: String)
    fun changePassword(old: String, new: String)
    fun rotateRecovery(password: String)
    fun acknowledgeRecovery()
    fun lock()
    fun setAutoLock(minutes: Int)
    fun forgetDevice()
    fun dismissRejectedKey()
    fun dismissRejectedRotation()
    fun dismissStaleWrites()
    fun save(payload: ItemPayload, id: String?, onDone: () -> Unit)
    fun remove(item: SandiItem)
    fun copy(what: String, text: String, secret: Boolean = true)
    fun copyCode(item: SandiItem, code: String)
    fun saveQuickTotp(title: String, config: TotpConfig, username: String, onDone: () -> Unit)
    fun deleteLog(id: String)
    fun clearLog()
    fun setGenerator(p: Generator.Prefs)
    fun regenerate()

    // ekspor (bawaan kosong supaya implementasi palsu di tes tidak perlu ikut diubah)
    /** Cek ulang master password, lalu siapkan ekspor [kind] (berkas atau QR). */
    fun startExport(kind: ExportKind, password: String) {}
    /** Pemilih lokasi sudah dibuka untuk [PendingExport] ini (jangan dibuka lagi setelah rotasi layar). */
    fun exportLaunched() {}
    /** Lokasi dipilih ([uri] null = batal): tulis berkas, lalu buang isinya dari memori. */
    fun finishExport(uri: String?) {}
    /** Tutup QR Google Authenticator dan buang isinya. */
    fun closeMigration() {}

    // impor CSV
    /** Buka pemilih berkas CSV. */
    fun startImport() {}
    fun importLaunched() {}
    /** Berkas dipilih ([uri] null = batal): baca & tampilkan ringkasan. */
    fun importPicked(uri: String?) {}
    fun confirmImport() {}
    fun cancelImport() {}
    fun deleteImportSource() {}
    fun dismissImportDone() {}
}


class SandiViewModel(
    private val service: SandiService,
    private val clipboard: SecureClipboard,
    private val prefs: SandiPrefs,
    private val messenger: Messenger,
    private val email: AccountEmail,
    private val writer: SandiFileWriter,
    private val documents: SandiDocuments,
) : ViewModel(), SandiActions {
    private val local = MutableStateFlow(
        SandiUiState(autoLock = service.session.autoLockMinutes, generator = prefs.generator, email = email.get(), now = service.totpNow()),
    )

    /**
     * Detak 1 dtk untuk kode TOTP — terpisah dari [state]: hanya baris kode yang membacanya, jadi
     * daftar (filter + urut) tidak dihitung & disusun ulang setiap detik.
     */
    val clock: StateFlow<Long> = flow { while (true) { emit(service.totpNow()); delay(1_000) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), service.totpNow())

    private val exporter = SandiExporter(viewModelScope, service::verifyMaster, writer, messenger::show)
    private val importer = SandiImporter(viewModelScope, documents, service::importItems, messenger::show)

    val state: StateFlow<SandiUiState> = combine(
        local,
        service.status,
        combine(service.items, service.totpLog) { i, l -> i to l },
        combine(service.rejectedKeyCreation, service.rejectedRotation, service.staleWrites) { a, b, c -> Triple(a, b, c) },
        combine(exporter.state, importer.state) { e, i -> e to i },
    ) { l, status, (items, log), (rk, rr, sw), (export, import) ->
        val open = status is VaultStatus.Unlocked
        l.copy(
            status = status, items = items, log = log, rejectedKeyCreation = rk, rejectedRotation = rr, staleWrites = sw,
            clockOffsetMs = service.clockOffsetMs,
            // QR rahasia tidak pernah tampil di atas brankas yang terkunci.
            migration = export.migration.takeIf { open },
            pendingExport = export.pending,
            importPicking = import.picking,
            // Judul item tidak tampil di atas brankas terkunci; muncul lagi setelah dibuka.
            importPreview = if (open && import.pending != null && items.ready) importer.preview(items.items.map { it.payload }) else null,
            importDone = import.done.takeIf { open },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    /** Status "ingat perangkat" dibaca dari prefs hanya saat bisa berubah (buka, lupakan), bukan tiap detik. */
    private fun refreshTrust() {
        local.update {
            it.copy(
                trustDaysLeft = service.trustMeta()?.let { m -> trustDaysLeft(m, System.currentTimeMillis()) }?.takeIf { d -> d > 0 },
                trustAvailable = service.trustAvailable(),
            )
        }
    }

    init {
        refreshTrust()
        if (local.value.generated.isEmpty()) regenerate()
        launchSafe { runCatching { withContext(Dispatchers.Default) { MasterPassword.warmUp() } } }
        // Brankas terkunci → QR rahasia dibuang dari memori juga (bukan hanya disembunyikan).
        launchSafe { service.status.collect { s -> if (s !is VaultStatus.Unlocked) exporter.onLocked() } }
    }

    private fun run(busy: String, block: suspend () -> Unit) {
        local.update { it.copy(busy = busy, error = null) }
        launchSafe {
            try {
                block()
            } catch (e: SandiUserException) {
                local.update { it.copy(error = e.message) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(error = e.message ?: "Terjadi kesalahan") }
            } finally {
                local.update { it.copy(busy = null) }
            }
        }
    }

    override fun setTab(tab: SandiTab) { local.update { it.copy(tab = tab) } }
    override fun setSearch(text: String) { local.update { it.copy(search = text) } }
    override fun clearError() { local.update { it.copy(error = null) } }
    override fun touch() { service.session.touch() }

    // ---------------------------------------------------------------- buka & buat

    override fun tryTrusted() {
        if (service.trustMeta() == null) return
        launchSafe {
            try {
                service.unlockTrusted()
            } catch (e: DeviceAuthException) {
                local.update { it.copy(error = e.message) }
            } finally {
                refreshTrust()
            }
        }
    }

    override fun unlock(password: String, remember: Boolean) = run("Membuka brankas…") {
        when (val r = service.unlock(password, remember)) {
            SandiService.UnlockOutcome.WrongPassword -> throw SandiUserException("Master password salah.")
            is SandiService.UnlockOutcome.Unlocked -> r.rememberNote?.let(messenger::show)
        }
        refreshTrust()
    }

    /** Penilaian master password (async karena kamus zxcvbn dimuat sekali). */
    override suspend fun evaluateMaster(password: String): Pair<Strength?, String?> = withContext(Dispatchers.Default) {
        val inputs = MasterPassword.userInputsFromEmail(email.get())
        val problem = MasterPassword.problem(password, inputs)
        val strength = if (MasterPassword.isReady && password.isNotEmpty()) MasterPassword.evaluate(password, inputs) else null
        strength to problem
    }

    override fun createVault(password: String) = run("Membuat brankas… (menurunkan kunci, beberapa detik)") {
        evaluateMaster(password).second?.let { throw SandiUserException(it) }
        val recovery = service.createVault(password)
        local.update { it.copy(recoveryToShow = recovery) }
    }

    override fun recover(recoveryKey: String, newPassword: String) = run("Memulihkan brankas…") {
        evaluateMaster(newPassword).second?.let { throw SandiUserException(it) }
        val done = service.recover(recoveryKey, newPassword)
        afterRotation(done.recoveryKey, done.confirmed, "Brankas dipulihkan", skippedMessage(done.result))
    }

    override fun changePassword(old: String, new: String) = run("Mengganti master password…") {
        evaluateMaster(new).second?.let { throw SandiUserException(it) }
        val done = service.changePassword(old, new)
        afterRotation(done.recoveryKey, done.confirmed, "Master password diganti", skippedMessage(done.result))
    }

    override fun rotateRecovery(password: String) = run("Membuat kunci pemulihan baru…") {
        val done = service.rotateRecoveryKey(password)
        afterRotation(done.recoveryKey, done.confirmed, "Kunci pemulihan baru dibuat", skippedMessage(done.result))
    }

    private fun afterRotation(recovery: String, confirmed: Boolean, what: String, skipped: String?) {
        local.update { it.copy(recoveryToShow = recovery) }
        messenger.show(if (confirmed) "$what." else "$what di perangkat ini; konfirmasi server belum diterima, akan dicek otomatis.")
        skipped?.let { messenger.show(it) }
    }

    override fun acknowledgeRecovery() { local.update { it.copy(recoveryToShow = null) } }
    override fun lock() { service.lock() }

    override fun setAutoLock(minutes: Int) {
        service.session.setAutoLock(minutes)
        local.update { it.copy(autoLock = minutes) }
    }

    override fun forgetDevice() {
        service.forgetDevice()
        messenger.show("Perangkat ini tidak lagi diingat")
        refreshTrust()
    }

    override fun dismissRejectedKey() { launchSafe { service.dismissRejectedKeyCreation() } }
    override fun dismissRejectedRotation() { launchSafe { service.dismissRejectedRotation() } }
    override fun dismissStaleWrites() { launchSafe { service.dismissStaleWrites() } }

    // ---------------------------------------------------------------- item

    override fun save(payload: ItemPayload, id: String?, onDone: () -> Unit) = run("Menyimpan…") {
        service.saveItem(payload, id)
        messenger.show(if (id == null) "Sandi ditambahkan" else "Sandi disimpan")
        onDone()
    }

    override fun remove(item: SandiItem) {
        launchSafe {
            service.removeItem(item.id)
            messenger.undoable("\"${item.payload.title.ifBlank { "Sandi" }}\" dihapus") { service.restoreItem(item.id) }
        }
    }

    override fun copy(what: String, text: String, secret: Boolean) {
        if (clipboard.copy(what, text, secret)) messenger.show(if (secret) "$what disalin · dihapus dari papan klip dalam 30 dtk" else "$what disalin")
        service.session.touch()
    }

    /** Salin kode TOTP + catat ke riwayat (terenkripsi). */
    override fun copyCode(item: SandiItem, code: String) {
        copy("Kode ${item.payload.title}", code)
        launchSafe { runCatching { service.recordTotpUse(item.id, item.payload.title, code) } }
    }

    /** Cek cepat → simpan kunci TOTP sebagai item baru. */
    override fun saveQuickTotp(title: String, config: TotpConfig, username: String, onDone: () -> Unit) = run("Menyimpan…") {
        service.saveItem(ItemPayload(title = title.trim(), username = username, totp = config))
        messenger.show("\"${title.trim()}\" disimpan ke brankas")
        onDone()
    }

    override fun deleteLog(id: String) { launchSafe { service.deleteTotpLog(id) } }
    override fun clearLog() { launchSafe { service.clearTotpLog() } }

    // ---------------------------------------------------------------- ekspor

    override fun startExport(kind: ExportKind, password: String) = run("Memeriksa master password…") {
        exporter.start(kind, password, state.value.items.items.map { it.payload })
    }

    override fun exportLaunched() = exporter.launched()
    override fun finishExport(uri: String?) = exporter.finish(uri)
    override fun closeMigration() = exporter.closeMigration()

    // ---------------------------------------------------------------- impor

    override fun startImport() = importer.pick()
    override fun importLaunched() = importer.launched()
    override fun importPicked(uri: String?) = run("Membaca berkas…") { importer.open(uri) }

    override fun confirmImport() = run("Mengimpor…") {
        val ready = state.value.items
        if (!ready.ready) throw SandiUserException("Brankas masih dibuka. Coba lagi sebentar.")
        importer.confirm(ready.items.map { it.payload })
    }

    override fun cancelImport() = importer.cancel()
    override fun deleteImportSource() { launchSafe { importer.deleteSource() } }
    override fun dismissImportDone() = importer.dismissDone()

    override fun onCleared() {
        exporter.clear()
        importer.clear()
        super.onCleared()
    }

    // ---------------------------------------------------------------- generator

    override fun setGenerator(p: Generator.Prefs) {
        val clean = Generator.sanitize(p)
        prefs.generator = clean
        local.update { it.copy(generator = clean) }
        regenerate()
    }

    override fun regenerate() {
        val g = local.value.generator
        val text = runCatching {
            if (g.mode == Generator.Mode.Password) Generator.password(g.password) else Generator.passphrase(g.passphrase)
        }.getOrDefault("")
        local.update { it.copy(generated = text) }
    }
}
