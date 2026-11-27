package com.rosyidputrar.taskify.core.store

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
            t("file_name"), t("mime_type"), i("size_bytes"), t("storage_key"), t("status"), t("etag"), t("share_token"), t("slug"),
            i("is_public"), t("expires_at"), i("download_count"), t("last_download_at"),
        ),
    )

    /** LOCAL-ONLY: perubahan yang ditolak server permanen. Tidak pernah masuk antrean upload. */
    val uploadErrors = Table(
        name = "upload_errors",
        columns = listOf(t("op"), t("table_name"), t("row_id"), t("payload"), t("error_code"), t("error_message"), t("created_at")),
        localOnly = true,
    )

    /**
     * LOCAL-ONLY: riwayat Mode Fokus (Pomodoro) perangkat ini — belum ada tabelnya di server/webapp,
     * jadi tidak disinkronkan. Dibaca Review ("Waktu fokus"); ikut terhapus saat Keluar.
     */
    val focusSessions = Table(
        name = "focus_sessions",
        columns = listOf(t("task_id"), t("task_title"), t("day"), t("started_at"), t("ended_at"), i("minutes")),
        indexes = listOf(idx("focus_sessions_day", "day")),
        localOnly = true,
    )

    /**
     * LOCAL-ONLY: modul Notifikasi — notifikasi yang dicatat pendengar notifikasi di HP ini
     * (tidak pernah disinkronkan). Kolom money_* / draft = draf transaksi; parcel_* = status paket.
     */
    val notifLog = Table(
        name = "notif_log",
        columns = listOf(
            t("package"), t("app_label"), t("n_key"), t("conversation"), t("title"), t("text"), t("category"), i("posted_ms"), t("day"),
            i("hidden"), t("handled"), i("money_amount"), t("money_kind"), t("money_party"), t("money_source"), t("draft"),
            t("parcel_stage"), t("parcel_ref"),
        ),
        indexes = listOf(idx("notif_log_day", "day", "posted_ms"), idx("notif_log_draft", "draft"), idx("notif_log_key", "package", "n_key")),
        localOnly = true,
    )

    /** LOCAL-ONLY: jumlah notifikasi per aplikasi per hari (disimpan lebih lama dari isi notifikasi). */
    val notifDaily = Table(
        name = "notif_daily",
        columns = listOf(t("day"), t("package"), t("app_label"), i("count")),
        indexes = listOf(idx("notif_daily_day", "day")),
        localOnly = true,
    )

    /** LOCAL-ONLY: Kesehatan Digital — sesi aplikasi di layar. */
    val usageSessions = Table(
        name = "usage_sessions",
        columns = listOf(t("day"), t("package"), i("start_ms"), i("end_ms")),
        indexes = listOf(idx("usage_sessions_day", "day", "start_ms")),
        localOnly = true,
    )

    /** LOCAL-ONLY: rekap pemakaian per aplikasi per hari. */
    val usageDaily = Table(
        name = "usage_daily",
        columns = listOf(t("day"), t("package"), t("app_label"), i("foreground_ms"), i("opens"), i("longest_ms")),
        indexes = listOf(idx("usage_daily_day", "day"), idx("usage_daily_pkg", "package", "day")),
        localOnly = true,
    )

    /** LOCAL-ONLY: rekap perangkat per hari (waktu layar, buka kunci, pertama dipegang). */
    val deviceDaily = Table(
        name = "device_daily",
        columns = listOf(t("day"), i("screen_ms"), i("unlocks"), i("first_pickup_ms")),
        indexes = listOf(idx("device_daily_day", "day")),
        localOnly = true,
    )

    /**
     * LOCAL-ONLY: asal transaksi yang dicatat dari notifikasi (id = id transaksi). Ikon aplikasi hanya
     * ada di HP ini, jadi tidak disinkronkan — skema server & webapp tidak berubah.
     */
    val txSources = Table(
        name = "tx_sources",
        columns = listOf(t("package"), t("app_label")),
        localOnly = true,
    )

    /** LOCAL-ONLY: sesi chat asisten AI (riwayat pribadi; webapp tidak punya AI → tidak disinkronkan). */
    val aiSessions = Table(
        name = "ai_sessions",
        columns = listOf(t("title"), t("provider"), t("model"), t("system_prompt"), i("data_access"), i("created_ms"), i("updated_ms")),
        indexes = listOf(idx("ai_sessions_updated", "updated_ms")),
        localOnly = true,
    )

    /** LOCAL-ONLY: pesan chat AI, urut [seq] dalam satu sesi. */
    val aiMessages = Table(
        name = "ai_messages",
        columns = listOf(
            t("session_id"), i("seq"), t("role"), t("content"), t("tool_calls"), t("tool_call_id"), t("tool_name"),
            t("state"), t("error"), i("created_ms"),
        ),
        indexes = listOf(idx("ai_messages_session", "session_id", "seq")),
        localOnly = true,
    )

    /**
     * LOCAL-ONLY: pintasan yang disematkan ke Hari ini (id = `jenis:sasaran`). Milik akun di HP ini
     * — ikut terhapus saat keluar akun (disconnectAndClear); webapp tidak punya fitur ini.
     */
    val homePins = Table(
        name = "home_pins",
        columns = listOf(t("kind"), t("target"), t("title"), r("position"), i("created_ms")),
        localOnly = true,
    )

    /** LOCAL-ONLY: kapan isi terakhir DIBUKA di HP ini (id = `jenis:sasaran`) — untuk "Lanjutkan" di Hari ini. */
    val homeRecent = Table(
        name = "home_recent",
        columns = listOf(t("kind"), t("target"), i("opened_ms")),
        localOnly = true,
    )

    val all: List<Table> = listOf(
        userSettings, tags, taggings, entityLinks, taskLists, tasks, journalEntries, notes, wallets, categories, debts,
        recurringTransactions, transactions, budgets, snippets, codeProjects, attachments, pwKeys, pwItems, pwTotpLog,
        configFiles, configVersions, tempFiles, uploadErrors, focusSessions,
        notifLog, notifDaily, usageSessions, usageDaily, deviceDaily, txSources, aiSessions, aiMessages, homePins, homeRecent,
    )

    val schema = Schema(all)

    /** Tabel tersinkron (tanpa local-only) — urutan sama dengan EXPORT_TABLES webapp. */
    val syncedTableNames: List<String> = all.filterNot { it.options.localOnly }.map { it.name }
}
