package com.rosyidputrar.taskify.core.domain.sandi

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class DekConcurrencyTest {
    private val ref = SandiCrypto.keyRef("wrapped-key-stamp-1")

    @Test fun destroyedDekRefusesEncryptDecryptAndExport() {
        val raw = ByteArray(32) { (it + 1).toByte() }
        val dek = Dek(raw)
        val enc = SandiCrypto.encryptItem(dek, "u1", "i1", ItemPayload(title = "Bank", password = "secret"), ref)
        dek.destroy()

        assertEquals(SandiException.Code.Invalid, assertFailsWith<SandiException> {
            SandiCrypto.encryptItem(dek, "u1", "i2", ItemPayload(title = "X"), ref)
        }.code)
        assertEquals(SandiException.Code.Invalid, assertFailsWith<SandiException> {
            SandiCrypto.decryptItem(dek, "u1", "i1", enc)
        }.code)
        assertEquals(SandiException.Code.Invalid, assertFailsWith<SandiException> {
            dek.export()
        }.code)
    }

    @Test fun concurrentDestroyNeverEncryptsWithZeroKey() {
        val zeroDek = Dek(ByteArray(32))
        val encryptedWithZero = AtomicBoolean(false)
        val pool = Executors.newFixedThreadPool(2)
        try {
            repeat(50) { round ->
                val raw = ByteArray(32) { ((it + round + 1) and 0xFF).toByte() }
                val dek = Dek(raw)
                val start = CountDownLatch(1)
                val done = CountDownLatch(2)
                pool.execute {
                    start.await()
                    try {
                        repeat(20) { i ->
                            val enc = SandiCrypto.encryptItem(dek, "u1", "item-$i", ItemPayload(title = "T$i", password = "p$i"), ref)
                            // Jika enkripsi berhasil, ia TIDAK boleh pernah bisa didekripsi dengan kunci all-zero.
                            val openedByZero = runCatching { SandiCrypto.decryptItem(zeroDek, "u1", "item-$i", enc) }.isSuccess
                            if (openedByZero) encryptedWithZero.set(true)
                        }
                    } catch (_: SandiException) {
                        // Diharapkan setelah dek.destroy() dipanggil.
                    } finally {
                        done.countDown()
                    }
                }
                pool.execute {
                    start.await()
                    dek.destroy()
                    done.countDown()
                }
                start.countDown()
                done.await()
            }
        } finally {
            pool.shutdownNow()
        }
        assertFalse(encryptedWithZero.get(), "DEK yang sedang dihancurkan tidak boleh mengenkripsi dengan kunci all-zero")
    }
}
