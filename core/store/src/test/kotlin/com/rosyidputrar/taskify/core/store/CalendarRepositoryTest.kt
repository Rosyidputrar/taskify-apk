package com.rosyidputrar.taskify.core.store

import com.powersync.PowerSyncDatabase
import com.powersync.inMemory
import com.rosyidputrar.taskify.core.store.calendar.CalendarRepository
import com.rosyidputrar.taskify.core.store.tasks.NewTask
import com.rosyidputrar.taskify.core.store.tasks.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CalendarRepositoryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = PowerSyncDatabase.inMemory(TaskifySchema.schema, scope)
    private val calendar = CalendarRepository(db)
    private val tasks = TaskRepository(db) { "u" }

    @After fun tearDown() = runBlocking {
        db.close()
        scope.cancel()
    }

    private suspend fun sql(s: String, vararg p: Any?) = db.execute(s, p.toList())

    @Test fun activityAggregatesAllModulesWithSameRulesAsWebapp() = runBlocking {
        sql("INSERT INTO journal_entries (id,user_id,created_at,updated_at,entry_date,content,mood,is_pinned) VALUES ('j1','u','x','x','2026-10-07','a',4,0)")
        sql("INSERT INTO journal_entries (id,user_id,created_at,updated_at,entry_date,content,mood,is_pinned) VALUES ('j2','u','x','x','2026-10-07','b',5,0)")
        sql("INSERT INTO notes (id,user_id,created_at,updated_at,note_date,content,is_pinned) VALUES ('n1','u','x','x','2026-10-07','c',0)")
        sql("INSERT INTO notes (id,user_id,created_at,updated_at,note_date,content,is_pinned) VALUES ('n2','u','x','x',NULL,'bebas',0)")
        sql("INSERT INTO wallets (id,user_id,created_at,updated_at,name,type,currency,initial_balance,position,is_archived) VALUES ('w','u','x','x','D','cash','IDR',0,0,0)")
        sql("INSERT INTO transactions (id,user_id,created_at,updated_at,kind,amount,wallet_id,occurred_on) VALUES ('t1','u','x','x','expense',20000,'w','2026-10-07')")
        sql("INSERT INTO transactions (id,user_id,created_at,updated_at,kind,amount,wallet_id,occurred_on) VALUES ('t2','u','x','x','transfer',99000,'w','2026-10-07')")
        sql("INSERT INTO transactions (id,user_id,created_at,updated_at,kind,amount,wallet_id,occurred_on,debt_id) VALUES ('t3','u','x','x','expense',50000,'w','2026-10-07','d')")
        sql("INSERT INTO transactions (id,user_id,created_at,updated_at,kind,amount,wallet_id,occurred_on) VALUES ('t4','u','x','x','income',5000,'w','2026-10-08')")
        // Dibuat 23:30 UTC 6 Okt = 06:30 WIB 7 Okt → masuk ke 7 Okt.
        sql("INSERT INTO snippets (id,user_id,created_at,updated_at,title,language,code,is_favorite) VALUES ('s','u','2026-10-06 23:30:00Z','x','Regex','js','x',0)")

        val map = calendar.watchActivity("2026-10-01", "2026-10-31").first()
        val day = map["2026-10-07"]!!
        assertEquals(2, day.journal)
        assertEquals(5, day.mood) // rata-rata 4.5 dibulatkan
        assertEquals(1, day.notes)
        assertEquals(1, day.transactions)
        assertEquals(20_000, day.expense)
        assertEquals(1, day.vault)
        assertEquals(5_000, map["2026-10-08"]!!.income)
        assertNull(map["2026-10-06"])

        assertEquals(listOf("Regex"), calendar.watchVault("2026-10-07").first().map { it.title })
        assertEquals(3, calendar.watchTransactions("2026-10-07").first().size)
        assertEquals(1, calendar.watchNotes("2026-10-07").first().size)
    }

    @Test fun tasksInRangeAndRecurringSources() = runBlocking {
        tasks.create(NewTask("Dalam", dueDate = "2026-10-10"))
        tasks.create(NewTask("Luar", dueDate = "2026-11-10"))
        tasks.create(NewTask("Ulang", dueDate = "2026-09-30", rrule = "FREQ=DAILY;INTERVAL=1"))
        assertEquals(listOf("Dalam"), calendar.watchTasksInRange("2026-10-01", "2026-10-31").first().map { it.title })
        assertEquals(listOf("Ulang"), calendar.watchActiveRecurring("2026-10-31").first().map { it.title })
    }
}
