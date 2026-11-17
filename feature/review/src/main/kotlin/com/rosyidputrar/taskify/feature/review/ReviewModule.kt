package com.rosyidputrar.taskify.feature.review

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val reviewFeatureModule = module {
    viewModel { ReviewViewModel(get(), get(), getOrNull(), getOrNull()) }
}
