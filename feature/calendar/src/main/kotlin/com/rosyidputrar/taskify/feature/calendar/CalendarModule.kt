package com.rosyidputrar.taskify.feature.calendar

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val calendarFeatureModule = module {
    viewModel { params -> CalendarViewModel(params.getOrNull(), get(), get(), get(), get()) }
}
