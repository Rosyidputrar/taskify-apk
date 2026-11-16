package com.rosyidputrar.taskify.feature.tasks

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Surface
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import com.rosyidputrar.taskify.core.designsystem.component.rememberNotificationPermission
import com.rosyidputrar.taskify.core.designsystem.component.rememberNotificationsAllowed
import com.rosyidputrar.taskify.core.reminders.ReminderNotifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rosyidputrar.taskify.core.designsystem.component.IndexTab
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.designsystem.theme.namedColor
import com.rosyidputrar.taskify.core.domain.tasks.PRIORITIES
import com.rosyidputrar.taskify.core.domain.tasks.ReminderPreset
import com.rosyidputrar.taskify.core.domain.tasks.Task
import com.rosyidputrar.taskify.core.domain.tasks.TaskStatus
import com.rosyidputrar.taskify.core.domain.tasks.computeRemindAt
import com.rosyidputrar.taskify.core.domain.tasks.describeRule
import com.rosyidputrar.taskify.core.domain.tasks.detectPreset
import com.rosyidputrar.taskify.core.domain.tasks.presetsFor
import com.rosyidputrar.taskify.core.domain.tasks.priorityLabel
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.ui.DatePickerSheet
import com.rosyidputrar.taskify.feature.tasks.component.ProgressRing
import com.rosyidputrar.taskify.feature.tasks.component.RecurrenceDialog
import com.rosyidputrar.taskify.core.ui.TagPicker
import com.rosyidputrar.taskify.core.domain.files.EntityType
import com.rosyidputrar.taskify.feature.attachments.AttachmentsSection
import com.rosyidputrar.taskify.feature.links.LinksSection
import com.rosyidputrar.taskify.feature.tasks.component.TaskCheck
import com.rosyidputrar.taskify.core.ui.TimePickerDialog
import com.rosyidputrar.taskify.feature.tasks.component.priorityColor
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Detail tugas sebagai sheet setinggi layar. Semua perubahan tersimpan langsung. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailSheet(taskId: String, today: String, onDismiss: () -> Unit) {
    val viewModel: TaskDetailViewModel = koinViewModel(key = "task-$taskId") { parametersOf(taskId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(state.task?.id, state.task?.title) {
        state.task?.takeIf { it.deletedAt == null }?.let { com.rosyidputrar.taskify.core.designsystem.component.RecentEntities.report("task", it.id, it.title) }
    }
    // Ketikan judul/catatan yang belum tersimpan ikut disimpan saat sheet ditutup dengan cara apa pun.
    val close = { viewModel.flushEdits(); onDismiss() }
    ModalBottomSheet(onDismissRequest = close, sheetState = sheet) {
        val task = state.task
        if (task == null) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Text(if (state.loaded) "Tugas tidak ditemukan." else "Memuat…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            TaskDetailContent(task, state, today, viewModel, close)
        }
    }
}

@Composable
private fun TaskDetailContent(task: Task, state: TaskDetailState, today: String, vm: TaskDetailViewModel, onClose: () -> Unit) {
    // Saveable: ketikan bertahan saat HP diputar / proses dibuat ulang.
    var title by rememberSaveable(task.id) { mutableStateOf(task.title) }
    var notes by rememberSaveable(task.id) { mutableStateOf(task.notes.orEmpty()) }
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    // Pengingat kustom = dialog dua langkah: tanggal lalu jam.
    var pendingReminderDate by rememberSaveable { mutableStateOf<String?>(null) }
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Tasks)
    val inTrash = task.deletedAt != null

    val notificationsAllowed by rememberNotificationsAllowed(ReminderNotifier.CHANNEL_ID)
    val requestNotifications = rememberNotificationPermission()
    fun setReminder(remindAt: String?) {
        vm.setRemindAt(remindAt)
        // Minta sekali saat pengingat dipasang; status & jalan keluarnya tetap tampil di bawah pengingat.
        if (remindAt != null && !notificationsAllowed) requestNotifications()
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IndexTab(TaskifyModule.Tasks, text = if (task.isRecurring) "Tugas berulang" else "Tugas")
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Tutup") }
        }
        if (inTrash) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tugas ini ada di Sampah.", color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                TextButton(onClick = vm::restore) { Text("Pulihkan") }
            }
        }
        Row(verticalAlignment = Alignment.Top) {
            TaskCheck(task, vm::toggleDone, Modifier.padding(top = 14.dp))
            Spacer(Modifier.width(8.dp))
            TextField(
                value = title,
                onValueChange = { title = it; vm.editTitle(it) },
                textStyle = MaterialTheme.typography.headlineSmall,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.rename(title) }),
                modifier = Modifier.weight(1f).onFocusChanged { if (!it.isFocused) vm.flushEdits() },
            )
        }

        // Jadwal & atribut sebagai baris chip.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MetaChip(Icons.Outlined.CalendarToday, task.dueDate?.let { Dates.relativeDayLabel(it, today) } ?: "Tanggal", task.dueDate != null) { dialog = "date" }
            if (task.dueDate != null) {
                MetaChip(Icons.Outlined.Schedule, Dates.shortTime(task.dueTime).ifEmpty { "Jam" }, task.dueTime != null) { dialog = "time" }
            }
            Box {
                MetaChip(Icons.Outlined.Flag, "Prioritas ${priorityLabel(task.priority).lowercase()}", task.priority > 0, tint = priorityColor(task.priority)) { dialog = "priority" }
                DropdownMenu(expanded = dialog == "priority", onDismissRequest = { dialog = null }) {
                    PRIORITIES.forEach { p ->
                        DropdownMenuItem(text = { Text(p.label) }, onClick = { vm.setPriority(p.value); dialog = null }, leadingIcon = { Icon(Icons.Outlined.Flag, null, tint = priorityColor(p.value)) })
                    }
                }
            }
            Box {
                val list = state.lists.firstOrNull { it.id == task.listId }
                MetaChip(Icons.AutoMirrored.Outlined.FormatListBulleted, list?.name ?: "Tanpa daftar", list != null, tint = list?.let { namedColor(it.color) }) { dialog = "list" }
                DropdownMenu(expanded = dialog == "list", onDismissRequest = { dialog = null }) {
                    DropdownMenuItem(text = { Text("Tanpa daftar") }, onClick = { vm.setList(null); dialog = null })
                    state.lists.forEach { l ->
                        DropdownMenuItem(text = { Text(l.name) }, onClick = { vm.setList(l.id); dialog = null })
                    }
                }
            }
            MetaChip(Icons.Outlined.Repeat, if (task.isRecurring) describeRule(task.rrule, task.dueDate) else "Ulangi", task.isRecurring) { dialog = "repeat" }
            Box {
                val preset = detectPreset(task.remindAt, task.dueDate, task.dueTime)
                val label = when {
                    task.remindAt == null -> "Pengingat"
                    preset == ReminderPreset.Custom -> Dates.isoToLocal(task.remindAt!!)?.let { (d, t) -> "${Dates.relativeDayLabel(d, today)} $t" } ?: "Pengingat"
                    else -> preset.label
                }
                MetaChip(Icons.Outlined.NotificationsNone, label, task.remindAt != null) { dialog = if (task.dueDate == null) "date" else "remind" }
                DropdownMenu(expanded = dialog == "remind", onDismissRequest = { dialog = null }) {
                    presetsFor(task.dueTime != null).forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.label) },
                            onClick = {
                                dialog = if (p == ReminderPreset.Custom) "remindDate" else null
                                if (p != ReminderPreset.Custom) setReminder(computeRemindAt(p, task.dueDate, task.dueTime))
                            },
                        )
                    }
                }
            }
        }
        if (task.remindAt != null && !inTrash && !notificationsAllowed) {
            ReminderBlockedNotice(onFix = requestNotifications)
        }

        // Subtask dengan progres.
        Section("Subtask") {
            val done = state.subtasks.count { it.status == TaskStatus.Done }
            if (state.subtasks.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(done, state.subtasks.size, accent, size = 28.dp, stroke = 3.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("$done dari ${state.subtasks.size} selesai", style = MaterialTheme.typography.bodyMedium)
                }
                LinearProgressIndicator(progress = { done.toFloat() / state.subtasks.size }, color = accent, modifier = Modifier.fillMaxWidth())
            }
            state.subtasks.forEach { sub ->
                var subTitle by remember(sub.id, sub.title) { mutableStateOf(sub.title) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TaskCheck(sub, { vm.toggleSubtask(sub) })
                    TextField(
                        value = subTitle,
                        onValueChange = { subTitle = it },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        keyboardActions = KeyboardActions(onDone = { vm.renameSubtask(sub, subTitle) }),
                        modifier = Modifier.weight(1f).onFocusChanged { if (!it.isFocused) vm.renameSubtask(sub, subTitle) },
                    )
                    IconButton(onClick = { vm.removeSubtask(sub) }) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Hapus subtask") }
                }
            }
            var newSub by rememberSaveable(task.id) { mutableStateOf("") }
            OutlinedTextField(
                value = newSub,
                onValueChange = { newSub = it },
                placeholder = { Text("Tambah subtask…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.addSubtask(newSub); newSub = "" }),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Section("Tag") { TagPicker(state.tags, state.allTags, vm::addTag, vm::removeTag) }
        AttachmentsSection(EntityType.Task, task.id)
        LinksSection(EntityType.Task, task.id)

        Section("Catatan") {
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it; vm.editNotes(it) },
                placeholder = { Text("Detail, tautan, atau langkah…") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) vm.flushEdits() },
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (task.status.isActive) OutlinedButton(onClick = vm::cancelTask) { Text("Batalkan tugas") }
            if (!inTrash) {
                OutlinedButton(onClick = { vm.delete(onClose) }) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    Text(" Hapus", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        Text(
            "Dibuat ${task.createdAt.take(10)}" + (task.completedAt?.let { " · selesai ${it.take(10)}" } ?: ""),
            style = TaskifyTextStyles.code,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    when (dialog) {
        "date" -> DatePickerSheet(task.dueDate, today, onPick = { vm.setSchedule(it, task.dueTime?.take(5)); dialog = null }, onDismiss = { dialog = null })
        "time" -> TimePickerDialog(task.dueTime, onPick = { vm.setSchedule(task.dueDate, it); dialog = null }, onDismiss = { dialog = null })
        "repeat" -> RecurrenceDialog(task.rrule, task.dueDate ?: today, today, onSave = { vm.setRecurrence(it); dialog = null }, onDismiss = { dialog = null })
        "remindDate" -> DatePickerSheet(task.dueDate, today, allowClear = false, onPick = { picked -> pendingReminderDate = picked; dialog = if (picked != null) "remindTime" else null }, onDismiss = { dialog = null })
        "remindTime" -> TimePickerDialog(null, onPick = { time ->
            val date = pendingReminderDate
            if (date != null && time != null) setReminder(Dates.localDateTimeToIso(date, time))
            dialog = null
        }, onDismiss = { dialog = null })
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), style = TaskifyTextStyles.indexLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun MetaChip(icon: ImageVector, label: String, active: Boolean, tint: Color? = null, onClick: () -> Unit) {
    val accent = tint ?: TaskifyTheme.modules.accent(TaskifyModule.Tasks)
    AssistChip(
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        colors = if (active) {
            AssistChipDefaults.assistChipColors(
                containerColor = accent.copy(alpha = 0.12f),
                labelColor = MaterialTheme.colorScheme.onSurface,
                leadingIconContentColor = accent,
            )
        } else {
            AssistChipDefaults.assistChipColors(leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant)
        },
    )
}

/** Pengingat terpasang tetapi notifikasi Taskify dimatikan → beri tahu dan beri jalan keluar. */
@Composable
internal fun ReminderBlockedNotice(onFix: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer, shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.NotificationsOff, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Notifikasi Taskify mati — pengingat ini tidak akan muncul.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onFix) { Text("Izinkan") }
        }
    }
}
