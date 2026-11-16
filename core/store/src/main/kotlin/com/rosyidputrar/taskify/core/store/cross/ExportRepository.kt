package com.rosyidputrar.taskify.core.store.cross

import com.powersync.PowerSyncDatabase
import kotlinx.serialization.json.JsonPrimitive
import java.io.OutputStream
import java.time.Instant

/**
 * Ekspor seluruh data dari SQLite lokal ke satu file JSON — jalan offline, format SAMA dengan
 * `export-data.ts` webapp (format 1). Termasuk item di Sampah agar cadangan lengkap; isi file
 * lampiran tetap di R2 (hanya metadata). Baris dibaca dari tabel internal PowerSync
 * (`ps_data__<tabel>`: id + JSON data) supaya tipe kolom terjaga persis seperti tersinkron.
 */
class ExportRepository(private val db: PowerSyncDatabase) {

    suspend fun export(out: OutputStream, userId: String?, appVersion: String, nowMs: Long = System.currentTimeMillis()): Int {
        var rows = 0
        val w = out.bufferedWriter(Charsets.UTF_8)
        fun str(s: String?) = if (s == null) "null" else JsonPrimitive(s).toString()
        w.write("{\n")
        w.write("  \"app\": \"taskify\",\n  \"format\": 1,\n  \"version\": ${str(appVersion)},\n")
        w.write("  \"exported_at\": ${str(Instant.ofEpochMilli(nowMs).toString())},\n  \"user_id\": ${str(userId)},\n")
        w.write("  \"note\": ${str(NOTE)},\n  \"tables\": {\n")
        TABLES.forEachIndexed { ti, table ->
            val list = db.getAll("SELECT json_set(COALESCE(data, '{}'), '$.id', id) FROM ps_data__$table ORDER BY id") { it.getString(0) ?: "{}" }
            rows += list.size
            w.write("    ${str(table)}: [")
            list.forEachIndexed { i, json -> w.write(if (i == 0) "\n      " else ",\n      "); w.write(json) }
            w.write(if (list.isEmpty()) "]" else "\n    ]")
            w.write(if (ti < TABLES.lastIndex) ",\n" else "\n")
        }
        w.write("  }\n}\n")
        w.flush()
        return rows
    }

    companion object {
        /** Tabel tersinkron yang ikut diekspor — sama dengan `EXPORT_TABLES` webapp. */
        val TABLES = listOf(
            "user_settings", "tags", "taggings", "entity_links", "task_lists", "tasks", "journal_entries", "notes", "wallets",
            "categories", "debts", "recurring_transactions", "transactions", "budgets", "snippets", "code_projects", "attachments",
            "pw_keys", "pw_items", "pw_totp_log", "config_files", "config_versions", "temp_files",
        )
        const val NOTE = "Uang dalam rupiah utuh (integer). Tanggal tanpa jam = WIB. Isi file lampiran ada di Cloudflare R2."
    }
}
