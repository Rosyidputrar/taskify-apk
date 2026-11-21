package com.rosyidputrar.taskify.ui.shell

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

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
    /** Shortcut launcher modul. */
    data class OpenModule(val module: com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule) : AppEvent
    /** Kotak masuk "Bagikan ke Taskify" sudah terisi. */
    data object OpenShare : AppEvent

    /** Layar Mode Fokus; [taskId] = tugas yang disiapkan (belum mulai). */
    data class OpenFocus(val taskId: String? = null) : AppEvent
    /** Buka entitas lintas modul (hasil pencarian, tag, tautan, Sampah). */
    data class OpenEntity(val type: com.rosyidputrar.taskify.core.domain.files.EntityType, val id: String) : AppEvent
    /** Buka pintasan Hari ini (isi modul atau tampilan cepat). */
    data class OpenShortcut(val ref: com.rosyidputrar.taskify.core.domain.home.ShortcutRef) : AppEvent
}

/**
 * Antrean peristiwa satu-konsumen yang MENYIMPAN peristiwa sampai ada yang mengambilnya.
 *
 * Peristiwa dari notifikasi, shortcut, dan widget sering dikirim sebelum shell tersusun (cold
 * start, gerbang login/PIN/MFA masih tertutup, atau Activity sedang dibuat ulang). SharedFlow tanpa
 * replay membuangnya karena belum ada kolektor; antrean ini menahannya lalu menyerahkannya
 * begitu shell mulai mengumpulkan. Bila penuh, yang paling lama dibuang.
 */
class EventQueue<T>(capacity: Int = 16) {
    private val channel = Channel<T>(capacity, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: Flow<T> = channel.receiveAsFlow()

    fun send(event: T) {
        channel.trySend(event)
    }

    /** Buang semua peristiwa tertunda (mis. saat keluar akun supaya intent akun lama tidak ter-replay). */
    fun clear() {
        while (channel.tryReceive().isSuccess) {
            // drain
        }
    }
}

object AppEvents {
    private val queue = EventQueue<AppEvent>()
    val events: Flow<AppEvent> = queue.events

    fun send(event: AppEvent) = queue.send(event)
    fun clear() = queue.clear()
}
