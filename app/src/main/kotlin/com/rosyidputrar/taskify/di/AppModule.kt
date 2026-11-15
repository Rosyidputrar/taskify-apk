package com.rosyidputrar.taskify.di

import com.rosyidputrar.taskify.data.AppPreferences
import com.rosyidputrar.taskify.ui.shell.AppViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { AppPreferences(androidContext()) }
    viewModel { AppViewModel(get()) }
}
