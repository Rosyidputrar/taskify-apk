package com.rosyidputrar.taskify.widget

import android.content.Context
import com.rosyidputrar.taskify.core.auth.LockManager
import com.rosyidputrar.taskify.core.domain.time.todayFlow
import com.rosyidputrar.taskify.core.store.tasks.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Perbarui widget saat tugas fokus berubah (termasuk yang datang lewat sinkron dari laptop),
 * saat hari berganti, atau saat PIN diaktifkan/dimatikan.
 */
@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WidgetUpdater(private val context: Context, private val tasks: TaskRepository, private val lock: LockManager, private val scope: CoroutineScope) {
    fun start() {
        scope.launch {
            combine(todayFlow().flatMapLatest { t -> tasks.watchFocus(t, 6).map { list -> t to list.map { it.id + it.title + it.dueDate + it.dueTime } } }, lock.state.map { it.config != null }) { a, b -> a to b }
                .distinctUntilChanged()
                .debounce(1_000)
                .collect { TodayWidget.refresh(context) }
        }
    }
}
