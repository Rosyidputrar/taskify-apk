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

/** Modul yang layarnya belum dibangun memakai rute generik ini sampai tahapnya tiba. */
@Serializable data class ModuleRoute(val moduleName: String) {
    val module: TaskifyModule get() = TaskifyModule.valueOf(moduleName)
}

fun TaskifyModule.route(): Any = when (this) {
    TaskifyModule.Home -> HomeRoute
    TaskifyModule.Calendar -> CalendarRoute()
    TaskifyModule.Settings -> SettingsRoute
    TaskifyModule.Tasks -> TasksRoute()
    else -> ModuleRoute(name)
}
