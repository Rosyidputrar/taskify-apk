package com.rosyidputrar.taskify.core.store

import com.powersync.PowerSyncDatabase
import com.powersync.inMemory
import com.rosyidputrar.taskify.core.domain.sandi.ConfigFormat
import com.rosyidputrar.taskify.core.domain.sandi.ConfigPayload
import com.rosyidputrar.taskify.core.domain.sandi.ItemPayload
import com.rosyidputrar.taskify.core.domain.sandi.SandiCrypto
import com.rosyidputrar.taskify.core.domain.sandi.SandiException
import com.rosyidputrar.taskify.core.domain.sandi.TotpLogEntry
import com.rosyidputrar.taskify.core.store.sandi.ConfigRepository
import com.rosyidputrar.taskify.core.store.sandi.SandiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SandiRepositoryTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db = PowerSyncDatabase.inMemory(TaskifySchema.schema, scope)
    private val user = "11111111-2222-3333-4444-555555555555"
    private val sandi = SandiRepository(db) { user }
    private val config = ConfigRepository(db) { user }

    @After fun tearDown() = runBlocking {
        db.close()
        scope.cancel()
    }

    @Test fun createUnlockSaveAndRotateEverything() = runBlocking {
        val vault = SandiCrypto.createVault("frasa lama yang panjang", user, iterations = 100_000)
        val keyId = sandi.createKey(vault.record)
        assertFailsWith<SandiException> { sandi.createKey(vault.record) } // satu brankas per akun
        assertTrue(sandi.opPending("pw_keys", keyId))

        val key = sandi.getKey()!!
        val dek = assertNotNull(SandiCrypto.unlockWithPassword(key.record, "frasa lama yang panjang", user))
        val ref = SandiCrypto.keyRef(key.record.wrappedKey)
        val itemId = sandi.newId()
        sandi.insertItem(itemId, SandiCrypto.encryptItem(dek, user, itemId, ItemPayload(title = "GitHub", password = "p"), ref))
        val trashed = sandi.newId()
        sandi.insertItem(trashed, SandiCrypto.encryptItem(dek, user, trashed, ItemPayload(title = "Lama"), ref))
        sandi.removeItem(trashed)
        val broken = sandi.newId()
        sandi.insertItem(broken, "ffffffffffffffff.YWJj") // tidak bisa dibuka siapa pun
        val logId = sandi.newId()
        sandi.insertTotpLog(logId, SandiCrypto.encryptTotpLog(dek, user, logId, TotpLogEntry(itemId, "GitHub", "123456", "2026-10-07T00:00:00.000Z"), ref))
        val fileId = config.createFile(".env.production", ConfigFormat.Env, " Production ", null) { f, v -> SandiCrypto.encryptConfig(dek, user, f, v, ConfigPayload("A=1"), ref) }
        config.saveVersion(fileId) { v -> SandiCrypto.encryptConfig(dek, user, fileId, v, ConfigPayload("A=2", "naik"), ref) }
        assertEquals(2, sandi.watchItemRows().first().size)
        assertEquals("production", config.watchFile(fileId).first()!!.environment)
        assertEquals(listOf(2, 1), config.watchVersions(fileId).first().map { it.number })

        val next = SandiCrypto.changePassword(key.record, "frasa lama yang panjang", "frasa baru yang juga panjang", user, iterations = 100_000)!!
        val result = sandi.rekey(keyId, next)
        assertEquals(2, result.reencrypted) // termasuk yang di Sampah
        assertEquals(1, result.skipped)
        assertEquals(1, result.logReencrypted)
        assertEquals(2, result.configReencrypted)

        val newKey = sandi.getKey()!!
        assertEquals(next.record.wrappedKey, newKey.record.wrappedKey)
        val newDek = SandiCrypto.unlockWithPassword(newKey.record, "frasa baru yang juga panjang", user)!!
        val row = sandi.watchItemRows().first().first { it.id == itemId }
        assertTrue(row.enc.startsWith(SandiCrypto.keyRef(newKey.record.wrappedKey) + "."))
        assertEquals("GitHub", SandiCrypto.decryptItem(newDek, user, itemId, row.enc).title)
        val versions = config.watchVersions(fileId).first()
        assertEquals("A=2", SandiCrypto.decryptConfig(newDek, user, fileId, versions.first().id, versions.first().enc).text)
        assertEquals(
            SandiCrypto.keyRef(vault.record.wrappedKey),
            db.get("SELECT rotated_from FROM pw_keys WHERE id = ?", listOf(keyId)) { it.getString(0)!! },
        )
        // Rotasi kedua dengan record lama → ditolak tanpa mengubah apa pun.
        assertFailsWith<SandiException> { sandi.rekey(keyId, next) }
        Unit
    }

    @Test fun totpLogRetentionAndRejections() = runBlocking {
        db.execute("INSERT INTO pw_totp_log (id, user_id, created_at, updated_at, enc, v) VALUES ('a', 'u', '2026-07-01 00:00:00Z', 'x', 'e', 1)")
        db.execute("INSERT INTO pw_totp_log (id, user_id, created_at, updated_at, enc, v) VALUES ('b', 'u', '2026-10-06T00:00:00.000Z', 'x', 'e', 1)")
        sandi.purgeTotpLog("2026-07-09T00:00:00.000Z")
        assertEquals(listOf("b"), sandi.watchTotpLogRows().first().map { it.id })

        db.execute("INSERT INTO upload_errors (id, op, table_name, row_id, payload, error_code, error_message, created_at) VALUES ('e1', 'PATCH', 'pw_keys', 'k', '{}', '23V01', 'x', '2026-10-07T00:00:00.000Z')")
        db.execute("INSERT INTO upload_errors (id, op, table_name, row_id, payload, error_code, error_message, created_at) VALUES ('e2', 'PATCH', 'pw_items', 'i', '{}', '23V03', 'x', '2026-10-07T00:00:00.000Z')")
        assertEquals("23V01", sandi.watchRejectedRotation().first())
        assertEquals(true to "23V01", sandi.keyRejection("k"))
        assertEquals(false to null, sandi.keyRejection("k", "2026-10-08T00:00:00.000Z"))
        assertEquals(1, sandi.watchStaleWrites().first())
        sandi.dismissStaleWrites()
        assertEquals(0, sandi.watchStaleWrites().first())
    }
}
