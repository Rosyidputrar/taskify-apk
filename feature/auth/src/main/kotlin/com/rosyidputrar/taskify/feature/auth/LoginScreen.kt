package com.rosyidputrar.taskify.feature.auth

import androidx.compose.runtime.LaunchedEffect
import com.rosyidputrar.taskify.core.designsystem.component.rememberHaptics
import com.rosyidputrar.taskify.core.designsystem.component.TaskifyLoadingIndicator
import com.rosyidputrar.taskify.core.designsystem.component.launchSafe
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.auth.AuthError
import com.rosyidputrar.taskify.core.auth.AuthRepository
import com.rosyidputrar.taskify.core.auth.LoginNameStore
import com.rosyidputrar.taskify.core.domain.auth.loginGreeting
import com.rosyidputrar.taskify.core.domain.auth.translateLoginError
import com.rosyidputrar.taskify.feature.auth.component.AuthScaffold
import com.rosyidputrar.taskify.feature.auth.component.ErrorText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

data class LoginUiState(val loading: Boolean = false, val error: String? = null)

class LoginViewModel(private val auth: AuthRepository, loginNames: LoginNameStore) : ViewModel() {
    val greeting: String = loginGreeting(loginNames.read())

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun submit(email: String, password: String) {
        if (email.isBlank() || password.isEmpty()) {
            _state.value = LoginUiState(error = "Isi email dan password.")
            return
        }
        _state.value = LoginUiState(loading = true)
        launchSafe {
            try {
                auth.signIn(email, password)
                // Sukses → status sesi berubah, gerbang pindah ke verifikasi dua langkah.
                _state.update { it.copy(loading = false) }
            } catch (e: AuthError) {
                _state.value = LoginUiState(error = loginErrorText(e.failure))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Jaring terakhir: apa pun yang lolos, tombol berhenti berputar dan error mentahnya terlihat.
                _state.value = LoginUiState(error = "Gagal masuk.\n${e::class.java.simpleName}: ${e.message.orEmpty()}".trimEnd(' ', ':'))
            }
        }
    }
}

@Composable
fun LoginScreen(viewModel: LoginViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LoginContent(greeting = viewModel.greeting, state = state, onSubmit = viewModel::submit)
}

@Composable
fun LoginContent(greeting: String, state: LoginUiState, onSubmit: (email: String, password: String) -> Unit) {
    val haptics = rememberHaptics()
    LaunchedEffect(state.error) { if (state.error != null) haptics.error() }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }

    AuthScaffold(
        tab = "Taskify",
        title = greeting,
        subtitle = "Masuk untuk menyinkronkan tugas, jurnal, keuangan, dan brankasmu di HP & laptop.",
        footer = {
            Text(
                "Akun dibuat oleh pemilik server. Pendaftaran mandiri dimatikan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.EmailAddress },
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (showPassword) "Sembunyikan password" else "Tampilkan password",
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onSubmit(email, password) }),
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
        )
        ErrorText(state.error)
        Button(onClick = { onSubmit(email, password) }, enabled = !state.loading, modifier = Modifier.fillMaxWidth()) {
            if (state.loading) TaskifyLoadingIndicator(size = 24.dp, color = androidx.compose.material3.LocalContentColor.current) else Text("Masuk")
        }
    }
}

/**
 * Pesan ramah + detail mentah untuk error yang tidak dikenali (kode/kelas & pesan asli), supaya
 * masalah seperti Keystore atau server yang salah konfigurasi bisa didiagnosis dari layar.
 */
internal fun loginErrorText(f: com.rosyidputrar.taskify.core.domain.auth.AuthFailure): String {
    val friendly = translateLoginError(f)
    val known = f.network || f.status == 429 || f.code == "over_request_rate_limit" || f.code == "invalid_credentials" || f.status == 400
    if (known && f.code != "timeout") return friendly
    val raw = listOfNotNull(f.status?.let { "HTTP $it" }, f.code, f.message?.takeIf { it != friendly }).joinToString(" · ")
    return if (raw.isBlank()) friendly else "$friendly\n$raw"
}
