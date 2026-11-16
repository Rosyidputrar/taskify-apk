package com.rosyidputrar.taskify.core.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.rosyidputrar.taskify.core.domain.tasks.ReminderPlanner
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.store.tasks.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Pengingat tugas sebagai ALARM LOKAL: semua `tasks.remind_at` yang akan datang (termasuk yang
 * dibuat di laptop dan tiba lewat sync) dipasang di AlarmManager. Jalan tanpa internet dan tanpa
 * perubahan server. Daftar alarm diselaraskan setiap kali tabel tugas berubah — hanya alarm yang
 * baru atau berubah yang dipasang (lihat [ReminderPlanner]), sehingga pengingat yang sudah
 * berbunyi tidak berbunyi lagi saat tugas lain diedit atau data tersinkron.
 */
class ReminderScheduler(
    private val context: Context,
    private val tasks: TaskRepository,
    private val prefs: ReminderPrefs,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val mutex = Mutex()
    private var job: Job? = null

    /** Mulai menyelaraskan alarm mengikuti perubahan data (dipanggil sekali saat aplikasi dibuka). */
    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            // Penyelarasan pertama per proses memasang ulang semua alarm yang AKAN DATANG: setelah
            // "Paksa berhenti" Android menghapus alarm aplikasi walau catatan kita bilang terpasang.
            // Memasang PendingIntent yang sama hanya menimpanya; alarm yang sudah lewat tetap tidak.
            var first = true
            // Watch hanya sebagai pemicu; daftar dibaca ulang dengan batas waktu SAAT INI.
            tasks.watchUpcomingReminders(Dates.millisToIso(now() - ReminderPlanner.GRACE_MS)).collect {
                sync(force = first)
                first = false
            }
        }
    }

    /** Pasang ulang semuanya sekarang (setelah reboot, ganti zona waktu, izin alarm tepat berubah). */
    suspend fun rescheduleNow() = sync(force = true)

    /** Batalkan semua alarm (saat Keluar). */
    suspend fun cancelAll() = mutex.withLock {
        prefs.scheduled.keys.forEach { alarms?.cancel(pendingIntent(it, null)) }
        prefs.scheduled = emptyMap()
    }

    private suspend fun sync(force: Boolean) = mutex.withLock {
        val manager = alarms ?: return@withLock
        val nowMs = now()
        val candidates = tasks.upcomingReminders(Dates.millisToIso(nowMs - ReminderPlanner.GRACE_MS))
        val plan = ReminderPlanner.plan(candidates, prefs.scheduled, nowMs, force)
        plan.cancel.forEach { manager.cancel(pendingIntent(it, null)) }
        plan.set.forEach { alarm ->
            val pi = pendingIntent(alarm.taskId, alarm.remindAt)
            if (canExact()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.atMillis, pi)
            } else {
                // Tanpa izin alarm tepat: tetap berbunyi, bisa meleset beberapa menit (mode hemat daya).
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.atMillis, pi)
            }
        }
        if (plan.scheduled != prefs.scheduled) prefs.scheduled = plan.scheduled
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
