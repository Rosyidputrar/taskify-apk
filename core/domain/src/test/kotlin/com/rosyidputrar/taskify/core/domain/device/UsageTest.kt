package com.rosyidputrar.taskify.core.domain.device

import com.rosyidputrar.taskify.core.domain.device.UsageEvent.Kind
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UsageTest {
    private val day = "2026-11-16"
    private val w = UsageMath.window(day) // 00.00 WIB
    private val m = 60_000L
    private fun at(h: Int, min: Int = 0, s: Int = 0) = w.first + h * UsageMath.HOUR + min * m + s * 1000L
    private fun ev(pkg: String?, kind: Kind, t: Long) = UsageEvent(pkg, kind, t)

    @Test fun windowAndDayRespectDayStart() {
        assertEquals("2026-11-16", UsageMath.dayOf(at(1)))
        // Hari berganti jam 04.00: jam 01.00 masih termasuk hari sebelumnya.
        assertEquals("2026-11-15", UsageMath.dayOf(at(1), dayStartHour = 4))
        val late = UsageMath.window(day, 4)
        assertEquals(w.first + 4 * UsageMath.HOUR, late.first)
        assertEquals("06.42", formatClockTime(at(6, 42)))
    }

    @Test fun sessionsFromEvents() {
        val events = listOf(
            ev("com.whatsapp", Kind.Resumed, at(8)),
            ev("com.whatsapp", Kind.Paused, at(8, 10)),
            // Perpindahan activity dalam aplikasi yang sama → tetap satu sesi.
            ev("com.whatsapp", Kind.Resumed, at(8, 10, 1)),
            ev("com.zhiliaoapp.musically", Kind.Resumed, at(8, 20)), // aplikasi baru menutup yang lama
            ev(null, Kind.ScreenOff, at(9)),
            ev("launcher", Kind.Resumed, at(9, 5)),
            ev("com.android.chrome", Kind.Resumed, at(9, 6)),
        )
        val s = UsageMath.sessions(events, until = at(9, 16), ignore = setOf("launcher"))
        assertEquals(
            listOf(
                UsageSession("com.whatsapp", at(8), at(8, 20)),
                UsageSession("com.zhiliaoapp.musically", at(8, 20), at(9)),
                UsageSession("com.android.chrome", at(9, 6), at(9, 16)),
            ),
            s,
        )
        val apps = UsageMath.perApp(s)
        assertEquals("com.zhiliaoapp.musically", apps.first().pkg)
        assertEquals(40 * m, apps.first().foregroundMs)
    }

    @Test fun opensIgnoreQuickReturns() {
        val list = listOf(
            UsageSession("a", at(8), at(8, 1)),
            UsageSession("a", at(8, 1, 10), at(8, 2)), // kembali dalam 10 detik → masih "membuka"
            UsageSession("a", at(9), at(9, 5)),
        )
        assertEquals(2, UsageMath.opens(list))
    }

    @Test fun clipSplitsAcrossMidnight() {
        val s = UsageSession("a", w.first - 30 * m, w.first + 30 * m)
        assertEquals(listOf(UsageSession("a", w.first, w.first + 30 * m)), UsageMath.clip(listOf(s), w))
    }

    @Test fun unlocksAndFirstPickup() {
        val events = listOf(
            ev(null, Kind.ScreenOn, at(5, 50)),
            ev(null, Kind.Unlock, at(6, 42)),
            ev(null, Kind.Unlock, at(12)),
            ev(null, Kind.Unlock, w.first - m), // hari kemarin
        )
        assertEquals(2, UsageMath.unlocks(events, w))
        assertEquals(at(6, 42), UsageMath.firstPickup(events, w))
        // Tanpa kunci layar: layar menyala dipakai.
        val noLock = listOf(ev(null, Kind.ScreenOn, at(7)), ev(null, Kind.ScreenOn, at(8)))
        assertEquals(2, UsageMath.unlocks(noLock, w))
        assertEquals(at(7), UsageMath.firstPickup(noLock, w))
        assertNull(UsageMath.firstPickup(emptyList(), w))
    }

    @Test fun hourlySplitsSessions() {
        val slots = UsageMath.hourly(listOf(UsageSession("a", at(8, 40), at(10, 10))), w)
        assertEquals(20 * m, slots[8])
        assertEquals(60 * m, slots[9])
        assertEquals(10 * m, slots[10])
        assertEquals(90 * m, slots.sum())
    }

    @Test fun formatting() {
        assertEquals("4 j 12 m", formatDuration(252 * m))
        assertEquals("38 m", formatDuration(38 * m))
        assertEquals("2 j", formatDuration(120 * m))
        assertEquals("< 1 m", formatDuration(30_000))
        assertEquals("0 m", formatDuration(0))
        assertEquals(15L, UsageMath.median(listOf(30, 10, 15)))
        assertContentEquals(LongArray(24), UsageMath.hourly(emptyList(), w))
    }
}
