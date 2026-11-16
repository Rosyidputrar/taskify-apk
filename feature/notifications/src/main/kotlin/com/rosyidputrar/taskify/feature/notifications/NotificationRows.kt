package com.rosyidputrar.taskify.feature.notifications

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddTask
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.common.Rupiah
import com.rosyidputrar.taskify.core.designsystem.component.ConfirmDeleteDialog
import com.rosyidputrar.taskify.core.designsystem.component.rememberHaptics
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.designsystem.theme.namedColor
import com.rosyidputrar.taskify.core.device.ActiveNotifications
import com.rosyidputrar.taskify.core.device.ui.AppIcon
import com.rosyidputrar.taskify.core.device.ui.rememberAppInfo
import com.rosyidputrar.taskify.core.domain.device.formatClockTime
import com.rosyidputrar.taskify.core.domain.finance.TxKind
import com.rosyidputrar.taskify.core.store.device.DraftState
import com.rosyidputrar.taskify.core.store.device.NotifEntry
import kotlinx.coroutines.launch

/** Satu notifikasi: geser kanan = jadikan tugas, geser kiri = sembunyikan. */
@Composable
internal fun NotificationRow(entry: NotifEntry, actions: NotificationsActions, onOpen: (NotifEntry) -> Unit, indent: Boolean = false) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val state = rememberSwipeToDismissBoxState()
    LaunchedEffect(state.targetValue) {
        if (state.targetValue != SwipeToDismissBoxValue.Settled) haptics.threshold()
    }
    SwipeToDismissBox(
        state = state,
        onDismiss = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> actions.makeTask(entry)
                SwipeToDismissBoxValue.EndToStart -> actions.hide(entry)
                SwipeToDismissBoxValue.Settled -> Unit
            }
            scope.launch { state.reset() }
        },
        backgroundContent = {
            val toTask = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val color = if (toTask) TaskifyTheme.modules.accent(TaskifyModule.Tasks) else MaterialTheme.colorScheme.outline
            Box(
                Modifier.fillMaxSize().background(color.copy(alpha = 0.16f)).padding(horizontal = 24.dp),
                contentAlignment = if (toTask) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (toTask) Icons.Outlined.AddTask else Icons.Outlined.VisibilityOff, contentDescription = null, tint = color)
                    Spacer(Modifier.width(8.dp))
                    Text(if (toTask) "Jadikan tugas" else "Sembunyikan", style = MaterialTheme.typography.labelLarge, color = color)
                }
            }
        },
    ) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Row(
                Modifier.fillMaxWidth()
                    .clickable(onClickLabel = "Buka detail notifikasi") { onOpen(entry) }
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction("Jadikan tugas") { actions.makeTask(entry); true },
                            CustomAccessibilityAction("Sembunyikan") { actions.hide(entry); true },
                        )
                    }
                    .padding(start = if (indent) 68.dp else 20.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                if (!indent) {
                    AppIcon(entry.pkg, entry.appLabel)
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (indent) formatClockTime(entry.postedAt) else entry.appLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!indent) Text(formatClockTime(entry.postedAt), style = TaskifyTextStyles.code, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!indent) entry.title?.let { Text(it, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    entry.text?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    Badges(entry)
                }
            }
        }
    }
}

/** Percakapan beruntun dalam jam yang sama: satu baris ringkas, ketuk untuk membuka semuanya. */
@Composable
internal fun NotificationGroup(entries: List<NotifEntry>, actions: NotificationsActions, onOpen: (NotifEntry) -> Unit) {
    var open by rememberSaveable(entries.first().id) { mutableStateOf(false) }
    val first = entries.first()
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = if (open) "Ringkas percakapan" else "Buka semua pesan") { open = !open }
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            AppIcon(first.pkg, first.appLabel)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(first.appLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text("${formatClockTime(entries.last().postedAt)}–${formatClockTime(first.postedAt)}", style = TaskifyTextStyles.code, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "${first.conversation ?: first.title} · ${entries.size} pesan",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!open) first.text?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(open) {
            Column { entries.forEach { NotificationRow(it, actions, onOpen, indent = true) } }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Badges(e: NotifEntry) {
    val chips = buildList {
        e.money?.takeIf { e.draft != null }?.let { m ->
            add((if (m.kind == TxKind.Income) "+" else "−") + Rupiah.format(m.amount) to namedColor(if (m.kind == TxKind.Income) "emerald" else "rose"))
        }
        e.parcel?.let { add("Paket · ${it.stage.label}" to namedColor("orange")) }
        val handled = e.handled
        if (handled != null) add(handled.label to MaterialTheme.colorScheme.onSurfaceVariant)
        else if (e.isTaskLike) add("Tugas?" to TaskifyTheme.modules.accent(TaskifyModule.Tasks))
        if (e.draft == DraftState.Saved) add("Tercatat" to namedColor("emerald"))
    }
    if (chips.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
        chips.forEach { (label, color) -> Chip(label, color) }
    }
}

@Composable
private fun Chip(label: String, color: Color) {
    Surface(color = color.copy(alpha = 0.14f), shape = MaterialTheme.shapes.small) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

// ============================================================ detail

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun NotificationDetailSheet(entry: NotifEntry, actions: NotificationsActions, onOpenDraft: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val appInfo = rememberAppInfo()
    var confirmExclude by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(entry.pkg, entry.appLabel, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.appLabel, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(entry.category.label, formatClockTime(entry.postedAt), entry.conversation).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            SelectionContainer {
                Column {
                    entry.title?.let { Text(it, style = MaterialTheme.typography.titleLarge) }
                    entry.text?.let { Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 4.dp)) }
                        ?: Text("Isi tidak disimpan (tingkat penyimpanan \"Judul saja\").", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Badges(entry)
            Spacer(Modifier.height(16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Action(Icons.Outlined.OpenInNew, "Buka di aplikasi") {
                    val ok = ActiveNotifications.open(context, entry.key) || appInfo?.launchIntent(entry.pkg)?.let { runCatching { context.startActivity(it) }.isSuccess } == true
                    if (ok) onDismiss()
                }
                Action(Icons.Outlined.AddTask, "Jadikan tugas") { haptics.success(); actions.makeTask(entry); onDismiss() }
                Action(Icons.Outlined.StickyNote2, "Jadikan catatan") { haptics.success(); actions.makeNote(entry); onDismiss() }
                if (entry.money != null && entry.draft == DraftState.Pending) Action(Icons.Outlined.Payments, "Catat transaksi") { onOpenDraft() }
                if (!entry.text.isNullOrBlank() || !entry.title.isNullOrBlank()) {
                    Action(Icons.Outlined.ContentCopy, "Salin teks") {
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText("Notifikasi", listOfNotNull(entry.title, entry.text).joinToString("\n")))
                        haptics.tap()
                    }
                }
                Action(Icons.Outlined.VisibilityOff, "Sembunyikan") { actions.hide(entry); onDismiss() }
                Action(Icons.Outlined.Block, "Kecualikan ${entry.appLabel}") { confirmExclude = true }
            }
        }
    }
    if (confirmExclude) {
        ConfirmDeleteDialog(
            title = "Kecualikan ${entry.appLabel}?",
            message = "Notifikasi dari ${entry.appLabel} tidak akan dicatat lagi, dan riwayatnya di Taskify dihapus. Bisa diaktifkan lagi di Pengaturan Notifikasi.",
            confirmLabel = "Kecualikan",
            onConfirm = { confirmExclude = false; actions.setExcluded(entry.pkg, true); onDismiss() },
            onDismiss = { confirmExclude = false },
        )
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}
