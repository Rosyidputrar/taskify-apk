package com.rosyidputrar.taskify.core.domain.sync

import com.rosyidputrar.taskify.core.common.Hashes
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

enum class CrudOpType { PUT, PATCH, DELETE }

/** Bentuk minimal satu operasi antrean upload (terlepas dari tipe PowerSync). */
data class CrudOp(
    val op: CrudOpType,
    val table: String,
    val id: String,
    val opData: JsonObject?,
    val previousValues: Map<String, String?>? = null,
)

/**
 * Tabel yang kolom `enc`-nya dienkripsi DEK brankas Sandi. Harus sama dengan
 * `VAULT_ENCRYPTED_TABLES` webapp dan trigger server.
 */
val VAULT_ENCRYPTED_TABLES = setOf("pw_items", "pw_totp_log", "config_versions")

const val VAULT_ROTATION_TOO_LARGE = "23V04"
const val VAULT_ROTATION_INVALID = "23V05"

/** Transaksi penggantian kunci brankas: PATCH `pw_keys` yang mengubah `wrapped_key`. */
fun isVaultRotation(ops: List<CrudOp>): Boolean = ops.any {
    it.table == "pw_keys" && it.op == CrudOpType.PATCH && it.opData?.containsKey("wrapped_key") == true
}

/**
 * Argumen RPC `apply_vault_rotation`: `{ p_key, p_rows }`. Tiap baris membawa `b` = sidik
 * `enc` SEBELUM rotasi, sehingga server menolak bila baris sudah diubah perangkat lain.
 * null = bentuk transaksi tidak valid → ditolak utuh tanpa mengirim apa pun.
 */
fun rotationPayload(ops: List<CrudOp>): JsonObject? {
    val keyOps = ops.filter { it.table == "pw_keys" }
    val others = ops.filter { it.table != "pw_keys" }
    if (keyOps.size != 1 || keyOps[0].op != CrudOpType.PATCH) return null
    if (others.any { it.op != CrudOpType.PATCH || it.table !in VAULT_ENCRYPTED_TABLES }) return null
    val key = keyOps[0]
    val rows = buildJsonArray {
        others.forEach { op ->
            val before = op.previousValues?.get("enc")
            add(
                buildJsonObject {
                    put("t", JsonPrimitive(op.table))
                    put("id", JsonPrimitive(op.id))
                    put("enc", op.opData?.get("enc")?.takeIf { it !is JsonNull } ?: JsonNull)
                    put("v", op.opData?.get("v")?.takeIf { it !is JsonNull } ?: JsonNull)
                    put("b", before?.let { JsonPrimitive(Hashes.sha256Hex16(it)) } ?: JsonNull)
                },
            )
        }
    }
    val pKey = buildJsonObject {
        key.opData?.forEach { (k, v) -> put(k, v) }
        put("id", JsonPrimitive(key.id))
    }
    return buildJsonObject {
        put("p_key", pKey)
        put("p_rows", rows)
    }
}

/** Hasil RPC rotasi yang ditolak sebagai DATA (`{code, message}`), bukan error HTTP. */
fun rotationResultError(result: JsonElement?): PostgrestLikeError? {
    val obj = result as? JsonObject ?: return null
    val code = obj["code"]?.jsonPrimitive?.contentOrNull ?: return null
    return PostgrestLikeError(code, obj["message"]?.jsonPrimitive?.contentOrNull)
}
