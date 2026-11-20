package com.rosyidputrar.taskify.feature.tasks

import com.rosyidputrar.taskify.core.store.tasks.SmartView
import com.rosyidputrar.taskify.core.store.tasks.TaskView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TaskViewFromTest {
    @Test fun parsesSmartViewsAndLists() {
        assertEquals(TaskView.Smart(SmartView.Overdue), taskViewFrom("Overdue"))
        assertEquals(TaskView.Smart(SmartView.Today), taskViewFrom("Today"))
        assertEquals(TaskView.List("abc"), taskViewFrom("list:abc"))
    }

    @Test fun ignoresJunk() {
        assertNull(taskViewFrom(null))
        assertNull(taskViewFrom(""))
        assertNull(taskViewFrom("list:"))
        assertNull(taskViewFrom("Kemarin"))
    }
}
