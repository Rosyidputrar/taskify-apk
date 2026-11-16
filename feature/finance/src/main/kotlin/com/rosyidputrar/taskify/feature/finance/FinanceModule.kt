package com.rosyidputrar.taskify.feature.finance

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val financeFeatureModule = module {
    viewModel { FinanceViewModel(get(), get()) }
    // SavedStateHandle: draf transaksi bertahan saat proses dimatikan sistem.
    viewModel { params -> TxEditorViewModel(params.get(), get(), get(), get()) }
}
