package com.rosyidputrar.taskify.core.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rosyidputrar.taskify.core.store.tasks.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Menjalankan kerja async dari BroadcastReceiver tanpa memblokir thread utama. */
private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** Alarm pengingat berbunyi → tampilkan notifikasi bila tugas masih aktif & waktunya belum diubah. */
class ReminderReceiver : BroadcastReceiver(), KoinComponent {
    private val tasks: TaskRepository by inject()
    private val notifier: ReminderNotifier by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val expected = intent.getStringExtra(EXTRA_REMIND_AT)
        runAsync {
            val task = tasks.get(taskId) ?: return@runAsync
            if (task.deletedAt != null || !task.status.isActive) return@runAsync
            // Waktu pengingat diubah (mis. dari laptop) setelah alarm dipasang → alarm baru yang berlaku.
            if (expected != null && task.remindAt != expected) return@runAsync
            notifier.show(task)
        }
    }

    companion object {
        const val ACTION_FIRE = "com.rosyidputrar.taskify.reminder.FIRE"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_REMIND_AT = "remind_at"
    }
}

/** Aksi dari notifikasi: tandai selesai (tugas berulang → kejadian berikutnya) atau tunda. */
class ReminderActionReceiver : BroadcastReceiver(), KoinComponent {
    private val tasks: TaskRepository by inject()
    private val notifier: ReminderNotifier by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(ReminderReceiver.EXTRA_TASK_ID) ?: return
        val action = intent.action
        runAsync {
            notifier.cancel(taskId)
            val task = tasks.get(taskId) ?: return@runAsync
            when (action) {
                ACTION_DONE -> if (task.status.isActive) tasks.toggle(task)
                ACTION_SNOOZE -> tasks.snoozeReminder(taskId, SNOOZE_MINUTES)
            }
        }
    }

    companion object {
        const val ACTION_DONE = "com.rosyidputrar.taskify.reminder.DONE"
        const val ACTION_SNOOZE = "com.rosyidputrar.taskify.reminder.SNOOZE"
        const val SNOOZE_MINUTES = 10
    }
}

/** Setelah reboot / pembaruan aplikasi / ganti zona waktu: pasang ulang semua alarm. */
class RescheduleReceiver : BroadcastReceiver(), KoinComponent {
    private val scheduler: ReminderScheduler by inject()

    override fun onReceive(context: Context, intent: Intent) {
        runAsync { scheduler.rescheduleNow() }
    }
}
