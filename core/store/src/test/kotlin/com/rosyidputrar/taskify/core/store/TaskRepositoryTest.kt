package com.rosyidputrar.taskify.core.store

import com.powersync.PowerSyncDatabase
import com.powersync.inMemory
import com.rosyidputrar.taskify.core.domain.tasks.RecurrenceFreq
import com.rosyidputrar.taskify.core.domain.tasks.RecurrenceState
import com.rosyidputrar.taskify.core.domain.tasks.TaskStatus
import com.rosyidputrar.taskify.core.domain.tasks.buildRule
import com.rosyidputrar.taskify.core.store.tasks.NewTask
import com.rosyidputrar.taskify.core.store.tasks.SmartView
import com.rosyidputrar.taskify.core.store.tasks.TagRepository
import com.rosyidputrar.taskify.core.store.tasks.TaskRepository
import com.rosyidputrar.taskify.core.store.tasks.TaskView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Repository diuji terhadap PowerSync sungguhan (SQLite + ekstensi PowerSync, di memori):
 * view, trigger antrean upload, dan SQL-nya persis seperti di HP.
 */
class TaskRepositoryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db: PowerSyncDatabase = PowerSyncDatabase.inMemory(TaskifySchema.schema, scope)
    private val users = UserIdProvider { "user-1" }
    private val tasks = TaskRepository(db, users)
    private val tags = TagRepository(db, users)
    private val today = "2026-10-07"

    @After fun tearDown() = runBlocking {
        db.close()
        scope.cancel()
    }

    @Test fun createdTaskAppearsInTodayAndQueuesUpload() = runBlocking {
        val id = tasks.create(NewTask("Bayar listrik", dueDate = today, dueTime = "09:00", priority = 3))
        val rows = tasks.watchView(TaskView.Smart(SmartView.Today), today).first()
        assertEquals(listOf(id), rows.map { it.id })
        assertEquals("09:00:00", rows[0].dueTime)
        assertEquals("user-1", rows[0].userId)
        val queued = db.get("SELECT count(*) FROM ps_crud") { it.getLong(0)!! }
        assertEquals(1L, queued)
        assertEquals(1, tasks.watchCounts(today).first().today)
    }

    @Test fun toggleAndUndo() = runBlocking {
        val id = tasks.create(NewTask("Tulis laporan", dueDate = today))
        val outcome = tasks.toggle(tasks.get(id)!!, today)
        assertEquals("Tugas selesai", outcome.message)
        assertEquals(TaskStatus.Done, tasks.get(id)!!.status)
        assertNotNull(tasks.get(id)!!.completedAt)
        outcome.undo!!.invoke()
        assertEquals(TaskStatus.Todo, tasks.get(id)!!.status)
        assertNull(tasks.get(id)!!.completedAt)
    }

    @Test fun completingRecurringRollsToNextOccurrence() = runBlocking {
        val rule = buildRule(RecurrenceState(RecurrenceFreq.Monthly), "2026-01-31")!!
        val id = tasks.create(NewTask("Bayar sewa", dueDate = "2026-01-31", dueTime = "09:00", remindAt = "2026-01-31T02:00:00.000Z", rrule = rule))
        tasks.create(NewTask("Transfer", parentId = id))
        val sub = tasks.watchSubtasks(id).first().single()
        tasks.toggle(sub, "2026-01-31")

        val outcome = tasks.toggle(tasks.get(id)!!, "2026-01-31")
        assertEquals("Selesai · berikutnya sab, 28 feb", outcome.message)
        val done = tasks.get(id)!!
        assertEquals(TaskStatus.Done, done.status)
        assertNull(done.rrule)
        assertEquals(id, done.seriesId)

        val next = db.getAll("SELECT id FROM tasks WHERE series_id = ? AND id <> ?", listOf(id, id)) { it.getString(0)!! }.single()
        val nextTask = tasks.get(next)!!
        assertEquals("2026-02-28", nextTask.dueDate)
        assertEquals(rule, nextTask.rrule)
        assertEquals("2026-02-28T02:00:00.000Z", nextTask.remindAt)
        val copied = tasks.watchSubtasks(next).first().single()
        assertEquals("Transfer", copied.title)
        assertEquals(TaskStatus.Todo, copied.status)

        outcome.undo!!.invoke()
        assertNull(tasks.get(next))
        assertEquals(rule, tasks.get(id)!!.rrule)
        assertEquals(TaskStatus.Todo, tasks.get(id)!!.status)
    }

    @Test fun clearingDateClearsTimeRuleAndReminder() = runBlocking {
        val id = tasks.create(NewTask("Rapat", dueDate = today, dueTime = "10:00", remindAt = "2026-10-07T02:50:00.000Z", rrule = "FREQ=DAILY;INTERVAL=1"))
        tasks.update(id, mapOf("due_date" to null))
        val t = tasks.get(id)!!
        assertNull(t.dueTime)
        assertNull(t.rrule)
        assertNull(t.remindAt)
        tasks.update(id, mapOf("title" to "x".repeat(400)))
        assertEquals(300, tasks.get(id)!!.title.length)
    }

    @Test fun subtaskProgressAndSoftDeleteWithRestore() = runBlocking {
        val id = tasks.create(NewTask("Pindahan"))
        repeat(3) { tasks.create(NewTask("Langkah $it", parentId = id)) }
        val first = tasks.watchSubtasks(id).first().first()
        tasks.toggle(first)
        val progress = tasks.watchSubtaskProgress().first()[id]!!
        assertEquals(1, progress.done)
        assertEquals(3, progress.total)

        val outcome = tasks.remove(tasks.get(id)!!)
        assertEquals(1, tasks.watchCounts(today).first().trash)
        assertTrue(tasks.watchSubtasks(id).first().isEmpty())
        outcome.undo!!.invoke()
        assertEquals(3, tasks.watchSubtasks(id).first().size)
    }

    @Test fun focusListsOverdueThenToday() = runBlocking {
        tasks.create(NewTask("Nanti", dueDate = "2026-10-09"))
        val late = tasks.create(NewTask("Terlambat", dueDate = "2026-10-01"))
        val now = tasks.create(NewTask("Sekarang", dueDate = today, priority = 3))
        assertEquals(listOf(late, now), tasks.watchFocus(today).first().map { it.id })
    }

    @Test fun tagsAreReusedCaseInsensitively() = runBlocking {
        val a = tasks.create(NewTask("A"))
        val b = tasks.create(NewTask("B"))
        tags.addByName("task", a, listOf("Kerja", "#urgent"))
        tags.addByName("task", b, listOf("kerja"))
        tags.addByName("task", b, listOf("KERJA"))
        val all = tags.watchAll().first()
        assertEquals(listOf("kerja", "urgent"), all.map { it.name })
        assertEquals(2, all.first().count)
        assertEquals(listOf("kerja"), tags.watchTagMap("task").first()[b]!!.map { it.name })
        tags.remove("task", a, all.first().id)
        assertEquals(listOf("urgent"), tags.watchEntityTags("task", a).first().map { it.name })
    }

    @Test fun listsAndReminders() = runBlocking {
        val list = tasks.createList("Rumah", "teal")
        val id = tasks.create(NewTask("Cat pagar", listId = list, dueDate = today, remindAt = "2026-10-07T02:00:00.000Z"))
        assertEquals(mapOf(list to 1), tasks.watchListCounts().first())
        assertEquals(listOf(id), tasks.watchView(TaskView.List(list), today).first().map { it.id })
        assertEquals(listOf(id), tasks.watchUpcomingReminders("2026-10-07T00:00:00.000Z").first().map { it.id })
        assertTrue(tasks.watchUpcomingReminders("2026-10-08T00:00:00.000Z").first().isEmpty())
        assertEquals(mapOf(id to "2026-10-07T02:00:00.000Z"), tasks.upcomingReminders("2026-10-07T00:00:00.000Z"))
        assertTrue(tasks.upcomingReminders("2026-10-08T00:00:00.000Z").isEmpty())
        tasks.removeList(list)
        assertNull(tasks.get(id)!!.listId)
        assertTrue(tasks.watchLists().first().isEmpty())
    }
}
