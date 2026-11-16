package com.rosyidputrar.taskify.session

import com.rosyidputrar.taskify.core.data.TaskifyDatabase
import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.domain.time.todayFlow
import com.rosyidputrar.taskify.core.store.finance.FinanceRepository
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
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * Catat otomatis transaksi berulang (auto_post) yang jatuh tempo — padanan `useRecurringProcessor`
 * webapp. Menunggu sinkron pertama selesai supaya tidak memproses data yang belum lengkap, lalu
 * jalan tiap 30 menit dan saat tanggal berganti. ID deterministik mencegah dobel antar perangkat.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecurringProcessor(
    private val session: SessionCoordinator,
    private val database: TaskifyDatabase,
    private val finance: FinanceRepository,
    private val messenger: Messenger,
    private val scope: CoroutineScope,
) {
    init {
        scope.launch {
            session.readyUser.flatMapLatest { user -> if (user == null) emptyFlow() else ticks() }.collect { runOnce() }
        }
    }

    private fun ticks(): Flow<Unit> = flow {
        database.syncStatus.map { it.hasSynced == true }.distinctUntilChanged().filter { it }.first()
        emitAll(merge(flow { while (true) { emit(Unit); delay(30 * 60_000L) } }, todayFlow().map { }))
    }

    private suspend fun runOnce() {
        try {
            val posted = finance.processDueRecurring()
            if (posted > 0) messenger.show("$posted transaksi berulang dicatat otomatis")
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.w("Taskify", "Gagal memproses transaksi berulang", e)
        }
    }
}

private suspend fun <T> kotlinx.coroutines.flow.FlowCollector<T>.emitAll(flow: Flow<T>) = flow.collect { emit(it) }
