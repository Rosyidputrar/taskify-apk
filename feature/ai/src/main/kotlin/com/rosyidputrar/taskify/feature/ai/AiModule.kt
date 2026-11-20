package com.rosyidputrar.taskify.feature.ai

import com.rosyidputrar.taskify.core.ai.AiClient
import com.rosyidputrar.taskify.core.ai.AiClients
import com.rosyidputrar.taskify.core.ai.AiProvider
import com.rosyidputrar.taskify.core.ai.AiSettingsStore
import com.rosyidputrar.taskify.core.ai.KeystoreSecretBox
import com.rosyidputrar.taskify.core.store.ai.AiRepository
import com.rosyidputrar.taskify.feature.ai.tools.AiData
import com.rosyidputrar.taskify.feature.ai.tools.StoreAiData
import com.rosyidputrar.taskify.feature.ai.tools.TaskifyToolbox
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Pembuat klien per penyedia (bisa diganti di tes). */
typealias AiClientFactory = (AiProvider, String, String) -> AiClient

/** Cakupan aplikasi untuk mesin chat — jawaban tetap berjalan walau layar ditutup. */
val AI_SCOPE = named("ai_scope")

val aiFeatureModule = module {
    single(AI_SCOPE) { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default) }
    single { AiSettingsStore(androidContext(), KeystoreSecretBox()) }
    single { AiRepository(get()) }
    single<AiClientFactory> { { p, url, key -> AiClients.create(p, url, key) } }
    single<AiData> { StoreAiData(get(), get(), get(), get(), get()) }
    single<AiToolbox> { TaskifyToolbox(get()) }
    single { ChatEngine(get<AiRepository>(), get<AiSettingsStore>(), get<AiClientFactory>(), get(), get(AI_SCOPE), maxTokens = { get<AiSettingsStore>().settings.value.maxTokens }) }
    viewModel { params -> AiChatViewModel(params.getOrNull(), get(), get(), get(), get()) }
    viewModel { AiSessionsViewModel(get(), get(), get(), get()) }
    viewModel { AiSettingsViewModel(get(), get(), get(), get<AiClientFactory>(), get()) }
}
