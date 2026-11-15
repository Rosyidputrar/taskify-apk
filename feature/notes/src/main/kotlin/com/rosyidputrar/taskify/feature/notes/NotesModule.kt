package com.rosyidputrar.taskify.feature.notes

import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

val notesFeatureModule = module {
    viewModel { NotesViewModel(get(), get()) }
    viewModel { params -> NoteEditorViewModel(params.get(), get(), get(), get(), get(named("appScope"))) }
}
