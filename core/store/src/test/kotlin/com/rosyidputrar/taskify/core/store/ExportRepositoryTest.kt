package com.rosyidputrar.taskify.core.store

import com.powersync.PowerSyncDatabase
import com.powersync.inMemory
import com.rosyidputrar.taskify.core.store.cross.ExportRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.After
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportRepositoryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = PowerSyncDatabase.inMemory(TaskifySchema.schema, scope)

    @After fun tearDown() = runBlocking {
        db.close()
        scope.cancel()
    }

    @Test fun exportsAllTablesIncludingTrashWithTypes() = runBlocking {
        db.execute("INSERT INTO tasks (id, user_id, title, status, priority, deleted_at, created_at, updated_at, position) VALUES ('t1', 'u', 'Tugas \"kutip\"', 'todo', 2, '2026-11-01T00:00:00Z', '2026-11-01T00:00:00Z', '2026-11-01T00:00:00Z', 1.5)")
        db.execute("INSERT INTO transactions (id, user_id, occurred_on, kind, amount, created_at, updated_at) VALUES ('x', 'u', '2026-11-03', 'expense', 25000, '2026-11-01T00:00:00Z', '2026-11-01T00:00:00Z')")
        val out = ByteArrayOutputStream()
        val rows = ExportRepository(db).export(out, "u", "1.0.0", 0)
        assertEquals(2, rows)
        val root = Json.parseToJsonElement(out.toString(Charsets.UTF_8)).jsonObject
        assertEquals("taskify", root["app"]!!.jsonPrimitive.content)
        val tables = root["tables"]!!.jsonObject
        assertEquals(ExportRepository.TABLES.toSet(), tables.keys)
        val task = tables["tasks"]!!.jsonArray.single().jsonObject
        assertEquals("t1", task["id"]!!.jsonPrimitive.content)
        assertEquals("Tugas \"kutip\"", task["title"]!!.jsonPrimitive.content)
        assertEquals(2, task["priority"]!!.jsonPrimitive.long)
        assertTrue(task["deleted_at"]!!.jsonPrimitive.content.isNotEmpty(), "item di Sampah ikut diekspor")
        assertEquals(25000, tables["transactions"]!!.jsonArray.single().jsonObject["amount"]!!.jsonPrimitive.long)
        assertTrue(tables["notes"]!!.jsonArray.isEmpty())
    }
}
