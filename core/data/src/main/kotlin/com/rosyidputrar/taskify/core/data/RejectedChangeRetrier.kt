package com.rosyidputrar.taskify.core.data

import com.rosyidputrar.taskify.core.common.Timestamps
import com.rosyidputrar.taskify.core.domain.sync.CrudOp
import com.rosyidputrar.taskify.core.domain.sync.PostgrestLikeError
import com.rosyidputrar.taskify.core.domain.sync.isFatal
import com.rosyidputrar.taskify.core.domain.sync.isSchemaError
import com.rosyidputrar.taskify.core.domain.sync.rejectedOpForRetry

/** Bagian `upload_errors` yang dibutuhkan "Coba lagi". */
interface UploadErrorStore {
    suspend fun deleteUploadError(id: String)
    suspend fun updateUploadError(id: String, code: String?, message: String?, at: String)
}

/**
 * "Coba lagi" untuk perubahan yang ditolak server. Operasi dikirim ulang APA ADANYA langsung ke
 * server; bila diterima, barisnya dihapus dari `upload_errors` dan versi server mengalir kembali
 * lewat sinkron. Masih ditolak → alasan terbaru disimpan. Error sementara (jaringan/5xx) tidak
 * mengubah apa-apa.
 */
class RejectedChangeRetrier(
    private val database: UploadErrorStore,
    private val send: suspend (CrudOp) -> PostgrestLikeError?,
) {
    constructor(database: TaskifyDatabase, connector: SupabaseConnector) : this(database, connector::applyOp)

    sealed interface Outcome {
        data object Sent : Outcome
        data class StillRejected(val message: String) : Outcome
        data class Unreachable(val message: String) : Outcome
        data object NotRetryable : Outcome
    }

    suspend fun retry(row: UploadErrorRow): Outcome {
        val op = rejectedOpForRetry(row.op, row.tableName, row.rowId, row.payload) ?: return Outcome.NotRetryable
        val error = send(op)
        if (error == null) {
            database.deleteUploadError(row.id)
            return Outcome.Sent
        }
        return if (isFatal(error.code) || isSchemaError(error.code)) {
            database.updateUploadError(row.id, error.code, error.message, Timestamps.nowIso())
            Outcome.StillRejected(error.message ?: "Ditolak server")
        } else {
            Outcome.Unreachable(error.message ?: "Server tidak bisa dihubungi")
        }
    }

    data class Summary(val sent: Int, val rejected: Int, val unreachable: Int, val skipped: Int) {
        val text: String get() = when {
            unreachable > 0 && sent == 0 && rejected == 0 -> "Server tidak bisa dihubungi. Periksa internet lalu coba lagi."
            else -> listOfNotNull(
                sent.takeIf { it > 0 }?.let { "$it terkirim" },
                rejected.takeIf { it > 0 }?.let { "$it masih ditolak" },
                unreachable.takeIf { it > 0 }?.let { "$it gagal terhubung" },
                skipped.takeIf { it > 0 }?.let { "$it tidak bisa dikirim ulang" },
            ).joinToString(" · ").ifEmpty { "Tidak ada yang dikirim" }
        }
    }

    suspend fun retryAll(rows: List<UploadErrorRow>): Summary {
        var sent = 0; var rejected = 0; var unreachable = 0; var skipped = 0
        // Urut dari yang paling lama: perubahan yang bergantung (mis. sub-tugas setelah induk) ikut berurutan.
        for (row in rows.sortedBy { it.createdAt }) {
            when (retry(row)) {
                Outcome.Sent -> sent++
                is Outcome.StillRejected -> rejected++
                is Outcome.Unreachable -> unreachable++
                Outcome.NotRetryable -> skipped++
            }
        }
        return Summary(sent, rejected, unreachable, skipped)
    }
}
