package com.rosyidputrar.taskify.feature.vault

import com.rosyidputrar.taskify.core.designsystem.component.ListSkeleton
import com.rosyidputrar.taskify.core.designsystem.component.FitText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rosyidputrar.taskify.core.designsystem.component.EmptyState
import com.rosyidputrar.taskify.core.designsystem.component.IndexCard
import com.rosyidputrar.taskify.core.designsystem.component.ModuleHeader
import com.rosyidputrar.taskify.core.designsystem.theme.SerifFamily
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.files.formatBytes
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.domain.vault.CodeProject
import com.rosyidputrar.taskify.core.domain.vault.Snippet
import com.rosyidputrar.taskify.core.domain.vault.languageLabel
import com.rosyidputrar.taskify.core.domain.vault.templateVariables
import com.rosyidputrar.taskify.feature.vault.component.CodeView
import com.rosyidputrar.taskify.feature.vault.component.TemplateDialog
import com.rosyidputrar.taskify.feature.vault.component.copyToClipboard
import com.rosyidputrar.taskify.feature.vault.component.looksSecret
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun VaultScreen(
    contentPadding: PaddingValues,
    onOpenSnippet: (String?) -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenConfig: (String?) -> Unit = {},
    onBack: (() -> Unit)? = null,
    backButton: @Composable (() -> Unit) -> Unit = {},
    viewModel: VaultViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    VaultContent(state, viewModel, contentPadding, onOpenSnippet, onOpenProject, onBack, backButton, onOpenConfig)
}

@Composable
fun VaultContent(
    state: VaultUiState,
    actions: VaultActions,
    contentPadding: PaddingValues,
    onOpenSnippet: (String?) -> Unit,
    onOpenProject: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    backButton: @Composable (() -> Unit) -> Unit = {},
    onOpenConfig: (String?) -> Unit = {},
) {
    var newProject by rememberSaveable { mutableStateOf(false) }
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Vault)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "header") {
            ModuleHeader(
                TaskifyModule.Vault,
                "Vault Kode",
                subtitle = "${state.snippets.size} snippet · ${state.projects.size} project",
                navigation = onBack?.let { { backButton(it) } },
                actions = {
                    FilledTonalIconButton(
                        onClick = { when (state.tab) { VaultTab.Snippets -> onOpenSnippet(null); VaultTab.Projects -> newProject = true; VaultTab.Config -> onOpenConfig(null) } },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = accent, contentColor = TaskifyTheme.modules.onAccent(TaskifyModule.Vault)),
                    ) { Icon(Icons.Rounded.Add, contentDescription = when (state.tab) { VaultTab.Snippets -> "Snippet baru"; VaultTab.Projects -> "Project baru"; VaultTab.Config -> "Konfigurasi baru" }) }
                },
            )
        }
        item(key = "tabs") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                VaultTab.entries.forEachIndexed { i, t ->
                    SegmentedButton(selected = state.tab == t, onClick = { actions.setTab(t) }, shape = SegmentedButtonDefaults.itemShape(i, VaultTab.entries.size), label = { FitText(t.label) })
                }
            }
        }
        when (state.tab) {
            VaultTab.Snippets -> snippets(state, actions, onOpenSnippet)
            VaultTab.Projects -> projects(state, actions, onOpenProject)
            VaultTab.Config -> configs(state, actions, onOpenConfig)
        }
        item(key = "usage") { UsageBar(state) }
    }
    if (newProject) ProjectDialog(null, onDismiss = { newProject = false }) { name, desc, repo ->
        actions.createProject(name, desc, repo) { id -> newProject = false; onOpenProject(id) }
    }
}

private fun LazyListScope.configs(state: VaultUiState, actions: VaultActions, onOpen: (String?) -> Unit) {
    item(key = "c-search") {
        OutlinedTextField(
            state.configSearch, actions::setConfigSearch,
            placeholder = { Text("Cari nama berkas atau lingkungan…") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = if (state.configSearch.isNotEmpty()) ({ IconButton(onClick = { actions.setConfigSearch("") }) { Icon(Icons.Rounded.Close, contentDescription = "Hapus pencarian") } }) else null,
            singleLine = true, shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
    if (state.environments.isNotEmpty()) item(key = "c-envs") {
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.environments, key = { it.first }) { (env, n) ->
                FilterChip(selected = state.configEnv == env, onClick = { actions.setConfigEnv(if (state.configEnv == env) null else env) }, label = { Text("$env $n") })
            }
        }
    }
    if (!state.loaded) item(key = "c-skeleton") { ListSkeleton(TaskifyModule.Vault, rows = 3) }
    if (state.loaded && state.configs.isEmpty()) item(key = "c-empty") {
        EmptyState(
            TaskifyModule.Vault,
            if (state.configSearch.isNotBlank() || state.configEnv != null) "Tidak ada yang cocok" else "Belum ada berkas konfigurasi",
            "Simpan .env dan konfigurasi JSON per lingkungan. Isinya dienkripsi dengan kunci brankas Sandi; setiap simpan menjadi versi baru.",
        )
    }
    items(state.configs, key = { "c-" + it.id }) { f ->
        IndexCard(TaskifyModule.Vault, onClick = { onOpen(f.id) }, onClickLabel = "Buka konfigurasi", modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).animateItem(), contentPadding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(f.name, style = MaterialTheme.typography.titleMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(f.environment, f.format.label, f.projectName, "${f.versions} versi").joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Outlined.Lock, contentDescription = "Terenkripsi", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun LazyListScope.snippets(state: VaultUiState, actions: VaultActions, onOpen: (String?) -> Unit) {
    val f = state.filter
    item(key = "s-search") {
        OutlinedTextField(
            f.search, { actions.setFilter(f.copy(search = it)) },
            placeholder = { Text("Cari judul, deskripsi, isi kode…") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = if (f.search.isNotEmpty()) ({ IconButton(onClick = { actions.setFilter(f.copy(search = "")) }) { Icon(Icons.Rounded.Close, contentDescription = "Hapus pencarian") } }) else null,
            singleLine = true, shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
    item(key = "s-langs") {
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(selected = f.favorites, onClick = { actions.setFilter(f.copy(favorites = !f.favorites)) }, label = { Text("Favorit") },
                    leadingIcon = { Icon(if (f.favorites) Icons.Rounded.Star else Icons.Outlined.StarOutline, null, Modifier.size(18.dp)) })
            }
            items(state.languages, key = { it.language }) { l ->
                FilterChip(selected = f.language == l.language, onClick = { actions.setFilter(f.copy(language = if (f.language == l.language) null else l.language)) },
                    label = { Text("${languageLabel(l.language)} ${l.count}") })
            }
        }
    }
    if (!state.loaded) item(key = "s-skeleton") { ListSkeleton(TaskifyModule.Vault, rows = 4, lines = 2) }
    if (state.loaded && state.snippets.isEmpty()) {
        item(key = "s-empty") {
            EmptyState(
                TaskifyModule.Vault,
                if (f.active) "Tidak ada yang cocok" else "Belum ada snippet",
                if (f.active) "Coba kata lain atau lepas filter." else "Simpan potongan kode yang sering dipakai. Gunakan {{variabel}} untuk template yang bisa diisi saat disalin.",
            )
        }
    }
    items(state.snippets, key = { it.id }) { s -> SnippetCard(s, actions, onOpen = { onOpen(s.id) }, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp).animateItem()) }
}

@Composable
fun SnippetCard(s: Snippet, actions: VaultActions, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var template by rememberSaveable { mutableStateOf(false) }
    val vars = templateVariables(s.code)
    IndexCard(TaskifyModule.Vault, onClick = onOpen, onClickLabel = "Buka snippet", modifier = modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.title, style = MaterialTheme.typography.titleMedium.copy(fontFamily = SerifFamily), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(languageLabel(s.language), if (vars.isNotEmpty()) "${vars.size} variabel" else null, s.description).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { actions.toggleFavorite(s) }) {
                Icon(if (s.favorite) Icons.Rounded.Star else Icons.Outlined.StarOutline, contentDescription = if (s.favorite) "Hapus dari favorit" else "Favoritkan",
                    tint = if (s.favorite) TaskifyTheme.modules.accent(TaskifyModule.Vault) else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = {
                if (vars.isEmpty()) {
                    copyToClipboard(context, s.title, s.code, looksSecret(s.code)); actions.copied(s)
                } else template = true
            }) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Salin kode") }
        }
        Spacer(Modifier.size(8.dp))
        CodeView(s.code, s.language, maxLines = 6, lineNumbers = false)
    }
    if (template) TemplateDialog(s.code, onDismiss = { template = false }) { filled ->
        copyToClipboard(context, s.title, filled, looksSecret(filled)); actions.copied(s); template = false
    }
}

private fun LazyListScope.projects(state: VaultUiState, actions: VaultActions, onOpen: (String) -> Unit) {
    item(key = "p-search") {
        OutlinedTextField(
            state.projectSearch, actions::setProjectSearch,
            placeholder = { Text("Cari project…") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
            singleLine = true, shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
    if (!state.loaded) item(key = "p-skeleton") { ListSkeleton(TaskifyModule.Vault, rows = 3) }
    if (state.loaded && state.projects.isEmpty()) {
        item(key = "p-empty") {
            EmptyState(TaskifyModule.Vault, "Belum ada project", "Arsipkan source code sebagai ZIP per versi — dicek dulu dari .env, node_modules, dan .git sebelum diunggah.")
        }
    }
    items(state.projects, key = { it.id }) { p -> ProjectCard(p, actions, onOpen = { onOpen(p.id) }, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp).animateItem()) }
}

@Composable
private fun ProjectCard(p: CodeProject, actions: VaultActions, onOpen: () -> Unit, modifier: Modifier) {
    IndexCard(TaskifyModule.Vault, onClick = onOpen, onClickLabel = "Buka project", modifier = modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.FolderZip, null, tint = TaskifyTheme.modules.accent(TaskifyModule.Vault), modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium.copy(fontFamily = SerifFamily), maxLines = 1, overflow = TextOverflow.Ellipsis)
                p.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Text(
                    if (p.versions == 0) "Belum ada versi" else
                        "${p.versions} versi · terbaru ${p.latestLabel ?: "tanpa label"} (${formatBytes(p.latestSize)})" + (p.latestAt?.let { at -> Dates.isoToLocal(at)?.first?.let { " · ${Dates.format(it, "d MMM yyyy")}" } } ?: ""),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { actions.toggleFavorite(p) }) {
                Icon(if (p.favorite) Icons.Rounded.Star else Icons.Outlined.StarOutline, contentDescription = if (p.favorite) "Hapus dari favorit" else "Favoritkan",
                    tint = if (p.favorite) TaskifyTheme.modules.accent(TaskifyModule.Vault) else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun UsageBar(state: VaultUiState) {
    val fraction = (state.usage.bytes.toDouble() / R2_FREE_BYTES).toFloat()
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp).semantics(mergeDescendants = true) {
        contentDescription = "Penyimpanan cloud ${formatBytes(state.usage.bytes)} dari kuota gratis 10 GB, ${state.usage.files} file"
    }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text("PENYIMPANAN CLOUD", style = TaskifyTextStyles.indexLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("${formatBytes(state.usage.bytes)} / 10 GB · ${state.usage.files} file", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, drawStopIndicator = {}, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)))
    }
}

/** Dialog project baru / ubah project. */
@Composable
fun ProjectDialog(project: CodeProject?, onDismiss: () -> Unit, onSave: (String, String?, String?) -> Unit) {
    var name by rememberSaveable { mutableStateOf(project?.name ?: "") }
    var desc by rememberSaveable { mutableStateOf(project?.description ?: "") }
    var repo by rememberSaveable { mutableStateOf(project?.repoUrl ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (project == null) "Project baru" else "Ubah project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nama") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(desc, { desc = it }, label = { Text("Deskripsi (opsional)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(repo, { repo = it }, label = { Text("URL repo (opsional)") }, placeholder = { Text("https://github.com/…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onSave(name, desc.ifBlank { null }, repo.ifBlank { null }) }, enabled = name.isNotBlank()) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

