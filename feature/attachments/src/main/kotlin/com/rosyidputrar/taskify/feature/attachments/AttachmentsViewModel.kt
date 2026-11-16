package com.rosyidputrar.taskify.feature.attachments

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.domain.files.Attachment
import com.rosyidputrar.taskify.core.domain.files.EntityType
import com.rosyidputrar.taskify.core.files.AttachmentManager
import com.rosyidputrar.taskify.core.files.UploadProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Satu daftar lampiran milik entitas ([type], [id]). */
class AttachmentsViewModel(
    private val type: EntityType,
    private val id: String,
    private val manager: AttachmentManager,
    private val messenger: Messenger,
) : ViewModel(), AttachmentActions {
    val items: StateFlow<List<Attachment>> = manager.watch(type, id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _busy = MutableStateFlow<String?>(null)
    /** Nama file yang sedang disiapkan (disalin/dikompres) sebelum masuk antrean. */
    val busy: StateFlow<String?> = _busy

    override fun progress(att: Attachment): Flow<UploadProgress?> = manager.progress(att.id)
    override fun hasLocalCopy(att: Attachment) = manager.hasLocalCopy(att.id)

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                messenger.show(e.message ?: "Gagal")
            }
        }
    }

    override fun add(uris: List<Uri>) = act {
        for (uri in uris) {
            _busy.value = "Menyiapkan file…"
            try {
                val att = manager.upload(uri, type, id)
                messenger.show("${att.fileName} masuk antrean unggah")
            } finally {
                _busy.value = null
            }
        }
    }

    override fun open(att: Attachment) = act { manager.open(att) }
    override fun saveTo(att: Attachment, target: Uri) = act {
        manager.saveTo(att, target)
        messenger.show("${att.fileName} disimpan")
    }
    override fun retry(att: Attachment) = act {
        if (!manager.retry(att)) messenger.show("File aslinya tidak ada di perangkat ini. Hapus lalu unggah ulang.")
    }
    override fun cancel(att: Attachment) = act {
        manager.cancel(att)
        messenger.show("Upload dibatalkan")
    }
    override fun remove(att: Attachment) = act {
        manager.remove(att)
        messenger.show("${att.fileName} dihapus")
    }
}

interface AttachmentActions {
    fun progress(att: Attachment): Flow<UploadProgress?>
    fun hasLocalCopy(att: Attachment): Boolean
    fun add(uris: List<Uri>)
    fun open(att: Attachment)
    fun saveTo(att: Attachment, target: Uri)
    fun retry(att: Attachment)
    fun cancel(att: Attachment)
    fun remove(att: Attachment)
}
