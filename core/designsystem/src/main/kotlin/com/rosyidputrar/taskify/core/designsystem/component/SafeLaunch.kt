package com.rosyidputrar.taskify.core.designsystem.component

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Jaring pengaman untuk error yang lolos dari coroutine UI/aplikasi.
 *
 * Tanpa ini, satu `throw` di `viewModelScope.launch` (mis. menyimpan saat belum masuk akun, atau
 * SQLite gagal menulis) langsung membuat aplikasi force close. Error dilaporkan ke [sink] yang
 * dipasang aplikasi (snackbar + log); [CancellationException] tidak pernah sampai ke sini.
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
fun ViewModel.launchSafe(block: suspend CoroutineScope.() -> Unit): Job = viewModelScope.launchSafe(block)

/**
 * Seperti `launch`, tetapi error dilaporkan ke pengguna, bukan crash — termasuk pada
 * `rememberCoroutineScope()` (yang memakai Job biasa sehingga `CoroutineExceptionHandler` saja
 * tidak cukup).
 */
fun CoroutineScope.launchSafe(block: suspend CoroutineScope.() -> Unit): Job = launch(UiErrors.handler) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        UiErrors.report(e)
    }
}
