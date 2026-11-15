package com.rosyidputrar.taskify.core.data

import com.powersync.PowerSyncDatabase
import com.powersync.connectors.PowerSyncBackendConnector
import com.powersync.connectors.PowerSyncCredentials
import com.powersync.db.crud.CrudEntry
import com.powersync.db.crud.UpdateType
import com.rosyidputrar.taskify.core.common.Ids
import com.rosyidputrar.taskify.core.common.Timestamps
import com.rosyidputrar.taskify.core.domain.auth.jwtAal
import com.rosyidputrar.taskify.core.domain.sync.CrudOp
import com.rosyidputrar.taskify.core.domain.sync.CrudOpType
import com.rosyidputrar.taskify.core.domain.sync.PostgrestLikeError
import com.rosyidputrar.taskify.core.domain.sync.ROW_MISSING_CODE
import com.rosyidputrar.taskify.core.domain.sync.UploadFailureTracker
import com.rosyidputrar.taskify.core.domain.sync.UploadVerdict
import com.rosyidputrar.taskify.core.domain.sync.VAULT_ROTATION_INVALID
import com.rosyidputrar.taskify.core.domain.sync.VAULT_ROTATION_TOO_LARGE
import com.rosyidputrar.taskify.core.domain.sync.isVaultRotation
import com.rosyidputrar.taskify.core.domain.sync.rotationPayload
import com.rosyidputrar.taskify.core.domain.sync.rotationResultError
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/** Penolakan server yang sudah diklasifikasi (dilempar di dalam uploadData). */
class UploadRejected(val error: PostgrestLikeError) : Exception(error.message ?: error.code)

/**
 * Jembatan PowerSync ↔ Supabase — port dari `db/connector.ts` webapp.
 *
 * - fetchCredentials: JWT sesi Supabase untuk PowerSync, HANYA bila sesi aal2. Token aal1 hanya
 *   menerima data kosong dari Sync Streams dan bisa mengosongkan salinan lokal.
 * - uploadData: antrean diproses per transaksi lokal & berurutan; PUT beruntun pada tabel yang
 *   sama digabung jadi satu upsert. Penolakan permanen disimpan ke `upload_errors` lalu antrean
 *   dilanjutkan; error sementara dilempar supaya PowerSync mencoba lagi dengan backoff.
 */
class SupabaseConnector(
    private val supabase: SupabaseClient,
    private val powerSyncUrl: String,
) : PowerSyncBackendConnector() {

    private val failures = UploadFailureTracker()

    override suspend fun fetchCredentials(): PowerSyncCredentials? {
        val session = freshSession() ?: return null
        if (!session.isAal2()) return null
        return PowerSyncCredentials(endpoint = powerSyncUrl, token = session.accessToken)
    }

    override suspend fun uploadData(database: PowerSyncDatabase) {
        val transaction = database.getNextCrudTransaction() ?: return
        if (!currentSessionIsAal2()) error("Sesi belum terverifikasi dua langkah; unggahan ditunda.")

        val ops = transaction.crud.map { it.toCrudOp() }
        var index = 0
        try {
            if (isVaultRotation(ops)) {
                // Satu permintaan, satu transaksi Postgres: diterima semua atau ditolak semua.
                applyVaultRotation(ops)?.let { throw UploadRejected(it) }
                index = ops.size
            }
            while (index < ops.size) {
                val batch = takeBatch(ops, index)
                applyBatch(batch)?.let { throw UploadRejected(it) }
                index += batch.size
            }
            transaction.complete(null)
            failures.reset()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = e.toPostgrestLike()
            val key = (transaction.transactionId ?: transaction.crud.firstOrNull()?.clientId ?: "").toString()
            // Sesi turun ke aal1 di tengah unggahan → penolakan RLS bukan salah data: tunda saja.
            if (failures.verdict(key, error.code) == UploadVerdict.Retry || !currentSessionIsAal2()) throw e

            val failed = ops.drop(index)
            database.writeTransactionAsync { tx ->
                failed.forEach { op ->
                    tx.executeAsync(
                        """INSERT INTO upload_errors
                           (id, op, table_name, row_id, payload, error_code, error_message, created_at)
                           VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                        listOf(
                            Ids.newId(), op.op.name, op.table, op.id, op.opData?.toString() ?: "null",
                            error.code, error.message, Timestamps.nowIso(),
                        ),
                    )
                }
            }
            transaction.complete(null)
        }
    }

    /** Terapkan satu operasi langsung ke server (untuk "Coba lagi" di daftar upload_errors). */
    suspend fun applyOp(op: CrudOp): PostgrestLikeError? = runCatching { applyBatch(listOf(op)) }
        .getOrElse { if (it is CancellationException) throw it else it.toPostgrestLike() }

    private suspend fun freshSession(): UserSession? {
        val auth = supabase.auth
        val session = auth.currentSessionOrNull() ?: return null
        if (session.expiresAt > Clock.System.now() + 30.seconds) return session
        return runCatching {
            auth.refreshCurrentSession()
            auth.currentSessionOrNull()
        }.getOrElse { if (it is CancellationException) throw it else session }
    }

    private fun currentSessionIsAal2(): Boolean = supabase.auth.currentSessionOrNull()?.isAal2() == true

    private fun UserSession.isAal2() = jwtAal(accessToken) == "aal2"

    /** Operasi beruntun yang bisa digabung: PUT pada tabel yang sama. */
    private fun takeBatch(ops: List<CrudOp>, start: Int): List<CrudOp> {
        val first = ops[start]
        if (first.op != CrudOpType.PUT) return listOf(first)
        var end = start + 1
        while (end < ops.size && ops[end].op == CrudOpType.PUT && ops[end].table == first.table) end++
        return ops.subList(start, end)
    }

    private suspend fun applyBatch(batch: List<CrudOp>): PostgrestLikeError? {
        val first = batch.first()
        val table = supabase.postgrest.from(first.table)
        when (first.op) {
            CrudOpType.PUT -> {
                val rows = batch.map { op ->
                    buildJsonObject {
                        op.opData?.forEach { (k, v) -> put(k, v) }
                        put("id", JsonPrimitive(op.id))
                    }
                }
                // defaultToNull = false → kolom yang tidak dikirim memakai DEFAULT Postgres.
                table.upsert(rows) { defaultToNull = false }
            }
            CrudOpType.PATCH -> {
                // `select id`: UPDATE yang tidak mengenai baris mana pun dianggap SUKSES oleh
                // PostgREST — tanpa pemeriksaan ini perubahannya hilang tanpa jejak.
                val result = table.update(first.opData ?: JsonObject(emptyMap())) {
                    select(Columns.list("id"))
                    filter { eq("id", first.id) }
                }
                val updated = result.decodeAs<JsonArray>()
                if (updated.isEmpty()) {
                    return PostgrestLikeError(ROW_MISSING_CODE, "Baris ${first.table}/${first.id} tidak ada di server")
                }
            }
            CrudOpType.DELETE -> table.delete { filter { eq("id", first.id) } }
        }
        return null
    }

    private suspend fun applyVaultRotation(ops: List<CrudOp>): PostgrestLikeError? {
        val payload = rotationPayload(ops)
            ?: return PostgrestLikeError(VAULT_ROTATION_INVALID, "Transaksi rotasi kunci brankas tidak valid")
        return try {
            val result = supabase.postgrest.rpc("apply_vault_rotation", payload)
            rotationResultError(result.decodeAsOrNull<kotlinx.serialization.json.JsonElement>())
        } catch (e: RestException) {
            if (e.statusCode == 413) {
                PostgrestLikeError(VAULT_ROTATION_TOO_LARGE, "Rotasi kunci brankas terlalu besar untuk satu permintaan")
            } else {
                e.toPostgrestLike()
            }
        }
    }
}

internal fun CrudEntry.toCrudOp(): CrudOp = CrudOp(
    op = when (op) {
        UpdateType.PUT -> CrudOpType.PUT
        UpdateType.PATCH -> CrudOpType.PATCH
        UpdateType.DELETE -> CrudOpType.DELETE
    },
    table = table,
    id = id,
    // jsonValues mempertahankan tipe (angka tetap angka), tidak semuanya jadi string.
    opData = opData?.jsonValues,
    previousValues = previousValues?.let { HashMap(it) },
)

internal fun Throwable.toPostgrestLike(): PostgrestLikeError = when (this) {
    is UploadRejected -> error
    is PostgrestRestException -> PostgrestLikeError(code, message)
    else -> PostgrestLikeError(null, message)
}
