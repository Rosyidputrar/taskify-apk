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
import kotlin.test.assertFailsWith
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
        manager().enable("246810", autoLockMinutes = 5)
        val relaunched = manager()
        assertTrue(relaunched.state.value.locked)
        assertEquals(6, relaunched.state.value.config?.length)
    }

    @Test fun correctPinUnlocks() = runTest {
        manager().enable("123456", 1)
        val m = manager()
        assertIs<UnlockResult.Success>(m.unlock("123456"))
        assertFalse(m.state.value.locked)
    }

    @Test fun fiveWrongPinsStartCooldownThatSurvivesRelaunch() = runTest {
        manager().enable("246810", 1)
        val m = manager()
        repeat(4) { assertIs<UnlockResult.Wrong>(m.unlock("000000")) }
        assertIs<UnlockResult.CoolingDown>(m.unlock("000000"))
        // Membunuh aplikasi tidak mereset jeda.
        val relaunched = manager()
        assertIs<UnlockResult.CoolingDown>(relaunched.unlock("246810"))
        clock += LockPolicy.COOLDOWN_MS
        assertIs<UnlockResult.Success>(relaunched.unlock("246810"))
    }

    @Test fun autoLockAfterBackgroundTimeout() = runTest {
        manager().enable("246810", autoLockMinutes = 1)
        val m = manager()
        m.unlock("246810")
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
        manager().enable("246810", 0)
        manager().disable()
        val m = manager()
        assertNull(m.state.value.config)
        assertFalse(m.state.value.locked)
    }

    /** D-04: PIN baru di bawah 6 digit ditolak. */
    @Test fun shortPinIsRejectedForNewPins() = runTest {
        assertFailsWith<IllegalArgumentException> { manager().enable("2468", 1) }
        assertFailsWith<IllegalArgumentException> { manager().enable("12345", 1) }
        assertNull(manager().state.value.config)
    }

    /**
     * D-04: PIN lama 4 digit (hash v1, sekali MAC) tetap bisa dibuka — dan langsung di-upgrade
     * ke hash v2 saat berhasil dibuka, tanpa mengubah PIN pengguna.
     */
    @Test fun legacyV1HashUnlocksAndUpgradesToV2() = runTest {
        val salt = ByteArray(16) { it.toByte() }
        val legacyHash = LockPolicy.hashLegacy(softwareMac::mac, salt, "2468")
        prefs.prefs.edit()
            .putString("lock_salt", android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP))
            .putString("lock_hash", android.util.Base64.encodeToString(legacyHash, android.util.Base64.NO_WRAP))
            .putInt("lock_length", 4)
            .putInt("lock_auto_minutes", 5)
            .putInt("lock_failed", 0) // instalasi lama selalu menulis kunci ini saat enable
            .commit()
        val m = manager()
        assertTrue(m.state.value.locked)
        assertIs<UnlockResult.Success>(m.unlock("2468"))
        assertEquals(LockPolicy.PIN_HASH_VERSION, prefs.prefs.getInt("lock_hash_v", 0))
        val stored = android.util.Base64.decode(prefs.prefs.getString("lock_hash", "")!!, android.util.Base64.NO_WRAP)
        assertFalse(stored.contentEquals(legacyHash), "hash harus sudah format baru setelah unlock")
        assertIs<UnlockResult.Success>(manager().unlock("2468"), "PIN tetap bisa dibuka setelah migrasi")
    }

    /** D-04: menghapus catatan gagal tidak mereset jeda — justru memicu jeda sekali. */
    @Test fun deletingAttemptsRecordDoesNotResetCooldown() = runTest {
        manager().enable("246810", 1)
        val m = manager()
        repeat(5) { m.unlock("000000") }
        assertIs<UnlockResult.CoolingDown>(m.unlock("246810"))
        prefs.prefs.edit().remove("lock_attempts_v2").remove("lock_failed").remove("lock_cooldown_until").commit()
        val relaunched = manager()
        assertIs<UnlockResult.CoolingDown>(relaunched.unlock("246810"), "catatan gagal yang hilang = dicurigai direset")
        clock += LockPolicy.COOLDOWN_MS
        assertIs<UnlockResult.Success>(relaunched.unlock("246810"), "jeda tamper hanya sekali, bukan kunci permanen")
    }

    /** D-04: hash hilang tapi salt masih ada = gagal tertutup, bukan buka diam-diam. */
    @Test fun partialPinStateFailsClosed() = runTest {
        manager().enable("246810", 1)
        prefs.prefs.edit().remove("lock_hash").commit()
        val m = manager()
        assertTrue(m.state.value.locked)
        assertTrue(m.state.value.config?.corrupt == true)
        assertIs<UnlockResult.Corrupt>(m.unlock("246810"))
    }

    /** D-04: lockout kedua jedanya dua kali lipat (30 dtk → 60 dtk). */
    @Test fun secondLockoutCooldownDoubles() = runTest {
        manager().enable("246810", 1)
        val m = manager()
        repeat(5) { m.unlock("000000") }
        clock += LockPolicy.COOLDOWN_MS
        repeat(5) { m.unlock("000000") }
        val result = m.unlock("246810")
        assertIs<UnlockResult.CoolingDown>(result)
        assertEquals(clock + 60_000L, result.untilMillis)
    }

    private object FakeOwner : androidx.lifecycle.LifecycleOwner {
        override val lifecycle get() = androidx.lifecycle.LifecycleRegistry.createUnsafe(this)
    }
}
