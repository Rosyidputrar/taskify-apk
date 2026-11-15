package com.rosyidputrar.taskify.core.domain.lock

import java.security.MessageDigest

/**
 * Kunci aplikasi PIN (per perangkat, tidak disinkron). Ini kunci TAMPILAN, bukan enkripsi data.
 * 5 percobaan gagal → jeda 30 detik. Hitungan disimpan agar membunuh aplikasi tidak mereset batas.
 */
object LockPolicy {
    const val MAX_ATTEMPTS = 5
    const val COOLDOWN_MS = 30_000L
    val AUTO_LOCK_OPTIONS = listOf(0, 1, 5, 15, 60)
    const val DEFAULT_AUTO_LOCK_MINUTES = 5

    fun isValidPin(pin: String): Boolean = pin.length in 4..8 && pin.all { it in '0'..'9' }

    /**
     * Jeda yang sudah lewat dibuang; jeda tersimpan dijepit ke now + COOLDOWN_MS supaya jam
     * perangkat yang pernah maju jauh tidak mengunci pengguna berbulan-bulan.
     */
    fun sanitize(attempts: LockAttempts, now: Long): LockAttempts = LockAttempts(
        failedAttempts = attempts.failedAttempts.coerceAtLeast(0),
        cooldownUntil = attempts.cooldownUntil?.takeIf { it > now }?.coerceAtMost(now + COOLDOWN_MS),
    )

    fun registerFailure(current: LockAttempts, now: Long): LockAttempts {
        val failed = current.failedAttempts + 1
        return if (failed >= MAX_ATTEMPTS) {
            LockAttempts(failedAttempts = 0, cooldownUntil = now + COOLDOWN_MS)
        } else {
            current.copy(failedAttempts = failed)
        }
    }

    /** Kunci lagi setelah aplikasi di latar ≥ [autoLockMinutes] (0 = segera). */
    fun shouldLockAfterBackground(hiddenAt: Long, now: Long, autoLockMinutes: Int): Boolean =
        now - hiddenAt >= autoLockMinutes * 60_000L

    /**
     * Hash PIN = MAC(salt ‖ PIN). Di perangkat, MAC memakai kunci HMAC Android Keystore yang tidak
     * bisa diekspor, sehingga salinan file preferensi saja tidak cukup untuk menebak PIN offline.
     */
    fun hash(mac: (ByteArray) -> ByteArray, salt: ByteArray, pin: String): ByteArray =
        mac(salt + pin.toByteArray(Charsets.UTF_8))

    fun verify(mac: (ByteArray) -> ByteArray, salt: ByteArray, pin: String, expected: ByteArray): Boolean =
        MessageDigest.isEqual(hash(mac, salt, pin), expected)
}

data class LockAttempts(val failedAttempts: Int = 0, val cooldownUntil: Long? = null) {
    fun coolingDown(now: Long): Boolean = cooldownUntil != null && cooldownUntil > now
}
