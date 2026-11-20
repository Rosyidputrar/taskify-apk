package com.rosyidputrar.taskify.session

import com.rosyidputrar.taskify.core.auth.AuthRepository
import com.rosyidputrar.taskify.core.auth.AuthState
import com.rosyidputrar.taskify.core.auth.LoginNameStore
import com.rosyidputrar.taskify.core.auth.MfaTrustStore
import com.rosyidputrar.taskify.core.common.Limits
import com.rosyidputrar.taskify.core.common.Timestamps
import com.rosyidputrar.taskify.core.data.SupabaseConnector
import com.rosyidputrar.taskify.core.data.TaskifyDatabase
import com.rosyidputrar.taskify.core.reminders.ReminderScheduler
import com.rosyidputrar.taskify.core.domain.auth.GateStatus
import com.rosyidputrar.taskify.core.domain.auth.cleanDisplayName
import com.rosyidputrar.taskify.core.designsystem.component.UiErrors
import com.rosyidputrar.taskify.core.designsystem.component.launchSafe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Urutan gerbang setelah auth: database lokal baru dibuka & disinkron setelah login DAN sesi aal2.
 * Bila akun yang masuk berbeda dari pemilik database, isinya dikosongkan lebih dulu.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionCoordinator(
    private val auth: AuthRepository,
    private val database: TaskifyDatabase,
    private val connector: SupabaseConnector,
    private val trustStore: MfaTrustStore,
    private val loginNames: LoginNameStore,
    private val reminders: ReminderScheduler,
    private val scope: CoroutineScope,
    private val attachments: com.rosyidputrar.taskify.core.files.AttachmentManager,
    private val sandi: com.rosyidputrar.taskify.core.sandi.SandiService,
    private val tempFiles: com.rosyidputrar.taskify.core.files.TempFileUploader,
    private val search: com.rosyidputrar.taskify.core.store.search.SearchRepository,
    private val trash: com.rosyidputrar.taskify.core.store.cross.TrashRepository,
    private val music: com.rosyidputrar.taskify.core.media.MusicPlayer,
    private val leftovers: DeviceLeftovers,
) {
    private val mutex = Mutex()
    private val _readyUser = MutableStateFlow<String?>(null)

    /** User yang database-nya sudah siap dipakai (null = belum/tertutup). */
    val readyUser: StateFlow<String?> = _readyUser.asStateFlow()

    /**
     * Galat terakhir saat menyiapkan database untuk akun yang baru masuk (null = tidak ada).
     * Ditampilkan di layar "Menyiapkan data…" bersama tombol Coba lagi / Keluar — dulu galat
     * di sini mematikan pengamat sesi dan layar itu berputar selamanya.
     */
    private val _prepareError = MutableStateFlow<String?>(null)
    val prepareError: StateFlow<String?> = _prepareError.asStateFlow()
    private var pendingUser: String? = null

    /** Nama tampilan dari `user_settings`, ikut disalin untuk sapaan layar masuk. */
    val displayName: StateFlow<String?> = readyUser.flatMapLatest<String?, String?> { userId ->
        if (userId == null) {
            flowOf(null)
        } else {
            database.db.watch("SELECT coalesce(display_name, '') FROM user_settings WHERE user_id = ? LIMIT 1", listOf(userId)) {
                it.getString(0) ?: ""
            }.map { rows -> cleanDisplayName(rows.firstOrNull()).ifEmpty { null } }
        }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch {
            auth.state
                .map { (it as? AuthState.SignedIn)?.takeIf { s -> s.gate == GateStatus.Ok }?.userId }
                .distinctUntilChanged()
                .collect { userId -> mutex.withLock { if (userId != null) openSafely(userId) else { pendingUser = null; _prepareError.value = null; close() } } }
        }
        scope.launch { displayName.collect { name -> if (name != null) loginNames.write(name) } }
    }

    /** Coba lagi menyiapkan database setelah gagal (tombol di layar "Menyiapkan data…"). */
    fun retryPrepare(): Job = scope.launchSafe {
        mutex.withLock { pendingUser?.let { if (_readyUser.value != it) openSafely(it) } }
    }

    private suspend fun openSafely(userId: String) {
        pendingUser = userId
        _prepareError.value = null
        try {
            open(userId)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("Taskify", "Gagal menyiapkan database untuk akun", e)
            _readyUser.value = null
            _prepareError.value = "${e::class.java.simpleName}: ${e.message.orEmpty()}".trimEnd(' ', ':')
        }
    }

    private suspend fun open(userId: String) {
        database.prepareFor(userId)
        // Indeks pencarian FTS5 lokal (trigger di tabel PowerSync); gagal = fallback LIKE.
        runCatching { search.setup() }
        _readyUser.value = userId
        database.connect(connector)
        // Isi Sampah > 30 hari dihapus permanen sekali per sesi, SETELAH sinkron awal (sama dengan
        // webapp) — sebelum itu data lokal belum lengkap.
        scope.launch {
            runCatching {
                database.db.waitForFirstSync()
                if (_readyUser.value == userId) trash.purgeExpired()
            }
        }
    }

    private suspend fun close() {
        if (_readyUser.value == null) return
        _readyUser.value = null
        // PowerSync memutus sync saat gerbang menutup aplikasi; database tetap ada di perangkat.
        database.disconnect()
    }

    /** Jumlah perubahan yang belum terunggah — untuk peringatan sebelum Keluar. */
    suspend fun pendingUploads(): Long = database.pendingUploadCount()

    /**
     * Keluar dari layar: berjalan di cakupan aplikasi supaya tidak ikut batal saat layar Pengaturan
     * hilang (gerbang langsung pindah ke layar masuk begitu [readyUser] null).
     */
    fun requestLogout(): Job = scope.launchSafe { logout() }

    /**
     * Keluar: lupakan kepercayaan perangkat, kosongkan database lokal & semua sisa akun, lalu keluarkan
     * sesi (global bila online, lokal bila tidak). Urutan sama dengan webapp. Tiap langkah berdiri
     * sendiri dan sesi SELALU dikeluarkan — bila database gagal dikosongkan, isinya tetap dibuang saat
     * akun lain masuk (pemilik berbeda).
     */
    suspend fun logout() = withContext(NonCancellable) {
        mutex.withLock {
            _readyUser.value = null
            val failed = runLogoutSteps(
                listOf(
                    LogoutStep("kepercayaan perangkat") { trustStore.clear() },
                    // Pengingat akun ini tidak boleh berbunyi lagi setelah keluar.
                    LogoutStep("pengingat") { reminders.cancelAll() },
                    // Salinan lampiran & unggahan tertunda milik akun ini juga dihapus dari perangkat.
                    LogoutStep("lampiran") { attachments.wipe() },
                    // Brankas Sandi dikunci (DEK dihapus dari memori) dan kunci perangkat tepercaya dilupakan.
                    LogoutStep("Sandi") { sandi.wipe() },
                    // Unggahan Berkas yang sedang berjalan dibatalkan; overlay hasil server dilupakan.
                    LogoutStep("Berkas") { tempFiles.wipe() },
                    LogoutStep("indeks pencarian") { search.clear() },
                    // Musik berhenti (token akun ini tidak berlaku lagi).
                    LogoutStep("musik") { withContext(Dispatchers.Main) { music.wipe() } },
                    LogoutStep("database") { database.wipe() },
                    LogoutStep("sisa di perangkat") { leftovers.clear() },
                    LogoutStep("sesi") { auth.signOut() },
                ),
            )
            if (failed.isNotEmpty()) UiErrors.report(LogoutIncompleteException(failed))
        }
    }

    /** Ubah nama tampilan (baris `user_settings` dibuat server saat akun didaftarkan). */
    suspend fun updateDisplayName(raw: String): Boolean {
        val userId = _readyUser.value ?: return false
        val name = Limits.clip(cleanDisplayName(raw), Limits.DISPLAY_NAME)
        // Jumlah baris dari UPDATE pada view PowerSync (trigger INSTEAD OF) selalu 0 → cek dulu.
        val exists = database.db.get("SELECT count(*) FROM user_settings WHERE user_id = ?", listOf(userId)) { it.getLong(0) ?: 0L } > 0
        if (!exists) return false
        database.db.execute(
            "UPDATE user_settings SET display_name = ?, updated_at = ? WHERE user_id = ?",
            listOf(name.ifEmpty { null }, Timestamps.nowIso(), userId),
        )
        return true
    }
}

/** Keluar tetap terjadi, tetapi sebagian sisa akun gagal dibersihkan. */
class LogoutIncompleteException(val steps: List<String>) : IllegalStateException("Keluar, tetapi gagal membersihkan: ${steps.joinToString()}")
