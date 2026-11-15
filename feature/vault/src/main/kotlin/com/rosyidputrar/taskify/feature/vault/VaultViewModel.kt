package com.rosyidputrar.taskify.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.domain.vault.CodeProject
import com.rosyidputrar.taskify.core.domain.vault.Snippet
import com.rosyidputrar.taskify.core.store.files.AttachmentRepository
import com.rosyidputrar.taskify.core.store.files.StorageUsage
import com.rosyidputrar.taskify.core.store.vault.LanguageCount
import com.rosyidputrar.taskify.core.store.vault.VaultRepository
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
)

interface VaultActions {
    fun setTab(tab: VaultTab)
    fun setFilter(filter: SnippetFilter)
    fun setProjectSearch(text: String)
    fun toggleFavorite(snippet: Snippet)
    fun toggleFavorite(project: CodeProject)
    fun copied(snippet: Snippet)
    fun createProject(name: String, description: String?, repoUrl: String?, onCreated: (String) -> Unit)
}

/** Kuota gratis Cloudflare R2 (ditampilkan sebagai acuan pemakaian). */
const val R2_FREE_BYTES = 10L * 1024 * 1024 * 1024

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class VaultViewModel(private val repo: VaultRepository, attachments: AttachmentRepository, private val messenger: Messenger) : ViewModel(), VaultActions {
    private val tab = MutableStateFlow(VaultTab.Snippets)
    private val filter = MutableStateFlow(SnippetFilter())
    private val projectSearch = MutableStateFlow("")

    private val snippets = filter.debounce { if (it.search.isEmpty()) 0 else 200 }.distinctUntilChanged().flatMapLatest { repo.watchSnippets(it.search, it.language, it.favorites) }
    private val projects = projectSearch.debounce { if (it.isEmpty()) 0 else 200 }.distinctUntilChanged().flatMapLatest { repo.watchProjects(it) }

    val state: StateFlow<VaultUiState> = combine(
        combine(tab, filter, projectSearch) { t, f, p -> Triple(t, f, p) },
        snippets, repo.watchLanguages(), projects, attachments.watchUsage(),
    ) { (t, f, p), s, l, pr, u -> VaultUiState(t, f, s, l, p, pr, u, true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VaultUiState())

    override fun setTab(tab: VaultTab) { this.tab.value = tab }
    override fun setFilter(filter: SnippetFilter) { this.filter.value = filter }
    override fun setProjectSearch(text: String) { projectSearch.value = text }
    override fun toggleFavorite(snippet: Snippet) { viewModelScope.launch { repo.setSnippetFavorite(snippet.id, !snippet.favorite) } }
    override fun toggleFavorite(project: CodeProject) { viewModelScope.launch { repo.setProjectFavorite(project.id, !project.favorite) } }
    override fun copied(snippet: Snippet) = messenger.show("${snippet.title} tersalin")

    override fun createProject(name: String, description: String?, repoUrl: String?, onCreated: (String) -> Unit) {
        if (name.isBlank()) return
        viewModelScope.launch { onCreated(repo.saveProject(null, name, description, repoUrl)) }
    }
}
