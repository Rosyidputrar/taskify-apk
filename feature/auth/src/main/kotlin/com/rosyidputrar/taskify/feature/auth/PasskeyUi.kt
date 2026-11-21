package com.rosyidputrar.taskify.feature.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rosyidputrar.taskify.core.auth.AuthError
import com.rosyidputrar.taskify.core.auth.Passkey
import com.rosyidputrar.taskify.core.auth.PasskeyAuth
import com.rosyidputrar.taskify.core.designsystem.component.launchSafe
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.domain.auth.AuthFailure
import com.rosyidputrar.taskify.core.domain.auth.AuthMessages
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.feature.auth.component.ErrorText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.koin.compose.viewmodel.koinViewModel
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.util.Base64

// ===================================================================== pesan

/**
 * Pesan galat server untuk passkey. Passkey yang belum diaktifkan di Supabase Auth biasanya
 * menjawab 404 / kode "*_disabled" — dijelaskan, plus detail mentahnya.
 */
fun passkeyErrorText(f: AuthFailure): String {
    if (f.network) return AuthMessages.OFFLINE_LOGIN
    if (f.status == 429 || f.code == "over_request_rate_limit") return AuthMessages.RATE_LIMITED
    val raw = listOfNotNull(f.status?.let { "HTTP $it" }, f.code, f.message).joinToString(" · ")
    val text = "${f.code.orEmpty()} ${f.message.orEmpty()}".lowercase()
    val friendly = when {
        f.status == 404 || "disabled" in text || "not enabled" in text ->
            "Passkey belum diaktifkan di server (Supabase Auth → Passkey)."
        "not found" in text || "credential" in text || f.status == 401 || f.status == 403 ->
            "Passkey ini tidak dikenali server — mungkin sudah dihapus dari akun. Masuk dengan email lalu tambahkan lagi."
        else -> "Passkey gagal."
    }
    return if (raw.isBlank()) friendly else "$friendly\n$raw"
}

// ===================================================================== perangkat

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Credential Manager untuk layar ini; null bila tidak tersedia (Android < 9 tidak mendukung
 * passkey, atau tidak ada Activity — mis. pratinjau).
 */
@Composable
fun rememberPasskeyDevice(): PasskeyDevice? {
    val activity = LocalContext.current.findActivity()
    return remember(activity) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) null else PasskeyClient(activity).asDevice()
    }
}

/**
 * Origin Android yang harus didaftarkan di server WebAuthn: `android:apk-key-hash:` + SHA-256
 * sertifikat penanda tangan APK (base64url tanpa padding). Beda antara build debug dan rilis.
 */
fun androidPasskeyOrigin(context: Context): String? = runCatching {
    val pm = context.packageManager
    val cert = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo ?: return null
        (if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory).firstOrNull()
    } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
    } ?: return null
    apkKeyHashOrigin(cert.toByteArray())
}.getOrNull()

/** Murni: origin dari byte sertifikat DER (dites di JVM). */
fun apkKeyHashOrigin(certDer: ByteArray): String =
    "android:apk-key-hash:" + Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(certDer))

// ===================================================================== ViewModel

data class PasskeysUi(
    val items: List<Passkey> = emptyList(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

/** Kelola passkey akun di Pengaturan → Keamanan. */
class PasskeysViewModel(private val api: PasskeyAuth) : ViewModel() {
    private val _state = MutableStateFlow(PasskeysUi())
    val state: StateFlow<PasskeysUi> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        launchSafe { attempt { _state.update { it.copy(items = api.passkeys(), loading = false) } } }
    }

    /**
     * Buat passkey baru di perangkat ini. [deviceName] (mis. "Pixel 8") dipakai sebagai nama bila
     * server tidak memberi nama yang jelas, supaya daftar passkey bisa dibedakan.
     */
    fun add(device: PasskeyDevice, deviceName: String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }
        launchSafe {
            attempt {
                val challenge = api.passkeyRegistrationChallenge()
                val credential = device.create(challenge.optionsJson)
                val created = api.passkeyRegister(challenge.challengeId, credential)
                if (created.name.isBlank() && deviceName.isNotBlank()) runCatching { api.renamePasskey(created.id, deviceName) }
                _state.update { it.copy(busy = false, message = "Passkey ditambahkan. Lain kali bisa masuk tanpa password.") }
                load()
            }
        }
    }

    fun rename(id: String, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) { _state.update { it.copy(error = "Nama passkey tidak boleh kosong.") }; return }
        _state.update { it.copy(busy = true, error = null) }
        launchSafe {
            attempt {
                api.renamePasskey(id, clean)
                _state.update { it.copy(busy = false, message = "Nama passkey diubah") }
                load()
            }
        }
    }

    fun delete(id: String) {
        _state.update { it.copy(busy = true, error = null) }
        launchSafe {
            attempt {
                api.deletePasskey(id)
                _state.update { it.copy(busy = false, message = "Passkey dihapus dari akun") }
                load()
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    /** Jalankan aksi; galat → pesan di state. (Bukan `run`: di dalam launchSafe nama itu tertangkap stdlib `CoroutineScope.run` sehingga galat lolos.) */
    private suspend fun attempt(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: PasskeyProblem) {
            _state.update { it.copy(loading = false, busy = false, error = e.userMessage()) }
        } catch (e: AuthError) {
            _state.update { it.copy(loading = false, busy = false, error = passkeyErrorText(e.failure)) }
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, busy = false, error = "Passkey gagal.\n${e::class.java.simpleName}: ${e.message.orEmpty()}".trimEnd(' ', ':')) }
        }
    }
}

// ===================================================================== UI

private fun dateLabel(ms: Long): String =
    Dates.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate().toString(), "d MMM yyyy")

/** Keterangan baris: kapan ditambahkan & terakhir dipakai. */
fun passkeyCaption(p: Passkey): String = listOfNotNull(
    p.createdAtMs?.let { "Ditambahkan ${dateLabel(it)}" },
    p.lastUsedAtMs?.let { "terakhir dipakai ${dateLabel(it)}" },
).joinToString(" · ").ifEmpty { "Passkey" }

/** Isi bagian "Passkey" di Pengaturan. */
@Composable
fun PasskeySettings(onMessage: (String) -> Unit, vm: PasskeysViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val device = rememberPasskeyDevice()
    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(state.message) { state.message?.let { onMessage(it); vm.consumeMessage() } }
    PasskeySettingsContent(
        state = state,
        canAdd = device != null,
        origin = androidPasskeyOrigin(LocalContext.current),
        onAdd = { device?.let { vm.add(it, Build.MODEL.orEmpty()) } },
        onRename = vm::rename,
        onDelete = vm::delete,
    )
}

/** Konten tanpa ViewModel (bisa dirender di tes). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasskeySettingsContent(
    state: PasskeysUi,
    canAdd: Boolean,
    origin: String?,
    onAdd: () -> Unit,
    onRename: (id: String, name: String) -> Unit,
    onDelete: (id: String) -> Unit,
) {
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    var showOrigin by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Masuk tanpa password memakai sidik jari, wajah, atau kunci layar. Passkey disimpan di pengelola sandi (mis. Google Password Manager) dan ikut tersedia di HP lain dengan akun Google yang sama.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.loading && state.items.isEmpty()) Text("Memuat…", style = MaterialTheme.typography.bodySmall)
        if (!state.loading && state.items.isEmpty() && state.error == null) {
            Text("Belum ada passkey.", style = MaterialTheme.typography.bodyMedium)
        }
        state.items.forEach { p ->
            var menu by remember(p.id) { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Key, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name.ifBlank { "Passkey" }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(passkeyCaption(p), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.foundation.layout.Box {
                    IconButton(onClick = { menu = true }, enabled = !state.busy) { Icon(Icons.Outlined.MoreVert, contentDescription = "Pilihan passkey ${p.name}") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Ganti nama") },
                            leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) },
                            onClick = { menu = false; renaming = p.id },
                        )
                        DropdownMenuItem(
                            text = { Text("Hapus", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; deleting = p.id },
                        )
                    }
                }
            }
        }
        ErrorText(state.error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onAdd, enabled = canAdd && !state.busy) {
                Icon(Icons.Outlined.Key, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (state.busy) "Menyiapkan…" else "Tambah passkey")
            }
            if (origin != null) TextButton(onClick = { showOrigin = !showOrigin }) { Text(if (showOrigin) "Tutup info server" else "Info server") }
        }
        if (!canAdd) {
            Text("HP ini belum mendukung passkey (butuh Android 9 atau lebih baru).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (showOrigin && origin != null) {
            Text(
                "Daftarkan origin ini di Supabase Auth → WebAuthn (allowed origins) agar passkey dari aplikasi ini diterima:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(origin, style = TaskifyTextStyles.code, modifier = Modifier.weight(1f))
                IconButton(onClick = { clipboard.setText(AnnotatedString(origin)) }) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Salin origin") }
            }
        }
    }

    renaming?.let { id ->
        val p = state.items.firstOrNull { it.id == id }
        if (p == null) {
            renaming = null
        } else {
            ModalBottomSheet(onDismissRequest = { renaming = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                var name by rememberSaveable(id) { mutableStateOf(p.name) }
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Ganti nama passkey", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(name, { name = it.take(60) }, label = { Text("Nama") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { onRename(id, name); renaming = null }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Simpan") }
                }
            }
        }
    }

    deleting?.let { id ->
        val p = state.items.firstOrNull { it.id == id }
        if (p == null) deleting = null else AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Hapus passkey \"${p.name.ifBlank { "Passkey" }}\"?") },
            text = { Text("Passkey ini tidak bisa dipakai lagi untuk masuk ke Taskify. Hapus juga dari pengelola sandi di HP-mu bila tidak dipakai.") },
            confirmButton = { TextButton(onClick = { deleting = null; onDelete(id) }) { Text("Hapus", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Batal") } },
        )
    }
}
