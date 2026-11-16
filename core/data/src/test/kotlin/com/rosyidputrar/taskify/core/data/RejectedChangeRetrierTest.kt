package com.rosyidputrar.taskify.core.data

import com.rosyidputrar.taskify.core.domain.sync.CrudOp
import com.rosyidputrar.taskify.core.domain.sync.PostgrestLikeError
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RejectedChangeRetrierTest {
    private class Store : UploadErrorStore {
        val deleted = mutableListOf<String>()
        val updated = mutableMapOf<String, Pair<String?, String?>>()
        override suspend fun deleteUploadError(id: String) { deleted += id }
        override suspend fun updateUploadError(id: String, code: String?, message: String?, at: String) { updated[id] = code to message }
    }

    private fun row(id: String, op: String = "PATCH", table: String = "tasks", payload: String? = """{"title":"x"}""", at: String = "2026-11-16T01:00:00.000Z") =
        UploadErrorRow(id, op, table, "r-$id", payload, "23514", "lama", at)

    @Test fun acceptedRetryRemovesTheRow() = runBlocking {
        val store = Store()
        val sent = mutableListOf<CrudOp>()
        val outcome = RejectedChangeRetrier(store) { sent += it; null }.retry(row("e1"))
        assertIs<RejectedChangeRetrier.Outcome.Sent>(outcome)
        assertEquals(listOf("e1"), store.deleted)
        assertEquals("r-e1", sent.single().id)
    }

    @Test fun permanentRejectionKeepsRowWithNewReason() = runBlocking {
        val store = Store()
        val outcome = RejectedChangeRetrier(store) { PostgrestLikeError("23514", "melanggar batas") }.retry(row("e1"))
        assertIs<RejectedChangeRetrier.Outcome.StillRejected>(outcome)
        assertTrue(store.deleted.isEmpty())
        assertEquals("23514" to "melanggar batas", store.updated["e1"])
    }

    @Test fun networkTroubleChangesNothing() = runBlocking {
        val store = Store()
        val outcome = RejectedChangeRetrier(store) { PostgrestLikeError(null, "timeout") }.retry(row("e1"))
        assertIs<RejectedChangeRetrier.Outcome.Unreachable>(outcome)
        assertTrue(store.deleted.isEmpty() && store.updated.isEmpty())
    }

    @Test fun retryAllGoesOldestFirstAndSummarizes() = runBlocking {
        val store = Store()
        val order = mutableListOf<String>()
        val retrier = RejectedChangeRetrier(store) { op -> order += op.id; if (op.id == "r-b") PostgrestLikeError("42501", "RLS") else null }
        val summary = retrier.retryAll(
            listOf(
                row("c", at = "2026-11-16T03:00:00.000Z"),
                row("a", at = "2026-11-16T01:00:00.000Z"),
                row("b", at = "2026-11-16T02:00:00.000Z"),
                row("k", table = "pw_keys", payload = """{"wrapped_key":"z"}"""),
            ),
        )
        assertEquals(listOf("r-a", "r-b", "r-c"), order)
        assertEquals(RejectedChangeRetrier.Summary(sent = 2, rejected = 1, unreachable = 0, skipped = 1), summary)
        assertEquals("2 terkirim · 1 masih ditolak · 1 tidak bisa dikirim ulang", summary.text)
    }
}
