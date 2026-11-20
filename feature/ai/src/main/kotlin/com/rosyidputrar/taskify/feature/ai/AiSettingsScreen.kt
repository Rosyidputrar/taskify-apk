package com.rosyidputrar.taskify.feature.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rosyidputrar.taskify.core.ai.AiPrompt
import com.rosyidputrar.taskify.core.ai.AiProvider
import com.rosyidputrar.taskify.core.designsystem.component.FitText
import com.rosyidputrar.taskify.core.designsystem.component.ModuleHeader
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import org.koin.compose.viewmodel.koinViewModel

/** Alamat umum layanan OpenAI-compatible (bisa diubah manual). */
internal val BaseUrlPresets = listOf(
    "OpenAI" to "https://api.openai.com/v1",
    "OpenRouter" to "https://openrouter.ai/api/v1",
    "Groq" to "https://api.groq.com/openai/v1",
    "Gemini" to "https://generativelanguage.googleapis.com/v1beta/openai",
    "DeepSeek" to "https://api.deepseek.com/v1",
    "Mistral" to "https://api.mistral.ai/v1",
)

@Composable
fun AiSettingsScreen(contentPadding: PaddingValues, onBack: (() -> Unit)?, backButton: @Composable (() -> Unit) -> Unit) {
    val vm: AiSettingsViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    AiSettingsContent(state, vm, contentPadding, onBack, backButton)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiSettingsContent(
    state: AiSettingsUi,
    actions: AiSettingsActions,
    contentPadding: PaddingValues,
    onBack: (() -> Unit)?,
    backButton: @Composable (() -> Unit) -> Unit,
) {
    val s = state.settings
    var confirmWipe by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).imePadding().widthIn(max = 720.dp),
        ) {
            ModuleHeader(TaskifyModule.Assistant, "Pengaturan AI", subtitle = "Pakai kunci API milikmu sendiri", navigation = onBack?.let { { backButton(it) } })
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Section("PENYEDIA")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    AiProvider.entries.forEachIndexed { i, p ->
                        SegmentedButton(selected = s.provider == p, onClick = { actions.setProvider(p) }, shape = SegmentedButtonDefaults.itemShape(i, AiProvider.entries.size), label = { FitText(p.label) })
                    }
                }
                Hint(s.provider.hint)

                Section("ALAMAT (BASE URL)")
                var url by remember(s.provider, s.baseUrl) { mutableStateOf(s.baseUrl) }
                OutlinedTextField(
                    url, { url = it }, label = { Text("Base URL") }, singleLine = true, isError = state.urlError != null,
                    supportingText = { Text(state.urlError ?: if (s.provider == AiProvider.Anthropic) "Biasanya https://api.anthropic.com" else "Contoh: https://api.openai.com/v1") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (s.provider == AiProvider.OpenAiCompatible) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        BaseUrlPresets.forEach { (name, preset) -> SuggestionChip(onClick = { url = preset }, label = { Text(name) }) }
                    }
                }
                AnimatedVisibility(url.trim().trimEnd('/') != s.baseUrl) {
                    FilledTonalButton(onClick = { actions.saveBaseUrl(url) }) { Text("Simpan alamat") }
                }

                Section("KUNCI API")
                KeySection(state, actions)

                Section("MODEL")
                ModelSection(state, actions)

                HorizontalDivider()
                Section("PERILAKU")
                var prompt by remember(s.systemPrompt) { mutableStateOf(s.systemPrompt) }
                OutlinedTextField(
                    prompt, { prompt = it }, label = { Text("System prompt bawaan") }, minLines = 5, maxLines = 14,
                    supportingText = { Text("Disalin ke setiap chat baru. {hari} {tanggal} {jam} {zona} diisi otomatis saat mengirim.") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { actions.saveSystemPrompt(prompt) }, enabled = prompt.trim() != s.systemPrompt) { Text("Simpan") }
                    if (s.systemPrompt != AiPrompt.DEFAULT) OutlinedButton(onClick = { prompt = AiPrompt.DEFAULT; actions.saveSystemPrompt(AiPrompt.DEFAULT) }) { Text("Kembalikan bawaan") }
                }
                Row(Modifier.fillMaxWidth().toggleable(s.dataAccess, role = Role.Switch) { actions.setDataAccess(it) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Akses data Taskify untuk chat baru", style = MaterialTheme.typography.bodyLarge)
                        Text("Asisten boleh membaca tugas, catatan, jurnal, dan keuangan lewat alat. Bisa diubah per percakapan. Sandi tidak pernah bisa dibaca.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(s.dataAccess, onCheckedChange = null)
                }

                HorizontalDivider()
                Section("PRIVASI")
                Hint("• Kunci API dienkripsi dengan Keystore perangkat dan tidak pernah ditampilkan lagi.\n• Percakapan hanya tersimpan di perangkat ini (tidak ikut sinkron) dan terhapus saat keluar akun.\n• Pesanmu — dan data Taskify yang dibaca alat bila akses data hidup — dikirim ke penyedia yang kamu pilih.")
                OutlinedButton(onClick = { confirmWipe = true }, enabled = state.chats > 0) {
                    Text(if (state.chats > 0) "Hapus semua percakapan (${state.chats})" else "Belum ada percakapan", color = if (state.chats > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (confirmWipe) AlertDialog(
        onDismissRequest = { confirmWipe = false },
        title = { Text("Hapus semua percakapan?") },
        text = { Text("${state.chats} percakapan dihapus dari perangkat ini. Tidak bisa dibatalkan.") },
        confirmButton = { TextButton(onClick = { actions.deleteAllChats(); confirmWipe = false }) { Text("Hapus", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Batal") } },
    )
}

@Composable
private fun KeySection(state: AiSettingsUi, actions: AiSettingsActions) {
    val s = state.settings
    var replacing by remember(s.provider, s.hasKey) { mutableStateOf(!s.hasKey) }
    var key by remember(s.provider) { mutableStateOf("") }
    if (s.hasKey && !replacing) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Key, null, tint = TaskifyTheme.modules.accent(TaskifyModule.Assistant))
                Spacer(Modifier.width(12.dp))
                Text("Tersimpan terenkripsi di perangkat ini", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { replacing = true }) { Text("Ganti") }
                TextButton(onClick = actions::clearKey) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            }
        }
    } else {
        OutlinedTextField(
            key, { key = it }, label = { Text("Kunci API") }, singleLine = true, isError = state.keyError != null,
            supportingText = { Text(state.keyError ?: if (s.keyInvalid) "Kunci yang tersimpan tidak bisa dibuka lagi (Keystore perangkat berubah). Simpan ulang kuncinya." else "Disimpan terenkripsi; tidak bisa dilihat lagi setelah disimpan.") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { actions.saveKey(key); key = "" }, enabled = key.isNotBlank()) { Text("Simpan kunci") }
            if (s.hasKey) TextButton(onClick = { replacing = false; key = "" }) { Text("Batal") }
        }
    }
}

@Composable
private fun ModelSection(state: AiSettingsUi, actions: AiSettingsActions) {
    val s = state.settings
    Button(onClick = actions::testConnection, enabled = s.hasKey && !state.testing) {
        if (state.testing) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
        Text(if (state.testing) "Menghubungi…" else "Tes koneksi & muat model")
    }
    state.testMessage?.let { msg ->
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                if (state.testOk == true) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, null,
                tint = if (state.testOk == true) TaskifyTheme.modules.accent(TaskifyModule.Assistant) else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(msg, style = MaterialTheme.typography.bodySmall, color = if (state.testOk == true) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
        }
    }
    var query by remember { mutableStateOf("") }
    var manual by remember(s.model) { mutableStateOf("") }
    if (s.models.isNotEmpty()) {
        OutlinedTextField(query, { query = it }, label = { Text("Cari model (${s.models.size})") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        val shown = remember(s.models, query) { s.models.filter { it.contains(query.trim(), true) }.take(60) }
        Column(Modifier.fillMaxWidth().animateContentSize()) {
            shown.forEach { m ->
                Row(
                    Modifier.fillMaxWidth().selectable(m == s.model, role = Role.RadioButton) { actions.chooseModel(m) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(m == s.model, onClick = null)
                    Spacer(Modifier.width(8.dp))
                    Text(m, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (s.models.size > shown.size && query.isBlank()) Hint("Ketik untuk mencari di ${s.models.size} model.")
        }
    }
    Text(if (s.model.isBlank()) "Belum ada model terpilih." else "Model aktif: ${s.model}", style = MaterialTheme.typography.labelLarge)
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(manual, { manual = it }, label = { Text("Atau ketik nama model") }, singleLine = true, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = { actions.chooseModel(manual); manual = "" }, enabled = manual.isNotBlank()) { Text("Pakai") }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = TaskifyTextStyles.indexLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
