package com.rosyidputrar.taskify.feature.sandi

import com.rosyidputrar.taskify.core.designsystem.component.LoadingRow
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.rosyidputrar.taskify.core.designsystem.theme.SerifFamily
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.sandi.MasterPassword
import com.rosyidputrar.taskify.core.domain.sandi.Strength
import com.rosyidputrar.taskify.core.domain.sandi.recoveryFileText
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.feature.sandi.component.SecretField
import com.rosyidputrar.taskify.feature.sandi.component.StrengthMeter
import kotlinx.coroutines.delay

/** Kartu kertas di tengah layar untuk alur gerbang. */
@Composable
internal fun GateCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Sandi)
    Column(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(20.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(48.dp).background(accent.copy(alpha = 0.14f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = accent)
        }
        Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = SerifFamily))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
internal fun BusyLine(busy: String?, error: String?) {
    if (busy != null) {
        LoadingRow(busy, module = TaskifyModule.Sandi)
    }
    if (error != null) Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
}

/** Penilaian master password berjalan di latar (debounce) — zxcvbn + kamus Indonesia. */
@Composable
internal fun rememberMasterCheck(actions: SandiActions, password: String): Pair<Strength?, String?> {
    var result by remember { mutableStateOf<Pair<Strength?, String?>>(null to null) }
    LaunchedEffect(password) {
        if (password.isEmpty()) { result = null to null; return@LaunchedEffect }
        delay(250)
        result = runCatching { actions.evaluateMaster(password) }.getOrElse { null to MasterPassword.ENGINE_LOAD_ERROR }
    }
    return result
}

/** Langkah pembuatan brankas: pengantar → master password. Kunci pemulihan ditampilkan lewat [RecoveryKeyDialog]. */
@Composable
internal fun SetupPanel(state: SandiUiState, actions: SandiActions) {
    var step by rememberSaveable { mutableStateOf(0) }
    if (step == 0) {
        GateCard(Icons.Outlined.Shield, "Brankas sandi terenkripsi", "Semua sandi, kode 2FA, dan berkas konfigurasi dienkripsi di perangkat ini sebelum disinkronkan. Server hanya menyimpan sandi acak yang tidak bisa dibaca.") {
            Bullet("Satu master password membuka semuanya — hanya kamu yang tahu.")
            Bullet("Kunci pemulihan menyelamatkan brankas bila master password lupa. Simpan di tempat aman.")
            Bullet("Tanpa keduanya, isi brankas tidak bisa dipulihkan oleh siapa pun.")
            Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth()) { Text("Mulai buat brankas") }
        }
        return
    }
    // Jangan pakai rememberSaveable untuk master password (tidak boleh masuk Bundle savedInstanceState).
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val (strength, problem) = rememberMasterCheck(actions, password)
    val mismatch = confirm.isNotEmpty() && confirm != password
    val canCreate = password.isNotEmpty() && problem == null && strength != null && confirm == password && state.busy == null
    GateCard(Icons.Outlined.Key, "Pilih master password", "Minimal ${MasterPassword.MIN_LENGTH} karakter dan sulit ditebak. ${MasterPassword.ADVICE}") {
        SecretField(password, { password = it }, "Master password", error = problem?.takeIf { password.length >= 4 })
        StrengthMeter(strength)
        SecretField(confirm, { confirm = it }, "Ulangi master password", imeAction = ImeAction.Done,
            onDone = { if (canCreate) actions.createVault(password) }, error = if (mismatch) "Belum sama dengan yang pertama." else null)
        BusyLine(state.busy, state.error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { step = 0 }) { Text("Kembali") }
            Spacer(Modifier.weight(1f))
            Button(onClick = { actions.createVault(password) }, enabled = canCreate) { Text("Buat brankas") }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text("•", color = TaskifyTheme.modules.accent(TaskifyModule.Sandi), modifier = Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Layar buka brankas. Mencoba kunci perangkat tepercaya sekali saat tampil. */
@Composable
internal fun UnlockPanel(state: SandiUiState, actions: SandiActions, onQuickCheck: () -> Unit) {
    LaunchedEffect(Unit) { actions.tryTrusted() }
    var password by remember { mutableStateOf("") }
    var remember by rememberSaveable { mutableStateOf(false) }
    var recover by rememberSaveable { mutableStateOf(false) }
    if (recover) {
        RecoverPanel(state, actions, onCancel = { recover = false; actions.clearError() })
        // D-11: peringatan baris tertinggal pada alur pemulihan brankas terkunci.
        state.rotationWarning?.let { RotationWarningDialog(it, actions) }
        return
    }
    GateCard(Icons.Outlined.Lock, "Brankas terkunci", "Masukkan master password untuk membuka. Brankas terkunci lagi otomatis saat aplikasi ditinggal.") {
        SecretField(password, { password = it; if (state.error != null) actions.clearError() }, "Master password", imeAction = ImeAction.Go,
            onDone = { if (password.isNotEmpty()) actions.unlock(password, remember && state.trustAvailable) })
        Row(
            Modifier.fillMaxWidth().toggleable(remember, enabled = state.trustAvailable, role = Role.Checkbox, onValueChange = { remember = it }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(remember && state.trustAvailable, onCheckedChange = null, enabled = state.trustAvailable, modifier = Modifier.padding(12.dp))
            Column {
                Text("Ingat perangkat ini 30 hari", style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (state.trustAvailable) "Buka berikutnya dengan sidik jari atau kunci layar HP. Hapus kapan saja dari pengaturan."
                    else "Pasang kunci layar (PIN/pola/sandi) di HP dulu untuk memakai ini.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        BusyLine(state.busy, state.error)
        Button(onClick = { actions.unlock(password, remember && state.trustAvailable) }, enabled = password.isNotEmpty() && state.busy == null, modifier = Modifier.fillMaxWidth()) { Text("Buka brankas") }
        if (state.trustDaysLeft != null) {
            OutlinedButton(onClick = { actions.clearError(); actions.tryTrusted() }, enabled = state.busy == null, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Buka dengan sidik jari / kunci layar")
            }
        }
        Row {
            TextButton(onClick = { recover = true; actions.clearError() }) { Text("Lupa master password?") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onQuickCheck) { Text("Cek kode 2FA") }
        }
    }
}

@Composable
private fun RecoverPanel(state: SandiUiState, actions: SandiActions, onCancel: () -> Unit) {
    var key by remember { mutableStateOf("") }
    // Jangan pakai rememberSaveable untuk master password (tidak boleh masuk Bundle savedInstanceState).
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val (strength, problem) = rememberMasterCheck(actions, password)
    val ok = key.isNotBlank() && password.isNotEmpty() && problem == null && strength != null && confirm == password && state.busy == null
    GateCard(Icons.Outlined.Key, "Pulihkan dengan kunci pemulihan", "Ketik kunci pemulihan (26 karakter, tanda hubung boleh diabaikan), lalu pilih master password baru. Kunci pemulihan lama tidak berlaku lagi setelah ini.") {
        OutlinedTextField(key, { key = it.uppercase() }, label = { Text("Kunci pemulihan") }, singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.fillMaxWidth())
        SecretField(password, { password = it }, "Master password baru", error = problem?.takeIf { password.length >= 4 })
        StrengthMeter(strength)
        SecretField(confirm, { confirm = it }, "Ulangi master password baru", imeAction = ImeAction.Done,
            error = if (confirm.isNotEmpty() && confirm != password) "Belum sama dengan yang pertama." else null)
        BusyLine(state.busy, state.error)
        Row {
            TextButton(onClick = onCancel) { Text("Batal") }
            Spacer(Modifier.weight(1f))
            Button(onClick = { actions.recover(key, password) }, enabled = ok) { Text("Pulihkan") }
        }
    }
}

/**
 * Kunci pemulihan baru — ditampilkan SEKALI. Tidak bisa ditutup sebelum pengguna mencentang
 * "sudah saya simpan"; bisa disalin (papan klip dibersihkan otomatis) atau disimpan sebagai .txt.
 */
@Composable
internal fun RecoveryKeyDialog(recoveryKey: String, email: String?, actions: SandiActions) {
    val context = LocalContext.current
    var saved by rememberSaveable { mutableStateOf(false) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(recoveryFileText(recoveryKey, email, Dates.today()).toByteArray()) }
            saved = true
        }
    }
    Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)) {
        RecoveryKeyCard(recoveryKey, saved, onSaved = { saved = it }, onCopy = { actions.copy("Kunci pemulihan", recoveryKey) },
            onDownload = { save.launch("taskify-kunci-pemulihan.txt") }, onDone = { actions.acknowledgeRecovery() })
    }
}

@Composable
fun RecoveryKeyCard(recoveryKey: String, saved: Boolean, onSaved: (Boolean) -> Unit, onCopy: () -> Unit, onDownload: () -> Unit, onDone: () -> Unit) {
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Sandi)
    Column(
        Modifier.padding(16.dp).fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
            .verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Simpan kunci pemulihan", style = MaterialTheme.typography.headlineSmall.copy(fontFamily = SerifFamily))
        Text("Ini satu-satunya cara membuka brankas bila master password lupa. Kunci ini hanya ditampilkan sekarang.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer {
            Text(
                recoveryKey,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().border(2.dp, accent, RoundedCornerShape(14.dp)).padding(vertical = 18.dp, horizontal = 12.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Salin") }
            OutlinedButton(onClick = onDownload, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Simpan .txt") }
        }
        Text("Tulis di kertas atau simpan di pengelola sandi lain. Jangan simpan di catatan biasa.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(saved, onSaved)
            Text("Sudah saya simpan di tempat aman", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(2.dp))
        Button(onClick = onDone, enabled = saved, modifier = Modifier.fillMaxWidth()) { Text("Selesai") }
    }
}
