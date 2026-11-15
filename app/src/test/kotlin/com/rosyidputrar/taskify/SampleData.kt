package com.rosyidputrar.taskify

import com.rosyidputrar.taskify.core.domain.tasks.Task
import com.rosyidputrar.taskify.core.domain.tasks.TaskStatus
import com.rosyidputrar.taskify.core.domain.tasks.groupByDate
import com.rosyidputrar.taskify.core.store.home.ActivityItem
import com.rosyidputrar.taskify.core.store.home.ActivityKind
import com.rosyidputrar.taskify.core.store.home.TodaySummary
import com.rosyidputrar.taskify.core.store.tasks.Progress
import com.rosyidputrar.taskify.core.store.tasks.TagRef
import com.rosyidputrar.taskify.core.store.tasks.TaskList
import com.rosyidputrar.taskify.core.store.tasks.ViewCounts
import com.rosyidputrar.taskify.feature.tasks.TasksActions
import com.rosyidputrar.taskify.feature.tasks.TasksUiState
import com.rosyidputrar.taskify.ui.home.HomeUiState

/** Data contoh untuk tangkapan layar (tanpa database). Tanggal tetap supaya gambar stabil. */
object SampleData {
    const val TODAY = "2026-10-07"

    fun task(
        id: String,
        title: String,
        due: String? = null,
        time: String? = null,
        priority: Int = 0,
        status: TaskStatus = TaskStatus.Todo,
        rrule: String? = null,
        remind: String? = null,
        list: String? = null,
    ) = Task(id, "u", list, null, null, title, null, status, priority, due, time, remind, rrule, null, 0.0, "2026-10-01T00:00:00.000Z", "", null)

    val lists = listOf(TaskList("l1", "Kantor", "indigo", 0.0), TaskList("l2", "Rumah", "teal", 1.0))

    val tasks = listOf(
        task("a", "Kirim laporan kuartal ke Bu Rina", "2026-10-05", priority = 3, list = "l1"),
        task("b", "Bayar listrik & air", TODAY, "09:00:00", priority = 2, remind = "2026-10-07T01:50:00.000Z", rrule = "FREQ=MONTHLY;INTERVAL=1;BYMONTHDAY=7"),
        task("c", "Rapat sprint", TODAY, "14:30:00", priority = 1, list = "l1"),
        task("d", "Siram tanaman", TODAY, status = TaskStatus.Done, list = "l2"),
        task("e", "Servis motor", "2026-10-09", list = "l2"),
        task("f", "Baca buku Atomic Habits"),
    )

    val progress = mapOf("a" to Progress(2, 5), "c" to Progress(1, 3))
    val tags = mapOf("a" to listOf(TagRef("t1", "kerja", "indigo")), "e" to listOf(TagRef("t2", "rumah", "teal"), TagRef("t3", "rutin", "amber")))

    fun tasksState() = TasksUiState(
        today = TODAY,
        groups = groupByDate(tasks, TODAY),
        counts = ViewCounts(today = 2, upcoming = 1, overdue = 1, all = 5),
        lists = lists,
        progress = progress,
        tags = tags,
        loaded = true,
    )

    fun homeState() = HomeUiState(
        today = TODAY,
        focus = listOf(tasks[0], tasks[1], tasks[2]),
        progress = progress,
        summary = TodaySummary(tasksToday = 3, tasksTodayDone = 1, tasksOverdue = 1, expenseToday = 47_500, expenseMonth = 1_250_000, journalToday = 1, moodToday = 4.0),
        streak = 6,
        activity = listOf(
            ActivityItem(ActivityKind.Journal, "j", "Pagi yang tenang, sempat jalan kaki", "06:40", mood = 4),
            ActivityItem(ActivityKind.Task, "b", "Bayar listrik & air", "09:00"),
            ActivityItem(ActivityKind.Transaction, "t", "Makan siang", "12:15", amount = 35_000),
            ActivityItem(ActivityKind.Task, "c", "Rapat sprint", "14:30"),
            ActivityItem(ActivityKind.Task, "d", "Siram tanaman", "", done = true),
        ),
        loaded = true,
    )

    object NoActions : TasksActions {
        override fun quickAdd(text: String) = Unit
        override fun setSegment(segment: com.rosyidputrar.taskify.feature.tasks.Segment) = Unit
        override fun setView(view: com.rosyidputrar.taskify.core.store.tasks.TaskView) = Unit
        override fun toggleHighPriority() = Unit
        override fun toggle(task: Task) = Unit
        override fun remove(task: Task) = Unit
        override fun createList(name: String, color: String) = Unit
        override fun renameList(id: String, name: String, color: String) = Unit
        override fun removeList(id: String) = Unit
    }
}
