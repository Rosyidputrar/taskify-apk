package com.rosyidputrar.taskify.feature.tasks

import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val tasksFeatureModule = module {
    viewModel { TasksViewModel(get(), get(), get()) }
    // "appScope": simpan judul/catatan terakhir tetap selesai walau sheet sudah ditutup.
    viewModel { params -> TaskDetailViewModel(params.get(), get(), get(), get(), get(named("appScope"))) }
}
