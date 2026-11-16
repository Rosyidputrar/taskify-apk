package com.rosyidputrar.taskify.feature.files

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.domain.files.ExpiryOption
import com.rosyidputrar.taskify.core.domain.files.TempFile
import com.rosyidputrar.taskify.core.domain.files.TempFiles
import com.rosyidputrar.taskify.core.files.TempFilePatch
import com.rosyidputrar.taskify.core.files.TempFileUploader
import com.rosyidputrar.taskify.core.files.TempFilesApi
import com.rosyidputrar.taskify.core.files.UploadJob
import com.rosyidputrar.taskify.core.store.files.TempFileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Asal halaman publik (`PUBLIC_WEB_ORIGIN`). Kosong → tautan langsung ke Edge Function. */
data class FilesConfig(val publicOrigin: String)

/** Status online (petunjuk saja; server tetap yang menentukan). */
fun interface OnlineStatus {
    fun flow(): Flow<Boolean>
}

data class FilesUiState(
    val files: List<TempFile> = emptyList(),
    val jobs: List<UploadJob> = emptyList(),
    val now: Long = System.currentTimeMillis(),
    val online: Boolean = true,
    val expiry: ExpiryOption = ExpiryOption.DEFAULT,
    val loaded: Boolean = false,
    /** Aksi server yang sedang berjalan untuk detail berkas (mis. "public", "token"). */
    val busy: String? = null,
    val downloadProgress: Float? = null,
) {
    val used: Long get() = TempFiles.usedBytes(files, now)
    /** Berkas yang sedang diunggah dari perangkat ini sudah tampil sebagai baris progres. */
    val listed: List<TempFile> get() {
        val uploading = jobs.filter { it.fileId != null && it.phase.active }.mapNotNull { it.fileId }.toSet()
        return files.filterNot { it.id in uploading }
    }
}

interface FilesActions {
    fun setExpiry(expiry: ExpiryOption)
    fun pick(uris: List<Uri>)
    fun cancel(key: String)
    fun dismiss(key: String)
    fun link(file: TempFile): String
    fun copied()
    fun setPublic(file: TempFile, value: Boolean)
    fun regenerate(file: TempFile)
    fun applyExpiry(file: TempFile, expiry: ExpiryOption)
    fun rename(file: TempFile, name: String)
    fun delete(file: TempFile, onDone: () -> Unit)
    fun download(file: TempFile, target: Uri)
}

class FilesViewModel(
    repo: TempFileRepository,
    private val uploader: TempFileUploader,
    private val api: TempFilesApi,
    private val config: FilesConfig,
    online: OnlineStatus,
    private val messenger: Messenger,
) : ViewModel(), FilesActions {
    private val local = MutableStateFlow(FilesUiState())
    private val ticker = flow { while (true) { emit(System.currentTimeMillis()); delay(30_000) } }

    val state: StateFlow<FilesUiState> = combine(
        local,
        combine(repo.watchFiles().onEach { uploader.settle(it) }, uploader.overlay) { l, o -> TempFiles.withOverlay(l, o) },
        uploader.jobs,
        online.flow(),
        ticker,
    ) { l, files, jobs, on, now -> l.copy(files = files, jobs = jobs, online = on, now = now, loaded = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    override fun setExpiry(expiry: ExpiryOption) { local.value = local.value.copy(expiry = expiry) }

    override fun pick(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (!state.value.online) { messenger.show("Mengunggah butuh koneksi internet."); return }
        viewModelScope.launch {
            // Pemeriksaan awal saja; server tetap menegakkan kuota secara atomik
            var planned = 0L
            for (uri in uris) {
                val picked = withContext(Dispatchers.IO) { runCatching { uploader.describe(uri) }.getOrNull() }
                if (picked == null) { messenger.show("Ukuran berkas tidak bisa dibaca."); continue }
                val problem = TempFiles.quotaProblem(picked.size, state.value.files, System.currentTimeMillis(), planned)
                if (problem != null) { messenger.show("${picked.name}: $problem"); continue }
                planned += picked.size
                uploader.start(picked, local.value.expiry)
            }
        }
    }

    override fun cancel(key: String) = uploader.cancel(key)
    override fun dismiss(key: String) = uploader.dismiss(key)

    override fun link(file: TempFile): String =
        if (config.publicOrigin.isNotBlank()) TempFiles.shareUrl(config.publicOrigin, file.shareToken) else api.directDownloadUrl(file.shareToken)

    override fun copied() = messenger.show("Tautan disalin")

    private fun run(key: String, success: String?, block: suspend () -> Unit) {
        if (local.value.busy != null) return
        local.value = local.value.copy(busy = key)
        viewModelScope.launch {
            try {
                block(); success?.let(messenger::show)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messenger.show(e.message ?: "Gagal. Coba lagi.")
            } finally {
                local.value = local.value.copy(busy = null, downloadProgress = null)
            }
        }
    }

    override fun setPublic(file: TempFile, value: Boolean) =
        run("public", if (value) "Tautan diaktifkan" else "Tautan dinonaktifkan") { uploader.change(file.id, TempFilePatch(isPublic = value)) }

    override fun regenerate(file: TempFile) =
        run("token", "Tautan baru dibuat; tautan lama dicabut") { uploader.change(file.id, TempFilePatch(regenerate = true)) }

    override fun applyExpiry(file: TempFile, expiry: ExpiryOption) =
        run("expiry", "Masa berlaku diperbarui") { uploader.change(file.id, TempFilePatch(expiry = expiry)) }

    override fun rename(file: TempFile, name: String) {
        val next = name.trim()
        if (next.isEmpty() || next == file.fileName) return
        run("rename", "Nama diganti") { uploader.change(file.id, TempFilePatch(name = next)) }
    }

    override fun delete(file: TempFile, onDone: () -> Unit) = run("delete", "\"${file.fileName}\" dihapus") {
        uploader.remove(file.id); onDone()
    }

    override fun download(file: TempFile, target: Uri) = run("download", "Berkas tersimpan") {
        uploader.downloadOwn(file.id, target) { p -> local.value = local.value.copy(downloadProgress = p) }
    }
}
