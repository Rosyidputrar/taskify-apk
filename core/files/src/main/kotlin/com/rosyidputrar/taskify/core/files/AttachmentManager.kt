package com.rosyidputrar.taskify.core.files

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.rosyidputrar.taskify.core.domain.files.Attachment
import com.rosyidputrar.taskify.core.domain.files.AttachmentStatus
import com.rosyidputrar.taskify.core.domain.files.EntityType
import com.rosyidputrar.taskify.core.domain.files.MAX_FILE_BYTES
import com.rosyidputrar.taskify.core.domain.files.MAX_PER_RUN
import com.rosyidputrar.taskify.core.domain.files.formatBytes
import com.rosyidputrar.taskify.core.domain.files.summarizeZip
import com.rosyidputrar.taskify.core.store.files.AttachmentRepository
import com.rosyidputrar.taskify.core.store.files.DuplicateInfo
import com.rosyidputrar.taskify.core.store.files.NewAttachment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/** Progres unggah satu lampiran: null = tidak sedang diunggah. */
data class UploadProgress(val running: Boolean, val fraction: Float?, val error: String?)

/**
 * Semua aksi lampiran (port `attachment-actions.ts`), versi Android:
 *  1. File disalin ke perangkat DULU dan baris `attachments` dibuat (status 'pending') → ikut sync.
 *  2. WorkManager mengunggah saat ada jaringan (tetap jalan bila aplikasi ditutup) → 'uploaded'.
 *  3. Gagal → 'failed', dicoba otomatis; "Ulangi" memakai salinan di perangkat.
 * Hapus: soft delete lokal (instan, offline) + hapus objek R2 sebisanya; sisanya dibersihkan cron.
 */
class AttachmentManager(
    private val context: Context,
    private val repo: AttachmentRepository,
    private val api: FilesApi,
    private val store: FileStore,
    private val transfers: Transfers,
    private val scope: CoroutineScope,
) {
    private val work get() = WorkManager.getInstance(context)

    fun watch(entityType: EntityType, entityId: String) = repo.watch(entityType, entityId)

    /** Salin & analisis file pilihan pengguna (ZIP: daftar isi & peringatan; semua: SHA-256, duplikat). */
    suspend fun prepare(uri: Uri, compress: Boolean = true): PreparedFile = withContext(Dispatchers.IO) {
        val (raw, name, mime) = FilePrep.copy(context, uri, store.scratchDir)
        finish(raw, name, mime, compress)
    }

    /** Sama dengan [prepare] untuk berkas lokal (mis. kotak masuk "Bagikan ke Taskify"). */
    suspend fun prepareLocal(source: java.io.File, name: String, mime: String, compress: Boolean = true): PreparedFile = withContext(Dispatchers.IO) {
        val raw = java.io.File(store.scratchDir, java.util.UUID.randomUUID().toString())
        source.copyTo(raw, overwrite = true)
        finish(raw, name, mime, compress)
    }

    private fun finish(raw: java.io.File, name: String, mime: String, compress: Boolean): PreparedFile {
        val (file, finalName, finalMime) = if (compress) FilePrep.compressIfLarge(raw, name, mime) else Triple(raw, name, mime)
        if (file.length() > MAX_FILE_BYTES) {
            file.delete()
            throw FileTransferException("File terlalu besar (${formatBytes(file.length())}). Maksimal 100 MB.")
        }
        if (file.length() == 0L) {
            file.delete()
            throw FileTransferException("File kosong")
        }
        val zip = if (finalMime.contains("zip") || finalName.endsWith(".zip", true)) runCatching { summarizeZip(ZipReader.entries(file)) }.getOrNull() else null
        return PreparedFile(file, finalName, finalMime, file.length(), FilePrep.sha256(file), zip)
    }

    suspend fun findDuplicate(sha: String): DuplicateInfo? = repo.findDuplicate(sha)

    fun discard(prepared: PreparedFile) {
        prepared.file.delete()
    }

    /** Simpan baris lampiran + salinan lokal, lalu antrekan unggah. */
    suspend fun commit(prepared: PreparedFile, entityType: EntityType, entityId: String, versionLabel: String? = null, note: String? = null): Attachment {
        val att = repo.create(NewAttachment(entityType, entityId, prepared.name, prepared.mimeType, prepared.size, prepared.sha256, versionLabel, note))
        withContext(Dispatchers.IO) { store.put(att.id, prepared.file) }
        enqueue(att.id)
        return att
    }

    suspend fun upload(uri: Uri, entityType: EntityType, entityId: String): Attachment = commit(prepare(uri), entityType, entityId)

    private fun enqueue(id: String, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(workDataOf(UploadWorker.KEY_ID to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .addTag(UploadWorker.TAG)
            .build()
        work.enqueueUniqueWork(UploadWorker.uniqueName(id), policy, request)
    }

    fun progress(id: String): Flow<UploadProgress?> = work.getWorkInfosForUniqueWorkFlow(UploadWorker.uniqueName(id)).map { infos ->
        val info = infos.lastOrNull() ?: return@map null
        when (info.state) {
            WorkInfo.State.RUNNING -> UploadProgress(true, info.progress.getFloat(UploadWorker.KEY_PROGRESS, 0f), null)
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> UploadProgress(false, null, null)
            WorkInfo.State.FAILED -> UploadProgress(false, null, info.outputData.getString(UploadWorker.KEY_ERROR))
            else -> null
        }
    }

    /** Apakah salinan file ada di perangkat ini (bisa diulang / dibuka offline). */
    fun hasLocalCopy(id: String) = store.has(id)

    /** Ulangi unggah yang gagal; false bila salinan file tidak ada di perangkat ini. */
    suspend fun retry(att: Attachment): Boolean {
        if (!store.has(att.id)) return false
        repo.setStatus(att.id, AttachmentStatus.Pending)
        enqueue(att.id)
        return true
    }

    suspend fun cancel(att: Attachment) {
        work.cancelUniqueWork(UploadWorker.uniqueName(att.id))
        repo.setStatus(att.id, AttachmentStatus.Failed)
    }

    suspend fun remove(att: Attachment) {
        work.cancelUniqueWork(UploadWorker.uniqueName(att.id))
        repo.remove(att.id)
        store.delete(att.id)
        // Sebisanya — bila offline, objek yatim dibersihkan cron harian server.
        scope.launch { runCatching { api.delete(att.storageKey) } }
    }

    suspend fun update(att: Attachment, versionLabel: String?, note: String?) = repo.update(att.id, versionLabel, note)

    /** Isi file di perangkat: dari salinan lokal, atau diunduh dari R2 lalu disimpan untuk offline. */
    suspend fun ensureLocal(att: Attachment): File = withContext(Dispatchers.IO) {
        if (store.has(att.id)) return@withContext store.file(att.id)
        if (att.status != AttachmentStatus.Uploaded) throw FileTransferException("File belum selesai diunggah dari perangkat asalnya")
        val url = api.downloadUrl(att.storageKey, att.fileName, inline = true)
        transfers.download(url, store.file(att.id))
        store.file(att.id)
    }

    /** Buka dengan aplikasi penampil yang cocok (PDF, gambar, ZIP, …). */
    suspend fun open(att: Attachment) {
        val file = ensureLocal(att)
        val shared = withContext(Dispatchers.IO) { store.shareCopy(att.id, att.fileName) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", shared)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, att.mimeType ?: FilePrep.guessMime(att.fileName))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(Intent.createChooser(intent, att.fileName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            throw FileTransferException("Tidak ada aplikasi untuk membuka ${file.name}")
        }
    }

    /** Simpan salinan ke lokasi pilihan pengguna (SAF). */
    suspend fun saveTo(att: Attachment, target: Uri) = withContext(Dispatchers.IO) {
        val file = ensureLocal(att)
        context.contentResolver.openOutputStream(target)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            ?: throw FileTransferException("Lokasi simpan tidak bisa ditulis")
    }

    /** Simpan satu file dari dalam ZIP ke lokasi pilihan pengguna. */
    suspend fun saveZipEntry(att: Attachment, path: String, target: Uri) = withContext(Dispatchers.IO) {
        val file = ensureLocal(att)
        val tmp = File(store.scratchDir, "entry-" + System.nanoTime())
        try {
            if (!ZipReader.extract(file, path, tmp)) throw FileTransferException("File tidak ditemukan di arsip")
            context.contentResolver.openOutputStream(target)?.use { out -> tmp.inputStream().use { it.copyTo(out) } }
        } finally {
            tmp.delete()
        }
    }

    /**
     * Outbox: antrekan lampiran yang belum terunggah dan punya salinan di perangkat INI (baris dari
     * perangkat lain dilewati). Maks. [MAX_PER_RUN] per putaran; WorkManager menunggu jaringan.
     */
    suspend fun kickOutbox() = withContext(Dispatchers.IO) {
        var handled = 0
        for (att in repo.pending()) {
            if (handled >= MAX_PER_RUN) break
            if (!store.has(att.id)) continue
            val infos = work.getWorkInfosForUniqueWork(UploadWorker.uniqueName(att.id)).get()
            val active = infos.any { !it.state.isFinished }
            if (active) continue
            val gaveUp = infos.any { it.state == WorkInfo.State.FAILED }
            if (gaveUp && att.status == AttachmentStatus.Failed) continue // tunggu "Ulangi" manual
            enqueue(att.id, ExistingWorkPolicy.KEEP)
            handled++
        }
    }

    /** Keluar akun: hentikan unggahan & hapus semua salinan file di perangkat. */
    fun wipe() {
        work.cancelAllWorkByTag(UploadWorker.TAG)
        store.clearAll()
    }
}
