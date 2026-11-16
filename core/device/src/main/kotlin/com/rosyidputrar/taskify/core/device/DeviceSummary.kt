package com.rosyidputrar.taskify.core.device

import com.rosyidputrar.taskify.core.domain.device.UsageMath
import com.rosyidputrar.taskify.core.store.device.NotificationLogRepository
import com.rosyidputrar.taskify.core.store.device.UsageRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

/** Ringkasan hari ini untuk Beranda (null = belum ada data / izin). */
data class DeviceToday(val screenMs: Long?, val notifications: Int?)

/** Ringkasan satu rentang untuk Review. */
data class DeviceRange(
    val daysWithData: Int = 0,
    val avgScreenMs: Long = 0,
    val avgUnlocks: Int = 0,
    /** Median jam pertama dipegang (menit sejak tengah malam). */
    val pickupMinute: Long? = null,
    val notifications: Int = 0,
) {
    val isEmpty: Boolean get() = daysWithData == 0 && notifications == 0
}

/** Angka Kesehatan Digital & Notifikasi untuk modul lain (Beranda, Review). Menghormati aplikasi yang dikecualikan. */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceSummary(
    private val usage: UsageRepository,
    private val notifs: NotificationLogRepository,
    private val prefs: DeviceModulePrefs,
) {
    fun watchToday(now: () -> Long = System::currentTimeMillis): Flow<DeviceToday> = prefs.usage.flatMapLatest { settings ->
        val day = UsageMath.dayOf(now(), settings.dayStartHour)
        val calendarDay = UsageMath.dayOf(now(), 0)
        combine(usage.watchApps(day, day), usage.watchDevice(day, day), notifs.watchDailyTotals(calendarDay, calendarDay)) { apps, device, totals ->
            DeviceToday(
                screenMs = if (device.isEmpty()) null else apps.filter { it.pkg !in settings.excluded }.sumOf { it.foregroundMs },
                notifications = totals[calendarDay],
            )
        }
    }

    fun watchRange(from: String, to: String): Flow<DeviceRange> = prefs.usage.flatMapLatest { settings ->
        combine(usage.watchAppDays(from, to), usage.watchDevice(from, to), notifs.watchDailyTotals(from, to)) { appDays, devices, totals ->
            val days = devices.map { it.day }
            DeviceRange(
                daysWithData = days.size,
                avgScreenMs = if (days.isEmpty()) 0 else days.sumOf { d -> appDays[d].orEmpty().filter { it.pkg !in settings.excluded }.sumOf { it.foregroundMs } } / days.size,
                avgUnlocks = if (devices.isEmpty()) 0 else devices.sumOf { it.unlocks } / devices.size,
                pickupMinute = UsageMath.median(devices.mapNotNull { it.firstPickup }.map(UsageMath::minuteOfDay)),
                notifications = totals.values.sum(),
            )
        }
    }
}
