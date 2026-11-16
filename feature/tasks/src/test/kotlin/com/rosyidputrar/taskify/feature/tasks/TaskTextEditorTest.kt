package com.rosyidputrar.taskify.feature.tasks

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import java.util.Collections
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskTextEditorTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val saved: MutableList<Map<String, Any?>> = Collections.synchronizedList(mutableListOf())
    private fun editor(debounce: Long = 100) = TaskTextEditor(scope, debounce) { saved += it }

    @After fun tearDown() = scope.cancel()

    private suspend fun eventually(block: () -> Boolean) = withTimeout(3_000) { while (!block()) delay(20) }

    @Test fun typingSavesAfterAPauseWithoutLeavingTheField() = runBlocking {
        val e = editor()
        e.editTitle("Bayar")
        e.editTitle("Bayar listrik")
        e.editNotes("Token PLN")
        eventually { saved.isNotEmpty() }
        delay(150)
        // Ketikan beruntun digabung jadi satu simpan dengan nilai terakhir.
        assertEquals(listOf(mapOf("title" to "Bayar listrik", "notes" to "Token PLN")), saved.toList())
    }

    @Test fun closingTheSheetSavesImmediately() = runBlocking {
        val e = editor(debounce = 60_000)
        e.editNotes("Draf bab 2")
        e.flush()
        eventually { saved.isNotEmpty() }
        assertEquals(mapOf<String, Any?>("notes" to "Draf bab 2"), saved.single())
    }

    @Test fun blankTitleIsNeverSavedAndBlankNotesClear() = runBlocking {
        val e = editor()
        e.editTitle("   ")
        e.flush()
        delay(250)
        assertTrue(saved.isEmpty())
        e.editNotes("  ", immediate = true)
        eventually { saved.isNotEmpty() }
        assertEquals(mapOf<String, Any?>("notes" to null), saved.single())
    }
}
