package com.rosyidputrar.taskify.feature.auth

import com.rosyidputrar.taskify.core.designsystem.component.launchSafe
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhonelinkLock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.auth.AuthError
import com.rosyidputrar.taskify.core.auth.AuthRepository
import com.rosyidputrar.taskify.core.auth.TotpEnrollment
import com.rosyidputrar.taskify.core.auth.TotpFactor
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.domain.auth.translateMfaError
import com.rosyidputrar.taskify.feature.auth.component.CodeInput
import com.rosyidputrar.taskify.feature.auth.component.ErrorText
import com.rosyidputrar.taskify.core.designsystem.component.QrCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

data class AuthenticatorsUi(
    val factors: List<TotpFactor> = emptyList(),
    val loading: Boolean = true,
    val busy: Boolean = false,
    val error: String? = null,
    val enrollment: TotpEnrollment? = null,
    val message: String? = null,
)

/** Kelola authenticator akun (padanan MfaSettings webapp). MFA wajib → yang terakhir tidak bisa dihapus. */
class AuthenticatorsViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(AuthenticatorsUi())
    val state: StateFlow<AuthenticatorsUi> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        launchSafe {
            run { _state.update { it.copy(factors = auth.verifiedTotpFactors(), loading = false) } }
        }
    }

    private suspend fun run(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: AuthError) {
            _state.update { it.copy(loading = false, busy = false, error = translateMfaError(e.failure)) }
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, busy = false, error = e.message ?: "Gagal. Periksa koneksi.") }
        }
    }

    fun startAdd(name: String) {
        _state.update { it.copy(busy = true, error = null) }
        launchSafe { run { _state.update { it.copy(enrollment = auth.startEnrollment(name.ifBlank { "Authenticator cadangan" }), busy = false) } } }
    }

    fun confirmAdd(code: String) {
        val e = _state.value.enrollment ?: return
        _state.update { it.copy(busy = true, error = null) }
        launchSafe {
            run {
                auth.verify(e.factorId, code, remember = null)
                _state.update { it.copy(enrollment = null, busy = false, message = "Authenticator ditambahkan") }
                load()
            }
        }
    }

    fun cancelAdd() = _state.update { it.copy(enrollment = null, error = null) }

    fun remove(factor: TotpFactor) {
        if (_state.value.factors.size <= 1) { _state.update { it.copy(error = "Authenticator terakhir tidak bisa dihapus — verifikasi dua langkah wajib.") }; return }
        _state.update { it.copy(busy = true, error = null) }
        launchSafe {
            run {
                auth.unenroll(factor.id)
                _state.update { it.copy(busy = false, message = "Authenticator dihapus") }
                load()
            }
        }
    }

    fun forgetDevice() {
        auth.forgetDevice()
        _state.update { it.copy(message = "Perangkat ini dilupakan. Kode diminta lagi saat aplikasi dibuka berikutnya.") }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}

/** Isi bagian "Verifikasi dua langkah" di Pengaturan. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatorSettings(onMessage: (String) -> Unit, vm: AuthenticatorsViewModel = koinViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.load() }
    LaunchedEffect(state.message) { state.message?.let { onMessage(it); vm.consumeMessage() } }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Authenticator terdaftar", style = MaterialTheme.typography.titleMedium)
        if (state.loading) Text("Memuat…", style = MaterialTheme.typography.bodySmall)
        state.factors.forEach { f ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.PhonelinkLock, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(f.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { confirm = f.id }, enabled = !state.busy && state.factors.size > 1) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Hapus authenticator ${f.name}")
                }
            }
        }
        if (state.factors.size == 1) Text("Tambahkan authenticator cadangan (mis. di HP lain) supaya tidak terkunci bila HP ini hilang.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!adding) ErrorText(state.error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { adding = true }, enabled = !state.busy) { Text("Tambah authenticator") }
            TextButton(onClick = vm::forgetDevice) { Text("Lupakan perangkat ini") }
        }
    }

    if (adding) ModalBottomSheet(onDismissRequest = { adding = false; vm.cancelAdd() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Tambah authenticator", style = MaterialTheme.typography.titleLarge)
            val e = state.enrollment
            if (e == null) {
                var name by rememberSaveable { mutableStateOf("Authenticator cadangan") }
                OutlinedTextField(name, { name = it }, label = { Text("Nama") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                ErrorText(state.error)
                Button(onClick = { vm.startAdd(name) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text(if (state.busy) "Menyiapkan…" else "Buat kode QR") }
            } else {
                EnrollBody(e, state, onVerify = vm::confirmAdd)
            }
        }
    }
    // Selesai menambah → tutup lembar.
    LaunchedEffect(state.factors.size) { if (adding && state.enrollment == null && !state.busy) adding = false }

    confirm?.let { id ->
        val f = state.factors.firstOrNull { it.id == id }
        if (f == null) confirm = null else AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Hapus authenticator \"${f.name}\"?") },
            text = { Text("Kode dari aplikasi itu tidak bisa dipakai lagi untuk masuk ke Taskify.") },
            confirmButton = { TextButton(onClick = { confirm = null; vm.remove(f) }) { Text("Hapus", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Batal") } },
        )
    }
}

@Composable
private fun EnrollBody(e: TotpEnrollment, state: AuthenticatorsUi, onVerify: (String) -> Unit) {
    val context = LocalContext.current
    var code by rememberSaveable { mutableStateOf("") }
    var noApp by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { QrCode(e.uri) }
    OutlinedButton(onClick = {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(e.uri))) } catch (_: ActivityNotFoundException) { noApp = true }
    }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp)); Text("  Buka di aplikasi authenticator")
    }
    if (noApp) ErrorText("Belum ada aplikasi authenticator di HP ini. Pindai QR dari perangkat lain.")
    Text("Kunci manual: " + e.secret.chunked(4).joinToString(" "), style = TaskifyTextStyles.code)
    CodeInput(value = code, onValueChange = { code = it }, onComplete = onVerify, enabled = !state.busy, autoFocus = false)
    ErrorText(state.error)
    Button(onClick = { onVerify(code) }, enabled = !state.busy && code.length == 6, modifier = Modifier.fillMaxWidth()) { Text(if (state.busy) "Memeriksa…" else "Aktifkan") }
}
