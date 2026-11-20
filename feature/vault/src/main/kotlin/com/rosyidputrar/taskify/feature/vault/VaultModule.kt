package com.rosyidputrar.taskify.feature.vault

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val vaultFeatureModule = module {
    viewModel { VaultViewModel(get(), get(), get(), get()) }
    viewModel { params -> com.rosyidputrar.taskify.feature.vault.config.ConfigFileViewModel(params.get(), get(), get(), get(), get()) }
    viewModel { params -> com.rosyidputrar.taskify.feature.vault.config.ConfigEditorViewModel(params.values.getOrNull(0) as String?, params.values.getOrNull(1) as String?, get(), get(), get(), get()) }
    viewModel { params -> SnippetEditorViewModel(params.getOrNull(), get(), get(), get()) }
    viewModel { params -> ProjectViewModel(params.get(), get(), get(), get()) }
    viewModel { params -> ZipViewerViewModel(params.get(), get(), get(), get()) }
}
