package com.rosyidputrar.taskify.core.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.rosyidputrar.taskify.core.domain.tasks.Task
import com.rosyidputrar.taskify.core.domain.time.Dates

/** Menampilkan notifikasi pengingat tugas dengan aksi "Selesai" dan "Tunda 10 mnt". */
class ReminderNotifier(private val context: Context, private val prefs: ReminderPrefs) {

    fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL_ID, "Pengingat tugas", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Berbunyi saat waktu pengingat tugas tiba"
        }
        manager.createNotificationChannel(channel)
    }

    fun canPost(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(task: Task) {
        if (!canPost()) return
        ensureChannel()
        val hide = prefs.hideContent
        val today = Dates.today()
        val due = task.dueDate?.let { d ->
            val t = Dates.shortTime(task.dueTime)
            Dates.relativeDayLabel(d, today) + if (t.isNotEmpty()) ", $t" else ""
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(if (hide) "Ada pengingat tugas" else task.title)
            .setContentText(if (hide) null else due?.let { "Jatuh tempo $it" })
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            // Notifikasi yang masih tampil diperbarui tanpa bunyi/heads-up kedua.
            .setOnlyAlertOnce(true)
            .setContentIntent(openTask(task.id))
            .addAction(0, "Selesai", action(task.id, ReminderActionReceiver.ACTION_DONE))
            .addAction(0, "Tunda 10 mnt", action(task.id, ReminderActionReceiver.ACTION_SNOOZE))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(notificationId(task.id), notification) }
    }

    fun cancel(taskId: String) = NotificationManagerCompat.from(context).cancel(notificationId(taskId))

    private fun openTask(taskId: String): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.setAction(ACTION_OPEN_TASK)
            ?.setData(Uri.parse("taskify://task/$taskId"))
            ?.putExtra(EXTRA_TASK_ID, taskId)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            ?: Intent()
        return PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun action(taskId: String, action: String): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("taskify://reminder-action/$action/$taskId"))
            .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun notificationId(taskId: String) = taskId.hashCode()

    companion object {
        const val CHANNEL_ID = "task_reminders"
        const val ACTION_OPEN_TASK = "com.rosyidputrar.taskify.action.OPEN_TASK"
        const val EXTRA_TASK_ID = "task_id"
    }
}
