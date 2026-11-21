package com.rosyidputrar.taskify.core.device

import android.content.Context
import androidx.core.content.edit
import com.rosyidputrar.taskify.core.domain.device.NotifStorageLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Pengaturan modul Notifikasi. */
data class NotifSettings(
    val level: NotifStorageLevel = NotifStorageLevel.Full,
    val retentionDays: Int = 7,
    val excluded: Set<String> = emptySet(),
    val ignoreOngoing: Boolean = true,
    val draftAlerts: Boolean = true,
    /** Paket bank/e-wallet → id dompet Taskify (diingat setelah pertama kali dipilih). */
    val wallets: Map<String, String> = emptyMap(),
    /** Kunci merchant/pengirim (ternormalisasi) → id kategori Taskify (dipelajari otomatis saat menyimpan draf). */
    val merchantCategories: Map<String, String> = emptyMap(),
) {
    companion object {
        val RETENTION_CHOICES = listOf(1, 3, 7, 14, 30)
        const val MAX_LEARNED_MERCHANTS = 200
    }
}

/** Pengaturan modul Kesehatan Digital. */
data class UsageSettings(
    /** Jam (WIB) hari pemakaian berganti: 0 = tengah malam; 4 = jam 04.00 untuk yang suka begadang. */
    val dayStartHour: Int = 0,
    val excluded: Set<String> = emptySet(),
    val sessionRetentionDays: Int = 30,
) {
    companion object {
        val DAY_START_CHOICES = listOf(0, 3, 4, 5)
        val RETENTION_CHOICES = listOf(7, 30, 90)
    }
}

/** Preferensi lokal kedua modul (SharedPreferences) + StateFlow supaya UI langsung ikut berubah. */
class DeviceModulePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("taskify_device_modules", Context.MODE_PRIVATE)

    private val _notif = MutableStateFlow(readNotif())
    val notif: StateFlow<NotifSettings> = _notif.asStateFlow()

    private val _usage = MutableStateFlow(readUsage())
    val usage: StateFlow<UsageSettings> = _usage.asStateFlow()

    private val _listenerConnected = MutableStateFlow(prefs.getBoolean(K_CONNECTED, false))
    /** Pendengar notifikasi sedang tersambung ke sistem. */
    val listenerConnected: StateFlow<Boolean> = _listenerConnected.asStateFlow()

    var listenerConnectedAt: Long
        get() = prefs.getLong(K_CONNECTED_AT, 0)
        private set(v) = prefs.edit { putLong(K_CONNECTED_AT, v) }

    /** Kapan pendengar terputus (untuk spanduk "Pencatatan berhenti sejak …"). */
    var listenerDisconnectedAt: Long
        get() = prefs.getLong(K_DISCONNECTED_AT, 0)
        private set(v) = prefs.edit { putLong(K_DISCONNECTED_AT, v) }

    fun setListenerConnected(connected: Boolean, now: Long = System.currentTimeMillis()) {
        prefs.edit { putBoolean(K_CONNECTED, connected) }
        if (connected) listenerConnectedAt = now else listenerDisconnectedAt = now
        _listenerConnected.value = connected
    }

    fun updateNotif(block: (NotifSettings) -> NotifSettings) {
        val raw = block(_notif.value)
        // Batasi memori merchant agar SharedPreferences tetap ringan.
        val next = if (raw.merchantCategories.size > NotifSettings.MAX_LEARNED_MERCHANTS) {
            raw.copy(merchantCategories = raw.merchantCategories.entries.toList().takeLast(NotifSettings.MAX_LEARNED_MERCHANTS).associate { it.key to it.value })
        } else {
            raw
        }
        prefs.edit {
            putString(K_LEVEL, next.level.name)
            putInt(K_RETENTION, next.retentionDays)
            putStringSet(K_NOTIF_EXCLUDED, next.excluded)
            putBoolean(K_IGNORE_ONGOING, next.ignoreOngoing)
            putBoolean(K_DRAFT_ALERTS, next.draftAlerts)
            putStringSet(K_WALLETS, next.wallets.map { (k, v) -> "$k=$v" }.toSet())
            putStringSet(K_MERCHANT_CATEGORIES, next.merchantCategories.map { (k, v) -> "$k=$v" }.toSet())
        }
        _notif.value = next
    }

    fun updateUsage(block: (UsageSettings) -> UsageSettings) {
        val next = block(_usage.value)
        prefs.edit {
            putInt(K_DAY_START, next.dayStartHour)
            putStringSet(K_USAGE_EXCLUDED, next.excluded)
            putInt(K_SESSION_RETENTION, next.sessionRetentionDays)
        }
        _usage.value = next
    }

    /** Keluar akun: kembali ke bawaan. */
    fun clear() {
        prefs.edit { clear() }
        _notif.value = NotifSettings()
        _usage.value = UsageSettings()
        _listenerConnected.value = false
    }

    private fun readNotif() = NotifSettings(
        level = runCatching { NotifStorageLevel.valueOf(prefs.getString(K_LEVEL, null)!!) }.getOrDefault(NotifStorageLevel.Full),
        retentionDays = prefs.getInt(K_RETENTION, 7).takeIf { it in NotifSettings.RETENTION_CHOICES } ?: 7,
        excluded = prefs.getStringSet(K_NOTIF_EXCLUDED, emptySet()).orEmpty().toSet(),
        ignoreOngoing = prefs.getBoolean(K_IGNORE_ONGOING, true),
        draftAlerts = prefs.getBoolean(K_DRAFT_ALERTS, true),
        wallets = prefs.getStringSet(K_WALLETS, emptySet()).orEmpty().mapNotNull { e ->
            e.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
        }.toMap(),
        merchantCategories = prefs.getStringSet(K_MERCHANT_CATEGORIES, emptySet()).orEmpty().mapNotNull { e ->
            e.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] }
        }.toMap(),
    )

    private fun readUsage() = UsageSettings(
        dayStartHour = prefs.getInt(K_DAY_START, 0).takeIf { it in UsageSettings.DAY_START_CHOICES } ?: 0,
        excluded = prefs.getStringSet(K_USAGE_EXCLUDED, emptySet()).orEmpty().toSet(),
        sessionRetentionDays = prefs.getInt(K_SESSION_RETENTION, 30).takeIf { it in UsageSettings.RETENTION_CHOICES } ?: 30,
    )

    private companion object {
        const val K_LEVEL = "notif_level"
        const val K_RETENTION = "notif_retention"
        const val K_NOTIF_EXCLUDED = "notif_excluded"
        const val K_IGNORE_ONGOING = "notif_ignore_ongoing"
        const val K_DRAFT_ALERTS = "notif_draft_alerts"
        const val K_WALLETS = "notif_wallets"
        const val K_MERCHANT_CATEGORIES = "notif_merchant_categories"
        const val K_CONNECTED = "listener_connected"
        const val K_CONNECTED_AT = "listener_connected_at"
        const val K_DISCONNECTED_AT = "listener_disconnected_at"
        const val K_DAY_START = "usage_day_start"
        const val K_USAGE_EXCLUDED = "usage_excluded"
        const val K_SESSION_RETENTION = "usage_session_retention"
    }
}
