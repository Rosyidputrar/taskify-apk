package com.rosyidputrar.taskify.core.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.store.tasks.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Pengingat tugas sebagai ALARM LOKAL: semua `tasks.remind_at` yang akan datang (termasuk yang
 * dibuat di laptop dan tiba lewat sync) dipasang di AlarmManager. Jalan tanpa internet dan tanpa
 * perubahan server. Daftar alarm diselaraskan setiap kali tabel tugas berubah.
 */
class ReminderScheduler(
    private val context: Context,
    private val tasks: TaskRepository,
    private val prefs: ReminderPrefs,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val mutex = Mutex()
    private var job: Job? = null

    /** Mulai menyelaraskan alarm mengikuti perubahan data (dipanggil sekali saat aplikasi dibuka). */
    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            // Sedikit ke belakang: pengingat yang jatuh tepat saat ini tetap terpasang & langsung berbunyi.
            tasks.watchUpcomingReminders(Dates.millisToIso(System.currentTimeMillis() - 60_000)).collect { rows ->
                sync(rows.mapNotNull { t -> t.remindAt?.let { Dates.parseTimestampMillis(it) }?.let { t.id to Pair(it, t.remindAt!!) } }.toMap())
            }
        }
    }

    /** Pasang ulang semuanya sekarang (setelah reboot, ganti zona waktu, izin alarm tepat berubah). */
    suspend fun rescheduleNow() {
        val rows = tasks.watchUpcomingReminders(Dates.millisToIso(System.currentTimeMillis() - 60_000)).first()
        sync(rows.mapNotNull { t -> t.remindAt?.let { Dates.parseTimestampMillis(it) }?.let { t.id to Pair(it, t.remindAt!!) } }.toMap())
    }

    /** Batalkan semua alarm (saat Keluar). */
    suspend fun cancelAll() = mutex.withLock {
        prefs.scheduledIds.forEach { alarms?.cancel(pendingIntent(it, null)) }
        prefs.scheduledIds = emptySet()
    }

    private suspend fun sync(desired: Map<String, Pair<Long, String>>) = mutex.withLock {
        val manager = alarms ?: return@withLock
        (prefs.scheduledIds - desired.keys).forEach { manager.cancel(pendingIntent(it, null)) }
        desired.forEach { (taskId, value) ->
            val (at, remindAt) = value
            val pi = pendingIntent(taskId, remindAt)
            if (canExact()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                // Tanpa izin alarm tepat: tetap berbunyi, bisa meleset beberapa menit (mode hemat daya).
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
        prefs.scheduledIds = desired.keys
    }

    fun canExact(): Boolean = Build.VERSION.SDK_INT < 31 || alarms?.canScheduleExactAlarms() == true

    private fun pendingIntent(taskId: String, remindAt: String?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_FIRE)
            // Data unik per tugas → satu alarm per tugas, mudah dibatalkan.
            .setData(Uri.parse("taskify://reminder/$taskId"))
            .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
            .apply { if (remindAt != null) putExtra(ReminderReceiver.EXTRA_REMIND_AT, remindAt) }
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
