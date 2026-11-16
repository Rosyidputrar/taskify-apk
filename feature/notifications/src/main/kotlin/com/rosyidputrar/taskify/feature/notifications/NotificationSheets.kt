package com.rosyidputrar.taskify.feature.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.common.Rupiah
import com.rosyidputrar.taskify.core.designsystem.component.ConfirmDeleteDialog
import com.rosyidputrar.taskify.core.designsystem.component.IndexCard
import com.rosyidputrar.taskify.core.designsystem.component.rememberHaptics
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.designsystem.theme.namedColor
import com.rosyidputrar.taskify.core.device.DeviceAccess
import com.rosyidputrar.taskify.core.device.NotifSettings
import com.rosyidputrar.taskify.core.device.ui.AppIcon
import com.rosyidputrar.taskify.core.domain.device.formatClockTime
import com.rosyidputrar.taskify.core.domain.finance.TxKind
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.store.device.NotifEntry
import com.rosyidputrar.taskify.core.store.device.Parcel

// ============================================================ draf transaksi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DraftsSheet(state: NotificationsUiState, actions: NotificationsActions, onDismiss: () -> Unit) {
    val haptics = rememberHaptics()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(bottom = 20.dp)) {
            item(key = "head") {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text("DRAF TRANSAKSI", style = TaskifyTextStyles.indexLabel, color = namedColor("emerald"))
                    Text("Tinjau sebelum dicatat", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    Text(
                        "Dibaca dari notifikasi bank & dompet digital. Tidak ada yang tersimpan tanpa persetujuanmu.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.wallets.isEmpty()) {
                        Text("Belum ada dompet. Buat dompet dulu di Keuangan.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                    } else if (state.readyDrafts.size >= 2) {
                        Button(onClick = { haptics.success(); actions.saveAllDrafts() }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                            Text("Simpan semua yang dompetnya sudah dikenal (${state.readyDrafts.size})")
                        }
                    }
                }
            }
            if (state.drafts.isEmpty()) item(key = "none") {
                Text("Semua draf sudah ditinjau.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(20.dp))
            }
            items(state.drafts, key = { it.id }) { d -> DraftCard(d, state, actions) }
        }
    }
}

@Composable
private fun DraftCard(d: NotifEntry, state: NotificationsUiState, actions: NotificationsActions) {
    val money = d.money ?: return
    val haptics = rememberHaptics()
    var amount by rememberSaveable(d.id) { mutableStateOf(money.amount.toString()) }
    var kind by rememberSaveable(d.id) { mutableStateOf(money.kind) }
    var title by rememberSaveable(d.id) { mutableStateOf(NotificationsViewModel.defaultTitle(d)) }
    var wallet by rememberSaveable(d.id) { mutableStateOf(state.walletFor(d)) }
    var category by rememberSaveable(d.id) { mutableStateOf(state.guessCategory(d)) }
    val parsed = Rupiah.parse(amount)
    IndexCard(TaskifyModule.Finance, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(d.pkg, d.appLabel, size = 28.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                "${money.source} · ${Dates.relativeDayLabel(d.day, state.today)} ${formatClockTime(d.postedAt)}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
        }
        listOfNotNull(d.title, d.text).joinToString(" — ").takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(TxKind.Expense to "Keluar", TxKind.Income to "Masuk").forEachIndexed { i, (k, label) ->
                SegmentedButton(selected = kind == k, onClick = { kind = k; category = null }, shape = SegmentedButtonDefaults.itemShape(i, 2), label = { Text(label) })
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Nominal") },
            prefix = { Text("Rp ") },
            singleLine = true,
            isError = parsed == null || parsed <= 0,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Judul") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text("DOMPET", style = TaskifyTextStyles.indexLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // Row + horizontalScroll (bukan LazyRow): IndexCard mengukur tinggi intrinsik isinya.
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.wallets.forEach { w ->
                FilterChip(selected = wallet == w.id, onClick = { haptics.tick(); wallet = w.id }, label = { Text(w.name) })
            }
        }
        if (state.settings.wallets[d.pkg] == null && wallet != null) {
            Text("Notifikasi ${money.source} berikutnya otomatis ke dompet ini.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val pool = state.categories.filter { it.kind.value == kind.value }
        if (pool.isNotEmpty()) {
            Text("KATEGORI", style = TaskifyTextStyles.indexLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = category == null, onClick = { category = null }, label = { Text("Tanpa") })
                pool.forEach { c -> FilterChip(selected = category == c.id, onClick = { haptics.tick(); category = c.id }, label = { Text(c.name) }) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { actions.dismissDraft(d) }) { Text("Abaikan") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { haptics.success(); actions.saveDraft(d, DraftEdit(parsed!!, kind, title, wallet!!, category)) },
                enabled = wallet != null && parsed != null && parsed > 0,
            ) { Text("Simpan") }
        }
    }
}

// ============================================================ paket

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ParcelsSheet(parcels: List<Parcel>, today: String, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
            item {
                Text("Paket 7 hari terakhir", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Text("Status terakhir dari notifikasi marketplace & kurir.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
            }
            items(parcels, key = { it.pkg + it.ref }) { ParcelRow(it, today) }
        }
    }
}

// ============================================================ pengaturan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotifSettingsSheet(state: NotificationsUiState, actions: NotificationsActions, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var confirmClear by remember { mutableStateOf(false) }
    val s = state.settings
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Pengaturan Notifikasi", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() }) }
            item {
                Label("AKSES")
                Text(
                    when {
                        !state.access.granted -> "Belum diizinkan. Taskify belum mencatat apa pun."
                        state.access.connected -> "Aktif · pencatat tersambung."
                        else -> "Diizinkan, tetapi pencatat sedang terputus."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = { DeviceAccess.open(context, DeviceAccess.notificationSettings(context)) }) { Text("Buka Akses notifikasi") }
            }
            item { HorizontalDivider() }
            item {
                Label("YANG DISIMPAN")
                LevelChoice(s.level) { haptics.tick(); actions.setLevel(it) }
            }
            item {
                Label("MASA SIMPAN ISI")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(NotifSettings.RETENTION_CHOICES) { d ->
                        FilterChip(selected = s.retentionDays == d, onClick = { haptics.tick(); actions.setRetention(d) }, label = { Text("$d hari") })
                    }
                }
                Text("Jumlah per aplikasi tetap disimpan lebih lama untuk Kesehatan Digital & Review.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { HorizontalDivider() }
            item {
                ToggleRow("Abaikan notifikasi berjalan", "Musik, unduhan, navigasi, dan panggilan berlangsung tidak dicatat.", s.ignoreOngoing) { haptics.toggle(it); actions.setIgnoreOngoing(it) }
                ToggleRow("Beri tahu draf transaksi baru", "Notifikasi \"Transaksi terdeteksi\" saat ada draf dari bank/dompet.", s.draftAlerts) { haptics.toggle(it); actions.setDraftAlerts(it) }
            }
            if (s.wallets.isNotEmpty()) {
                item { HorizontalDivider() }
                item { Label("DOMPET OTOMATIS") }
                items(s.wallets.entries.toList(), key = { "w-" + it.key }) { (pkg, walletId) ->
                    val label = state.knownApps.firstOrNull { it.pkg == pkg }?.label ?: pkg
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(pkg, label, size = 28.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("$label → ${state.wallets.firstOrNull { it.id == walletId }?.name ?: "dompet terhapus"}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { actions.forgetWallet(pkg) }) { Text("Lupakan") }
                    }
                }
            }
            item { HorizontalDivider() }
            item {
                Label("APLIKASI")
                Text("Matikan untuk berhenti mencatat aplikasi itu (riwayatnya ikut dihapus).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val apps = (state.knownApps.map { it.pkg to it.label } + s.excluded.filter { e -> state.knownApps.none { it.pkg == e } }.map { it to it }).distinctBy { it.first }
            if (apps.isEmpty()) item { Text("Belum ada aplikasi yang tercatat.", style = MaterialTheme.typography.bodyMedium) }
            items(apps, key = { "a-" + it.first }) { (pkg, label) ->
                val on = pkg !in s.excluded
                Row(
                    Modifier.fillMaxWidth().toggleable(on, role = Role.Switch) { haptics.toggle(it); actions.setExcluded(pkg, !it) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(pkg, label, size = 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Switch(checked = on, onCheckedChange = null)
                }
            }
            item { HorizontalDivider() }
            item {
                TextButton(onClick = { confirmClear = true }) { Text("Hapus semua riwayat notifikasi", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (confirmClear) {
        ConfirmDeleteDialog(
            title = "Hapus semua riwayat?",
            message = "Semua notifikasi, hitungan, dan draf transaksi yang belum ditinjau dihapus dari HP ini. Tugas, catatan, dan transaksi yang sudah dibuat tidak terpengaruh.",
            onConfirm = { confirmClear = false; actions.clearAll() },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = TaskifyTextStyles.indexLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun ToggleRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}
