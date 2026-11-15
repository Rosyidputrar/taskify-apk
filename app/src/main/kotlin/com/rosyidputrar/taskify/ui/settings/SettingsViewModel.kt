package com.rosyidputrar.taskify.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.powersync.sync.SyncStatusData
import com.rosyidputrar.taskify.core.auth.AuthRepository
import com.rosyidputrar.taskify.core.auth.LockManager
import com.rosyidputrar.taskify.core.auth.LockState
import com.rosyidputrar.taskify.core.auth.LoginNameStore
import com.rosyidputrar.taskify.core.auth.NetworkMonitor
import com.rosyidputrar.taskify.core.data.TaskifyDatabase
import com.rosyidputrar.taskify.core.data.UploadErrorRow
import com.rosyidputrar.taskify.session.SessionCoordinator
import com.rosyidputrar.taskify.core.reminders.ReminderNotifier
import com.rosyidputrar.taskify.core.reminders.ReminderPrefs
import com.rosyidputrar.taskify.core.reminders.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SyncSummary(val label: String, val detail: String?, val healthy: Boolean)

data class AccountUi(
    val email: String? = null,
    val displayName: String? = null,
    val loginNameEnabled: Boolean = true,
    val nameSaveMessage: String? = null,
)

/** Pengaturan yang butuh akun/database. Semua dependensi server boleh null (belum dikonfigurasi). */
class SettingsViewModel(
    private val coordinator: SessionCoordinator?,
    private val auth: AuthRepository?,
    private val lock: LockManager,
    private val loginNames: LoginNameStore,
    private val database: TaskifyDatabase,
    private val network: NetworkMonitor,
    private val notifier: ReminderNotifier,
    private val scheduler: ReminderScheduler,
    private val reminderPrefs: ReminderPrefs,
) : ViewModel() {

    data class NotificationUi(val canPost: Boolean = false, val exactAlarms: Boolean = false, val hideContent: Boolean = false)

    private val _notifications = MutableStateFlow(NotificationUi())
    val notifications: StateFlow<NotificationUi> = _notifications.asStateFlow()

    /** Dipanggil saat layar tampil / kembali dari pengaturan sistem. */
    fun refreshNotifications() {
        _notifications.value = NotificationUi(notifier.canPost(), scheduler.canExact(), reminderPrefs.hideContent)
        viewModelScope.launch { scheduler.rescheduleNow() }
    }

    fun setHideContent(hide: Boolean) {
        reminderPrefs.hideContent = hide
        refreshNotifications()
    }

    val hasAccount: Boolean = coordinator != null && auth != null

    private val _account = MutableStateFlow(AccountUi(email = auth?.currentEmail(), loginNameEnabled = loginNames.enabled))
    val account: StateFlow<AccountUi> = combine(_account, coordinator?.displayName ?: flowOf(null)) { a, name ->
        a.copy(displayName = name)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), _account.value)

    val lockState: StateFlow<LockState> = lock.state

    val sync: StateFlow<SyncSummary> = if (hasAccount) {
        combine(database.syncStatus, network.online) { status, online -> summarize(status, online) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncSummary("Memeriksa…", null, true))
    } else {
        MutableStateFlow(SyncSummary("Tidak aktif", "Server belum dikonfigurasi", false))
    }

    val uploadErrors: StateFlow<List<UploadErrorRow>> = (if (hasAccount) database.watchUploadErrors() else flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _storage = MutableStateFlow(StorageUi())
    val storage: StateFlow<StorageUi> = _storage.asStateFlow()

    data class StorageUi(val dbBytes: Long = 0, val pendingUploads: Long = 0)

    fun refreshStorage() {
        viewModelScope.launch {
            val pending = if (hasAccount) runCatching { database.pendingUploadCount() }.getOrDefault(0) else 0
            _storage.value = StorageUi(database.databaseSizeBytes(), pending)
        }
    }

    fun saveDisplayName(name: String) {
        val c = coordinator ?: return
        viewModelScope.launch {
            val ok = c.updateDisplayName(name)
            _account.value = _account.value.copy(
                nameSaveMessage = if (ok) "Tersimpan. Akan tersinkron ke perangkat lain." else "Data akun belum tersinkron. Coba lagi setelah online.",
            )
        }
    }

    fun setLoginNameEnabled(enabled: Boolean) {
        loginNames.setEnabled(enabled, account.value.displayName)
        _account.value = _account.value.copy(loginNameEnabled = enabled)
    }

    fun enablePin(pin: String, autoLockMinutes: Int) {
        viewModelScope.launch { lock.enable(pin, autoLockMinutes) }
    }

    fun disablePin() = lock.disable()

    fun setAutoLock(minutes: Int) = lock.setAutoLockMinutes(minutes)

    fun forgetDevice() = auth?.forgetDevice()

    fun clearUploadErrors() {
        viewModelScope.launch { database.clearUploadErrors() }
    }

    fun logout() {
        val c = coordinator ?: return
        viewModelScope.launch { c.logout() }
    }

    private fun summarize(status: SyncStatusData, online: Boolean): SyncSummary {
        val last = status.lastSyncedAt?.let { "Terakhir sinkron ${formatInstant(it.toEpochMilliseconds())}" }
        return when {
            !online -> SyncSummary("Offline", "Perubahan disimpan di perangkat & dikirim saat online. ${last.orEmpty()}".trim(), true)
            status.anyError != null -> SyncSummary("Gangguan sinkronisasi", "Akan dicoba lagi otomatis. ${last.orEmpty()}".trim(), false)
            status.downloading -> SyncSummary("Mengunduh perubahan…", last, true)
            status.uploading -> SyncSummary("Mengunggah perubahan…", last, true)
            status.connecting -> SyncSummary("Menghubungkan…", last, true)
            status.connected -> SyncSummary("Tersinkron", last, true)
            else -> SyncSummary("Belum terhubung", last, false)
        }
    }

    private fun formatInstant(millis: Long): String {
        val zoned = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault())
        return java.time.format.DateTimeFormatter.ofPattern("d MMM, HH:mm", java.util.Locale.forLanguageTag("id-ID")).format(zoned)
    }
}
