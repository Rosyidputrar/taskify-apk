package com.rosyidputrar.taskify.feature.tasks

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val tasksFeatureModule = module {
    viewModel { TasksViewModel(get(), get(), get()) }
    viewModel { params -> TaskDetailViewModel(params.get(), get(), get(), get()) }
}
