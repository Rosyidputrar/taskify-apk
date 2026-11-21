package com.rosyidputrar.taskify.core.domain.editor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SaveState { Idle, Pending, Saving, Saved, Error }

/**
 * Simpan otomatis editor — port perilaku `JournalEditor` webapp:
 * - teks disimpan setelah berhenti mengetik [debounceMs] (0,7 dtk); mood/tanggal/pin langsung;
 * - penyimpanan diserialkan (create selalu selesai sebelum update berikutnya);
 * - gagal simpan → perubahan dikembalikan ke antrean (yang lebih baru tetap menang), tidak hilang;
 * - [save] mengembalikan false bila tidak ada yang perlu disimpan (draf masih kosong).
 *
 * [scope] sebaiknya hidup lebih lama dari layar (scope aplikasi) agar flush saat layar ditutup
 * tetap selesai.
 */
class Autosaver(
    private val scope: CoroutineScope,
    initial: SaveState = SaveState.Idle,
    private val debounceMs: Long = DEBOUNCE_MS,
    private val save: suspend (patch: Map<String, Any?>) -> Boolean,
) {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<SaveState> = _state

    private val mutex = Mutex()
    private var pending: Map<String, Any?> = emptyMap()
    private var timer: Job? = null
    private val lock = Any()

    val hasPending: Boolean get() = synchronized(lock) { pending.isNotEmpty() }

    fun queue(patch: Map<String, Any?>, immediate: Boolean = false) {
        synchronized(lock) {
            pending = pending + patch
            timer?.cancel()
            _state.value = SaveState.Pending
            timer = scope.launch {
                if (!immediate) delay(debounceMs)
                flush()
            }
        }
    }

    /** Simpan semua perubahan tertunda sekarang (dipanggil saat layar ditutup / aplikasi ke latar). */
    suspend fun flush() {
        val me = currentCoroutineContext()[Job]
        mutex.withLock {
            // Ambil `pending` DI DALAM mutex supaya pembatalan saat menunggu mutex tidak
            // pernah menghilangkan patch, dan flush paralel tidak bisa menimpa urutan terbaru.
            val patch = synchronized(lock) {
                timer?.takeIf { it !== me }?.cancel()
                timer = null
                pending.also { pending = emptyMap() }
            }
            if (patch.isEmpty()) return
            _state.value = SaveState.Saving
            try {
                val saved = save(patch)
                _state.value = when {
                    hasPending -> SaveState.Pending
                    saved -> SaveState.Saved
                    else -> SaveState.Idle
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                synchronized(lock) { pending = patch + pending }
                throw e
            } catch (e: Exception) {
                synchronized(lock) { pending = patch + pending }
                _state.value = SaveState.Error
            }
        }
    }

    /** Flush di [scope] tanpa menunggu (mis. dari onCleared). */
    fun flushLater(): Job = scope.launch { flush() }

    companion object {
        const val DEBOUNCE_MS = 700L
    }
}
