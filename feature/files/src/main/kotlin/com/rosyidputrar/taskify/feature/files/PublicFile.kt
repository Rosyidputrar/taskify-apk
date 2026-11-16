package com.rosyidputrar.taskify.feature.files

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.designsystem.theme.SerifFamily
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.designsystem.theme.namedColor
import com.rosyidputrar.taskify.core.domain.files.TempFiles
import com.rosyidputrar.taskify.core.domain.files.formatBytes
import com.rosyidputrar.taskify.core.files.PublicFileInfo
import com.rosyidputrar.taskify.core.files.TempFileUploader
import com.rosyidputrar.taskify.core.files.TempFilesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

sealed interface PublicFileState {
    data object Loading : PublicFileState
    data object Missing : PublicFileState
    data object Error : PublicFileState
    data class Ready(val info: PublicFileInfo, val checkedAt: Long) : PublicFileState
}

data class PublicDownload(val progress: Float? = null, val running: Boolean = false, val done: Boolean = false, val error: String? = null)

interface PublicFileActions {
    fun retry()
    fun save(target: Uri)
    fun browserUrl(): String?
}

/**
 * Halaman unduh untuk penerima tautan Berkas publik (`/unduh/<token>`), dibuka TANPA login dan
 * tanpa sesi siapa pun — padanan PublicFilePage webapp. Info dulu (nama, ukuran, masa berlaku)
 * supaya penerima tahu apa yang diunduh; unduhan sebenarnya lewat Edge Function.
 */
class PublicFileViewModel(private val token: String?, private val api: TempFilesApi, private val uploader: TempFileUploader) : ViewModel(), PublicFileActions {
    private val _state = MutableStateFlow<PublicFileState>(if (token == null) PublicFileState.Missing else PublicFileState.Loading)
    val state: StateFlow<PublicFileState> = _state.asStateFlow()
    private val _download = MutableStateFlow(PublicDownload())
    val download: StateFlow<PublicDownload> = _download.asStateFlow()

    init { load() }

    private fun load() {
        val t = token ?: return
        _state.value = PublicFileState.Loading
        viewModelScope.launch {
            _state.value = try {
                api.publicInfo(t)?.let { PublicFileState.Ready(it, System.currentTimeMillis()) } ?: PublicFileState.Missing
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                PublicFileState.Error
            }
        }
    }

    override fun retry() = load()

    override fun save(target: Uri) {
        val t = token ?: return
        _download.value = PublicDownload(running = true)
        viewModelScope.launch {
            try {
                uploader.downloadPublic(t, target) { p -> _download.update { it.copy(progress = p) } }
                _download.value = PublicDownload(done = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _download.value = PublicDownload(error = e.message ?: "Gagal mengunduh.")
            }
        }
    }

    override fun browserUrl(): String? = token?.let(api::directDownloadUrl)
}

@Composable
fun PublicFileScreen(token: String?) {
    val vm: PublicFileViewModel = koinViewModel(key = "public-$token") { parametersOf(token) }
    val state by vm.state.collectAsStateWithLifecycle()
    val download by vm.download.collectAsStateWithLifecycle()
    PublicFileContent(state, download, vm)
}

@Composable
fun PublicFileContent(state: PublicFileState, download: PublicDownload, actions: PublicFileActions) {
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Files)
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text("Taskify", style = MaterialTheme.typography.headlineMedium.copy(fontFamily = SerifFamily), color = accent)
        Spacer(Modifier.height(20.dp))
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(24.dp), modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth()) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (state) {
                    PublicFileState.Loading -> Row(Modifier.padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(10.dp)); Text("Memeriksa tautan…")
                    }
                    PublicFileState.Missing -> {
                        Icon(Icons.Outlined.LinkOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Berkas tidak tersedia", style = MaterialTheme.typography.titleLarge.copy(fontFamily = SerifFamily))
                        Text("Tautan ini sudah kedaluwarsa, dicabut, berkasnya dihapus pemiliknya, atau tautannya tidak lengkap saat disalin.",
                            textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    PublicFileState.Error -> {
                        Text("Tidak bisa memeriksa tautan", style = MaterialTheme.typography.titleLarge.copy(fontFamily = SerifFamily))
                        Text("Periksa koneksi internet lalu coba lagi.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = actions::retry) { Text("Coba lagi") }
                    }
                    is PublicFileState.Ready -> ReadyBody(state, download, actions)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Dibagikan lewat Taskify. Buka berkas hanya bila kamu mempercayai pengirimnya.", textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(max = 440.dp))
    }
}

@Composable
private fun ReadyBody(state: PublicFileState.Ready, download: PublicDownload, actions: PublicFileActions) {
    val info = state.info
    val uri = LocalUriHandler.current
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(info.mimeType.ifBlank { "application/octet-stream" })) { it?.let(actions::save) }
    FileGlyph(info.mimeType, info.fileName, size = 64.dp)
    Text(info.fileName, style = MaterialTheme.typography.titleLarge.copy(fontFamily = SerifFamily), textAlign = TextAlign.Center)
    Text("${formatBytes(info.sizeBytes)} · ${TempFiles.expiryLabel(info.expiresAt, state.checkedAt)}",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    when {
        download.running -> {
            download.progress?.let { p -> LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth()) } ?: LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Mengunduh…", style = MaterialTheme.typography.labelMedium)
        }
        download.done -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CheckCircle, null, tint = namedColor("emerald")); Spacer(Modifier.width(8.dp)); Text("Tersimpan")
        }
        else -> Button(onClick = { save.launch(info.fileName) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Icon(Icons.Outlined.Download, null); Spacer(Modifier.width(8.dp)); Text("Unduh")
        }
    }
    download.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    actions.browserUrl()?.let { url ->
        TextButton(onClick = { runCatching { uri.openUri(url) } }) {
            Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Unduh lewat browser")
        }
    }
    if (TempFiles.isExecutableName(info.fileName)) {
        Surface(color = namedColor("amber").copy(alpha = 0.14f), shape = MaterialTheme.shapes.medium) {
            Row(Modifier.padding(12.dp)) {
                Icon(Icons.Outlined.GppMaybe, null, tint = namedColor("amber"), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Ini berkas program yang bisa dijalankan. Jangan buka bila kamu tidak yakin isinya.", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

