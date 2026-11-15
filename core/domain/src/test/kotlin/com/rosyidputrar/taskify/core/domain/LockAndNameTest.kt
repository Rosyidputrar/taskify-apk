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
        assertTrue(LockPolicy.isValidPin("1234"))
        assertTrue(LockPolicy.isValidPin("12345678"))
        assertFalse(LockPolicy.isValidPin("123"))
        assertFalse(LockPolicy.isValidPin("123456789"))
        assertFalse(LockPolicy.isValidPin("12a4"))
    }

    @Test fun fiveFailuresStartCooldown() {
        var a = LockAttempts()
        repeat(4) { a = LockPolicy.registerFailure(a, now = 0) }
        assertEquals(4, a.failedAttempts)
        assertNull(a.cooldownUntil)
        a = LockPolicy.registerFailure(a, now = 1_000)
        assertEquals(LockAttempts(0, 1_000 + LockPolicy.COOLDOWN_MS), a)
        assertTrue(a.coolingDown(1_000))
        assertFalse(a.coolingDown(1_000 + LockPolicy.COOLDOWN_MS))
    }

    @Test fun storedCooldownIsClampedAndExpiredDropped() {
        assertEquals(10_000 + LockPolicy.COOLDOWN_MS, LockPolicy.sanitize(LockAttempts(0, Long.MAX_VALUE), 10_000).cooldownUntil)
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
        val hash = LockPolicy.hash(mac, salt, "2468")
        assertTrue(LockPolicy.verify(mac, salt, "2468", hash))
        assertFalse(LockPolicy.verify(mac, salt, "2469", hash))
        assertFalse(LockPolicy.verify(mac, ByteArray(16), "2468", hash))
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
