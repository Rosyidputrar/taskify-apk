package com.rosyidputrar.taskify.core.domain.tasks

import com.rosyidputrar.taskify.core.domain.time.Dates

enum class TaskStatus(val db: String) {
    Todo("todo"), InProgress("in_progress"), Done("done"), Cancelled("cancelled");

    val isActive get() = this == Todo || this == InProgress

    companion object {
        fun from(value: String?): TaskStatus = entries.firstOrNull { it.db == value } ?: Todo
    }
}

/** Satu baris tabel `tasks` (kolom sama dengan webapp). */
data class Task(
    val id: String,
    val userId: String,
    val listId: String?,
    val parentId: String?,
    val seriesId: String?,
    val title: String,
    val notes: String?,
    val status: TaskStatus,
    val priority: Int,
    val dueDate: String?,
    val dueTime: String?,
    val remindAt: String?,
    val rrule: String?,
    val completedAt: String?,
    val position: Double,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String?,
) {
    val isDone get() = status == TaskStatus.Done
    val isRecurring get() = !rrule.isNullOrBlank()
}

data class Priority(val value: Int, val label: String)

val PRIORITIES = listOf(Priority(0, "Tanpa"), Priority(1, "Rendah"), Priority(2, "Sedang"), Priority(3, "Tinggi"))

fun priorityLabel(value: Int): String = PRIORITIES.firstOrNull { it.value == value }?.label ?: "Tanpa"

enum class GroupTone { Normal, Danger, Muted }

data class TaskGroup(val key: String, val label: String, val tone: GroupTone, val items: List<Task>)

/** Kelompokkan per tanggal: Terlambat → per hari → Tanpa tanggal (sama dengan webapp). */
fun groupByDate(items: List<Task>, today: String): List<TaskGroup> {
    val overdue = mutableListOf<Task>()
    val noDate = mutableListOf<Task>()
    val byDay = sortedMapOf<String, MutableList<Task>>()
    for (t in items) {
        when {
            t.dueDate == null -> noDate += t
            t.dueDate < today && t.status != TaskStatus.Done -> overdue += t
            else -> byDay.getOrPut(t.dueDate) { mutableListOf() } += t
        }
    }
    return buildList {
        if (overdue.isNotEmpty()) add(TaskGroup("overdue", "Terlambat", GroupTone.Danger, overdue))
        byDay.forEach { (day, list) -> add(TaskGroup(day, dayGroupLabel(day, today), GroupTone.Normal, list)) }
        if (noDate.isNotEmpty()) add(TaskGroup("none", "Tanpa tanggal", GroupTone.Muted, noDate))
    }
}

fun dayGroupLabel(day: String, today: String): String {
    val rel = Dates.relativeDayLabel(day, today)
    val full = Dates.format(day, "EEEE, d MMMM")
    return if (rel == "Hari ini" || rel == "Besok" || rel == "Kemarin") "$rel · $full" else full
}

/** Label ringkas jatuh tempo untuk baris tugas: "Besok 09:00", "Terlambat · Sen, 5 Okt". */
fun dueLabel(task: Task, today: String): String? {
    val date = task.dueDate ?: return null
    val time = Dates.shortTime(task.dueTime)
    val rel = Dates.relativeDayLabel(date, today)
    val base = if (time.isNotEmpty()) "$rel $time" else rel
    return if (date < today && task.status.isActive) "Terlambat · $base" else base
}

/**
 * Ubah tanggal/jam tugas: rule disesuaikan (retargetRule) dan pengingat preset dihitung ulang;
 * preset yang tidak cocok lagi (mis. "10 menit sebelum" tapi jam dihapus) menghapus pengingat.
 * Tanpa tanggal → repository menghapus jam, pengulangan, dan pengingat.
 */
fun scheduleChange(task: Task, dueDate: String?, dueTime: String?): Map<String, Any?> {
    if (dueDate == null) return mapOf("due_date" to null)
    val patch = linkedMapOf<String, Any?>("due_date" to dueDate, "due_time" to dueTime)
    if (task.isRecurring) patch["rrule"] = retargetRule(task.rrule, dueDate)
    val preset = detectPreset(task.remindAt, task.dueDate, task.dueTime)
    if (preset != ReminderPreset.None && preset != ReminderPreset.Custom) {
        patch["remind_at"] = computeRemindAt(preset, dueDate, dueTime)
    }
    return patch
}
