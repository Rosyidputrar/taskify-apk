package com.rosyidputrar.taskify.core.store.device

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * D-06: enkripsi kolom teks sensitif log notifikasi (`title`, `text`, `conversation`,
 * `money_party`, `money_source`). Bukan enkripsi seluruh database — SQLCipher ditolak untuk
 * ronde ini karena butuh dependensi + migrasi + risiko performa/startup yang tidak sebanding
 * (tabel `notif_log` lokal saja, tidak disinkron; retensi 7 hari). Kolom angka (`money_amount`)
 * dan metadata (paket, kategori, waktu) tetap plaintext — hanya isi yang dibaca manusia yang
 * dienkripsi.
 */
interface NotifLogCrypto {
    fun encrypt(plain: String): String
    fun decrypt(cipher: String): String
}

/** Tanpa enkripsi — dipakai tes lama & sebagai fallback bila kunci Keystore tidak tersedia. */
object PlainNotifLogCrypto : NotifLogCrypto {
    override fun encrypt(plain: String) = plain
    override fun decrypt(cipher: String) = cipher
}

/**
 * AES-256-GCM murni (javax.crypto, bisa dites di JVM). Format: `v1:` + Base64(iv 12 byte ||
 * ciphertext+tag). Baris lama tanpa awalan `v1:` dianggap plaintext dan dikembalikan apa adanya,
 * jadi migrasi berjalan bertahap sampai baris lama ter-prune (retensi 7 hari).
 */
class AesGcmNotifLogCrypto(private val key: SecretKey) : NotifLogCrypto {
    override fun encrypt(plain: String): String {
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        }
        val out = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.getEncoder().encodeToString(iv + out)
    }

    override fun decrypt(cipher: String): String {
        if (!cipher.startsWith(PREFIX)) return cipher // baris lama plaintext
        val combined = Base64.getDecoder().decode(cipher.removePrefix(PREFIX))
        require(combined.size > IV_BYTES) { "Data log notifikasi rusak." }
        val c = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, combined, 0, IV_BYTES))
        }
        return c.doFinal(combined, IV_BYTES, combined.size - IV_BYTES).toString(Charsets.UTF_8)
    }

    companion object {
        const val PREFIX = "v1:"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }
}
