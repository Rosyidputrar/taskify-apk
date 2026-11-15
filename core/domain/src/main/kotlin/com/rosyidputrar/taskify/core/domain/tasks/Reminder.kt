package com.rosyidputrar.taskify.core.domain.tasks

import com.rosyidputrar.taskify.core.domain.time.Dates

/**
 * Pengingat = `tasks.remind_at` (timestamp absolut UTC) — port dari `features/tasks/reminder.ts`.
 * Di HP, alarm lokal berbunyi pada remind_at; di laptop server mengirim Web Push.
 */
enum class ReminderPreset(val label: String, val needsTime: Boolean = false, val allDayOnly: Boolean = false) {
    None("Tanpa pengingat"),
    AtTime("Saat jatuh tempo", needsTime = true),
    Min10("10 menit sebelum", needsTime = true),
    Min30("30 menit sebelum", needsTime = true),
    Hour1("1 jam sebelum", needsTime = true),
    DayBefore("1 hari sebelum", needsTime = true),
    Morning("Hari itu, $MORNING", allDayOnly = true),
    MorningBefore("Sehari sebelum, $MORNING", allDayOnly = true),
    Custom("Waktu lain…"),
}

const val MORNING = "09:00"

fun presetsFor(hasTime: Boolean): List<ReminderPreset> =
    ReminderPreset.entries.filter { if (hasTime) !it.allDayOnly else !it.needsTime }

private val MINUTES = mapOf(
    ReminderPreset.AtTime to 0,
    ReminderPreset.Min10 to 10,
    ReminderPreset.Min30 to 30,
    ReminderPreset.Hour1 to 60,
    ReminderPreset.DayBefore to 24 * 60,
)

/** Hitung remind_at (ISO UTC) dari preset. null = tidak ada / tidak valid. */
fun computeRemindAt(preset: ReminderPreset, dueDate: String?, dueTime: String?): String? {
    if (preset == ReminderPreset.None || preset == ReminderPreset.Custom || dueDate == null) return null
    if (preset == ReminderPreset.Morning) return Dates.localDateTimeToIso(dueDate, MORNING)
    if (preset == ReminderPreset.MorningBefore) return Dates.localDateTimeToIso(Dates.addDays(dueDate, -1), MORNING)
    val minutes = MINUTES[preset] ?: return null
    if (dueTime.isNullOrBlank()) return null
    val due = Dates.localDateTimeToMillis(dueDate, dueTime)
    return Dates.millisToIso(due - minutes * 60_000L)
}

/** Tebak preset dari remind_at tersimpan. */
fun detectPreset(remindAt: String?, dueDate: String?, dueTime: String?): ReminderPreset {
    if (remindAt == null) return ReminderPreset.None
    val stored = Dates.isoToLocal(remindAt) ?: return ReminderPreset.Custom
    for (p in presetsFor(!dueTime.isNullOrBlank())) {
        if (p == ReminderPreset.None || p == ReminderPreset.Custom) continue
        val iso = computeRemindAt(p, dueDate, dueTime) ?: continue
        if (Dates.isoToLocal(iso) == stored) return p
    }
    return ReminderPreset.Custom
}

/** Geser remind_at sebanyak N hari (kejadian berikutnya tugas berulang), jam WIB tetap. */
fun shiftRemindAt(remindAt: String?, days: Long): String? {
    if (remindAt == null) return null
    val (date, time) = Dates.isoToLocal(remindAt) ?: return null
    return Dates.localDateTimeToIso(Dates.addDays(date, days), time)
}
