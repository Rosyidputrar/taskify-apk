package com.rosyidputrar.taskify.feature.vault

import com.rosyidputrar.taskify.core.designsystem.component.LoadingRow
import com.rosyidputrar.taskify.core.designsystem.component.launchSafe
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rosyidputrar.taskify.core.designsystem.theme.SerifFamily
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.files.PublicSnippetInfo
import com.rosyidputrar.taskify.core.files.SnippetsApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

sealed interface PublicSnippetState {
    data object Loading : PublicSnippetState
    data object Missing : PublicSnippetState
    data object Error : PublicSnippetState
    data class Ready(val info: PublicSnippetInfo) : PublicSnippetState
}

/**
 * Halaman snippet publik (`/snippet/<slug>`), dibuka TANPA login dan tanpa sesi siapa pun —
 * padanan PublicSnippetPage webapp. Read-only; perubahan kode pemilik langsung terlihat.
 */
class PublicSnippetViewModel(private val slug: String?, private val api: SnippetsApi) : ViewModel() {
    private val _state = MutableStateFlow<PublicSnippetState>(if (slug == null) PublicSnippetState.Missing else PublicSnippetState.Loading)
    val state: StateFlow<PublicSnippetState> = _state.asStateFlow()

    init { load() }

    fun retry() = load()

    private fun load() {
        val s = slug ?: return
        _state.value = PublicSnippetState.Loading
        launchSafe {
            _state.value = try {
                api.publicInfo(s)?.let { PublicSnippetState.Ready(it) } ?: PublicSnippetState.Missing
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                PublicSnippetState.Error
            }
        }
    }
}

@Composable
fun PublicSnippetScreen(slug: String?) {
    val vm: PublicSnippetViewModel = koinViewModel(key = "public-snippet-$slug") { parametersOf(slug) }
    val state by vm.state.collectAsStateWithLifecycle()
    PublicSnippetContent(state, vm::retry)
}

@Composable
fun PublicSnippetContent(state: PublicSnippetState, onRetry: () -> Unit) {
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Vault)
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text("Taskify", style = MaterialTheme.typography.headlineMedium.copy(fontFamily = SerifFamily), color = accent)
        Spacer(Modifier.height(20.dp))
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(24.dp), modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (state) {
                    PublicSnippetState.Loading -> Row(Modifier.padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                        LoadingRow("Memuat snippet…", module = TaskifyModule.Vault)
                    }
                    PublicSnippetState.Missing -> {
                        Icon(Icons.Outlined.LinkOff, null, Modifier.size(48.dp).align(Alignment.CenterHorizontally), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Snippet tidak tersedia", style = MaterialTheme.typography.titleLarge.copy(fontFamily = SerifFamily), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Text("Tautan ini sudah dicabut, snippet-nya dihapus pemiliknya, atau tautannya tidak lengkap saat disalin.",
                            textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    PublicSnippetState.Error -> {
                        Text("Tidak bisa memuat snippet", style = MaterialTheme.typography.titleLarge.copy(fontFamily = SerifFamily))
                        Text("Periksa koneksi internet lalu coba lagi.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = onRetry) { Text("Coba lagi") }
                    }
                    is PublicSnippetState.Ready -> ReadyBody(state.info)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Dibagikan lewat Taskify.", textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReadyBody(info: PublicSnippetInfo) {
    val context = LocalContext.current
    Text(info.title, style = MaterialTheme.typography.titleLarge.copy(fontFamily = SerifFamily))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(info.language, style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Button(onClick = {
            val cm = context.getSystemService(android.content.ClipboardManager::class.java)
            cm?.setPrimaryClip(android.content.ClipData.newPlainText(info.title, info.code))
        }) {
            Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Salin kode")
        }
    }
    info.description?.takeIf { it.isNotBlank() }?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        SelectionContainer {
            Text(
                info.code,
                Modifier.horizontalScroll(rememberScrollState()).padding(14.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }
}
