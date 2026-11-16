package com.rosyidputrar.taskify.core.device

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rosyidputrar.taskify.core.store.UserIdProvider
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/** Tiap jam: salin data pemakaian & buang riwayat notifikasi yang lewat masa simpan. */
class DeviceSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val collector: UsageCollector by inject()
    private val users: UserIdProvider by inject()

    override suspend fun doWork(): Result {
        if (users.currentUserId() == null) return Result.success()
        return runCatching { collector.sync() }.fold({ Result.success() }, { Result.retry() })
    }
}

class DeviceScheduler(private val context: Context) {
    fun schedule() {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            WORK_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DeviceSyncWorker>(1, TimeUnit.HOURS, 15, TimeUnit.MINUTES).build(),
        )
    }

    /** Segarkan sekarang (mis. saat layar Kesehatan Digital dibuka). */
    fun syncNow() {
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NOW, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<DeviceSyncWorker>().build())
    }

    fun cancel() {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(WORK_PERIODIC)
        wm.cancelUniqueWork(WORK_NOW)
    }

    companion object {
        const val WORK_PERIODIC = "device-sync"
        const val WORK_NOW = "device-sync-now"
    }
}
