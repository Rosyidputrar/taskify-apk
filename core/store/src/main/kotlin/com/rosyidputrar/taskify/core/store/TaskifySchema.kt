package com.rosyidputrar.taskify.core.data

import com.powersync.db.schema.Column
import com.powersync.db.schema.Index
import com.powersync.db.schema.Schema
import com.powersync.db.schema.Table
import com.powersync.db.schema.TrackPreviousValuesOptions

/**
 * Skema database LOKAL — cermin `src/db/schema.ts` webapp. HP dan laptop memakai Postgres &
 * Sync Streams yang sama, jadi nama tabel/kolom di sini HARUS identik dengan migrasi Supabase.
 *
 * Pemetaan tipe: uuid/text/date/time/timestamptz → text · boolean/smallint/bigint → integer
 * (boolean = 0/1) · double → real. Kolom `id` dibuat otomatis oleh PowerSync.
 */
object TaskifySchema {
    private fun t(name: String) = Column.text(name)
    private fun i(name: String) = Column.integer(name)
    private fun r(name: String) = Column.real(name)

    /** user_id, created_at, updated_at — ada di setiap tabel tersinkron. */
    private val base = listOf(t("user_id"), t("created_at"), t("updated_at"))
    private val softDelete = listOf(t("deleted_at"))

    /** Tabel terenkripsi DEK Sandi: nilai `enc` lama dilacak untuk transaksi rotasi kunci. */
    private val trackOldEnc = TrackPreviousValuesOptions(columnFilter = listOf("enc"))

    private fun table(
        name: String,
        columns: List<Column>,
        indexes: List<Index> = emptyList(),
        soft: Boolean = true,
        trackPrevious: TrackPreviousValuesOptions? = null,
    ) = Table(
        name = name,
        columns = base + (if (soft) softDelete else emptyList()) + columns,
        indexes = indexes,
        trackPreviousValues = trackPrevious,
    )

    private fun idx(name: String, vararg columns: String) = Index.ascending(name, columns.toList())

    val userSettings = table("user_settings", soft = false, columns = listOf(t("display_name"), t("currency"), t("locale"), t("timezone"), i("week_start")))
    val tags = table("tags", listOf(t("name"), t("color")))
    val taggings = table(
        "taggings", soft = false,
        columns = listOf(t("tag_id"), t("entity_type"), t("entity_id")),
        indexes = listOf(idx("taggings_entity", "entity_type", "entity_id"), idx("taggings_tag", "tag_id")),
    )
    val entityLinks = table(
        "entity_links", soft = false,
        columns = listOf(t("source_type"), t("source_id"), t("target_type"), t("target_id")),
        indexes = listOf(idx("entity_links_source", "source_type", "source_id"), idx("entity_links_target", "target_type", "target_id")),
    )
    val taskLists = table("task_lists", listOf(t("name"), t("color"), t("icon"), r("position"), i("is_archived")))
    val tasks = table(
        "tasks",
        columns = listOf(
            t("list_id"), t("parent_id"), t("series_id"), t("title"), t("notes"), t("status"), i("priority"),
            t("due_date"), t("due_time"), t("remind_at"), t("rrule"), t("completed_at"), r("position"),
        ),
        indexes = listOf(idx("tasks_due_date", "due_date"), idx("tasks_list", "list_id"), idx("tasks_parent", "parent_id")),
    )
    val journalEntries = table(
        "journal_entries",
        columns = listOf(t("entry_date"), t("title"), t("content"), i("mood"), i("is_pinned")),
        indexes = listOf(idx("journal_entries_date", "entry_date")),
    )
    val notes = table(
        "notes",
        columns = listOf(t("note_date"), t("title"), t("content"), i("is_pinned")),
        indexes = listOf(idx("notes_date", "note_date")),
    )
    val wallets = table("wallets", listOf(t("name"), t("type"), t("currency"), i("initial_balance"), t("color"), t("icon"), r("position"), i("is_archived")))
    val categories = table("categories", listOf(t("parent_id"), t("name"), t("kind"), t("color"), t("icon"), r("position"), i("is_archived")))
    val debts = table("debts", listOf(t("direction"), t("counterparty"), i("amount"), t("due_date"), t("note"), t("settled_at")))
    val recurringTransactions = table(
        "recurring_transactions",
        listOf(
            t("kind"), i("amount"), t("wallet_id"), t("to_wallet_id"), t("category_id"), t("title"), t("note"),
            t("rrule"), t("start_on"), t("end_on"), t("next_run_on"), i("auto_post"), i("is_active"),
        ),
    )
    val transactions = table(
        "transactions",
        columns = listOf(
            t("kind"), i("amount"), t("wallet_id"), t("to_wallet_id"), t("category_id"), t("occurred_on"), t("occurred_time"),
            t("title"), t("note"), t("task_id"), t("recurring_id"), t("debt_id"),
        ),
        indexes = listOf(idx("transactions_date", "occurred_on"), idx("transactions_wallet", "wallet_id"), idx("transactions_category", "category_id")),
    )
    val budgets = table("budgets", listOf(t("category_id"), i("amount"), t("start_month"), t("end_month"), i("alert_pct")))
    val snippets = table("snippets", listOf(t("title"), t("language"), t("code"), t("description"), i("is_favorite")))
    val codeProjects = table("code_projects", listOf(t("name"), t("description"), t("repo_url"), i("is_favorite"), r("position")))
    val attachments = table(
        "attachments",
        columns = listOf(
            t("entity_type"), t("entity_id"), t("storage_key"), t("file_name"), t("mime_type"), i("size_bytes"),
            t("sha256"), t("version_label"), t("note"), t("status"), t("uploaded_at"),
        ),
        indexes = listOf(idx("attachments_entity", "entity_type", "entity_id")),
    )
    val pwKeys = table(
        "pw_keys", soft = false,
        columns = listOf(
            i("version"), t("kdf"), i("iterations"), t("salt"), t("wrapped_key"), t("recovery_salt"),
            t("recovery_wrapped_key"), t("rotated_from"), t("labels_required_at"),
        ),
    )
    val pwItems = table("pw_items", listOf(t("enc"), i("v")), trackPrevious = trackOldEnc)
    val pwTotpLog = table("pw_totp_log", listOf(t("enc"), i("v")), soft = false, trackPrevious = trackOldEnc)
    val configFiles = table(
        "config_files",
        columns = listOf(t("project_id"), t("name"), t("format"), t("environment")),
        indexes = listOf(idx("config_files_project", "project_id")),
    )
    val configVersions = table(
        "config_versions", soft = false,
        columns = listOf(t("file_id"), t("enc"), i("v")),
        indexes = listOf(idx("config_versions_file", "file_id", "created_at")),
        trackPrevious = trackOldEnc,
    )
    val tempFiles = table(
        "temp_files",
        listOf(
            t("file_name"), t("mime_type"), i("size_bytes"), t("storage_key"), t("status"), t("etag"), t("share_token"),
            i("is_public"), t("expires_at"), i("download_count"), t("last_download_at"),
        ),
    )

    /** LOCAL-ONLY: perubahan yang ditolak server permanen. Tidak pernah masuk antrean upload. */
    val uploadErrors = Table(
        name = "upload_errors",
        columns = listOf(t("op"), t("table_name"), t("row_id"), t("payload"), t("error_code"), t("error_message"), t("created_at")),
        localOnly = true,
    )

    val all: List<Table> = listOf(
        userSettings, tags, taggings, entityLinks, taskLists, tasks, journalEntries, notes, wallets, categories, debts,
        recurringTransactions, transactions, budgets, snippets, codeProjects, attachments, pwKeys, pwItems, pwTotpLog,
        configFiles, configVersions, tempFiles, uploadErrors,
    )

    val schema = Schema(all)

    /** Tabel tersinkron (tanpa local-only) — urutan sama dengan EXPORT_TABLES webapp. */
    val syncedTableNames: List<String> = all.filterNot { it.name == "upload_errors" }.map { it.name }
}
