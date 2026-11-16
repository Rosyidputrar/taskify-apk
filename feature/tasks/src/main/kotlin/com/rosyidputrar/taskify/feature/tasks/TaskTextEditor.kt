package com.rosyidputrar.taskify.feature.tasks

import com.rosyidputrar.taskify.core.domain.editor.Autosaver
import com.rosyidputrar.taskify.core.domain.editor.SaveState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Judul & catatan tugas disimpan otomatis [debounceMs] setelah berhenti mengetik (seperti
 * Jurnal/Catatan), bukan hanya saat fokus pindah — menutup sheet dengan geser/tombol X tidak
 * menghilangkan ketikan. [scope] sebaiknya scope aplikasi agar simpan terakhir tetap selesai
 * setelah sheet ditutup.
 */
class TaskTextEditor(
    scope: CoroutineScope,
    debounceMs: Long = Autosaver.DEBOUNCE_MS,
    private val save: suspend (patch: Map<String, Any?>) -> Unit,
) {
    private val saver = Autosaver(scope, debounceMs = debounceMs) { patch ->
        save(patch)
        true
    }

    val state: StateFlow<SaveState> = saver.state

    /** Judul kosong tidak pernah disimpan (judul lama tetap). */
    fun editTitle(title: String, immediate: Boolean = false) {
        if (title.isNotBlank()) saver.queue(mapOf("title" to title), immediate)
    }

    fun editNotes(notes: String, immediate: Boolean = false) {
        saver.queue(mapOf("notes" to notes.ifBlank { null }), immediate)
    }

    /** Simpan yang tertunda sekarang tanpa menunggu (Enter, fokus pindah, sheet ditutup). */
    fun flush() {
        if (saver.hasPending) saver.flushLater()
    }
}
