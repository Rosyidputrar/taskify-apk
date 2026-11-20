package com.rosyidputrar.taskify.feature.vault.config

import androidx.compose.runtime.LaunchedEffect
import com.rosyidputrar.taskify.core.designsystem.component.RecentEntities
import com.rosyidputrar.taskify.core.domain.home.ShortcutType
import com.rosyidputrar.taskify.core.ui.home.TodayPinMenuItem
import androidx.activity.compose.BackHandler
import com.rosyidputrar.taskify.core.designsystem.component.DiscardChangesDialog
import com.rosyidputrar.taskify.core.designsystem.component.rememberHaptics
import com.rosyidputrar.taskify.core.designsystem.component.TaskifyHaptics
import com.rosyidputrar.taskify.core.designsystem.component.LoadingBlock
import com.rosyidputrar.taskify.core.designsystem.component.LoadingRow
import com.rosyidputrar.taskify.core.designsystem.component.SecureWindow
import com.rosyidputrar.taskify.core.designsystem.component.FitText
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rosyidputrar.taskify.core.designsystem.component.EmptyState
import com.rosyidputrar.taskify.core.designsystem.component.IndexCard
import com.rosyidputrar.taskify.core.designsystem.component.ModuleHeader
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.namedColor
import com.rosyidputrar.taskify.core.domain.files.formatBytes
import com.rosyidputrar.taskify.core.domain.sandi.ConfigFormat
import com.rosyidputrar.taskify.core.domain.sandi.ConfigParser
import com.rosyidputrar.taskify.core.domain.sandi.ProblemLevel
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.sandi.DecryptedVersion
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private const val MASK = "••••••••"

internal fun versionDate(iso: String): String = Dates.parseTimestampMillis(iso)?.let { Dates.isoToLocal(Dates.millisToIso(it)) }
    ?.let { (d, t) -> "${Dates.format(d, "d MMM yyyy")} $t" } ?: iso

/** Kartu "brankas terkunci" — isi konfigurasi hanya bisa dibaca setelah brankas Sandi dibuka. */
@Composable
fun LockedNotice(onOpenSandi: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        TaskifyModule.Sandi, "Brankas Sandi terkunci",
        "Isi berkas konfigurasi dienkripsi dengan kunci brankas Sandi. Buka brankas dulu untuk melihat atau mengubahnya.",
        modifier = modifier,
        action = { Button(onClick = onOpenSandi) { Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Buka brankas") } },
    )
}

// ================================================================== berkas

@Composable
fun ConfigFileScreen(id: String, contentPadding: PaddingValues, onBack: () -> Unit, onEdit: (String) -> Unit, onOpenSandi: () -> Unit, backButton: @Composable (() -> Unit) -> Unit) {
    val vm: ConfigFileViewModel = koinViewModel(key = "config-$id") { parametersOf(id) }
    val state by vm.state.collectAsStateWithLifecycle()
    // Nilai konfigurasi yang didekripsi (bisa ditampilkan) → tidak boleh di-screenshot / tampil di Terkini.
    SecureWindow()
    ConfigFileContent(state, vm, contentPadding, onBack, onEdit = { onEdit(id) }, onOpenSandi, backButton)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConfigFileContent(
    state: ConfigFileUiState,
    actions: ConfigFileActions,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenSandi: () -> Unit,
    backButton: @Composable (() -> Unit) -> Unit,
) {
    val haptics = rememberHaptics()
    @Suppress("NAME_SHADOWING")
    val actions = remember(actions, haptics) { HapticConfigFileActions(actions, haptics) }
    val file = state.file
    LaunchedEffect(file?.id, file?.name) { file?.let { RecentEntities.report("config_file", it.id, it.name) } }
    var menu by remember { mutableStateOf(false) }
    var meta by rememberSaveable { mutableStateOf(false) }
    var raw by rememberSaveable { mutableStateOf(false) }
    var revealAll by rememberSaveable { mutableStateOf(false) }
    var revealed by remember { mutableStateOf(setOf<String>()) }
    var compare by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "header") {
            ModuleHeader(
                TaskifyModule.Vault, file?.name ?: "Konfigurasi",
                subtitle = file?.let { listOfNotNull(it.environment, it.format.label, it.projectName, "${it.versions} versi").joinToString(" · ") },
                navigation = { backButton(onBack) },
                actions = {
                    if (file != null) {
                        if (!state.locked) IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "Ubah isi") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Lainnya") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Ubah nama & lingkungan") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; meta = true })
                                TodayPinMenuItem(ShortcutType.Config, file.id, file.name, onDone = { menu = false })
                                DropdownMenuItem(text = { Text("Pindahkan ke Sampah") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; actions.remove(onBack) })
                            }
                        }
                    }
                },
            )
        }
        if (state.busy) item(key = "busy") { LoadingRow("Memproses…", Modifier.padding(horizontal = 20.dp), module = TaskifyModule.Vault) }
        if (state.loaded && file == null) {
            item(key = "missing") { EmptyState(TaskifyModule.Vault, "Berkas tidak ditemukan", "Mungkin sudah dihapus atau belum tersinkron ke perangkat ini.") }
            return@LazyColumn
        }
        if (state.locked) {
            item(key = "locked") { LockedNotice(onOpenSandi) }
            return@LazyColumn
        }
        val versions = state.versions.versions
        item(key = "versions") {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(versions, key = { it.row.id }) { v ->
                    FilterChip(selected = state.selected?.row?.id == v.row.id, onClick = { actions.select(v.row.id) },
                        label = { Text("v${v.row.number} · ${versionDate(v.row.createdAt).substringBeforeLast(' ')}") })
                }
            }
        }
        if (state.versions.failed.isNotEmpty()) item(key = "failed") {
            Text("${state.versions.failed.size} versi tidak bisa dibuka dengan kunci ini.", Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
        }
        val sel = state.selected ?: run {
            if (state.versions.ready) item(key = "none") { EmptyState(TaskifyModule.Vault, "Belum ada versi", "Ketuk ubah untuk menulis isi pertama.") }
            return@LazyColumn
        }
        val format = file?.format ?: ConfigFormat.Env
        val parsed = ConfigParser.parse(sel.payload.text, format)
        val prev = state.previous
        val diff = prev?.let { ConfigParser.diff(ConfigParser.parse(it.payload.text, format).entries, parsed.entries) }
        item(key = "info") {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    listOfNotNull(versionDate(sel.row.createdAt), "${parsed.entries.size} kunci", formatBytes(ConfigParser.byteLength(sel.payload.text).toLong()),
                        diff?.let { ConfigParser.summarize(it) ?: "tanpa perubahan kunci" }).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (sel.payload.note.isNotBlank()) Text("“${sel.payload.note}”", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(raw, { raw = !raw }, label = { Text("Teks mentah") })
                    FilterChip(revealAll, { revealAll = !revealAll; revealed = emptySet() }, label = { Text(if (revealAll) "Sembunyikan nilai" else "Tampilkan nilai") })
                    if (diff != null && !diff.isEmpty) FilterChip(compare, { compare = !compare }, label = { Text("Banding v${prev.row.number}") })
                    IconButton(onClick = { actions.copyRaw(sel.payload.text) }) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Salin seluruh isi") }
                }
                if (!state.isLatest) OutlinedButton(onClick = { actions.restore(sel) }, enabled = !state.busy) {
                    Icon(Icons.Outlined.Restore, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Pulihkan v${sel.row.number} sebagai versi baru")
                }
            }
        }
        if (compare && diff != null) {
            item(key = "diff") {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    diff.added.forEach { DiffLine("+", it.key, if (revealAll) it.value else MASK, namedColor("emerald")) }
                    diff.changed.forEach { (k, a, b) -> DiffLine("~", k, if (revealAll) "$a → $b" else "$MASK → $MASK", namedColor("amber")) }
                    diff.removed.forEach { DiffLine("−", it.key, if (revealAll) it.value else MASK, MaterialTheme.colorScheme.error) }
                }
            }
        }
        if (parsed.problems.isNotEmpty()) item(key = "problems") { Problems(parsed.problems.map { it.level to it.message }) }
        if (raw) {
            item(key = "raw") {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(
                        if (revealAll) sel.payload.text else maskRaw(sel.payload.text, format),
                        fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
                    )
                }
            }
        } else {
            items(parsed.entries, key = { "e-" + it.key }) { e ->
                val shown = revealAll || e.key in revealed
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(e.key, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (shown) e.value.ifEmpty { "(kosong)" } else MASK, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (shown) 6 else 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { revealed = if (e.key in revealed) revealed - e.key else revealed + e.key }) {
                        Icon(if (shown) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, contentDescription = if (shown) "Sembunyikan ${e.key}" else "Tampilkan ${e.key}")
                    }
                    IconButton(onClick = { actions.copyValue(e.key, e.value) }) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Salin ${e.key}") }
                }
            }
        }
    }
    if (meta && file != null) MetaDialog(file.name, file.environment ?: "", onDismiss = { meta = false }) { n, env -> actions.updateMeta(n, env); meta = false }
}

/** Teks mentah dengan nilai disamarkan (kunci & komentar tetap terlihat). */
internal fun maskRaw(text: String, format: ConfigFormat): String = if (format == ConfigFormat.Json) {
    text.replace(Regex("(:\\s*)(\"(?:[^\"\\\\]|\\\\.)*\"|-?\\d[\\d.eE+-]*|true|false|null)")) { it.groupValues[1] + "\"$MASK\"" }
} else {
    text.lines().joinToString("\n") { line ->
        val m = Regex("^(\\s*(?:export\\s+)?[A-Za-z_][A-Za-z0-9_.-]*\\s*=)(.*)$").find(line)
        if (m == null || line.trimStart().startsWith("#")) line else m.groupValues[1] + if (m.groupValues[2].isBlank()) "" else MASK
    }
}

@Composable
private fun DiffLine(sign: String, key: String, value: String, color: Color) {
    Row(Modifier.fillMaxWidth().background(color.copy(alpha = 0.10f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(sign, color = color, fontFamily = FontFamily.Monospace, modifier = Modifier.width(18.dp))
        Text(key, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun Problems(list: List<Pair<ProblemLevel, String>>) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        list.take(8).forEach { (level, msg) ->
            Text((if (level == ProblemLevel.Error) "✕ " else "! ") + msg, style = MaterialTheme.typography.labelMedium,
                color = if (level == ProblemLevel.Error) MaterialTheme.colorScheme.error else namedColor("amber"))
        }
        if (list.size > 8) Text("…dan ${list.size - 8} lagi", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MetaDialog(name: String, environment: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var n by rememberSaveable { mutableStateOf(name) }
    var env by rememberSaveable { mutableStateOf(environment) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ubah berkas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(n, { n = it }, label = { Text("Nama berkas") }, singleLine = true)
                OutlinedTextField(env, { env = it }, label = { Text("Lingkungan") }, placeholder = { Text("production, staging…") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(n, env) }, enabled = n.isNotBlank()) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

// ================================================================== editor

@Composable
fun ConfigEditorScreen(fileId: String?, projectId: String?, contentPadding: PaddingValues, onBack: () -> Unit, onSaved: (String) -> Unit, onOpenSandi: () -> Unit, backButton: @Composable (() -> Unit) -> Unit) {
    val vm: ConfigEditorViewModel = koinViewModel(key = "config-edit-$fileId") { parametersOf(fileId, projectId) }
    val state by vm.state.collectAsStateWithLifecycle()
    // Editor selalu menampilkan teks polos konfigurasi.
    SecureWindow()
    var confirmLeave by remember { mutableStateOf(false) }
    val back = { if (state.dirty && !state.saving) confirmLeave = true else onBack() }
    BackHandler(onBack = back)
    ConfigEditorContent(state, vm, contentPadding, back, onSaved, onOpenSandi, backButton)
    if (confirmLeave) {
        DiscardChangesDialog(
            if (state.isNew) "konfigurasi baru ini" else "\"${state.name}\"",
            onSave = { confirmLeave = false; vm.save(onSaved) },
            onDiscard = { confirmLeave = false; onBack() },
            onDismiss = { confirmLeave = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConfigEditorContent(
    state: ConfigEditorUiState,
    actions: ConfigEditorActions,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onOpenSandi: () -> Unit,
    backButton: @Composable (() -> Unit) -> Unit,
) {
    val parsed = remember(state.text, state.format) { ConfigParser.parse(state.text, state.format) }
    val size = remember(state.text) { ConfigParser.sizeProblem(state.text) }
    val bytes = remember(state.text) { ConfigParser.byteLength(state.text) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "header") {
            ModuleHeader(
                TaskifyModule.Vault, if (state.isNew) "Konfigurasi baru" else state.name,
                subtitle = (if (state.isNew) "" else "Versi baru · ") + "${parsed.entries.size} kunci · ${formatBytes(bytes.toLong())} dari ${formatBytes(ConfigParser.MAX_BYTES.toLong())}",
                navigation = { backButton(onBack) },
            )
        }
        if (state.locked) {
            item(key = "locked") { LockedNotice(onOpenSandi) }
            return@LazyColumn
        }
        if (!state.loaded) {
            item(key = "loading") { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingBlock(label = "Membuka konfigurasi…", module = TaskifyModule.Vault) } }
            return@LazyColumn
        }
        if (state.isNew) item(key = "meta") {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(state.name, actions::setName, label = { Text("Nama berkas") }, placeholder = { Text(".env.production") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ConfigFormat.entries.forEachIndexed { i, f ->
                        SegmentedButton(selected = state.format == f, onClick = { actions.setFormat(f) }, shape = SegmentedButtonDefaults.itemShape(i, ConfigFormat.entries.size), label = { FitText(f.label) })
                    }
                }
                OutlinedTextField(state.environment, actions::setEnvironment, label = { Text("Lingkungan (opsional)") }, placeholder = { Text("production") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (state.projects.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(state.projectId == null, { actions.setProject(null) }, label = { Text("Tanpa project") })
                    state.projects.take(12).forEach { p -> FilterChip(state.projectId == p.id, { actions.setProject(p.id) }, label = { Text(p.name) }) }
                }
            }
        }
        item(key = "text") {
            OutlinedTextField(
                state.text, actions::setText,
                label = { Text("Isi (${state.format.label})") },
                placeholder = { Text(if (state.format == ConfigFormat.Json) "{\n  \"API_URL\": \"https://…\"\n}" else "API_URL=https://…\nDEBUG=0", fontFamily = FontFamily.Monospace) },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                isError = parsed.blocking || size != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp).padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        val problems = listOfNotNull(size) + parsed.problems
        if (problems.isNotEmpty()) item(key = "problems") { Problems(problems.map { it.level to (if (it.line > 0) "Baris ${it.line}: " else "") + it.message }) }
        item(key = "save") {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(state.note, actions::setNote, label = { Text("Catatan perubahan (opsional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                if (state.unchanged) Text("Belum ada perubahan dari versi terakhir.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Isi dienkripsi di perangkat ini; versi lama tetap tersimpan.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { actions.save(onSaved) }, enabled = !state.saving && !state.unchanged && !parsed.blocking && size == null, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.saving) "Mengenkripsi…" else if (state.isNew) "Buat berkas" else "Simpan versi baru")
                }
            }
        }
    }
}

/** Getar halus per makna (lihat [TaskifyHaptics]) tanpa mengubah logika aksi. */
private class HapticConfigFileActions(private val a: ConfigFileActions, private val h: TaskifyHaptics) : ConfigFileActions by a {
    override fun select(versionId: String) { h.tick(); a.select(versionId) }
    override fun copyValue(key: String, value: String) { h.success(); a.copyValue(key, value) }
    override fun copyRaw(text: String) { h.success(); a.copyRaw(text) }
    override fun restore(version: DecryptedVersion) { h.success(); a.restore(version) }
}
