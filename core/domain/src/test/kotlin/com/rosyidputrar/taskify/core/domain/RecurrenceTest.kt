package com.rosyidputrar.taskify.core.domain

import com.rosyidputrar.taskify.core.domain.tasks.RecurrenceFreq
import com.rosyidputrar.taskify.core.domain.tasks.RecurrenceState
import com.rosyidputrar.taskify.core.domain.tasks.buildRule
import com.rosyidputrar.taskify.core.domain.tasks.describeRule
import com.rosyidputrar.taskify.core.domain.tasks.nextDueAfterCompletion
import com.rosyidputrar.taskify.core.domain.tasks.nextOccurrence
import com.rosyidputrar.taskify.core.domain.tasks.occurrencesBetween
import com.rosyidputrar.taskify.core.domain.tasks.parseRule
import com.rosyidputrar.taskify.core.domain.tasks.retargetRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Port dari recurrence.test.ts webapp + bentuk string kanonik rrule.js. */
class RecurrenceTest {
    private fun monthly(interval: Int = 1) = RecurrenceState(RecurrenceFreq.Monthly, interval)

    @Test fun day31FallsOnLastDayOfShortMonths() {
        val rule = buildRule(monthly(), "2026-01-31")!!
        assertEquals("FREQ=MONTHLY;INTERVAL=1;BYMONTHDAY=28,29,30,31;BYSETPOS=-1", rule)
        assertEquals(
            listOf("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30", "2026-05-31", "2026-06-30", "2026-07-31", "2026-08-31", "2026-09-30", "2026-10-31", "2026-11-30", "2026-12-31"),
            occurrencesBetween(rule, "2026-01-31", "2026-01-01", "2026-12-31"),
        )
    }

    @Test fun day30AndDay29() {
        val r30 = buildRule(monthly(), "2026-01-30")!!
        assertEquals(listOf("2026-01-30", "2026-02-28", "2026-03-30", "2026-04-30"), occurrencesBetween(r30, "2026-01-30", "2026-01-01", "2026-04-30"))
        assertEquals(listOf("2028-02-29"), occurrencesBetween(r30, "2028-01-30", "2028-02-01", "2028-02-29"))
        val r29 = buildRule(monthly(), "2026-01-29")!!
        assertEquals(listOf("2026-02-28", "2026-03-29"), occurrencesBetween(r29, "2026-01-29", "2026-02-01", "2026-03-31"))
        assertEquals(listOf("2028-02-29"), occurrencesBetween(r29, "2028-01-29", "2028-02-01", "2028-02-29"))
    }

    @Test fun days1To28Unchanged() {
        val rule = buildRule(monthly(), "2026-01-15")!!
        assertEquals("FREQ=MONTHLY;INTERVAL=1;BYMONTHDAY=15", rule)
        assertEquals(listOf("2026-01-15", "2026-02-15", "2026-03-15"), occurrencesBetween(rule, "2026-01-15", "2026-01-01", "2026-03-31"))
    }

    @Test fun twoMonthIntervalRespectsMonthEnd() {
        val rule = buildRule(monthly(2), "2026-01-31")!!
        assertEquals(
            listOf("2026-01-31", "2026-03-31", "2026-05-31", "2026-07-31", "2026-09-30", "2026-11-30"),
            occurrencesBetween(rule, "2026-01-31", "2026-01-01", "2026-12-31"),
        )
    }

    @Test fun legacyRuleIsNormalisedOnRead() {
        val legacy = "FREQ=MONTHLY;INTERVAL=1;BYMONTHDAY=31"
        assertEquals("2026-02-28", nextOccurrence(legacy, "2026-01-31", "2026-01-31"))
        assertEquals(listOf("2026-04-30"), occurrencesBetween(legacy, "2026-01-31", "2026-04-01", "2026-04-30"))
    }

    @Test fun untilIsRespected() {
        val rule = buildRule(monthly().copy(until = "2026-03-15"), "2026-01-31")!!
        assertEquals("FREQ=MONTHLY;INTERVAL=1;BYMONTHDAY=28,29,30,31;UNTIL=20260315T235959Z;BYSETPOS=-1", rule)
        assertEquals(listOf("2026-01-31", "2026-02-28"), occurrencesBetween(rule, "2026-01-31", "2026-01-01", "2026-12-31"))
    }

    @Test fun rollingModelAfterFeb28GoesToMar31() {
        val rule = buildRule(monthly(), "2026-01-31")!!
        assertEquals("2026-02-28", nextDueAfterCompletion(rule, "2026-01-31", "2026-01-31"))
        assertEquals("2026-03-31", nextDueAfterCompletion(rule, "2026-02-28", "2026-02-28"))
    }

    @Test fun overdueJumpsToToday() {
        val daily = buildRule(RecurrenceState(RecurrenceFreq.Daily), "2026-01-01")!!
        assertEquals("2026-03-10", nextDueAfterCompletion(daily, "2026-01-01", "2026-03-10"))
    }

    @Test fun parseAndDescribe() {
        val rule = buildRule(monthly(3), "2026-01-31")!!
        assertEquals(RecurrenceFreq.Monthly, parseRule(rule).freq)
        assertEquals(3, parseRule(rule).interval)
        assertTrue(describeRule(buildRule(monthly(), "2026-01-31"), "2026-01-31").contains("tgl 31"))
        assertTrue(describeRule("FREQ=MONTHLY;INTERVAL=1;BYMONTHDAY=31").contains("tgl 31"))
        assertEquals("Setiap bulan tgl 15", describeRule(buildRule(monthly(), "2026-01-15"), "2026-01-15"))
    }

    @Test fun yearlyFeb29() {
        val rule = buildRule(RecurrenceState(RecurrenceFreq.Yearly), "2028-02-29")!!
        assertEquals(
            listOf("2028-02-29", "2029-02-28", "2030-02-28", "2031-02-28", "2032-02-29"),
            occurrencesBetween(rule, "2028-02-29", "2028-01-01", "2032-12-31"),
        )
    }

    private val rule31 = buildRule(monthly(), "2026-01-31")!!

    @Test fun parseCarriesIntendedMonthDay() {
        assertEquals(31, parseRule(rule31).monthDay)
        assertEquals(15, parseRule(buildRule(monthly(), "2026-01-15")).monthDay)
    }

    @Test fun editingTimeOnFeb28KeepsRule() {
        assertEquals(rule31, retargetRule(rule31, "2026-02-28"))
        assertEquals("2026-03-31", nextDueAfterCompletion(retargetRule(rule31, "2026-02-28")!!, "2026-02-28", "2026-02-28"))
    }

    @Test fun editingIntervalKeeps31() {
        val edited = buildRule(parseRule(rule31).copy(interval = 2), "2026-02-28")!!
        assertEquals(
            listOf("2026-02-28", "2026-04-30", "2026-06-30", "2026-08-31", "2026-10-31", "2026-12-31"),
            occurrencesBetween(edited, "2026-02-28", "2026-02-01", "2026-12-31"),
        )
    }

    @Test fun movingDateRebuilds() {
        assertEquals("FREQ=MONTHLY;INTERVAL=1;BYMONTHDAY=15", retargetRule(rule31, "2026-02-15"))
        assertEquals(rule31, retargetRule(rule31, "2026-04-30"))
    }

    @Test fun nonMonthlyRules() {
        val daily = buildRule(RecurrenceState(RecurrenceFreq.Daily, 3), "2026-02-01")!!
        assertEquals(daily, retargetRule(daily, "2026-02-10"))
        assertNull(retargetRule(null, "2026-02-10"))
        val yearly = buildRule(RecurrenceState(RecurrenceFreq.Yearly), "2026-10-07")!!
        assertEquals("FREQ=YEARLY;INTERVAL=1;BYMONTH=10;BYMONTHDAY=8", retargetRule(yearly, "2026-10-08"))
    }

    @Test fun weeklyAndWeekdays() {
        val weekly = buildRule(RecurrenceState(RecurrenceFreq.Weekly, byDay = listOf(3, 0)), "2026-10-05")!!
        assertEquals("FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,TH", weekly)
        assertEquals(listOf("2026-10-05", "2026-10-08", "2026-10-12", "2026-10-15"), occurrencesBetween(weekly, "2026-10-05", "2026-10-01", "2026-10-16"))
        val biweekly = buildRule(RecurrenceState(RecurrenceFreq.Weekly, 2, listOf(0)), "2026-10-05")!!
        assertEquals(listOf("2026-10-05", "2026-10-19", "2026-11-02"), occurrencesBetween(biweekly, "2026-10-05", "2026-10-01", "2026-11-05"))
        val weekdays = buildRule(RecurrenceState(RecurrenceFreq.Weekdays), "2026-10-09")!!
        assertEquals("FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,TU,WE,TH,FR", weekdays)
        assertEquals("2026-10-12", nextOccurrence(weekdays, "2026-10-09", "2026-10-09"))
        assertEquals(RecurrenceFreq.Weekdays, parseRule(weekdays).freq)
        assertEquals("Setiap minggu: Sen, Kam", describeRule(weekly))
    }

    @Test fun unknownRulesAreIgnoredSafely() {
        assertEquals(RecurrenceFreq.None, parseRule("FREQ=HOURLY").freq)
        assertEquals(emptyList(), occurrencesBetween("FREQ=MONTHLY;BYDAY=1MO", "2026-01-01", "2026-01-01", "2026-12-31"))
        assertNull(nextOccurrence("bukan rule", "2026-01-01", "2026-01-01"))
    }

    @Test fun duplicateKeysAndBrokenDatesDoNotCrash() {
        // Kunci duplikat (dulu: IllegalArgumentException dari associate) — yang terakhir menang.
        assertEquals(RecurrenceFreq.Weekly, parseRule("FREQ=DAILY;FREQ=WEEKLY;BYDAY=MO").freq)
        assertEquals(RecurrenceFreq.Daily, parseRule("FREQ=WEEKLY;FREQ=DAILY").freq)
        assertEquals(listOf(1), parseRule("FREQ=WEEKLY;BYDAY=MO;BYDAY=TU").byDay) // TU = indeks 1
        // UNTIL dengan bulan/hari tidak valid diabaikan, bukan crash saat deskripsi dibaca.
        assertEquals("Setiap hari", describeRule("FREQ=DAILY;INTERVAL=1;UNTIL=20261345T000000Z"))
        // Tanggal rusak di jalur baca data eksternal → null / kosong, bukan DateTimeParseException.
        assertNull(nextOccurrence("FREQ=DAILY;INTERVAL=1", "bukan-tanggal", "2026-10-07"))
        assertNull(nextOccurrence("FREQ=DAILY;INTERVAL=1", "2026-10-07", "bukan-tanggal"))
        assertEquals(emptyList(), occurrencesBetween("FREQ=DAILY;INTERVAL=1", "bukan-tanggal", "2026-10-07", "2026-10-14"))
        assertEquals(emptyList(), occurrencesBetween("FREQ=DAILY;INTERVAL=1", "2026-10-07", "bukan-tanggal", "2026-10-14"))
        assertNull(nextDueAfterCompletion("FREQ=DAILY;INTERVAL=1", "bukan-tanggal", "2026-10-07"))
        assertNull(buildRule(monthly(), "bukan-tanggal"))
        assertEquals("Setiap hari", describeRule("FREQ=DAILY;INTERVAL=1", "bukan-tanggal"))
        assertEquals("Setiap tahun", describeRule("FREQ=YEARLY;INTERVAL=1", "bukan-tanggal"))
    }
}
