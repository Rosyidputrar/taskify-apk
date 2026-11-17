package com.rosyidputrar.taskify.feature.wellbeing

import com.rosyidputrar.taskify.core.device.DeviceScheduler
import com.rosyidputrar.taskify.core.device.UsageCollector
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val wellbeingFeatureModule = module {
    viewModel {
        val collector = get<UsageCollector>()
        val scheduler = get<DeviceScheduler>()
        WellbeingViewModel(get(), get(), get(), get(), sync = { scheduler.schedule(); collector.sync() })
    }
}
