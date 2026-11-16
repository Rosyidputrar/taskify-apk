package com.rosyidputrar.taskify.feature.files

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val filesFeatureModule = module {
    viewModel { FilesViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { params -> PublicFileViewModel(params.values.getOrNull(0) as String?, get(), get()) }
}
