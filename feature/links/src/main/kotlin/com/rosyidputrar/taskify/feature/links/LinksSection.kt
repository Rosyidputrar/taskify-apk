package com.rosyidputrar.taskify.feature.links

import com.rosyidputrar.taskify.core.designsystem.component.launchSafe
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.files.EntityType
import com.rosyidputrar.taskify.core.domain.search.EntityRow
import com.rosyidputrar.taskify.core.store.cross.LinkRepository
import com.rosyidputrar.taskify.core.store.cross.LinkedEntity
import com.rosyidputrar.taskify.core.store.search.SearchRepository
import com.rosyidputrar.taskify.core.ui.describe
import com.rosyidputrar.taskify.core.ui.icon
import com.rosyidputrar.taskify.core.ui.module
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

/** Membuka entitas lintas modul (diisi app lewat AppEvents). */
fun interface EntityOpener {
    fun open(type: EntityType, id: String)
}

interface LinksActions {
    fun link(target: EntityRow)
    fun unlink(link: LinkedEntity)
    fun open(row: EntityRow)
    suspend fun candidates(query: String): List<EntityRow>
}

class LinksViewModel(
    private val type: EntityType,
    private val id: String,
    private val repo: LinkRepository,
    private val search: SearchRepository,
    private val opener: EntityOpener,
    private val messenger: Messenger,
) : ViewModel(), LinksActions {
    val links: StateFlow<List<LinkedEntity>> = repo.watchLinks(type, id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    override fun link(target: EntityRow) {
        launchSafe { if (repo.link(type, id, target.type, target.id)) messenger.show("Ditautkan ke \"${target.title}\"") }
    }

    override fun unlink(link: LinkedEntity) {
        launchSafe {
            repo.unlink(link.linkId)
            messenger.undoable("Tautan dilepas") { repo.link(type, id, link.entity.type, link.entity.id) }
        }
    }

    override fun open(row: EntityRow) = opener.open(row.type, row.id)

    /** Kandidat = hasil pencarian global, tanpa entitas ini sendiri & yang sudah tertaut. */
    override suspend fun candidates(query: String): List<EntityRow> {
        val linked = links.value.map { it.entity.type to it.entity.id }.toSet() + (type to id)
        return search.search(query, 20).filterNot { (it.type to it.id) in linked }
    }
}

val linksFeatureModule = module {
    viewModel { p -> LinksViewModel(p.get(), p.get(), get(), get(), get(), get()) }
}

/** Bagian "Tertaut" di detail tugas / editor jurnal / catatan / transaksi. [readOnly] = true (mode Baca) → tombol Tautkan & Lepas disembunyikan, baris tetap bisa dibuka. */
@Composable
fun LinksSection(type: EntityType, id: String, modifier: Modifier = Modifier, readOnly: Boolean = false) {
    val vm: LinksViewModel = koinViewModel(key = "links-${type.value}-$id") { parametersOf(type, id) }
    val links by vm.links.collectAsStateWithLifecycle()
    LinksContent(links, vm, modifier, readOnly = readOnly)
}

@Composable
fun LinksContent(links: List<LinkedEntity>, actions: LinksActions, modifier: Modifier = Modifier, readOnly: Boolean = false) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tertaut", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (!readOnly) TextButton(onClick = { picking = true }) { Icon(Icons.Outlined.AddLink, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Tautkan") }
        }
        if (links.isEmpty()) Text("Hubungkan dengan tugas, jurnal, catatan, transaksi, atau Vault yang berkaitan.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        links.forEach { l -> EntityLine(l.entity, onClick = { actions.open(l.entity) }, trailing = {
            if (!readOnly) IconButton(onClick = { actions.unlink(l) }) { Icon(Icons.Outlined.LinkOff, contentDescription = "Lepas tautan ${l.entity.title}") }
        }) }
    }
    if (picking) EntityPicker(title = "Tautkan ke…", search = actions::candidates, onPick = { actions.link(it); picking = false }, onDismiss = { picking = false })
}

/** Satu baris entitas (ikon modul, judul, keterangan). Item di Sampah dicoret. */
@Composable
fun EntityLine(row: EntityRow, onClick: (() -> Unit)?, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    val accent = TaskifyTheme.modules.accent(row.type.module)
    val trashed = row.deletedAt != null
    Row(
        modifier.fillMaxWidth().then(if (onClick != null && !trashed) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp).background(accent.copy(alpha = 0.16f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(row.type.icon, null, tint = accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(row.title.ifBlank { "(tanpa judul)" }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textDecoration = if (trashed) TextDecoration.LineThrough else null)
            Text(row.describe() + if (trashed) " · di Sampah" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        trailing()
    }
}

/** Dialog cari entitas (pencarian global FTS5). */
@Composable
fun EntityPicker(title: String, search: suspend (String) -> List<EntityRow>, onPick: (EntityRow) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<EntityRow>()) }
    LaunchedEffect(query) {
        if (query.isBlank()) { results = emptyList(); return@LaunchedEffect }
        delay(150)
        results = runCatching { search(query) }.getOrDefault(emptyList())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, placeholder = { Text("Cari judul atau isi…") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 340.dp).padding(top = 8.dp)) {
                    items(results, key = { it.type.value + it.id }) { r -> EntityLine(r, onClick = { onPick(r) }) }
                    if (query.isNotBlank() && results.isEmpty()) item {
                        Text("Tidak ada yang cocok.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
}
