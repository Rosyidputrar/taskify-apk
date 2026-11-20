package com.rosyidputrar.taskify.core.device

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.rosyidputrar.taskify.core.common.Rupiah
import com.rosyidputrar.taskify.core.domain.device.MoneyNotif
import com.rosyidputrar.taskify.core.domain.finance.TxKind

/** Notifikasi "Transaksi terdeteksi" — mengajak meninjau draf, tidak pernah menyimpan otomatis. */
class DraftAlerts(private val context: Context, private val hideContent: () -> Boolean = { false }) {
    // False positive lint: izin POST_NOTIFICATIONS SUDAH dicek di awal metode ini
    // (early return bila belum diberikan, SDK 33+; di bawah 33 izin ini tidak ada).
    // Lint tidak mengenali guard itu karena notify() berada di dalam runCatching,
    // dan @RequiresPermission di sini justru memindah galat ke pemanggil
    // (TaskifyNotificationListener, callback framework yang tetap jalan tanpa izin
    // ini — ia hanya melewati alert). Jadi supresi terarah dengan alasan ini.
    @SuppressLint("MissingPermission")
    fun show(id: String, money: MoneyNotif) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL, "Transaksi terdeteksi", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Draf transaksi dari notifikasi bank & dompet digital, menunggu kamu tinjau."
            },
        )
        val verb = if (money.kind == TxKind.Income) "Masuk" else "Keluar"
        val text = if (hideContent()) "Ketuk untuk meninjau di Taskify." else
            "$verb ${Rupiah.format(money.amount)}" + (money.counterparty?.let { " · $it" } ?: "") + " · ${money.source}"
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.setAction(ACTION_OPEN_MODULE)
            ?.setData(Uri.parse("taskify://module/$MODULE"))
            ?.putExtra(EXTRA_MODULE, MODULE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            ?: Intent()
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_device_money)
            .setContentTitle("Transaksi terdeteksi — tinjau draf")
            .setContentText(text)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(PendingIntent.getActivity(context, ID, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID, n) }
    }

    fun cancel() = NotificationManagerCompat.from(context).cancel(ID)

    companion object {
        const val CHANNEL = "device_drafts"
        const val ID = 43_001
        /** Sama dengan ModuleShortcuts di aplikasi. */
        const val ACTION_OPEN_MODULE = "com.rosyidputrar.taskify.action.OPEN_MODULE"
        const val EXTRA_MODULE = "module"
        const val MODULE = "Notifications"
    }
}
