package com.rosyidputrar.taskify.core.store.home

import com.powersync.PowerSyncDatabase
import com.powersync.db.SqlCursor
import com.rosyidputrar.taskify.core.domain.home.HomeShortcut
import com.rosyidputrar.taskify.core.domain.home.HomeShortcuts
import com.rosyidputrar.taskify.core.domain.home.ShortcutRef
import com.rosyidputrar.taskify.core.domain.home.ShortcutType
import com.rosyidputrar.taskify.core.store.search.ENTITY_UNION
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** ISO UTC dari kolom milidetik tabel lokal (ai_sessions) — sebanding dengan `updated_at` tersinkron. */
private fun isoFromMs(col: String) = "strftime('%Y-%m-%dT%H:%M:%fZ', $col / 1000.0, 'unixepoch')"

/**
 * Semua yang bisa disematkan: entitas umum (`ENTITY_UNION`, sama dengan pencarian & Sampah) +
 * sasaran khusus pintasan. Kolom: type, id, title, subtitle, deleted_at, updated_at.
 */
private val PIN_TARGETS = """$ENTITY_UNION
  UNION ALL
  SELECT 'config_file', id, name, environment, deleted_at, updated_at FROM config_files
  UNION ALL
  SELECT 'ai_session', id, COALESCE(NULLIF(title, ''), 'Percakapan'), model, NULL, ${isoFromMs("updated_ms")} FROM ai_sessions
  UNION ALL
  SELECT 'task_list', id, name, NULL, deleted_at, updated_at FROM task_lists
  UNION ALL
  SELECT 'wallet', id, name, type, deleted_at, updated_at FROM wallets
  UNION ALL
  SELECT 'tag', id, '#' || name, NULL, deleted_at, updated_at FROM tags"""

/**
 * Pintasan Hari ini: yang DISEMATKAN pengguna (tabel lokal `home_pins`, urutan bebas) dan
 * "Lanjutkan" otomatis (isi yang terakhir diubah). Judul selalu diambil hidup dari sumbernya —
 * catatan yang diganti judulnya ikut berganti; yang dihapus/di Sampah ditandai tidak tersedia.
 */
class HomeShortcutRepository(
    private val db: PowerSyncDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun watchPinned(): Flow<List<HomeShortcut>> = db.watch(
        """WITH e AS ($PIN_TARGETS)
           SELECT p.kind, p.target, p.title, e.title, e.subtitle,
                  CASE WHEN p.kind = 'view' THEN 1 WHEN e.id IS NOT NULL AND e.deleted_at IS NULL THEN 1 ELSE 0 END,
                  e.updated_at
             FROM home_pins p LEFT JOIN e ON e.type = p.kind AND e.id = p.target
            ORDER BY p.position, p.created_ms""",
    ) { it.raw(7) }.map { rows -> rows.mapNotNull { it.toPinned() } }

    /** Kunci yang disematkan — untuk tombol "Sematkan/Lepas" di layar lain (murah, tanpa join). */
    fun watchPinnedKeys(): Flow<Set<String>> =
        db.watch("SELECT id FROM home_pins") { it.getString(0)!! }.map { it.toSet() }.distinctUntilChanged()

    fun watchIsPinned(ref: ShortcutRef): Flow<Boolean> = watchPinnedKeys().map { ref.key in it }.distinctUntilChanged()

    /**
     * "Lanjutkan": isi yang terakhir DIBUKA di HP ini atau DIUBAH (di mana pun) — mana yang lebih
     * baru. Catatan, jurnal, snippet, project, konfigurasi, dan chat asisten; yang di Sampah dilewati.
     */
    fun watchRecent(limit: Int = HomeShortcuts.MAX_RECENT * 2): Flow<List<HomeShortcut>> = db.watch(
        """WITH src AS (
             SELECT 'note' AS type, id, COALESCE(NULLIF(title, ''), NULLIF(trim(substr(content, 1, 80)), ''), 'Catatan') AS title,
                    note_date AS subtitle, updated_at FROM notes WHERE deleted_at IS NULL
             UNION ALL
             SELECT 'journal_entry', id, COALESCE(NULLIF(title, ''), NULLIF(trim(substr(content, 1, 80)), ''), 'Jurnal'),
                    entry_date, updated_at FROM journal_entries WHERE deleted_at IS NULL
             UNION ALL
             SELECT 'snippet', id, title, language, updated_at FROM snippets WHERE deleted_at IS NULL
             UNION ALL
             SELECT 'code_project', id, name, NULL, updated_at FROM code_projects WHERE deleted_at IS NULL
             UNION ALL
             SELECT 'config_file', id, name, environment, updated_at FROM config_files WHERE deleted_at IS NULL
             UNION ALL
             SELECT 'ai_session', id, COALESCE(NULLIF(title, ''), 'Percakapan'), model, ${isoFromMs("updated_ms")} FROM ai_sessions
           ), seen AS (
             SELECT s.type, s.id, s.title, s.subtitle,
                    CASE WHEN r.opened_ms IS NOT NULL
                              AND (s.updated_at IS NULL OR julianday(${isoFromMs("r.opened_ms")}) > julianday(s.updated_at))
                         THEN ${isoFromMs("r.opened_ms")} ELSE s.updated_at END AS at
               FROM src s LEFT JOIN home_recent r ON r.kind = s.type AND r.target = s.id
           )
           SELECT type, id, title, subtitle, at FROM seen WHERE at IS NOT NULL
           ORDER BY julianday(at) DESC LIMIT ?""",
        listOf(limit.toLong()),
    ) { it.raw(5) }.map { rows ->
        rows.mapNotNull { r ->
            val type = ShortcutType.from(r[0]) ?: return@mapNotNull null
            HomeShortcut(ShortcutRef(type, r[1]!!), clean(r[2]), r[3], updatedAt = r[4])
        }
    }

    /** Catat "baru dibuka" (dari layar editor/baca). Disimpan 60 terakhir saja. */
    suspend fun recordOpen(type: String, id: String) {
        val t = ShortcutType.from(type) ?: return
        if (id.isBlank() || t == ShortcutType.View) return
        val ref = ShortcutRef(t, id)
        db.writeTransaction { tx ->
            tx.execute("DELETE FROM home_recent WHERE id = ?", listOf(ref.key))
            tx.execute("INSERT INTO home_recent (id, kind, target, opened_ms) VALUES (?, ?, ?, ?)", listOf(ref.key, t.code, id, clock()))
            tx.execute("DELETE FROM home_recent WHERE id NOT IN (SELECT id FROM home_recent ORDER BY opened_ms DESC LIMIT $RECENT_KEEP)")
        }
    }

    /** Sasaran khusus yang bisa dipilih di lembar "Tambah pintasan" (daftar tugas, dompet, tag). */
    fun watchCollections(): Flow<List<HomeShortcut>> = db.watch(
        """SELECT 'task_list', id, name, NULL FROM task_lists WHERE deleted_at IS NULL AND COALESCE(is_archived, 0) = 0
           UNION ALL
           SELECT 'wallet', id, name, type FROM wallets WHERE deleted_at IS NULL AND COALESCE(is_archived, 0) = 0
           UNION ALL
           SELECT 'tag', id, '#' || name, NULL FROM tags WHERE deleted_at IS NULL""",
    ) { it.raw(4) }.map { rows ->
        rows.mapNotNull { r ->
            val type = ShortcutType.from(r[0]) ?: return@mapNotNull null
            HomeShortcut(ShortcutRef(type, r[1]!!), clean(r[2]), r[3])
        }
    }

    /**
     * Sematkan di AKHIR baris. Sudah tersemat → tidak berubah. Penuh ([HomeShortcuts.MAX_PINNED])
     * → false, supaya pemanggil bisa memberi tahu pengguna.
     */
    suspend fun pin(ref: ShortcutRef, title: String): Boolean {
        val count = db.get("SELECT COUNT(*) FROM home_pins") { it.getLong(0)!! }
        val exists = db.getOptional("SELECT 1 FROM home_pins WHERE id = ?", listOf(ref.key)) { 1 } != null
        if (exists) return true
        if (count >= HomeShortcuts.MAX_PINNED) return false
        val next = db.get("SELECT COALESCE(MAX(position), -1) + 1 FROM home_pins") { it.getDouble(0)!! }
        db.execute(
            "INSERT INTO home_pins (id, kind, target, title, position, created_ms) VALUES (?, ?, ?, ?, ?, ?)",
            listOf(ref.key, ref.type.code, ref.target, title.trim().take(TITLE_MAX), next, clock()),
        )
        return true
    }

    suspend fun unpin(ref: ShortcutRef) {
        db.execute("DELETE FROM home_pins WHERE id = ?", listOf(ref.key))
    }

    /** Sematkan ↔ lepas. Hasil: true = sekarang tersemat, false = dilepas, null = penuh. */
    suspend fun toggle(ref: ShortcutRef, title: String): Boolean? {
        val pinned = db.getOptional("SELECT 1 FROM home_pins WHERE id = ?", listOf(ref.key)) { 1 } != null
        if (pinned) { unpin(ref); return false }
        return if (pin(ref, title)) true else null
    }

    /** Geser satu pintasan [by] langkah (negatif = ke kiri/atas); urutan dinormalisasi 0..n-1. */
    suspend fun move(ref: ShortcutRef, by: Int) {
        db.writeTransaction { tx ->
            val keys = tx.getAll("SELECT id FROM home_pins ORDER BY position, created_ms") { it.getString(0)!! }.toMutableList()
            val i = keys.indexOf(ref.key)
            if (i < 0) return@writeTransaction
            val j = (i + by).coerceIn(0, keys.lastIndex)
            if (i == j) return@writeTransaction
            keys.add(j, keys.removeAt(i))
            keys.forEachIndexed { pos, key -> tx.execute("UPDATE home_pins SET position = ? WHERE id = ?", listOf(pos.toDouble(), key)) }
        }
    }

    /** Buang sematan yang sasarannya sudah tidak ada (dari "Susun Beranda"). */
    suspend fun removeUnavailable(keys: Collection<String>) {
        if (keys.isEmpty()) return
        db.writeTransaction { tx -> keys.forEach { tx.execute("DELETE FROM home_pins WHERE id = ?", listOf(it)) } }
    }

    /** Baris mentah sebagai teks (nilai bisa null) — dipetakan di luar kursor. */
    private fun SqlCursor.raw(n: Int): List<String?> = List(n) { getString(it) }

    /** kind, target, judul tersimpan, judul hidup, subjudul, tersedia(0/1), updated_at. */
    private fun List<String?>.toPinned(): HomeShortcut? {
        val type = ShortcutType.from(this[0]) ?: return null
        val ref = ShortcutRef(type, this[1] ?: return null)
        if (type == ShortcutType.View && ref.view == null) return null
        return HomeShortcut(
            ref = ref,
            title = clean(this[3] ?: this[2]),
            subtitle = this[4],
            pinned = true,
            available = this[5] == "1",
            updatedAt = this[6],
        )
    }

    private fun clean(s: String?): String = (s ?: "").replace(Regex("\\s+"), " ").trim()

    companion object {
        const val TITLE_MAX = 120
        const val RECENT_KEEP = 60
    }
}
