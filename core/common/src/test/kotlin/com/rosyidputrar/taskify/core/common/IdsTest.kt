package com.rosyidputrar.taskify.core.common

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IdsTest {
    @Test fun producesVersion7Variant2() {
        val uuid = UUID.fromString(Ids.newId())
        assertEquals(7, uuid.version())
        assertEquals(2, uuid.variant())
    }

    @Test fun idsAreMonotonicEvenWithinSameMillisecond() {
        val now = 1_763_000_000_000L
        val ids = List(5000) { Ids.newId(now) }
        assertEquals(ids.sorted(), ids)
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun embedsTimestamp() {
        val now = 1_763_000_000_123L
        val uuid = UUID.fromString(Ids.newId(now + 10_000))
        assertTrue((uuid.mostSignificantBits ushr 16) >= now)
        assertTrue(Ids.isUuid(uuid.toString()))
    }
}
