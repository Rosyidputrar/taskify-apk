package com.rosyidputrar.taskify.feature.media

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val mediaFeatureModule = module {
    viewModel { MusicViewModel(get(), get(), get()) }
    viewModel { TikTokViewModel(get(), get(), get()) }
}
