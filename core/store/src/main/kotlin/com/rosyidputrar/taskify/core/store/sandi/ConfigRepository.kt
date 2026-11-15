package com.rosyidputrar.taskify.core.store.sandi

import com.powersync.PowerSyncDatabase
import com.rosyidputrar.taskify.core.common.Ids
import com.rosyidputrar.taskify.core.common.Limits
import com.rosyidputrar.taskify.core.common.Timestamps
import com.rosyidputrar.taskify.core.domain.notes.escapeLike
import com.rosyidputrar.taskify.core.domain.sandi.ConfigFormat
import com.rosyidputrar.taskify.core.domain.sandi.ConfigParser
import com.rosyidputrar.taskify.core.domain.sandi.SandiCrypto
import com.rosyidputrar.taskify.core.store.UserIdProvider
import com.rosyidputrar.taskify.core.store.require
import com.rosyidputrar.taskify.core.store.tasks.int
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ConfigFile(
    val id: String,
    val name: String,
    val format: ConfigFormat,
    val environment: String?,
    val projectId: String?,
    val projectName: String? = null,
    val versions: Int = 0,
    val latestAt: String? = null,
    val updatedAt: String = "",
)

/** Satu versi terenkripsi; [number] dihitung dari urutan (1 = paling lama). */
data class ConfigVersionRow(val id: String, val enc: String, val createdAt: String, val number: Int)

/**
 * Berkas konfigurasi Vault (port `config-data.ts`). Metadata biasa; ISI tiap versi terenkripsi
 * DEK brankas Sandi. Versi lama tidak pernah diubah — "pulihkan" pun menjadi versi baru.
 * Urutan versi memakai `datetime(created_at), id` (baris tersinkron berformat spasi, lokal "T").
 */
class ConfigRepository(private val db: PowerSyncDatabase, private val users: UserIdProvider) {
    private val fileSelect = """
        SELECT c.id, c.name, c.format, c.environment, c.project_id, p.name,
               (SELECT COUNT(*) FROM config_versions v WHERE v.file_id = c.id),
               (SELECT MAX(datetime(v.created_at)) FROM config_versions v WHERE v.file_id = c.id),
               c.updated_at
          FROM config_files c
          LEFT JOIN code_projects p ON p.id = c.project_id AND p.deleted_at IS NULL"""

    private fun row(c: com.powersync.db.SqlCursor) = ConfigFile(
        c.getString(0)!!, c.getString(1) ?: "", ConfigFormat.from(c.getString(2)), c.getString(3), c.getString(4), c.getString(5), c.int(6), c.getString(7), c.getString(8) ?: "",
    )

    fun watchFiles(search: String = "", environment: String? = null, projectId: String? = null): Flow<List<ConfigFile>> = db.watch(
        """$fileSelect
           WHERE c.deleted_at IS NULL
             AND (?1 = '' OR LOWER(c.name) LIKE '%' || ?1 || '%' ESCAPE '\' OR LOWER(COALESCE(c.environment, '')) LIKE '%' || ?1 || '%' ESCAPE '\')
             AND (?2 IS NULL OR c.environment = ?2)
             AND (?3 IS NULL OR c.project_id = ?3)
           ORDER BY COALESCE(p.name, ''), c.name, COALESCE(c.environment, '')""",
        listOf(escapeLike(search.trim().lowercase()), environment, projectId),
    ) { row(it) }

    fun watchFile(id: String): Flow<ConfigFile?> = db.watch("$fileSelect WHERE c.id = ?1", listOf(id)) { row(it) }.map { it.firstOrNull() }

    fun watchEnvironments(): Flow<List<Pair<String, Int>>> = db.watch(
        """SELECT environment, COUNT(*) FROM config_files
            WHERE deleted_at IS NULL AND environment IS NOT NULL AND environment <> ''
            GROUP BY environment ORDER BY 2 DESC, environment""",
    ) { (it.getString(0) ?: "") to it.int(1) }

    /** Versi berkas, TERBARU DULU, bernomor (1 = paling lama). */
    fun watchVersions(fileId: String): Flow<List<ConfigVersionRow>> = db.watch(
        "SELECT id, enc, created_at FROM config_versions WHERE file_id = ? ORDER BY datetime(created_at), id",
        listOf(fileId),
    ) { Triple(it.getString(0)!!, it.getString(1) ?: "", it.getString(2) ?: "") }
        .map { rows -> rows.mapIndexed { i, (id, enc, at) -> ConfigVersionRow(id, enc, at, i + 1) }.reversed() }

    /** Berkas baru + versi pertama dalam SATU transaksi. [encrypt] menerima (fileId, versionId) — keduanya bagian AAD. */
    suspend fun createFile(name: String, format: ConfigFormat, environment: String, projectId: String?, encrypt: (String, String) -> String): String {
        val fileId = Ids.newId()
        val versionId = Ids.newId()
        val enc = encrypt(fileId, versionId)
        val userId = users.require()
        val now = Timestamps.nowIso()
        db.writeTransactionAsync { tx ->
            tx.executeAsync(
                "INSERT INTO config_files (id, user_id, created_at, updated_at, deleted_at, project_id, name, format, environment) VALUES (?, ?, ?, ?, NULL, ?, ?, ?, ?)",
                listOf(fileId, userId, now, now, projectId, Limits.clip(name.trim(), Limits.CONFIG_NAME), format.value, ConfigParser.normalizeEnvironment(environment)),
            )
            tx.executeAsync(
                "INSERT INTO config_versions (id, user_id, created_at, updated_at, file_id, enc, v) VALUES (?, ?, ?, ?, ?, ?, ?)",
                listOf(versionId, userId, now, now, fileId, enc, SandiCrypto.ITEM_VERSION),
            )
        }
        return fileId
    }

    /** Versi BARU (versi lama tidak pernah diubah). */
    suspend fun saveVersion(fileId: String, encrypt: (String) -> String): String {
        val versionId = Ids.newId()
        val enc = encrypt(versionId)
        val now = Timestamps.nowIso()
        db.writeTransactionAsync { tx ->
            tx.executeAsync(
                "INSERT INTO config_versions (id, user_id, created_at, updated_at, file_id, enc, v) VALUES (?, ?, ?, ?, ?, ?, ?)",
                listOf(versionId, users.require(), now, now, fileId, enc, SandiCrypto.ITEM_VERSION),
            )
            tx.executeAsync("UPDATE config_files SET updated_at = ? WHERE id = ?", listOf(now, fileId))
        }
        return versionId
    }

    suspend fun updateMeta(id: String, name: String, environment: String, projectId: String?) {
        db.execute(
            "UPDATE config_files SET name = ?, environment = ?, project_id = ?, updated_at = ? WHERE id = ?",
            listOf(Limits.clip(name.trim(), Limits.CONFIG_NAME), ConfigParser.normalizeEnvironment(environment), projectId, Timestamps.nowIso(), id),
        )
    }

    suspend fun removeFile(id: String) {
        val now = Timestamps.nowIso()
        db.execute("UPDATE config_files SET deleted_at = ?, updated_at = ? WHERE id = ?", listOf(now, now, id))
    }

    suspend fun restoreFile(id: String) {
        db.execute("UPDATE config_files SET deleted_at = NULL, updated_at = ? WHERE id = ?", listOf(Timestamps.nowIso(), id))
    }
}
