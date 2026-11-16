package com.rosyidputrar.taskify.core.data

import android.content.Context
import com.powersync.DatabaseDriverFactory
import com.powersync.PowerSyncDatabase
import com.powersync.sync.SyncStatusData
import com.rosyidputrar.taskify.core.store.TaskifySchema
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

/**
 * Database lokal tunggal (SQLite + PowerSync). Semua baca & tulis aplikasi lewat sini — UI tidak
 * pernah menunggu jaringan. PowerSync mengantrekan perubahan dan menyinkronkannya saat online.
 */
class TaskifyDatabase(
    private val context: Context,
    scope: CoroutineScope,
    private val devicePrefs: DevicePrefs,
) {
    val db: PowerSyncDatabase = PowerSyncDatabase(
        factory = DatabaseDriverFactory(context),
        schema = TaskifySchema.schema,
        dbFilename = DB_FILENAME,
        scope = scope,
    )

    val syncStatus: Flow<SyncStatusData> get() = db.currentStatus.asFlow()

    suspend fun connect(connector: SupabaseConnector) = db.connect(connector)

    suspend fun disconnect() = db.disconnect()

    /**
     * Pemilik database dicatat. Sesi bisa berakhir tanpa tombol Keluar (token dicabut); bila
     * kemudian akun LAIN masuk, isi database akun sebelumnya dikosongkan dulu. Akun yang SAMA
     * masuk lagi → tidak dikosongkan, jadi perubahan offline yang belum terunggah tetap terkirim.
     */
    suspend fun prepareFor(userId: String) {
        val owner = devicePrefs.dbOwner
        if (owner != null && owner != userId) wipe()
        devicePrefs.dbOwner = userId
    }

    /** Hapus seluruh isi database lokal, termasuk indeks pencarian buatan sendiri. */
    suspend fun wipe() {
        db.disconnectAndClear()
        try {
            db.execute("DELETE FROM search_index")
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Indeks FTS belum dibuat — tidak ada yang perlu dihapus.
        }
        devicePrefs.dbOwner = null
    }

    /** Jumlah operasi di antrean upload yang belum terkirim. */
    suspend fun pendingUploadCount(): Long =
        db.get("SELECT count(*) AS n FROM ps_crud") { it.getLong(0) ?: 0L }

    fun watchUploadErrorCount(): Flow<Long> =
        db.watch("SELECT count(*) AS n FROM upload_errors") { it.getLong(0) ?: 0L }.map { it.firstOrNull() ?: 0L }

    fun watchUploadErrors(): Flow<List<UploadErrorRow>> = db.watch(
        """SELECT id, op, table_name, row_id, payload, error_code, error_message, created_at
           FROM upload_errors ORDER BY created_at DESC""",
    ) { c ->
        UploadErrorRow(
            id = c.getString(0)!!,
            op = c.getString(1) ?: "",
            tableName = c.getString(2) ?: "",
            rowId = c.getString(3) ?: "",
            payload = c.getString(4),
            errorCode = c.getString(5),
            errorMessage = c.getString(6),
            createdAt = c.getString(7) ?: "",
        )
    }

    suspend fun clearUploadErrors() {
        db.execute("DELETE FROM upload_errors")
    }

    fun databaseSizeBytes(): Long {
        val main = context.getDatabasePath(DB_FILENAME)
        return listOf(main, File(main.path + "-wal"), File(main.path + "-shm")).sumOf { if (it.exists()) it.length() else 0L }
    }

    companion object {
        const val DB_FILENAME = "taskify.db"
    }
}

data class UploadErrorRow(
    val id: String,
    val op: String,
    val tableName: String,
    val rowId: String,
    val payload: String?,
    val errorCode: String?,
    val errorMessage: String?,
    val createdAt: String,
)
