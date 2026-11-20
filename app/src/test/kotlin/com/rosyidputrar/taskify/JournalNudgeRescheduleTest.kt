package com.rosyidputrar.taskify

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.rosyidputrar.taskify.core.data.DevicePrefs
import com.rosyidputrar.taskify.core.reminders.ContextReminderPrefs
import com.rosyidputrar.taskify.core.reminders.ContextReminderScheduler
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Temuan 13: JournalNudgeWorker tidak boleh menjadwalkan ulang dirinya sendiri setelah keluar
 * (dbOwner null). Diuji lewat [ContextReminderScheduler.rescheduleJournalAfterNudge], seam yang
 * dipanggil worker di blok `finally`-nya — doWork() sendiri butuh HomeRepository + Koin dan
 * tidak bisa dibangun tanpa database di tes unit.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class JournalNudgeRescheduleTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var scheduler: ContextReminderScheduler

    @Before fun setUp() {
        app.getSharedPreferences("taskify_device", 0).edit().clear().commit()
        // Penyedia startup androidx tidak jalan di Robolectric → inisialisasi manual.
        runCatching { WorkManager.initialize(app, Configuration.Builder().build()) }
        // WorkManager (DB-nya) dipakai bersama antar kelas tes dalam satu worker Gradle:
        // LogoutCleanupTest dan tes saudara di kelas ini sama-sama mengantrekan
        // "journal-nudge", dan entri CANCELLED tetap terdaftar di
        // getWorkInfosForUniqueWork(). Tanpa pembersihan ini, hasil tes ini bergantung
        // pada urutan eksekusi (flaky: lolos di base dan run C/D, gagal di run A dan
        // full test). Batalkan lalu pangkas, tunggu keduanya selesai.
        val wm = WorkManager.getInstance(app)
        wm.cancelUniqueWork(ContextReminderScheduler.WORK_JOURNAL).result.get()
        wm.pruneWork().result.get()
        scheduler = ContextReminderScheduler(app, ContextReminderPrefs(DevicePrefs(app)))
    }

    private fun journalWork() =
        WorkManager.getInstance(app).getWorkInfosForUniqueWork(ContextReminderScheduler.WORK_JOURNAL).get()

    @Test fun nudgeIsNotRescheduledAfterLogout() {
        scheduler.rescheduleJournalAfterNudge(dbOwner = null)
        assertTrue(journalWork().isEmpty(), "sudah keluar → tidak ada pekerjaan journal-nudge")
    }

    @Test fun nudgeIsRescheduledWhenSignedIn() {
        scheduler.rescheduleJournalAfterNudge(dbOwner = "owner@example.com")
        val work = journalWork()
        assertEquals(1, work.size)
        assertEquals(WorkInfo.State.ENQUEUED, work.single().state)
    }
}
