package com.rosyidputrar.taskify.core.domain.sync

import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RejectedChangesTest {
    @Test fun patchIsRebuiltWithTypedValues() {
        val op = rejectedOpForRetry("PATCH", "tasks", "t1", """{"title":"Bayar listrik","priority":3,"done":null}""")!!
        assertEquals(CrudOpType.PATCH, op.op)
        assertEquals("tasks", op.table)
        assertEquals("t1", op.id)
        assertEquals("3", op.opData!!["priority"]!!.jsonPrimitive.content)
        assertEquals(true, op.opData!!["priority"]!!.jsonPrimitive.isString.not())
    }

    @Test fun deleteNeedsNoPayload() {
        assertEquals(CrudOpType.DELETE, rejectedOpForRetry("DELETE", "notes", "n1", "null")!!.op)
        assertEquals(CrudOpType.DELETE, rejectedOpForRetry("DELETE", "notes", "n1", null)!!.op)
    }

    @Test fun brokenOrUnsafeRowsAreNotRetried() {
        assertNull(rejectedOpForRetry("PUT", "tasks", "t1", "null"), "PUT tanpa data")
        assertNull(rejectedOpForRetry("PATCH", "tasks", "t1", "{rusak"), "payload rusak")
        assertNull(rejectedOpForRetry("MERGE", "tasks", "t1", "{}"), "jenis tidak dikenal")
        assertNull(rejectedOpForRetry("PATCH", "", "t1", "{}"))
        // Rotasi kunci brankas hanya sah lewat RPC atomik.
        assertNull(rejectedOpForRetry("PATCH", "pw_keys", "k1", """{"wrapped_key":"abc"}"""))
        assertNull(rejectedOpForRetry("PUT", "pw_keys", "k1", """{"kdf":"pbkdf2"}"""))
    }

    @Test fun onlyNewRejectionsAreAnnounced() {
        assertEquals(0, newRejections(null, 5), "patokan saat aplikasi dibuka")
        assertEquals(2, newRejections(5, 7))
        assertEquals(0, newRejections(7, 0), "dibersihkan")
        assertEquals(1, newRejections(0, 1))
    }
}
