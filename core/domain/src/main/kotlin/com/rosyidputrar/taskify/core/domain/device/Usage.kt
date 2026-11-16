package com.rosyidputrar.taskify.core.domain.device

import com.rosyidputrar.taskify.core.domain.time.Dates
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Perhitungan Kesehatan Digital dari kejadian sistem (UsageStatsManager): sesi per aplikasi,
 * waktu layar, berapa kali dibuka, buka kunci, dan jam pertama HP dipegang. Murni → bisa diuji.
 */

/** Kejadian sistem yang relevan, sudah dipetakan dari UsageEvents.Event. */
data class UsageEvent(val pkg: String?, val kind: Kind, val at: Long) {
    enum class Kind { Resumed, Paused, Unlock, ScreenOn, ScreenOff }
}

/** Satu rentang aplikasi tampil di layar. */
data class UsageSession(val pkg: String, val start: Long, val end: Long) {
    val duration: Long get() = end - start
}

/** Rekap satu aplikasi dalam satu hari. */
data class AppDay(val pkg: String, val foregroundMs: Long, val opens: Int, val longestMs: Long)

/** Rekap perangkat dalam satu hari. */
data class DeviceDay(val day: String, val screenMs: Long, val unlocks: Int, val firstPickup: Long?)

object UsageMath {
    const val HOUR = 3_600_000L
    const val DAY = 24 * HOUR

    /** Jeda maksimal antar sesi aplikasi yang sama yang dianggap satu sesi (perpindahan activity). */
    const val MERGE_GAP_MS = 2_000L

    /** Jeda maksimal yang masih dihitung "masih membuka" (tidak menambah hitungan dibuka). */
    const val OPEN_GAP_MS = 15_000L

    /** Hari pemakaian untuk [millis] bila hari berganti pada jam [dayStartHour] (WIB). */
    fun dayOf(millis: Long, dayStartHour: Int = 0): String =
        Instant.ofEpochMilli(millis - dayStartHour * HOUR).atOffset(Dates.WIB).toLocalDate().toString()

    /** [start, end) satu hari pemakaian. */
    fun window(day: String, dayStartHour: Int = 0): LongRange {
        val start = LocalDate.parse(day).atTime(LocalTime.of(dayStartHour, 0)).toInstant(Dates.WIB).toEpochMilli()
        return start until start + DAY
    }

    /**
     * Susun sesi dari kejadian (urut waktu). Aplikasi baru tampil menutup yang lama; layar mati
     * menutup semuanya; sesi yang masih terbuka ditutup pada [until]. Sesi aplikasi yang sama dengan
     * jeda ≤ [MERGE_GAP_MS] digabung. Paket di [ignore] (launcher, system UI) tidak dihitung.
     */
    fun sessions(events: List<UsageEvent>, until: Long, ignore: Set<String> = emptySet()): List<UsageSession> {
        val out = mutableListOf<UsageSession>()
        var current: String? = null
        var since = 0L
        fun close(at: Long) {
            val pkg = current ?: return
            if (at > since && pkg !in ignore) out += UsageSession(pkg, since, at)
            current = null
        }
        for (e in events.sortedBy { it.at }) {
            when (e.kind) {
                UsageEvent.Kind.Resumed -> {
                    val pkg = e.pkg ?: continue
                    if (current == pkg) continue
                    close(e.at)
                    current = pkg
                    since = e.at
                }
                UsageEvent.Kind.Paused -> if (e.pkg == current) close(e.at)
                UsageEvent.Kind.ScreenOff -> close(e.at)
                else -> Unit
            }
        }
        close(until)
        return merge(out, MERGE_GAP_MS)
    }

    private fun merge(list: List<UsageSession>, gap: Long): List<UsageSession> {
        val out = mutableListOf<UsageSession>()
        for (s in list) {
            val last = out.lastOrNull()
            if (last != null && last.pkg == s.pkg && s.start - last.end <= gap) out[out.lastIndex] = last.copy(end = maxOf(last.end, s.end))
            else out += s
        }
        return out
    }

    /** Potong sesi pada batas [window] (sesi yang melewati tengah malam terbagi dua hari). */
    fun clip(sessions: List<UsageSession>, window: LongRange): List<UsageSession> = sessions.mapNotNull { s ->
        val start = maxOf(s.start, window.first)
        val end = minOf(s.end, window.last + 1)
        if (end > start) s.copy(start = start, end = end) else null
    }

    /** Rekap per aplikasi, terlama dulu. */
    fun perApp(sessions: List<UsageSession>): List<AppDay> = sessions.groupBy { it.pkg }.map { (pkg, list) ->
        AppDay(pkg, list.sumOf { it.duration }, opens(list), list.maxOf { it.duration })
    }.sortedByDescending { it.foregroundMs }

    /** "Dibuka" = sesi yang dimulai lebih dari [OPEN_GAP_MS] setelah sesi sebelumnya berakhir. */
    fun opens(list: List<UsageSession>): Int {
        var count = 0
        var lastEnd = Long.MIN_VALUE / 2
        for (s in list.sortedBy { it.start }) {
            if (s.start - lastEnd > OPEN_GAP_MS) count++
            lastEnd = maxOf(lastEnd, s.end)
        }
        return count
    }

    /**
     * Buka kunci dalam [window]. HP tanpa kunci layar tidak punya kejadian buka kunci → dihitung
     * dari layar menyala.
     */
    fun unlocks(events: List<UsageEvent>, window: LongRange): Int {
        val inside = events.filter { it.at in window }
        val unlock = inside.count { it.kind == UsageEvent.Kind.Unlock }
        return if (unlock > 0) unlock else inside.count { it.kind == UsageEvent.Kind.ScreenOn }
    }

    /** Jam pertama HP dipegang: buka kunci (atau layar menyala) pertama dalam [window]. */
    fun firstPickup(events: List<UsageEvent>, window: LongRange): Long? {
        val inside = events.filter { it.at in window }.sortedBy { it.at }
        return inside.firstOrNull { it.kind == UsageEvent.Kind.Unlock }?.at
            ?: inside.firstOrNull { it.kind == UsageEvent.Kind.ScreenOn }?.at
    }

    fun deviceDay(day: String, events: List<UsageEvent>, sessions: List<UsageSession>, window: LongRange) =
        DeviceDay(day, sessions.sumOf { it.duration }, unlocks(events, window), firstPickup(events, window))

    /** Milidetik pemakaian per jam (24 slot) dalam [window]. */
    fun hourly(sessions: List<UsageSession>, window: LongRange): LongArray {
        val slots = LongArray(24)
        for (s in clip(sessions, window)) {
            var t = s.start
            while (t < s.end) {
                val slot = ((t - window.first) / HOUR).toInt().coerceIn(0, 23)
                val slotEnd = window.first + (slot + 1) * HOUR
                val end = minOf(s.end, slotEnd)
                slots[slot] += end - t
                t = end
            }
        }
        return slots
    }

    /** Median (untuk "biasanya jam …"); null bila kosong. */
    fun median(values: List<Long>): Long? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    /** Menit sejak awal hari (WIB) — untuk membandingkan jam pertama dipegang antar hari. */
    fun minuteOfDay(millis: Long): Long {
        val t = Instant.ofEpochMilli(millis).atOffset(Dates.WIB).toLocalTime()
        return t.hour * 60L + t.minute
    }
}

/** "4 j 12 m" · "38 m" · "< 1 m". */
fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        ms <= 0L -> "0 m"
        minutes < 1 -> "< 1 m"
        minutes < 60 -> "$minutes m"
        minutes % 60 == 0L -> "${minutes / 60} j"
        else -> "${minutes / 60} j ${minutes % 60} m"
    }
}

/** "06.42" (WIB). */
fun formatClockTime(millis: Long): String {
    val t = Instant.ofEpochMilli(millis).atOffset(Dates.WIB).toLocalTime()
    return "%02d.%02d".format(t.hour, t.minute)
}
