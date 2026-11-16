package com.rosyidputrar.taskify.feature.auth

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** ViewModel layar gerbang. Dependensi (AuthRepository, LockManager, …) disediakan modul aplikasi. */
val authFeatureModule = module {
    viewModel { LoginViewModel(get(), get()) }
    viewModel { MfaViewModel(get(), get()) }
    viewModel { LockViewModel(get(), get(), get()) }
    viewModel { AuthenticatorsViewModel(get()) }
}
