package com.rosyidputrar.taskify

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.domain.focus.FocusPhase
import com.rosyidputrar.taskify.core.domain.focus.MINUTE
import com.rosyidputrar.taskify.focus.FocusController
import com.rosyidputrar.taskify.focus.FocusRecord
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Mode Fokus: alarm akhir fase, notifikasi berjalan, catatan riwayat, Jangan Ganggu. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class FocusControllerTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val nm get() = app.getSystemService(NotificationManager::class.java)
    private val alarms get() = shadowOf(app.getSystemService(AlarmManager::class.java))
    private val t0 = 1_763_280_000_000L // 2025-11-16 15:00 WIB
    private var clock = t0
    private val records = mutableListOf<FocusRecord>()
    private var hide = false
    private lateinit var focus: FocusController

    @Before fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        app.getSharedPreferences("focus", 0).edit().clear().commit()
        focus = FocusController(app, record = { records += it }, hideContent = { hide }, now = { clock })
    }

    private fun running() = shadowOf(nm).allNotifications.firstOrNull { it.channelId == FocusController.CHANNEL_RUNNING }

    @Test fun startPostsOngoingCountdownAndSchedulesAlarm() = runBlocking<Unit> {
        focus.start("t1", "Tulis laporan")
        val n = assertNotNull(running())
        assertEquals("Tulis laporan", n.extras.getString("android.title"))
        assertTrue(n.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue(n.extras.getBoolean("android.chronometerCountDown"))
        assertEquals(t0 + 25 * MINUTE, n.`when`)
        assertEquals(listOf("Jeda", "Lewati", "Berhenti"), n.actions.map { it.title.toString() })
        assertEquals(t0 + 25 * MINUTE, alarms.nextScheduledAlarm!!.triggerAtTime)
    }

    @Test fun pauseCancelsAlarmAndResumeShiftsIt() = runBlocking<Unit> {
        focus.start(null, null)
        clock += 10 * MINUTE
        focus.pause()
        assertNull(alarms.nextScheduledAlarm)
        assertEquals(listOf("Lanjut", "Lewati", "Berhenti"), running()!!.actions.map { it.title.toString() })
        clock += 30 * MINUTE
        focus.resume()
        assertEquals(clock + 15 * MINUTE, alarms.nextScheduledAlarm!!.triggerAtTime)
    }

    @Test fun phaseEndRecordsFocusAndStartsBreak() = runBlocking<Unit> {
        focus.start("t1", "Tulis laporan")
        clock += 25 * MINUTE + 3_000
        focus.catchUp()
        val run = focus.run.value!!
        assertEquals(FocusPhase.ShortBreak, run.phase)
        assertEquals(t0 + 25 * MINUTE, run.phaseStartedAt, "istirahat dihitung dari akhir fokus, bukan saat alarm telat")
        assertEquals(1, records.size)
        assertEquals(25L, records[0].minutes)
        assertEquals("2025-11-16", records[0].day)
        assertNotNull(shadowOf(nm).allNotifications.firstOrNull { it.channelId == FocusController.CHANNEL_ALERT })
    }

    @Test fun missedPhasesCatchUpAndWaitForStart() = runBlocking<Unit> {
        focus.start(null, null)
        clock += 3 * 60 * MINUTE // HP mati lama
        focus.catchUp(alert = false)
        val run = focus.run.value!!
        assertEquals(FocusPhase.Focus, run.phase)
        assertEquals(2, run.round)
        assertTrue(run.isPaused, "putaran berikutnya menunggu Mulai")
        assertEquals(1, records.size)
        assertNull(alarms.nextScheduledAlarm)
    }

    @Test fun stopRecordsPartialAndClearsNotification() = runBlocking<Unit> {
        focus.start("t1", "Tulis laporan")
        clock += 12 * MINUTE
        focus.stop()
        assertNull(focus.run.value)
        assertNull(running())
        assertEquals(12L, records.single().minutes)
        // Di bawah satu menit tidak dicatat.
        focus.start(null, null)
        clock += 30_000
        focus.stop()
        assertEquals(1, records.size)
    }

    @Test fun survivesProcessDeath() = runBlocking<Unit> {
        focus.start("t1", "Tulis laporan")
        val reborn = FocusController(app, record = { records += it }, now = { clock })
        assertEquals(focus.run.value, reborn.run.value)
    }

    @Test fun hiddenContentHidesTaskTitle() = runBlocking<Unit> {
        hide = true
        focus.start("t1", "Rahasia")
        assertEquals("Mode Fokus", running()!!.extras.getString("android.title"))
    }

    @Test fun dndOnlyDuringFocusAndOnlyRestoredIfWeSetIt() = runBlocking<Unit> {
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        focus.setDnd(true)
        focus.start(null, null)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, nm.currentInterruptionFilter)
        focus.pause()
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, nm.currentInterruptionFilter)
        focus.resume()
        // Pengguna mengubah sendiri ke "alarm saja" → tidak kita timpa saat berhenti.
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
        focus.stop()
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALARMS, nm.currentInterruptionFilter)
    }

    @Test fun clearOnLogoutForgetsEverythingWithoutRecording() = runBlocking<Unit> {
        focus.start("t1", "Tulis laporan")
        clock += 10 * MINUTE
        focus.clear()
        assertNull(focus.run.value)
        assertNull(running())
        assertTrue(records.isEmpty())
        assertNull(FocusController(app, record = {}, now = { clock }).run.value)
    }
}
