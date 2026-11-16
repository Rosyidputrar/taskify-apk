package com.rosyidputrar.taskify.core.domain

import com.rosyidputrar.taskify.core.domain.sync.ROW_MISSING_CODE
import com.rosyidputrar.taskify.core.domain.sync.SCHEMA_GRACE_MS
import com.rosyidputrar.taskify.core.domain.sync.SCHEMA_MIN_ATTEMPTS
import com.rosyidputrar.taskify.core.domain.sync.UploadFailureTracker
import com.rosyidputrar.taskify.core.domain.sync.UploadVerdict.Fatal
import com.rosyidputrar.taskify.core.domain.sync.UploadVerdict.Retry
import com.rosyidputrar.taskify.core.domain.sync.isFatal
import com.rosyidputrar.taskify.core.domain.sync.isSchemaError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UploadPolicyTest {
    private var clock = 0L
    private val tracker = UploadFailureTracker { clock }

    @Test fun constraintDataRlsAndMissingRowAreFatal() {
        listOf("23514", "23503", "23505", "22P02", "42501", ROW_MISSING_CODE, "23V01", "23V03", "23V04").forEach {
            assertTrue(isFatal(it), it)
        }
    }

    @Test fun networkJwtAndSchemaAreNotImmediatelyFatal() {
        listOf(null, "PGRST301", "PGRST204", "500", "08006").forEach { assertFalse(isFatal(it), it.toString()) }
    }

    @Test fun schemaCodesArePgrst1xxAnd2xxOnly() {
        assertTrue(isSchemaError("PGRST204"))
        assertTrue(isSchemaError("PGRST205"))
        assertTrue(isSchemaError("PGRST100"))
        assertFalse(isSchemaError("PGRST002"))
        assertFalse(isSchemaError("PGRST301"))
    }

    @Test fun fatalIsImmediate() = assertEquals(Fatal, tracker.verdict("1", "23514"))

    @Test fun transientAlwaysRetries() = repeat(50) { assertEquals(Retry, tracker.verdict("1", null)) }

    @Test fun schemaRetriesDuringGraceThenFatal() {
        repeat(SCHEMA_MIN_ATTEMPTS) { assertEquals(Retry, tracker.verdict("1", "PGRST204")) }
        clock += SCHEMA_GRACE_MS - 1
        assertEquals(Retry, tracker.verdict("1", "PGRST204"))
        clock += 1
        assertEquals(Fatal, tracker.verdict("1", "PGRST204"))
    }

    @Test fun manyQuickAttemptsDoNotShortenGrace() {
        repeat(100) { assertEquals(Retry, tracker.verdict("1", "PGRST204")) }
    }

    @Test fun anotherTransactionStartsFromZero() {
        repeat(SCHEMA_MIN_ATTEMPTS) { tracker.verdict("1", "PGRST204") }
        clock += SCHEMA_GRACE_MS
        assertEquals(Retry, tracker.verdict("2", "PGRST204"))
    }

    @Test fun networkDropDuringGraceDoesNotReset() {
        repeat(SCHEMA_MIN_ATTEMPTS) { tracker.verdict("1", "PGRST204") }
        clock += SCHEMA_GRACE_MS / 2
        tracker.verdict("1", null)
        clock += SCHEMA_GRACE_MS / 2
        assertEquals(Fatal, tracker.verdict("1", "PGRST204"))
    }
}
