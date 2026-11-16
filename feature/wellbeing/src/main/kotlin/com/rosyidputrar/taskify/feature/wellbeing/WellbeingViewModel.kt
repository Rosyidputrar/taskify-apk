package com.rosyidputrar.taskify.feature.wellbeing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.designsystem.component.launchSafe
import com.rosyidputrar.taskify.core.device.AccessProbe
import com.rosyidputrar.taskify.core.device.DeviceModulePrefs
import com.rosyidputrar.taskify.core.device.UsageSettings
import com.rosyidputrar.taskify.core.domain.device.DeviceDay
import com.rosyidputrar.taskify.core.domain.device.UsageMath
import com.rosyidputrar.taskify.core.domain.device.UsageSession
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.store.device.AppDayPoint
import com.rosyidputrar.taskify.core.store.device.AppUsage
import com.rosyidputrar.taskify.core.store.device.NotificationLogRepository
import com.rosyidputrar.taskify.core.store.device.UsageRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class WbRange(val label: String) { Day("Hari"), Week("Minggu") }

data class WellbeingUiState(
    val today: String = Dates.today(),
    val day: String = today,
    val range: WbRange = WbRange.Day,
    val access: Boolean = false,
    val settings: UsageSettings = UsageSettings(),
    /** Rekap perangkat 14 hari terakhir s.d. [day] (lama → baru). */
    val history: List<DeviceDay> = emptyList(),
    /** Aplikasi per hari, 14 hari terakhir s.d. [day]. */
    val appDays: Map<String, List<AppUsage>> = emptyMap(),
    val sessions: List<UsageSession> = emptyList(),
    val notifTimes: List<Long> = emptyList(),
    val notifTotals: Map<String, Int> = emptyMap(),
    val days: List<String> = emptyList(),
    val syncing: Boolean = false,
    val loaded: Boolean = false,
    val now: Long = System.currentTimeMillis(),
) {
    private fun counted(list: List<AppUsage>) = list.filter { it.pkg !in settings.excluded }

    /** Tujuh hari yang tampil di mode Minggu (berakhir di [day]). */
    val weekDays: List<String> get() = (6 downTo 0).map { Dates.addDays(day, -it.toLong()) }

    fun appsOn(d: String): List<AppUsage> = counted(appDays[d].orEmpty()).sortedByDescending { it.foregroundMs }

    fun screenOn(d: String): Long = appsOn(d).sumOf { it.foregroundMs }

    /** Aplikasi untuk rentang yang dipilih, terlama dulu. */
    val apps: List<AppUsage>
        get() = when (range) {
            WbRange.Day -> appsOn(day)
            WbRange.Week -> weekDays.flatMap { appsOn(it) }.groupBy { it.pkg }.map { (pkg, l) ->
                AppUsage(pkg, l.first().label, l.sumOf { it.foregroundMs }, l.sumOf { it.opens }, l.maxOf { it.longestMs })
            }.sortedByDescending { it.foregroundMs }
        }

    val screenMs: Long get() = if (range == WbRange.Day) screenOn(day) else weekDays.sumOf { screenOn(it) }

    val device: DeviceDay? get() = history.firstOrNull { it.day == day }

    private val previousDays: List<String> get() = (7 downTo 1).map { Dates.addDays(day, -it.toLong()) }.filter { d -> history.any { it.day == d } }

    /** Rata-rata waktu layar 7 hari sebelum [day] (null bila belum ada data). */
    val average7: Long? get() = previousDays.takeIf { it.isNotEmpty() }?.let { ds -> ds.sumOf { screenOn(it) } / ds.size }

    /** Rata-rata per hari dalam minggu yang tampil (hari yang ada datanya). */
    val weekAverage: Long? get() = weekDays.filter { d -> history.any { it.day == d } }.takeIf { it.isNotEmpty() }?.let { ds -> ds.sumOf { screenOn(it) } / ds.size }

    /** Jam "biasanya" pertama dipegang (median 7 hari sebelumnya, menit sejak tengah malam). */
    val usualPickupMinute: Long?
        get() = UsageMath.median(history.filter { it.day in previousDays }.mapNotNull { it.firstPickup }.map(UsageMath::minuteOfDay))

    /** Rata-rata jeda antar buka kunci pada [day] (ms). */
    val unlockInterval: Long?
        get() {
            val d = device ?: return null
            val first = d.firstPickup ?: return null
            if (d.unlocks < 2) return null
            val end = if (day == today) now else sessions.maxOfOrNull { it.end } ?: return null
            return ((end - first) / d.unlocks).takeIf { it > 0 }
        }

    val notifCount: Int get() = if (range == WbRange.Day) notifTotals[day] ?: 0 else weekDays.sumOf { notifTotals[it] ?: 0 }

    /** Peringkat aplikasi untuk pewarnaan (4 teratas berwarna, sisanya "lainnya"). */
    val colorRank: Map<String, Int> get() = apps.take(TOP_COLORED).mapIndexed { i, a -> a.pkg to i }.toMap()

    val hasData: Boolean get() = history.isNotEmpty()

    companion object {
        const val TOP_COLORED = 4
    }
}

/** Data detail satu aplikasi. */
data class AppDetail(
    val pkg: String,
    val label: String,
    val day: String,
    val history: List<AppDayPoint> = emptyList(),
    val sessions: List<UsageSession> = emptyList(),
    val notifications: Int = 0,
) {
    val today: AppDayPoint? get() = history.firstOrNull { it.day == day }
    val longestMs: Long get() = sessions.maxOfOrNull { it.duration } ?: 0
    val average7: Long?
        get() = (1..7).map { Dates.addDays(day, -it.toLong()) }.mapNotNull { d -> history.firstOrNull { it.day == d }?.foregroundMs }
            .takeIf { it.isNotEmpty() }?.average()?.toLong()
}

interface WellbeingActions {
    fun refresh() {}
    fun selectDay(day: String) {}
    fun setRange(range: WbRange) {}
    fun setDayStart(hour: Int) {}
    fun setSessionRetention(days: Int) {}
    fun setExcluded(pkg: String, excluded: Boolean) {}
    fun clearData() {}

    companion object {
        val None = object : WellbeingActions {}
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class WellbeingViewModel(
    private val usage: UsageRepository,
    private val notifs: NotificationLogRepository,
    private val prefs: DeviceModulePrefs,
    private val probe: AccessProbe,
    /** Salin data sistem sekarang (UsageCollector.sync). */
    private val sync: suspend () -> Unit = {},
) : ViewModel(), WellbeingActions {
    private val selected = MutableStateFlow<Pair<String?, WbRange>>(null to WbRange.Day)
    private val access = MutableStateFlow(probe.usage())
    private val syncing = MutableStateFlow(false)

    private val today: Flow<String> = prefs.usage.map { UsageMath.dayOf(System.currentTimeMillis(), it.dayStartHour) }

    private data class Data(
        val day: String,
        val range: WbRange,
        val history: List<DeviceDay>,
        val appDays: Map<String, List<AppUsage>>,
        val sessions: List<UsageSession>,
        val notifTimes: List<Long>,
        val notifTotals: Map<String, Int>,
        val days: List<String>,
    )

    private val data = combine(selected, today) { (sel, range), t -> Triple(sel ?: t, range, t) }.flatMapLatest { (day, range, _) ->
        val from = Dates.addDays(day, -13)
        val usageData = combine(usage.watchDevice(from, day), usage.watchAppDays(from, day), usage.watchSessions(day), usage.watchDays(), ::UsageData)
        combine(usageData, notifs.watchTimes(day), notifs.watchDailyTotals(from, day)) { u, times, totals ->
            Data(day, range, u.history, u.appDays, u.sessions, times, totals, u.days)
        }
    }

    private data class UsageData(val history: List<DeviceDay>, val appDays: Map<String, List<AppUsage>>, val sessions: List<UsageSession>, val days: List<String>)

    val state: StateFlow<WellbeingUiState> = combine(data, today, access, prefs.usage, syncing) { d, t, acc, settings, busy ->
        WellbeingUiState(
            today = t,
            day = d.day,
            range = d.range,
            access = acc,
            settings = settings,
            history = d.history,
            appDays = d.appDays,
            sessions = d.sessions,
            notifTimes = d.notifTimes,
            notifTotals = d.notifTotals,
            days = (listOf(t) + d.days).distinct().sortedDescending().take(14),
            syncing = busy,
            loaded = true,
            now = System.currentTimeMillis(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WellbeingUiState())

    init {
        refresh()
    }

    override fun refresh() {
        access.value = probe.usage()
        if (!access.value || syncing.value) return
        launchSafe {
            syncing.value = true
            try {
                sync()
            } finally {
                syncing.value = false
            }
        }
    }

    override fun selectDay(day: String) {
        selected.value = day to selected.value.second
    }

    override fun setRange(range: WbRange) {
        selected.value = selected.value.first to range
    }

    override fun setDayStart(hour: Int) {
        prefs.updateUsage { it.copy(dayStartHour = hour) }
        // Batas hari berubah → hitung ulang semua hari yang masih ada di sistem.
        launchSafe {
            usage.clearAll()
            sync()
        }
    }

    override fun setSessionRetention(days: Int) = prefs.updateUsage { it.copy(sessionRetentionDays = days) }

    override fun setExcluded(pkg: String, excluded: Boolean) =
        prefs.updateUsage { it.copy(excluded = if (excluded) it.excluded + pkg else it.excluded - pkg) }

    override fun clearData() {
        launchSafe { usage.clearAll() }
    }

    /** Detail satu aplikasi pada [day] (30 hari riwayat). */
    fun detail(pkg: String, label: String, day: String): Flow<AppDetail> = combine(
        usage.watchAppHistory(pkg, Dates.addDays(day, -29), day),
        usage.watchSessions(day, pkg),
        notifs.watchAppCounts(day, day).map { list -> list.firstOrNull { it.pkg == pkg }?.count ?: 0 },
    ) { history, sessions, n -> AppDetail(pkg, label, day, history, sessions, n) }
}
