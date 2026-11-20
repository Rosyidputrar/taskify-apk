package com.rosyidputrar.taskify.feature.vault

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.rosyidputrar.taskify.core.designsystem.component.rememberHaptics
import com.rosyidputrar.taskify.core.designsystem.theme.SerifFamily
import com.rosyidputrar.taskify.core.domain.vault.SnippetExport
import com.rosyidputrar.taskify.core.domain.vault.SnippetFormat
import com.rosyidputrar.taskify.core.ui.share.ShareCode
import com.rosyidputrar.taskify.core.ui.share.ShareOption
import com.rosyidputrar.taskify.core.ui.share.ShareSection
import com.rosyidputrar.taskify.core.ui.share.ShareService
import com.rosyidputrar.taskify.core.ui.share.ShareSheet
import com.rosyidputrar.taskify.core.ui.share.confirmCopied
import com.rosyidputrar.taskify.feature.vault.component.TemplateDialog
import com.rosyidputrar.taskify.core.designsystem.component.DiscardChangesDialog
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
    val favorite: Boolean = false,
)

/** Snippet untuk dibagikan / disalin (isi draf saat ini, termasuk yang belum disimpan). */
fun SnippetDraft.toShare() = ShareCode(title.trim().ifEmpty { "snippet" }, language, code, description.trim().ifEmpty { null }, TaskifyModule.Vault.light.toArgb())

private const val TRUNCATED = "Kode terlalu panjang — gambar dipotong. Bagikan sebagai berkas untuk versi lengkap."

/** Editor snippet dengan simpan eksplisit (sama dengan webapp) + peringatan perubahan belum disimpan. */
class SnippetEditorViewModel(
    private val id: String?,
    private val repo: VaultRepository,
    private val messenger: Messenger,
    private val sharer: ShareService,
) : ViewModel() {
    private val _draft = MutableStateFlow(SnippetDraft())
    val draft: StateFlow<SnippetDraft> = _draft

    init {
        launchSafe {
            _draft.value = id?.let { repo.getSnippet(it) }?.let { SnippetDraft(true, it.id, it.title, it.language, it.description.orEmpty(), it.code, favorite = it.favorite) }
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

    /** Favorit langsung tersimpan (tidak menunggu tombol Simpan). */
    fun toggleFavorite() {
        val d = _draft.value
        val sid = d.id ?: return
        _draft.update { it.copy(favorite = !d.favorite) }
        launchSafe { repo.setSnippetFavorite(sid, !d.favorite) }
    }

    /** [format] null = kirim sebagai teks (Markdown berpagar). */
    fun share(format: SnippetFormat?) {
        val code = _draft.value.toShare()
        if (code.code.isBlank()) { messenger.show("Kode masih kosong"); return }
        if (format == null) sharer.shareCodeText(code)
        else launchSafe { if (sharer.shareCode(code, format)) messenger.show(TRUNCATED) }
    }

    fun copyImage() {
        val code = _draft.value.toShare()
        if (code.code.isBlank()) return
        launchSafe {
            val truncated = sharer.copyCodeImage(code)
            when {
                truncated -> messenger.show(TRUNCATED)
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> messenger.show("Gambar kode disalin")
            }
        }
    }
}

interface SnippetEditorActions {
    fun edit(block: (SnippetDraft) -> SnippetDraft)
    fun back()
    fun save()
    fun remove()
    fun toggleFavorite() {}
    /** [format] null = kirim sebagai teks. */
    fun share(format: SnippetFormat?) {}
    fun copyImage() {}
}

@Composable
fun SnippetEditorScreen(id: String?, onBack: () -> Unit) {
    val vm: SnippetEditorViewModel = koinViewModel(key = "snippet-$id") { parametersOf(id) }
    val d by vm.draft.collectAsStateWithLifecycle()
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val back = { if (d.dirty) confirmLeave = true else onBack() }
    BackHandler(onBack = back)
    SnippetEditorContent(
        d,
        object : SnippetEditorActions {
            override fun edit(block: (SnippetDraft) -> SnippetDraft) = vm.edit(block)
            override fun back() = back()
            override fun save() = vm.save(onBack)
            override fun remove() = vm.remove(onBack)
            override fun toggleFavorite() = vm.toggleFavorite()
            override fun share(format: SnippetFormat?) = vm.share(format)
            override fun copyImage() = vm.copyImage()
        },
    )
    if (confirmLeave) {
        DiscardChangesDialog(
            "snippet ini",
            onSave = { vm.save(onBack); confirmLeave = false },
            onDiscard = { confirmLeave = false; onBack() },
            onDismiss = { confirmLeave = false },
        )
    }
}

/** Pilihan bagikan snippet: teks (Markdown berpagar), berkas kode sesuai bahasa, Markdown, gambar. */
fun snippetShareSections(language: String): List<ShareSection<SnippetFormat?>> = listOf(
    ShareSection(null, listOf(ShareOption(null, "Kirim sebagai teks", "Blok kode Markdown — rapi di Slack, Discord, GitHub", Icons.AutoMirrored.Outlined.Chat))),
    ShareSection(
        "Sebagai berkas",
        listOf(
            ShareOption(SnippetFormat.Source, "${SnippetFormat.Source.label} (.${SnippetExport.extension(language)})", SnippetFormat.Source.hint, Icons.Outlined.Description),
            ShareOption(SnippetFormat.Markdown, "${SnippetFormat.Markdown.label} (.md)", SnippetFormat.Markdown.hint, Icons.Outlined.Code),
            ShareOption(SnippetFormat.Image, "${SnippetFormat.Image.label} (.png)", SnippetFormat.Image.hint, Icons.Outlined.Image),
        ),
    ),
)

/**
 * Snippet yang sudah ada dibuka di mode **Lihat** (kode berwarna, siap salin/bagikan) dengan
 * tombol "Ubah"; snippet baru langsung di mode Tulis. [initialPreview] null = otomatis.
 */
@Composable
fun SnippetEditorContent(d: SnippetDraft, actions: SnippetEditorActions, initialPreview: Boolean? = null) {
    if (!d.loaded) {
        LoadingScreen(module = TaskifyModule.Vault)
        return
    }
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Vault)
    var preview by rememberSaveable { mutableStateOf(initialPreview ?: (d.id != null && d.code.isNotBlank())) }
    var langMenu by rememberSaveable { mutableStateOf(false) }
    var menu by rememberSaveable { mutableStateOf(false) }
    var sharing by rememberSaveable { mutableStateOf(false) }
    var template by rememberSaveable { mutableStateOf(false) }
    // Aksi bagikan yang menunggu konfirmasi karena kode tampak berisi rahasia.
    var pendingSecret by remember { mutableStateOf<(() -> Unit)?>(null) }
    val vars = templateVariables(d.code)
    val secret = looksSecret(d.code)
    val empty = d.code.isBlank()
    val guarded = { action: () -> Unit -> if (secret) pendingSecret = action else action() }
    val copy = { text: String, what: String ->
        copyToClipboard(context, d.title.ifBlank { "snippet" }, text, looksSecret(text))
        haptics.tick()
        confirmCopied(context, what)
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali") }
                Text(
                    when { d.id == null -> "Snippet baru"; preview -> "Snippet"; else -> "Ubah snippet" },
                    style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { copy(d.code, "Kode") }, enabled = !empty) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Salin kode") }
                IconButton(onClick = { sharing = true }, enabled = !empty) { Icon(Icons.Outlined.Share, contentDescription = "Bagikan") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Lainnya") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (vars.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Isi variabel & salin") },
                                leadingIcon = { Icon(Icons.Outlined.EditNote, null) },
                                onClick = { menu = false; template = true },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Salin sebagai Markdown") },
                            leadingIcon = { Icon(Icons.Outlined.Code, null) },
                            enabled = !empty,
                            onClick = { menu = false; copy(SnippetExport.markdown(d.title, d.language, d.code, d.description.ifBlank { null }).trimEnd(), "Markdown") },
                        )
                        DropdownMenuItem(
                            text = { Text("Salin sebagai gambar") },
                            leadingIcon = { Icon(Icons.Outlined.Image, null) },
                            enabled = !empty,
                            onClick = { menu = false; guarded { haptics.tick(); actions.copyImage() } },
                        )
                        if (d.id != null) {
                            DropdownMenuItem(
                                text = { Text(if (d.favorite) "Hapus dari favorit" else "Favoritkan") },
                                leadingIcon = { Icon(if (d.favorite) Icons.Rounded.Star else Icons.Outlined.StarOutline, null, tint = if (d.favorite) accent else LocalContentColor.current) },
                                onClick = { menu = false; haptics.toggle(!d.favorite); actions.toggleFavorite() },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Pindahkan ke Sampah") },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                onClick = { menu = false; actions.remove() },
                            )
                        }
                    }
                }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 760.dp).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IndexTab(TaskifyModule.Vault)
                if (preview) {
                    SnippetReadView(d, accent, vars)
                } else {
                    OutlinedTextField(d.title, { v -> actions.edit { it.copy(title = v) } }, label = { Text("Judul") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box {
                            AssistChip(onClick = { langMenu = true }, label = { Text(languageLabel(d.language)) })
                            DropdownMenu(expanded = langMenu, onDismissRequest = { langMenu = false }) {
                                LANGUAGES.forEach { l -> DropdownMenuItem(text = { Text(l.label) }, onClick = { actions.edit { it.copy(language = l.value) }; langMenu = false }) }
                            }
                        }
                        SingleChoiceSegmentedButtonRow {
                            listOf("Tulis" to false, "Lihat" to true).forEachIndexed { i, (label, v) ->
                                SegmentedButton(selected = preview == v, onClick = { preview = v }, shape = SegmentedButtonDefaults.itemShape(i, 2), icon = {}, label = { FitText(label) })
                            }
                        }
                    }
                    Surface(color = colors.surfaceContainerHigh, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                        BasicTextField(
                            value = d.code,
                            onValueChange = { v -> actions.edit { it.copy(code = v) } },
                            textStyle = TaskifyTextStyles.code.copy(color = colors.onSurface),
                            cursorBrush = SolidColor(accent),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp).horizontalScroll(rememberScrollState()).padding(12.dp).semantics { contentDescription = "Kode" },
                            decorationBox = { inner ->
                                if (d.code.isEmpty()) Text("// Tempel kode di sini\n// Pakai {{nama}} untuk variabel template", style = TaskifyTextStyles.code, color = colors.onSurfaceVariant)
                                inner()
                            },
                        )
                    }
                    if (vars.isNotEmpty()) Text("Variabel template: " + vars.joinToString { "{{$it}}" }, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    OutlinedTextField(d.description, { v -> actions.edit { it.copy(description = v) } }, label = { Text("Deskripsi (opsional)") }, modifier = Modifier.fillMaxWidth())
                }
                // Ruang untuk tombol mengambang "Ubah" di mode Lihat.
                Spacer(Modifier.height(if (preview) 88.dp else 12.dp))
            }
            // Bilah simpan: selalu di mode Tulis, dan di mode Lihat bila ada perubahan belum disimpan.
            if (!preview || d.dirty) {
                Surface(color = colors.surfaceContainer, modifier = Modifier.fillMaxWidth().imePadding()) {
                    Button(onClick = actions::save, enabled = d.title.isNotBlank() && d.code.isNotBlank(), modifier = Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp).fillMaxWidth()) {
                        Text(if (d.id == null) "Simpan snippet" else "Simpan perubahan")
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = preview,
            enter = scaleIn(initialScale = 0.8f) + fadeIn(),
            exit = scaleOut(targetScale = 0.8f) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 20.dp, bottom = if (d.dirty) 84.dp else 20.dp),
        ) {
            ExtendedFloatingActionButton(
                onClick = { preview = false },
                icon = { Icon(Icons.Outlined.Edit, null) },
                text = { Text("Ubah") },
                containerColor = TaskifyTheme.modules.tint(TaskifyModule.Vault, colors.surfaceContainerHigh),
                contentColor = colors.onSurface,
            )
        }
    }

    if (sharing) {
        ShareSheet("Bagikan snippet", TaskifyModule.Vault, snippetShareSections(d.language), onPick = { f -> sharing = false; guarded { actions.share(f) } }, onDismiss = { sharing = false })
    }
    if (template) TemplateDialog(d.code, onDismiss = { template = false }) { filled -> copy(filled, "Kode"); template = false }
    pendingSecret?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingSecret = null },
            icon = { Icon(Icons.Outlined.WarningAmber, null) },
            title = { Text("Kode tampak berisi rahasia") },
            text = { Text("Ada kata seperti token, password, atau API key di kode ini. Pastikan tidak ada kunci asli sebelum membagikannya ke luar Taskify.") },
            confirmButton = { TextButton(onClick = { pendingSecret = null; action() }) { Text("Tetap bagikan") } },
            dismissButton = { TextButton(onClick = { pendingSecret = null }) { Text("Batal") } },
        )
    }
}

/** Tampilan Lihat: judul, info bahasa & ukuran, deskripsi, kode berwarna bernomor baris. */
@Composable
private fun SnippetReadView(d: SnippetDraft, accent: Color, vars: List<String>) {
    val colors = MaterialTheme.colorScheme
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(d.title.ifBlank { "Tanpa judul" }, style = MaterialTheme.typography.headlineMedium.copy(fontFamily = SerifFamily))
            val lines = if (d.code.isEmpty()) 0 else d.code.trimEnd('\n').lines().size
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = TaskifyTheme.modules.tint(TaskifyModule.Vault, colors.surface), shape = MaterialTheme.shapes.small) {
                    Text(languageLabel(d.language), style = MaterialTheme.typography.labelLarge, color = accent, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
                Text("$lines baris · ${d.code.length} karakter", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                if (d.favorite) Icon(Icons.Rounded.Star, contentDescription = "Favorit", tint = accent, modifier = Modifier.size(18.dp))
            }
            d.description.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant) }
            CodeView(d.code.ifEmpty { " " }, d.language)
            if (vars.isNotEmpty()) Text("Variabel template: " + vars.joinToString { "{{$it}}" } + " — isi lewat menu ⋮ saat menyalin.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}
