package com.rosyidputrar.taskify.feature.attachments

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val attachmentsFeatureModule = module {
    viewModel { params -> AttachmentsViewModel(params.get(), params.get(), get(), get()) }
}
