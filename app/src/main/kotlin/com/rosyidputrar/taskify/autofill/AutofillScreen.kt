package com.rosyidputrar.taskify.autofill

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.designsystem.component.LoadingBlock
import com.rosyidputrar.taskify.core.designsystem.component.rememberHaptics
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.sandi.AutofillMatch
import com.rosyidputrar.taskify.core.sandi.SandiItem

/** Isi jendela Isi Otomatis (tanpa Activity/ViewModel — bisa diuji). */
@Composable
fun AutofillContent(state: AutofillUiState, actions: AutofillActions) {
    var confirm by remember { mutableStateOf<SandiItem?>(null) }
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Sandi)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                Text("SANDI · ISI OTOMATIS", style = TaskifyTextStyles.indexLabel, color = accent)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (state.mode == AutofillMode.Save) "Simpan ke Sandi?" else "Isi untuk ${state.target.label}",
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state.phase) {
                    VaultPhase.Loading -> LoadingBlock(label = "Membuka brankas…")
                    VaultPhase.NoVault -> Message(
                        "Belum ada brankas Sandi di perangkat ini.",
                        "Buat atau sinkronkan brankas di Taskify → Sandi, lalu coba lagi.",
                    )
                    VaultPhase.Locked -> UnlockPanel(state, actions)
                    VaultPhase.Unlocked -> if (state.mode == AutofillMode.Save) SavePanel(state, actions) else PickPanel(state, actions, onPick = { item ->
                        if (SandiAutofillActivity.needsConfirm(item, state.target)) confirm = item else actions.pick(item)
                    })
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = actions::cancel) { Text(if (state.mode == AutofillMode.Save) "Jangan simpan" else "Batal") }
                if (state.mode == AutofillMode.Save && state.phase == VaultPhase.Unlocked) {
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = actions::save, enabled = !state.busy) { Text(if (state.saveTarget != null) "Perbarui" else "Simpan") }
                }
            }
        }
    }
    confirm?.let { item ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Alamat tidak cocok") },
            text = {
                Text(
                    "“${item.payload.title}” tersimpan untuk ${AutofillMatch.host(item.payload.url) ?: AutofillMatch.packageOf(item.payload.url) ?: "alamat lain"}, " +
                        "bukan ${state.target.label}. Pastikan ini bukan halaman palsu sebelum mengisi sandi.",
                )
            },
            confirmButton = { TextButton(onClick = { confirm = null; actions.pick(item) }) { Text("Tetap isi") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Batal") } },
        )
    }
}

@Composable
private fun Message(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun UnlockPanel(state: AutofillUiState, actions: AutofillActions) {
    var password by rememberSaveable { mutableStateOf("") }
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxWidth().padding(20.dp).widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text("Brankas terkunci. Buka untuk mengisi nama pengguna dan sandi.", style = MaterialTheme.typography.bodyMedium)
        }
        if (state.canUseDevice) {
            OutlinedButton(onClick = actions::unlockWithDevice, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Fingerprint, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Pakai sidik jari / kunci layar")
            }
        }
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Master password") },
            singleLine = true,
            isError = state.error != null,
            supportingText = state.error?.let { e -> { Text(e) } },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { actions.unlock(password) }),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { haptics.tap(); actions.unlock(password) },
            enabled = password.isNotEmpty() && !state.busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(if (state.busy) "Membuka…" else "Buka brankas") }
    }
}

@Composable
private fun PickPanel(state: AutofillUiState, actions: AutofillActions, onPick: (SandiItem) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
        item(key = "search") {
            OutlinedTextField(
                value = state.query,
                onValueChange = actions::query,
                placeholder = { Text("Cari judul, nama pengguna, alamat") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        if (state.query.isBlank()) {
            item(key = "matches-label") { Label(if (state.matches.isEmpty()) "Belum ada item untuk ${state.target.label}" else "COCOK UNTUK ${state.target.label.uppercase()}") }
            items(state.matches, key = { "m-" + it.id }) { ItemRow(it, onPick) }
        }
        if (state.others.isNotEmpty()) {
            item(key = "others-label") { Label(if (state.query.isBlank()) "SEMUA ITEM" else "HASIL PENCARIAN") }
            items(state.others, key = { "o-" + it.id }) { ItemRow(it, onPick) }
        } else if (state.query.isNotBlank()) {
            item(key = "none") { Message("Tidak ada yang cocok.", "Coba kata lain, atau tambahkan item di Taskify → Sandi.") }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = TaskifyTextStyles.indexLabel,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun ItemRow(item: SandiItem, onPick: (SandiItem) -> Unit) {
    val p = item.payload
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Sandi)
    Row(
        Modifier.fillMaxWidth().clickable(onClickLabel = "Isi dengan ${p.title}") { onPick(item) }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Text(p.title.trim().take(1).uppercase().ifEmpty { "•" }, style = MaterialTheme.typography.titleMedium, color = accent)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(p.title.ifBlank { "Tanpa judul" }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = listOfNotNull(p.username.ifBlank { null }, AutofillMatch.host(p.url) ?: AutofillMatch.packageOf(p.url)).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SavePanel(state: AutofillUiState, actions: AutofillActions) {
    val p = state.pending ?: return
    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            state.saveTarget?.let { "Sandi untuk “${it.payload.title}” akan diperbarui." }
                ?: "Item baru “${state.target.label}” akan dibuat di brankas.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Field("Alamat", AutofillMatch.urlFor(state.target))
        Field("Nama pengguna", p.username.ifBlank { "—" })
        Field("Sandi", "•".repeat(p.password.length.coerceIn(8, 16)))
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
