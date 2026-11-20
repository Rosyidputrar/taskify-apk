package com.rosyidputrar.taskify.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChecklistRtl
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.designsystem.component.fadingEdges
import com.rosyidputrar.taskify.core.designsystem.component.rememberHaptics
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyLogo
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.home.HomeShortcut
import com.rosyidputrar.taskify.core.domain.home.HomeShortcuts
import com.rosyidputrar.taskify.core.domain.home.QuickView
import com.rosyidputrar.taskify.core.domain.home.ShortcutModule
import com.rosyidputrar.taskify.core.domain.home.ShortcutRef
import com.rosyidputrar.taskify.core.domain.home.ShortcutType

// ============================================================ tampilan per jenis

internal fun ShortcutModule.module(): TaskifyModule = when (this) {
    ShortcutModule.Tasks, ShortcutModule.Focus -> TaskifyModule.Tasks
    ShortcutModule.Journal -> TaskifyModule.Journal
    ShortcutModule.Notes -> TaskifyModule.Notes
    ShortcutModule.Finance -> TaskifyModule.Finance
    ShortcutModule.Vault -> TaskifyModule.Vault
    ShortcutModule.Sandi -> TaskifyModule.Sandi
    ShortcutModule.Calendar -> TaskifyModule.Calendar
    ShortcutModule.Assistant -> TaskifyModule.Assistant
    ShortcutModule.Review -> TaskifyModule.Review
    ShortcutModule.Notifications -> TaskifyModule.Notifications
    ShortcutModule.Wellbeing -> TaskifyModule.Wellbeing
    ShortcutModule.Music -> TaskifyModule.Music
    ShortcutModule.Files -> TaskifyModule.Files
    ShortcutModule.Tags -> TaskifyModule.Tags
}

internal fun ShortcutRef.module(): TaskifyModule = when (type) {
    ShortcutType.Task, ShortcutType.TaskList -> TaskifyModule.Tasks
    ShortcutType.Journal -> TaskifyModule.Journal
    ShortcutType.Note -> TaskifyModule.Notes
    ShortcutType.Transaction, ShortcutType.Wallet -> TaskifyModule.Finance
    ShortcutType.Snippet, ShortcutType.Project, ShortcutType.Config -> TaskifyModule.Vault
    ShortcutType.AiChat -> TaskifyModule.Assistant
    ShortcutType.Tag -> TaskifyModule.Tags
    ShortcutType.View -> view?.module?.module() ?: TaskifyModule.Home
}

internal fun ShortcutRef.icon(): ImageVector = when (type) {
    ShortcutType.Task -> Icons.Outlined.ChecklistRtl
    ShortcutType.TaskList -> Icons.Outlined.ListAlt
    ShortcutType.Journal -> Icons.Outlined.AutoStories
    ShortcutType.Note -> Icons.AutoMirrored.Outlined.StickyNote2
    ShortcutType.Transaction -> Icons.Outlined.ReceiptLong
    ShortcutType.Wallet -> Icons.Outlined.AccountBalanceWallet
    ShortcutType.Snippet -> Icons.Outlined.Code
    ShortcutType.Project -> Icons.Outlined.Folder
    ShortcutType.Config -> Icons.Outlined.DataObject
    ShortcutType.AiChat -> TaskifyLogo.Assistant
    ShortcutType.Tag -> Icons.Outlined.LocalOffer
    ShortcutType.View -> when (view) {
        QuickView.TasksToday -> Icons.Outlined.Today
        QuickView.TasksOverdue -> Icons.Outlined.Schedule
        QuickView.TasksUpcoming -> Icons.Outlined.CalendarMonth
        QuickView.JournalToday -> Icons.Outlined.EditNote
        QuickView.JournalInsight, QuickView.Review -> Icons.Outlined.Insights
        QuickView.FinanceTransactions -> Icons.Outlined.SwapHoriz
        QuickView.FinanceBudget -> Icons.Outlined.PieChart
        QuickView.FinanceDebt -> Icons.Outlined.Payments
        QuickView.NewTransaction -> Icons.Outlined.ReceiptLong
        QuickView.NotificationDrafts -> Icons.Outlined.NotificationsActive
        QuickView.SandiCodes -> Icons.Outlined.Key
        QuickView.SandiGenerator -> Icons.Outlined.Password
        QuickView.VaultConfig -> Icons.Outlined.DataObject
        QuickView.VaultProjects -> Icons.Outlined.Folder
        QuickView.CalendarToday -> Icons.Outlined.CalendarMonth
        QuickView.FocusStart -> Icons.Outlined.Timer
        QuickView.AiNewChat -> TaskifyLogo.Assistant
        QuickView.Music -> Icons.Outlined.MusicNote
        QuickView.Wellbeing -> Icons.Outlined.Timelapse
        QuickView.Files -> Icons.Outlined.FolderZip
        null -> Icons.Outlined.PushPin
    }
}

/** Keterangan kecil kartu: tampilan cepat → petunjuknya; isi → "Jenis · keterangan". */
internal fun HomeShortcut.caption(): String {
    ref.view?.let { return it.hint }
    val sub = subtitle?.takeIf { it.isNotBlank() }?.let { raw ->
        when {
            ref.type == ShortcutType.Wallet -> com.rosyidputrar.taskify.core.domain.finance.WalletType.from(raw).label
            // Tanggal catatan/jurnal/transaksi: "5 Okt 2026", bukan "2026-10-05".
            ISO_DATE.matches(raw) -> runCatching { com.rosyidputrar.taskify.core.domain.time.Dates.format(raw, "d MMM yyyy") }.getOrDefault(raw)
            else -> raw
        }
    }
    return if (sub == null) ref.type.label else "${ref.type.label} · $sub"
}

private val ISO_DATE = Regex("""\d{4}-\d{2}-\d{2}""")

/** Semua tampilan cepat sebagai calon pintasan. */
internal val QUICK_VIEW_SHORTCUTS: List<HomeShortcut> = QuickView.entries.map { HomeShortcut(ShortcutRef.view(it), it.label, it.hint) }

// ============================================================ bagian di Hari ini

/**
 * Bagian Pintasan: baris kartu yang bisa digeser. Sematan dulu (urutan pengguna), lalu "Lanjutkan"
 * otomatis, lalu kartu "Tambah". Tekan lama kartu → sematkan/lepas, geser, atau atur.
 */
@Composable
internal fun ShortcutsSection(
    row: List<HomeShortcut>,
    pinnedCount: Int,
    nowMs: Long,
    actions: HomeShortcutActions,
    onAdd: () -> Unit,
    onArrange: () -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Pintasan", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
            TextButton(onClick = onArrange) {
                Icon(Icons.Outlined.Tune, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Atur")
            }
        }
        val listState = rememberLazyListState()
        LazyRow(
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fadingEdges(listState),
        ) {
            items(row, key = { it.ref.key }) { sc ->
                val pinIndex = if (sc.pinned) row.indexOf(sc) else -1
                ShortcutCard(
                    sc,
                    nowMs = nowMs,
                    canMoveLeft = sc.pinned && pinIndex > 0,
                    canMoveRight = sc.pinned && pinIndex in 0 until pinnedCount - 1,
                    actions = actions,
                    onArrange = onArrange,
                    modifier = Modifier.animateItem(),
                )
            }
            item(key = "add") { AddShortcutCard(onAdd, compact = row.isNotEmpty()) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortcutCard(
    sc: HomeShortcut,
    nowMs: Long,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    actions: HomeShortcutActions,
    onArrange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val module = sc.ref.module()
    val accent = TaskifyTheme.modules.accent(module)
    var menu by remember { mutableStateOf(false) }
    val caption = if (sc.pinned) sc.caption() else HomeShortcuts.agoLabel(sc.updatedAt, nowMs)?.let { "Lanjutkan · $it" } ?: "Lanjutkan · ${sc.ref.type.label}"
    val pinLabel = if (sc.pinned) "Lepas dari Hari ini" else "Sematkan ke Hari ini"
    Box(modifier) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = TaskifyTheme.modules.tint(module, MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier
                .width(156.dp)
                .heightIn(min = 112.dp)
                .clip(MaterialTheme.shapes.large)
                .combinedClickable(
                    onClickLabel = "Buka",
                    onLongClickLabel = "Pilihan pintasan",
                    role = Role.Button,
                    onLongClick = { haptics.tick(); menu = true },
                    onClick = { haptics.tap(); actions.open(sc.ref) },
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = "${sc.title}. $caption"
                    if (sc.pinned) stateDescription = "Disematkan"
                    customActions = listOfNotNull(
                        CustomAccessibilityAction(pinLabel) { actions.togglePin(sc); true },
                        if (canMoveLeft) CustomAccessibilityAction("Geser ke kiri") { actions.move(sc.ref, -1); true } else null,
                        if (canMoveRight) CustomAccessibilityAction("Geser ke kanan") { actions.move(sc.ref, 1); true } else null,
                    )
                },
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.size(32.dp).background(accent.copy(alpha = 0.16f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Icon(sc.ref.icon(), null, tint = accent, modifier = Modifier.size(18.dp)) }
                    Spacer(Modifier.weight(1f))
                    if (sc.pinned) Icon(Icons.Rounded.PushPin, null, tint = accent.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text(sc.title.ifBlank { sc.ref.type.label }, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(pinLabel) },
                leadingIcon = { Icon(if (sc.pinned) Icons.Outlined.Close else Icons.Outlined.PushPin, null) },
                onClick = { menu = false; actions.togglePin(sc) },
            )
            if (canMoveLeft) DropdownMenuItem(
                text = { Text("Geser ke kiri") },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) },
                onClick = { menu = false; actions.move(sc.ref, -1) },
            )
            if (canMoveRight) DropdownMenuItem(
                text = { Text("Geser ke kanan") },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, null) },
                onClick = { menu = false; actions.move(sc.ref, 1) },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Atur pintasan…") },
                leadingIcon = { Icon(Icons.Outlined.Tune, null) },
                onClick = { menu = false; onArrange() },
            )
        }
    }
}

@Composable
private fun AddShortcutCard(onAdd: () -> Unit, compact: Boolean) {
    Surface(
        onClick = onAdd,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.width(if (compact) 112.dp else 220.dp).heightIn(min = 112.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.Center) {
            Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text("Tambah pintasan", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            if (!compact) {
                Text(
                    "Sematkan catatan, snippet, dompet, atau tampilan seperti \"Tugas terlambat\".",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ============================================================ lembar "Tambah pintasan"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShortcutPickerSheet(state: HomeUiState, actions: HomeShortcutActions, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = { actions.setPickerQuery(""); onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        ShortcutPicker(state, actions, onDone = { actions.setPickerQuery(""); onDismiss() })
    }
}

/** Isi lembar pemilih (tanpa sheet — bisa diuji). Ketuk baris = sematkan/lepas; perubahan langsung terlihat. */
@Composable
internal fun ShortcutPicker(state: HomeUiState, actions: HomeShortcutActions, onDone: () -> Unit) {
    val picker = state.picker
    val q = picker.query.trim()
    val pinnedKeys = picker.pinnedKeys.ifEmpty { state.pinned.mapTo(HashSet()) { it.ref.key } }
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("HARI INI", style = TaskifyTextStyles.indexLabel, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tambah pintasan", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
                Text(
                    "${pinnedKeys.size}/${HomeShortcuts.MAX_PINNED}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { contentDescription = "${pinnedKeys.size} dari ${HomeShortcuts.MAX_PINNED} pintasan disematkan" },
                )
            }
            OutlinedTextField(
                value = picker.query,
                onValueChange = actions::setPickerQuery,
                placeholder = { Text("Cari catatan, snippet, tugas, dompet…") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = if (picker.query.isNotEmpty()) ({ IconButton(onClick = { actions.setPickerQuery("") }) { Icon(Icons.Outlined.Close, contentDescription = "Hapus pencarian") } }) else null,
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
        }
        val groups: List<Pair<String, List<HomeShortcut>>> = if (q.isEmpty()) {
            listOf(
                "TAMPILAN CEPAT" to QUICK_VIEW_SHORTCUTS,
                "TERAKHIR DIUBAH" to state.recent,
                "DAFTAR, DOMPET & TAG" to picker.collections,
            )
        } else {
            val match: (HomeShortcut) -> Boolean = { it.title.contains(q, ignoreCase = true) || (it.subtitle?.contains(q, ignoreCase = true) == true) }
            listOf(
                "TAMPILAN CEPAT" to QUICK_VIEW_SHORTCUTS.filter(match),
                "DAFTAR, DOMPET & TAG" to picker.collections.filter(match),
                "HASIL PENCARIAN" to picker.results,
            )
        }.filter { it.second.isNotEmpty() }
        LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 520.dp)) {
            groups.forEach { (label, list) ->
                item(key = "h-$label") {
                    Text(label, style = TaskifyTextStyles.indexLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp))
                }
                items(list, key = { "$label-${it.ref.key}" }) { sc ->
                    val pinned = sc.ref.key in pinnedKeys
                    PickerRow(sc, pinned) { haptics.toggle(!pinned); actions.togglePin(sc) }
                }
            }
            if (groups.isEmpty()) item(key = "none") {
                Text(
                    if (picker.searching) "Mencari…" else "Tidak ada yang cocok dengan \"$q\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDone) { Text("Selesai") }
        }
    }
}

@Composable
private fun PickerRow(sc: HomeShortcut, pinned: Boolean, onToggle: () -> Unit) {
    val module = sc.ref.module()
    val accent = TaskifyTheme.modules.accent(module)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch, onClickLabel = if (pinned) "Lepas" else "Sematkan", onClick = onToggle)
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { stateDescription = if (pinned) "Disematkan" else "Belum disematkan" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).background(accent.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(sc.ref.icon(), null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(sc.title.ifBlank { sc.ref.type.label }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sc.caption(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(
            if (pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
            contentDescription = null,
            tint = if (pinned) accent else MaterialTheme.colorScheme.outline,
        )
    }
}
