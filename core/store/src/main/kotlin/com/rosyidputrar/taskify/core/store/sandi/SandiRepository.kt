package com.rosyidputrar.taskify.core.store.sandi

import com.powersync.PowerSyncDatabase
import com.powersync.db.SqlCursor
import com.rosyidputrar.taskify.core.common.Ids
import com.rosyidputrar.taskify.core.common.Timestamps
import com.rosyidputrar.taskify.core.domain.sandi.EncryptedRow
import com.rosyidputrar.taskify.core.domain.sandi.KeyRecord
import com.rosyidputrar.taskify.core.domain.sandi.RekeyResult
import com.rosyidputrar.taskify.core.domain.sandi.RewrittenVault
import com.rosyidputrar.taskify.core.domain.sandi.SandiCrypto
import com.rosyidputrar.taskify.core.domain.sandi.SandiException
import com.rosyidputrar.taskify.core.domain.sandi.TOTP_LOG_PAGE_SIZE
import com.rosyidputrar.taskify.core.domain.sandi.VaultError
import com.rosyidputrar.taskify.core.domain.sync.VAULT_ENCRYPTED_TABLES
import com.rosyidputrar.taskify.core.store.UserIdProvider
import com.rosyidputrar.taskify.core.store.require
import com.rosyidputrar.taskify.core.store.tasks.int
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Baris `pw_keys` (satu per user). */
data class KeyRow(val id: String, val record: KeyRecord)

/** Baris terenkripsi (isi dibuka di memori hanya saat brankas terbuka). */
data class EncRow(val id: String, val enc: String, val createdAt: String, val updatedAt: String)

private val VAULT_TABLES_SQL = VAULT_ENCRYPTED_TABLES.joinToString { "'$it'" }

private fun SqlCursor.toKeyRow() = KeyRow(
    getString(0)!!,
    KeyRecord(int(1), getString(2) ?: "", int(3), getString(4) ?: "", getString(5) ?: "", getString(6) ?: "", getString(7) ?: ""),
)

/**
 * Data Sandi di SQLite lokal — port `sandi-data.ts` + `totp-log.ts` + `vault-rekey.ts`. Isi item,
 * riwayat TOTP, dan versi konfigurasi selalu ciphertext; repository ini tidak pernah melihat isinya
 * kecuali saat penggantian kunci (DEK diberikan pemanggil).
 */
class SandiRepository(private val db: PowerSyncDatabase, private val users: UserIdProvider) {
    private val keySelect = "SELECT id, version, kdf, iterations, salt, wrapped_key, recovery_salt, recovery_wrapped_key FROM pw_keys"

    fun watchKey(): Flow<KeyRow?> = db.watch("$keySelect ORDER BY created_at LIMIT 1") { it.toKeyRow() }.map { it.firstOrNull() }

    suspend fun getKey(): KeyRow? = db.getOptional("$keySelect ORDER BY created_at LIMIT 1") { it.toKeyRow() }

    fun watchItemRows(): Flow<List<EncRow>> = db.watch(
        "SELECT id, enc, created_at, updated_at FROM pw_items WHERE deleted_at IS NULL ORDER BY updated_at DESC",
    ) { c -> EncRow(c.getString(0)!!, c.getString(1) ?: "", c.getString(2) ?: "", c.getString(3) ?: "") }

    /** Simpan record kunci baru; menolak bila sudah ada (dua kunci berbeda = item tak terbaca di salah satu perangkat). */
    suspend fun createKey(record: KeyRecord): String {
        val exists = db.get("SELECT COUNT(*) FROM pw_keys") { it.int(0) } > 0
        if (exists) throw SandiException(SandiException.Code.Invalid, "Brankas sudah dibuat di perangkat lain. Buka dengan master password-nya.")
        val id = Ids.newId()
        val now = Timestamps.nowIso()
        db.execute(
            """INSERT INTO pw_keys (id, user_id, created_at, updated_at, version, kdf, iterations, salt, wrapped_key, recovery_salt,
                      recovery_wrapped_key, rotated_from, labels_required_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL)""",
            listOf(id, users.require(), now, now, record.version, record.kdf, record.iterations, record.salt, record.wrappedKey, record.recoverySalt, record.recoveryWrappedKey),
        )
        return id
    }

    /** id dibuat DULU oleh pemanggil karena menjadi bagian AAD enkripsi. */
    fun newId(): String = Ids.newId()

    suspend fun insertItem(id: String, enc: String) {
        val now = Timestamps.nowIso()
        db.execute(
            "INSERT INTO pw_items (id, user_id, created_at, updated_at, enc, v) VALUES (?, ?, ?, ?, ?, ?)",
            listOf(id, users.require(), now, now, enc, SandiCrypto.ITEM_VERSION),
        )
    }

    suspend fun updateItem(id: String, enc: String) {
        db.execute("UPDATE pw_items SET enc = ?, v = ?, updated_at = ? WHERE id = ?", listOf(enc, SandiCrypto.ITEM_VERSION, Timestamps.nowIso(), id))
    }

    suspend fun removeItem(id: String) {
        val now = Timestamps.nowIso()
        db.execute("UPDATE pw_items SET deleted_at = ?, updated_at = ? WHERE id = ?", listOf(now, now, id))
    }

    suspend fun restoreItem(id: String) {
        db.execute("UPDATE pw_items SET deleted_at = NULL, updated_at = ? WHERE id = ?", listOf(Timestamps.nowIso(), id))
    }

    // ------------------------------------------------------------------ riwayat TOTP

    fun watchTotpLogRows(limit: Int = TOTP_LOG_PAGE_SIZE): Flow<List<EncRow>> = db.watch(
        "SELECT id, enc, created_at, updated_at FROM pw_totp_log ORDER BY datetime(created_at) DESC, id DESC LIMIT ?",
        listOf(limit),
    ) { c -> EncRow(c.getString(0)!!, c.getString(1) ?: "", c.getString(2) ?: "", c.getString(3) ?: "") }

    /** Sengaja TIDAK menyentuh `pw_items` (urutan daftar & updated_at item tetap). */
    suspend fun insertTotpLog(id: String, enc: String) {
        val now = Timestamps.nowIso()
        db.execute(
            "INSERT INTO pw_totp_log (id, user_id, created_at, updated_at, enc, v) VALUES (?, ?, ?, ?, ?, ?)",
            listOf(id, users.require(), now, now, enc, SandiCrypto.ITEM_VERSION),
        )
    }

    /** `datetime()` di kedua sisi: baris tersinkron berformat "… HH:MM:SSZ" (spasi), lokal ISO "T". */
    suspend fun purgeTotpLog(cutoffIso: String) {
        db.execute("DELETE FROM pw_totp_log WHERE datetime(created_at) < datetime(?)", listOf(cutoffIso))
    }

    suspend fun deleteTotpLog(id: String) = db.execute("DELETE FROM pw_totp_log WHERE id = ?", listOf(id))
    suspend fun clearTotpLog() = db.execute("DELETE FROM pw_totp_log")

    // ------------------------------------------------------------------ penggantian kunci

    /**
     * Ganti kunci brankas dalam SATU transaksi tulis (diunggah atomik lewat RPC
     * `apply_vault_rotation`): record kunci dulu, lalu SEMUA baris `pw_items` (termasuk Sampah),
     * `pw_totp_log`, dan `config_versions` dienkripsi ulang. Baris yang tidak bisa dibuka dengan
     * DEK lama tetap DISENTUH (`updated_at`) agar server tahu baris itu sudah dipertimbangkan.
     */
    suspend fun rekey(keyId: String, vault: RewrittenVault): RekeyResult {
        val userId = users.require()
        return unwrapSandi { db.writeTransactionAsync { tx ->
            val current = tx.getAllAsync("SELECT wrapped_key FROM pw_keys WHERE id = ?", listOf(keyId)) { it.getString(0) ?: "" }.firstOrNull()
            if (current == null || current != vault.previousWrappedKey) {
                throw SandiException(SandiException.Code.Invalid, "Kunci brankas berubah saat proses berlangsung. Tidak ada yang diubah; buka brankas lagi lalu ulangi.")
            }
            val fromRef = SandiCrypto.keyRef(vault.previousWrappedKey)
            val toRef = SandiCrypto.keyRef(vault.record.wrappedKey)
            val itemRows = tx.getAllAsync("SELECT id, enc FROM pw_items", emptyList()) { EncryptedRow(it.getString(0)!!, it.getString(1) ?: "") }
            val items = SandiCrypto.reencryptItems(itemRows, vault.previousDek, vault.dek, userId, toRef)
            val logRows = tx.getAllAsync("SELECT id, enc FROM pw_totp_log", emptyList()) { EncryptedRow(it.getString(0)!!, it.getString(1) ?: "") }
            val log = SandiCrypto.reencryptTotpLog(logRows, vault.previousDek, vault.dek, userId, toRef)
            val cfgRows = tx.getAllAsync("SELECT id, enc, file_id FROM config_versions", emptyList()) { EncryptedRow(it.getString(0)!!, it.getString(1) ?: "", it.getString(2) ?: "") }
            val cfg = SandiCrypto.reencryptConfigVersions(cfgRows, vault.previousDek, vault.dek, userId, toRef)

            val now = Timestamps.nowIso()
            val r = vault.record
            tx.executeAsync(
                """UPDATE pw_keys SET version = ?, kdf = ?, iterations = ?, salt = ?, wrapped_key = ?, recovery_salt = ?,
                          recovery_wrapped_key = ?, rotated_from = ?, updated_at = ? WHERE id = ?""",
                listOf(r.version, r.kdf, r.iterations, r.salt, r.wrappedKey, r.recoverySalt, r.recoveryWrappedKey, fromRef, now, keyId),
            )
            for (row in items.updated) tx.executeAsync("UPDATE pw_items SET enc = ?, v = ?, updated_at = ? WHERE id = ?", listOf(row.enc, SandiCrypto.ITEM_VERSION, now, row.id))
            for (id in items.skipped) tx.executeAsync("UPDATE pw_items SET updated_at = ? WHERE id = ?", listOf(now, id))
            for (row in log.updated) tx.executeAsync("UPDATE pw_totp_log SET enc = ?, v = ?, updated_at = ? WHERE id = ?", listOf(row.enc, SandiCrypto.ITEM_VERSION, now, row.id))
            for (id in log.skipped) tx.executeAsync("UPDATE pw_totp_log SET updated_at = ? WHERE id = ?", listOf(now, id))
            for (row in cfg.updated) tx.executeAsync("UPDATE config_versions SET enc = ?, v = ?, updated_at = ? WHERE id = ?", listOf(row.enc, SandiCrypto.ITEM_VERSION, now, row.id))
            for (id in cfg.skipped) tx.executeAsync("UPDATE config_versions SET updated_at = ? WHERE id = ?", listOf(now, id))
            RekeyResult(items.updated.size, items.skipped.size, log.updated.size, log.skipped.size, cfg.updated.size, cfg.skipped.size)
        } }
    }

    /** PowerSync membungkus galat dari dalam transaksi; kembalikan SandiException aslinya. */
    private suspend fun <T> unwrapSandi(block: suspend () -> T): T = try {
        block()
    } catch (e: Exception) {
        var cause: Throwable? = e
        while (cause != null && cause !is SandiException) cause = cause.cause
        throw (cause as? SandiException) ?: e
    }

    // ------------------------------------------------------------------ status antrean & penolakan server

    /** Masih ada operasi untuk baris ini di antrean upload? */
    suspend fun opPending(table: String, id: String): Boolean = db.get(
        "SELECT COUNT(*) FROM ps_crud WHERE json_extract(data, '$.type') = ? AND json_extract(data, '$.id') = ?",
        listOf(table, id),
    ) { it.int(0) } > 0

    /** Kode penolakan pw_keys untuk [keyId] sejak [sinceIso]; null kode = ditolak tanpa kode; tidak ada baris = belum ditolak. */
    suspend fun keyRejection(keyId: String, sinceIso: String? = null): Pair<Boolean, String?> {
        val rows = db.getAll(
            "SELECT COALESCE(error_code, '') FROM upload_errors WHERE table_name = 'pw_keys' AND row_id = ? AND (? IS NULL OR created_at >= ?) LIMIT 1",
            listOf(keyId, sinceIso, sinceIso),
        ) { it.getString(0) ?: "" }
        return rows.isNotEmpty() to rows.firstOrNull()?.ifEmpty { null }
    }

    /** Pembuatan brankas dari perangkat ini ditolak server (brankas sudah ada di perangkat lain). */
    fun watchRejectedKeyCreation(): Flow<Boolean> = db.watch("SELECT COUNT(*) FROM upload_errors WHERE table_name = 'pw_keys' AND op = 'PUT'") { it.int(0) > 0 }.map { it.firstOrNull() == true }

    /** Penggantian kunci yang ditolak server setelah layar ganti sandi ditutup: kode error terakhir. */
    fun watchRejectedRotation(): Flow<String?> = db.watch(
        "SELECT COALESCE(error_code, '') FROM upload_errors WHERE table_name = 'pw_keys' AND op = 'PATCH' ORDER BY created_at DESC LIMIT 1",
    ) { it.getString(0) ?: "" }.map { it.firstOrNull() }

    /** Tulisan brankas yang ditolak karena dienkripsi dengan kunci lama (23V03). */
    fun watchStaleWrites(): Flow<Int> = db.watch(
        "SELECT COUNT(*) FROM upload_errors WHERE error_code = ? AND table_name IN ($VAULT_TABLES_SQL)",
        listOf(VaultError.STALE_KEY),
    ) { it.int(0) }.map { it.firstOrNull() ?: 0 }

    suspend fun dismissRejectedKeyCreation(keyId: String? = null) {
        if (keyId != null) db.execute("DELETE FROM upload_errors WHERE table_name = 'pw_keys' AND row_id = ?", listOf(keyId))
        else db.execute("DELETE FROM upload_errors WHERE table_name = 'pw_keys' AND op = 'PUT'")
    }

    suspend fun dismissRejectedRotation(sinceIso: String? = null) {
        if (sinceIso != null) {
            db.execute("DELETE FROM upload_errors WHERE created_at >= ? AND table_name IN ($VAULT_TABLES_SQL, 'pw_keys')", listOf(sinceIso))
        } else {
            db.execute(
                """DELETE FROM upload_errors WHERE (table_name = 'pw_keys' AND op = 'PATCH')
                   OR (table_name IN ($VAULT_TABLES_SQL) AND error_code IN (?, ?, ?, ?))""",
                listOf(VaultError.KEY_CHANGED, VaultError.INCOMPLETE, VaultError.TOO_LARGE, VaultError.INVALID),
            )
        }
    }

    suspend fun dismissStaleWrites() {
        db.execute("DELETE FROM upload_errors WHERE error_code = ? AND table_name IN ($VAULT_TABLES_SQL)", listOf(VaultError.STALE_KEY))
    }
}
