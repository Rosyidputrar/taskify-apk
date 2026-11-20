package com.rosyidputrar.taskify.core.store.vault

import com.powersync.PowerSyncDatabase
import com.powersync.db.SqlCursor
import com.rosyidputrar.taskify.core.common.Ids
import com.rosyidputrar.taskify.core.common.Limits
import com.rosyidputrar.taskify.core.common.Timestamps
import com.rosyidputrar.taskify.core.domain.notes.escapeLike
import com.rosyidputrar.taskify.core.domain.vault.CodeProject
import com.rosyidputrar.taskify.core.domain.vault.Snippet
import com.rosyidputrar.taskify.core.store.UserIdProvider
import com.rosyidputrar.taskify.core.store.require
import com.rosyidputrar.taskify.core.store.tasks.int
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val SNIPPET_COLUMNS = "id, title, language, code, description, is_favorite, created_at, updated_at"

private fun SqlCursor.toSnippet() = Snippet(
    getString(0)!!, getString(1) ?: "", getString(2) ?: "plaintext", getString(3) ?: "", getString(4), int(5) == 1, getString(6) ?: "", getString(7) ?: "",
)

data class LanguageCount(val language: String, val count: Int)

/** Snippet & project Vault Kode — port `vault-data.ts`. */
class VaultRepository(private val db: PowerSyncDatabase, private val users: UserIdProvider) {

    // ================================================================== snippet

    fun watchSnippets(search: String = "", language: String? = null, favorites: Boolean = false): Flow<List<Snippet>> = db.watch(
        """SELECT $SNIPPET_COLUMNS FROM snippets
            WHERE deleted_at IS NULL
              AND (?1 IS NULL OR language = ?1)
              AND (?2 = 0 OR is_favorite = 1)
              AND (?3 = '' OR LOWER(title || ' ' || COALESCE(description, '') || ' ' || code) LIKE '%' || ?3 || '%' ESCAPE '\')
            ORDER BY is_favorite DESC, updated_at DESC LIMIT 1000""",
        listOf(language, if (favorites) 1 else 0, escapeLike(search.trim().lowercase())),
    ) { it.toSnippet() }

    fun watchSnippet(id: String): Flow<Snippet?> = db.watch("SELECT $SNIPPET_COLUMNS FROM snippets WHERE id = ?", listOf(id)) { it.toSnippet() }.map { it.firstOrNull() }

    suspend fun getSnippet(id: String): Snippet? = db.getOptional("SELECT $SNIPPET_COLUMNS FROM snippets WHERE id = ?", listOf(id)) { it.toSnippet() }

    fun watchLanguages(): Flow<List<LanguageCount>> = db.watch(
        "SELECT language, COUNT(*) FROM snippets WHERE deleted_at IS NULL GROUP BY language ORDER BY 2 DESC",
    ) { c -> LanguageCount(c.getString(0) ?: "plaintext", c.int(1)) }

    suspend fun saveSnippet(id: String?, title: String, language: String, code: String, description: String?): String {
        val clean = Limits.clip(title.trim(), Limits.SNIPPET_TITLE)
        val desc = description?.trim()?.ifEmpty { null }
        val now = Timestamps.nowIso()
        if (id != null) {
            db.execute(
                "UPDATE snippets SET title = ?, language = ?, code = ?, description = ?, updated_at = ? WHERE id = ?",
                listOf(clean, language, code, desc, now, id),
            )
            return id
        }
        val newId = Ids.newId()
        db.execute(
            "INSERT INTO snippets (id, user_id, created_at, updated_at, title, language, code, description, is_favorite) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)",
            listOf(newId, users.require(), now, now, clean, language, code, desc),
        )
        return newId
    }

    suspend fun setSnippetFavorite(id: String, favorite: Boolean) {
        db.execute("UPDATE snippets SET is_favorite = ?, updated_at = ? WHERE id = ?", listOf(if (favorite) 1 else 0, Timestamps.nowIso(), id))
    }

    suspend fun removeSnippet(id: String) = softDelete("snippets", id)
    suspend fun restoreSnippet(id: String) = restore("snippets", id)

    // ================================================================== project

    private val projectSelect = """
        SELECT p.id, p.name, p.description, p.repo_url, p.is_favorite, p.created_at, p.updated_at,
               (SELECT COUNT(*) FROM attachments a WHERE a.entity_type = 'code_project' AND a.entity_id = p.id AND a.deleted_at IS NULL),
               (SELECT COALESCE(SUM(size_bytes), 0) FROM attachments a WHERE a.entity_type = 'code_project' AND a.entity_id = p.id AND a.deleted_at IS NULL),
               l.size_bytes, l.version_label, l.created_at
          FROM code_projects p
          LEFT JOIN attachments l ON l.id = (
            SELECT id FROM attachments a WHERE a.entity_type = 'code_project' AND a.entity_id = p.id AND a.deleted_at IS NULL
             ORDER BY a.created_at DESC LIMIT 1)"""

    private fun SqlCursor.toProject() = CodeProject(
        getString(0)!!, getString(1) ?: "", getString(2), getString(3), int(4) == 1, getString(5) ?: "", getString(6) ?: "",
        int(7), getLong(8) ?: 0, getLong(9), getString(10), getString(11),
    )

    fun watchProjects(search: String = ""): Flow<List<CodeProject>> = db.watch(
        """$projectSelect
           WHERE p.deleted_at IS NULL
             AND (?1 = '' OR LOWER(p.name || ' ' || COALESCE(p.description, '')) LIKE '%' || ?1 || '%' ESCAPE '\')
           ORDER BY p.is_favorite DESC, COALESCE(l.created_at, p.updated_at) DESC""",
        listOf(escapeLike(search.trim().lowercase())),
    ) { it.toProject() }

    fun watchProject(id: String): Flow<CodeProject?> = db.watch("$projectSelect WHERE p.id = ?1", listOf(id)) { it.toProject() }.map { it.firstOrNull() }

    suspend fun saveProject(id: String?, name: String, description: String?, repoUrl: String?): String {
        val clean = Limits.clip(name.trim(), Limits.PROJECT_NAME)
        val desc = description?.trim()?.ifEmpty { null }
        val repo = repoUrl?.trim()?.ifEmpty { null }
        val now = Timestamps.nowIso()
        if (id != null) {
            db.execute("UPDATE code_projects SET name = ?, description = ?, repo_url = ?, updated_at = ? WHERE id = ?", listOf(clean, desc, repo, now, id))
            return id
        }
        val newId = Ids.newId()
        db.execute(
            "INSERT INTO code_projects (id, user_id, created_at, updated_at, name, description, repo_url, is_favorite, position) VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?)",
            listOf(newId, users.require(), now, now, clean, desc, repo, System.currentTimeMillis().toDouble()),
        )
        return newId
    }

    suspend fun setProjectFavorite(id: String, favorite: Boolean) {
        db.execute("UPDATE code_projects SET is_favorite = ?, updated_at = ? WHERE id = ?", listOf(if (favorite) 1 else 0, Timestamps.nowIso(), id))
    }

    /**
     * Pindahkan project ke Sampah beserta versi-versinya (lampiran): versi yang tetap hidup
     * akan terus terhitung di pemakaian penyimpanan padahal projectnya sudah tidak ada.
     * Versi dihidupkan lagi oleh [restoreProject] bila project dipulihkan.
     */
    suspend fun removeProject(id: String) {
        val now = Timestamps.nowIso()
        db.writeTransactionAsync { tx ->
            tx.executeAsync("UPDATE code_projects SET deleted_at = ?, updated_at = ? WHERE id = ?", listOf(now, now, id))
            tx.executeAsync(
                "UPDATE attachments SET deleted_at = ?, updated_at = ? WHERE entity_type = 'code_project' AND entity_id = ? AND deleted_at IS NULL",
                listOf(now, now, id),
            )
        }
    }

    suspend fun restoreProject(id: String) {
        val now = Timestamps.nowIso()
        db.writeTransactionAsync { tx ->
            val deletedAt = tx.getAllAsync(
                "SELECT deleted_at FROM code_projects WHERE id = ? AND deleted_at IS NOT NULL",
                listOf(id),
            ) { it.getString(0)!! }.firstOrNull()
            tx.executeAsync("UPDATE code_projects SET deleted_at = NULL, updated_at = ? WHERE id = ?", listOf(now, id))
            // Hidupkan lagi versi yang ikut terhapus bersama project ini — bukan versi yang
            // dihapus sendiri sebelum project masuk Sampah (deleted_at-nya berbeda).
            if (deletedAt != null) {
                tx.executeAsync(
                    "UPDATE attachments SET deleted_at = NULL, updated_at = ? WHERE entity_type = 'code_project' AND entity_id = ? AND deleted_at = ?",
                    listOf(now, id, deletedAt),
                )
            }
        }
    }

    private suspend fun softDelete(table: String, id: String) {
        val now = Timestamps.nowIso()
        db.execute("UPDATE $table SET deleted_at = ?, updated_at = ? WHERE id = ?", listOf(now, now, id))
    }

    private suspend fun restore(table: String, id: String) {
        db.execute("UPDATE $table SET deleted_at = NULL, updated_at = ? WHERE id = ?", listOf(Timestamps.nowIso(), id))
    }
}
