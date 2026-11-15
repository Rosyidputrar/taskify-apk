package com.rosyidputrar.taskify.feature.journal

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.rounded.PushPin
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
import com.rosyidputrar.taskify.feature.journal.component.MoodSlider
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun JournalEditorScreen(args: JournalEditorArgs, onBack: () -> Unit) {
    val vm: JournalEditorViewModel = koinViewModel(key = "journal-editor-$args") { parametersOf(args) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val saveState by vm.saveState.collectAsStateWithLifecycle()
    val tags by vm.tagState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.flush() }
    val close = { vm.flush(); onBack() }
    BackHandler(onBack = close)
    JournalEditorContent(
        ui = ui,
        saveState = saveState,
        tags = tags.selected,
        allTags = tags.all,
        actions = object : JournalEditorActions {
            override fun back() = close()
            override fun retry() = vm.retry()
            override fun title(value: String) = vm.setTitle(value)
            override fun body(value: TextFieldValue) = vm.setBody(value)
            override fun mood(value: Int?) = vm.setMood(value)
            override fun date(value: String) = vm.setDate(value)
            override fun togglePin() = vm.togglePin()
            override fun remove() = vm.remove(onBack)
            override fun restore() = vm.restore()
            override fun addTag(name: String) = vm.addTag(name)
            override fun removeTag(tag: TagRef) = vm.removeTag(tag)
        },
    )
}

interface JournalEditorActions {
    fun back()
    fun retry()
    fun title(value: String)
    fun body(value: TextFieldValue)
    fun mood(value: Int?)
    fun date(value: String)
    fun togglePin()
    fun remove()
    fun restore()
    fun addTag(name: String)
    fun removeTag(tag: TagRef)
}

@Composable
fun JournalEditorContent(
    ui: JournalDraftUi,
    saveState: SaveState,
    tags: List<TagRef>,
    allTags: List<TagRef>,
    actions: JournalEditorActions,
    initialPreview: Boolean = false,
    today: String = Dates.today(),
) {
    if (!ui.loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (ui.missing) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            EditorTopBar(onBack = actions::back, saveState = SaveState.Idle, onRetry = {})
            EmptyState(TaskifyModule.Journal, "Entri tidak ditemukan", "Entri ini mungkin sudah dihapus permanen atau belum tersinkron ke perangkat ini.")
        }
        return
    }
    var preview by rememberSaveable { mutableStateOf(initialPreview) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var menu by rememberSaveable { mutableStateOf(false) }
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Journal)

    PaperEditor(
        module = TaskifyModule.Journal,
        title = ui.title,
        onTitleChange = actions::title,
        body = ui.body,
        onBodyChange = actions::body,
        bodyPlaceholder = "${ui.prompt}\n\nMarkdown didukung: **tebal**, _miring_, - daftar, - [ ] checklist",
        preview = preview,
        onPreviewChange = { preview = it },
        topBar = {
            EditorTopBar(
                onBack = actions::back,
                saveState = saveState,
                onRetry = actions::retry,
                leading = { DateChip(
                        Dates.format(ui.date, if (ui.date.take(4) == today.take(4)) "EEE, d MMM" else "d MMM yyyy"),
                        onClick = { picking = true },
                        module = TaskifyModule.Journal,
                        contentDescription = "Tanggal ${Dates.format(ui.date, "EEEE, d MMMM yyyy")}, ubah",
                    ) },
                actions = {
                    IconButton(onClick = actions::togglePin) {
                        Icon(
                            if (ui.pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (ui.pinned) "Lepas sematan" else "Sematkan",
                            tint = if (ui.pinned) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (ui.id != null && !ui.trashed) {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "Lainnya") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
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
                        Text("Entri ini ada di Sampah.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                        FilledTonalButton(onClick = actions::restore) {
                            Icon(Icons.Outlined.RestoreFromTrash, null)
                            Text("Pulihkan", Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        },
        beforeBody = { MoodSlider(ui.mood, actions::mood) },
        afterBody = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tag", style = MaterialTheme.typography.titleSmall)
                if (ui.id != null) {
                    TagPicker(tags, allTags, actions::addTag, actions::removeTag)
                } else {
                    Text("Tag bisa ditambahkan setelah entri tersimpan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
    )

    if (picking) {
        DatePickerSheet(ui.date, today, allowClear = false, onPick = { picked -> picked?.let(actions::date); picking = false }, onDismiss = { picking = false })
    }
}
