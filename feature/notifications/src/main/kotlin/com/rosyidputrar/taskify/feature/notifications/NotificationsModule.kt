package com.rosyidputrar.taskify.feature.notifications

import com.rosyidputrar.taskify.core.device.DeviceAccess
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val notificationsFeatureModule = module {
    viewModel { params ->
        val context = androidContext()
        NotificationsViewModel(get(), get(), get(), get(), get(), get(), get(), params.getOrNull(), rebind = { DeviceAccess.reconnectListener(context) })
    }
}
