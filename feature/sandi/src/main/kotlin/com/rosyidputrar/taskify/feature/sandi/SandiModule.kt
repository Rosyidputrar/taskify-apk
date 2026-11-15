package com.rosyidputrar.taskify.feature.sandi

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val sandiFeatureModule = module {
    viewModel { SandiViewModel(get(), get(), get(), get(), get()) }
}
