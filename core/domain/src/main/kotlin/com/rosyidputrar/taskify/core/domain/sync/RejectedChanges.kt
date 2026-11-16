package com.rosyidputrar.taskify.core.domain.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Perubahan yang ditolak server (baris `upload_errors`) → operasi yang bisa dikirim ulang.
 * null bila tidak bisa: payload rusak, jenis operasi tidak dikenal, atau bagian dari penggantian
 * kunci brankas Sandi (hanya sah lewat RPC atomik `apply_vault_rotation`, tidak boleh dikirim
 * satu per satu).
 */
fun rejectedOpForRetry(op: String, table: String, rowId: String, payload: String?): CrudOp? {
    val type = CrudOpType.entries.firstOrNull { it.name == op } ?: return null
    if (table.isBlank() || rowId.isBlank()) return null
    val data: JsonObject? = when {
        payload == null || payload == "null" -> null
        else -> runCatching { Json.parseToJsonElement(payload) as? JsonObject }.getOrNull() ?: return null
    }
    if (type != CrudOpType.DELETE && data == null) return null
    val crud = CrudOp(type, table, rowId, data)
    if (table == "pw_keys" || isVaultRotation(listOf(crud))) return null
    return crud
}

/**
 * Berapa penolakan BARU sejak terakhir dilihat. Emisi pertama (saat aplikasi dibuka) hanya
 * menjadi patokan — penolakan lama sudah ditampilkan di Pengaturan dan tidak diumumkan ulang.
 */
fun newRejections(previous: Long?, current: Long): Long = if (previous == null) 0 else (current - previous).coerceAtLeast(0)
