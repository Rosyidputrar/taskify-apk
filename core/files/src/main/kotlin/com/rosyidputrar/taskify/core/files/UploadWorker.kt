package com.rosyidputrar.taskify.core.files

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.rosyidputrar.taskify.core.domain.files.AttachmentStatus
import com.rosyidputrar.taskify.core.domain.files.MAX_AUTO_RETRIES
import com.rosyidputrar.taskify.core.store.files.AttachmentRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Satu unggahan lampiran (outbox): presign → PUT ke R2 dengan progres → status 'uploaded'.
 * Gagal → status 'failed' dan dicoba lagi otomatis (maks. [MAX_AUTO_RETRIES] kali, jeda ≥ 1 menit,
 * hanya saat ada jaringan) — kebijakan sama dengan outbox webapp. Salinan file di perangkat
 * dipakai, jadi upload tetap jalan setelah aplikasi ditutup.
 */
class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val repo: AttachmentRepository by inject()
    private val api: FilesApi by inject()
    private val store: FileStore by inject()
    private val transfers: Transfers by inject()

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val att = repo.get(id) ?: return Result.success()
        if (att.status == AttachmentStatus.Uploaded) return Result.success()
        if (!repo.isLive(id)) return Result.success()
        if (!store.has(id)) {
            repo.setStatus(id, AttachmentStatus.Failed)
            return Result.failure(workDataOf(KEY_ERROR to "File aslinya tidak ada di perangkat ini"))
        }
        repo.setStatus(id, AttachmentStatus.Pending)
        setProgress(workDataOf(KEY_PROGRESS to 0f))
        return try {
            val file = store.file(id)
            val mime = att.mimeType ?: "application/octet-stream"
            val url = api.uploadUrl(att.storageKey, file.length(), mime)
            var last = 0f
            transfers.put(url, file, mime) { f ->
                if (f - last >= 0.02f || f >= 1f) {
                    last = f
                    setProgressAsync(workDataOf(KEY_PROGRESS to f))
                }
            }
            repo.setStatus(id, AttachmentStatus.Uploaded)
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            repo.setStatus(id, AttachmentStatus.Failed)
            throw e
        } catch (e: Exception) {
            repo.setStatus(id, AttachmentStatus.Failed)
            if (runAttemptCount + 1 < MAX_AUTO_RETRIES) Result.retry() else Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Upload gagal")))
        }
    }

    companion object {
        const val KEY_ID = "attachment_id"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        const val TAG = "taskify-upload"
        fun uniqueName(id: String) = "upload-$id"
    }
}
