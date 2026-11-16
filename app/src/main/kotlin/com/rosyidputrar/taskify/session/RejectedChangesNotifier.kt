package com.rosyidputrar.taskify.session

import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.domain.sync.newRejections
import com.rosyidputrar.taskify.ui.shell.AppEvent
import com.rosyidputrar.taskify.ui.shell.AppEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Umumkan perubahan yang BARU ditolak server (mis. melanggar batas data) lewat snackbar "Lihat" →
 * Pengaturan, tempat perubahan itu bisa dicoba lagi atau disalin. Tanpa ini, data lokal diam-diam
 * kembali ke versi server.
 */
class RejectedChangesNotifier(private val count: Flow<Long>, private val messenger: Messenger, private val scope: CoroutineScope) {
    fun start(): Job = scope.launch {
        var previous: Long? = null
        count.collect { n ->
            val fresh = newRejections(previous, n)
            previous = n
            if (fresh > 0) {
                messenger.action("$fresh perubahan ditolak server dan tidak tersimpan", "Lihat") {
                    AppEvents.send(AppEvent.OpenModule(TaskifyModule.Settings))
                }
            }
        }
    }
}
