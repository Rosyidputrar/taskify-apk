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
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.PhonelinkLock
import androidx.compose.material.icons.outlined.QrCode2
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.rosyidputrar.taskify.core.designsystem.component.QrCode
import com.rosyidputrar.taskify.core.domain.sandi.ExportKind
import com.rosyidputrar.taskify.core.domain.sandi.GoogleAuthIssue
import com.rosyidputrar.taskify.core.domain.sandi.GoogleAuthPlan
import com.rosyidputrar.taskify.core.domain.sandi.SandiExport
import com.rosyidputrar.taskify.feature.sandi.component.SecretField

// ---------------------------------------------------------------- seksi di Pengaturan brankas

/** Seksi "Ekspor" di Pengaturan brankas. */
@Composable
fun ExportSection(onExport: (ExportKind) -> Unit) {
    Text("Ekspor", style = MaterialTheme.typography.labelLarge)
    Text(
        "Pindahkan isi brankas ke aplikasi lain. Hasilnya tidak terenkripsi, jadi perlu master password.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    ExportButton(Icons.Outlined.Key, "Sandi ke Pengelola Sandi Google", "Berkas CSV") { onExport(ExportKind.GoogleCsv) }
    ExportButton(Icons.Outlined.QrCode2, "2FA ke Google Authenticator", "Pindai QR") { onExport(ExportKind.GoogleAuthenticator) }
    ExportButton(Icons.Outlined.PhonelinkLock, "2FA ke aplikasi lain", "Daftar otpauth:// (.txt)") { onExport(ExportKind.OtpauthList) }
}

@Composable
private fun ExportButton(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, hint: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------- konfirmasi

/** Ringkasan yang ditampilkan sebelum ekspor (dihitung dari isi yang sedang terbuka, tanpa membuat berkasnya). */
internal data class ExportPreview(val count: Int, val notes: List<String>)

internal fun exportPreview(kind: ExportKind, state: SandiUiState): ExportPreview {
    val items = state.items.items.map { it.payload }
    val notes = mutableListOf<String>()
    val count = when (kind) {
        ExportKind.GoogleCsv -> {
            val s = SandiExport.csvSummary(items)
            if (s.skippedNoPassword > 0) notes += "${s.skippedNoPassword} item tanpa sandi (mis. hanya kunci 2FA) tidak ikut."
            if (s.withoutWebAddress > 0) notes += "${s.withoutWebAddress} sandi tanpa alamat situs. Google bisa menolaknya; pengelola sandi lain tetap menerima."
            notes += "Kunci 2FA tidak ikut CSV. Pindahkan lewat ekspor 2FA."
            s.rows
        }
        ExportKind.GoogleAuthenticator -> {
            val all = SandiExport.withTotp(items)
            val skipped = all.mapNotNull { p -> SandiExport.googleIssue(p.totp!!)?.let { p.title.ifBlank { p.username.ifBlank { "(tanpa judul)" } } to it } }
            if (skipped.isNotEmpty()) {
                notes += "${skipped.size} kunci tidak didukung Google Authenticator dan tidak ikut: " +
                    skipped.take(5).joinToString(", ") { (t, i) -> "$t (${i.label})" } + (if (skipped.size > 5) ", …" else "") +
                    ". Pindahkan lewat \"2FA ke aplikasi lain\"."
            }
            all.size - skipped.size
        }
        ExportKind.OtpauthList -> SandiExport.withTotp(items).size.also {
            notes += "Pengaturan asli (digit, periode, algoritme) ikut tersimpan. Bisa diimpor aplikasi authenticator yang menerima daftar otpauth://, misalnya Aegis."
        }
    }
    if (state.items.failed.isNotEmpty()) notes += "${state.items.failed.size} item tidak bisa dibuka dengan kunci ini dan tidak ikut."
    return ExportPreview(count, notes)
}

private fun ExportKind.title() = when (this) {
    ExportKind.GoogleCsv -> "Ekspor sandi"
    ExportKind.GoogleAuthenticator -> "Pindahkan ke Google Authenticator"
    ExportKind.OtpauthList -> "Ekspor kunci 2FA"
}

private fun ExportKind.countLine(n: Int) = when (this) {
    ExportKind.GoogleCsv -> "$n sandi akan diekspor ke berkas CSV."
    ExportKind.GoogleAuthenticator -> "$n akun 2FA akan ditampilkan sebagai QR untuk dipindai."
    ExportKind.OtpauthList -> "$n kunci 2FA akan diekspor ke berkas teks."
}

private fun ExportKind.warning() = when (this) {
    ExportKind.GoogleCsv -> "Berkas ini berisi semua sandi tanpa enkripsi. Simpan di perangkat saja (bukan folder yang tersinkron ke cloud), jangan dibuka di spreadsheet, dan hapus setelah diimpor."
    ExportKind.OtpauthList -> "Berkas ini berisi kunci 2FA tanpa enkripsi — siapa pun yang memegangnya bisa membuat kodemu. Hapus setelah diimpor."
    ExportKind.GoogleAuthenticator -> "Siapa pun yang melihat QR ini bisa membuat kode 2FA-mu. Tampilkan hanya untuk dipindai sendiri."
}

/** Ekspor bisa dimulai: ada isi, master password diisi, tidak sedang memproses. */
fun canStartExport(kind: ExportKind, state: SandiUiState, password: String): Boolean =
    password.isNotEmpty() && state.busy == null && exportPreview(kind, state).count > 0

/** Konfirmasi ekspor: ringkasan, peringatan, dan master password. Jendela aman (tanpa screenshot). */
@Composable
fun ExportDialog(kind: ExportKind, state: SandiUiState, actions: SandiActions, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") } // tidak di saved-state
    val busy = state.busy != null
    val ready = canStartExport(kind, state, password)
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(kind.title()) },
        text = {
            ExportConfirmBody(kind, state, password, onPassword = { password = it; actions.touch() }, onSubmit = { if (ready) actions.startExport(kind, password) })
        },
        confirmButton = {
            TextButton(onClick = { actions.startExport(kind, password) }, enabled = ready) {
                Text(if (kind == ExportKind.GoogleAuthenticator) "Tampilkan QR" else "Ekspor")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") } },
    )
}

/** Isi dialog ekspor (dipisah dari jendela dialog supaya bisa diuji & dipotret). */
@Composable
fun ExportConfirmBody(kind: ExportKind, state: SandiUiState, password: String, onPassword: (String) -> Unit, onSubmit: () -> Unit) {
    val preview = remember(kind, state.items) { exportPreview(kind, state) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (preview.count > 0) kind.countLine(preview.count) else "Tidak ada yang bisa diekspor.", style = MaterialTheme.typography.bodyMedium)
        preview.notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.WarningAmber, null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(kind.warning(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        if (preview.count > 0) {
            SecretField(password, onPassword, "Master password", imeAction = ImeAction.Done, onDone = onSubmit)
            BusyLine(state.busy, state.error)
        }
    }
}

// ---------------------------------------------------------------- QR Google Authenticator

/** QR "Transfer akun" satu per satu. Jendela aman: tidak bisa di-screenshot / direkam. */
@Composable
fun MigrationDialog(plan: GoogleAuthPlan, actions: SandiActions) {
    var page by rememberSaveable(plan.codes.size) { mutableIntStateOf(0) }
    val index = page.coerceIn(0, plan.codes.lastIndex)
    val last = index == plan.codes.lastIndex
    AlertDialog(
        onDismissRequest = actions::closeMigration,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(if (plan.codes.size > 1) "QR ${index + 1} dari ${plan.codes.size}" else "Pindai di Google Authenticator") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Di Google Authenticator: menu → Transfer akun → Impor akun, lalu pindai QR ini." +
                        if (plan.codes.size > 1) " Pindai semua QR berurutan." else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
                QrCode(plan.codes[index], size = 260.dp, description = "Kode QR transfer akun ${index + 1} dari ${plan.codes.size}")
                Text(
                    "${plan.accounts} akun total. Cocokkan satu kode di Google Authenticator dengan Taskify sebelum menghapus apa pun.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (plan.skipped.isNotEmpty()) {
                    Text(
                        "Tidak ikut (${plan.skipped.size}): " + plan.skipped.joinToString(", ") { (t, i: GoogleAuthIssue) -> "$t — ${i.label}" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { actions.touch(); if (last) actions.closeMigration() else page = index + 1 }) { Text(if (last) "Selesai" else "Berikutnya") }
        },
        dismissButton = {
            if (index > 0) TextButton(onClick = { actions.touch(); page = index - 1 }) { Text("Sebelumnya") }
            else TextButton(onClick = actions::closeMigration) { Text("Tutup") }
        },
    )
}

// ---------------------------------------------------------------- pemilih lokasi

/**
 * Membuka pemilih lokasi untuk [SandiUiState.pendingExport] sekali saja, lalu meneruskan hasilnya
 * ke [SandiActions.finishExport]. Ditaruh di tingkat layar supaya tetap hidup walau lembar
 * pengaturan ditutup.
 */
@Composable
fun ExportHost(state: SandiUiState, actions: SandiActions) {
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { actions.finishExport(it?.toString()) }
    val txt = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { actions.finishExport(it?.toString()) }
    val pending = state.pendingExport
    LaunchedEffect(pending) {
        if (pending == null || pending.launched) return@LaunchedEffect
        actions.exportLaunched()
        try {
            (if (pending.kind == ExportKind.GoogleCsv) csv else txt).launch(pending.fileName)
        } catch (_: ActivityNotFoundException) {
            actions.finishExport(null)
        }
    }
    state.migration?.let { MigrationDialog(it, actions) }
    ImportHost(state, actions)
}
