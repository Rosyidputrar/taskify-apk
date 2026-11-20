package com.rosyidputrar.taskify.ui.focus

import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.OutlinedButton
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rosyidputrar.taskify.core.designsystem.component.rememberHaptics
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.focus.FocusPhase
import com.rosyidputrar.taskify.core.domain.focus.FocusRun
import com.rosyidputrar.taskify.core.domain.focus.MINUTE
import com.rosyidputrar.taskify.core.domain.focus.PomodoroConfig
import com.rosyidputrar.taskify.core.domain.focus.formatClock
import com.rosyidputrar.taskify.core.domain.focus.formatMinutes
import com.rosyidputrar.taskify.core.domain.tasks.Task
import com.rosyidputrar.taskify.core.domain.tasks.TaskStatus
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.feature.tasks.component.TaskCheck
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun FocusScreen(
    taskId: String?,
    contentPadding: PaddingValues,
    backButton: @Composable (() -> Unit) -> Unit,
    onBack: () -> Unit,
    viewModel: FocusViewModel = koinViewModel { parametersOf(taskId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Kembali dari Setelan "Akses Jangan Ganggu" → baca ulang izinnya.
    LifecycleResumeEffect(Unit) {
        viewModel.recheckDnd()
        onPauseOrDispose { }
    }
    FocusContent(state, viewModel, contentPadding, backButton = { backButton(onBack) },
        musicSlot = { com.rosyidputrar.taskify.feature.media.FocusMusicCard(Modifier.padding(top = 24.dp, bottom = 12.dp)) })
}

/** Layar Mode Fokus (tanpa ViewModel — bisa diuji). [now] untuk pratinjau/tes; null = jam berjalan. */
@Composable
fun FocusContent(
    state: FocusUiState,
    actions: FocusActions,
    contentPadding: PaddingValues,
    backButton: @Composable () -> Unit = {},
    now: Long? = null,
    /** Pilihan "Musik fokus" (stasiun radio langsung) dari modul Musik. */
    musicSlot: @Composable () -> Unit = {},
) {
    val run = state.run
    val running = run != null && !run.isPaused
    val clock by produceState(now ?: System.currentTimeMillis(), now, running) {
        if (now != null) { value = now; return@produceState }
        value = System.currentTimeMillis()
        while (running) {
            delay(1000 - value % 1000)
            value = System.currentTimeMillis()
        }
    }
    // Layar tetap menyala selama hitung mundur berjalan.
    val view = LocalView.current
    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            backButton()
            Spacer(Modifier.width(4.dp))
            Text("MODE FOKUS", style = TaskifyTextStyles.indexLabel, color = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth()) {
            if (run == null) FocusSetup(state, actions) else FocusRunning(state, run, clock, actions)
            musicSlot()
        }
    }
}

@Composable
private fun FocusSetup(state: FocusUiState, actions: FocusActions) {
    val haptics = rememberHaptics()
    val context = LocalContext.current
    Spacer(Modifier.height(8.dp))
    Text("Mau fokus ke apa?", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    Text(
        "Satu hal saja. Ponsel menghitung mundur, kamu cukup mengerjakan.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    val options = remember(state.candidates, state.task) {
        buildList {
            state.task?.takeIf { t -> state.candidates.none { it.id == t.id } }?.let(::add)
            addAll(state.candidates)
        }
    }
    Column(Modifier.selectableGroup()) {
        ChoiceRow(label = "Fokus bebas", hint = "tanpa tugas tertentu", selected = state.selectedTaskId == null, onSelect = { actions.select(null) })
        options.forEach { task ->
            ChoiceRow(
                label = task.title,
                hint = task.dueDate?.let { if (it < Dates.today()) "terlambat" else "hari ini" },
                selected = state.selectedTaskId == task.id,
                onSelect = { actions.select(task.id) },
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    Text("DURASI FOKUS", style = TaskifyTextStyles.indexLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PomodoroConfig.FocusChoices.forEach { m ->
            FilterChip(
                selected = state.config.focusMinutes == m,
                onClick = { haptics.tick(); actions.setFocusMinutes(m) },
                label = { Text("$m mnt") },
            )
        }
    }
    Text(
        "Istirahat ${state.config.shortBreakMinutes} mnt; tiap ${state.config.roundsBeforeLong} putaran istirahat panjang ${state.config.longBreakMinutes} mnt.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    Row(
        Modifier.fillMaxWidth().toggleable(state.dnd, role = Role.Switch) { haptics.toggle(it); actions.setDnd(it) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Senyapkan notifikasi", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Menyalakan Jangan Ganggu selama fokus, mati lagi saat istirahat.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = state.dnd, onCheckedChange = null)
    }
    if (state.dnd && !state.dndGranted) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Taskify belum diberi akses Jangan Ganggu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }) { Text("Beri akses") }
        }
    }
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { haptics.success(); actions.start() },
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Mulai fokus ${state.config.focusMinutes} menit")
    }
    Spacer(Modifier.height(12.dp))
    TodayLine(state)
}

@Composable
private fun ChoiceRow(label: String, hint: String?, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).selectable(selected, role = Role.RadioButton, onClick = onSelect).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        hint?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun TodayLine(state: FocusUiState) {
    val s = state.today
    Text(
        if (s.sessions == 0) "Belum ada sesi fokus hari ini." else "Hari ini: ${formatMinutes(s.minutes)} fokus · ${s.sessions} sesi",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FocusRunning(state: FocusUiState, run: FocusRun, now: Long, actions: FocusActions) {
    val haptics = rememberHaptics()
    val isFocus = run.phase == FocusPhase.Focus
    val accent = if (isFocus) TaskifyTheme.modules.accent(TaskifyModule.Tasks) else MaterialTheme.colorScheme.tertiary
    val waiting = run.isPaused && isFocus && run.elapsed(now) == 0L
    val remaining = run.remaining(now)
    Spacer(Modifier.height(8.dp))
    Text(
        (if (isFocus) "FOKUS · PUTARAN ${run.round}" else run.phase.label.uppercase()),
        style = TaskifyTextStyles.indexLabel,
        color = accent,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
    Text(
        when {
            !isFocus -> "Jauhkan layar sejenak. Tarik napas, minum air."
            else -> state.task?.title ?: run.taskTitle ?: "Fokus bebas"
        },
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).semantics { heading() },
    )
    Spacer(Modifier.height(20.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        FocusRing(
            progress = run.progress(now),
            color = accent,
            time = formatClock(remaining),
            caption = when {
                waiting -> "siap"
                run.isPaused -> "dijeda"
                else -> "selesai ${clockOf(run.endsAt(now))}"
            },
            description = "${run.phase.label}, sisa ${(remaining + MINUTE - 1) / MINUTE} menit" + if (run.isPaused) ", dijeda" else "",
        )
    }
    Spacer(Modifier.height(12.dp))
    RoundDots(run, accent)
    Spacer(Modifier.height(20.dp))
    if (waiting) {
        Button(onClick = { haptics.success(); actions.resume() }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Mulai putaran ${run.round}")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { haptics.tap(); actions.stop() }, modifier = Modifier.fillMaxWidth()) { Text("Cukup untuk sekarang") }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            OutlinedIconButton(onClick = { haptics.tap(); actions.stop() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.Stop, contentDescription = "Berhenti")
            }
            FilledIconButton(
                onClick = { haptics.tap(); if (run.isPaused) actions.resume() else actions.pause() },
                modifier = Modifier.size(80.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent, contentColor = MaterialTheme.colorScheme.surface),
            ) {
                Icon(
                    if (run.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    contentDescription = if (run.isPaused) "Lanjutkan" else "Jeda",
                    modifier = Modifier.size(36.dp),
                )
            }
            OutlinedIconButton(onClick = { haptics.tick(); actions.skip() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipNext, contentDescription = if (isFocus) "Lewati ke istirahat" else "Lewati istirahat")
            }
        }
    }
    val task = state.task
    if (task != null && task.status != TaskStatus.Done) {
        Spacer(Modifier.height(12.dp))
        // Tombol bergaris + ikon centang + kata kerja: jelas sebuah AKSI ("tandai"), bukan
        // keterangan status — teks "Tugas ini sudah selesai" dulu terbaca seperti label.
        OutlinedButton(onClick = { haptics.success(); actions.completeTask() }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Tandai tugas selesai")
        }
    }
    if (state.subtasks.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        val done = state.subtasks.count { it.status == TaskStatus.Done }
        Text("LANGKAH · $done/${state.subtasks.size}", style = TaskifyTextStyles.indexLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.subtasks.forEach { sub ->
            Row(
                Modifier.fillMaxWidth().clickable { actions.toggle(sub) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TaskCheck(sub, { actions.toggle(sub) })
                Spacer(Modifier.width(12.dp))
                Text(
                    sub.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (sub.status == TaskStatus.Done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (sub.status == TaskStatus.Done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                )
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    TodayLine(state)
}

@Composable
private fun FocusRing(progress: Float, color: Color, time: String, caption: String, description: String) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val animated by animateFloatAsState(progress, label = "focus-progress")
    Box(Modifier.size(260.dp).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
            // Sisa waktu = busur yang MENYUSUT searah jarum jam dari atas.
            drawArc(color, -90f + 360f * animated, 360f * (1f - animated), useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clearAndSetSemantics {}) {
            Text(time, style = MaterialTheme.typography.displayLarge)
            Text(caption, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Titik putaran dalam satu siklus (sampai istirahat panjang). */
@Composable
private fun RoundDots(run: FocusRun, accent: Color) {
    val per = run.config.roundsBeforeLong
    val inCycle = ((run.round - 1) % per) + 1
    // Putaran yang sudah tuntas: sebelum putaran ini, plus putaran ini bila sedang istirahat.
    val completed = if (run.phase == FocusPhase.Focus) inCycle - 1 else inCycle
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$completed dari $per putaran sebelum istirahat panjang" },
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(per) { i ->
            Box(
                Modifier.padding(horizontal = 4.dp).size(10.dp).clip(CircleShape)
                    .background(if (i < completed) accent else MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        }
    }
}

private fun clockOf(millis: Long): String {
    val t = java.time.Instant.ofEpochMilli(millis).atOffset(Dates.WIB).toLocalTime()
    return "%02d.%02d".format(t.hour, t.minute)
}
