package com.rosyidputrar.taskify.feature.wellbeing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.designsystem.component.ConfirmDeleteDialog
import com.rosyidputrar.taskify.core.designsystem.component.IndexCard
import com.rosyidputrar.taskify.core.designsystem.component.rememberHaptics
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.device.DeviceAccess
import com.rosyidputrar.taskify.core.device.UsageSettings
import com.rosyidputrar.taskify.core.device.ui.AppIcon
import com.rosyidputrar.taskify.core.device.ui.rememberAppInfo
import com.rosyidputrar.taskify.core.domain.device.UsageMath
import com.rosyidputrar.taskify.core.domain.device.formatClockTime
import com.rosyidputrar.taskify.core.domain.device.formatDuration
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.store.device.AppUsage
import kotlinx.coroutines.flow.Flow

// ============================================================ detail aplikasi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppDetailSheet(
    app: AppUsage,
    state: WellbeingUiState,
    detailFlow: (pkg: String, label: String, day: String) -> Flow<AppDetail>,
    actions: WellbeingActions,
    onOpenNotifications: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val flow = remember(app.pkg, state.day) { detailFlow(app.pkg, app.label, state.day) }
    val detail by flow.collectAsState(AppDetail(app.pkg, app.label, state.day))
    val category = rememberAppInfo()?.category(app.pkg)
    var span by remember { mutableIntStateOf(7) }
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Wellbeing)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val excluded = app.pkg in state.settings.excluded
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app.pkg, app.label, size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(app.label, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                        Text(listOfNotNull(category, Dates.relativeDayLabel(state.day, state.today)).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                val today = detail.today
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("Waktu", formatDuration(today?.foregroundMs ?: 0), Modifier.weight(1f))
                    Stat("Dibuka", "${today?.opens ?: 0}×", Modifier.weight(1f))
                    Stat("Sesi terlama", formatDuration(detail.longestMs), Modifier.weight(1f))
                }
                detail.average7?.let { Text("Rata-rata 7 hari sebelumnya: ${formatDuration(it)} per hari", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)) }
            }
            item {
                Label("PER JAM")
                val window = UsageMath.window(state.day, state.settings.dayStartHour)
                val hourly = remember(detail.sessions) { UsageMath.hourly(detail.sessions, window) }
                val max = (hourly.maxOrNull() ?: 0).coerceAtLeast(60_000)
                Canvas(Modifier.fillMaxWidth().height(64.dp).semantics { contentDescription = "Pemakaian per jam" }) {
                    val slot = size.width / 24f
                    val w = slot * 0.62f
                    for (h in 0 until 24) {
                        val x = h * slot + (slot - w) / 2
                        drawRoundRect(track, Offset(x, size.height - 3.dp.toPx()), Size(w, 3.dp.toPx()), CornerRadius(2.dp.toPx()))
                        if (hourly[h] > 0) {
                            val bh = size.height * hourly[h] / max
                            drawRoundRect(accent, Offset(x, size.height - bh), Size(w, bh), CornerRadius(2.dp.toPx()))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    (0 until 4).forEach { i -> Text("%02d".format((state.settings.dayStartHour + i * 6) % 24), style = TaskifyTextStyles.code, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)) }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label("RIWAYAT", Modifier.weight(1f))
                    SingleChoiceSegmentedButtonRow {
                        listOf(7, 30).forEachIndexed { i, n ->
                            SegmentedButton(selected = span == n, onClick = { span = n }, shape = SegmentedButtonDefaults.itemShape(i, 2), label = { Text("$n hari") })
                        }
                    }
                }
                val days = (span - 1 downTo 0).map { Dates.addDays(state.day, -it.toLong()) }
                val byDay = detail.history.associateBy { it.day }
                val max = days.maxOf { byDay[it]?.foregroundMs ?: 0 }.coerceAtLeast(60_000)
                Canvas(Modifier.fillMaxWidth().height(96.dp).padding(top = 8.dp).semantics { contentDescription = "Pemakaian $span hari terakhir" }) {
                    val slot = size.width / days.size
                    val w = slot * 0.6f
                    days.forEachIndexed { i, d ->
                        val v = byDay[d]?.foregroundMs ?: 0
                        val x = i * slot + (slot - w) / 2
                        val h = size.height * v / max
                        drawRoundRect(if (d == state.day) accent else accent.copy(alpha = 0.55f), Offset(x, size.height - h), Size(w, h.coerceAtLeast(1f)), CornerRadius(2.dp.toPx()))
                    }
                }
            }
            item {
                Label("SESI · ${detail.sessions.size}")
            }
            items(detail.sessions.sortedByDescending { it.start }.take(30), key = { "s" + it.start }) { s ->
                Row {
                    Text("${formatClockTime(s.start)}–${formatClockTime(s.end)}", style = TaskifyTextStyles.code, modifier = Modifier.weight(1f))
                    Text(formatDuration(s.duration), style = MaterialTheme.typography.labelLarge)
                }
            }
            item {
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${detail.notifications} notifikasi ${Dates.relativeDayLabel(state.day, state.today).lowercase()}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onOpenNotifications(app.pkg) }) { Text("Lihat") }
                }
                Row(
                    Modifier.fillMaxWidth().toggleable(!excluded, role = Role.Switch) { actions.setExcluded(app.pkg, !it) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Hitung dalam waktu layar", style = MaterialTheme.typography.bodyLarge)
                        Text("Matikan untuk aplikasi yang tidak ingin kamu pantau (mis. navigasi).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = !excluded, onCheckedChange = null)
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    IndexCard(TaskifyModule.Wellbeing, modifier = modifier.fillMaxHeight(), contentPadding = 10.dp) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text, style = TaskifyTextStyles.indexLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(bottom = 4.dp))
}

// ============================================================ rincian satu jam

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HourSheet(slot: Int, state: WellbeingUiState, onOpenApp: (AppUsage) -> Unit, onDismiss: () -> Unit) {
    val window = UsageMath.window(state.day, state.settings.dayStartHour)
    val start = window.first + slot * UsageMath.HOUR
    val range = start until start + UsageMath.HOUR
    val perApp = UsageMath.perApp(UsageMath.clip(state.sessions, range))
    val labels = state.appsOn(state.day).associate { it.pkg to it.label }
    val notifs = state.notifTimes.count { it in range }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text("${formatClockTime(start)}–${formatClockTime(start + UsageMath.HOUR)}", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(
                "${formatDuration(perApp.sumOf { it.foregroundMs })} di layar · $notifs notifikasi",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            if (perApp.isEmpty()) Text("Tidak ada aplikasi yang dipakai di jam ini.", style = MaterialTheme.typography.bodyLarge)
            perApp.forEach { a ->
                val label = labels[a.pkg] ?: a.pkg
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenApp(AppUsage(a.pkg, label, a.foregroundMs, a.opens, a.longestMs)) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(a.pkg, label)
                    Spacer(Modifier.width(12.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatDuration(a.foregroundMs), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

// ============================================================ pengaturan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WellbeingSettingsSheet(state: WellbeingUiState, actions: WellbeingActions, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    var confirmClear by remember { mutableStateOf(false) }
    val s = state.settings
    val known = (state.appDays.values.flatten().groupBy { it.pkg }.map { (pkg, l) -> pkg to l.first().label } +
        s.excluded.map { it to it }).distinctBy { it.first }.sortedBy { it.second.lowercase() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Pengaturan Kesehatan Digital", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() }) }
            item {
                Label("AKSES")
                Text(if (state.access) "Aktif · data diambil dari sistem tiap jam." else "Belum diizinkan.", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { DeviceAccess.open(context, DeviceAccess.usageSettings(context), android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) { Text("Buka Akses penggunaan") }
            }
            item { HorizontalDivider() }
            item {
                Label("HARI BERGANTI JAM")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(UsageSettings.DAY_START_CHOICES) { h ->
                        FilterChip(selected = s.dayStartHour == h, onClick = { haptics.tick(); actions.setDayStart(h) }, label = { Text("%02d.00".format(h)) })
                    }
                }
                Text("Untuk yang sering begadang: pemakaian jam 01.00 tetap dihitung ke \"hari ini\". \"Pertama dipegang\" = buka kunci pertama setelah jam ini.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                Label("SIMPAN RIWAYAT SESI")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(UsageSettings.RETENTION_CHOICES) { d ->
                        FilterChip(selected = s.sessionRetentionDays == d, onClick = { haptics.tick(); actions.setSessionRetention(d) }, label = { Text("$d hari") })
                    }
                }
                Text("Rekap harian disimpan terus (ukurannya kecil).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item { HorizontalDivider() }
            item { Label("APLIKASI YANG DIHITUNG") }
            if (known.isEmpty()) item { Text("Belum ada aplikasi.", style = MaterialTheme.typography.bodyMedium) }
            items(known, key = { "k-" + it.first }) { (pkg, label) ->
                val on = pkg !in s.excluded
                Row(
                    Modifier.fillMaxWidth().toggleable(on, role = Role.Switch) { haptics.toggle(it); actions.setExcluded(pkg, !it) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(pkg, label, size = 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Switch(checked = on, onCheckedChange = null)
                }
            }
            item { HorizontalDivider() }
            item {
                Text(
                    "ⓘ Angka bisa berbeda beberapa menit dari Kesehatan Digital Google. Launcher dan antarmuka sistem tidak dihitung.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { confirmClear = true }) { Text("Hapus data Kesehatan Digital", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (confirmClear) {
        ConfirmDeleteDialog(
            title = "Hapus data pemakaian?",
            message = "Rekap & riwayat sesi di Taskify dihapus. Data seminggu terakhir yang masih disimpan sistem akan diimpor lagi saat disegarkan.",
            onConfirm = { confirmClear = false; actions.clearData() },
            onDismiss = { confirmClear = false },
        )
    }
}
