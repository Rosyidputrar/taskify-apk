package com.rosyidputrar.taskify.core.files

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Layanan foreground (tipe dataSync) selama unggahan/unduhan Berkas berjalan. Transfernya sendiri
 * tetap di [TempFileUploader]; layanan ini menjaga proses tetap hidup & tidak dibekukan saat
 * Taskify ditinggal, menampilkan progres, dan berhenti sendiri begitu tidak ada transfer aktif.
 */
class TransferService : Service(), KoinComponent {
    private val uploader: TempFileUploader by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            uploader.cancelAll()
            return START_NOT_STICKY
        }
        // Wajib masuk foreground segera setelah startForegroundService (batas 5 detik).
        val initial = summarizeTransfers(uploader.jobs.value, uploader.downloads.value) ?: TransferSummary("Menyiapkan transfer…", "", null, 1)
        ServiceCompat.startForeground(this, NOTIFICATION_ID, build(initial),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)
        if (!started) {
            started = true
            scope.launch {
                combine(uploader.jobs, uploader.downloads) { u, d -> summarizeTransfers(u, d) }
                    .distinctUntilChanged()
                    .collect { summary ->
                        if (summary == null) {
                            ServiceCompat.stopForeground(this@TransferService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        } else {
                            runCatching { NotificationManagerCompat.from(this@TransferService).notify(NOTIFICATION_ID, build(summary)) }
                        }
                    }
            }
        }
        // Proses mati → transfer di memori ikut hilang; jangan dibangkitkan ulang tanpa isinya.
        return START_NOT_STICKY
    }

    private fun build(s: TransferSummary) = NotificationCompat.Builder(this, ensureChannel(this))
        .setSmallIcon(android.R.drawable.stat_sys_upload)
        .setContentTitle(s.title)
        .setContentText(s.text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .apply { if (s.progress == null) setProgress(0, 0, true) else setProgress(1000, (s.progress * 1000).toInt(), false) }
        .apply { packageManager.getLaunchIntentForPackage(packageName)?.let { setContentIntent(PendingIntent.getActivity(this@TransferService, 0, it, PendingIntent.FLAG_IMMUTABLE)) } }
        .addAction(0, "Batalkan", PendingIntent.getService(this, 1, Intent(this, TransferService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        .build()

    /** Aplikasi digeser dari Terkini: transfer tetap jalan (itulah gunanya layanan ini). */
    override fun onTaskRemoved(rootIntent: Intent?) = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 4107
        const val CHANNEL_ID = "transfers"
        const val ACTION_CANCEL = "com.rosyidputrar.taskify.files.CANCEL_TRANSFERS"

        fun ensureChannel(context: Context): String {
            if (Build.VERSION.SDK_INT >= 26) {
                val nm = context.getSystemService(NotificationManager::class.java)
                if (nm?.getNotificationChannel(CHANNEL_ID) == null) {
                    nm?.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Unggah & unduh Berkas", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "Progres transfer berkas yang berjalan di latar"
                        setShowBadge(false)
                    })
                }
            }
            return CHANNEL_ID
        }
    }
}
