package com.rosyidputrar.taskify.core.domain.lock

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Kunci aplikasi PIN (per perangkat, tidak disinkron). Ini kunci TAMPILAN, bukan enkripsi data.
 * 5 percobaan gagal → jeda 30 detik, lalu jeda BERLIPAT (60 dtk, 2 mnt, 4 mnt, maks. 8 mnt).
 * Hitungan disimpan agar membunuh aplikasi tidak mereset batas.
 *
 * D-04: PIN baru minimal 6 digit; hash PIN diiterasi (KDF lambat) supaya tebakan massal mahal;
 * hitungan gagal disimpan dengan tag integritas (lihat LockManager) supaya tidak bisa direset
 * dengan menghapus kunci preferensi.
 */
object LockPolicy {
    const val MAX_ATTEMPTS = 5
    const val COOLDOWN_MS = 30_000L
    const val MAX_COOLDOWN_MS = 8 * 60_000L
    val AUTO_LOCK_OPTIONS = listOf(0, 1, 5, 15, 60)
    const val DEFAULT_AUTO_LOCK_MINUTES = 5

    /** PIN baru: 6–8 digit. PIN lama 4–5 digit tetap bisa dibuka (verifikasi tidak memeriksa panjang). */
    const val MIN_PIN_LENGTH = 6
    const val MAX_PIN_LENGTH = 8

    /** Versi format hash: 1 = MAC sekali (lama), 2 = MAC Keystore + iterasi software (baru). */
    const val PIN_HASH_VERSION = 2

    /**
     * Iterasi HMAC-SHA256 software setelah satu MAC Keystore. Satu percobaan unlock ≈ 0,1–0,3 dtk
     * di HP (sekali per buka kunci, tidak terasa); menebak seluruh ruang PIN 4 digit (10.000)
     * butuh puluhan menit walau penyerang bisa memanggil kunci Keystore dari proses, dan ruang
     * 6 digit praktis tidak bisa ditebak habis. Iterasi memakai kunci = keluaran MAC Keystore,
     * jadi salinan berkas preferensi saja tetap tidak cukup untuk menebak offline.
     */
    const val PIN_HASH_ITERATIONS = 100_000

    fun isValidPin(pin: String): Boolean = pin.length in MIN_PIN_LENGTH..MAX_PIN_LENGTH && pin.all { it in '0'..'9' }

    /**
     * Jeda yang sudah lewat dibuang; jeda tersimpan dijepit ke now + MAX_COOLDOWN_MS supaya jam
     * perangkat yang pernah maju jauh tidak mengunci pengguna berbulan-bulan.
     */
    fun sanitize(attempts: LockAttempts, now: Long): LockAttempts = LockAttempts(
        failedAttempts = attempts.failedAttempts.coerceAtLeast(0),
        cooldownUntil = attempts.cooldownUntil?.takeIf { it > now }?.coerceAtMost(now + MAX_COOLDOWN_MS),
        lockouts = attempts.lockouts.coerceAtLeast(0),
    )

    /** Jeda untuk lockout ke-[lockouts] (1 = pertama): 30 dtk, 60 dtk, 2 mnt, 4 mnt, maks. 8 mnt. */
    fun cooldownFor(lockouts: Int): Long {
        var ms = COOLDOWN_MS
        repeat((lockouts - 1).coerceAtLeast(0).coerceAtMost(4)) { ms *= 2 }
        return ms.coerceAtMost(MAX_COOLDOWN_MS)
    }

    fun registerFailure(current: LockAttempts, now: Long): LockAttempts {
        val failed = current.failedAttempts + 1
        return if (failed >= MAX_ATTEMPTS) {
            val lockouts = current.lockouts + 1
            LockAttempts(failedAttempts = 0, cooldownUntil = now + cooldownFor(lockouts), lockouts = lockouts)
        } else {
            current.copy(failedAttempts = failed)
        }
    }

    /** Kunci lagi setelah aplikasi di latar ≥ [autoLockMinutes] (0 = segera). */
    fun shouldLockAfterBackground(hiddenAt: Long, now: Long, autoLockMinutes: Int): Boolean =
        now - hiddenAt >= autoLockMinutes * 60_000L

    /**
     * Hash PIN v2 = iterasi HMAC-SHA256 (kunci = MAC Keystore atas `salt ‖ PIN`).
     * Format lama (v1) = satu kali `MAC(salt ‖ PIN)` — masih diverifikasi untuk migrasi.
     */
    fun hash(mac: (ByteArray) -> ByteArray, salt: ByteArray, pin: String, iterations: Int = PIN_HASH_ITERATIONS): ByteArray {
        val seed = mac(salt + pin.toByteArray(Charsets.UTF_8))
        try {
            return iterate(seed, salt, iterations)
        } finally {
            seed.fill(0)
        }
    }

    /** Hash lama (v1): satu kali MAC — hanya untuk verifikasi PIN yang dibuat sebelum D-04. */
    fun hashLegacy(mac: (ByteArray) -> ByteArray, salt: ByteArray, pin: String): ByteArray =
        mac(salt + pin.toByteArray(Charsets.UTF_8))

    /** PBKDF2 satu blok (HMAC-SHA256, XOR akumulasi) dengan kunci [seed] atas pesan [salt]. */
    fun iterate(seed: ByteArray, salt: ByteArray, iterations: Int): ByteArray {
        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec(seed, "HmacSHA256"))
        var u = hmac.doFinal(salt)
        val out = u.copyOf()
        for (i in 1 until iterations) {
            u = hmac.doFinal(u)
            for (j in out.indices) out[j] = (out[j].toInt() xor u[j].toInt()).toByte()
        }
        return out
    }

    fun verify(
        mac: (ByteArray) -> ByteArray,
        salt: ByteArray,
        pin: String,
        expected: ByteArray,
        version: Int = PIN_HASH_VERSION,
        iterations: Int = PIN_HASH_ITERATIONS,
    ): Boolean {
        val candidate = if (version == 1) hashLegacy(mac, salt, pin) else hash(mac, salt, pin, iterations)
        return MessageDigest.isEqual(candidate, expected)
    }
}

/** [lockouts] = berapa kali sudah masuk jeda (menentukan besar jeda berikutnya). */
data class LockAttempts(val failedAttempts: Int = 0, val cooldownUntil: Long? = null, val lockouts: Int = 0) {
    fun coolingDown(now: Long): Boolean = cooldownUntil != null && cooldownUntil > now
}
