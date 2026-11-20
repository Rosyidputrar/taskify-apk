package com.rosyidputrar.taskify.core.domain

import com.rosyidputrar.taskify.core.domain.auth.cleanDisplayName
import com.rosyidputrar.taskify.core.domain.auth.homeGreeting
import com.rosyidputrar.taskify.core.domain.auth.loginGreeting
import com.rosyidputrar.taskify.core.domain.lock.LockAttempts
import com.rosyidputrar.taskify.core.domain.lock.LockPolicy
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LockAndNameTest {
    @Test fun pinValidity() {
        // D-04: PIN baru minimal 6 digit; 4–5 digit hanya berlaku untuk PIN lama yang sudah ada.
        assertFalse(LockPolicy.isValidPin("1234"))
        assertFalse(LockPolicy.isValidPin("12345"))
        assertTrue(LockPolicy.isValidPin("123456"))
        assertTrue(LockPolicy.isValidPin("12345678"))
        assertFalse(LockPolicy.isValidPin("123"))
        assertFalse(LockPolicy.isValidPin("123456789"))
        assertFalse(LockPolicy.isValidPin("12a456"))
    }

    @Test fun fiveFailuresStartCooldown() {
        var a = LockAttempts()
        repeat(4) { a = LockPolicy.registerFailure(a, now = 0) }
        assertEquals(4, a.failedAttempts)
        assertNull(a.cooldownUntil)
        a = LockPolicy.registerFailure(a, now = 1_000)
        assertEquals(LockAttempts(0, 1_000 + LockPolicy.COOLDOWN_MS, 1), a)
        assertTrue(a.coolingDown(1_000))
        assertFalse(a.coolingDown(1_000 + LockPolicy.COOLDOWN_MS))
    }

    /** D-04: jeda berlipat tiap lockout — 30 dtk, 60 dtk, 2 mnt, 4 mnt, maks. 8 mnt. */
    @Test fun cooldownEscalatesPerLockout() {
        assertEquals(30_000L, LockPolicy.cooldownFor(1))
        assertEquals(60_000L, LockPolicy.cooldownFor(2))
        assertEquals(120_000L, LockPolicy.cooldownFor(3))
        assertEquals(240_000L, LockPolicy.cooldownFor(4))
        assertEquals(480_000L, LockPolicy.cooldownFor(5))
        assertEquals(LockPolicy.MAX_COOLDOWN_MS, LockPolicy.cooldownFor(99))
        var a = LockAttempts()
        repeat(5) { a = LockPolicy.registerFailure(a, now = 0) }
        assertEquals(30_000L, a.cooldownUntil)
        // Lockout kedua (setelah jeda pertama lewat) memakai jeda 60 detik.
        a = LockPolicy.sanitize(a, now = 30_000)
        repeat(5) { a = LockPolicy.registerFailure(a, now = 30_000) }
        assertEquals(30_000 + 60_000L, a.cooldownUntil)
        assertEquals(2, a.lockouts)
    }

    @Test fun storedCooldownIsClampedAndExpiredDropped() {
        assertEquals(10_000 + LockPolicy.MAX_COOLDOWN_MS, LockPolicy.sanitize(LockAttempts(0, Long.MAX_VALUE), 10_000).cooldownUntil)
        assertNull(LockPolicy.sanitize(LockAttempts(2, 5_000), 10_000).cooldownUntil)
    }

    @Test fun autoLockAfterBackground() {
        assertTrue(LockPolicy.shouldLockAfterBackground(0, 0, 0))
        assertFalse(LockPolicy.shouldLockAfterBackground(0, 59_999, 1))
        assertTrue(LockPolicy.shouldLockAfterBackground(0, 60_000, 1))
    }

    @Test fun pinHashVerifies() {
        val mac: (ByteArray) -> ByteArray = { Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(ByteArray(32) { 7 }, "HmacSHA256")) }.doFinal(it) }
        val salt = ByteArray(16) { it.toByte() }
        val hash = LockPolicy.hash(mac, salt, "246810", iterations = 1_000)
        assertTrue(LockPolicy.verify(mac, salt, "246810", hash, iterations = 1_000))
        assertFalse(LockPolicy.verify(mac, salt, "246811", hash, iterations = 1_000))
        assertFalse(LockPolicy.verify(mac, ByteArray(16), "246810", hash, iterations = 1_000))
    }

    /** D-04: hash lama (v1, sekali MAC) tetap diverifikasi — tapi hash baru tidak sama dengan v1. */
    @Test fun legacyHashStillVerifiesAsVersion1() {
        val mac: (ByteArray) -> ByteArray = { Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(ByteArray(32) { 7 }, "HmacSHA256")) }.doFinal(it) }
        val salt = ByteArray(16) { it.toByte() }
        val legacy = LockPolicy.hashLegacy(mac, salt, "2468")
        assertTrue(LockPolicy.verify(mac, salt, "2468", legacy, version = 1))
        assertFalse(LockPolicy.verify(mac, salt, "2468", legacy, version = 2, iterations = 1_000))
        val fresh = LockPolicy.hash(mac, salt, "2468", iterations = 1_000)
        assertFalse(fresh.contentEquals(legacy))
        assertTrue(LockPolicy.verify(mac, salt, "2468", fresh, version = 2, iterations = 1_000))
    }

    @Test fun displayNameCleaning() {
        assertEquals("Rosyid Putra", cleanDisplayName("  Rosyid \n  Putra "))
        assertEquals("", cleanDisplayName(null))
        assertEquals(60, cleanDisplayName("a".repeat(100)).length)
        assertEquals("AB", cleanDisplayName("A\u0007B"))
    }

    @Test fun greetings() {
        assertEquals("Selamat datang di Taskify", loginGreeting(null))
        assertEquals("Selamat datang kembali, Ana", loginGreeting("Ana"))
        assertEquals("Selamat pagi, Ana", homeGreeting("Selamat pagi", "Ana"))
        assertEquals("Selamat pagi", homeGreeting("Selamat pagi", ""))
    }
}
