package com.rosyidputrar.taskify.core.domain

import com.rosyidputrar.taskify.core.domain.editor.Autosaver
import com.rosyidputrar.taskify.core.domain.editor.SaveState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AutosaverTest {
    @Test fun debouncesAndMergesPatches() = runTest {
        val saved = mutableListOf<Map<String, Any?>>()
        val saver = Autosaver(backgroundScope) { saved += it; true }
        saver.queue(mapOf("content" to "a"))
        advanceTimeBy(300)
        saver.queue(mapOf("content" to "ab", "title" to "T"))
        advanceTimeBy(600)
        runCurrent()
        assertEquals(emptyList(), saved)
        assertEquals(SaveState.Pending, saver.state.value)
        advanceTimeBy(200)
        runCurrent()
        assertEquals(listOf(mapOf<String, Any?>("content" to "ab", "title" to "T")), saved)
        assertEquals(SaveState.Saved, saver.state.value)
    }

    @Test fun immediateAndFlush() = runTest {
        val saved = mutableListOf<Map<String, Any?>>()
        val saver = Autosaver(backgroundScope) { saved += it; true }
        saver.queue(mapOf("mood" to 4), immediate = true)
        runCurrent()
        assertEquals(1, saved.size)
        saver.queue(mapOf("content" to "x"))
        saver.flush()
        assertEquals(mapOf<String, Any?>("content" to "x"), saved.last())
    }

    @Test fun failureRequeuesWithoutLosingNewerChanges() = runTest {
        var fail = true
        val saved = mutableListOf<Map<String, Any?>>()
        val saver = Autosaver(backgroundScope) { if (fail) error("offline") else { saved += it; true } }
        saver.queue(mapOf("content" to "lama", "title" to "T"), immediate = true)
        runCurrent()
        assertEquals(SaveState.Error, saver.state.value)
        fail = false
        saver.queue(mapOf("content" to "baru"))
        saver.flush()
        assertEquals(mapOf<String, Any?>("content" to "baru", "title" to "T"), saved.single())
        assertEquals(SaveState.Saved, saver.state.value)
    }

    @Test fun emptyDraftStaysIdle() = runTest {
        val saver = Autosaver(backgroundScope) { false }
        saver.queue(mapOf("content" to ""), immediate = true)
        runCurrent()
        assertEquals(SaveState.Idle, saver.state.value)
    }

    @Test fun cancelledFlushWhileWaitingForMutexDoesNotLosePendingPatch() = runTest {
        val gate = CompletableDeferred<Unit>()
        val saved = mutableListOf<Map<String, Any?>>()
        val saver = Autosaver(backgroundScope) { patch ->
            if (patch["step"] == 1) gate.await()
            saved += patch
            true
        }
        val first = launch {
            saver.queue(mapOf("step" to 1))
            saver.flush()
        }
        runCurrent()
        saver.queue(mapOf("step" to 2, "content" to "penting"))
        val second = launch { saver.flush() }
        runCurrent()
        // Batalkan flush kedua saat masih menunggu mutex dari flush pertama.
        second.cancel()
        gate.complete(Unit)
        first.join()
        // Patch kedua tidak boleh hilang; flush berikutnya masih menyimpannya.
        saver.flush()
        assertEquals(listOf(mapOf<String, Any?>("step" to 1), mapOf<String, Any?>("step" to 2, "content" to "penting")), saved)
    }
}
