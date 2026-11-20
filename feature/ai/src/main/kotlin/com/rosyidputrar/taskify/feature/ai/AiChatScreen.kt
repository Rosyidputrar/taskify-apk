package com.rosyidputrar.taskify.feature.ai

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rosyidputrar.taskify.core.designsystem.component.rememberHaptics
import com.rosyidputrar.taskify.core.designsystem.theme.SerifFamily
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyLogo
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.store.ai.ChatState
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Lebar maksimum kolom percakapan di layar lebar (tablet / lanskap) supaya baris tetap enak dibaca. */
internal val ChatMaxWidth = 760.dp

@Composable
fun AiChatScreen(
    sessionId: String?,
    contentPadding: PaddingValues,
    onBack: (() -> Unit)?,
    backButton: @Composable (() -> Unit) -> Unit,
    onSettings: () -> Unit,
    onDeleted: () -> Unit,
) {
    val vm: AiChatViewModel = koinViewModel(key = "ai-chat-${sessionId ?: "baru"}") { parametersOf(sessionId) }
    val state by vm.state.collectAsStateWithLifecycle()
    AiChatContent(state, vm, contentPadding, onBack, backButton, onSettings, onDeleted)
}

@Composable
fun AiChatContent(
    state: ChatUiState,
    actions: ChatActions,
    contentPadding: PaddingValues,
    onBack: (() -> Unit)?,
    backButton: @Composable (() -> Unit) -> Unit,
    onSettings: () -> Unit,
    onDeleted: () -> Unit,
) {
    val haptics = rememberHaptics()
    // TextFieldValue: teks yang diisi dari luar (Ubah, saran) menaruh kursor di AKHIR, bukan di awal.
    var input by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    fun fill(text: String) { input = TextFieldValue(text, TextRange(text.length)) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmAccess by rememberSaveable { mutableStateOf(false) }
    var modelSheet by rememberSaveable { mutableStateOf(false) }
    val submit: () -> Unit = {
        val text = input.text.trim()
        if (text.isNotEmpty() && !state.running && state.ready) {
            haptics.tap()
            if (editing) actions.editLast(text) else actions.send(text)
            input = TextFieldValue("")
            editing = false
        }
    }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .padding(contentPadding).consumeWindowInsets(contentPadding).imePadding(),
    ) {
        ChatTopBar(state, actions, onBack, backButton, onSettings, onDeleted)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.items.isEmpty() && state.loaded) {
                EmptyChat(state, onPick = ::fill)
            } else {
                MessageList(
                    state, actions,
                    onEdit = { text -> fill(text); editing = true },
                )
            }
        }
        AnimatedVisibility(!state.ready && state.loaded) { SetupBanner(onSettings) }
        Composer(
            value = input, onValue = { input = it }, editing = editing,
            onCancelEdit = { editing = false; input = TextFieldValue("") },
            running = state.running, enabled = state.ready, onSend = submit, onStop = { haptics.tap(); actions.stop() },
            model = state.model, dataAccess = state.dataAccess,
            onModel = { modelSheet = true },
            onToggleAccess = { if (state.dataAccess) actions.setDataAccess(false) else confirmAccess = true },
        )
    }
    if (confirmAccess) DataAccessDialog(state.host, onConfirm = { actions.setDataAccess(true); confirmAccess = false }, onDismiss = { confirmAccess = false })
    if (modelSheet) ModelSheet(state, onPick = { actions.setModel(it); modelSheet = false }, onSettings = { modelSheet = false; onSettings() }, onDismiss = { modelSheet = false })
}

// ------------------------------------------------------------------ bilah atas

@Composable
private fun ChatTopBar(
    state: ChatUiState,
    actions: ChatActions,
    onBack: (() -> Unit)?,
    backButton: @Composable (() -> Unit) -> Unit,
    onSettings: () -> Unit,
    onDeleted: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) backButton(onBack) else Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(
                state.title.ifBlank { "Chat baru" },
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = SerifFamily),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(state.model.ifBlank { "belum ada model" }, state.host).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Menu percakapan") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (state.sessionId != null) {
                    DropdownMenuItem(text = { Text("Ganti nama") }, onClick = { menu = false; dialog = "rename" })
                    DropdownMenuItem(text = { Text("System prompt percakapan ini") }, onClick = { menu = false; dialog = "prompt" })
                }
                DropdownMenuItem(text = { Text("Pengaturan AI") }, leadingIcon = { Icon(Icons.Outlined.Tune, null) }, onClick = { menu = false; onSettings() })
                if (state.sessionId != null) {
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Hapus percakapan", color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; dialog = "delete" },
                    )
                }
            }
        }
    }
    when (dialog) {
        "rename" -> TextDialog("Ganti nama", state.title, singleLine = true, onSave = { actions.rename(it); dialog = null }, onDismiss = { dialog = null })
        "prompt" -> TextDialog(
            "System prompt", state.systemPrompt, singleLine = false,
            hint = "Hanya untuk percakapan ini. Variabel {hari} {tanggal} {jam} {zona} diisi otomatis.",
            onSave = { actions.setSystemPrompt(it); dialog = null }, onDismiss = { dialog = null },
        )
        "delete" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Hapus percakapan?") },
            text = { Text("Semua pesan di percakapan ini dihapus dari perangkat. Tidak bisa dibatalkan.") },
            confirmButton = { TextButton(onClick = { dialog = null; actions.delete(onDeleted) }) { Text("Hapus", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Batal") } },
        )
    }
}

@Composable
internal fun TextDialog(title: String, initial: String, singleLine: Boolean, hint: String? = null, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value, { value = it }, singleLine = singleLine, minLines = if (singleLine) 1 else 5, maxLines = if (singleLine) 1 else 12, modifier = Modifier.fillMaxWidth())
                hint?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value) }, enabled = value.isNotBlank()) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

// ------------------------------------------------------------------ daftar pesan

@Composable
private fun MessageList(state: ChatUiState, actions: ChatActions, onEdit: (String) -> Unit) {
    val list = rememberLazyListState()
    // Ikuti jawaban ke bawah — kecuali pengguna sedang membaca ke atas.
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress to list.canScrollForward }.distinctUntilChanged().collect { (scrolling, canForward) ->
            if (!canForward) follow = true else if (scrolling) follow = false
        }
    }
    val last = state.items.lastOrNull()
    val growth = (last as? ChatItem.Assistant)?.let { it.text.length + it.tools.size * 1000 + it.state.ordinal } ?: 0
    LaunchedEffect(state.items.size) {
        if (state.items.isNotEmpty() && (follow || last is ChatItem.User)) {
            follow = true
            list.animateScrollToItem(state.items.size) // ke penanda akhir
        }
    }
    LaunchedEffect(growth) { if (follow && state.items.isNotEmpty()) list.scrollToItem(state.items.size) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(state.items, key = { it.id }, contentType = { it::class }) { item ->
                Box(Modifier.widthIn(max = ChatMaxWidth).fillMaxWidth().animateItem()) {
                    when (item) {
                        is ChatItem.User -> UserMessage(item, onEdit)
                        is ChatItem.Assistant -> AssistantMessage(item, state.running, actions)
                    }
                }
            }
            item(key = "akhir") { Spacer(Modifier.size(1.dp)) }
        }
        AnimatedVisibility(
            visible = !follow && list.canScrollForward,
            enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
        ) {
            SmallFloatingActionButton(onClick = { follow = true }, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Ke pesan terbaru")
            }
        }
    }
    LaunchedEffect(follow) { if (follow && state.items.isNotEmpty()) list.animateScrollToItem(state.items.size) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserMessage(item: ChatItem.User, onEdit: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            color = TaskifyTheme.modules.accent(TaskifyModule.Assistant).copy(alpha = 0.14f),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp),
            // Gelembung mengikuti panjang teks; sisi kiri selalu menyisakan ruang supaya terbaca sebagai pesan sendiri.
            modifier = Modifier.padding(start = 48.dp).widthIn(max = 560.dp)
                .clip(RoundedCornerShape(20.dp))
                .combinedClickable(onClick = {}, onLongClick = { menu = true }, onLongClickLabel = "Menu pesan"),
        ) {
            Text(item.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Salin") }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }, onClick = { clipboard.setText(AnnotatedString(item.text)); menu = false })
            if (item.editable) DropdownMenuItem(text = { Text("Ubah") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { onEdit(item.text); menu = false })
        }
    }
}

@Composable
private fun AssistantMessage(item: ChatItem.Assistant, running: Boolean, actions: ChatActions) {
    val clipboard = LocalClipboardManager.current
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (item.text.isNotBlank()) {
            SelectionContainer { ChatMarkdown(item.text, streaming = item.streaming) }
        } else if (item.streaming && item.tools.isEmpty()) {
            Thinking()
        }
        item.tools.forEach { t ->
            if (t.status == ToolStatus.Awaiting && t.preview != null) {
                ConfirmCard(t, onDecide = { approve -> if (approve) haptics.success() else haptics.tap(); actions.decide(t.callId, approve) })
            } else {
                ToolRow(t)
            }
        }
        when (item.state) {
            ChatState.Error -> ErrorCard(item.error ?: "Gagal mendapat jawaban.", canRetry = item.last && !running, onRetry = actions::retry)
            ChatState.Stopped -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dihentikan", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.last && !running) TextButton(onClick = actions::retry) { Text("Jawab ulang") }
            }
            else -> Unit
        }
        val finished = !item.streaming && item.state == ChatState.Done && item.text.isNotBlank()
        AnimatedVisibility(finished, enter = fadeIn(tween(240))) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                SmallAction(Icons.Outlined.ContentCopy, "Salin jawaban") { clipboard.setText(AnnotatedString(item.text)); haptics.success() }
                if (item.last && !running) SmallAction(Icons.Outlined.Refresh, "Jawab ulang") { actions.retry() }
            }
        }
    }
}

@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Tiga titik berdenyut berurutan — "sedang berpikir". */
@Composable
private fun Thinking() {
    val t = rememberInfiniteTransition(label = "berpikir")
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Assistant)
    Row(
        Modifier.padding(vertical = 8.dp).semantics { contentDescription = "Asisten sedang menyiapkan jawaban" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(3) { i ->
            val a by t.animateFloat(
                0.25f, 1f,
                infiniteRepeatable(tween(560, delayMillis = i * 160), RepeatMode.Reverse), label = "titik$i",
            )
            Box(Modifier.size(8.dp).alpha(a).background(accent, CircleShape))
        }
    }
}

@Composable
private fun ToolRow(t: ToolActivity) {
    var open by remember { mutableStateOf(false) }
    val (icon, tint, status) = when (t.status) {
        ToolStatus.Running -> Triple(Icons.Outlined.HourglassTop, MaterialTheme.colorScheme.onSurfaceVariant, "berjalan…")
        ToolStatus.Done -> Triple(Icons.Outlined.CheckCircle, TaskifyTheme.modules.accent(TaskifyModule.Assistant), "selesai")
        ToolStatus.Failed -> Triple(Icons.Outlined.ErrorOutline, MaterialTheme.colorScheme.error, "gagal")
        ToolStatus.Rejected -> Triple(Icons.Outlined.Block, MaterialTheme.colorScheme.onSurfaceVariant, "dibatalkan")
        ToolStatus.Skipped -> Triple(Icons.Outlined.Block, MaterialTheme.colorScheme.onSurfaceVariant, "tidak dijalankan")
        ToolStatus.Awaiting -> Triple(Icons.Outlined.HourglassTop, MaterialTheme.colorScheme.onSurfaceVariant, "menunggu")
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(enabled = t.result != null, onClickLabel = if (open) "Tutup hasil" else "Lihat hasil") { open = !open }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("${t.label} · $status", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (t.result != null) Icon(Icons.Outlined.ExpandMore, null, modifier = Modifier.size(18.dp).alpha(0.7f))
            }
            if (open && t.result != null) {
                Text(
                    t.result.take(4_000),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun ConfirmCard(t: ToolActivity, onDecide: (Boolean) -> Unit) {
    val p = t.preview ?: return
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(1.dp, TaskifyTheme.modules.accent(TaskifyModule.Assistant).copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(p.title, style = MaterialTheme.typography.titleSmall)
            p.lines.forEach { (k, v) ->
                Row {
                    Text(k, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp))
                    Text(v, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
            Text("Belum tersimpan sebelum kamu setujui.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onDecide(false) }) { Text("Batal") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onDecide(true) }) { Text("Simpan") }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, canRetry: Boolean, onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                SelectionContainer { Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer) }
            }
            if (canRetry) TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.End)) { Text("Coba lagi") }
        }
    }
}

// ------------------------------------------------------------------ kosong & siapkan

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyChat(state: ChatUiState, onPick: (String) -> Unit) {
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Assistant)
    val ideas = if (state.dataAccess) {
        listOf("Apa saja tugasku minggu ini?", "Ringkas pengeluaranku bulan ini", "Cari catatanku soal rapat", "Buatkan tugas: bayar listrik besok")
    } else {
        listOf("Bantu susun rencana belajar seminggu", "Rapikan kalimat ini supaya lebih sopan", "Jelaskan bunga majemuk dengan sederhana", "Ide judul untuk catatan harian")
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(TaskifyLogo.Full, null, tint = Color.Unspecified, modifier = Modifier.size(64.dp))
        Spacer(Modifier.size(16.dp))
        Text("Mau dibantu apa?", style = MaterialTheme.typography.headlineSmall.copy(fontFamily = SerifFamily))
        Spacer(Modifier.size(6.dp))
        Text(
            if (state.dataAccess) "Asisten bisa membaca data Taskify-mu. Menyimpan sesuatu selalu minta persetujuan dulu."
            else "Akses data Taskify mati — asisten tidak bisa melihat tugas, catatan, atau keuanganmu.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp),
        )
        Spacer(Modifier.size(20.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.widthIn(max = 520.dp)) {
            ideas.forEach { idea -> SuggestionChip(onClick = { onPick(idea) }, label = { Text(idea) }) }
        }
    }
}

@Composable
private fun SetupBanner(onSettings: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Hubungkan penyedia AI dan pilih model dulu.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.weight(1f))
            TextButton(onClick = onSettings) { Text("Atur") }
        }
    }
}

// ------------------------------------------------------------------ input

@Composable
private fun Composer(
    value: TextFieldValue,
    onValue: (TextFieldValue) -> Unit,
    editing: Boolean,
    onCancelEdit: () -> Unit,
    running: Boolean,
    enabled: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    model: String,
    dataAccess: Boolean,
    onModel: () -> Unit,
    onToggleAccess: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 10.dp, top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = ChatMaxWidth).fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = onModel,
                    label = { Text(model.ifBlank { "Pilih model" }, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp)) },
                    leadingIcon = { Icon(TaskifyLogo.Mono, null, modifier = Modifier.size(18.dp)) },
                )
                FilterChip(
                    selected = dataAccess, onClick = onToggleAccess,
                    label = { Text(if (dataAccess) "Akses data: hidup" else "Akses data: mati") },
                    leadingIcon = { Icon(if (dataAccess) Icons.Outlined.LockOpen else Icons.Outlined.Lock, null, modifier = Modifier.size(16.dp)) },
                )
                AnimatedVisibility(editing) {
                    AssistChip(onClick = onCancelEdit, label = { Text("Mengubah pesan") }, trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = "Batal ubah", modifier = Modifier.size(16.dp)) })
                }
            }
            Spacer(Modifier.size(6.dp))
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(4.dp)) {
                    TextField(
                        value = value, onValueChange = onValue,
                        placeholder = { Text(if (enabled) "Tanya apa saja…" else "Atur AI dulu di Pengaturan") },
                        maxLines = 6, enabled = enabled,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    AnimatedContent(running, transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.8f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.8f)) }, label = "kirim") { busy ->
                        if (busy) {
                            FilledTonalIconButton(onClick = onStop, modifier = Modifier.padding(4.dp)) { Icon(Icons.Rounded.Stop, contentDescription = "Hentikan jawaban") }
                        } else {
                            FilledIconButton(onClick = onSend, enabled = enabled && value.text.isNotBlank(), modifier = Modifier.padding(4.dp)) {
                                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Kirim")
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ dialog & lembar

@Composable
internal fun DataAccessDialog(host: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.LockOpen, null) },
        title = { Text("Izinkan akses data?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Asisten bisa membaca tugas, catatan, jurnal, dan keuanganmu lewat alat saat dibutuhkan.")
                Text("Data yang dibaca ikut dikirim ke ${host.ifBlank { "penyedia AI" }}.", style = MaterialTheme.typography.bodyMedium)
                Text("Membuat atau mengubah sesuatu selalu minta persetujuanmu. Sandi tidak pernah bisa dibaca.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Izinkan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSheet(state: ChatUiState, onPick: (String) -> Unit, onSettings: () -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        ModelPicker(state.models, state.model, query, { query = it }, onPick, onSettings)
    }
}

/** Isi pemilih model (dipisah supaya bisa diuji tanpa lembar). */
@Composable
fun ModelPicker(models: List<String>, current: String, query: String, onQuery: (String) -> Unit, onPick: (String) -> Unit, onSettings: () -> Unit) {
    val filtered = remember(models, query) { models.filter { it.contains(query.trim(), ignoreCase = true) }.take(200) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Model untuk percakapan ini", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(query, onQuery, label = { Text("Cari atau ketik nama model") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (query.isNotBlank() && filtered.none { it.equals(query.trim(), true) }) {
            OutlinedButton(onClick = { onPick(query.trim()) }, modifier = Modifier.fillMaxWidth()) { Text("Pakai \"${query.trim()}\"") }
        }
        if (models.isEmpty()) {
            Text("Daftar model belum dimuat. Buka Pengaturan AI → Tes koneksi, atau ketik nama model di atas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onSettings) { Text("Pengaturan AI") }
        }
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(filtered, key = { it }) { m ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(m) }.padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(m, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (m == current) Icon(Icons.Outlined.CheckCircle, contentDescription = "Dipakai", tint = TaskifyTheme.modules.accent(TaskifyModule.Assistant), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
