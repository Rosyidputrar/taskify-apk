package com.rosyidputrar.taskify.core.domain

import com.rosyidputrar.taskify.core.domain.sandi.ConfigPayload
import com.rosyidputrar.taskify.core.domain.sandi.EncryptedRow
import com.rosyidputrar.taskify.core.domain.sandi.ItemPayload
import com.rosyidputrar.taskify.core.domain.sandi.KeyRecord
import com.rosyidputrar.taskify.core.domain.sandi.SandiCrypto
import com.rosyidputrar.taskify.core.domain.sandi.SandiException
import com.rosyidputrar.taskify.core.domain.sandi.TotpAlgorithm
import com.rosyidputrar.taskify.core.domain.sandi.TotpConfig
import com.rosyidputrar.taskify.core.domain.sandi.TotpLogEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonObject
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Kompatibilitas dua arah dengan webapp. `webapp-vectors.json` dibuat oleh `crypto.ts` webapp
 * APA ADANYA (dijalankan dengan Node); tes ini membukanya. Sebaliknya, `kotlin-vectors.json`
 * ditulis ke build/ untuk dibuka oleh `crypto.ts` (lihat docs/sandi-vectors.md).
 */
class SandiCryptoTest {
    private val v = Json.parseToJsonElement(javaClass.getResourceAsStream("/sandi/webapp-vectors.json")!!.bufferedReader().readText()).jsonObject
    private fun s(o: JsonObject, k: String) = o[k]!!.jsonPrimitive.content
    private val user = s(v, "user")
    private val password = s(v, "password")
    private val record = v["record"]!!.jsonObject.let {
        KeyRecord(it["version"]!!.jsonPrimitive.int, s(it, "kdf"), it["iterations"]!!.jsonPrimitive.int, s(it, "salt"), s(it, "wrapped_key"), s(it, "recovery_salt"), s(it, "recovery_wrapped_key"))
    }

    @Test fun opensVaultAndItemsCreatedByWebapp() {
        assertEquals(s(v, "ref"), SandiCrypto.keyRef(record.wrappedKey))
        assertNull(SandiCrypto.unlockWithPassword(record, "salah total", user))
        assertNull(SandiCrypto.unlockWithPassword(record, password, "user-lain")) // AAD terikat user
        val dek = assertNotNull(SandiCrypto.unlockWithPassword(record, password, user))
        // Lebar penuh "ｋｕｄａ" dinormalkan NFKC → "kuda": keduanya membuka brankas yang sama.
        assertNotNull(SandiCrypto.unlockWithPassword(record, password.replace("ｋｕｄａ", "kuda"), user))

        val item = v["item"]!!.jsonObject
        val p = SandiCrypto.decryptItem(dek, user, s(item, "id"), s(item, "enc"))
        assertEquals("GitHub", p.title)
        assertEquals("Zk9#mQ2\$vL7!pXr4 ünï", p.password)
        assertEquals("baris 1\nbaris 2 \"kutip\"", p.notes)
        assertTrue(p.favorite)
        assertEquals(TotpConfig("JBSWY3DPEHPK3PXP", 6, 30, TotpAlgorithm.SHA1), p.totp)
        assertNull(SandiCrypto.decryptItem(dek, user, "item-2", s(v["itemNoTotp"]!!.jsonObject, "enc")).totp)
        // Ciphertext tidak bisa dipindah ke baris lain (AAD = id baris).
        assertFailsWith<SandiException> { SandiCrypto.decryptItem(dek, user, "item-2", s(item, "enc")) }

        val log = SandiCrypto.decryptTotpLog(dek, user, "log-1", s(v["log"]!!.jsonObject, "enc"))
        assertEquals(TotpLogEntry("item-1", "GitHub", "123456", "2026-10-07T01:02:03.000Z"), log)
        val cfg = v["config"]!!.jsonObject
        assertEquals(ConfigPayload("A=1\n# komentar\nB=\"x y\"", "ganti URL"), SandiCrypto.decryptConfig(dek, user, "file-1", "ver-1", s(cfg, "enc")))
        assertFailsWith<SandiException> { SandiCrypto.decryptConfig(dek, user, "file-2", "ver-1", s(cfg, "enc")) }

        // Kunci pemulihan buatan webapp membuka DEK yang sama (toleran huruf kecil & O/0).
        val viaRecovery = assertNotNull(SandiCrypto.unlockWithRecoveryKey(record, s(v, "recoveryKey").lowercase().replace('0', 'o'), user))
        assertEquals("GitHub", SandiCrypto.decryptItem(viaRecovery, user, "item-1", s(item, "enc")).title)
    }

    @Test fun rotationReencryptsEverythingAndOldKeyStopsWorking() {
        val next = assertNotNull(SandiCrypto.changePassword(record, password, "frasa baru yang cukup panjang sekali", user, iterations = 100_000))
        assertNull(SandiCrypto.changePassword(record, "salah", "x", user, iterations = 100_000))
        val toRef = SandiCrypto.keyRef(next.record.wrappedKey)
        val rows = listOf(EncryptedRow("item-1", s(v["item"]!!.jsonObject, "enc")), EncryptedRow("rusak", "abc.def"))
        val r = SandiCrypto.reencryptItems(rows, next.previousDek, next.dek, user, toRef)
        assertEquals(listOf("rusak"), r.skipped)
        assertTrue(r.updated.single().enc.startsWith("$toRef."))
        assertEquals("GitHub", SandiCrypto.decryptItem(next.dek, user, "item-1", r.updated.single().enc).title)
        assertNull(SandiCrypto.unlockWithPassword(next.record, password, user))
        assertNotNull(SandiCrypto.unlockWithPassword(next.record, "frasa baru yang cukup panjang sekali", user))
        assertNull(SandiCrypto.unlockWithRecoveryKey(next.record, s(v, "recoveryKey"), user)) // kunci pemulihan lama dicabut
        assertNotNull(SandiCrypto.unlockWithRecoveryKey(next.record, next.recoveryKey, user))
        val cfgRows = listOf(EncryptedRow("ver-1", s(v["config"]!!.jsonObject, "enc"), "file-1"))
        assertEquals(1, SandiCrypto.reencryptConfigVersions(cfgRows, next.previousDek, next.dek, user, toRef).updated.size)
    }

    @Test fun guardsAndFormats() {
        assertFailsWith<SandiException> { SandiCrypto.unlockWithPassword(record.copy(iterations = 1_000), password, user) }
        assertFailsWith<SandiException> { SandiCrypto.unlockWithPassword(record.copy(kdf = "argon2id"), password, user) }
        assertFailsWith<SandiException> { SandiCrypto.createVault("x", user, iterations = 50_000) }
        val bytes = ByteArray(16) { it.toByte() }
        val text = SandiCrypto.formatRecoveryKey(bytes)
        assertEquals(26 + 6, text.length)
        assertTrue(SandiCrypto.parseRecoveryKey(text)!!.contentEquals(bytes))
        assertNull(SandiCrypto.parseRecoveryKey("ABCD"))
        assertEquals("ba7816bf8f01cfea", SandiCrypto.keyRef("abc"))
        assertEquals(SandiCrypto.SplitEnc(null, "lama"), SandiCrypto.splitEnc("lama"))
    }

    /** Vektor arah sebaliknya: dibuat Kotlin, diverifikasi dengan crypto.ts webapp (lihat docs). */
    @Test fun writesKotlinVectorsForWebapp() {
        val vault = SandiCrypto.createVault(password, user, iterations = 100_000)
        val ref = SandiCrypto.keyRef(vault.record.wrappedKey)
        val payload = ItemPayload("Bank BCA", "klikbca.com", "rosyid", "Rahasia#2026 ☕", "catatan", false, TotpConfig("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", 8, 60, TotpAlgorithm.SHA256))
        val out = buildJsonObject {
            put("user", user); put("password", password); put("recoveryKey", vault.recoveryKey)
            put("record", buildJsonObject {
                put("version", vault.record.version); put("kdf", vault.record.kdf); put("iterations", vault.record.iterations)
                put("salt", vault.record.salt); put("wrapped_key", vault.record.wrappedKey)
                put("recovery_salt", vault.record.recoverySalt); put("recovery_wrapped_key", vault.record.recoveryWrappedKey)
            })
            put("itemEnc", SandiCrypto.encryptItem(vault.dek, user, "k-item", payload, ref))
            put("logEnc", SandiCrypto.encryptTotpLog(vault.dek, user, "k-log", TotpLogEntry("k-item", "Bank", "654321", "2026-10-07T00:00:00.000Z"), ref))
            put("configEnc", SandiCrypto.encryptConfig(vault.dek, user, "k-file", "k-ver", ConfigPayload("X=1", "catatan"), ref))
        }
        File("build").mkdirs()
        File("build/kotlin-vectors.json").writeText(out.toString())
    }
}
