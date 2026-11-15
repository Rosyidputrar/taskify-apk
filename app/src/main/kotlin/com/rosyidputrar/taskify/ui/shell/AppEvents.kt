package com.rosyidputrar.taskify.ui.shell

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Peristiwa dari luar Compose (shortcut launcher, keyboard fisik, intent) ke shell aplikasi. */
sealed interface AppEvent {
    data object OpenSpotlight : AppEvent
    data class OpenCapture(val text: String = "") : AppEvent
    data class OpenCalendar(val date: String? = null) : AppEvent
    data class OpenTask(val taskId: String) : AppEvent
    /** Buka editor jurnal: entri [id], atau draf baru di [date]. */
    data class OpenJournal(val id: String? = null, val date: String? = null) : AppEvent
    /** Buka editor catatan: catatan [id], atau catatan baru (ditempel ke [date] bila diisi). */
    data class OpenNote(val id: String? = null, val date: String? = null) : AppEvent
    /** Buka editor transaksi: transaksi [id], atau transaksi baru di [date]. */
    data class OpenTransaction(val id: String? = null, val date: String? = null) : AppEvent
}

object AppEvents {
    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<AppEvent> = _events

    fun send(event: AppEvent) {
        _events.tryEmit(event)
    }
}
