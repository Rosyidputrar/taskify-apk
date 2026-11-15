package com.rosyidputrar.taskify.navigation

import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object CalendarRoute
@Serializable data object RoomRoute
@Serializable data object SettingsRoute

/** Modul yang layarnya belum dibangun memakai rute generik ini sampai tahapnya tiba. */
@Serializable data class ModuleRoute(val moduleName: String) {
    val module: TaskifyModule get() = TaskifyModule.valueOf(moduleName)
}

fun TaskifyModule.route(): Any = when (this) {
    TaskifyModule.Home -> HomeRoute
    TaskifyModule.Calendar -> CalendarRoute
    TaskifyModule.Settings -> SettingsRoute
    else -> ModuleRoute(name)
}
