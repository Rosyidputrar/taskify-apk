package com.rosyidputrar.taskify.feature.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.ai.AiClient
import com.rosyidputrar.taskify.core.ai.AiEndpoint
import com.rosyidputrar.taskify.core.ai.AiException
import com.rosyidputrar.taskify.core.ai.AiProvider
import com.rosyidputrar.taskify.core.ai.AiSettings
import com.rosyidputrar.taskify.core.ai.AiSettingsStore
import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.designsystem.component.launchSafe
import com.rosyidputrar.taskify.core.store.ai.AiRepository
import com.rosyidputrar.taskify.core.store.ai.ChatSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

// ==================================================================== chat

data class ChatUiState(
    val sessionId: String? = null,
    val title: String = "",
    val items: List<ChatItem> = emptyList(),
    val running: Boolean = false,
    /** Penyedia & model siap dipakai (kunci + model ada). */
    val ready: Boolean = false,
    val provider: AiProvider = AiProvider.OpenAiCompatible,
    val model: String = "",
    /** Host tujuan data (untuk keterangan privasi). */
    val host: String = "",
    val dataAccess: Boolean = false,
    val models: List<String> = emptyList(),
    val systemPrompt: String = "",
    val loaded: Boolean = false,
)

interface ChatActions {
    fun send(text: String) {}
    fun stop() {}
    fun retry() {}
    fun editLast(text: String) {}
    fun decide(callId: String, approve: Boolean) {}
    fun setDataAccess(on: Boolean) {}
    fun setModel(model: String) {}
    fun rename(title: String) {}
    fun setSystemPrompt(text: String) {}
    fun delete(onDone: () -> Unit) {}
}

/** Satu percakapan. [initialId] null = chat baru; sesi dibuat saat pesan pertama dikirim. */
@OptIn(ExperimentalCoroutinesApi::class)
class AiChatViewModel(
    initialId: String?,
    private val repo: AiRepository,
    private val engine: ChatEngine,
    private val settingsStore: AiSettingsStore,
    private val toolbox: AiToolbox,
) : ViewModel(), ChatActions {
    private val sessionId = MutableStateFlow(initialId)
    /** Pilihan untuk chat baru (sebelum sesi ada). */
    private val draftAccess = MutableStateFlow(settingsStore.settings.value.dataAccess)
    private val draftModel = MutableStateFlow<String?>(null)

    private val session = sessionId.flatMapLatest { id -> if (id == null) flowOf(null) else repo.watchSession(id) }
    private val messages = sessionId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repo.watchMessages(id) }

    val state: StateFlow<ChatUiState> = combine(
        combine(sessionId, session) { id, s -> id to s },
        messages,
        combine(engine.live, engine.running) { l, r -> l to r },
        settingsStore.settings,
        combine(draftAccess, draftModel) { a, m -> a to m },
    ) { (id, s), msgs, (live, running), settings, (access, model) ->
        val isRunning = id != null && id in running
        val provider = s?.provider?.let { p -> AiProvider.entries.firstOrNull { it.name == p } } ?: settings.provider
        val chosenModel = s?.model ?: model ?: settings.model
        val connected = if (s == null) settings.hasKey else settingsStore.connection(provider) != null
        ChatUiState(
            sessionId = id,
            title = s?.title.orEmpty(),
            items = chatItems(msgs, live, isRunning, toolbox),
            running = isRunning,
            ready = connected && chosenModel.isNotBlank(),
            provider = provider,
            model = chosenModel,
            host = AiEndpoint.host(if (provider == settings.provider) settings.baseUrl else settingsStore.connection(provider)?.baseUrl.orEmpty()),
            dataAccess = s?.dataAccess ?: access,
            models = if (provider == settings.provider) settings.models else emptyList(),
            systemPrompt = s?.systemPrompt ?: settings.systemPrompt,
            loaded = id == null || s != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState(sessionId = initialId, loaded = initialId == null))

    override fun send(text: String) {
        if (text.isBlank()) return
        launchSafe {
            val id = sessionId.value ?: run {
                val s = settingsStore.settings.value
                repo.createSession(s.provider.name, draftModel.value ?: s.model, s.systemPrompt, draftAccess.value).also { sessionId.value = it }
            }
            engine.send(id, text)
        }
    }

    override fun stop() { sessionId.value?.let(engine::stop) }
    override fun retry() { sessionId.value?.let(engine::retry) }
    override fun editLast(text: String) { sessionId.value?.let { engine.editLast(it, text) } }
    override fun decide(callId: String, approve: Boolean) { sessionId.value?.let { engine.decide(it, callId, approve) } }

    override fun setDataAccess(on: Boolean) {
        val id = sessionId.value ?: run { draftAccess.value = on; return }
        launchSafe { repo.setDataAccess(id, on) }
    }

    override fun setModel(model: String) {
        val m = model.trim()
        if (m.isEmpty()) return
        val id = sessionId.value ?: run { draftModel.value = m; return }
        launchSafe { repo.setModel(id, settingsStore.settings.value.provider.name, m) }
    }

    override fun rename(title: String) { sessionId.value?.let { id -> launchSafe { repo.rename(id, title) } } }

    override fun setSystemPrompt(text: String) {
        val id = sessionId.value ?: return
        launchSafe { repo.setSystemPrompt(id, text.trim()) }
    }

    override fun delete(onDone: () -> Unit) {
        val id = sessionId.value ?: run { onDone(); return }
        engine.stop(id)
        launchSafe {
            repo.deleteSession(id)
            onDone()
        }
    }
}

// ==================================================================== daftar sesi

data class SessionsUiState(val sessions: List<ChatSession> = emptyList(), val loaded: Boolean = false, val ready: Boolean = false, val running: Set<String> = emptySet())

interface SessionsActions {
    fun rename(id: String, title: String) {}
    fun delete(id: String) {}
}

class AiSessionsViewModel(
    private val repo: AiRepository,
    private val engine: ChatEngine,
    settingsStore: AiSettingsStore,
    private val messenger: Messenger,
) : ViewModel(), SessionsActions {
    val state: StateFlow<SessionsUiState> = combine(repo.watchSessions(), settingsStore.settings, engine.running) { list, s, running ->
        SessionsUiState(list, true, s.ready, running)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionsUiState())

    override fun rename(id: String, title: String) { launchSafe { repo.rename(id, title) } }

    override fun delete(id: String) {
        engine.stop(id)
        launchSafe {
            repo.deleteSession(id)
            messenger.show("Percakapan dihapus")
        }
    }
}

// ==================================================================== pengaturan

data class AiSettingsUi(
    val settings: AiSettings = AiSettings(),
    val testing: Boolean = false,
    /** Hasil tes koneksi terakhir (null = belum dites). */
    val testOk: Boolean? = null,
    val testMessage: String? = null,
    val urlError: String? = null,
    val keyError: String? = null,
    val chats: Int = 0,
)

interface AiSettingsActions {
    fun setProvider(p: AiProvider) {}
    fun saveBaseUrl(raw: String) {}
    fun saveKey(raw: String) {}
    fun clearKey() {}
    fun testConnection() {}
    fun chooseModel(model: String) {}
    fun saveSystemPrompt(text: String) {}
    fun setDataAccess(on: Boolean) {}
    fun deleteAllChats() {}
}

class AiSettingsViewModel(
    private val store: AiSettingsStore,
    private val repo: AiRepository,
    private val engine: ChatEngine,
    private val clients: (AiProvider, String, String) -> AiClient,
    private val messenger: Messenger,
) : ViewModel(), AiSettingsActions {
    private val local = MutableStateFlow(AiSettingsUi())

    val state: StateFlow<AiSettingsUi> = combine(local, store.settings, repo.watchSessions().map { it.size }) { l, s, n -> l.copy(settings = s, chats = n) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiSettingsUi(settings = store.settings.value))

    override fun setProvider(p: AiProvider) {
        store.setProvider(p)
        local.update { AiSettingsUi() }
    }

    override fun saveBaseUrl(raw: String) {
        try {
            store.setBaseUrl(raw)
            local.update { it.copy(urlError = null, testOk = null, testMessage = null) }
        } catch (e: AiException) {
            local.update { it.copy(urlError = e.message) }
        }
    }

    override fun saveKey(raw: String) {
        if (raw.isBlank()) { local.update { it.copy(keyError = "Tempel kunci API dulu.") }; return }
        try {
            store.setApiKey(raw)
            local.update { it.copy(keyError = null, testOk = null, testMessage = null) }
            messenger.show("Kunci API disimpan terenkripsi")
        } catch (e: AiException) {
            local.update { it.copy(keyError = e.message) }
        }
    }

    override fun clearKey() {
        store.setApiKey("")
        local.update { it.copy(testOk = null, testMessage = null) }
        messenger.show("Kunci API dihapus dari perangkat ini")
    }

    /** Ambil daftar model: sekaligus membuktikan base URL + kunci benar. */
    override fun testConnection() {
        val s = store.settings.value
        val conn = store.connection(s.provider) ?: run {
            local.update { it.copy(testOk = false, testMessage = "Simpan kunci API dulu.") }
            return
        }
        local.update { it.copy(testing = true, testOk = null, testMessage = null) }
        launchSafe {
            try {
                val models = clients(s.provider, conn.baseUrl, conn.apiKey).models()
                store.setModels(models)
                local.update {
                    it.copy(
                        testing = false, testOk = true,
                        testMessage = if (models.isEmpty()) "Tersambung, tapi server tidak memberi daftar model. Ketik nama modelnya manual." else "Tersambung · ${models.size} model tersedia.",
                    )
                }
            } catch (e: AiException) {
                local.update { it.copy(testing = false, testOk = false, testMessage = e.message) }
            } catch (e: Exception) {
                local.update { it.copy(testing = false, testOk = false, testMessage = "Gagal: ${e.message ?: e::class.simpleName}") }
            }
        }
    }

    override fun chooseModel(model: String) { store.setModel(model) }

    override fun saveSystemPrompt(text: String) {
        store.setSystemPrompt(text)
        messenger.show("System prompt disimpan. Berlaku untuk chat baru.")
    }

    override fun setDataAccess(on: Boolean) = store.setDataAccess(on)

    override fun deleteAllChats() {
        engine.cancelAll()
        launchSafe {
            repo.deleteAll()
            messenger.show("Semua percakapan dihapus")
        }
    }
}
