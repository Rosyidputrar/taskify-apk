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
import kotlinx.coroutines.CoroutineScope
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
) {
    private val mutex = Mutex()
    private val _readyUser = MutableStateFlow<String?>(null)

    /** User yang database-nya sudah siap dipakai (null = belum/tertutup). */
    val readyUser: StateFlow<String?> = _readyUser.asStateFlow()

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
                .collect { userId -> mutex.withLock { if (userId != null) open(userId) else close() } }
        }
        scope.launch { displayName.collect { name -> if (name != null) loginNames.write(name) } }
    }

    private suspend fun open(userId: String) {
        database.prepareFor(userId)
        _readyUser.value = userId
        database.connect(connector)
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
     * Keluar: lupakan kepercayaan perangkat, kosongkan database lokal, lalu keluarkan sesi
     * (global bila online, lokal bila tidak). Urutan sama dengan webapp.
     */
    suspend fun logout() = mutex.withLock {
        trustStore.clear()
        _readyUser.value = null
        // Pengingat akun ini tidak boleh berbunyi lagi setelah keluar.
        reminders.cancelAll()
        // Salinan lampiran & unggahan tertunda milik akun ini juga dihapus dari perangkat.
        attachments.wipe()
        // Brankas Sandi dikunci (DEK dihapus dari memori) dan kunci perangkat tepercaya dilupakan.
        sandi.wipe()
        database.wipe()
        auth.signOut()
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
