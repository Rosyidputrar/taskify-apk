package com.rosyidputrar.taskify.core.device

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import com.rosyidputrar.taskify.core.domain.device.AppDay
import com.rosyidputrar.taskify.core.domain.device.DeviceDay
import com.rosyidputrar.taskify.core.domain.device.UsageEvent
import com.rosyidputrar.taskify.core.domain.device.UsageSession
import com.rosyidputrar.taskify.core.domain.device.UsageMath
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.store.device.NotificationLogRepository
import com.rosyidputrar.taskify.core.store.device.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Sumber kejadian pemakaian (dipisah supaya bisa diganti di tes). */
fun interface UsageEventSource {
    fun read(from: Long, to: Long): List<UsageEvent>
}

/** Membaca UsageStatsManager → [UsageEvent]. */
class SystemUsageEventSource(private val context: Context) : UsageEventSource {
    override fun read(from: Long, to: Long): List<UsageEvent> {
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val events = usm.queryEvents(from, to) ?: return emptyList()
        val out = ArrayList<UsageEvent>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val kind = when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> UsageEvent.Kind.Resumed
                UsageEvents.Event.ACTIVITY_PAUSED -> UsageEvent.Kind.Paused
                KEYGUARD_HIDDEN -> UsageEvent.Kind.Unlock
                SCREEN_INTERACTIVE -> UsageEvent.Kind.ScreenOn
                SCREEN_NON_INTERACTIVE -> UsageEvent.Kind.ScreenOff
                else -> null
            } ?: continue
            out += UsageEvent(e.packageName, kind, e.timeStamp)
        }
        return out
    }

    private companion object {
        // Konstanta API 28 (minSdk 26 → ditulis sebagai angka).
        const val SCREEN_INTERACTIVE = 15
        const val SCREEN_NON_INTERACTIVE = 16
        const val KEYGUARD_HIDDEN = 18
    }
}

/**
 * Menyalin data pemakaian sistem ke tabel lokal. Sistem hanya menyimpan kejadian rinci ±7 hari,
 * jadi setiap jalan hari-hari yang masih tersedia dihitung ulang; hari yang sudah hilang dari sistem
 * tidak ditimpa (rekap lama tetap).
 */
class UsageCollector(
    private val context: Context,
    private val repo: UsageRepository,
    private val notifs: NotificationLogRepository,
    private val prefs: DeviceModulePrefs,
    private val apps: AppInfo,
    private val source: UsageEventSource = SystemUsageEventSource(context),
    private val hasAccess: () -> Boolean = { DeviceAccess.usageAccess(context) },
) {
    private val mutex = Mutex()

    /** @return false bila izin Akses penggunaan belum ada. */
    suspend fun sync(now: Long = System.currentTimeMillis()): Boolean = mutex.withLock {
        pruneNotifications(now)
        if (!hasAccess()) return@withLock false
        withContext(Dispatchers.IO) {
            val settings = prefs.usage.value
            val dayStart = settings.dayStartHour
            val today = UsageMath.dayOf(now, dayStart)
            val last = repo.lastDay()
            val earliestAllowed = Dates.addDays(today, -BACKFILL_DAYS)
            val firstDay = maxOf(earliestAllowed, last?.let { Dates.addDays(it, -1) } ?: earliestAllowed)
            val from = UsageMath.window(firstDay, dayStart).first - LOOKBACK_MS
            val events = source.read(from, now)
            plan(events, now, firstDay, today, dayStart, last, apps.launchers() + AppInfo.SYSTEM_UI).forEach { d ->
                repo.replaceDay(d.day, d.sessions, d.apps, d.apps.associate { it.pkg to apps.label(it.pkg) }, d.device)
            }
            repo.pruneSessions(Dates.addDays(today, -settings.sessionRetentionDays.toLong()))
        }
        true
    }

    private suspend fun pruneNotifications(now: Long) {
        val today = Dates.today(now)
        runCatching {
            notifs.prune(
                beforeDay = Dates.addDays(today, -(prefs.notif.value.retentionDays - 1).toLong()),
                countsBeforeDay = Dates.addDays(today, -NotificationLogRepository.COUNT_KEEP_DAYS.toLong()),
            )
        }
    }

    /** Hasil hitung satu hari, siap ditulis. */
    data class DayData(val day: String, val sessions: List<UsageSession>, val apps: List<AppDay>, val device: DeviceDay)

    companion object {
        /**
         * Hitung ulang setiap hari [firstDay]..[today] dari [events]. Hari yang kejadiannya sudah
         * sebagian dibuang sistem (kejadian paling awal sesudah awal hari) dilewati bila rekapnya
         * sudah tersimpan ([lastStored]) — supaya data lama yang lengkap tidak tertimpa data terpotong.
         */
        fun plan(
            events: List<UsageEvent>,
            now: Long,
            firstDay: String,
            today: String,
            dayStartHour: Int,
            lastStored: String?,
            ignore: Set<String>,
        ): List<DayData> {
            if (events.isEmpty()) return emptyList()
            val earliest = events.minOf { it.at }
            val sessions = UsageMath.sessions(events, until = now, ignore = ignore)
            val out = mutableListOf<DayData>()
            var day = firstDay
            while (day <= today) {
                val window = UsageMath.window(day, dayStartHour)
                val partial = earliest > window.first
                if (!partial || lastStored == null || day > lastStored) {
                    val clipped = UsageMath.clip(sessions, window.first until minOf(window.last + 1, now))
                    out += DayData(day, clipped, UsageMath.perApp(clipped), UsageMath.deviceDay(day, events, clipped, window))
                }
                day = Dates.addDays(day, 1)
            }
            return out
        }

        /** Sistem menyimpan kejadian rinci kira-kira seminggu; impor pertama mengambil sebanyak itu. */
        const val BACKFILL_DAYS = 9L
        /** Mundur sedikit dari awal hari supaya aplikasi yang sudah terbuka sebelum tengah malam ikut terhitung. */
        const val LOOKBACK_MS = 6 * UsageMath.HOUR
    }
}
