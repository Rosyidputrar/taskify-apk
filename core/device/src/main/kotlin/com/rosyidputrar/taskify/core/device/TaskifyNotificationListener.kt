package com.rosyidputrar.taskify.core.device

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.rosyidputrar.taskify.core.store.UserIdProvider
import com.rosyidputrar.taskify.core.store.device.NotificationLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.ConcurrentHashMap

/**
 * Pencatat modul Notifikasi. Hanya aktif setelah pengguna memberi Akses notifikasi; tidak mencatat
 * apa pun saat belum masuk akun. Isi disaring [NotificationCapture] (OTP disensor, tingkat
 * penyimpanan dihormati) lalu disimpan di tabel lokal — tidak pernah dikirim ke server.
 */
class TaskifyNotificationListener : NotificationListenerService(), KoinComponent {
    private val repo: NotificationLogRepository by inject()
    private val prefs: DeviceModulePrefs by inject()
    private val apps: AppInfo by inject()
    private val users: UserIdProvider by inject()
    private val alerts: DraftAlerts by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        prefs.setListenerConnected(true)
        // Notifikasi yang sudah tampil sebelum tersambung ikut dicatat (duplikat diabaikan repositori).
        runCatching { activeNotifications }.getOrNull()?.forEach { handle(it) }
    }

    override fun onListenerDisconnected() {
        prefs.setListenerConnected(false)
        ActiveNotifications.clear()
        DeviceAccess.rebindListener(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = handle(sbn)

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        ActiveNotifications.remove(sbn.key)
    }

    private fun handle(sbn: StatusBarNotification) {
        sbn.notification.contentIntent?.let { ActiveNotifications.put(sbn.key, it) }
        if (users.currentUserId() == null) return
        val raw = read(sbn)
        val decision = NotificationCapture.decide(raw, prefs.notif.value, packageName, apps.label(raw.pkg))
        scope.launch {
            runCatching {
                when (decision) {
                    CaptureDecision.Ignore -> Unit
                    is CaptureDecision.CountOnly -> repo.count(decision.day, decision.pkg, apps.label(decision.pkg))
                    is CaptureDecision.Log -> {
                        val id = repo.log(decision.notif)
                        val money = decision.notif.money
                        if (id != null && money != null && prefs.notif.value.draftAlerts) alerts.show(id, money)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun read(sbn: StatusBarNotification): RawNotif {
            val n = sbn.notification
            val extras = n.extras ?: Bundle()
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
            val conversation = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            val text = lastMessage(extras)
                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            return RawNotif(
                pkg = sbn.packageName,
                key = sbn.key,
                title = title,
                conversation = conversation,
                text = text,
                androidCategory = n.category,
                postedAt = sbn.postTime,
                ongoing = sbn.isOngoing || (n.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0,
                groupSummary = (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0,
            )
        }

        /** Gaya pesan (WhatsApp, Telegram, …): ambil pesan terakhir, bukan "3 pesan baru". */
        @Suppress("DEPRECATION")
        private fun lastMessage(extras: Bundle): String? {
            val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return null
            val last = messages.lastOrNull() as? Bundle ?: return null
            return last.getCharSequence("text")?.toString()
        }
    }
}

/** PendingIntent notifikasi yang masih tampil — untuk "Buka di aplikasi" membuka percakapan aslinya. */
object ActiveNotifications {
    private val map = ConcurrentHashMap<String, PendingIntent>()

    fun put(key: String, intent: PendingIntent) {
        map[key] = intent
    }

    fun remove(key: String) {
        map.remove(key)
    }

    fun clear() = map.clear()

    /** true bila berhasil membuka lewat notifikasi aslinya. */
    fun open(context: Context, key: String?): Boolean {
        val pi = key?.let { map[it] } ?: return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= 34) {
                @Suppress("DEPRECATION")
                val options = ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                pi.send(context, 0, null, null, null, null, options.toBundle())
            } else {
                pi.send()
            }
            true
        }.getOrDefault(false)
    }
}
