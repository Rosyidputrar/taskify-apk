package com.rosyidputrar.taskify.feature.sandi

import androidx.compose.runtime.LaunchedEffect
import com.rosyidputrar.taskify.core.designsystem.component.rememberHaptics
import com.rosyidputrar.taskify.core.designsystem.component.TaskifyHaptics
import com.rosyidputrar.taskify.core.designsystem.component.ListSkeleton
import com.rosyidputrar.taskify.core.designsystem.component.LoadingBlock
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.State
import com.rosyidputrar.taskify.core.designsystem.component.FitText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rosyidputrar.taskify.core.designsystem.component.EmptyState
import com.rosyidputrar.taskify.core.designsystem.component.SecureWindow
import com.rosyidputrar.taskify.core.designsystem.component.IndexCard
import com.rosyidputrar.taskify.core.designsystem.component.ModuleHeader
import com.rosyidputrar.taskify.core.designsystem.theme.SerifFamily
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.sandi.KEY_REJECTED_MESSAGE
import com.rosyidputrar.taskify.core.domain.sandi.Totp
import com.rosyidputrar.taskify.core.domain.sandi.displayHost
import com.rosyidputrar.taskify.core.domain.sandi.rotationRejectedMessage
import com.rosyidputrar.taskify.core.sandi.SandiItem
import com.rosyidputrar.taskify.core.sandi.VaultStatus
import com.rosyidputrar.taskify.feature.sandi.component.CountdownRing
import com.rosyidputrar.taskify.feature.sandi.component.SandiAvatar
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.abs

@Composable
fun SandiScreen(
    contentPadding: PaddingValues,
    onBack: (() -> Unit)? = null,
    backButton: @Composable (() -> Unit) -> Unit = {},
    viewModel: SandiViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Detak 1 dtk dibaca HANYA oleh baris kode 2FA (bukan seluruh layar) → daftar tidak disusun ulang tiap detik.
    val clock = viewModel.clock.collectAsStateWithLifecycle()
    SecureWindow()
    SandiContent(state, viewModel, contentPadding, onBack, backButton, clock)
}

@Composable
fun SandiContent(
    state: SandiUiState,
    actions: SandiActions,
    contentPadding: PaddingValues,
    onBack: (() -> Unit)? = null,
    backButton: @Composable (() -> Unit) -> Unit = {},
    clock: State<Long>? = null,
) {
    val haptics = rememberHaptics()
    @Suppress("NAME_SHADOWING")
    val actions = remember(actions, haptics) { HapticSandiActions(actions, haptics) }
    // Master password salah / gagal → getar penolakan; brankas terbuka → getar berhasil.
    LaunchedEffect(state.error) { if (state.error != null) haptics.error() }
    var wasLocked by remember { mutableStateOf(state.status is VaultStatus.Locked) }
    LaunchedEffect(state.status is VaultStatus.Unlocked) {
        if (state.status is VaultStatus.Unlocked && wasLocked) haptics.success()
        wasLocked = state.status is VaultStatus.Locked
    }
    val fixedClock = remember(state.now) { mutableLongStateOf(state.now) }
    val liveClock = clock ?: fixedClock
    val now: () -> Long = remember(liveClock) { { liveClock.value } }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var quick by rememberSaveable { mutableStateOf(false) }
    var history by rememberSaveable { mutableStateOf(false) }
    val unlocked = state.status is VaultStatus.Unlocked
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Sandi)

    LazyColumn(
        Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope { while (true) { awaitPointerEvent(PointerEventPass.Initial); actions.touch() } }
        },
        contentPadding = contentPadding,
    ) {
        item(key = "header") {
            ModuleHeader(
                TaskifyModule.Sandi, "Sandi",
                subtitle = when (state.status) {
                    is VaultStatus.Unlocked -> "${state.items.items.size} sandi · ${state.items.items.count { it.payload.totp != null }} kode 2FA"
                    is VaultStatus.Locked -> "Terkunci"
                    VaultStatus.NoVault -> "Belum ada brankas"
                    VaultStatus.Loading -> null
                },
                navigation = onBack?.let { { backButton(it) } },
                actions = {
                    if (unlocked) {
                        IconButton(onClick = { settings = true }) { Icon(Icons.Outlined.Settings, contentDescription = "Pengaturan brankas") }
                        IconButton(onClick = actions::lock) { Icon(Icons.Outlined.Lock, contentDescription = "Kunci sekarang") }
                        if (state.tab == SandiTab.Vault) FilledTonalIconButton(
                            onClick = { creating = true },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = accent, contentColor = TaskifyTheme.modules.onAccent(TaskifyModule.Sandi)),
                        ) { Icon(Icons.Rounded.Add, contentDescription = "Sandi baru") }
                    }
                },
            )
        }
        when (state.status) {
            VaultStatus.Loading -> item(key = "loading") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingBlock(label = "Membuka brankas…", module = TaskifyModule.Sandi) }
            }
            VaultStatus.NoVault -> {
                if (state.rejectedKeyCreation) item(key = "b-key") { Banner(KEY_REJECTED_MESSAGE, actions::dismissRejectedKey) }
                item(key = "setup") { SetupPanel(state, actions) }
            }
            is VaultStatus.Locked -> {
                banners(state, actions)
                item(key = "unlock") { UnlockPanel(state, actions, onQuickCheck = { quick = true }) }
            }
            is VaultStatus.Unlocked -> {
                item(key = "tabs") {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
                        SandiTab.entries.forEachIndexed { i, t ->
                            SegmentedButton(selected = state.tab == t, onClick = { actions.setTab(t) }, shape = SegmentedButtonDefaults.itemShape(i, SandiTab.entries.size), label = { FitText(t.label) })
                        }
                    }
                }
                banners(state, actions)
                when (state.tab) {
                    SandiTab.Vault -> vaultList(state, actions, now, onOpen = { open = it.id })
                    SandiTab.Codes -> codes(state, actions, now, onQuick = { quick = true }, onHistory = { history = true })
                    SandiTab.Generator -> item(key = "gen") { GeneratorPanel(state, actions) }
                }
            }
        }
    }

    val byId = { id: String? -> state.items.items.firstOrNull { it.id == id } }
    if (unlocked) {
        byId(open)?.let { item -> ItemSheet(item, now, actions, onEdit = { editing = item.id; open = null }, onDismiss = { open = null }) }
        if (creating) ItemEditorSheet(null, state, actions, onDismiss = { creating = false })
        byId(editing)?.let { item -> ItemEditorSheet(item, state, actions, onDismiss = { editing = null }) }
        if (settings) SettingsSheet(state, actions, onDismiss = { settings = false })
        if (history) HistorySheet(state, actions, onDismiss = { history = false })
    }
    // Di luar `if (unlocked)`: pemilih lokasi bisa kembali saat brankas sudah terkunci otomatis.
    ExportHost(state, actions)
    if (quick) QuickCheckSheet(state, actions, now, onDismiss = { quick = false })
    state.recoveryToShow?.let { RecoveryKeyDialog(it, state.email, actions) }
}

private fun LazyListScope.banners(state: SandiUiState, actions: SandiActions) {
    state.rejectedRotation?.let { code -> item(key = "b-rot") { Banner(rotationRejectedMessage(code), actions::dismissRejectedRotation) } }
    if (state.staleWrites > 0) item(key = "b-stale") {
        Banner("${state.staleWrites} perubahan ditolak server karena dienkripsi dengan kunci lama (brankas diganti kuncinya di perangkat lain). Buka lagi lalu ulangi perubahannya.", actions::dismissStaleWrites)
    }
    if (state.items.failed.isNotEmpty()) item(key = "b-fail") {
        Banner("${state.items.failed.size} item tidak bisa dibuka dengan kunci ini (mungkin masih disinkronkan setelah ganti kunci).", null)
    }
    if (Totp.isClockSkewed(state.clockOffsetMs)) item(key = "b-clock") {
        Banner("Jam perangkat selisih ${abs(state.clockOffsetMs) / 1000} detik dari server. Kode 2FA sudah disesuaikan otomatis.", null, warning = false)
    }
}

@Composable
internal fun Banner(text: String, onDismiss: (() -> Unit)?, warning: Boolean = true) {
    Surface(
        color = if (warning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (warning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (onDismiss != null) IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Tutup") }
        }
    }
}

private fun LazyListScope.vaultList(state: SandiUiState, actions: SandiActions, now: () -> Long, onOpen: (SandiItem) -> Unit) {
    item(key = "search") {
        OutlinedTextField(
            state.search, actions::setSearch,
            placeholder = { Text("Cari judul, situs, nama pengguna…") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = if (state.search.isNotEmpty()) ({ IconButton(onClick = { actions.setSearch("") }) { Icon(Icons.Rounded.Close, contentDescription = "Hapus pencarian") } }) else null,
            singleLine = true, shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
    val list = state.filtered
    // Item didekripsi setelah brankas terbuka — kerangka selama proses itu.
    if (!state.items.ready) item(key = "skeleton") { ListSkeleton(TaskifyModule.Sandi, rows = 5, label = "Mendekripsi isi brankas…") }
    if (state.items.ready && list.isEmpty()) item(key = "empty") {
        EmptyState(
            TaskifyModule.Sandi,
            if (state.search.isNotBlank()) "Tidak ada yang cocok" else "Brankas masih kosong",
            if (state.search.isNotBlank()) "Coba kata lain." else "Tambahkan sandi pertama dengan tombol +. Semua isinya dienkripsi sebelum meninggalkan perangkat.",
        )
    }
    items(list, key = { it.id }) { item -> ItemRow(item, now, actions, onClick = { onOpen(item) }, modifier = Modifier.animateItem()) }
}

@Composable
fun ItemRow(item: SandiItem, now: () -> Long, actions: SandiActions, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = item.payload
    IndexCard(TaskifyModule.Sandi, onClick = onClick, onClickLabel = "Buka sandi", modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp), contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SandiAvatar(p.title)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.title.ifBlank { displayHost(p.url).ifBlank { "(tanpa judul)" } }, style = MaterialTheme.typography.titleMedium.copy(fontFamily = SerifFamily),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (p.favorite) Icon(Icons.Rounded.Star, contentDescription = "Favorit", tint = TaskifyTheme.modules.accent(TaskifyModule.Sandi), modifier = Modifier.padding(start = 4.dp).size(16.dp))
                }
                Text(listOf(p.username, displayHost(p.url)).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "—" },
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // D-09: di tab Brankas kode TOTP TIDAK ditampilkan — hanya ikon salin. Kode hanya
            // tampil di tab Kode (CodeRow). Sebelumnya kode terlihat polos di daftar brankas.
            val w = p.totp?.let { Totp.window(it, now()) }
            if (w != null) {
                IconButton(onClick = { actions.copyCode(item, w.code) }) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Salin kode 2FA") }
            } else if (p.password.isNotEmpty()) {
                IconButton(onClick = { actions.copy("Sandi ${p.title}", p.password) }) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Salin sandi") }
            }
        }
    }
}

private fun LazyListScope.codes(state: SandiUiState, actions: SandiActions, now: () -> Long, onQuick: () -> Unit, onHistory: () -> Unit) {
    item(key = "codes-bar") {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onQuick) { Text("Cek kunci cepat") }
            TextButton(onClick = onHistory) { Text("Riwayat (${state.log.size})") }
        }
    }
    val list = state.withTotp
    if (!state.items.ready) item(key = "codes-skeleton") { ListSkeleton(TaskifyModule.Sandi, rows = 3, label = "Mendekripsi isi brankas…") }
    if (state.items.ready && list.isEmpty()) item(key = "codes-empty") {
        EmptyState(TaskifyModule.Sandi, "Belum ada kode 2FA", "Tambahkan kunci 2FA saat mengubah sandi, atau pakai \"Cek kunci cepat\" untuk menempel kunci dari situs.")
    }
    items(list, key = { "c-" + it.id }) { item -> CodeRow(item, now, actions, Modifier.animateItem()) }
}

@Composable
fun CodeRow(item: SandiItem, now: () -> Long, actions: SandiActions, modifier: Modifier = Modifier) {
    val cfg = item.payload.totp ?: return
    val w = Totp.window(cfg, now()) ?: return
    val secs = ((w.remainingMs + 999) / 1000).toInt()
    IndexCard(TaskifyModule.Sandi, onClick = { actions.copyCode(item, w.code) }, onClickLabel = "Salin kode", modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp), contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SandiAvatar(item.payload.title, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.payload.title.ifBlank { "(tanpa judul)" }, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(spacedCode(w.code), style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                    color = if (secs <= 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                if (secs <= 5) Text("Berikutnya ${spacedCode(w.next)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            CountdownRing(w.remainingMs / (w.period * 1000f), secs, size = 38.dp)
        }
    }
}

/** Getar halus per makna (lihat [TaskifyHaptics]) tanpa mengubah logika aksi. */
private class HapticSandiActions(private val a: SandiActions, private val h: TaskifyHaptics) : SandiActions by a {
    override fun setTab(tab: SandiTab) { h.tick(); a.setTab(tab) }
    override fun copy(what: String, text: String, secret: Boolean) { h.success(); a.copy(what, text, secret) }
    override fun copyCode(item: SandiItem, code: String) { h.success(); a.copyCode(item, code) }
    override fun lock() { h.tap(); a.lock() }
    override fun regenerate() { h.tick(); a.regenerate() }
}
