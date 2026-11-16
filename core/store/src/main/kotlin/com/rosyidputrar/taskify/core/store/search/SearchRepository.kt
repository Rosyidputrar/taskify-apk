package com.rosyidputrar.taskify.core.store.search

import com.powersync.PowerSyncDatabase
import com.powersync.db.SqlCursor
import com.rosyidputrar.taskify.core.domain.files.EntityType
import com.rosyidputrar.taskify.core.domain.search.EntityRow
import com.rosyidputrar.taskify.core.domain.search.Search
import com.rosyidputrar.taskify.core.domain.search.TagHit

/**
 * Subquery (type, id, title, subtitle, deleted_at, updated_at) untuk semua entitas — SAMA dengan
 * `ENTITY_UNION` webapp. Dipakai pencarian, halaman tag, tautan silang, dan Sampah.
 */
const val ENTITY_UNION = """
  SELECT 'task' AS type, id, title, due_date AS subtitle, deleted_at, updated_at FROM tasks
  UNION ALL
  SELECT 'journal_entry', id, COALESCE(NULLIF(title, ''), substr(content, 1, 80), 'Jurnal'), entry_date, deleted_at, updated_at
    FROM journal_entries
  UNION ALL
  SELECT 'note', id, COALESCE(NULLIF(title, ''), NULLIF(substr(content, 1, 80), ''), 'Catatan'), note_date, deleted_at, updated_at
    FROM notes
  UNION ALL
  SELECT 'transaction', t.id,
         COALESCE(NULLIF(t.title, ''), c.name, CASE t.kind WHEN 'income' THEN 'Pemasukan' WHEN 'expense' THEN 'Pengeluaran' ELSE 'Transfer' END),
         t.occurred_on || '|' || t.kind || '|' || t.amount, t.deleted_at, t.updated_at
    FROM transactions t LEFT JOIN categories c ON c.id = t.category_id
  UNION ALL
  SELECT 'snippet', id, title, language, deleted_at, updated_at FROM snippets
  UNION ALL
  SELECT 'code_project', id, name, NULL, deleted_at, updated_at FROM code_projects"""

fun SqlCursor.entityRowOrNull(): EntityRow? = entityRow()

internal fun SqlCursor.entityRow(snippetIndex: Int? = null): EntityRow? {
    val type = EntityType.from(getString(0)) ?: return null
    return EntityRow(type, getString(1)!!, (getString(2) ?: "").replace(Regex("\\s+"), " ").trim(), getString(3), getString(4), snippetIndex?.let { getString(it) })
}

/**
 * Indeks pencarian global (SQLite FTS5, LOKAL — tidak disinkron), port `search-index.ts`.
 *
 * PowerSync menyimpan tiap tabel sebagai `ps_data__<tabel>(id, data JSON)`. Trigger di tabel
 * internal itu menangkap SEMUA perubahan (tulisan lokal maupun hasil sync) lalu memperbarui
 * `search_index`. Baris soft-deleted dikeluarkan. Tanpa FTS5 → fallback LIKE pada judul.
 */
class SearchRepository(private val db: PowerSyncDatabase) {
    @Volatile var ftsReady: Boolean = false
        private set

    private data class Source(val table: String, val type: String, val title: String, val body: String)

    private fun j(field: String) = "COALESCE(json_extract(d, '$.$field'), '')"

    private val sources = listOf(
        Source("tasks", "task", j("title"), j("notes")),
        Source("journal_entries", "journal_entry", "COALESCE(json_extract(d, '$.title'), json_extract(d, '$.entry_date'), '')", j("content")),
        Source("notes", "note", j("title"), j("content")),
        Source("transactions", "transaction", j("title"), "${j("note")} || ' ' || COALESCE(json_extract(d, '$.amount'), '')"),
        Source("snippets", "snippet", j("title"), "${j("description")} || ' ' || ${j("code")}"),
        Source("code_projects", "code_project", j("name"), j("description")),
    )

    /** Ganti alias `d` dengan kolom JSON baris trigger. */
    private fun sub(expr: String, ref: String) = expr.replace(Regex("\\bd\\b(?=,)"), ref)

    /** Pasang tabel FTS + trigger; bangun ulang bila versi berubah. Aman dipanggil berkali-kali. */
    suspend fun setup(): Boolean {
        try {
            db.execute(
                """CREATE VIRTUAL TABLE IF NOT EXISTS search_index USING fts5(
                     entity_type UNINDEXED, entity_id UNINDEXED, title, body,
                     tokenize = 'unicode61 remove_diacritics 2')""",
            )
        } catch (e: Exception) {
            ftsReady = false
            return false
        }
        db.execute("CREATE TABLE IF NOT EXISTS search_meta (key TEXT PRIMARY KEY, value TEXT)")
        val version = db.getOptional("SELECT COALESCE(value, '') FROM search_meta WHERE key = 'version'") { it.getString(0) ?: "" }
        val fresh = version != INDEX_VERSION.toString()
        db.writeTransaction { tx ->
            for (s in sources) {
                val internal = "ps_data__${s.table}"
                val name = "search_${s.table}"
                if (fresh) listOf("ins", "upd", "del").forEach { tx.execute("DROP TRIGGER IF EXISTS ${name}_$it") }
                fun insert(ref: String) = """
                    INSERT INTO search_index (entity_type, entity_id, title, body)
                    SELECT '${s.type}', $ref.id, ${sub(s.title, "$ref.data")}, ${sub(s.body, "$ref.data")}
                     WHERE json_extract($ref.data, '$.deleted_at') IS NULL;"""
                fun remove(ref: String) = "DELETE FROM search_index WHERE entity_type = '${s.type}' AND entity_id = $ref.id;"
                tx.execute("CREATE TRIGGER IF NOT EXISTS ${name}_ins AFTER INSERT ON $internal BEGIN ${insert("NEW")} END")
                tx.execute("CREATE TRIGGER IF NOT EXISTS ${name}_upd AFTER UPDATE ON $internal BEGIN ${remove("OLD")} ${insert("NEW")} END")
                tx.execute("CREATE TRIGGER IF NOT EXISTS ${name}_del AFTER DELETE ON $internal BEGIN ${remove("OLD")} END")
            }
            if (fresh) {
                tx.execute("DELETE FROM search_index")
                for (s in sources) {
                    tx.execute(
                        """INSERT INTO search_index (entity_type, entity_id, title, body)
                           SELECT '${s.type}', id, ${sub(s.title, "data")}, ${sub(s.body, "data")}
                             FROM ps_data__${s.table} WHERE json_extract(data, '$.deleted_at') IS NULL""",
                    )
                }
                tx.execute("INSERT OR REPLACE INTO search_meta (key, value) VALUES ('version', ?)", listOf(INDEX_VERSION.toString()))
            }
        }
        ftsReady = true
        return true
    }

    /** Logout: indeks ikut dikosongkan (isinya salinan teks data pribadi). */
    suspend fun clear() {
        runCatching { db.execute("DELETE FROM search_index") }
        runCatching { db.execute("DELETE FROM search_meta") }
    }

    /** FTS5 (peringkat bm25, judul lebih berbobot) atau fallback LIKE; hanya yang tidak di Sampah. */
    suspend fun search(query: String, limit: Int = 30): List<EntityRow> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val match = Search.toFtsQuery(q)
        if (ftsReady && match != null) {
            val hits = runCatching {
                db.getAll(
                    """WITH entities AS ($ENTITY_UNION),
                            r AS (SELECT entity_type, entity_id,
                                         snippet(search_index, 3, '${Search.MARK_OPEN}', '${Search.MARK_CLOSE}', '…', 12) AS snippet,
                                         bm25(search_index, 0.0, 0.0, 6.0, 1.0) AS rank
                                    FROM search_index WHERE search_index MATCH ?1 ORDER BY rank LIMIT ?2)
                       SELECT e.type, e.id, e.title, e.subtitle, e.deleted_at, r.snippet
                         FROM r JOIN entities e ON e.type = r.entity_type AND e.id = r.entity_id
                        WHERE e.deleted_at IS NULL ORDER BY r.rank""",
                    listOf(match, limit.toLong()),
                ) { it.entityRow(5) ?: EMPTY }
            }.getOrNull()
            if (hits != null) return hits.filter { it !== EMPTY }
        }
        return db.getAll(
            """WITH entities AS ($ENTITY_UNION)
               SELECT type, id, title, subtitle, deleted_at FROM entities
                WHERE deleted_at IS NULL AND LOWER(title) LIKE '%' || LOWER(?1) || '%'
                ORDER BY updated_at DESC LIMIT ?2""",
            listOf(q, limit.toLong()),
        ) { it.entityRow() ?: EMPTY }.filter { it !== EMPTY }
    }

    suspend fun searchTags(query: String): List<TagHit> = db.getAll(
        """SELECT t.id, t.name, t.color, (SELECT COUNT(*) FROM taggings g WHERE g.tag_id = t.id) AS count
             FROM tags t WHERE t.deleted_at IS NULL AND t.name LIKE '%' || LOWER(?1) || '%' ORDER BY count DESC LIMIT 5""",
        listOf(query.trim().removePrefix("#")),
    ) { TagHit(it.getString(0)!!, it.getString(1) ?: "", it.getString(2), it.getLong(3)?.toInt() ?: 0) }

    companion object {
        /** Naikkan bila definisi kolom/trigger berubah → indeks dibangun ulang. Sama dengan webapp. */
        const val INDEX_VERSION = 2
        private val EMPTY = EntityRow(EntityType.Task, "", "", null)
    }
}
