package com.rosyidputrar.taskify.core.domain.sandi

import com.rosyidputrar.taskify.core.common.Hashes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.text.Normalizer
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Kriptografi Sandi — port BYTE-KOMPATIBEL `features/sandi/crypto.ts` webapp (diuji dengan vektor
 * yang dibuat kode webapp sendiri). Data yang dibuat di laptop terbuka di HP, dan sebaliknya.
 *
 *   master password ──PBKDF2-SHA256 (600 rb iterasi, salt acak)──► KEK ──AES-GCM──► membungkus DEK
 *   kunci pemulihan (128 bit acak) ──HKDF-SHA256──► KEK pemulihan ──AES-GCM──► membungkus DEK
 *   DEK (256 bit acak) ──AES-256-GCM, IV acak, AAD = user + id baris──► isi item/riwayat/konfigurasi
 *
 * PBKDF2 & HKDF ditulis sendiri di atas HmacSHA256 (bukan SecretKeyFactory) supaya pengodean
 * password (NFKC → UTF-8) identik di semua versi Android dan di JVM tes.
 */
object SandiCrypto {
    const val KEY_RECORD_VERSION = 1
    const val ITEM_VERSION = 1
    const val DEFAULT_ITERATIONS = 600_000
    const val MIN_ITERATIONS = 100_000
    const val MAX_ITERATIONS = 10_000_000
    private const val PAD_BLOCK = 256
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val DEK_BYTES = 32
    private const val RECOVERY_BYTES = 16

    private val random = SecureRandom()
    private val json = Json { ignoreUnknownKeys = true }
    private val b64 = Base64.getEncoder()
    private val b64d = Base64.getDecoder()

    fun toB64(bytes: ByteArray): String = b64.encodeToString(bytes)
    fun fromB64(value: String): ByteArray = b64d.decode(value)
    fun randomBytes(n: Int) = ByteArray(n).also { random.nextBytes(it) }

    // ------------------------------------------------------------------ label kunci

    /** 16 hex pertama SHA-256 dari `pw_keys.wrapped_key` — penanda generasi kunci data. */
    fun keyRef(wrappedKey: String): String = Hashes.sha256Hex16(wrappedKey)

    private val REF = Regex("^[0-9a-f]{16}$")
    private val FRAMED = Regex("^([0-9a-f]{16})\\.(.+)$", RegexOption.DOT_MATCHES_ALL)

    data class SplitEnc(val ref: String?, val body: String)

    fun splitEnc(enc: String): SplitEnc = FRAMED.find(enc)?.let { SplitEnc(it.groupValues[1], it.groupValues[2]) } ?: SplitEnc(null, enc)

    private fun frame(ref: String, sealed: ByteArray): String {
        if (!REF.matches(ref)) throw SandiException(SandiException.Code.Invalid, "Label kunci brankas tidak valid. Buka brankas lagi.")
        return "$ref.${toB64(sealed)}"
    }

    // ------------------------------------------------------------------ primitif

    private fun seal(key: ByteArray, plaintext: ByteArray, aad: String): ByteArray {
        val iv = randomBytes(IV_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        return iv + cipher.doFinal(plaintext)
    }

    /** null = gagal diautentikasi (kunci salah, data diubah, AAD tidak cocok). */
    private fun open(key: ByteArray, sealed: ByteArray, aad: String): ByteArray? {
        if (sealed.size <= IV_BYTES + 16) return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
            cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
            cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
        } catch (_: AEADBadTagException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    /** NFKC: "ｐａｓｓ" (lebar penuh) dan "pass" menghasilkan kunci yang sama di semua perangkat. */
    fun normalize(password: String): String = Normalizer.normalize(password, Normalizer.Form.NFKC)

    /** PBKDF2-HMAC-SHA256, panjang keluaran 32 byte (satu blok). */
    fun pbkdf2(password: ByteArray, salt: ByteArray, iterations: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(if (password.isEmpty()) EMPTY_KEY else password, "HmacSHA256"))
        mac.update(salt)
        mac.update(byteArrayOf(0, 0, 0, 1))
        var u = mac.doFinal()
        val out = u.copyOf()
        for (i in 1 until iterations) {
            u = mac.doFinal(u)
            for (j in out.indices) out[j] = (out[j].toInt() xor u[j].toInt()).toByte()
        }
        return out
    }

    /** HMAC dengan kunci kosong tidak diizinkan SecretKeySpec; HMAC(k="") = HMAC(k=0x00…) per RFC 2104. */
    private val EMPTY_KEY = ByteArray(64)

    /** HKDF-SHA256 (RFC 5869), keluaran 32 byte. */
    fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray): ByteArray {
        val extract = Mac.getInstance("HmacSHA256")
        extract.init(SecretKeySpec(if (salt.isEmpty()) ByteArray(32) else salt, "HmacSHA256"))
        val prk = extract.doFinal(ikm)
        val expand = Mac.getInstance("HmacSHA256")
        expand.init(SecretKeySpec(prk, "HmacSHA256"))
        expand.update(info)
        expand.update(1.toByte())
        return expand.doFinal()
    }

    private fun passwordKek(password: String, salt: ByteArray, iterations: Int): ByteArray =
        pbkdf2(normalize(password).toByteArray(Charsets.UTF_8), salt, iterations)

    private fun recoveryKek(recovery: ByteArray, salt: ByteArray): ByteArray =
        hkdf(recovery, salt, "taskify-sandi/recovery/v1".toByteArray(Charsets.UTF_8))

    private fun aadPassword(userId: String) = "taskify-sandi/key/v1/$userId"
    private fun aadRecovery(userId: String) = "taskify-sandi/recovery/v1/$userId"
    private fun aadItem(userId: String, itemId: String) = "taskify-sandi/item/v1/$userId/$itemId"
    private fun aadTotpLog(userId: String, rowId: String) = "taskify-sandi/totp-log/v1/$userId/$rowId"
    private fun aadConfig(userId: String, fileId: String, versionId: String) = "taskify-sandi/config/v1/$userId/$fileId/$versionId"

    private fun assertIterations(iterations: Int) {
        if (iterations < MIN_ITERATIONS || iterations > MAX_ITERATIONS) {
            throw SandiException(SandiException.Code.Invalid, "Pengaturan keamanan brankas tidak valid (iterasi di luar batas aman). Brankas tidak dibuka.")
        }
    }

    private fun assertSupported(record: KeyRecord) {
        if (record.version > KEY_RECORD_VERSION || record.kdf != "pbkdf2-sha256") {
            throw SandiException(SandiException.Code.Unsupported, "Versi brankas tidak didukung aplikasi ini. Perbarui aplikasi.")
        }
        assertIterations(record.iterations)
    }

    // ------------------------------------------------------------------ kunci pemulihan

    private const val CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    /** 16 byte → 26 karakter Crockford base32, dikelompokkan 4: "ABCD-EFGH-…-YZ". */
    fun formatRecoveryKey(bytes: ByteArray): String {
        var bits = 0
        var value = 0
        val out = StringBuilder()
        for (b in bytes) {
            value = (value shl 8) or (b.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                out.append(CROCKFORD[(value ushr (bits - 5)) and 31])
                bits -= 5
            }
            value = value and ((1 shl bits) - 1)
        }
        if (bits > 0) out.append(CROCKFORD[(value shl (5 - bits)) and 31])
        return out.chunked(4).joinToString("-")
    }

    /** Toleran huruf kecil, spasi, tanda hubung, salah baca O/0 dan I/L/1. null = tidak valid. */
    fun parseRecoveryKey(text: String): ByteArray? {
        val clean = text.uppercase().replace(Regex("[\\s-]"), "").replace('O', '0').replace(Regex("[IL]"), "1")
        if (clean.length != 26) return null
        var bits = 0
        var value = 0
        val out = ByteArray(RECOVERY_BYTES)
        var n = 0
        for (ch in clean) {
            val idx = CROCKFORD.indexOf(ch)
            if (idx < 0) return null
            value = (value shl 5) or idx
            bits += 5
            if (bits >= 8) {
                if (n < RECOVERY_BYTES) out[n++] = ((value ushr (bits - 8)) and 255).toByte()
                bits -= 8
                value = value and ((1 shl bits) - 1)
            }
        }
        return if (n == RECOVERY_BYTES) out else null
    }

    // ------------------------------------------------------------------ siklus brankas

    private fun wrapBoth(rawDek: ByteArray, password: String, userId: String, iterations: Int): Pair<KeyRecord, String> {
        assertIterations(iterations)
        val salt = randomBytes(16)
        val recoverySalt = randomBytes(16)
        val recovery = randomBytes(RECOVERY_BYTES)
        val kek = passwordKek(password, salt, iterations)
        val rkek = recoveryKek(recovery, recoverySalt)
        val record = KeyRecord(
            version = KEY_RECORD_VERSION,
            kdf = "pbkdf2-sha256",
            iterations = iterations,
            salt = toB64(salt),
            wrappedKey = toB64(seal(kek, rawDek, aadPassword(userId))),
            recoverySalt = toB64(recoverySalt),
            recoveryWrappedKey = toB64(seal(rkek, rawDek, aadRecovery(userId))),
        )
        val formatted = formatRecoveryKey(recovery)
        recovery.fill(0); kek.fill(0); rkek.fill(0)
        return record to formatted
    }

    /** Brankas baru: DEK acak, dibungkus master password dan kunci pemulihan. */
    fun createVault(password: String, userId: String, iterations: Int = DEFAULT_ITERATIONS): NewVault {
        val raw = randomBytes(DEK_BYTES)
        val (record, recoveryKey) = wrapBoth(raw, password, userId, iterations)
        return NewVault(record, Dek(raw), recoveryKey)
    }

    private fun unwrapWithPassword(record: KeyRecord, password: String, userId: String): ByteArray? {
        assertSupported(record)
        val kek = passwordKek(password, fromB64(record.salt), record.iterations)
        return open(kek, fromB64(record.wrappedKey), aadPassword(userId)).also { kek.fill(0) }
    }

    private fun unwrapWithRecovery(record: KeyRecord, recoveryText: String, userId: String): ByteArray? {
        assertSupported(record)
        val recovery = parseRecoveryKey(recoveryText) ?: return null
        val rkek = recoveryKek(recovery, fromB64(record.recoverySalt))
        recovery.fill(0)
        return open(rkek, fromB64(record.recoveryWrappedKey), aadRecovery(userId)).also { rkek.fill(0) }
    }

    /** null = master password salah. */
    fun unlockWithPassword(record: KeyRecord, password: String, userId: String): Dek? = unwrapWithPassword(record, password, userId)?.let(::Dek)

    /** null = kunci pemulihan salah atau salah format. */
    fun unlockWithRecoveryKey(record: KeyRecord, recoveryText: String, userId: String): Dek? = unwrapWithRecovery(record, recoveryText, userId)?.let(::Dek)

    /** Ganti master password: DEK baru + kunci pemulihan baru. null = sandi lama salah. */
    fun changePassword(record: KeyRecord, oldPassword: String, newPassword: String, userId: String, iterations: Int = DEFAULT_ITERATIONS): RewrittenVault? =
        rewrap(unwrapWithPassword(record, oldPassword, userId), record, newPassword, userId, iterations)

    /** Lupa master password: buka dengan kunci pemulihan, atur sandi + kunci pemulihan baru. */
    fun resetPasswordWithRecovery(record: KeyRecord, recoveryText: String, newPassword: String, userId: String, iterations: Int = DEFAULT_ITERATIONS): RewrittenVault? =
        rewrap(unwrapWithRecovery(record, recoveryText, userId), record, newPassword, userId, iterations)

    /** Kunci pemulihan baru (yang lama dicabut). Butuh master password. */
    fun rotateRecoveryKey(record: KeyRecord, password: String, userId: String, iterations: Int = DEFAULT_ITERATIONS): RewrittenVault? =
        rewrap(unwrapWithPassword(record, password, userId), record, password, userId, iterations)

    private fun rewrap(oldRaw: ByteArray?, from: KeyRecord, newPassword: String, userId: String, iterations: Int): RewrittenVault? {
        if (oldRaw == null) return null
        val newRaw = randomBytes(DEK_BYTES)
        val (record, recoveryKey) = wrapBoth(newRaw, newPassword, userId, iterations)
        return RewrittenVault(record, Dek(newRaw), recoveryKey, Dek(oldRaw), from.wrappedKey)
    }

    // ------------------------------------------------------------------ padding

    private fun pad(plain: ByteArray): ByteArray {
        val total = ((4 + plain.size + PAD_BLOCK - 1) / PAD_BLOCK) * PAD_BLOCK
        val out = ByteArray(total)
        ByteBuffer.wrap(out).putInt(plain.size)
        plain.copyInto(out, 4)
        return out
    }

    private fun unpad(padded: ByteArray): ByteArray? {
        if (padded.size < 4) return null
        val len = ByteBuffer.wrap(padded).int.toLong() and 0xffffffffL
        if (len > padded.size - 4) return null
        return padded.copyOfRange(4, 4 + len.toInt())
    }

    private fun sealJson(dek: Dek, value: JsonObject, aad: String, ref: String): String =
        frame(ref, seal(dek.bytes(), pad(value.toString().toByteArray(Charsets.UTF_8)), aad))

    private fun openJson(dek: Dek, enc: String, aad: String, what: String): JsonObject {
        val bytes = try { fromB64(splitEnc(enc).body) } catch (_: Exception) { throw SandiException(SandiException.Code.Corrupt, "Data $what rusak.") }
        val plain = open(dek.bytes(), bytes, aad)?.let(::unpad) ?: throw SandiException(SandiException.Code.Corrupt, "${what.replaceFirstChar { it.uppercase() }} tidak bisa dibuka dengan kunci ini.")
        return try {
            json.parseToJsonElement(plain.toString(Charsets.UTF_8)) as? JsonObject ?: throw IllegalStateException()
        } catch (_: Exception) {
            throw SandiException(SandiException.Code.Corrupt, "Isi $what tidak valid.")
        }
    }

    // ------------------------------------------------------------------ item

    fun encryptItem(dek: Dek, userId: String, itemId: String, payload: ItemPayload, ref: String): String =
        sealJson(dek, payload.toJson(), aadItem(userId, itemId), ref)

    fun decryptItem(dek: Dek, userId: String, itemId: String, enc: String): ItemPayload =
        ItemPayload.fromJson(openJson(dek, enc, aadItem(userId, itemId), "item"))

    fun encryptTotpLog(dek: Dek, userId: String, rowId: String, entry: TotpLogEntry, ref: String): String =
        sealJson(dek, entry.toJson(), aadTotpLog(userId, rowId), ref)

    fun decryptTotpLog(dek: Dek, userId: String, rowId: String, enc: String): TotpLogEntry =
        TotpLogEntry.fromJson(openJson(dek, enc, aadTotpLog(userId, rowId), "riwayat"))

    fun encryptConfig(dek: Dek, userId: String, fileId: String, versionId: String, payload: ConfigPayload, ref: String): String =
        sealJson(dek, payload.toJson(), aadConfig(userId, fileId, versionId), ref)

    fun decryptConfig(dek: Dek, userId: String, fileId: String, versionId: String, enc: String): ConfigPayload =
        ConfigPayload.fromJson(openJson(dek, enc, aadConfig(userId, fileId, versionId), "versi konfigurasi"))

    // ------------------------------------------------------------------ enkripsi ulang

    data class Reencrypted(val updated: List<EncryptedRow>, val skipped: List<String>)

    /**
     * Enkripsi ulang dari [from] ke [to]. Baris yang tidak bisa dibuka dengan [from] tidak diubah
     * (dicatat di `skipped`): menimpanya hanya menghilangkan satu-satunya salinan.
     */
    private fun <T> reencrypt(rows: List<EncryptedRow>, decrypt: (EncryptedRow) -> T, encrypt: (EncryptedRow, T) -> String): Reencrypted {
        val updated = mutableListOf<EncryptedRow>()
        val skipped = mutableListOf<String>()
        for (row in rows) {
            val value = try { decrypt(row) } catch (_: SandiException) { skipped += row.id; continue }
            updated += EncryptedRow(row.id, encrypt(row, value), row.fileId)
        }
        return Reencrypted(updated, skipped)
    }

    fun reencryptItems(rows: List<EncryptedRow>, from: Dek, to: Dek, userId: String, toRef: String) =
        reencrypt(rows, { decryptItem(from, userId, it.id, it.enc) }, { r, v -> encryptItem(to, userId, r.id, v, toRef) })

    fun reencryptTotpLog(rows: List<EncryptedRow>, from: Dek, to: Dek, userId: String, toRef: String) =
        reencrypt(rows, { decryptTotpLog(from, userId, it.id, it.enc) }, { r, v -> encryptTotpLog(to, userId, r.id, v, toRef) })

    fun reencryptConfigVersions(rows: List<EncryptedRow>, from: Dek, to: Dek, userId: String, toRef: String) =
        reencrypt(rows, { decryptConfig(from, userId, it.fileId!!, it.id, it.enc) }, { r, v -> encryptConfig(to, userId, r.fileId!!, r.id, v, toRef) })
}

class SandiException(val code: Code, message: String) : Exception(message) {
    enum class Code { Unsupported, Corrupt, Invalid }
}

/** Baris `pw_keys` (nama field mengikuti kolom). */
data class KeyRecord(
    val version: Int,
    val kdf: String,
    val iterations: Int,
    val salt: String,
    val wrappedKey: String,
    val recoverySalt: String,
    val recoveryWrappedKey: String,
)

/**
 * Kunci data brankas (DEK) di memori. Hanya ada selama brankas terbuka; [destroy] menolkan
 * byte-nya saat dikunci.
 */
class Dek(private val raw: ByteArray) {
    init { require(raw.size == 32) { "DEK harus 32 byte" } }
    internal fun bytes(): ByteArray = raw
    /** Salinan byte untuk dibungkus Keystore ("ingat perangkat"). Nolkan setelah dipakai. */
    fun export(): ByteArray = raw.copyOf()
    fun destroy() = raw.fill(0)
}

data class NewVault(val record: KeyRecord, val dek: Dek, val recoveryKey: String)

/** Hasil ganti kunci: DEK BARU + DEK lama; semua baris wajib dienkripsi ulang dalam satu transaksi. */
data class RewrittenVault(val record: KeyRecord, val dek: Dek, val recoveryKey: String, val previousDek: Dek, val previousWrappedKey: String)

/** [fileId] hanya untuk versi konfigurasi (bagian dari AAD). */
data class EncryptedRow(val id: String, val enc: String, val fileId: String? = null)

private fun JsonElement?.str(): String = (this as? JsonPrimitive)?.takeIf { it.isString }?.content ?: ""

/** Isi satu sandi. Semua field ikut terenkripsi (termasuk judul & URL). */
data class ItemPayload(
    val title: String = "",
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val notes: String = "",
    val favorite: Boolean = false,
    val totp: TotpConfig? = null,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("title", title); put("url", url); put("username", username); put("password", password); put("notes", notes)
        put("favorite", favorite)
        put("totp", totp?.toJson() ?: JsonNull)
    }

    companion object {
        /** Field yang hilang diberi nilai bawaan (kompatibel ke depan), sama dengan `parsePayload`. */
        fun fromJson(o: JsonObject) = ItemPayload(
            title = o["title"].str(), url = o["url"].str(), username = o["username"].str(), password = o["password"].str(),
            notes = o["notes"].str(), favorite = (o["favorite"] as? JsonPrimitive)?.booleanOrNull == true,
            totp = Totp.parseConfig(o["totp"]),
        )
    }
}

data class TotpLogEntry(val itemId: String, val name: String, val code: String, val usedAt: String) {
    fun toJson() = buildJsonObject { put("itemId", itemId); put("name", name); put("code", code); put("usedAt", usedAt) }

    companion object {
        fun fromJson(o: JsonObject) = TotpLogEntry(o["itemId"].str(), o["name"].str(), o["code"].str(), o["usedAt"].str())
    }
}

/** Isi satu versi berkas konfigurasi: teks mentah apa adanya + catatan perubahan. */
data class ConfigPayload(val text: String, val note: String = "") {
    fun toJson() = buildJsonObject { put("text", text); put("note", note) }

    companion object {
        fun fromJson(o: JsonObject) = ConfigPayload(o["text"].str(), o["note"].str())
    }
}
