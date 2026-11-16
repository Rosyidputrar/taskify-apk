package com.rosyidputrar.taskify

import com.rosyidputrar.taskify.ui.shell.EventQueue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import kotlin.test.assertEquals

class EventQueueTest {
    @Test fun eventSentBeforeCollectorIsDelivered() = runBlocking {
        // Cold start: notifikasi/shortcut dikirim di onCreate, shell baru mengumpulkan setelah gerbang terbuka.
        val queue = EventQueue<String>()
        queue.send("open-task")
        assertEquals("open-task", withTimeout(1_000) { queue.events.first() })
    }

    @Test fun eventsKeepOrderAndAreDeliveredOnce() = runBlocking {
        val queue = EventQueue<Int>()
        (1..3).forEach(queue::send)
        assertEquals(listOf(1, 2), withTimeout(1_000) { queue.events.take(2).toList() })
        // Kolektor baru (mis. shell disusun ulang) hanya menerima yang belum diambil.
        assertEquals(3, withTimeout(1_000) { queue.events.first() })
    }

    @Test fun overflowDropsOldest() = runBlocking {
        val queue = EventQueue<Int>(capacity = 2)
        (1..4).forEach(queue::send)
        assertEquals(listOf(3, 4), withTimeout(1_000) { queue.events.take(2).toList() })
    }
}
