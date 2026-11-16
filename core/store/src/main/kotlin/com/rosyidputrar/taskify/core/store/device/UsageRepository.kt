package com.rosyidputrar.taskify.core.store.device

import com.powersync.PowerSyncDatabase
import com.rosyidputrar.taskify.core.domain.device.AppDay
import com.rosyidputrar.taskify.core.domain.device.DeviceDay
import com.rosyidputrar.taskify.core.domain.device.UsageSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Rekap aplikasi dengan label (untuk tampilan). */
data class AppUsage(val pkg: String, val label: String, val foregroundMs: Long, val opens: Int, val longestMs: Long)

/** Pemakaian satu aplikasi pada satu hari (grafik 7/30 hari). */
data class AppDayPoint(val day: String, val foregroundMs: Long, val opens: Int)

/** Data Kesehatan Digital lokal (`usage_sessions`, `usage_daily`, `device_daily`). */
class UsageRepository(private val db: PowerSyncDatabase) {

    /**
     * Ganti seluruh data satu hari dengan hasil hitung ulang (idempoten: kolektor menghitung ulang
     * hari yang masih ada di sistem setiap kali jalan).
     */
    suspend fun replaceDay(day: String, sessions: List<UsageSession>, apps: List<AppDay>, labels: Map<String, String>, device: DeviceDay) {
        db.writeTransaction { tx ->
            tx.execute("DELETE FROM usage_sessions WHERE day = ?", listOf(day))
            tx.execute("DELETE FROM usage_daily WHERE day = ?", listOf(day))
            tx.execute("DELETE FROM device_daily WHERE day = ?", listOf(day))
            sessions.forEachIndexed { i, s ->
                tx.execute(
                    "INSERT INTO usage_sessions (id, day, package, start_ms, end_ms) VALUES (?, ?, ?, ?, ?)",
                    listOf("$day|$i|${s.pkg}", day, s.pkg, s.start, s.end),
                )
            }
            apps.forEach { a ->
                tx.execute(
                    "INSERT INTO usage_daily (id, day, package, app_label, foreground_ms, opens, longest_ms) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    listOf("$day|${a.pkg}", day, a.pkg, labels[a.pkg] ?: a.pkg, a.foregroundMs, a.opens, a.longestMs),
                )
            }
            tx.execute(
                "INSERT INTO device_daily (id, day, screen_ms, unlocks, first_pickup_ms) VALUES (?, ?, ?, ?, ?)",
                listOf(day, day, device.screenMs, device.unlocks, device.firstPickup),
            )
        }
    }

    /** Rekap perangkat per hari (terlama dulu → terbaru). */
    fun watchDevice(from: String, to: String): Flow<List<DeviceDay>> = db.watch(
        "SELECT day, screen_ms, unlocks, first_pickup_ms FROM device_daily WHERE day BETWEEN ? AND ? ORDER BY day",
        listOf(from, to),
    ) { c -> DeviceDay(c.getString(0)!!, c.getLong(1) ?: 0, c.getLong(2)?.toInt() ?: 0, c.getLong(3)) }

    /** Hari yang punya data (pita hari), terbaru dulu. */
    fun watchDays(): Flow<List<String>> = db.watch("SELECT day FROM device_daily ORDER BY day DESC LIMIT 60") { it.getString(0)!! }

    /** Total per aplikasi dalam rentang (satu hari: from = to), terlama dulu. */
    fun watchApps(from: String, to: String): Flow<List<AppUsage>> = db.watch(
        """SELECT package, MAX(app_label), SUM(foreground_ms) AS ms, SUM(opens), MAX(longest_ms) FROM usage_daily
           WHERE day BETWEEN ? AND ? GROUP BY package ORDER BY ms DESC""",
        listOf(from, to),
    ) { c -> AppUsage(c.getString(0)!!, c.getString(1) ?: c.getString(0)!!, c.getLong(2) ?: 0, c.getLong(3)?.toInt() ?: 0, c.getLong(4) ?: 0) }

    /** Rekap per hari per aplikasi dalam rentang (grafik minggu bertumpuk). */
    fun watchAppDays(from: String, to: String): Flow<Map<String, List<AppUsage>>> = db.watch(
        "SELECT day, package, app_label, foreground_ms, opens, longest_ms FROM usage_daily WHERE day BETWEEN ? AND ?",
        listOf(from, to),
    ) { c -> c.getString(0)!! to AppUsage(c.getString(1)!!, c.getString(2) ?: c.getString(1)!!, c.getLong(3) ?: 0, c.getLong(4)?.toInt() ?: 0, c.getLong(5) ?: 0) }
        .map { rows -> rows.groupBy({ it.first }, { it.second }) }

    /** Satu aplikasi per hari dalam rentang. */
    fun watchAppHistory(pkg: String, from: String, to: String): Flow<List<AppDayPoint>> = db.watch(
        "SELECT day, foreground_ms, opens FROM usage_daily WHERE package = ? AND day BETWEEN ? AND ? ORDER BY day",
        listOf(pkg, from, to),
    ) { c -> AppDayPoint(c.getString(0)!!, c.getLong(1) ?: 0, c.getLong(2)?.toInt() ?: 0) }

    /** Sesi satu hari (semua aplikasi, atau satu [pkg]), urut waktu. */
    fun watchSessions(day: String, pkg: String? = null): Flow<List<UsageSession>> = db.watch(
        "SELECT package, start_ms, end_ms FROM usage_sessions WHERE day = ? AND (? IS NULL OR package = ?) ORDER BY start_ms",
        listOf(day, pkg, pkg),
    ) { c -> UsageSession(c.getString(0)!!, c.getLong(1) ?: 0, c.getLong(2) ?: 0) }

    suspend fun lastDay(): String? = db.getOptional("SELECT day FROM device_daily ORDER BY day DESC LIMIT 1") { it.getString(0)!! }

    /** Sesi rinci dibuang setelah [beforeDay]; rekap harian disimpan. */
    suspend fun pruneSessions(beforeDay: String) {
        db.execute("DELETE FROM usage_sessions WHERE day < ?", listOf(beforeDay))
    }

    suspend fun clearAll() {
        db.writeTransaction { tx ->
            tx.execute("DELETE FROM usage_sessions")
            tx.execute("DELETE FROM usage_daily")
            tx.execute("DELETE FROM device_daily")
        }
    }
}
