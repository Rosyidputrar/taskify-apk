package com.rosyidputrar.taskify.feature.notes

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import com.rosyidputrar.taskify.core.designsystem.component.rememberHaptics
import com.rosyidputrar.taskify.core.domain.text.DocExport
import com.rosyidputrar.taskify.core.domain.text.ShareFormat
import com.rosyidputrar.taskify.core.ui.share.ShareSheet
import com.rosyidputrar.taskify.core.ui.share.confirmCopied
import com.rosyidputrar.taskify.core.ui.share.docShareSections
import androidx.compose.material.icons.outlined.Image
import androidx.compose.runtime.LaunchedEffect
import com.rosyidputrar.taskify.core.designsystem.component.LoadingScreen
import com.rosyidputrar.taskify.feature.links.LinksSection
import com.rosyidputrar.taskify.core.domain.files.EntityType
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rosyidputrar.taskify.core.designsystem.component.EmptyState
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.editor.SaveState
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.store.tasks.TagRef
import com.rosyidputrar.taskify.core.ui.DatePickerSheet
import com.rosyidputrar.taskify.core.ui.TagPicker
import com.rosyidputrar.taskify.core.ui.markdown.DateChip
import com.rosyidputrar.taskify.core.ui.markdown.EditorTopBar
import com.rosyidputrar.taskify.core.ui.markdown.PaperEditor
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun NoteEditorScreen(args: NoteEditorArgs, onBack: () -> Unit) {
    val vm: NoteEditorViewModel = koinViewModel(key = "note-editor-$args") { parametersOf(args) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    // Item yang dibuka → shortcut launcher "terakhir dibuka" (aplikasi yang memutuskan boleh/tidak).
    LaunchedEffect(ui.id, ui.loaded, ui.title) {
        val id = ui.id
        if (ui.loaded && id != null && !ui.trashed) com.rosyidputrar.taskify.core.designsystem.component.RecentEntities.report("note", id, ui.title)
    }
    val saveState by vm.saveState.collectAsStateWithLifecycle()
    val tags by vm.tagState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.flush() }
    val close = { vm.flush(); onBack() }
    BackHandler(onBack = close)
    NoteEditorContent(
        ui, saveState, tags.selected, tags.all,
        object : NoteEditorActions {
            override fun back() = close()
            override fun retry() = vm.retry()
            override fun title(value: String) = vm.setTitle(value)
            override fun body(value: TextFieldValue) = vm.setBody(value)
            override fun date(value: String?) = vm.setDate(value)
            override fun togglePin() = vm.togglePin()
            override fun remove() = vm.remove(onBack)
            override fun restore() = vm.restore()
            override fun addTag(name: String) = vm.addTag(name)
            override fun removeTag(tag: TagRef) = vm.removeTag(tag)
            override fun share(format: ShareFormat?) = vm.share(format)
            override fun copyImage() = vm.copyImage()
        },
        links = { id, readOnly -> LinksSection(EntityType.Note, id, readOnly = readOnly) },
    )
}

interface NoteEditorActions {
    fun back()
    fun retry()
    fun title(value: String)
    fun body(value: TextFieldValue)
    fun date(value: String?)
    fun togglePin()
    fun remove()
    fun restore()
    fun addTag(name: String)
    fun removeTag(tag: TagRef)
    /** Bagikan catatan: [format] null = kirim sebagai teks. */
    fun share(format: ShareFormat?) {}
    /** Salin catatan sebagai gambar. */
    fun copyImage() {}
}

@Composable
fun NoteEditorContent(
    ui: NoteDraftUi,
    saveState: SaveState,
    tags: List<TagRef>,
    allTags: List<TagRef>,
    actions: NoteEditorActions,
    /** null = otomatis: catatan yang sudah ada & berisi dibuka di mode Baca, catatan baru di Tulis. */
    initialPreview: Boolean? = null,
    today: String = Dates.today(),
    links: @Composable (noteId: String, readOnly: Boolean) -> Unit = { _, _ -> },
) {
    if (!ui.loaded) {
        LoadingScreen(module = com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule.Notes)
        return
    }
    if (ui.missing) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            EditorTopBar(onBack = actions::back, saveState = SaveState.Idle, onRetry = {})
            EmptyState(TaskifyModule.Notes, "Catatan tidak ditemukan", "Catatan ini mungkin sudah dihapus permanen atau belum tersinkron ke perangkat ini.")
        }
        return
    }
    var preview by rememberSaveable { mutableStateOf(initialPreview ?: (ui.id != null && ui.body.text.isNotBlank())) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var menu by rememberSaveable { mutableStateOf(false) }
    var sharing by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val copy = { text: String, what: String ->
        clipboard.setText(AnnotatedString(text))
        haptics.tick()
        confirmCopied(context, what)
    }
    val doc = ui.toShare()
    val empty = ui.title.isBlank() && ui.body.text.isBlank()
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Notes)

    Box(Modifier.fillMaxSize()) {
    PaperEditor(
        module = TaskifyModule.Notes,
        title = ui.title,
        onTitleChange = actions::title,
        body = ui.body,
        onBodyChange = actions::body,
        bodyPlaceholder = "Tulis apa saja…\n\nMarkdown didukung: ## judul, **tebal**, - [ ] checklist, [tautan](https://…)",
        preview = preview,
        onPreviewChange = { preview = it },
        topBar = {
            EditorTopBar(
                onBack = actions::back,
                saveState = saveState,
                onRetry = actions::retry,
                leading = {
                    val date = ui.date
                    if (date != null) {
                        DateChip(Dates.relativeDayLabel(date, today), onClick = { picking = true }, module = TaskifyModule.Notes, contentDescription = "Ditempel ke ${Dates.format(date, "d MMMM yyyy")}, ubah tanggal")
                    } else {
                        AssistChip(
                            onClick = { picking = true },
                            label = { Text("Tempel ke tanggal") },
                            leadingIcon = { Icon(Icons.Outlined.CalendarToday, null, Modifier.size(16.dp)) },
                            colors = AssistChipDefaults.assistChipColors(leadingIconContentColor = accent),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { sharing = true }, enabled = !empty) {
                        Icon(Icons.Outlined.Share, contentDescription = "Bagikan")
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Lainnya") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (ui.pinned) "Lepas sematan" else "Sematkan") },
                                leadingIcon = { Icon(if (ui.pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin, null, tint = if (ui.pinned) accent else LocalContentColor.current) },
                                onClick = { menu = false; actions.togglePin() },
                            )
                            DropdownMenuItem(
                                text = { Text("Salin teks") },
                                leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                                enabled = !empty,
                                onClick = { menu = false; copy(DocExport.plainText(doc.title, doc.markdown, doc.meta).trimEnd(), "Teks catatan") },
                            )
                            DropdownMenuItem(
                                text = { Text("Salin sebagai Markdown") },
                                leadingIcon = { Icon(Icons.Outlined.Code, null) },
                                enabled = !empty,
                                onClick = { menu = false; copy(DocExport.markdown(doc.title, doc.markdown, doc.meta).trimEnd(), "Markdown") },
                            )
                            DropdownMenuItem(
                                text = { Text("Salin sebagai gambar") },
                                leadingIcon = { Icon(Icons.Outlined.Image, null) },
                                enabled = !empty,
                                onClick = { menu = false; haptics.tick(); actions.copyImage() },
                            )
                            HorizontalDivider()
                            if (ui.date != null) {
                                DropdownMenuItem(
                                    text = { Text("Lepas dari tanggal") },
                                    leadingIcon = { Icon(Icons.Outlined.EventBusy, null) },
                                    onClick = { menu = false; actions.date(null) },
                                )
                            }
                            if (ui.id != null && !ui.trashed) {
                                DropdownMenuItem(
                                    text = { Text("Pindahkan ke Sampah") },
                                    leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                    onClick = { menu = false; actions.remove() },
                                )
                            }
                        }
                    }
                },
            )
        },
        banner = {
            if (ui.trashed) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Catatan ini ada di Sampah.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                        FilledTonalButton(onClick = actions::restore) {
                            Icon(Icons.Outlined.RestoreFromTrash, null)
                            Text("Pulihkan", Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        },
        beforeBody = {
            Text(
                if (ui.date != null) "Ditempel ke ${Dates.format(ui.date, "EEEE, d MMMM yyyy")} — muncul di Kalender." else "Catatan bebas — tidak terikat tanggal.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        afterBody = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Mode Baca tanpa tag: judul "Tag" tidak ditampilkan sendirian (tampak seperti bagian kosong).
                if (!preview || tags.isNotEmpty()) Text("Tag", style = MaterialTheme.typography.titleSmall)
                if (ui.id != null) {
                    TagPicker(tags, allTags, actions::addTag, actions::removeTag, enabled = !preview)
                } else {
                    Text("Tag bisa ditambahkan setelah catatan tersimpan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ui.id?.let { links(it, preview) }
            }
        },
    )

    // Mode Baca: tombol Ubah selalu terjangkau walau isi sudah digulir jauh.
    AnimatedVisibility(
        visible = preview && !ui.trashed,
        enter = scaleIn(initialScale = 0.8f) + fadeIn(),
        exit = scaleOut(targetScale = 0.8f) + fadeOut(),
        modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
    ) {
        ExtendedFloatingActionButton(
            onClick = { preview = false },
            icon = { Icon(Icons.Outlined.Edit, null) },
            text = { Text("Ubah") },
            containerColor = TaskifyTheme.modules.tint(TaskifyModule.Notes, MaterialTheme.colorScheme.surfaceContainerHigh),
            contentColor = MaterialTheme.colorScheme.onSurface,
        )
    }
    }

    if (sharing) {
        ShareSheet("Bagikan catatan", TaskifyModule.Notes, docShareSections(), onPick = { format -> sharing = false; actions.share(format) }, onDismiss = { sharing = false })
    }
    if (picking) {
        DatePickerSheet(ui.date, today, onPick = { picked -> actions.date(picked); picking = false }, onDismiss = { picking = false })
    }
}
