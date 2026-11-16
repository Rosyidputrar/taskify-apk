package com.rosyidputrar.taskify.feature.vault

import com.rosyidputrar.taskify.core.designsystem.component.LoadingScreen
import com.rosyidputrar.taskify.core.designsystem.component.launchSafe
import com.rosyidputrar.taskify.core.designsystem.component.FitText
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.designsystem.component.IndexTab
import com.rosyidputrar.taskify.core.designsystem.component.Messenger
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.vault.LANGUAGES
import com.rosyidputrar.taskify.core.domain.vault.languageLabel
import com.rosyidputrar.taskify.core.domain.vault.templateVariables
import com.rosyidputrar.taskify.core.store.vault.VaultRepository
import com.rosyidputrar.taskify.feature.vault.component.CodeView
import com.rosyidputrar.taskify.feature.vault.component.copyToClipboard
import com.rosyidputrar.taskify.feature.vault.component.looksSecret
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

data class SnippetDraft(
    val loaded: Boolean = false,
    val id: String? = null,
    val title: String = "",
    val language: String = "typescript",
    val description: String = "",
    val code: String = "",
    val dirty: Boolean = false,
)

/** Editor snippet dengan simpan eksplisit (sama dengan webapp) + peringatan perubahan belum disimpan. */
class SnippetEditorViewModel(private val id: String?, private val repo: VaultRepository, private val messenger: Messenger) : ViewModel() {
    private val _draft = MutableStateFlow(SnippetDraft())
    val draft: StateFlow<SnippetDraft> = _draft

    init {
        launchSafe {
            _draft.value = id?.let { repo.getSnippet(it) }?.let { SnippetDraft(true, it.id, it.title, it.language, it.description.orEmpty(), it.code) }
                ?: SnippetDraft(loaded = true)
        }
    }

    fun edit(block: (SnippetDraft) -> SnippetDraft) = _draft.update { block(it).copy(dirty = true) }

    fun save(onDone: () -> Unit) {
        val d = _draft.value
        if (d.title.isBlank() || d.code.isBlank()) return
        launchSafe {
            val saved = repo.saveSnippet(d.id, d.title, d.language, d.code, d.description)
            _draft.update { it.copy(id = saved, dirty = false) }
            messenger.show("Snippet disimpan")
            onDone()
        }
    }

    fun remove(onDone: () -> Unit) {
        val sid = _draft.value.id ?: return onDone()
        launchSafe {
            repo.removeSnippet(sid)
            messenger.undoable("Snippet dipindahkan ke Sampah") { repo.restoreSnippet(sid) }
            onDone()
        }
    }
}

@Composable
fun SnippetEditorScreen(id: String?, onBack: () -> Unit) {
    val vm: SnippetEditorViewModel = koinViewModel(key = "snippet-$id") { parametersOf(id) }
    val d by vm.draft.collectAsStateWithLifecycle()
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val back = { if (d.dirty) confirmLeave = true else onBack() }
    BackHandler(onBack = back)
    SnippetEditorContent(d, vm::edit, back, save = { vm.save(onBack) }, remove = { vm.remove(onBack) })
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Buang perubahan?") },
            text = { Text("Perubahan pada snippet ini belum disimpan.") },
            confirmButton = { Button(onClick = { vm.save(onBack); confirmLeave = false }) { Text("Simpan") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false; onBack() }) { Text("Buang") } },
        )
    }
}

@Composable
fun SnippetEditorContent(d: SnippetDraft, edit: ((SnippetDraft) -> SnippetDraft) -> Unit, back: () -> Unit, save: () -> Unit, remove: () -> Unit, initialPreview: Boolean = false) {
    if (!d.loaded) {
        LoadingScreen(module = com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule.Vault)
        return
    }
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    var preview by rememberSaveable { mutableStateOf(initialPreview) }
    var langMenu by rememberSaveable { mutableStateOf(false) }
    val vars = templateVariables(d.code)
    Column(Modifier.fillMaxSize().background(colors.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali") }
            Text(if (d.id == null) "Snippet baru" else "Ubah snippet", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { copyToClipboard(context, d.title, d.code, looksSecret(d.code)) }, enabled = d.code.isNotEmpty()) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Salin kode") }
            if (d.id != null) IconButton(onClick = remove) { Icon(Icons.Outlined.Delete, contentDescription = "Hapus snippet") }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 760.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IndexTab(TaskifyModule.Vault)
            OutlinedTextField(d.title, { v -> edit { it.copy(title = v) } }, label = { Text("Judul") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    AssistChip(onClick = { langMenu = true }, label = { Text(languageLabel(d.language)) })
                    DropdownMenu(expanded = langMenu, onDismissRequest = { langMenu = false }) {
                        LANGUAGES.forEach { l -> DropdownMenuItem(text = { Text(l.label) }, onClick = { edit { it.copy(language = l.value) }; langMenu = false }) }
                    }
                }
                SingleChoiceSegmentedButtonRow {
                    listOf("Tulis" to false, "Lihat" to true).forEachIndexed { i, (label, v) ->
                        SegmentedButton(selected = preview == v, onClick = { preview = v }, shape = SegmentedButtonDefaults.itemShape(i, 2), icon = {}, label = { FitText(label) })
                    }
                }
            }
            if (preview) {
                CodeView(d.code.ifEmpty { " " }, d.language)
            } else {
                Surface(color = colors.surfaceContainerHigh, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                    BasicTextField(
                        value = d.code,
                        onValueChange = { v -> edit { it.copy(code = v) } },
                        textStyle = TaskifyTextStyles.code.copy(color = colors.onSurface),
                        cursorBrush = SolidColor(TaskifyTheme.modules.accent(TaskifyModule.Vault)),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp).horizontalScroll(rememberScrollState()).padding(12.dp).semantics { contentDescription = "Kode" },
                        decorationBox = { inner ->
                            if (d.code.isEmpty()) Text("// Tempel kode di sini\n// Pakai {{nama}} untuk variabel template", style = TaskifyTextStyles.code, color = colors.onSurfaceVariant)
                            inner()
                        },
                    )
                }
            }
            if (vars.isNotEmpty()) Text("Variabel template: " + vars.joinToString { "{{$it}}" }, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            OutlinedTextField(d.description, { v -> edit { it.copy(description = v) } }, label = { Text("Deskripsi (opsional)") }, modifier = Modifier.fillMaxWidth())
        }
        Surface(color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth().imePadding()) {
            Button(onClick = save, enabled = d.title.isNotBlank() && d.code.isNotBlank(), modifier = Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp).fillMaxWidth()) {
                Text(if (d.id == null) "Simpan snippet" else "Simpan perubahan")
            }
        }
    }
}
