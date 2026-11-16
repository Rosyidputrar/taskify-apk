package com.rosyidputrar.taskify.core.designsystem.component

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Jaring pengaman untuk error yang lolos dari coroutine UI/aplikasi.
 *
 * Tanpa ini, satu `throw` di `viewModelScope.launch` (mis. menyimpan saat belum masuk akun, atau
 * SQLite gagal menulis) langsung membuat aplikasi force close. Error dilaporkan ke [sink] yang
 * dipasang aplikasi (snackbar + log); [CancellationException][kotlinx.coroutines.CancellationException]
 * tidak pernah sampai ke sini.
 */
object UiErrors {
    @Volatile
    private var sink: (Throwable) -> Unit = {}

    /** Dipasang sekali oleh aplikasi saat start. */
    fun install(sink: (Throwable) -> Unit) {
        this.sink = sink
    }

    fun report(error: Throwable) {
        runCatching { sink(error) }
    }

    val handler: CoroutineExceptionHandler = CoroutineExceptionHandler { _, error -> report(error) }
}

/** Seperti `viewModelScope.launch`, tetapi error dilaporkan ke pengguna, bukan crash. */
fun ViewModel.launchSafe(block: suspend CoroutineScope.() -> Unit): Job = viewModelScope.launch(UiErrors.handler, block = block)

/** Seperti `launch`, tetapi error dilaporkan ke pengguna, bukan crash. */
fun CoroutineScope.launchSafe(block: suspend CoroutineScope.() -> Unit): Job = launch(UiErrors.handler, block = block)
