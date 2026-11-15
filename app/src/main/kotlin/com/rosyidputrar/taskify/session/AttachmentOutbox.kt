package com.rosyidputrar.taskify.session

import com.rosyidputrar.taskify.core.data.TaskifyDatabase
import com.rosyidputrar.taskify.core.files.AttachmentManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Outbox lampiran (padanan `useAttachmentOutbox` webapp): setelah sinkron pertama, lampiran yang
 * belum terunggah dan punya salinan di perangkat ini diantrekan ke WorkManager tiap 2 menit.
 * WorkManager sendiri menunggu jaringan dan tetap jalan saat aplikasi ditutup.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AttachmentOutbox(
    session: SessionCoordinator,
    private val database: TaskifyDatabase,
    private val manager: AttachmentManager,
    scope: CoroutineScope,
) {
    init {
        scope.launch {
            session.readyUser.flatMapLatest { user -> if (user == null) emptyFlow() else ticks() }.collect {
                runCatching { manager.kickOutbox() }
            }
        }
    }

    private fun ticks(): Flow<Unit> = flow {
        database.syncStatus.map { it.hasSynced == true }.distinctUntilChanged().filter { it }.first()
        while (true) {
            emit(Unit)
            delay(2 * 60_000L)
        }
    }
}
