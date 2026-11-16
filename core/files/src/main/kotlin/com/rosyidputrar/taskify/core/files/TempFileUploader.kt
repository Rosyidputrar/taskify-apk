package com.rosyidputrar.taskify.core.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.rosyidputrar.taskify.core.common.Ids
import com.rosyidputrar.taskify.core.domain.files.ExpiryOption
import com.rosyidputrar.taskify.core.domain.files.TempFile
import com.rosyidputrar.taskify.core.domain.files.TempFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class UploadPhase(val label: String) {
    Reserving("Memesan tempat…"), Uploading("Mengunggah"), Verifying("Memeriksa…"), Done("Selesai"), Failed("Gagal"), Cancelled("Dibatalkan");

    val active get() = this == Reserving || this == Uploading || this == Verifying
}

/** Satu unggahan berjalan. [key] = id lokal; [fileId] = id berkas setelah dipesan di server. */
data class UploadJob(
    val key: String,
    val name: String,
    val size: Long,
    val progress: Float = 0f,
    val phase: UploadPhase = UploadPhase.Reserving,
    val error: String? = null,
    val fileId: String? = null,
)

/** Berkas yang dipilih pengguna (dari SAF / Share). */
data class PickedFile(val uri: Uri, val name: String, val size: Long, val mime: String)

/**
 * Unggahan Berkas publik + hasil server yang belum tiba lewat sinkron (padanan `tempfile-store.ts`).
 * Hidup di cakupan aplikasi supaya unggahan tetap jalan saat pengguna berpindah layar.
 * Alur: pesan tempat (kuota) → PUT ke R2 langsung dari `content://` → server memeriksa ukuran.
 */
class TempFileUploader(private val context: Context, private val api: TempFilesApi, private val transfers: Transfers, private val scope: CoroutineScope) {
    private val _jobs = MutableStateFlow<List<UploadJob>>(emptyList())
    val jobs: StateFlow<List<UploadJob>> = _jobs.asStateFlow()

    /** Baris terbaru dari server per id; null = sudah dihapus di server. */
    private val _overlay = MutableStateFlow<Map<String, TempFile?>>(emptyMap())
    val overlay: StateFlow<Map<String, TempFile?>> = _overlay.asStateFlow()

    private val running = mutableMapOf<String, Job>()

    private fun patch(key: String, f: (UploadJob) -> UploadJob) = _jobs.update { list -> list.map { if (it.key == key) f(it) else it } }
    fun dismiss(key: String) = _jobs.update { list -> list.filterNot { it.key == key } }
    private fun setOverlay(id: String, row: TempFile?) = _overlay.update { it + (id to row) }

    /** Buang overlay yang sudah tersusul sinkron. */
    fun settle(local: List<TempFile>) {
        val ids = TempFiles.settledOverlayIds(local, _overlay.value)
        if (ids.isNotEmpty()) _overlay.update { it - ids.toSet() }
    }

    /** Nama, ukuran & MIME dari `content://`. null bila ukuran tidak diketahui (tidak bisa dipesan). */
    fun describe(uri: Uri): PickedFile? {
        val resolver = context.contentResolver
        var name = "berkas"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 && !c.isNull(it) }?.let { name = c.getString(it) }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { size = c.getLong(it) }
            }
        }
        if (size < 0) size = runCatching { resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull() ?: -1
        if (size < 0) return null
        return PickedFile(uri, name, size, resolver.getType(uri) ?: "application/octet-stream")
    }

    fun start(file: PickedFile, expiry: ExpiryOption) {
        val key = Ids.newId()
        _jobs.update { listOf(UploadJob(key, file.name, file.size)) + it }
        running[key] = scope.launch {
            var fileId: String? = null
            var phase = UploadPhase.Reserving
            try {
                val (reserved, url) = api.create(file.name, file.size, file.mime, expiry)
                fileId = reserved.id
                setOverlay(reserved.id, reserved)
                phase = UploadPhase.Uploading
                patch(key) { it.copy(fileId = reserved.id, phase = phase) }
                withContext(Dispatchers.IO) {
                    transfers.putStream(url, file.size, file.mime, { context.contentResolver.openInputStream(file.uri) ?: throw FileTransferException("Berkas tidak bisa dibaca") }) { p ->
                        patch(key) { it.copy(progress = p) }
                    }
                }
                phase = UploadPhase.Verifying
                patch(key) { it.copy(phase = phase, progress = 1f) }
                // Jaringan putus tepat setelah PUT selesai: ulangi pemeriksaan beberapa kali
                var ready: TempFile? = null
                for (attempt in 0 until 3) {
                    try {
                        ready = api.complete(reserved.id); break
                    } catch (e: TempFileException) {
                        if (attempt == 2 || (e.code != null && e.code != "TF004")) throw e
                        delay(1_500L * (attempt + 1))
                    }
                }
                setOverlay(reserved.id, ready)
                patch(key) { it.copy(phase = UploadPhase.Done) }
                delay(4_000)
                dismiss(key)
            } catch (e: CancellationException) {
                patch(key) { it.copy(phase = UploadPhase.Cancelled) }
                // Bebaskan kuota yang sudah dipesan
                withContext(NonCancellable) {
                    fileId?.let { id -> runCatching { remove(id) } }
                    delay(2_500)
                    dismiss(key)
                }
            } catch (e: Exception) {
                patch(key) { it.copy(phase = UploadPhase.Failed, error = e.message ?: "Gagal mengunggah.") }
                // PUT tidak selesai → pesanan memakan kuota 2 jam, lepaskan. Gagal saat MEMERIKSA
                // tidak dilepas: objeknya mungkin sudah lengkap di server.
                if (fileId != null && phase == UploadPhase.Uploading) runCatching { remove(fileId) }
            } finally {
                running.remove(key)
            }
        }
    }

    fun cancel(key: String) { running[key]?.cancel() }

    suspend fun change(id: String, patch: TempFilePatch): TempFile = api.update(id, patch).also { setOverlay(id, it) }

    suspend fun remove(id: String) {
        api.delete(id)
        setOverlay(id, null)
    }

    /** Unduh salinan pemilik ke tujuan pilihan (SAF). */
    suspend fun downloadOwn(id: String, target: Uri, onProgress: (Float) -> Unit) {
        val url = api.ownerDownloadUrl(id)
        withContext(Dispatchers.IO) {
            transfers.downloadTo(url, { context.contentResolver.openOutputStream(target) ?: throw FileTransferException("Tujuan tidak bisa ditulis") }, onProgress)
        }
    }

    /** Unduh dari tautan publik (tanpa login) ke tujuan pilihan. */
    suspend fun downloadPublic(token: String, target: Uri, onProgress: (Float) -> Unit) = withContext(Dispatchers.IO) {
        transfers.downloadTo(api.directDownloadUrl(token), { context.contentResolver.openOutputStream(target) ?: throw FileTransferException("Tujuan tidak bisa ditulis") }, onProgress)
    }

    /** Logout: lupakan antrean & overlay (unggahan berjalan dibatalkan). */
    fun wipe() {
        running.values.toList().forEach { it.cancel() }
        _jobs.value = emptyList()
        _overlay.value = emptyMap()
    }
}
