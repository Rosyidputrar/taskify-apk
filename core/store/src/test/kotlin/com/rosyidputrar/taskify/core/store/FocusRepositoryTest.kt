package com.rosyidputrar.taskify.core.store

import com.powersync.PowerSyncDatabase
import com.powersync.inMemory
import com.rosyidputrar.taskify.core.store.focus.FocusRepository
import com.rosyidputrar.taskify.core.store.focus.FocusStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals

class FocusRepositoryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = PowerSyncDatabase.inMemory(TaskifySchema.schema, scope)
    private val focus = FocusRepository(db)

    @After fun tearDown() = runBlocking {
        db.close()
        scope.cancel()
    }

    @Test fun statsSumMinutesAndPickTopTask() = runBlocking {
        focus.record("a", "Laporan", "2026-10-06", "s", "e", 25)
        focus.record("a", "Laporan", "2026-10-07", "s", "e", 25)
        focus.record("b", "Desain", "2026-10-07", "s", "e", 40)
        focus.record(null, null, "2026-10-07", "s", "e", 10)
        focus.record("a", "Laporan", "2026-09-30", "s", "e", 100) // di luar rentang
        focus.record("a", "Laporan", "2026-10-07", "s", "e", 0) // diabaikan

        assertEquals(FocusStats(minutes = 100, sessions = 4, topTask = "Laporan", topTaskMinutes = 50), focus.watchStats("2026-10-01", "2026-10-07").first())
        assertEquals(FocusStats(), focus.watchStats("2026-11-01", "2026-11-30").first())
        assertEquals(150L, focus.watchTaskMinutes("a").first())
    }

    @Test fun topTaskTitleIsDeterministicWhenTitleChanges() = runBlocking {
        // Judul tugas bisa berubah (di-rename): GROUP BY task_id sambil memilih task_title
        // telanjang adalah kolom bare SQLite yang nilainya arbitrer — pakai MAX() supaya
        // judul teratas deterministik.
        focus.record("a", "Zebra", "2026-10-06", "s", "e", 25)
        focus.record("a", "Apel", "2026-10-07", "s", "e", 25)
        focus.record("b", "Desain", "2026-10-07", "s", "e", 40)
        val stats = focus.watchStats("2026-10-01", "2026-10-07").first()
        assertEquals("Zebra", stats.topTask)
        assertEquals(50L, stats.topTaskMinutes)
    }

    @Test fun focusSessionsNeverQueueForUpload() = runBlocking {
        focus.record("a", "Laporan", "2026-10-07", "s", "e", 25)
        assertEquals(null, db.getNextCrudTransaction())
    }
}
