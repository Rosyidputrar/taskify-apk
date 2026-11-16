package com.rosyidputrar.taskify.core.designsystem.component

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Pesan singkat di bawah layar, opsional dengan aksi (biasanya "Urungkan"). */
data class UiMessage(
    val text: String,
    val actionLabel: String? = null,
    val action: (suspend () -> Unit)? = null,
)

/** Saluran pesan snackbar aplikasi. ViewModel mengirim; shell yang menampilkan. */
class Messenger {
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val messages: SharedFlow<UiMessage> = _messages

    fun show(text: String) {
        _messages.tryEmit(UiMessage(text))
    }

    /** Pesan dengan tombol aksi bebas (mis. "Lihat"). */
    fun action(text: String, label: String, action: suspend () -> Unit) {
        _messages.tryEmit(UiMessage(text, label, action))
    }

    fun undoable(text: String, undo: suspend () -> Unit) {
        _messages.tryEmit(UiMessage(text, "Urungkan", undo))
    }
}
