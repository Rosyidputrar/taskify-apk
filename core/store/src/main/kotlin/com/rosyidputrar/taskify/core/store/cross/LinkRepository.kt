package com.rosyidputrar.taskify.core.store.cross

import com.powersync.PowerSyncDatabase
import com.rosyidputrar.taskify.core.common.Ids
import com.rosyidputrar.taskify.core.common.Timestamps
import com.rosyidputrar.taskify.core.domain.files.EntityType
import com.rosyidputrar.taskify.core.domain.search.EntityRow
import com.rosyidputrar.taskify.core.store.UserIdProvider
import com.rosyidputrar.taskify.core.store.require
import com.rosyidputrar.taskify.core.store.search.ENTITY_UNION
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class LinkedEntity(val linkId: String, val entity: EntityRow)

/**
 * Tautan silang antar entitas (`entity_links`, port `link-actions.ts`). Dua arah saat ditampilkan:
 * A→B juga muncul di B. Entitas yang ada di Sampah tetap tampil (ditandai) seperti webapp.
 */
class LinkRepository(private val db: PowerSyncDatabase, private val users: UserIdProvider) {

    fun watchLinks(type: EntityType, id: String): Flow<List<LinkedEntity>> = db.watch(
        """WITH entities AS ($ENTITY_UNION),
                other AS (
                  SELECT l.id AS link_id,
                         CASE WHEN l.source_type = ?1 AND l.source_id = ?2 THEN l.target_type ELSE l.source_type END AS type,
                         CASE WHEN l.source_type = ?1 AND l.source_id = ?2 THEN l.target_id ELSE l.source_id END AS id,
                         l.created_at
                    FROM entity_links l
                   WHERE (l.source_type = ?1 AND l.source_id = ?2) OR (l.target_type = ?1 AND l.target_id = ?2))
           SELECT e.type, e.id, e.title, e.subtitle, e.deleted_at, o.link_id
             FROM other o JOIN entities e ON e.type = o.type AND e.id = o.id
            ORDER BY o.created_at""",
        listOf(type.value, id),
    ) { c ->
        val t = EntityType.from(c.getString(0))
        LinkedEntity(if (t == null) "" else c.getString(5)!!, EntityRow(t ?: EntityType.Task, c.getString(1)!!, (c.getString(2) ?: "").trim(), c.getString(3), c.getString(4)))
    }.map { rows -> rows.filter { it.linkId.isNotEmpty() } }

    /** Buat tautan bila belum ada (arah mana pun). */
    suspend fun link(aType: EntityType, aId: String, bType: EntityType, bId: String): Boolean {
        if (aType == bType && aId == bId) return false
        val exists = db.get(
            """SELECT count(*) FROM entity_links
                WHERE (source_type = ?1 AND source_id = ?2 AND target_type = ?3 AND target_id = ?4)
                   OR (source_type = ?3 AND source_id = ?4 AND target_type = ?1 AND target_id = ?2)""",
            listOf(aType.value, aId, bType.value, bId),
        ) { it.getLong(0) ?: 0L } > 0
        if (exists) return false
        val now = Timestamps.nowIso()
        db.execute(
            "INSERT INTO entity_links (id, user_id, created_at, updated_at, source_type, source_id, target_type, target_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            listOf(Ids.newId(), users.require(), now, now, aType.value, aId, bType.value, bId),
        )
        return true
    }

    suspend fun unlink(linkId: String) {
        db.execute("DELETE FROM entity_links WHERE id = ?", listOf(linkId))
    }
}
