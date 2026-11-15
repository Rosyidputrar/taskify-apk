package com.rosyidputrar.taskify.core.store

import com.powersync.PowerSyncDatabase
import com.powersync.inMemory
import com.rosyidputrar.taskify.core.store.home.ActivityKind
import com.rosyidputrar.taskify.core.store.home.HomeRepository
import com.rosyidputrar.taskify.core.domain.journal.journalStreak
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

class HomeRepositoryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = PowerSyncDatabase.inMemory(TaskifySchema.schema, scope)
    private val home = HomeRepository(db)
    private val tasks = TaskRepository(db) { "u" }
    private val today = "2026-10-07"

    @After fun tearDown() = runBlocking {
        db.close()
        scope.cancel()
    }

    private suspend fun insert(sql: String, vararg params: Any?) = db.execute(sql, params.toList())

    @Test fun summaryIgnoresDebtsAndTransfersForStats() = runBlocking {
        tasks.create(NewTask("A", dueDate = today))
        tasks.create(NewTask("B", dueDate = "2026-10-01"))
        insert("INSERT INTO wallets (id, user_id, created_at, updated_at, name, type, currency, initial_balance, position, is_archived) VALUES ('w','u','x','x','Dompet','cash','IDR',100000,0,0)")
        insert("INSERT INTO transactions (id, user_id, created_at, updated_at, kind, amount, wallet_id, occurred_on) VALUES ('t1','u','2026-10-07T03:00:00.000Z','x','expense',25000,'w',?)", today)
        insert("INSERT INTO transactions (id, user_id, created_at, updated_at, kind, amount, wallet_id, occurred_on, debt_id) VALUES ('t2','u','x','x','expense',50000,'w',?,'d')", today)
        insert("INSERT INTO transactions (id, user_id, created_at, updated_at, kind, amount, wallet_id, occurred_on) VALUES ('t3','u','x','x','income',10000,'w','2026-10-02')")
        insert("INSERT INTO journal_entries (id, user_id, created_at, updated_at, entry_date, content, mood, is_pinned) VALUES ('j1','u','2026-10-07 01:00:00Z','x',?,'Pagi tenang',4,0)", today)
        insert("INSERT INTO journal_entries (id, user_id, created_at, updated_at, entry_date, content, mood, is_pinned) VALUES ('j2','u','2026-10-07 13:00:00Z','x',?,'Malam',5,0)", today)

        val s = home.watchSummary(today).first()
        assertEquals(1, s.tasksToday)
        assertEquals(1, s.tasksOverdue)
        assertEquals(25_000, s.expenseToday) // hutang tidak dihitung sebagai belanja
        assertEquals(10_000, s.incomeMonth)
        assertEquals(100_000 - 25_000 - 50_000 + 10_000, s.balance) // saldo tetap menghitung semuanya
        assertEquals(2, s.journalToday)
        assertEquals(4.5, s.moodToday)
    }

    @Test fun activityTimelineSortedByWibTime() = runBlocking {
        tasks.create(NewTask("Rapat", dueDate = today, dueTime = "14:00"))
        tasks.create(NewTask("Tanpa jam", dueDate = today))
        insert("INSERT INTO journal_entries (id, user_id, created_at, updated_at, entry_date, content, is_pinned) VALUES ('j','u','2026-10-07 01:30:00Z','x',?,'Bangun   pagi',0)", today)
        insert("INSERT INTO wallets (id, user_id, created_at, updated_at, name, type, currency, initial_balance, position, is_archived) VALUES ('w','u','x','x','D','cash','IDR',0,0,0)")
        insert("INSERT INTO transactions (id, user_id, created_at, updated_at, kind, amount, wallet_id, occurred_on, occurred_time, title) VALUES ('t','u','x','x','income',5000,'w',?,'12:15:00','Gaji')", today)
        val items = home.watchActivity(today).first()
        assertEquals(listOf("08:30", "12:15", "14:00", ""), items.map { it.time })
        assertEquals(ActivityKind.Journal, items[0].kind)
        assertEquals("Bangun pagi", items[0].title)
        assertEquals(true, items[1].isIncome)
        assertEquals(5000, items[1].amount)
    }

    @Test fun streak() {
        assertEquals(3, journalStreak(setOf("2026-10-07", "2026-10-06", "2026-10-05", "2026-10-03"), "2026-10-07"))
        assertEquals(2, journalStreak(setOf("2026-10-06", "2026-10-05"), "2026-10-07"))
        assertEquals(0, journalStreak(setOf("2026-10-04"), "2026-10-07"))
    }
}
