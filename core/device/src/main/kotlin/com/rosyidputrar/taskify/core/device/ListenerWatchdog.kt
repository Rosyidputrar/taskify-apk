package com.rosyidputrar.taskify.core.device

import android.content.Context
import android.os.Process
import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Penjaga pencatat notifikasi. Bila proses dimatikan paksa (penghemat baterai), sistem hanya sekali
 * mencoba menyambungkan pendengar lagi; kalau gagal, pencatatan berhenti diam-diam sampai HP
 * dinyalakan ulang. Dipanggil tiap proses mulai dan tiap jam dari [DeviceSyncWorker].
 */
class ListenerWatchdog(
    context: Context,
    private val prefs: DeviceModulePrefs,
    private val hasAccess: () -> Boolean = { DeviceAccess.notificationAccess(context) },
    private val reconnect: () -> Unit = { DeviceAccess.reconnectListener(context) },
    private val elapsed: () -> Long = SystemClock::elapsedRealtime,
    private val processStart: Long = Process.getStartElapsedRealtime(),
) {
    private val mutex = Mutex()
    private var lastAttempt: Long? = null

    /** @return true bila sambung ulang diminta ke sistem. */
    suspend fun check(): Boolean = mutex.withLock {
        if (!hasAccess()) return@withLock false
        // Proses baru: beri sistem waktu menyambungkan pendengar sebelum menganggapnya terputus.
        delay((STARTUP_GRACE_MS - (elapsed() - processStart)).coerceAtLeast(0))
        if (prefs.listenerLive) return@withLock false
        prefs.expireStaleConnection()
        val now = elapsed()
        if (lastAttempt?.let { now - it < RETRY_GAP_MS } == true) return@withLock false
        lastAttempt = now
        reconnect()
        true
    }

    companion object {
        const val STARTUP_GRACE_MS = 10_000L
        /** Sambungan butuh waktu terbentuk; pemeriksaan beruntun tidak boleh memutusnya lagi. */
        const val RETRY_GAP_MS = 60_000L
    }
}
