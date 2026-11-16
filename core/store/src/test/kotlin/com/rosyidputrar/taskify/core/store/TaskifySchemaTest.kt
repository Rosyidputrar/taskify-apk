package com.rosyidputrar.taskify.core.store

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Skema lokal harus identik dengan webapp (`src/db/schema.ts`), karena HP & laptop memakai
 * Postgres dan Sync Streams yang sama. Daftar di bawah dibangkitkan dari schema.ts webapp;
 * bila webapp menambah kolom, perbarui daftar ini DAN TaskifySchema.
 */
class TaskifySchemaTest {
    private val expected: Map<String, Map<String, String>> = mapOf(
        "user_settings" to mapOf("user_id" to "TEXT", "created_at" to "TEXT", "updated_at" to "TEXT", "display_name" to "TEXT", "currency" to "TEXT", "locale" to "TEXT", "timezone" to "TEXT", "week_start" to "INTEGER"),
        "tags" to mapOf("user_id" to "TEXT", "created_at" to "TEXT", "updated_at" to "TEXT", "deleted_at" to "TEXT", "name" to "TEXT", "color" to "TEXT"),
        "taggings" to mapOf("user_id" to "TEXT", "created_at" to "TEXT", "updated_at" to "TEXT", "tag_id" to "TEXT", "entity_type" to "TEXT", "entity_id" to "TEXT"),
        "entity_links" to mapOf("user_id" to "TEXT", "created_at" to "TEXT", "updated_at" to "TEXT", "source_type" to "TEXT", "source_id" to "TEXT", "target_type" to "TEXT", "target_id" to "TEXT"),
        "task_lists" to mapOf("user_id" to "TEXT", "created_at" to "TEXT", "updated_at" to "TEXT", "deleted_at" to "TEXT", "name" to "TEXT", "color" to "TEXT", "icon" to "TEXT", "position" to "REAL", "is_archived" to "INTEGER"),
        "tasks" to mapOf("user_id" to "TEXT", "created_at" to "TEXT", "updated_at" to "TEXT", "deleted_at" to "TEXT", "list_id" to "TEXT", "parent_id" to "TEXT", "series_id" to "TEXT", "title" to "TEXT", "notes" to "TEXT", "status" to "TEXT", "priority" to "INTEGER", "due_date" to "TEXT", "due_time" to "TEXT", "remind_at" to "TEXT", "rrule" to "TEXT", "completed_at" to "TEXT", "position" to "REAL"),
        "journal_entries" to mapOf("user_id" to "TEXT", "created_at" to "TEXT", "updated_at" to "TEXT", "deleted_at" to "TEXT", "entry_date" to "TEXT", "title" to "TEXT", "content" to "TEXT", "mood" to "INTEGER", "is_pinned" to "INTEGER"),
        "notes" to mapOf("user_id" to "TEXT", "created_at" to "TEXT", "updated_at" to "TEXT", "deleted_at" to "TEXT", "note_date" to "TEXT", "title" to "TEXT", "content" to "TEXT", "is_pinned" to "INTEGER"),
        "wallets" to mapOf("user_id" to "TEXT", "created_at" to "TEXT", "updated_at" to "TEXT", "deleted_at" to "TEXT", "name" to "TEXT", "type" to "TEXT", "currency" to "TEXT", "initial_balance" to "INTEGER", "color" to "TEXT", "icon" to "TEXT", "position" to "REAL", "is_archived" to "INTEGER"),
        "categories" to mapOf("user_id" to "TEXT", "created_at" to "TEXT", "updated_at" to "TEXT", "deleted_at" to "TEXT", "parent_id" to "TEXT", "name" to "TEXT", "kind" to "TEXT", "color" to "TEXT", "icon" to "TEXT", "position" to "REAL", "is_archived" to "INTEGER"),
        "debts" to mapOf("user_id" to "TEXT", "created_at" to "TEXT", "updated_at" to "TEXT", "deleted_at" to "TEXT", "direction" to "TEXT", "counterparty" to "TEXT", "amount" to "INTEGER", "due_date" to "TEXT", "note" to "TEXT", "settled_at" to "TEXT"),
        "recurring_transactions" to mapOf("user_id" to "TEXT", "created_at" to "TEXT", "updated_at" to "TEXT", "deleted_at" to "TEXT", "kind" to "TEXT", "amount" to "INTEGER", "wallet_id" to "TEXT", "to_wallet_id" to "TEXT", "category_id" to "TEXT", "title" to "TEXT", "note" to "TEXT", "rrule" to "TEXT", "start_on" to "TEXT", "end_on" to "TEXT", "next_run_on" to "TEXT", "auto_post" to "INTEGER", "is_active" to "INTEGER"),
        "transactions" to mapOf("user_id" to "TEXT", "created_at" to "TEXT", "updated_at" to "TEXT", "deleted_at" to "TEXT", "kind" to "TEXT", "amount" to "INTEGER", "wallet_id" to "TEXT", "to_wallet_id" to "TEXT", "category_id" to "TEXT", "occurred_on" to "TEXT", "occurred_time" to "TEXT", "title" to "TEXT", "note" to "TEXT", "task_id" to "TEXT", "recurring_id" to "TEXT", "debt_id" to "TEXT"),
        "budgets" to mapOf("user_id" to "TEXT", "created_at" to "TEXT", "updated_at" to "TEXT", "deleted_at" to "TEXT", "category_id" to "TEXT", "amount" to "INTEGER", "start_month" to "TEXT", "end_month" to "TEXT", "alert_pct" to "INTEGER"),
        "snippets" to mapOf("user_id" to "TEXT", "created_at" to "TEXT", "updated_at" to "TEXT", "deleted_at" to "TEXT", "title" to "TEXT", "language" to "TEXT", "code" to "TEXT", "description" to "TEXT", "is_favorite" to "INTEGER"),
        "code_projects" to mapOf("user_id" to "TEXT", "created_at" to "TEXT", "updated_at" to "TEXT", "deleted_at" to "TEXT", "name" to "TEXT", "description" to "TEXT", "repo_url" to "TEXT", "is_favorite" to "INTEGER", "position" to "REAL"),
        "attachments" to mapOf("user_id" to "TEXT", "created_at" to "TEXT", "updated_at" to "TEXT", "deleted_at" to "TEXT", "entity_type" to "TEXT", "entity_id" to "TEXT", "storage_key" to "TEXT", "file_name" to "TEXT", "mime_type" to "TEXT", "size_bytes" to "INTEGER", "sha256" to "TEXT", "version_label" to "TEXT", "note" to "TEXT", "status" to "TEXT", "uploaded_at" to "TEXT"),
        "pw_keys" to mapOf("user_id" to "TEXT", "created_at" to "TEXT", "updated_at" to "TEXT", "version" to "INTEGER", "kdf" to "TEXT", "iterations" to "INTEGER", "salt" to "TEXT", "wrapped_key" to "TEXT", "recovery_salt" to "TEXT", "recovery_wrapped_key" to "TEXT", "rotated_from" to "TEXT", "labels_required_at" to "TEXT"),
        "pw_items" to mapOf("user_id" to "TEXT", "created_at" to "TEXT", "updated_at" to "TEXT", "deleted_at" to "TEXT", "enc" to "TEXT", "v" to "INTEGER"),
        "pw_totp_log" to mapOf("user_id" to "TEXT", "created_at" to "TEXT", "updated_at" to "TEXT", "enc" to "TEXT", "v" to "INTEGER"),
        "config_files" to mapOf("user_id" to "TEXT", "created_at" to "TEXT", "updated_at" to "TEXT", "deleted_at" to "TEXT", "project_id" to "TEXT", "name" to "TEXT", "format" to "TEXT", "environment" to "TEXT"),
        "config_versions" to mapOf("user_id" to "TEXT", "created_at" to "TEXT", "updated_at" to "TEXT", "file_id" to "TEXT", "enc" to "TEXT", "v" to "INTEGER"),
        "temp_files" to mapOf("user_id" to "TEXT", "created_at" to "TEXT", "updated_at" to "TEXT", "deleted_at" to "TEXT", "file_name" to "TEXT", "mime_type" to "TEXT", "size_bytes" to "INTEGER", "storage_key" to "TEXT", "status" to "TEXT", "etag" to "TEXT", "share_token" to "TEXT", "is_public" to "INTEGER", "expires_at" to "TEXT", "download_count" to "INTEGER", "last_download_at" to "TEXT"),
        "upload_errors" to mapOf("op" to "TEXT", "table_name" to "TEXT", "row_id" to "TEXT", "payload" to "TEXT", "error_code" to "TEXT", "error_message" to "TEXT", "created_at" to "TEXT"),
    )

    @Test fun schemaIsValid() = TaskifySchema.schema.validate()

    @Test fun tablesAndColumnsMatchWebapp() {
        val actual = TaskifySchema.all.associate { table -> table.name to table.columns.associate { it.name to it.type.name } }
        assertEquals(expected.keys, actual.keys)
        expected.forEach { (table, columns) -> assertEquals(columns, actual[table], "kolom tabel $table") }
    }

    @Test fun uploadErrorsIsLocalOnlyAndVaultTablesTrackOldEnc() {
        assertTrue(TaskifySchema.uploadErrors.options.localOnly)
        listOf(TaskifySchema.pwItems, TaskifySchema.pwTotpLog, TaskifySchema.configVersions).forEach {
            assertEquals(listOf("enc"), it.options.trackPreviousValues?.columnFilter, it.name)
        }
        assertEquals(23, TaskifySchema.syncedTableNames.size)
    }
}
