package com.rosyidputrar.taskify.core.auth

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.data.DevicePrefs
import com.rosyidputrar.taskify.core.domain.lock.LockPolicy
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class LockManagerTest {
    private var clock = 1_000_000L
    private val softwareMac = PinMac { data ->
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(ByteArray(32) { 3 }, "HmacSHA256")) }.doFinal(data)
    }
    private lateinit var prefs: DevicePrefs

    private fun manager() = LockManager(prefs, softwareMac) { clock }

    @Before fun setUp() {
        prefs = DevicePrefs(ApplicationProvider.getApplicationContext())
        prefs.prefs.edit().clear().commit()
    }

    @Test fun lockedOnLaunchOnlyWhenPinEnabled() = runTest {
        assertFalse(manager().state.value.locked)
        manager().enable("2468", autoLockMinutes = 5)
        val relaunched = manager()
        assertTrue(relaunched.state.value.locked)
        assertEquals(4, relaunched.state.value.config?.length)
    }

    @Test fun correctPinUnlocks() = runTest {
        manager().enable("123456", 1)
        val m = manager()
        assertIs<UnlockResult.Success>(m.unlock("123456"))
        assertFalse(m.state.value.locked)
    }

    @Test fun fiveWrongPinsStartCooldownThatSurvivesRelaunch() = runTest {
        manager().enable("2468", 1)
        val m = manager()
        repeat(4) { assertIs<UnlockResult.Wrong>(m.unlock("0000")) }
        assertIs<UnlockResult.CoolingDown>(m.unlock("0000"))
        // Membunuh aplikasi tidak mereset jeda.
        val relaunched = manager()
        assertIs<UnlockResult.CoolingDown>(relaunched.unlock("2468"))
        clock += LockPolicy.COOLDOWN_MS
        assertIs<UnlockResult.Success>(relaunched.unlock("2468"))
    }

    @Test fun autoLockAfterBackgroundTimeout() = runTest {
        manager().enable("2468", autoLockMinutes = 1)
        val m = manager()
        m.unlock("2468")
        m.onStop(FakeOwner)
        clock += 30_000
        m.onStart(FakeOwner)
        assertFalse(m.state.value.locked)
        m.onStop(FakeOwner)
        clock += 60_000
        m.onStart(FakeOwner)
        assertTrue(m.state.value.locked)
    }

    @Test fun disableRemovesPin() = runTest {
        manager().enable("2468", 0)
        manager().disable()
        val m = manager()
        assertNull(m.state.value.config)
        assertFalse(m.state.value.locked)
    }

    private object FakeOwner : androidx.lifecycle.LifecycleOwner {
        override val lifecycle get() = androidx.lifecycle.LifecycleRegistry.createUnsafe(this)
    }
}
