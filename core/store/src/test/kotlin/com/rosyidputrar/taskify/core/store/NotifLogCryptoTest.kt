package com.rosyidputrar.taskify.core.store

import com.rosyidputrar.taskify.core.store.device.AesGcmNotifLogCrypto
import org.junit.Test
import java.security.AlgorithmParameters
import java.security.InvalidAlgorithmParameterException
import java.security.Key
import java.security.SecureRandom
import java.security.spec.AlgorithmParameterSpec
import javax.crypto.Cipher
import javax.crypto.CipherSpi
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class NotifLogCryptoTest {
    private val key = SecretKeySpec(ByteArray(32) { (it + 11).toByte() }, "AES")

    private fun androidKeyStoreLikeCipher(): Cipher {
        val delegate = Cipher.getInstance("AES/GCM/NoPadding")
        val spi = object : CipherSpi() {
            override fun engineSetMode(mode: String?) = Unit
            override fun engineSetPadding(padding: String?) = Unit
            override fun engineGetBlockSize(): Int = delegate.blockSize
            override fun engineGetOutputSize(inputLen: Int): Int = delegate.getOutputSize(inputLen)
            override fun engineGetIV(): ByteArray? = delegate.iv
            override fun engineGetParameters(): AlgorithmParameters? = delegate.parameters
            override fun engineInit(opmode: Int, key: Key?, random: SecureRandom?) = delegate.init(opmode, key, random)
            override fun engineInit(opmode: Int, key: Key?, params: AlgorithmParameterSpec?, random: SecureRandom?) {
                if (opmode == Cipher.ENCRYPT_MODE && params != null) {
                    throw InvalidAlgorithmParameterException("Caller-provided IV not permitted")
                }
                delegate.init(opmode, key, params, random)
            }
            override fun engineInit(opmode: Int, key: Key?, params: AlgorithmParameters?, random: SecureRandom?) {
                if (opmode == Cipher.ENCRYPT_MODE && params != null) {
                    throw InvalidAlgorithmParameterException("Caller-provided IV not permitted")
                }
                delegate.init(opmode, key, params, random)
            }
            override fun engineUpdate(input: ByteArray?, inputOffset: Int, inputLen: Int): ByteArray =
                delegate.update(input, inputOffset, inputLen) ?: ByteArray(0)
            override fun engineUpdate(input: ByteArray?, inputOffset: Int, inputLen: Int, output: ByteArray?, outputOffset: Int): Int =
                delegate.update(input, inputOffset, inputLen, output, outputOffset)
            override fun engineDoFinal(input: ByteArray?, inputOffset: Int, inputLen: Int): ByteArray =
                delegate.doFinal(input, inputOffset, inputLen)
            override fun engineDoFinal(input: ByteArray?, inputOffset: Int, inputLen: Int, output: ByteArray?, outputOffset: Int): Int =
                delegate.doFinal(input, inputOffset, inputLen, output, outputOffset)
        }
        return object : Cipher(spi, delegate.provider, "AES/GCM/NoPadding") {}
    }

    @Test fun encryptAndDecryptWorkWithoutCallerProvidedIv() {
        val crypto = AesGcmNotifLogCrypto(key, cipherFactory = ::androidKeyStoreLikeCipher)
        val first = crypto.encrypt("Transfer Rp50.000 dari BCA")
        val second = crypto.encrypt("Transfer Rp50.000 dari BCA")

        assertTrue(first.startsWith("v1:"))
        assertFalse(first.contains("Transfer"))
        assertNotEquals(first, second, "tiap enkripsi harus memakai IV acak baru")
        assertEquals("Transfer Rp50.000 dari BCA", crypto.decrypt(first))
        assertEquals("Transfer Rp50.000 dari BCA", crypto.decrypt(second))
    }

    @Test fun legacyPlaintextPassesThroughAndTamperingFails() {
        val crypto = AesGcmNotifLogCrypto(key)
        assertEquals("baris lama tanpa enkripsi", crypto.decrypt("baris lama tanpa enkripsi"))
        val encrypted = crypto.encrypt("rahasia")
        assertFailsWith<Exception> { crypto.decrypt(encrypted.dropLast(4) + "AAAA") }
    }
}
