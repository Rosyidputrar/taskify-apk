package com.rosyidputrar.taskify

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.domain.focus.MINUTE
import com.rosyidputrar.taskify.focus.FocusController
import com.rosyidputrar.taskify.focus.FocusReceiver
import com.rosyidputrar.taskify.focus.FocusRecord
import com.rosyidputrar.taskify.focus.FocusTileService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Temuan 11 & 12: alarm fokus dipasang ulang setelah reboot / ganti waktu / izin alarm tepat
 * berubah, dan "Jangan Ganggu" yang macet menyala dipulihkan saat aplikasi mulai — tanpa perlu
 * membuka aplikasi.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class FocusRecoveryTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val nm get() = app.getSystemService(NotificationManager::class.java)
    private val alarmManager get() = app.getSystemService(AlarmManager::class.java)!!
    private val alarms get() = shadowOf(alarmManager)
    private val t0 = 1_763_280_000_000L // 2025-11-16 15:00 WIB
    private var clock = t0
    private val records = mutableListOf<FocusRecord>()

    @Before fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(nm).setNotificationPolicyAccessGranted(true)
        app.getSharedPreferences("focus", 0).edit().clear().commit()
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        records.clear()
        clock = t0
    }

    @After fun tearDown() = stopKoin()

    private fun focusFromKoin(): FocusController =
        org.koin.core.context.GlobalContext.get().get()

    private fun startKoinWithFocus() {
        stopKoin()
        startKoin {
            modules(module {
                single { FocusController(app, record = { records += it }, hideContent = { false }, now = { clock }) }
            })
        }
    }

    /** PendingIntent yang sama dengan yang dipakai FocusController untuk alarm akhir fase. */
    private fun phaseEndAlarmIntent(): PendingIntent {
        val intent = Intent(app, FocusReceiver::class.java)
            .setAction(FocusReceiver.ACTION_PHASE_END)
            .setData(Uri.parse("taskify://focus/${FocusReceiver.ACTION_PHASE_END}"))
        return PendingIntent.getBroadcast(app, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun waitUntil(timeoutMs: Long = 5_000, check: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (check()) return
            Thread.sleep(50)
        }
        assertTrue(check(), "kondisi tidak terpenuhi dalam ${timeoutMs}ms (receiver jalan async)")
    }

    // ---- Temuan 11: re-arm alarm ---------------------------------------------------------------

    @Test fun recoverAfterRestartRearmsAlarmFromSavedRun() = runBlocking<Unit> {
        val first = FocusController(app, record = { records += it }, now = { clock })
        first.start("t1", "Tulis laporan")
        assertEquals(t0 + 25 * MINUTE, alarms.nextScheduledAlarm!!.triggerAtTime)
        // Proses mati: alarm AlarmManager hilang, sesi tetap tersimpan di SharedPreferences.
        alarmManager.cancel(phaseEndAlarmIntent())
        assertNull(alarms.nextScheduledAlarm)

        val reborn = FocusController(app, record = { records += it }, now = { clock })
        assertEquals(first.run.value, reborn.run.value)
        reborn.recoverAfterRestart()
        assertEquals(t0 + 25 * MINUTE, alarms.nextScheduledAlarm!!.triggerAtTime)
    }

    @Test fun bootBroadcastRearmsAlarm() = runBlocking<Unit> {
        startKoinWithFocus()
        val focus = focusFromKoin()
        focus.setDnd(true)
        focus.start("t1", "Tulis laporan")
        alarmManager.cancel(phaseEndAlarmIntent())
        assertNull(alarms.nextScheduledAlarm)
        // Proses mati lalu reboot: controller baru membaca sesi dari prefs.
        startKoinWithFocus()

        FocusReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        // Catatan: ShadowAlarmManager.nextScheduledAlarm MENGAMBIL alarm dari antrean
        // (poll + deschedule) — sekali dibaca alarmnya hilang. Tangkap nilainya saat terlihat.
        var rearmedAt: Long? = null
        waitUntil { alarms.nextScheduledAlarm?.also { rearmedAt = it.triggerAtTime } != null }
        assertEquals(t0 + 25 * MINUTE, rearmedAt)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, nm.currentInterruptionFilter)
    }

    @Test fun timeSetBroadcastCatchesUpFinishedPhaseAndRearms() = runBlocking<Unit> {
        startKoinWithFocus()
        val focus = focusFromKoin()
        focus.start("t1", "Tulis laporan")
        clock += 25 * MINUTE + 3_000 // fase fokus habis selagi HP mati
        alarmManager.cancel(phaseEndAlarmIntent())
        startKoinWithFocus()

        FocusReceiver().onReceive(app, Intent(Intent.ACTION_TIME_CHANGED))
        // Lihat bootBroadcastRearmsAlarm: nextScheduledAlarm mengonsumsi alarm saat dibaca.
        var rearmedAt: Long? = null
        waitUntil { alarms.nextScheduledAlarm?.also { rearmedAt = it.triggerAtTime } != null }
        // Fokus dicatat, istirahat 5 menit berjalan, alarm akhir istirahat dipasang ulang.
        assertEquals(1, records.size)
        assertEquals(25L, records[0].minutes)
        assertEquals(t0 + 30 * MINUTE, rearmedAt)
    }

    @Test fun manifestDeclaresFocusReceiverForSystemEvents() {
        val pm = app.packageManager
        val actions = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
        for (action in actions) {
            val receivers = pm.queryBroadcastReceivers(Intent(action), 0)
            assertTrue(
                receivers.any { it.activityInfo.name.endsWith("FocusReceiver") },
                "FocusReceiver harus menerima $action",
            )
        }
    }

    // ---- Temuan 12: DND macet ------------------------------------------------------------------

    @Test fun stuckDndIsRestoredWhenNoSessionAfterRestart() = runBlocking<Unit> {
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        app.getSharedPreferences("focus", 0).edit().putBoolean("dnd_applied", true).commit()
        val reborn = FocusController(app, record = {}, now = { clock })
        assertNull(reborn.run.value, "tidak ada sesi aktif")
        reborn.recoverAfterRestart()
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, nm.currentInterruptionFilter)
        assertFalse(app.getSharedPreferences("focus", 0).getBoolean("dnd_applied", false), "flag dibersihkan")
    }

    @Test fun dndIsNotRestoredWhenTaskifyDidNotApplyIt() = runBlocking<Unit> {
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        FocusController(app, record = {}, now = { clock }).recoverAfterRestart()
        assertEquals(
            NotificationManager.INTERRUPTION_FILTER_PRIORITY, nm.currentInterruptionFilter,
            "DND yang dinyalakan pengguna/aplikasi lain tidak boleh kita matikan",
        )
    }

    @Test fun dndIsNotRestoredWhenUserChangedFilterThemselves() = runBlocking<Unit> {
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
        app.getSharedPreferences("focus", 0).edit().putBoolean("dnd_applied", true).commit()
        FocusController(app, record = {}, now = { clock }).recoverAfterRestart()
        assertEquals(
            NotificationManager.INTERRUPTION_FILTER_ALARMS, nm.currentInterruptionFilter,
            "pengguna sudah mengubah filter sendiri → jangan timpa",
        )
    }

    @Test fun recoverWithActiveSessionKeepsDndOn() = runBlocking<Unit> {
        val first = FocusController(app, record = { records += it }, now = { clock })
        first.setDnd(true)
        first.start("t1", "Tulis laporan")
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, nm.currentInterruptionFilter)
        val reborn = FocusController(app, record = { records += it }, now = { clock })
        reborn.recoverAfterRestart()
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, nm.currentInterruptionFilter)
        assertEquals(t0 + 25 * MINUTE, alarms.nextScheduledAlarm!!.triggerAtTime)
    }

    // ---- Nit: tile terdaftar + aksi notifikasi bisa diklik --------------------------------------

    @Test fun focusTileServiceIsRegistered() {
        val info = app.packageManager.getServiceInfo(
            ComponentName(app, FocusTileService::class.java), PackageManager.GET_META_DATA,
        )
        assertNotNull(info)
        val tiles = app.packageManager.queryIntentServices(
            Intent("android.service.quicksettings.action.QS_TILE"), 0,
        )
        assertTrue(tiles.any { it.serviceInfo.name.endsWith("FocusTileService") })
    }

    @Test fun pauseActionPendingIntentPausesSession() = runBlocking<Unit> {
        startKoinWithFocus()
        val focus = focusFromKoin()
        focus.start("t1", "Tulis laporan")
        val running = shadowOf(nm).allNotifications.first { it.channelId == FocusController.CHANNEL_RUNNING }
        val pauseAction = running.actions.first { it.title.toString() == "Jeda" }
        val sent = shadowOf(pauseAction.actionIntent).savedIntent
        assertEquals(FocusReceiver.ACTION_PAUSE, sent.action)
        assertEquals(FocusReceiver::class.java.name, sent.component!!.className)

        FocusReceiver().onReceive(app, sent)
        waitUntil { focus.run.value?.isPaused == true }
        assertTrue(focus.run.value!!.isPaused)
        // Tunggu sampai pipeline jeda async selesai sepenuhnya (termasuk cancelAlarm),
        // bukan hanya sampai `isPaused` terlihat: `scheduledAlarms` tidak mengonsumsi
        // (berbeda dengan `nextScheduledAlarm` yang poll + deschedule, jadi tidak bisa
        // dipakai untuk menunggu).
        waitUntil { alarms.scheduledAlarms.isEmpty() }
        assertTrue(alarms.scheduledAlarms.isEmpty(), "jeda membatalkan alarm")
    }
}
