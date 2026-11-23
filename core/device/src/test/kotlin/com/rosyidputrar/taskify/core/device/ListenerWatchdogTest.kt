package com.rosyidputrar.taskify.core.device

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ListenerWatchdogTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var reconnects = 0
    private var uptime = ListenerWatchdog.STARTUP_GRACE_MS

    @Before fun reset() {
        context.getSharedPreferences("taskify_device_modules", Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** Instans baru = proses baru: hanya nilai tersimpan yang terbawa. */
    private fun newProcess() = DeviceModulePrefs(context)

    private fun watchdog(prefs: DeviceModulePrefs, access: Boolean = true) =
        ListenerWatchdog(context, prefs, hasAccess = { access }, reconnect = { reconnects++ }, elapsed = { uptime }, processStart = 0)

    @Test fun staleConnectedFlagExpiresSinceLastKnownAlive() {
        newProcess().apply {
            setListenerConnected(true, now = 1_000)
            noteListenerAlive(now = 500_000)
        }
        // Proses dimatikan paksa: onListenerDisconnected tidak jalan, "tersambung" tetap tersimpan.
        val reborn = newProcess()
        assertTrue(reborn.listenerConnected.value)
        assertFalse(reborn.listenerLive)
        reborn.expireStaleConnection()
        assertFalse(reborn.listenerConnected.value)
        assertEquals(500_000L, reborn.listenerDisconnectedAt)
        assertFalse(newProcess().listenerConnected.value)
    }

    @Test fun liveConnectionIsNeverExpired() {
        val prefs = newProcess()
        prefs.setListenerConnected(true, now = 1_000)
        prefs.expireStaleConnection()
        assertTrue(prefs.listenerConnected.value)
        assertEquals(0L, prefs.listenerDisconnectedAt)
    }

    @Test fun lastSeenIsWrittenAtMostOncePerMinute() {
        val prefs = newProcess()
        prefs.setListenerConnected(true, now = 1_000)
        prefs.noteListenerAlive(now = 100_000)
        prefs.noteListenerAlive(now = 130_000) // < 1 menit sejak tulisan terakhir → dilewati
        val reborn = newProcess()
        reborn.expireStaleConnection()
        assertEquals(100_000L, reborn.listenerDisconnectedAt)
    }

    @Test fun reconnectsWhenProcessDiedWhileConnected() = runTest {
        newProcess().setListenerConnected(true, now = 1_000)
        val prefs = newProcess()
        assertTrue(watchdog(prefs).check())
        assertEquals(1, reconnects)
        assertFalse(prefs.listenerConnected.value)
        assertEquals(1_000L, prefs.listenerDisconnectedAt)
    }

    @Test fun leavesLiveListenerAndMissingAccessAlone() = runTest {
        val live = newProcess().apply { setListenerConnected(true, now = 1_000) }
        assertFalse(watchdog(live).check())
        // Tanpa Akses notifikasi tidak ada yang bisa disambungkan; status tersimpan tidak disentuh.
        val revoked = newProcess()
        assertFalse(watchdog(revoked, access = false).check())
        assertEquals(0, reconnects)
        assertTrue(revoked.listenerConnected.value)
    }

    @Test fun waitsOutStartupGraceSoSystemCanConnectFirst() = runTest {
        newProcess().setListenerConnected(true, now = 1_000)
        val prefs = newProcess()
        uptime = 4_000
        assertTrue(watchdog(prefs).check())
        assertEquals(ListenerWatchdog.STARTUP_GRACE_MS - 4_000, currentTime)
    }

    @Test fun backToBackChecksDoNotTearDownAPendingReconnect() = runTest {
        val prefs = newProcess()
        val dog = watchdog(prefs)
        assertTrue(dog.check())
        uptime += ListenerWatchdog.RETRY_GAP_MS - 1
        assertFalse(dog.check())
        assertEquals(1, reconnects)
        uptime += 1
        assertTrue(dog.check())
        assertEquals(2, reconnects)
    }
}
