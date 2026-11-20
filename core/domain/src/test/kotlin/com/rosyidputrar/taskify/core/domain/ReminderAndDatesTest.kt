package com.rosyidputrar.taskify.core.domain

import com.rosyidputrar.taskify.core.domain.tags.normalizeTagName
import com.rosyidputrar.taskify.core.domain.tasks.ReminderPreset
import com.rosyidputrar.taskify.core.domain.tasks.Task
import com.rosyidputrar.taskify.core.domain.tasks.TaskStatus
import com.rosyidputrar.taskify.core.domain.tasks.computeRemindAt
import com.rosyidputrar.taskify.core.domain.tasks.detectPreset
import com.rosyidputrar.taskify.core.domain.tasks.groupByDate
import com.rosyidputrar.taskify.core.domain.tasks.presetsFor
import com.rosyidputrar.taskify.core.domain.tasks.shiftRemindAt
import com.rosyidputrar.taskify.core.domain.time.Dates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderAndDatesTest {
    @Test fun parseOrNullReturnsNullForBrokenDates() {
        assertEquals(java.time.LocalDate.of(2026, 10, 7), Dates.parseOrNull("2026-10-07"))
        assertNull(Dates.parseOrNull("bukan-tanggal"))
        assertNull(Dates.parseOrNull("2026-13-45"))
        assertNull(Dates.parseOrNull(""))
    }

    @Test fun wibConversion() {
        assertEquals("2026-10-07T02:00:00.000Z", Dates.localDateTimeToIso("2026-10-07", "09:00"))
        assertEquals("2026-10-06T17:00:00.000Z", Dates.localDateTimeToIso("2026-10-07"))
        assertEquals("2026-10-07" to "09:00", Dates.isoToLocal("2026-10-07T02:00:00.000Z"))
        // Format PowerSync (spasi, tanpa milidetik) juga terbaca.
        assertEquals("2026-10-07" to "09:00", Dates.isoToLocal("2026-10-07 02:00:00Z"))
        assertEquals("2026-10-07" to "09:00", Dates.isoToLocal("2026-10-07 02:00:00+00"))
        assertEquals("2026-10-08", Dates.today(Dates.localDateTimeToMillis("2026-10-08", "00:30")))
    }

    @Test fun relativeLabels() {
        val t = "2026-10-07"
        assertEquals("Hari ini", Dates.relativeDayLabel(t, t))
        assertEquals("Besok", Dates.relativeDayLabel("2026-10-08", t))
        assertEquals("Kemarin", Dates.relativeDayLabel("2026-10-06", t))
        assertEquals("Jumat", Dates.relativeDayLabel("2026-10-09", t))
        assertEquals("2 Jan 2027", Dates.relativeDayLabel("2027-01-02", t))
    }

    @Test fun reminderPresets() {
        assertEquals("2026-10-07T01:50:00.000Z", computeRemindAt(ReminderPreset.Min10, "2026-10-07", "09:00:00"))
        assertEquals("2026-10-06T02:00:00.000Z", computeRemindAt(ReminderPreset.DayBefore, "2026-10-07", "09:00"))
        assertEquals("2026-10-07T02:00:00.000Z", computeRemindAt(ReminderPreset.Morning, "2026-10-07", null))
        assertEquals("2026-10-06T02:00:00.000Z", computeRemindAt(ReminderPreset.MorningBefore, "2026-10-07", null))
        assertNull(computeRemindAt(ReminderPreset.Min10, "2026-10-07", null))
        assertNull(computeRemindAt(ReminderPreset.AtTime, null, "09:00"))
        assertEquals(ReminderPreset.Min30, detectPreset("2026-10-07T01:30:00.000Z", "2026-10-07", "09:00:00"))
        assertEquals(ReminderPreset.Custom, detectPreset("2026-10-07T01:31:00.000Z", "2026-10-07", "09:00:00"))
        assertEquals(ReminderPreset.None, detectPreset(null, "2026-10-07", null))
        assertFalse(presetsFor(hasTime = false).contains(ReminderPreset.Min10))
        assertTrue(presetsFor(hasTime = true).contains(ReminderPreset.AtTime))
    }

    @Test fun shiftKeepsWibClock() {
        assertEquals("2026-11-07T02:00:00.000Z", shiftRemindAt("2026-10-07T02:00:00.000Z", 31))
        assertNull(shiftRemindAt(null, 3))
    }

    @Test fun tagNames() {
        assertEquals("kerja-penting", normalizeTagName("#Kerja Penting"))
        assertEquals("ide", normalizeTagName("  ##ide!! "))
        assertEquals(50, normalizeTagName("a".repeat(80)).length)
    }

    @Test fun grouping() {
        fun t(id: String, due: String?, status: TaskStatus = TaskStatus.Todo) = Task(id, "u", null, null, null, id, null, status, 0, due, null, null, null, null, 0.0, "", "", null)
        val groups = groupByDate(listOf(t("a", "2026-10-01"), t("b", "2026-10-07"), t("c", null), t("d", "2026-10-08"), t("e", "2026-10-01", TaskStatus.Done)), "2026-10-07")
        assertEquals(listOf("overdue", "2026-10-01", "2026-10-07", "2026-10-08", "none"), groups.map { it.key })
        assertEquals("Hari ini · Rabu, 7 Oktober", groups[2].label)
    }
}

class ScheduleChangeTest {
    private fun task(due: String?, time: String?, remind: String?, rule: String? = null) =
        com.rosyidputrar.taskify.core.domain.tasks.Task("t", "u", null, null, null, "x", null, TaskStatus.Todo, 0, due, time, remind, rule, null, 0.0, "", "", null)

    @Test fun presetFollowsNewDate() {
        val t = task("2026-10-07", "09:00:00", "2026-10-07T01:50:00.000Z")
        val patch = com.rosyidputrar.taskify.core.domain.tasks.scheduleChange(t, "2026-10-09", "09:00")
        assertEquals("2026-10-09T01:50:00.000Z", patch["remind_at"])
    }

    @Test fun relativePresetDroppedWhenTimeRemoved() {
        val t = task("2026-10-07", "09:00:00", "2026-10-07T01:50:00.000Z")
        val patch = com.rosyidputrar.taskify.core.domain.tasks.scheduleChange(t, "2026-10-07", null)
        assertTrue(patch.containsKey("remind_at"))
        assertNull(patch["remind_at"])
    }

    @Test fun customReminderUntouchedAndRuleRetargeted() {
        val t = task("2026-10-07", null, "2026-10-01T05:00:00.000Z", "FREQ=MONTHLY;INTERVAL=1;BYMONTHDAY=7")
        val patch = com.rosyidputrar.taskify.core.domain.tasks.scheduleChange(t, "2026-10-15", null)
        assertFalse(patch.containsKey("remind_at"))
        assertEquals("FREQ=MONTHLY;INTERVAL=1;BYMONTHDAY=15", patch["rrule"])
        assertEquals(mapOf("due_date" to null), com.rosyidputrar.taskify.core.domain.tasks.scheduleChange(t, null, null))
    }
}
