package com.rosyidputrar.taskify.feature.auth

import com.rosyidputrar.taskify.core.designsystem.component.launchSafe
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.auth.AuthError
import com.rosyidputrar.taskify.core.auth.AuthRepository
import com.rosyidputrar.taskify.core.auth.LockManager
import com.rosyidputrar.taskify.core.auth.NetworkMonitor
import com.rosyidputrar.taskify.core.auth.UnlockResult
import com.rosyidputrar.taskify.core.designsystem.component.IndexTab
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.domain.auth.AuthMessages
import com.rosyidputrar.taskify.core.domain.lock.LockPolicy
import com.rosyidputrar.taskify.feature.auth.component.ErrorText
import com.rosyidputrar.taskify.feature.auth.component.PinDots
import com.rosyidputrar.taskify.feature.auth.component.PinPad
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

data class LockUiState(
    val entered: String = "",
    val checking: Boolean = false,
    val message: String? = null,
    val cooldownUntil: Long? = null,
    val forgotBusy: Boolean = false,
    val forgotError: String? = null,
)

class LockViewModel(
    private val lock: LockManager,
    private val auth: AuthRepository,
    private val network: NetworkMonitor,
) : ViewModel() {
    val pinLength: Int = lock.state.value.config?.length ?: 4
    private val _state = MutableStateFlow(LockUiState(cooldownUntil = lock.state.value.attempts.cooldownUntil))
    val state: StateFlow<LockUiState> = _state.asStateFlow()

    fun digit(d: Char) {
        val s = _state.value
        if (s.checking || s.entered.length >= pinLength || s.cooldownUntil.isActive()) return
        val next = s.entered + d
        _state.update { it.copy(entered = next, message = null) }
        if (next.length == pinLength) check(next)
    }

    fun backspace() = _state.update { it.copy(entered = it.entered.dropLast(1)) }

    private fun check(pin: String) {
        _state.update { it.copy(checking = true) }
        launchSafe {
            when (val result = lock.unlock(pin)) {
                UnlockResult.Success -> _state.value = LockUiState()
                is UnlockResult.Wrong -> _state.value = LockUiState(message = "PIN salah. Sisa ${result.remaining} percobaan.")
                is UnlockResult.CoolingDown -> _state.value = LockUiState(cooldownUntil = result.untilMillis)
            }
        }
    }

    fun cooldownEnded() = _state.update { it.copy(cooldownUntil = null, message = null) }

    /**
     * "Lupa PIN": password akun diperiksa ke server lalu PIN di perangkat ini dimatikan. Masuk
     * dengan password menghasilkan sesi aal1, jadi kode authenticator tetap diminta sesudahnya.
     */
    fun forgot(password: String) {
        val email = auth.currentEmail()
        when {
            email == null -> _state.update { it.copy(forgotError = "Email akun tidak ditemukan.") }
            password.isEmpty() -> _state.update { it.copy(forgotError = "Masukkan password akun.") }
            !network.isOnline() -> _state.update { it.copy(forgotError = "Butuh koneksi internet untuk memeriksa password.") }
            else -> {
                _state.update { it.copy(forgotBusy = true, forgotError = null) }
                launchSafe {
                    try {
                        auth.signIn(email, password)
                        lock.disable()
                    } catch (e: AuthError) {
                        val f = e.failure
                        val message = when {
                            f.network -> "Butuh koneksi internet untuk memeriksa password."
                            f.status == 429 -> AuthMessages.RATE_LIMITED
                            f.status == 400 || f.code == "invalid_credentials" -> "Password salah."
                            else -> "Gagal memeriksa password: ${f.message}"
                        }
                        _state.update { it.copy(forgotBusy = false, forgotError = message) }
                    }
                }
            }
        }
    }

    private fun Long?.isActive() = this != null && this > System.currentTimeMillis()
}

@Composable
fun LockScreen(viewModel: LockViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LockContent(
        pinLength = viewModel.pinLength,
        state = state,
        onDigit = viewModel::digit,
        onBackspace = viewModel::backspace,
        onCooldownEnded = viewModel::cooldownEnded,
        onForgot = viewModel::forgot,
    )
}

@Composable
fun LockContent(
    pinLength: Int,
    state: LockUiState,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onCooldownEnded: () -> Unit,
    onForgot: (password: String) -> Unit,
) {
    var forgotOpen by rememberSaveable { mutableStateOf(false) }
    var secondsLeft by remember { mutableLongStateOf(0L) }

    LaunchedEffect(state.cooldownUntil) {
        val until = state.cooldownUntil ?: return@LaunchedEffect
        while (true) {
            secondsLeft = ((until - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            if (secondsLeft <= 0) break
            delay(250)
        }
        onCooldownEnded()
    }
    val coolingDown = state.cooldownUntil != null && secondsLeft > 0

    Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            IndexTab(TaskifyModule.Sandi, text = "Terkunci")
            Spacer(Modifier.height(16.dp))
            Text("Masukkan PIN", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(24.dp))
            PinDots(filled = state.entered.length, total = pinLength)
            Spacer(Modifier.height(16.dp))
            val info = when {
                coolingDown -> "Terlalu banyak percobaan. Coba lagi dalam $secondsLeft detik."
                else -> state.message
            }
            Text(
                info ?: " ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Spacer(Modifier.height(24.dp))
            PinPad(onDigit = onDigit, onBackspace = onBackspace, enabled = !coolingDown && !state.checking)
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { forgotOpen = true }) { Text("Lupa PIN?") }
        }
    }

    if (forgotOpen) {
        var password by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { forgotOpen = false },
            title = { Text("Lupa PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Masukkan password akun Taskify. Bila benar, PIN di perangkat ini dimatikan dan kamu diminta kode authenticator. Butuh internet.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password akun") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ErrorText(state.forgotError)
                }
            },
            confirmButton = {
                TextButton(onClick = { onForgot(password) }, enabled = !state.forgotBusy) {
                    Text(if (state.forgotBusy) "Memeriksa…" else "Matikan PIN")
                }
            },
            dismissButton = { TextButton(onClick = { forgotOpen = false }) { Text("Batal") } },
        )
    }
}

/** Isi sheet/dialog pengaturan PIN: ketik dua kali lalu pilih jeda kunci otomatis. */
@Composable
fun PinSetupContent(onDone: (pin: String, autoLockMinutes: Int) -> Unit, onCancel: () -> Unit) {
    var first by rememberSaveable { mutableStateOf<String?>(null) }
    var entered by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var autoLock by rememberSaveable { mutableStateOf(LockPolicy.DEFAULT_AUTO_LOCK_MINUTES) }
    val confirming = first != null

    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(if (confirming) "Ulangi PIN" else "Buat PIN 4–8 angka", style = MaterialTheme.typography.headlineSmall)
        PinDots(filled = entered.length, total = maxOf(4, entered.length.coerceAtMost(8)))
        ErrorText(error)
        PinPad(
            onDigit = { if (entered.length < 8) entered += it; error = null },
            onBackspace = { entered = entered.dropLast(1) },
            enabled = true,
        )
        if (!confirming) {
            Text("Kunci otomatis setelah di latar", style = MaterialTheme.typography.labelLarge)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LockPolicy.AUTO_LOCK_OPTIONS.forEach { minutes ->
                    androidx.compose.material3.FilterChip(
                        selected = minutes == autoLock,
                        onClick = { autoLock = minutes },
                        label = { Text(if (minutes == 0) "Segera" else "$minutes mnt") },
                    )
                }
            }
        }
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onCancel) { Text("Batal") }
            androidx.compose.material3.Button(
                enabled = LockPolicy.isValidPin(entered),
                onClick = {
                    val pin = entered
                    when {
                        first == null -> { first = pin; entered = "" }
                        first == pin -> onDone(pin, autoLock)
                        else -> { first = null; entered = ""; error = "PIN tidak sama. Ulangi dari awal." }
                    }
                },
            ) { Text(if (confirming) "Simpan PIN" else "Lanjut") }
        }
    }
}
