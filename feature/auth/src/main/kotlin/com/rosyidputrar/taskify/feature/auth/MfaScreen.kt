package com.rosyidputrar.taskify.feature.auth

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.auth.AuthError
import com.rosyidputrar.taskify.core.auth.AuthRepository
import com.rosyidputrar.taskify.core.auth.NetworkMonitor
import com.rosyidputrar.taskify.core.auth.TotpEnrollment
import com.rosyidputrar.taskify.core.auth.TotpFactor
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.domain.auth.AuthMessages
import com.rosyidputrar.taskify.core.domain.auth.GateStatus
import com.rosyidputrar.taskify.core.domain.auth.TRUST_DAYS
import com.rosyidputrar.taskify.core.domain.auth.isTotpCode
import com.rosyidputrar.taskify.core.domain.auth.translateMfaError
import com.rosyidputrar.taskify.feature.auth.component.AuthScaffold
import com.rosyidputrar.taskify.feature.auth.component.CodeInput
import com.rosyidputrar.taskify.feature.auth.component.ErrorText
import com.rosyidputrar.taskify.feature.auth.component.QrCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

data class MfaUiState(
    val factors: List<TotpFactor> = emptyList(),
    val selectedFactor: String? = null,
    val loadingFactors: Boolean = false,
    val enrollment: TotpEnrollment? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

class MfaViewModel(private val auth: AuthRepository, private val network: NetworkMonitor) : ViewModel() {
    private val _state = MutableStateFlow(MfaUiState())
    val state: StateFlow<MfaUiState> = _state.asStateFlow()
    val online: StateFlow<Boolean> = network.online

    fun loadFactors() {
        _state.update { it.copy(loadingFactors = true, error = null) }
        viewModelScope.launch {
            try {
                val factors = auth.verifiedTotpFactors()
                _state.update { it.copy(factors = factors, selectedFactor = factors.firstOrNull()?.id, loadingFactors = false) }
            } catch (e: AuthError) {
                _state.update { it.copy(loadingFactors = false, error = translateMfaError(e.failure)) }
            }
        }
    }

    fun selectFactor(id: String) = _state.update { it.copy(selectedFactor = id) }

    fun verify(code: String, remember: Boolean) {
        val factorId = _state.value.enrollment?.factorId ?: _state.value.selectedFactor ?: return
        if (!isTotpCode(code)) {
            _state.update { it.copy(error = "Masukkan 6 angka.") }
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                auth.verify(factorId, code, remember)
                _state.update { it.copy(busy = false) }
            } catch (e: AuthError) {
                _state.update { it.copy(busy = false, error = translateMfaError(e.failure)) }
            }
        }
    }

    fun startEnrollment(name: String) {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val enrollment = auth.startEnrollment(name)
                _state.update { it.copy(busy = false, enrollment = enrollment) }
            } catch (e: AuthError) {
                _state.update { it.copy(busy = false, error = translateMfaError(e.failure)) }
            }
        }
    }

    fun skipOffline() = auth.skipReverifyOffline()

    fun signOut() {
        viewModelScope.launch { runCatching { auth.signOut() } }
    }
}

private fun headings(status: GateStatus): Pair<String, String> = when (status) {
    GateStatus.Enroll -> "Aktifkan verifikasi dua langkah" to
        "Taskify menyimpan jurnal, keuangan, dan brankas sandi. Agar password yang bocor saja tidak cukup, setiap login wajib memakai kode dari aplikasi authenticator."
    GateStatus.Reverify -> "Konfirmasi perangkat ini" to
        "Perangkat ini tidak sedang diingat (lewat $TRUST_DAYS hari, atau kamu memilih untuk tidak mengingatnya). Masukkan kode 6 digit dari aplikasi authenticator."
    else -> "Verifikasi dua langkah" to "Masukkan kode 6 digit dari aplikasi authenticator di HP-mu."
}

/** Layar pengganti aplikasi selama sesi belum aal2: database & sync BELUM dibuka di sini. */
@Composable
fun MfaScreen(status: GateStatus, viewModel: MfaViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val online by viewModel.online.collectAsStateWithLifecycle()
    LaunchedEffect(status, online) {
        if (status != GateStatus.Enroll && online && state.factors.isEmpty() && !state.loadingFactors) viewModel.loadFactors()
    }
    MfaContent(
        status = status,
        state = state,
        online = online,
        actions = MfaActions(
            selectFactor = viewModel::selectFactor,
            verify = viewModel::verify,
            retry = viewModel::loadFactors,
            startEnrollment = viewModel::startEnrollment,
            skipOffline = viewModel::skipOffline,
            signOut = viewModel::signOut,
        ),
    )
}

class MfaActions(
    val selectFactor: (String) -> Unit = {},
    val verify: (code: String, remember: Boolean) -> Unit = { _, _ -> },
    val retry: () -> Unit = {},
    val startEnrollment: (String) -> Unit = {},
    val skipOffline: () -> Unit = {},
    val signOut: () -> Unit = {},
)

@Composable
fun MfaContent(status: GateStatus, state: MfaUiState, online: Boolean, actions: MfaActions) {
    val (title, subtitle) = headings(status)
    val enrolling = status == GateStatus.Enroll

    AuthScaffold(
        tab = "Keamanan",
        title = title,
        subtitle = subtitle,
        module = TaskifyModule.Sandi,
        footer = {
            TextButton(onClick = actions.signOut) {
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Keluar")
            }
        },
    ) {
        if (!online) {
            Text(AuthMessages.OFFLINE_MFA, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            if (status == GateStatus.Reverify) {
                OutlinedButton(onClick = actions.skipOffline, modifier = Modifier.fillMaxWidth()) {
                    Text("Lewati untuk sekarang (offline)")
                }
            }
        }
        when {
            enrolling && state.enrollment == null -> EnrollStart(state, onStart = actions.startEnrollment)
            enrolling -> EnrollConfirm(state.enrollment!!, state, onVerify = actions.verify)
            else -> Challenge(state, onSelect = actions.selectFactor, onVerify = actions.verify, onRetry = actions.retry)
        }
    }
}

@Composable
private fun RememberRow(remember: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = remember, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = remember, onCheckedChange = null)
        Text("  Ingat perangkat ini selama $TRUST_DAYS hari", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Challenge(
    state: MfaUiState,
    onSelect: (String) -> Unit,
    onVerify: (String, Boolean) -> Unit,
    onRetry: () -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("") }
    var remember by rememberSaveable { mutableStateOf(true) }
    if (state.loadingFactors) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
        return
    }
    if (state.factors.size > 1) {
        Text("Authenticator", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.factors.forEach { factor ->
                FilterChip(selected = factor.id == state.selectedFactor, onClick = { onSelect(factor.id) }, label = { Text(factor.name) })
            }
        }
    }
    CodeInput(value = code, onValueChange = { code = it }, onComplete = { onVerify(it, remember) }, enabled = !state.busy)
    RememberRow(remember) { remember = it }
    ErrorText(state.error)
    if (state.factors.isEmpty() && state.error != null) {
        OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Coba lagi") }
    }
    Button(
        onClick = { onVerify(code, remember) },
        enabled = !state.busy && state.selectedFactor != null && code.length == 6,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (state.busy) "Memeriksa…" else "Verifikasi") }
}

@Composable
private fun EnrollStart(state: MfaUiState, onStart: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("Taskify Android") }
    Text(
        "1. Pasang aplikasi authenticator (Google Authenticator, Aegis, 2FAS, dsb.).\n2. Buat kode QR, lalu pindai atau buka langsung di aplikasi itu.\n3. Masukkan kode 6 digit yang muncul.",
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama perangkat (opsional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    ErrorText(state.error)
    Button(onClick = { onStart(name) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
        Text(if (state.busy) "Menyiapkan…" else "Buat kode QR")
    }
}

@Composable
private fun EnrollConfirm(enrollment: TotpEnrollment, state: MfaUiState, onVerify: (String, Boolean) -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var code by rememberSaveable { mutableStateOf("") }
    var remember by rememberSaveable { mutableStateOf(true) }
    var copied by rememberSaveable { mutableStateOf(false) }
    var noApp by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        QrCode(enrollment.uri)
    }
    // Authenticator di HP yang sama tidak bisa memindai layarnya sendiri → buka langsung.
    OutlinedButton(
        onClick = {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(enrollment.uri)))
            } catch (_: ActivityNotFoundException) {
                noApp = true
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
        Text("  Buka di aplikasi authenticator")
    }
    if (noApp) {
        ErrorText("Belum ada aplikasi authenticator di HP ini. Pasang dulu, atau pindai QR dari perangkat lain.")
    }
    Text("Atau ketik kunci ini secara manual:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(enrollment.secret.chunked(4).joinToString(" "), style = TaskifyTextStyles.code, modifier = Modifier.weight(1f))
        TextButton(onClick = {
            scope.launch {
                val clip = ClipData.newPlainText("Kunci authenticator", enrollment.secret)
                // Tandai sensitif supaya tidak tampil di pratinjau papan klip (Android 13+).
                if (Build.VERSION.SDK_INT >= 33) {
                    clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                }
                clipboard.setClipEntry(ClipEntry(clip))
                copied = true
            }
        }) {
            Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(if (copied) "  Tersalin" else "  Salin")
        }
    }
    CodeInput(value = code, onValueChange = { code = it }, onComplete = { onVerify(it, remember) }, enabled = !state.busy, autoFocus = false)
    RememberRow(remember) { remember = it }
    ErrorText(state.error)
    Button(onClick = { onVerify(code, remember) }, enabled = !state.busy && code.length == 6, modifier = Modifier.fillMaxWidth()) {
        Text(if (state.busy) "Memeriksa…" else "Aktifkan & masuk")
    }
}
