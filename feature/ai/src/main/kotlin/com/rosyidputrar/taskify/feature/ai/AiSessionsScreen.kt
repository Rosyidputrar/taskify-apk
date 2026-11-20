package com.rosyidputrar.taskify.feature.ai

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rosyidputrar.taskify.core.designsystem.component.ModuleHeader
import com.rosyidputrar.taskify.core.designsystem.theme.SerifFamily
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.store.ai.ChatSession
import org.koin.compose.viewmodel.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Lebar minimum untuk tampilan dua panel (daftar + chat). */
internal val TwoPaneWidth = 840.dp

/**
 * Halaman Asisten AI. Layar sempit: daftar percakapan (chat dibuka di layar sendiri lewat
 * [onOpenChat]). Layar lebar (tablet / lanskap): daftar dan chat berdampingan.
 */
@Composable
fun AiScreen(
    contentPadding: PaddingValues,
    onOpenChat: (String?) -> Unit,
    onSettings: () -> Unit,
    onBack: (() -> Unit)?,
    backButton: @Composable (() -> Unit) -> Unit,
) {
    val vm: AiSessionsViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (maxWidth >= TwoPaneWidth) {
            // Pilihan "chat baru" disimpan sebagai string kosong supaya bisa di-rememberSaveable.
            var selected by rememberSaveable { mutableStateOf<String?>(null) }
            if (selected != null && selected != NEW && state.loaded && state.sessions.none { it.id == selected }) selected = null
            Row(Modifier.fillMaxSize()) {
                SessionsContent(
                    state, vm, contentPadding, selected = selected,
                    onOpen = { selected = it ?: NEW }, onSettings = onSettings, onBack = onBack, backButton = backButton,
                    modifier = Modifier.width(360.dp).fillMaxHeight(),
                )
                VerticalDivider()
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    AnimatedContent(selected, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "panel-chat") { id ->
                        if (id == null) {
                            PaneHint(onNew = { selected = NEW })
                        } else {
                            AiChatScreen(
                                sessionId = id.takeIf { it != NEW }, contentPadding = contentPadding, onBack = null, backButton = backButton,
                                onSettings = onSettings, onDeleted = { selected = null },
                            )
                        }
                    }
                }
            }
        } else {
            SessionsContent(state, vm, contentPadding, selected = null, onOpen = onOpenChat, onSettings = onSettings, onBack = onBack, backButton = backButton)
        }
    }
}

private const val NEW = ""

@Composable
fun SessionsContent(
    state: SessionsUiState,
    actions: SessionsActions,
    contentPadding: PaddingValues,
    selected: String?,
    onOpen: (String?) -> Unit,
    onSettings: () -> Unit,
    onBack: (() -> Unit)?,
    backButton: @Composable (() -> Unit) -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    var dialog by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) } // (aksi, id)
    Box(modifier) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = contentPadding.calculateTopPadding(), bottom = contentPadding.calculateBottomPadding() + 96.dp)) {
            item(key = "header") {
                ModuleHeader(
                    TaskifyModule.Assistant, "Asisten AI",
                    subtitle = if (state.sessions.isEmpty()) "Tanya, ringkas, dan catat cepat" else "${state.sessions.size} percakapan · tersimpan di perangkat ini",
                    navigation = onBack?.let { { backButton(it) } },
                    actions = { IconButton(onClick = onSettings) { Icon(Icons.Outlined.Tune, contentDescription = "Pengaturan AI") } },
                )
            }
            if (state.loaded && !state.ready) item(key = "setup") { SetupCard(onSettings) }
            if (state.loaded && state.sessions.isEmpty()) item(key = "kosong") { EmptySessions() }
            items(state.sessions, key = { it.id }) { s ->
                SessionRow(
                    s, selected = s.id == selected, running = s.id in state.running,
                    onOpen = { onOpen(s.id) },
                    onRename = { dialog = "rename" to s.id },
                    onDelete = { dialog = "delete" to s.id },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = { onOpen(null) },
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("Chat baru") },
            containerColor = TaskifyTheme.modules.accent(TaskifyModule.Assistant),
            contentColor = TaskifyTheme.modules.onAccent(TaskifyModule.Assistant),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = contentPadding.calculateBottomPadding() + 20.dp),
        )
    }
    val target = dialog?.let { (_, id) -> state.sessions.firstOrNull { it.id == id } }
    when (dialog?.first) {
        "rename" -> if (target != null) TextDialog("Ganti nama", target.title, singleLine = true, onSave = { actions.rename(target.id, it); dialog = null }, onDismiss = { dialog = null })
        "delete" -> if (target != null) AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Hapus \"${target.title.ifBlank { "percakapan" }}\"?") },
            text = { Text("Semua pesannya dihapus dari perangkat. Tidak bisa dibatalkan.") },
            confirmButton = { TextButton(onClick = { actions.delete(target.id); dialog = null }) { Text("Hapus", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Batal") } },
        )
    }
}

@Composable
private fun SessionRow(
    s: ChatSession,
    selected: Boolean,
    running: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Assistant)
    Surface(
        color = if (selected) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
    ) {
        Column(Modifier.clickable(onClick = onOpen)) {
            Row(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(accent.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.ChatBubbleOutline, null, tint = accent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.title.ifBlank { "Percakapan" }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(8.dp))
                        Text(relativeTime(s.updatedMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        s.preview ?: s.model, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Menu ${s.title}") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Ganti nama") }, onClick = { menu = false; onRename() })
                        DropdownMenuItem(text = { Text("Hapus", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            if (running) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 6.dp), color = accent)
        }
    }
}

@Composable
private fun SetupCard(onSettings: () -> Unit) {
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Assistant)
    Surface(color = accent.copy(alpha = 0.10f), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Hubungkan AI", style = MaterialTheme.typography.titleMedium.copy(fontFamily = SerifFamily))
            Text(
                "Pakai kunci API milikmu sendiri: OpenAI, OpenRouter, Groq, Gemini, DeepSeek, Claude, atau server lain yang kompatibel. Kunci disimpan terenkripsi di perangkat ini.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onSettings) { Text("Atur sekarang") }
        }
    }
}

@Composable
private fun EmptySessions() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.AutoAwesome, null, tint = TaskifyTheme.modules.accent(TaskifyModule.Assistant), modifier = Modifier.size(36.dp))
        Text("Belum ada percakapan", style = MaterialTheme.typography.titleMedium)
        Text("Percakapan tersimpan di perangkat ini saja dan tidak ikut sinkron.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PaneHint(onNew: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Outlined.AutoAwesome, null, tint = TaskifyTheme.modules.accent(TaskifyModule.Assistant), modifier = Modifier.size(40.dp))
        Spacer(Modifier.size(12.dp))
        Text("Pilih percakapan atau mulai yang baru", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.size(12.dp))
        Button(onClick = onNew) { Text("Chat baru") }
    }
}

/** "baru saja", "12 mnt", "3 jam", "Kemarin", "14 Nov". */
internal fun relativeTime(ms: Long, now: Long = System.currentTimeMillis()): String {
    val diff = (now - ms).coerceAtLeast(0)
    val minute = 60_000L
    return when {
        diff < minute -> "baru saja"
        diff < 60 * minute -> "${diff / minute} mnt"
        diff < 24 * 60 * minute -> "${diff / (60 * minute)} jam"
        diff < 48 * 60 * minute -> "Kemarin"
        else -> SimpleDateFormat("d MMM", Locale.forLanguageTag("id-ID")).format(Date(ms))
    }
}
