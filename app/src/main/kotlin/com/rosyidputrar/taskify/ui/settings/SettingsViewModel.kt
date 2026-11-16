package com.rosyidputrar.taskify.ui.settings

import com.rosyidputrar.taskify.core.designsystem.component.launchSafe
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
    private val exporter: com.rosyidputrar.taskify.core.store.cross.ExportRepository,
    private val messenger: com.rosyidputrar.taskify.core.designsystem.component.Messenger,
    private val context: android.content.Context,
    private val retrier: com.rosyidputrar.taskify.core.data.RejectedChangeRetrier? = null,
    private val contextPrefs: com.rosyidputrar.taskify.core.reminders.ContextReminderPrefs? = null,
    private val contextScheduler: com.rosyidputrar.taskify.core.reminders.ContextReminderScheduler? = null,
) : ViewModel() {

    /** Pengingat di luar tugas: tagihan, anggaran, streak jurnal. */
    data class ContextUi(val bills: Boolean = true, val budgets: Boolean = true, val journal: Boolean = true, val journalTime: String = "20:00")

    private val _context = MutableStateFlow(readContext())
    val contextReminders: StateFlow<ContextUi> = _context.asStateFlow()

    private fun readContext() = contextPrefs?.let { ContextUi(it.bills, it.budgets, it.journal, it.journalTime) } ?: ContextUi()

    fun setContextReminders(update: (ContextUi) -> ContextUi) {
        val p = contextPrefs ?: return
        val next = update(_context.value)
        p.bills = next.bills
        p.budgets = next.budgets
        p.journal = next.journal
        p.journalTime = next.journalTime
        _context.value = readContext()
        contextScheduler?.schedule()
    }

    private val _retrying = MutableStateFlow(false)
    /** "Coba lagi" perubahan yang ditolak sedang berjalan. */
    val retrying: StateFlow<Boolean> = _retrying.asStateFlow()

    /** Bisa mengirim ulang (server dikonfigurasi). */
    val canRetry: Boolean get() = retrier != null

    fun retryUploadErrors(rows: List<UploadErrorRow>) {
        val r = retrier ?: return
        if (_retrying.value || rows.isEmpty()) return
        _retrying.value = true
        launchSafe {
            try {
                messenger.show(r.retryAll(rows).text)
            } finally {
                _retrying.value = false
            }
        }
    }

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting.asStateFlow()

    fun show(message: String) = messenger.show(message)

    /** Ekspor semua data lokal (termasuk Sampah) ke JSON di lokasi pilihan pengguna — jalan offline. */
    fun export(target: android.net.Uri) {
        if (_exporting.value) return
        _exporting.value = true
        launchSafe {
            try {
                val rows = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openOutputStream(target)?.use { out ->
                        exporter.export(out, auth?.currentUserId(), com.rosyidputrar.taskify.BuildConfig.VERSION_NAME)
                    } ?: error("Tujuan tidak bisa ditulis")
                }
                messenger.show("$rows baris diekspor")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                messenger.show("Ekspor gagal: ${e.message}")
            } finally {
                _exporting.value = false
            }
        }
    }

    data class NotificationUi(val canPost: Boolean = false, val exactAlarms: Boolean = false, val hideContent: Boolean = false)

    private val _notifications = MutableStateFlow(NotificationUi())
    val notifications: StateFlow<NotificationUi> = _notifications.asStateFlow()

    /** Dipanggil saat layar tampil / kembali dari pengaturan sistem. */
    fun refreshNotifications() {
        _notifications.value = NotificationUi(notifier.canPost(), scheduler.canExact(), reminderPrefs.hideContent)
        launchSafe { scheduler.rescheduleNow() }
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
        launchSafe {
            val pending = if (hasAccount) runCatching { database.pendingUploadCount() }.getOrDefault(0) else 0
            _storage.value = StorageUi(database.databaseSizeBytes(), pending)
        }
    }

    fun saveDisplayName(name: String) {
        val c = coordinator ?: return
        launchSafe {
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
        launchSafe { lock.enable(pin, autoLockMinutes) }
    }

    fun disablePin() = lock.disable()

    fun setAutoLock(minutes: Int) = lock.setAutoLockMinutes(minutes)

    fun forgetDevice() = auth?.forgetDevice()

    fun clearUploadErrors() {
        launchSafe { database.clearUploadErrors() }
    }

    fun logout() {
        coordinator?.requestLogout()
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
