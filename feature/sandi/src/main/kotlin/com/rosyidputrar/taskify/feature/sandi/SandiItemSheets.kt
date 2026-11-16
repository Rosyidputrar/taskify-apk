package com.rosyidputrar.taskify.feature.sandi

import androidx.compose.runtime.remember
import com.rosyidputrar.taskify.core.domain.sandi.TotpConfig
import com.rosyidputrar.taskify.core.designsystem.component.QrCode
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.designsystem.theme.SerifFamily
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.sandi.Generator
import com.rosyidputrar.taskify.core.domain.sandi.ItemPayload
import com.rosyidputrar.taskify.core.domain.sandi.Totp
import com.rosyidputrar.taskify.core.domain.sandi.displayHost
import com.rosyidputrar.taskify.core.domain.sandi.safeUrl
import com.rosyidputrar.taskify.core.sandi.SandiItem
import com.rosyidputrar.taskify.core.store.cross.TrashRepository.Companion.RETENTION_DAYS as TRASH_RETENTION_DAYS
import com.rosyidputrar.taskify.feature.sandi.component.CountdownRing
import com.rosyidputrar.taskify.feature.sandi.component.SandiAvatar
import com.rosyidputrar.taskify.feature.sandi.component.SecretField
import com.rosyidputrar.taskify.feature.sandi.component.StrengthMeter

/** Kode 6/8 digit dipisah di tengah agar mudah dibaca: "123 456". */
internal fun spacedCode(code: String) = if (code.length >= 6) code.substring(0, code.length / 2) + " " + code.substring(code.length / 2) else code

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ItemSheet(item: SandiItem, now: () -> Long, actions: SandiActions, onEdit: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        ItemDetail(item, now(), actions, onEdit, onDelete = { actions.remove(item); onDismiss() })
    }
}

@Composable
fun ItemDetail(item: SandiItem, now: Long, actions: SandiActions, onEdit: () -> Unit, onDelete: () -> Unit) {
    val p = item.payload
    val uri = LocalUriHandler.current
    var reveal by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SandiAvatar(p.title, size = 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(p.title.ifBlank { "(tanpa judul)" }, style = MaterialTheme.typography.titleLarge.copy(fontFamily = SerifFamily), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (p.url.isNotBlank()) Text(displayHost(p.url), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { actions.save(p.copy(favorite = !p.favorite), item.id) {} }) {
                Icon(if (p.favorite) Icons.Rounded.Star else Icons.Outlined.StarOutline, contentDescription = if (p.favorite) "Hapus dari favorit" else "Favoritkan",
                    tint = if (p.favorite) TaskifyTheme.modules.accent(TaskifyModule.Sandi) else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (p.username.isNotBlank()) FieldRow("Nama pengguna", p.username) { actions.copy("Nama pengguna", p.username, false) }
        if (p.password.isNotEmpty()) {
            FieldRow("Sandi", if (reveal) p.password else "•".repeat(p.password.length.coerceAtMost(16)), mono = true,
                extra = {
                    IconButton(onClick = { reveal = !reveal; actions.touch() }) {
                        Icon(if (reveal) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = if (reveal) "Sembunyikan sandi" else "Tampilkan sandi")
                    }
                }) { actions.copy("Sandi", p.password) }
        }
        p.totp?.let { cfg ->
            val w = Totp.window(cfg, now)
            if (w != null) {
                val secs = ((w.remainingMs + 999) / 1000).toInt()
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Kode 2FA", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(spacedCode(w.code), style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Monospace))
                        Text("Berikutnya ${spacedCode(w.next)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    CountdownRing(w.remainingMs / (w.period * 1000f), secs)
                    IconButton(onClick = { actions.copyCode(item, w.code) }) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Salin kode") }
                }
            }
            TotpKeyRow(cfg, issuer = p.title.ifBlank { displayHost(p.url) }, account = p.username, actions = actions)
        }
        val safe = safeUrl(p.url)
        if (p.url.isNotBlank()) FieldRow("Alamat", p.url, extra = {
            if (safe != null) IconButton(onClick = { runCatching { uri.openUri(safe) } }) { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = "Buka situs") }
        }) { actions.copy("Alamat", p.url, false) }
        if (p.notes.isNotBlank()) {
            Text("Catatan", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(p.notes, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, null); Spacer(Modifier.width(6.dp)); Text("Hapus") }
            Spacer(Modifier.weight(1f))
            Button(onClick = onEdit) { Icon(Icons.Outlined.Edit, null); Spacer(Modifier.width(6.dp)); Text("Ubah") }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus \"${p.title.ifBlank { "Sandi" }}\"?") },
            text = { Text("Item dipindahkan ke Sampah dan bisa dipulihkan dari sana selama ${TRASH_RETENTION_DAYS} hari.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } },
        )
    }
}

/**
 * Kunci rahasia 2FA (yang menghasilkan kode tiap 30 dtk): tersembunyi sampai diminta, bisa
 * disalin (papan klip dibersihkan otomatis), atau ditampilkan sebagai QR `otpauth://` untuk
 * dipindahkan ke aplikasi authenticator lain. Tidak disimpan di saved-state (`remember` biasa).
 */
@Composable
internal fun TotpKeyRow(cfg: TotpConfig, issuer: String, account: String, actions: SandiActions) {
    var reveal by remember(cfg.secret) { mutableStateOf(false) }
    var qr by remember(cfg.secret) { mutableStateOf(false) }
    FieldRow(
        "Kunci 2FA",
        if (reveal) Totp.formatSecret(cfg.secret) else "•••• •••• •••• ••••",
        mono = true,
        extra = {
            IconButton(onClick = { reveal = !reveal; actions.touch() }) {
                Icon(if (reveal) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = if (reveal) "Sembunyikan kunci 2FA" else "Tampilkan kunci 2FA")
            }
            IconButton(onClick = { qr = true; actions.touch() }) { Icon(Icons.Outlined.QrCode2, contentDescription = "Tampilkan QR kunci 2FA") }
        },
    ) { actions.copy("Kunci 2FA", cfg.secret) }
    Text(
        "${cfg.digits} digit · tiap ${cfg.period} dtk · ${cfg.algorithm.name}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (qr) {
        AlertDialog(
            onDismissRequest = { qr = false },
            // Jendela dialog sendiri: tetap tidak bisa di-screenshot / direkam.
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            title = { Text("QR kunci 2FA") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    QrCode(Totp.otpauthUri(cfg, issuer, account), size = 220.dp, description = "Kode QR kunci 2FA ${issuer.ifBlank { account }}")
                    Text(
                        "Pindai dengan aplikasi authenticator lain untuk memindahkan akun ini. Siapa pun yang melihat QR ini bisa membuat kode 2FA-nya — jangan tunjukkan ke orang lain.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { qr = false }) { Text("Tutup") } },
        )
    }
}

@Composable
private fun FieldRow(label: String, value: String, mono: Boolean = false, extra: @Composable () -> Unit = {}, onCopy: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = if (mono) MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyLarge,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        extra()
        IconButton(onClick = onCopy) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Salin $label") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ItemEditorSheet(item: SandiItem?, state: SandiUiState, actions: SandiActions, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        ItemEditor(item, state, actions, onDismiss)
    }
}

@Composable
fun ItemEditor(item: SandiItem?, state: SandiUiState, actions: SandiActions, onDone: () -> Unit) {
    val p = item?.payload ?: ItemPayload()
    var title by rememberSaveable { mutableStateOf(p.title) }
    var url by rememberSaveable { mutableStateOf(p.url) }
    var username by rememberSaveable { mutableStateOf(p.username) }
    var password by rememberSaveable { mutableStateOf(p.password) }
    var totp by rememberSaveable { mutableStateOf(p.totp?.let { Totp.formatSecret(it.secret) } ?: "") }
    var notes by rememberSaveable { mutableStateOf(p.notes) }
    val totpProblem = totp.takeIf { it.isNotBlank() }?.let { Totp.problem(it) }
    val parsed = totp.takeIf { it.isNotBlank() && totpProblem == null }?.let { Totp.parseInput(it) }
    // Item lama: pertahankan digit/periode/algoritme bila yang ditempel hanya kunci.
    val totpConfig = parsed?.config?.let { c ->
        val old = p.totp
        if (old != null && !totp.trim().startsWith("otpauth", true) && c.secret == old.secret) old else c
    }
    val canSave = (title.isNotBlank() || url.isNotBlank()) && totpProblem == null && state.busy == null
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (item == null) "Sandi baru" else "Ubah sandi", style = MaterialTheme.typography.titleLarge.copy(fontFamily = SerifFamily))
        OutlinedTextField(title, { title = it }, label = { Text("Judul") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(url, { url = it }, label = { Text("Alamat situs") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(username, { username = it }, label = { Text("Nama pengguna / email") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            SecretField(password, { password = it }, "Sandi", modifier = Modifier.weight(1f))
            IconButton(onClick = {
                val g = state.generator
                password = if (g.mode == Generator.Mode.Password) Generator.password(g.password) else Generator.passphrase(g.passphrase)
            }) { Icon(Icons.Outlined.Password, contentDescription = "Buat sandi acak") }
        }
        if (password.isNotEmpty()) StrengthMeter(Generator.estimate(password))
        OutlinedTextField(
            totp, { text ->
                totp = text
                Totp.parseInput(text)?.let { r ->
                    if (title.isBlank() && r.name.isNotBlank()) title = r.name
                    if (username.isBlank() && r.username.isNotBlank()) username = r.username
                }
            },
            label = { Text("Kunci 2FA (opsional)") },
            placeholder = { Text("Kunci base32 atau otpauth://…") },
            isError = totpProblem != null,
            supportingText = { Text(totpProblem ?: totpConfig?.let { "${it.digits} digit · ${it.period} dtk · ${it.algorithm.name}" } ?: "Tempel dari halaman pengaturan 2FA situs.") },
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(notes, { notes = it }, label = { Text("Catatan") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        BusyLine(state.busy, state.error)
        Row {
            TextButton(onClick = onDone) { Text("Batal") }
            Spacer(Modifier.weight(1f))
            Button(enabled = canSave, onClick = {
                actions.save(
                    p.copy(title = title.trim(), url = url.trim(), username = username.trim(), password = password, notes = notes.trimEnd(), totp = totpConfig),
                    item?.id, onDone,
                )
            }) { Text("Simpan") }
        }
    }
}
