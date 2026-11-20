package com.rosyidputrar.taskify.feature.journal

import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val journalFeatureModule = module {
    viewModel { JournalViewModel(get(), get()) }
    // "appScope" = scope aplikasi (didefinisikan di modul app) agar simpan terakhir tetap selesai.
    viewModel { params -> JournalEditorViewModel(params.get(), get(), get(), get(), get(named("appScope")), get()) }
}
