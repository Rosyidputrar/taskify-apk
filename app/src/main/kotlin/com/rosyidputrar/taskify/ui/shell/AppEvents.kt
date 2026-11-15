package com.rosyidputrar.taskify.ui.shell

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Peristiwa dari luar Compose (shortcut launcher, keyboard fisik, intent) ke shell aplikasi. */
sealed interface AppEvent {
    data object OpenSpotlight : AppEvent
    data class OpenCapture(val text: String = "") : AppEvent
    data object OpenCalendar : AppEvent
}

object AppEvents {
    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<AppEvent> = _events

    fun send(event: AppEvent) {
        _events.tryEmit(event)
    }
}
