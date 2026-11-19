package com.rosyidputrar.taskify.feature.sandi

import com.rosyidputrar.taskify.core.designsystem.component.FitText
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.designsystem.theme.SerifFamily
import com.rosyidputrar.taskify.core.domain.sandi.ExportKind
import com.rosyidputrar.taskify.core.domain.sandi.Generator
import com.rosyidputrar.taskify.core.domain.sandi.SANDI_AUTO_LOCK_OPTIONS
import com.rosyidputrar.taskify.core.domain.sandi.TOTP_LOG_RETENTION_DAYS
import com.rosyidputrar.taskify.core.domain.sandi.Totp
import com.rosyidputrar.taskify.core.domain.sandi.strengthFromBits
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.sandi.VaultStatus
import com.rosyidputrar.taskify.feature.sandi.component.CountdownRing
import com.rosyidputrar.taskify.feature.sandi.component.SecretField
import com.rosyidputrar.taskify.feature.sandi.component.StrengthMeter
import kotlin.math.roundToInt

// ---------------------------------------------------------------- generator

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GeneratorPanel(state: SandiUiState, actions: SandiActions) {
    val g = state.generator
    val bits = if (g.mode == Generator.Mode.Password) Generator.passwordEntropy(g.password) else Generator.passphraseEntropy(g.passphrase)
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Generator.Mode.entries.forEachIndexed { i, m ->
                SegmentedButton(selected = g.mode == m, onClick = { actions.setGenerator(g.copy(mode = m)) }, shape = SegmentedButtonDefaults.itemShape(i, 2),
                    label = { FitText(if (m == Generator.Mode.Password) "Sandi acak" else "Frasa kata") })
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(state.generated, style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace))
                StrengthMeter(strengthFromBits(bits))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = actions::regenerate, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Acak lagi") }
                    Button(onClick = { actions.copy("Sandi", state.generated) }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text("Salin") }
                }
            }
        }
        if (g.mode == Generator.Mode.Password) {
            val o = g.password
            Text("Panjang ${o.length}", style = MaterialTheme.typography.labelLarge)
            Slider(o.length.toFloat(), { actions.setGenerator(g.copy(password = o.copy(length = it.roundToInt()))) },
                valueRange = Generator.LENGTH_MIN.toFloat()..64f)
            ToggleRow("Huruf kecil (a–z)", o.lower) { actions.setGenerator(g.copy(password = o.copy(lower = it))) }
            ToggleRow("Huruf besar (A–Z)", o.upper) { actions.setGenerator(g.copy(password = o.copy(upper = it))) }
            ToggleRow("Angka (0–9)", o.digits) { actions.setGenerator(g.copy(password = o.copy(digits = it))) }
            ToggleRow("Simbol (!@#…)", o.symbols) { actions.setGenerator(g.copy(password = o.copy(symbols = it))) }
            ToggleRow("Hindari yang mirip (I l 1 O 0 o)", o.avoidAmbiguous) { actions.setGenerator(g.copy(password = o.copy(avoidAmbiguous = it))) }
        } else {
            val o = g.passphrase
            Text("${o.words} kata", style = MaterialTheme.typography.labelLarge)
            Slider(o.words.toFloat(), { actions.setGenerator(g.copy(passphrase = o.copy(words = it.roundToInt()))) },
                valueRange = Generator.WORDS_MIN.toFloat()..Generator.WORDS_MAX.toFloat(), steps = Generator.WORDS_MAX - Generator.WORDS_MIN - 1)
            Text("Pemisah", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Generator.SEPARATORS.forEach { sep ->
                    FilterChip(selected = o.separator == sep, onClick = { actions.setGenerator(g.copy(passphrase = o.copy(separator = sep))) },
                        label = { Text(when (sep) { " " -> "spasi"; "" -> "tanpa"; else -> sep }, fontFamily = FontFamily.Monospace) })
                }
            }
            ToggleRow("Huruf besar di awal kata", o.capitalize) { actions.setGenerator(g.copy(passphrase = o.copy(capitalize = it))) }
            ToggleRow("Sisipkan satu angka", o.includeNumber) { actions.setGenerator(g.copy(passphrase = o.copy(includeNumber = it))) }
            Text("Daftar kata: EFF Large Wordlist (7.776 kata).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked, onCheckedChange = null)
    }
}

// ---------------------------------------------------------------- cek cepat TOTP

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickCheckSheet(state: SandiUiState, actions: SandiActions, now: () -> Long, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    val problem = text.takeIf { it.isNotBlank() }?.let { Totp.problem(it) }
    val parsed = if (text.isNotBlank() && problem == null) Totp.parseInput(text) else null
    val w = parsed?.let { Totp.window(it.config, now()) }
    val unlocked = state.status is VaultStatus.Unlocked
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Cek kunci 2FA", style = MaterialTheme.typography.titleLarge.copy(fontFamily = SerifFamily))
            Text("Tempel kunci dari situs untuk melihat kodenya sebelum disimpan. Kunci tidak disimpan kecuali kamu memilih simpan.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(text, { text = it; Totp.parseInput(it)?.name?.takeIf { n -> n.isNotBlank() && title.isBlank() }?.let { n -> title = n } },
                label = { Text("Kunci base32 atau otpauth://") }, isError = problem != null, supportingText = problem?.let { { Text(it) } },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace), keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth())
            if (w != null) {
                val secs = ((w.remainingMs + 999) / 1000).toInt()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(spacedCode(w.code), style = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.weight(1f))
                    CountdownRing(w.remainingMs / (w.period * 1000f), secs, size = 44.dp)
                    IconButton(onClick = { actions.copy("Kode", w.code) }) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Salin kode") }
                }
                Text("${w.code.length} digit · ${w.period} dtk · ${parsed.config.algorithm.name}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (unlocked) {
                    HorizontalDivider()
                    OutlinedTextField(title, { title = it }, label = { Text("Nama akun") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { actions.saveQuickTotp(title, parsed.config, parsed.username, onDismiss) }, enabled = title.isNotBlank() && state.busy == null,
                        modifier = Modifier.fillMaxWidth()) { Text("Simpan ke brankas") }
                } else {
                    Text("Buka brankas untuk menyimpan kunci ini.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- riwayat TOTP

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistorySheet(state: SandiUiState, actions: SandiActions, onDismiss: () -> Unit) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Riwayat kode", style = MaterialTheme.typography.titleLarge.copy(fontFamily = SerifFamily))
                    Text("Kode yang disalin, terenkripsi. Dihapus otomatis setelah $TOTP_LOG_RETENTION_DAYS hari.",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.log.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Hapus semua") }
            }
            if (state.log.isEmpty()) Text("Belum ada kode yang disalin.", Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.fillMaxWidth()) {
                items(state.log, key = { it.id }) { row ->
                    val at = Dates.parseTimestampMillis(row.entry.usedAt.ifBlank { row.createdAt })?.let { Dates.isoToLocal(Dates.millisToIso(it)) }
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(row.entry.name.ifBlank { "(tanpa nama)" }, style = MaterialTheme.typography.bodyLarge)
                            Text(listOfNotNull(at?.let { "${Dates.format(it.first, "d MMM")} ${it.second}" }, spacedCode(row.entry.code)).joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { actions.deleteLog(row.id) }) { Icon(Icons.Outlined.Delete, contentDescription = "Hapus entri") }
                    }
                }
            }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Hapus semua riwayat?") },
        text = { Text("Riwayat kode di semua perangkat akan dihapus.") },
        confirmButton = { TextButton(onClick = { actions.clearLog(); confirmClear = false }) { Text("Hapus") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Batal") } },
    )
}

// ---------------------------------------------------------------- pengaturan

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SettingsSheet(state: SandiUiState, actions: SandiActions, onDismiss: () -> Unit) {
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        SettingsBody(state, actions, onChangePassword = { dialog = "password" }, onRotate = { dialog = "recovery" },
            onExport = { actions.clearError(); dialog = "export:" + it.name }, onImport = { actions.clearError(); actions.startImport() },
            onImportAuth = { actions.clearError(); actions.startAuthImport(); onDismiss() })
    }
    when (val d = dialog) {
        "password" -> ChangePasswordDialog(state, actions, onDismiss = { dialog = null; actions.clearError() })
        "recovery" -> RotateRecoveryDialog(state, actions, onDismiss = { dialog = null; actions.clearError() })
        null -> Unit
        else -> ExportKind.entries.firstOrNull { "export:" + it.name == d }?.let { kind ->
            ExportDialog(kind, state, actions, onDismiss = { dialog = null; actions.clearError() })
        }
    }
    // Dialog ditutup otomatis setelah rotasi berhasil (kunci pemulihan baru tampil) atau ekspor siap.
    if (state.recoveryToShow != null && dialog != null) dialog = null
    if ((state.pendingExport != null || state.migration != null) && dialog?.startsWith("export:") == true) dialog = null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsBody(state: SandiUiState, actions: SandiActions, onChangePassword: () -> Unit, onRotate: () -> Unit, onExport: (ExportKind) -> Unit = {}, onImport: () -> Unit = {}, onImportAuth: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Pengaturan brankas", style = MaterialTheme.typography.titleLarge.copy(fontFamily = SerifFamily))
        Text("Kunci otomatis setelah tidak dipakai", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SANDI_AUTO_LOCK_OPTIONS.forEach { m ->
                FilterChip(selected = state.autoLock == m, onClick = { actions.setAutoLock(m) }, label = { Text(if (m == 0) "Saat keluar" else "$m menit") })
            }
        }
        HorizontalDivider()
        Text("Perangkat ini", style = MaterialTheme.typography.labelLarge)
        if (state.trustDaysLeft != null) {
            Text("Diingat · terbuka tanpa master password ${state.trustDaysLeft} hari lagi.", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = actions::forgetDevice) { Text("Lupakan perangkat ini") }
        } else {
            Text("Tidak diingat. Centang \"Ingat perangkat ini\" saat membuka brankas untuk melewati master password selama 30 hari.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AutofillSetting()
        HorizontalDivider()
        Text("Kunci", style = MaterialTheme.typography.labelLarge)
        Text("Mengganti master password atau kunci pemulihan membuat kunci enkripsi baru dan mengenkripsi ulang seluruh isi brankas dalam satu transaksi. Perlu online dan tersinkron penuh.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalButton(onClick = onChangePassword, modifier = Modifier.fillMaxWidth()) { Text("Ganti master password") }
        OutlinedButton(onClick = onRotate, modifier = Modifier.fillMaxWidth()) { Text("Buat kunci pemulihan baru") }
        HorizontalDivider()
        ImportSection(onImport, onImportAuth)
        HorizontalDivider()
        ExportSection(onExport)
    }
}

@Composable
private fun ChangePasswordDialog(state: SandiUiState, actions: SandiActions, onDismiss: () -> Unit) {
    var old by rememberSaveable { mutableStateOf("") }
    var new by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    val (strength, problem) = rememberMasterCheck(actions, new)
    val ok = old.isNotEmpty() && new.isNotEmpty() && problem == null && strength != null && confirm == new && state.busy == null
    AlertDialog(
        onDismissRequest = { if (state.busy == null) onDismiss() },
        title = { Text("Ganti master password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SecretField(old, { old = it }, "Master password sekarang")
                SecretField(new, { new = it }, "Master password baru", error = problem?.takeIf { new.length >= 4 })
                StrengthMeter(strength)
                SecretField(confirm, { confirm = it }, "Ulangi yang baru", imeAction = ImeAction.Done,
                    error = if (confirm.isNotEmpty() && confirm != new) "Belum sama." else null)
                Text("Kunci pemulihan lama juga diganti — simpan yang baru setelah ini.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BusyLine(state.busy, state.error)
            }
        },
        confirmButton = { TextButton(onClick = { actions.changePassword(old, new) }, enabled = ok) { Text("Ganti") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = state.busy == null) { Text("Batal") } },
    )
}

@Composable
private fun RotateRecoveryDialog(state: SandiUiState, actions: SandiActions, onDismiss: () -> Unit) {
    var password by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (state.busy == null) onDismiss() },
        title = { Text("Kunci pemulihan baru") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Kunci pemulihan lama langsung tidak berlaku. Pakai ini bila kunci lama hilang atau mungkin diketahui orang lain.", style = MaterialTheme.typography.bodyMedium)
                SecretField(password, { password = it }, "Master password", imeAction = ImeAction.Done)
                BusyLine(state.busy, state.error)
            }
        },
        confirmButton = { TextButton(onClick = { actions.rotateRecovery(password) }, enabled = password.isNotEmpty() && state.busy == null) { Text("Buat baru") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = state.busy == null) { Text("Batal") } },
    )
}

/**
 * Isi otomatis: Sandi sebagai layanan Autofill Android. Status dibaca ulang setiap kembali dari
 * Setelan sistem. Disembunyikan bila perangkat tidak mendukung Autofill.
 */
@Composable
private fun AutofillSetting() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val manager = remember(context) { runCatching { context.getSystemService(android.view.autofill.AutofillManager::class.java) }.getOrNull() }
    if (manager == null || !runCatching { manager.isAutofillSupported }.getOrDefault(false)) return
    var enabled by remember { mutableStateOf(runCatching { manager.hasEnabledAutofillServices() }.getOrDefault(false)) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        enabled = runCatching { manager.hasEnabledAutofillServices() }.getOrDefault(false)
        onPauseOrDispose { }
    }
    HorizontalDivider()
    Text("Isi otomatis", style = MaterialTheme.typography.labelLarge)
    Text(
        if (enabled) "Aktif · Sandi menawarkan nama pengguna & sandi di aplikasi dan browser lain, serta menyimpan sandi baru yang kamu ketik."
        else "Isi nama pengguna & sandi di aplikasi dan browser lain langsung dari brankas — tetap perlu brankas terbuka atau sidik jari.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (!enabled) {
        FilledTonalButton(
            onClick = {
                val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE)
                    .setData(android.net.Uri.parse("package:" + context.packageName))
                runCatching { context.startActivity(intent) }
                    .onFailure { runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS)) } }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Jadikan Sandi layanan isi otomatis") }
    }
}
