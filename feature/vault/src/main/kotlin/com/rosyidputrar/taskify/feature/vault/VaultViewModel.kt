package com.rosyidputrar.taskify.feature.vault

import com.rosyidputrar.taskify.core.designsystem.component.launchSafe
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.domain.vault.CodeProject
import com.rosyidputrar.taskify.core.domain.vault.Snippet
import com.rosyidputrar.taskify.core.store.files.AttachmentRepository
import com.rosyidputrar.taskify.core.store.files.StorageUsage
import com.rosyidputrar.taskify.core.store.vault.LanguageCount
import com.rosyidputrar.taskify.core.store.vault.VaultRepository
import com.rosyidputrar.taskify.core.store.sandi.ConfigFile
import com.rosyidputrar.taskify.core.store.sandi.ConfigRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class VaultTab(val label: String) { Snippets("Snippet"), Projects("Project"), Config("Konfig") }

data class SnippetFilter(val search: String = "", val language: String? = null, val favorites: Boolean = false) {
    val active get() = search.isNotBlank() || language != null || favorites
}

data class VaultUiState(
    val tab: VaultTab = VaultTab.Snippets,
    val filter: SnippetFilter = SnippetFilter(),
    val snippets: List<Snippet> = emptyList(),
    val languages: List<LanguageCount> = emptyList(),
    val projectSearch: String = "",
    val projects: List<CodeProject> = emptyList(),
    val usage: StorageUsage = StorageUsage(),
    val loaded: Boolean = false,
    val configSearch: String = "",
    val configEnv: String? = null,
    val configs: List<ConfigFile> = emptyList(),
    val environments: List<Pair<String, Int>> = emptyList(),
)

interface VaultActions {
    fun setTab(tab: VaultTab)
    fun setFilter(filter: SnippetFilter)
    fun setProjectSearch(text: String)
    fun toggleFavorite(snippet: Snippet)
    fun toggleFavorite(project: CodeProject)
    fun copied(snippet: Snippet)
    /** Pindahkan snippet ke Sampah (geser ke kiri) — bisa diurungkan. */
    fun removeSnippet(snippet: Snippet) {}
    fun setConfigSearch(text: String)
    fun setConfigEnv(env: String?)
    fun createProject(name: String, description: String?, repoUrl: String?, onCreated: (String) -> Unit)
}

/** Kuota gratis Cloudflare R2 (ditampilkan sebagai acuan pemakaian). */
const val R2_FREE_BYTES = 10L * 1024 * 1024 * 1024

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class VaultViewModel(private val repo: VaultRepository, attachments: AttachmentRepository, configs: ConfigRepository, private val messenger: Messenger) : ViewModel(), VaultActions {
    private val tab = MutableStateFlow(VaultTab.Snippets)
    private val filter = MutableStateFlow(SnippetFilter())
    private val projectSearch = MutableStateFlow("")
    private val configFilter = MutableStateFlow("" to null as String?)
    private val configList = configFilter.debounce { if (it.first.isEmpty()) 0 else 200 }.distinctUntilChanged()
        .flatMapLatest { (q, env) -> configs.watchFiles(q, env) }
    private val configState = combine(configFilter, configList, configs.watchEnvironments()) { f, l, e -> Triple(f, l, e) }

    private val snippets = filter.debounce { if (it.search.isEmpty()) 0 else 200 }.distinctUntilChanged().flatMapLatest { repo.watchSnippets(it.search, it.language, it.favorites) }
    private val projects = projectSearch.debounce { if (it.isEmpty()) 0 else 200 }.distinctUntilChanged().flatMapLatest { repo.watchProjects(it) }

    val state: StateFlow<VaultUiState> = combine(
        combine(tab, filter, projectSearch) { t, f, p -> Triple(t, f, p) },
        snippets, repo.watchLanguages(), projects, combine(attachments.watchUsage(), configState) { u, c -> u to c },
    ) { (t, f, p), s, l, pr, (u, c) -> VaultUiState(t, f, s, l, p, pr, u, true, c.first.first, c.first.second, c.second, c.third) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VaultUiState())

    override fun setTab(tab: VaultTab) { this.tab.value = tab }
    override fun setFilter(filter: SnippetFilter) { this.filter.value = filter }
    override fun setProjectSearch(text: String) { projectSearch.value = text }
    override fun setConfigSearch(text: String) { configFilter.value = text to configFilter.value.second }
    override fun setConfigEnv(env: String?) { configFilter.value = configFilter.value.first to env }
    override fun toggleFavorite(snippet: Snippet) { launchSafe { repo.setSnippetFavorite(snippet.id, !snippet.favorite) } }
    override fun toggleFavorite(project: CodeProject) { launchSafe { repo.setProjectFavorite(project.id, !project.favorite) } }
    override fun copied(snippet: Snippet) = messenger.show("${snippet.title} tersalin")
    override fun removeSnippet(snippet: Snippet) {
        launchSafe {
            repo.removeSnippet(snippet.id)
            messenger.undoable("Snippet dipindahkan ke Sampah") { repo.restoreSnippet(snippet.id) }
        }
    }

    override fun createProject(name: String, description: String?, repoUrl: String?, onCreated: (String) -> Unit) {
        if (name.isBlank()) return
        launchSafe { onCreated(repo.saveProject(null, name, description, repoUrl)) }
    }
}
