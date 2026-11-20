package com.rosyidputrar.taskify

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.domain.tasks.Task
import com.rosyidputrar.taskify.core.domain.tasks.TaskStatus
import com.rosyidputrar.taskify.widget.TodayWidgetState
import com.rosyidputrar.taskify.widget.resolveWidgetState
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A7 — galat muat widget dibedakan dari kosong: gagal muat → [TodayWidgetState.Error]
 * (bukan "Semua beres"), kosong → [TodayWidgetState.Ready] dengan daftar kosong.
 */
@RunWith(AndroidJUnit4::class)
class TodayWidgetStateTest {
    private fun task(id: String) = Task(
        id = id, userId = "u", listId = null, parentId = null, seriesId = null,
        title = "Tugas $id", notes = null, status = TaskStatus.Todo, priority = 0,
        dueDate = null, dueTime = null, remindAt = null, rrule = null, completedAt = null,
        position = 0.0, createdAt = "", updatedAt = "", deletedAt = null,
    )

    @Test fun loadErrorIsErrorNotEmpty() {
        val state = resolveWidgetState(Result.failure(IllegalStateException("db rusak")), "2026-11-20", locked = false)
        assertEquals(TodayWidgetState.Error, state)
    }

    @Test fun loadErrorBeatsLockedCount() {
        // Galat didahulukan: jumlahnya tidak diketahui, jadi jangan tampilkan hitungan PIN.
        val state = resolveWidgetState(Result.failure(IllegalStateException("db rusak")), "2026-11-20", locked = true)
        assertEquals(TodayWidgetState.Error, state)
    }

    @Test fun emptyListIsReadyEmpty() {
        val state = resolveWidgetState(Result.success(emptyList()), "2026-11-20", locked = false)
        assertTrue(state is TodayWidgetState.Ready)
        assertTrue((state as TodayWidgetState.Ready).tasks.isEmpty())
    }

    @Test fun loadedTasksAreReady() {
        val tasks = listOf(task("a"), task("b"))
        val state = resolveWidgetState(Result.success(tasks), "2026-11-20", locked = false)
        assertEquals(TodayWidgetState.Ready(tasks, "2026-11-20"), state)
    }

    @Test fun lockedShowsCountOnly() {
        val state = resolveWidgetState(Result.success(listOf(task("a"), task("b"))), "2026-11-20", locked = true)
        assertEquals(TodayWidgetState.Locked(2), state)
    }
}
