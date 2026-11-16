package com.rosyidputrar.taskify.core.domain.focus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PomodoroTest {
    private val t0 = 1_700_000_000_000L

    @Test fun countsDownAndFinishes() {
        val run = FocusRun.start("t1", "Tulis laporan", t0)
        assertEquals(25 * MINUTE, run.remaining(t0))
        assertEquals(15 * MINUTE, run.remaining(t0 + 10 * MINUTE))
        assertFalse(run.isFinished(t0 + 24 * MINUTE))
        assertTrue(run.isFinished(t0 + 25 * MINUTE))
        assertEquals(0L, run.remaining(t0 + 30 * MINUTE))
        assertEquals(t0 + 25 * MINUTE, run.endsAt(t0 + 5 * MINUTE))
    }

    @Test fun pauseFreezesAndResumeShiftsEnd() {
        val paused = FocusRun.start(null, null, t0).pause(t0 + 10 * MINUTE)
        // Selama dijeda sisa waktu beku & tidak pernah "selesai".
        assertEquals(15 * MINUTE, paused.remaining(t0 + 40 * MINUTE))
        assertFalse(paused.isFinished(t0 + 60 * MINUTE))
        val resumed = paused.resume(t0 + 40 * MINUTE)
        assertNull(resumed.pausedAt)
        assertEquals(15 * MINUTE, resumed.remaining(t0 + 40 * MINUTE))
        assertEquals(t0 + 55 * MINUTE, resumed.endsAt(t0 + 40 * MINUTE))
        // Jeda dua kali tidak menggeser.
        assertEquals(paused, paused.pause(t0 + 50 * MINUTE))
    }

    @Test fun cycleShortThenLongBreak() {
        var run = FocusRun.start(null, null, t0, PomodoroConfig(roundsBeforeLong = 2))
        run = run.next(t0 + 25 * MINUTE)
        assertEquals(FocusPhase.ShortBreak, run.phase)
        assertFalse(run.isPaused, "istirahat langsung berjalan")
        run = run.next(t0 + 30 * MINUTE)
        assertEquals(FocusPhase.Focus, run.phase)
        assertEquals(2, run.round)
        assertTrue(run.isPaused, "fokus berikutnya menunggu Mulai")
        assertEquals(25 * MINUTE, run.remaining(t0 + 90 * MINUTE))
        run = run.resume(t0 + 90 * MINUTE).next(t0 + 115 * MINUTE)
        assertEquals(FocusPhase.LongBreak, run.phase)
        assertEquals(15 * MINUTE, run.durationMs)
    }

    @Test fun focusedTimeOnlyCountsFocusPhase() {
        val run = FocusRun.start(null, null, t0)
        assertEquals(12 * MINUTE, run.focusedMs(t0 + 12 * MINUTE))
        assertEquals(0L, run.next(t0 + 25 * MINUTE).focusedMs(t0 + 27 * MINUTE))
    }

    @Test fun encodeRoundTrip() {
        val run = FocusRun.start("t1", "Judul\u001Faneh", t0, PomodoroConfig(45, 10, 20, 3)).pause(t0 + 1234)
        val back = FocusRun.decode(run.encode())!!
        assertEquals(run.copy(taskTitle = "Judul aneh"), back)
        assertNull(FocusRun.decode("rusak"))
        assertNull(FocusRun.decode(null))
        assertEquals(PomodoroConfig(), PomodoroConfig.decode("0,5,15,4"))
    }

    @Test fun formats() {
        assertEquals("25:00", formatClock(25 * MINUTE))
        assertEquals("00:01", formatClock(1))
        assertEquals("1:05:00", formatClock(65 * MINUTE))
        assertEquals("40 mnt", formatMinutes(40))
        assertEquals("2 j", formatMinutes(120))
        assertEquals("1 j 25 mnt", formatMinutes(85))
    }
}
