package com.rosyidputrar.taskify.navigation

import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
/** Kalender; [date] memilih tanggal tertentu (dari strip minggu Beranda, shortcut, dsb.). */
@Serializable data class CalendarRoute(val date: String? = null)
@Serializable data object RoomRoute
@Serializable data object SettingsRoute

/** Tugas; [taskId] membuka detail tugas itu (dari notifikasi, Beranda, dsb.). */
@Serializable data class TasksRoute(val taskId: String? = null)

@Serializable data object JournalRoute

/**
 * Editor jurnal: [id] = entri yang ada; tanpa id = draf baru di [date] (default hari ini).
 * [template] "week"/"month" + [from]/[to] = templat refleksi dari Review.
 */
@Serializable data class JournalEntryRoute(
    val id: String? = null,
    val date: String? = null,
    val template: String? = null,
    val from: String? = null,
    val to: String? = null,
)

@Serializable data object NotesRoute

/** Editor catatan: [id] = catatan yang ada; tanpa id = catatan baru, ditempel ke [date] bila diisi. */
@Serializable data class NoteEditRoute(val id: String? = null, val date: String? = null)

@Serializable data object FinanceRoute

/** Editor transaksi: [id] = transaksi yang ada; tanpa id = baru di [date] / dompet [walletId]. */
@Serializable data class TransactionRoute(val id: String? = null, val date: String? = null, val walletId: String? = null)

/** Modul yang layarnya belum dibangun memakai rute generik ini sampai tahapnya tiba. */
@Serializable data class ModuleRoute(val moduleName: String) {
    val module: TaskifyModule get() = TaskifyModule.valueOf(moduleName)
}

fun TaskifyModule.route(): Any = when (this) {
    TaskifyModule.Home -> HomeRoute
    TaskifyModule.Calendar -> CalendarRoute()
    TaskifyModule.Settings -> SettingsRoute
    TaskifyModule.Tasks -> TasksRoute()
    TaskifyModule.Journal -> JournalRoute
    TaskifyModule.Notes -> NotesRoute
    TaskifyModule.Finance -> FinanceRoute
    else -> ModuleRoute(name)
}
