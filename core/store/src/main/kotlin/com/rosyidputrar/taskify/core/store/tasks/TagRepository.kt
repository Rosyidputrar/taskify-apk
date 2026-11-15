package com.rosyidputrar.taskify.core.store.tasks

import com.rosyidputrar.taskify.core.common.Ids
import com.rosyidputrar.taskify.core.common.Timestamps
import com.powersync.PowerSyncDatabase
import com.rosyidputrar.taskify.core.store.UserIdProvider
import com.rosyidputrar.taskify.core.store.require
import com.rosyidputrar.taskify.core.domain.tags.PALETTE_NAMES
import com.rosyidputrar.taskify.core.domain.tags.normalizeTagName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class TagRef(val id: String, val name: String, val color: String?, val count: Int = 0)

/**
 * Tag global lintas modul (`tags` + `taggings` polimorfik). Nama unik tanpa membedakan huruf
 * besar-kecil — dijaga di aplikasi, bukan UNIQUE di DB (UNIQUE bisa membuang perubahan offline).
 */
class TagRepository(private val db: PowerSyncDatabase, private val users: UserIdProvider) {

    fun watchAll(): Flow<List<TagRef>> = db.watch(
        """SELECT t.id, t.name, t.color, (SELECT COUNT(*) FROM taggings g WHERE g.tag_id = t.id)
           FROM tags t WHERE t.deleted_at IS NULL ORDER BY 4 DESC, t.name""",
    ) { c -> TagRef(c.getString(0)!!, c.getString(1) ?: "", c.getString(2), c.int(3)) }

    fun watchEntityTags(entityType: String, entityId: String): Flow<List<TagRef>> = db.watch(
        """SELECT t.id, t.name, t.color FROM taggings g JOIN tags t ON t.id = g.tag_id
           WHERE g.entity_type = ? AND g.entity_id = ? AND t.deleted_at IS NULL ORDER BY t.name""",
        listOf(entityType, entityId),
    ) { c -> TagRef(c.getString(0)!!, c.getString(1) ?: "", c.getString(2)) }

    /** entity_id → tag, untuk chip di daftar. */
    fun watchTagMap(entityType: String): Flow<Map<String, List<TagRef>>> = db.watch(
        """SELECT g.entity_id, t.id, t.name, t.color FROM taggings g JOIN tags t ON t.id = g.tag_id
           WHERE g.entity_type = ? AND t.deleted_at IS NULL ORDER BY t.name""",
        listOf(entityType),
    ) { c -> c.getString(0)!! to TagRef(c.getString(1)!!, c.getString(2) ?: "", c.getString(3)) }
        .map { rows -> rows.groupBy({ it.first }, { it.second }) }

    /** Cari tag berdasarkan nama; buat bila belum ada. */
    suspend fun ensureTags(names: List<String>): List<String> {
        val clean = names.map(::normalizeTagName).filter { it.isNotEmpty() }.distinct()
        if (clean.isEmpty()) return emptyList()
        val userId = users.require()
        val existing = db.getAll("SELECT id, lower(name) FROM tags WHERE deleted_at IS NULL") { c -> c.getString(1)!! to c.getString(0)!! }.toMap()
        val total = db.get("SELECT count(*) FROM tags") { it.int(0) }
        val now = Timestamps.nowIso()
        return clean.mapIndexed { i, name ->
            existing[name] ?: Ids.newId().also { id ->
                db.execute(
                    "INSERT INTO tags (id, user_id, created_at, updated_at, name, color) VALUES (?, ?, ?, ?, ?, ?)",
                    listOf(id, userId, now, now, name, PALETTE_NAMES[(total + i) % PALETTE_NAMES.size]),
                )
            }
        }
    }

    suspend fun addTags(entityType: String, entityId: String, tagIds: List<String>) {
        if (tagIds.isEmpty()) return
        val userId = users.require()
        val have = db.getAll("SELECT tag_id FROM taggings WHERE entity_type = ? AND entity_id = ?", listOf(entityType, entityId)) { it.getString(0)!! }.toSet()
        val now = Timestamps.nowIso()
        tagIds.filterNot { it in have }.distinct().forEach { tagId ->
            db.execute(
                "INSERT INTO taggings (id, user_id, created_at, updated_at, tag_id, entity_type, entity_id) VALUES (?, ?, ?, ?, ?, ?, ?)",
                listOf(Ids.newId(), userId, now, now, tagId, entityType, entityId),
            )
        }
    }

    suspend fun addByName(entityType: String, entityId: String, names: List<String>) =
        addTags(entityType, entityId, ensureTags(names))

    suspend fun remove(entityType: String, entityId: String, tagId: String) {
        db.execute("DELETE FROM taggings WHERE entity_type = ? AND entity_id = ? AND tag_id = ?", listOf(entityType, entityId, tagId))
    }
}
