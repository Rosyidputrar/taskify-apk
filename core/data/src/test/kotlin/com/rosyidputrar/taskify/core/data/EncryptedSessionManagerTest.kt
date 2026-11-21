package com.rosyidputrar.taskify.core.data

import io.github.jan.supabase.auth.exception.NoSessionFoundException
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-05: sesi disimpan terenkripsi (token tidak tampil plaintext), migrasi dari sesi lama
 * plaintext berjalan sekali, hapus membersihkan keduanya, sesi rusak = tidak ada sesi.
 * Keystore sendiri tidak dites di JVM — hanya bisa dibuktikan di HP (lihat status D-05).
 */
class EncryptedSessionManagerTest {
    private val json = Json { ignoreUnknownKeys = true }

    /** JSON persis seperti yang ditulis SettingsSessionManager bawaan (serializer yang sama; perhatikan `expiresAt` camelCase). */
    private val sessionJson = """{"access_token":"tok-akses-rahasia","refresh_token":"tok-refresh-rahasia","provider_token":null,"provider_refresh_token":null,"expires_in":3600,"token_type":"bearer","user":{"id":"user-1","aud":"authenticated","email":"a@b.c"},"type":"bearer","expiresAt":"2030-01-01T00:00:00Z"}"""
    private val session: UserSession = json.decodeFromString(UserSession.serializer(), sessionJson)

    private class MapStore : SessionStore {
        var value: String? = null
        override fun get(): String? = value
        override fun put(value: String) {
            this.value = value
        }

        override fun remove() {
            value = null
        }
    }

    private class FakeLegacy(var value: String? = null) : LegacySessionReader {
        var cleared = false
        override fun read(): String? = value
        override fun clear() {
            cleared = true
            value = null
        }
    }

    private fun crypto() = AesGcmSessionCrypto(SecretKeySpec(ByteArray(32) { it.toByte() }, "AES"))

    private fun manager(store: SessionStore = MapStore(), legacy: LegacySessionReader? = null) =
        EncryptedSessionManager(store, crypto(), legacy)

    @Test fun saveStoresEncryptedAndLoadRoundTrips() = runBlocking {
        val store = MapStore()
        val m = manager(store)
        m.saveSession(session)
        val stored = store.value!!
        assertTrue(stored.startsWith("v1:"), "sesi harus tersimpan terenkripsi")
        assertFalse(stored.contains("tok-akses-rahasia"), "access token tidak boleh tampil plaintext")
        assertFalse(stored.contains("tok-refresh-rahasia"), "refresh token tidak boleh tampil plaintext")
        assertEquals(session, m.loadSession())
    }

    @Test fun legacyPlaintextSessionIsMigratedOnce() = runBlocking {
        val store = MapStore()
        val legacy = FakeLegacy(sessionJson)
        val m = manager(store, legacy)
        assertEquals(session, m.loadSession())
        assertTrue(store.value!!.startsWith("v1:"), "setelah migrasi, store berisi ciphertext")
        assertTrue(legacy.cleared, "kunci legacy dihapus setelah migrasi")
        assertNull(legacy.value)
        // Baca kedua tetap dari store terenkripsi (bukan legacy lagi).
        assertEquals(session, m.loadSession())
    }

    @Test fun deleteRemovesEncryptedAndLegacy() {
        runBlocking {
            val store = MapStore()
            val legacy = FakeLegacy(sessionJson)
            val m = manager(store, legacy)
            m.saveSession(session)
            assertTrue(legacy.cleared, "save juga membersihkan legacy")
            m.deleteSession()
            assertNull(store.value)
            assertFailsWith<NoSessionFoundException> { m.loadSession() }
        }
    }

    @Test fun emptyStoreLoadsNothing() {
        runBlocking { assertFailsWith<NoSessionFoundException> { manager().loadSession() } }
    }

    @Test fun corruptCiphertextLoadsNothing() {
        runBlocking {
            val store = MapStore()
            store.value = "v1:!!!bukan-base64!!!"
            assertFailsWith<NoSessionFoundException> { manager(store).loadSession() }
        }
    }

    @Test fun legacyKeyMatchesSupabaseDefault() {
        assertEquals(
            "sb-https:--xyz-supabase-co-session",
            legacySessionKey("https://xyz.supabase.co"),
        )
        assertEquals(
            "sb-https:--xyz-supabase-co-session",
            legacySessionKey("https://xyz.supabase.co/"),
        )
    }

    @Test fun cryptoRoundTripsAndDetectsTampering() {
        val c = crypto()
        val cipher = c.encrypt("sesi-penting")
        assertEquals("sesi-penting", c.decrypt(cipher))
        assertFailsWith<Exception> { c.decrypt(cipher.dropLast(4) + "AAAA") }
    }

    private class BrokenCrypto : SessionCrypto {
        override fun encrypt(plain: String): String = throw java.security.InvalidKeyException("Keystore operation failed")
        override fun decrypt(cipher: String): String = throw java.security.InvalidKeyException("Keystore operation failed")
    }

    @Test fun saveFailureNeverBreaksSignInAndDropsStaleSession() = runBlocking {
        val store = MapStore().apply { value = "v1:sesi-lama" }
        val errors = mutableListOf<Throwable>()
        val m = EncryptedSessionManager(store, BrokenCrypto(), null, onSaveError = { errors += it })
        m.saveSession(session) // tidak melempar → login tidak menggantung
        assertEquals(1, errors.size)
        assertNull(store.value, "sesi lama yang tak cocok lagi dibuang")
        assertFailsWith<NoSessionFoundException> { m.loadSession() }
        Unit
    }

    @Test fun recoveringCryptoRegeneratesKeyOnce() {
        val good = SecretKeySpec(ByteArray(32) { 7 }, "AES")
        val bad = SecretKeySpec(ByteArray(3) { 1 }, "AES") // panjang kunci tidak sah → encrypt gagal
        var resets = 0
        var key: javax.crypto.SecretKey = bad
        val c = RecoveringSessionCrypto(keyProvider = { key }, resetKey = { resets++; key = good })
        val cipher = c.encrypt("halo")
        assertEquals(1, resets)
        assertEquals("halo", c.decrypt(cipher))
        // Kunci tetap rusak setelah reset → error asli dilempar (dengan percobaan pertama terlampir).
        val stuck = RecoveringSessionCrypto(keyProvider = { bad }, resetKey = {})
        val e = assertFailsWith<Exception> { stuck.encrypt("x") }
        assertEquals(1, e.suppressed.size)
    }

    /**
     * Mensimulasikan aturan AndroidKeyStore (`randomizedEncryptionRequired = true`):
     * menolak caller-provided IV (`GCMParameterSpec`) pada `ENCRYPT_MODE` dengan
     * `InvalidAlgorithmParameterException("Caller-provided IV not permitted")`.
     */
    private fun androidKeyStoreLikeCipher(): Cipher {
        val delegate = Cipher.getInstance("AES/GCM/NoPadding")
        val spi = object : CipherSpi() {
            override fun engineSetMode(mode: String?) = Unit
            override fun engineSetPadding(padding: String?) = Unit
            override fun engineGetBlockSize(): Int = delegate.blockSize
            override fun engineGetOutputSize(inputLen: Int): Int = delegate.getOutputSize(inputLen)
            override fun engineGetIV(): ByteArray? = delegate.iv
            override fun engineGetParameters(): AlgorithmParameters? = delegate.parameters

            override fun engineInit(opmode: Int, key: Key?, random: SecureRandom?) {
                delegate.init(opmode, key, random)
            }

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

    @Test fun saveAndLoadWorkUnderAndroidKeyStoreNoCallerIvRule() = runBlocking {
        val key = SecretKeySpec(ByteArray(32) { (it + 3).toByte() }, "AES")
        val strictCrypto = AesGcmSessionCrypto(key, cipherFactory = ::androidKeyStoreLikeCipher)
        val store = MapStore()
        val errors = mutableListOf<Throwable>()
        val manager = EncryptedSessionManager(store, strictCrypto, legacy = null, onSaveError = { errors += it })

        manager.saveSession(session)
        assertTrue(errors.isEmpty(), "saveSession tidak boleh gagal karena caller-provided IV: $errors")
        val firstCipher = store.value!!
        assertTrue(firstCipher.startsWith("v1:"))
        assertEquals(session, manager.loadSession())

        // Enkripsi ulang harus menghasilkan IV acak baru (ciphertext berbeda).
        manager.saveSession(session)
        assertTrue(errors.isEmpty())
        assertNotEquals(firstCipher, store.value)
        assertEquals(session, manager.loadSession())
    }
}
