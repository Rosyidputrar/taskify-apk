package com.rosyidputrar.taskify.feature.sandi

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy

/** Seksi "Impor" di Pengaturan brankas. */
@Composable
fun ImportSection(onImport: () -> Unit, onImportAuth: () -> Unit = {}) {
    Text("Impor", style = MaterialTheme.typography.labelLarge)
    Text(
        "Masukkan sandi dan kode 2FA dari aplikasi lain. Item yang sudah ada tidak ditimpa.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.FileUpload, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Impor dari CSV", style = MaterialTheme.typography.labelLarge)
            Text("Pengelola Sandi Google · Chrome · Bitwarden", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    OutlinedButton(onClick = onImportAuth, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.QrCodeScanner, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("2FA dari Google Authenticator", style = MaterialTheme.typography.labelLarge)
            Text("Pindai QR \"Transfer akun\"", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Baris ringkasan impor (dipisah supaya mudah diuji). */
fun importLines(p: ImportPreview): List<String> = buildList {
    add("Dari: ${p.format.label}")
    add(
        if (p.add > 0) "${p.add} sandi baru akan ditambahkan" + (if (p.withTotp > 0) ", ${p.withTotp} di antaranya dengan kunci 2FA." else ".")
        else "Tidak ada sandi baru untuk ditambahkan.",
    )
    if (p.identical > 0) add("${p.identical} sudah ada di brankas (sama persis), dilewati.")
    if (p.differentPassword > 0) add("${p.differentPassword} akun sudah ada dengan sandi berbeda, ditambahkan sebagai item baru. Item lama tidak diubah.")
    if (p.duplicateInFile > 0) add("${p.duplicateInFile} baris kembar di berkas, dilewati.")
    if (p.noSecret > 0) add("${p.noSecret} baris tanpa sandi, dilewati.")
    if (p.malformed > 0) add("${p.malformed} baris tidak lengkap, dilewati.")
    if (p.badTotp > 0) add("${p.badTotp} kunci 2FA tidak valid. Sandinya tetap diimpor tanpa 2FA.")
}

/** Ringkasan sebelum impor. Tanpa kolom teks, jadi tetap bisa diuji sebagai dialog. */
@Composable
fun ImportDialog(preview: ImportPreview, state: SandiUiState, actions: SandiActions) {
    val busy = state.busy != null
    AlertDialog(
        onDismissRequest = { if (!busy) actions.cancelImport() },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text("Impor sandi") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                importLines(preview).forEachIndexed { i, line ->
                    Text(line, style = if (i == 1) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodySmall,
                        color = if (i == 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (preview.sample.isNotEmpty()) {
                    Text("Contoh: " + preview.sample.joinToString(", ") + if (preview.add > preview.sample.size) ", …" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BusyLine(state.busy, state.error)
            }
        },
        confirmButton = {
            TextButton(onClick = actions::confirmImport, enabled = preview.add > 0 && !busy) { Text(if (preview.add > 0) "Impor ${preview.add}" else "Impor") }
        },
        dismissButton = { TextButton(onClick = actions::cancelImport, enabled = !busy) { Text("Batal") } },
    )
}

/** Hasil impor + tawaran menghapus berkas CSV (isinya sandi tanpa enkripsi). */
@Composable
fun ImportDoneDialog(done: ImportDone, actions: SandiActions) {
    AlertDialog(
        onDismissRequest = actions::dismissImportDone,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text("${done.added} sandi diimpor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (done.tooLarge.isNotEmpty()) {
                    Text(
                        "${done.tooLarge.size} item terlalu besar untuk disimpan (catatan sangat panjang) dan dilewati: " + done.tooLarge.take(5).joinToString(", ") +
                            (if (done.tooLarge.size > 5) ", …" else "") + ". Tambahkan manual dengan catatan yang lebih pendek.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.WarningAmber, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Berkas CSV masih berisi semua sandi tanpa enkripsi. Hapus sekarang supaya tidak tertinggal di perangkat.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = actions::deleteImportSource) { Text("Hapus berkas CSV") } },
        dismissButton = { TextButton(onClick = actions::dismissImportDone) { Text("Nanti") } },
    )
}

/** Pemilih berkas impor (dibuka sekali) + dialog ringkasan & hasil. Bagian dari [ExportHost]. */
@Composable
internal fun ImportHost(state: SandiUiState, actions: SandiActions) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { actions.importPicked(it?.toString()) }
    val picking = state.importPicking
    LaunchedEffect(picking) {
        if (picking == null || picking.launched) return@LaunchedEffect
        actions.importLaunched()
        try {
            // Penyedia memberi tipe CSV berbeda-beda (text/csv, text/comma-separated-values, octet-stream).
            picker.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream"))
        } catch (_: ActivityNotFoundException) {
            actions.importPicked(null)
        }
    }
    state.importPreview?.let { ImportDialog(it, state, actions) }
    state.importDone?.let { ImportDoneDialog(it, actions) }
    AuthImportHost(state, actions)
}
