package com.rosyidputrar.taskify.ui.settings

import android.content.ClipData
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rosyidputrar.taskify.core.domain.auth.TRUST_DAYS
import com.rosyidputrar.taskify.core.domain.lock.LockPolicy
import com.rosyidputrar.taskify.core.domain.sync.explainErrorCode
import com.rosyidputrar.taskify.feature.auth.PinSetupContent
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

@Composable
fun AccountSection(viewModel: SettingsViewModel) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    var name by rememberSaveable(account.displayName) { mutableStateOf(account.displayName.orEmpty()) }
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    val storage by viewModel.storage.collectAsStateWithLifecycle()

    SettingsSection("Akun") {
        Text(account.email ?: "—", style = MaterialTheme.typography.titleMedium)
        Text("Akun Taskify", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Nama tampilan") },
            singleLine = true,
            supportingText = account.nameSaveMessage?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { viewModel.saveDisplayName(name) }, enabled = name != account.displayName.orEmpty()) { Text("Simpan nama") }
        }
        SwitchRow(
            title = "Tampilkan namaku di layar masuk",
            subtitle = "Matikan bila HP ini dipakai bersama.",
            checked = account.loginNameEnabled,
            onChange = viewModel::setLoginNameEnabled,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                viewModel.refreshStorage()
                confirmLogout = true
            },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Keluar") }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("Keluar dari Taskify?") },
            text = {
                Text(
                    buildString {
                        append("Semua data di HP ini dihapus. Data yang sudah tersinkron tetap aman di server.")
                        if (storage.pendingUploads > 0) {
                            append("\n\n⚠ Masih ada ${storage.pendingUploads} perubahan yang BELUM terunggah dan akan hilang. Sambungkan internet dulu bila ingin menyimpannya.")
                        }
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    viewModel.logout()
                }) { Text("Keluar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Batal") } },
        )
    }
}

@Composable
fun SecuritySection(viewModel: SettingsViewModel) {
    val lock by viewModel.lockState.collectAsStateWithLifecycle()
    var setupOpen by rememberSaveable { mutableStateOf(false) }
    var forgotten by rememberSaveable { mutableStateOf(false) }
    val config = lock.config

    SettingsSection("Keamanan") {
        SwitchRow(
            title = "Kunci aplikasi dengan PIN",
            subtitle = "Kunci tampilan di perangkat ini (bukan enkripsi data).",
            checked = config != null,
            onChange = { on -> if (on) setupOpen = true else viewModel.disablePin() },
        )
        if (config != null) {
            Spacer(Modifier.height(8.dp))
            Text("Kunci otomatis setelah di latar", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LockPolicy.AUTO_LOCK_OPTIONS.forEach { minutes ->
                    FilterChip(
                        selected = minutes == config.autoLockMinutes,
                        onClick = { viewModel.setAutoLock(minutes) },
                        label = { Text(if (minutes == 0) "Segera" else "$minutes mnt") },
                    )
                }
            }
        }
        if (viewModel.hasAccount) {
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text("Verifikasi dua langkah", style = MaterialTheme.typography.titleMedium)
            Text(
                "Wajib untuk akun ini. Perangkat yang diingat tidak diminta kode selama $TRUST_DAYS hari.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                viewModel.forgetDevice()
                forgotten = true
            }, enabled = !forgotten) { Text(if (forgotten) "Perangkat dilupakan" else "Lupakan perangkat ini") }
        }
    }

    if (setupOpen) {
        Dialog(onDismissRequest = { setupOpen = false }) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
                PinSetupContent(
                    onDone = { pin, minutes ->
                        viewModel.enablePin(pin, minutes)
                        setupOpen = false
                    },
                    onCancel = { setupOpen = false },
                )
            }
        }
    }
}

@Composable
fun SyncSection(viewModel: SettingsViewModel) {
    val sync by viewModel.sync.collectAsStateWithLifecycle()
    val errors by viewModel.uploadErrors.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { viewModel.refreshStorage() }

    SettingsSection("Sinkronisasi & penyimpanan") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (sync.healthy) Icons.Outlined.CloudDone else Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = if (sync.healthy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(sync.label, style = MaterialTheme.typography.titleMedium)
                sync.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Antrean unggah: ${storage.pendingUploads} perubahan · Database lokal: ${Formatter.formatShortFileSize(context, storage.dbBytes)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (errors.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text("${errors.size} perubahan ditolak server", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            Text(
                "Disimpan di sini supaya tidak hilang diam-diam. Salin untuk dicek, lalu bersihkan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            errors.take(20).forEach { e ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text("${e.op} ${e.tableName}", style = MaterialTheme.typography.labelLarge)
                    Text(
                        listOfNotNull(explainErrorCode(e.errorCode), e.errorMessage).joinToString(" — "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val json = buildJsonArray {
                        errors.forEach { e ->
                            add(
                                buildJsonObject {
                                    put("op", JsonPrimitive(e.op))
                                    put("table", JsonPrimitive(e.tableName))
                                    put("row_id", JsonPrimitive(e.rowId))
                                    put("payload", JsonPrimitive(e.payload))
                                    put("error_code", JsonPrimitive(e.errorCode))
                                    put("error_message", JsonPrimitive(e.errorMessage))
                                    put("created_at", JsonPrimitive(e.createdAt))
                                },
                            )
                        }
                    }
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("upload_errors", json.toString()))) }
                }) { Text("Salin JSON") }
                Button(onClick = viewModel::clearUploadErrors) { Text("Bersihkan") }
            }
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
