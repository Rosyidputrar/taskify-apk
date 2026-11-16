package com.rosyidputrar.taskify.feature.wellbeing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rosyidputrar.taskify.core.designsystem.component.EmptyState
import com.rosyidputrar.taskify.core.designsystem.component.IndexCard
import com.rosyidputrar.taskify.core.designsystem.component.ListSkeleton
import com.rosyidputrar.taskify.core.designsystem.component.ModuleHeader
import com.rosyidputrar.taskify.core.designsystem.component.TaskifyLoadingIndicator
import com.rosyidputrar.taskify.core.designsystem.component.rememberHaptics
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.device.DeviceAccess
import com.rosyidputrar.taskify.core.device.ui.AppIcon
import com.rosyidputrar.taskify.core.device.ui.appColor
import com.rosyidputrar.taskify.core.domain.device.UsageMath
import com.rosyidputrar.taskify.core.domain.device.formatClockTime
import com.rosyidputrar.taskify.core.domain.device.formatDuration
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.store.device.AppUsage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.abs

@Composable
fun WellbeingScreen(
    contentPadding: PaddingValues,
    onOpenNotifications: (pkg: String?) -> Unit,
    onBack: (() -> Unit)? = null,
    backButton: @Composable (() -> Unit) -> Unit = {},
    viewModel: WellbeingViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    WellbeingContent(state, viewModel, contentPadding, onOpenNotifications, onBack, backButton, detail = viewModel::detail)
}

@Composable
fun WellbeingContent(
    state: WellbeingUiState,
    actions: WellbeingActions,
    contentPadding: PaddingValues,
    onOpenNotifications: (pkg: String?) -> Unit = {},
    onBack: (() -> Unit)? = null,
    backButton: @Composable (() -> Unit) -> Unit = {},
    detail: (pkg: String, label: String, day: String) -> Flow<AppDetail> = { _, _, _ -> emptyFlow() },
) {
    var openApp by remember { mutableStateOf<AppUsage?>(null) }
    var openHour by remember { mutableStateOf<Int?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "header") {
            ModuleHeader(
                TaskifyModule.Wellbeing, "Kesehatan Digital",
                subtitle = "Ke mana waktumu di HP — tersimpan hanya di HP ini",
                navigation = onBack?.let { { backButton(it) } },
                actions = {
                    if (state.access) {
                        if (state.syncing) TaskifyLoadingIndicator(size = 24.dp, module = TaskifyModule.Wellbeing, modifier = Modifier.padding(12.dp))
                        else IconButton(onClick = actions::refresh) { Icon(Icons.Outlined.Refresh, contentDescription = "Segarkan data") }
                    }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Outlined.Settings, contentDescription = "Pengaturan Kesehatan Digital") }
                },
            )
        }
        if (!state.loaded) {
            item(key = "skeleton") { ListSkeleton(TaskifyModule.Wellbeing, rows = 4, lines = 2) }
            return@LazyColumn
        }
        if (!state.access) {
            item(key = "onboarding") { WellbeingOnboarding() }
            return@LazyColumn
        }
        item(key = "days") { DayRibbon(state, actions) }
        item(key = "range") { RangeSwitch(state, actions) }
        if (!state.hasData) {
            item(key = "empty") {
                EmptyState(
                    TaskifyModule.Wellbeing,
                    title = if (state.syncing) "Mengambil data pemakaian…" else "Belum ada data",
                    message = "Data waktu layar muncul setelah sistem mencatat pemakaian. Coba segarkan sebentar lagi.",
                )
            }
            return@LazyColumn
        }
        if (state.range == WbRange.Day) {
            item(key = "hero") { DayHero(state) }
            item(key = "tiles") { StatTiles(state, onOpenNotifications) }
            item(key = "strip") { DayStrip(state, onHour = { openHour = it }) }
        } else {
            item(key = "week") { WeekHero(state, actions) }
            item(key = "week-trend") { WeekTrends(state) }
        }
        appList(state, onOpen = { openApp = it })
        item(key = "note") {
            Text(
                "ⓘ Angka bisa berbeda beberapa menit dari Kesehatan Digital Google karena cara menghitungnya sedikit berbeda.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
    openApp?.let { app ->
        AppDetailSheet(app, state, detail, actions, onOpenNotifications = { onOpenNotifications(it); openApp = null }, onDismiss = { openApp = null })
    }
    openHour?.let { h -> HourSheet(h, state, onOpenApp = { openHour = null; openApp = it }, onDismiss = { openHour = null }) }
    if (showSettings) WellbeingSettingsSheet(state, actions, onDismiss = { showSettings = false })
}

@Composable
private fun WellbeingOnboarding() {
    val context = LocalContext.current
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Wellbeing)
    IndexCard(TaskifyModule.Wellbeing, tinted = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("MULAI", style = TaskifyTextStyles.indexLabel, color = accent)
        Spacer(Modifier.height(6.dp))
        Text("Lihat ke mana waktumu di HP", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(6.dp))
        Text(
            "Waktu layar per hari & per aplikasi, berapa kali HP dibuka, jam pertama dipegang, dan aplikasi apa yang dipakai di jam berapa. " +
                "Data diambil dari sistem Android (izin Akses penggunaan), disimpan hanya di HP ini.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = { DeviceAccess.open(context, DeviceAccess.usageSettings(context), android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) {
            Text("Buka Akses penggunaan")
        }
        Text(
            "Nyalakan Taskify di daftar, lalu kembali ke sini. Data seminggu terakhir yang masih disimpan sistem langsung diimpor.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun DayRibbon(state: WellbeingUiState, actions: WellbeingActions) {
    val haptics = rememberHaptics()
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Wellbeing)
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), reverseLayout = true, modifier = Modifier.padding(vertical = 6.dp)) {
        items(state.days, key = { it }) { day ->
            val selected = day == state.day
            Surface(
                onClick = { haptics.tick(); actions.selectDay(day) },
                shape = MaterialTheme.shapes.medium,
                color = if (selected) accent else MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = if (selected) TaskifyTheme.modules.onAccent(TaskifyModule.Wellbeing) else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics {
                    contentDescription = Dates.format(day, "EEEE, d MMMM") + ", " + formatDuration(state.screenOn(day)) + if (selected) ", dipilih" else ""
                },
            ) {
                Column(Modifier.width(56.dp).padding(vertical = 8.dp).clearAndSetSemantics {}, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (day == state.today) "Ini" else Dates.format(day, "EEE"), style = MaterialTheme.typography.labelMedium)
                    Text(Dates.parse(day).dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium)
                    val ms = state.screenOn(day)
                    Text(if (ms > 0) shortHours(ms) else "–", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** "4,2j" untuk pita hari. */
private fun shortHours(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes < 60) "${minutes}m" else "%.1fj".format(minutes / 60.0).replace('.', ',')
}

@Composable
private fun RangeSwitch(state: WellbeingUiState, actions: WellbeingActions) {
    val haptics = rememberHaptics()
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        WbRange.entries.forEachIndexed { i, r ->
            SegmentedButton(
                selected = state.range == r,
                onClick = { haptics.tick(); actions.setRange(r) },
                shape = SegmentedButtonDefaults.itemShape(i, WbRange.entries.size),
                label = { Text(r.label) },
            )
        }
    }
}

// ============================================================ hari

@Composable
private fun DayHero(state: WellbeingUiState) {
    val apps = state.apps
    val rank = state.colorRank
    val total = state.screenMs
    val colors = (0 until WellbeingUiState.TOP_COLORED).map { appColor(it) }
    val other = MaterialTheme.colorScheme.outlineVariant
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    IndexCard(TaskifyModule.Wellbeing, tinted = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (state.day == state.today) "WAKTU LAYAR HARI INI" else "WAKTU LAYAR · " + Dates.relativeDayLabel(state.day, state.today).uppercase(),
                    style = TaskifyTextStyles.indexLabel,
                    color = TaskifyTheme.modules.accent(TaskifyModule.Wellbeing),
                )
                Text(formatDuration(total), style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
                state.average7?.let { avg ->
                    val diff = total - avg
                    Text(
                        when {
                            abs(diff) < 60_000 -> "sama dengan rata-rata 7 hari"
                            diff > 0 -> "↑ ${formatDuration(diff)} dari rata-rata 7 hari"
                            else -> "↓ ${formatDuration(-diff)} dari rata-rata 7 hari"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.day == state.today) {
                    Text("sampai ${formatClockTime(state.now)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Canvas(
                Modifier.size(104.dp).semantics {
                    contentDescription = "Pembagian waktu: " + apps.take(4).joinToString { "${it.label} ${formatDuration(it.foregroundMs)}" }
                },
            ) {
                val stroke = 14.dp.toPx()
                val inset = stroke / 2
                val arc = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
                if (total > 0) {
                    var start = -90f
                    val parts = apps.take(WellbeingUiState.TOP_COLORED).map { colors[rank[it.pkg] ?: 0] to it.foregroundMs } +
                        listOf(other to apps.drop(WellbeingUiState.TOP_COLORED).sumOf { it.foregroundMs })
                    parts.filter { it.second > 0 }.forEach { (c, ms) ->
                        val sweep = 360f * ms / total
                        drawArc(c, start + 1f, (sweep - 2f).coerceAtLeast(0.5f), false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Butt))
                        start += sweep
                    }
                }
            }
        }
        if (apps.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            // Tiga per baris supaya nama aplikasi tidak terpotong.
            val legend = apps.take(WellbeingUiState.TOP_COLORED).map { colors[rank[it.pkg] ?: 0] to it.label } +
                (if (apps.size > WellbeingUiState.TOP_COLORED) listOf(other to "lainnya") else emptyList())
            legend.chunked(3).forEach { line ->
                Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    line.forEach { (c, label) -> Legend(c, label) }
                }
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(8.dp)) { drawCircle(color) }
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 96.dp))
    }
}

@Composable
private fun StatTiles(state: WellbeingUiState, onOpenNotifications: (String?) -> Unit) {
    val d = state.device
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Tile(
            "Buka kunci", "${d?.unlocks ?: 0}×",
            state.unlockInterval?.let { "tiap ±${formatDuration(it)}" } ?: "—",
            Modifier.weight(1f),
        )
        Tile(
            "Pertama dipegang", d?.firstPickup?.let(::formatClockTime) ?: "—",
            state.usualPickupMinute?.let { "biasanya %02d.%02d".format(it / 60, it % 60) } ?: "belum ada pola",
            Modifier.weight(1f),
        )
        Tile("Notifikasi", "${state.notifCount}", "→ buka", Modifier.weight(1f), onClick = { onOpenNotifications(null) })
    }
}

@Composable
private fun Tile(label: String, value: String, hint: String, modifier: Modifier, onClick: (() -> Unit)? = null) {
    IndexCard(
        TaskifyModule.Wellbeing, onClick = onClick, onClickLabel = onClick?.let { "Buka $label" },
        modifier = modifier.fillMaxHeight(), contentPadding = 12.dp,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}

/** 24 jam dalam satu pita: blok = aplikasi yang dipakai, titik = notifikasi. Ketuk jam → rinciannya. */
@Composable
private fun DayStrip(state: WellbeingUiState, onHour: (Int) -> Unit) {
    val window = UsageMath.window(state.day, state.settings.dayStartHour)
    val rank = state.colorRank
    val colors = (0 until WellbeingUiState.TOP_COLORED).map { appColor(it) }
    val other = MaterialTheme.colorScheme.outline
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val dot = TaskifyTheme.modules.accent(TaskifyModule.Notifications)
    val hourly = remember(state.sessions, state.day) { UsageMath.hourly(state.sessions, window) }
    val busiest = hourly.indices.maxByOrNull { hourly[it] }?.takeIf { hourly[it] > 0 }
    IndexCard(TaskifyModule.Wellbeing, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text("SEPANJANG HARI", style = TaskifyTextStyles.indexLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Ketuk jam untuk melihat aplikasi yang dipakai.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Canvas(
            Modifier.fillMaxWidth().height(56.dp)
                .semantics {
                    contentDescription = busiest?.let {
                        val h = (state.settings.dayStartHour + it) % 24
                        "Pemakaian sepanjang hari. Paling banyak jam %02d.00 (%s). %d notifikasi.".format(h, formatDuration(hourly[it]), state.notifTimes.size)
                    } ?: "Belum ada pemakaian"
                }
                .pointerInput(state.day) { detectTapGestures { o -> onHour((o.x / size.width * 24).toInt().coerceIn(0, 23)) } },
        ) {
            val barTop = 0f
            val barH = size.height - 14.dp.toPx()
            drawRoundRect(track, Offset(0f, barTop), Size(size.width, barH), CornerRadius(4.dp.toPx()))
            for (s in state.sessions) {
                val x0 = ((s.start - window.first).toFloat() / UsageMath.DAY) * size.width
                val x1 = ((s.end - window.first).toFloat() / UsageMath.DAY) * size.width
                val c = rank[s.pkg]?.let { colors[it] } ?: other
                drawRect(c, Offset(x0, barTop), Size((x1 - x0).coerceAtLeast(1.5f), barH))
            }
            for (h in 1 until 24) {
                val x = size.width * h / 24f
                drawLine(track, Offset(x, barTop), Offset(x, barTop + barH), strokeWidth = if (h % 6 == 0) 1.5f else 0.5f)
            }
            val dy = size.height - 5.dp.toPx()
            for (t in state.notifTimes) {
                val x = ((t - window.first).toFloat() / UsageMath.DAY) * size.width
                drawCircle(dot, radius = 2.dp.toPx(), center = Offset(x, dy))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            (0 until 4).forEach { i ->
                Text("%02d".format((state.settings.dayStartHour + i * 6) % 24), style = TaskifyTextStyles.code, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Legend(dot, "notifikasi")
        }
    }
}

// ============================================================ minggu

@Composable
private fun WeekHero(state: WellbeingUiState, actions: WellbeingActions) {
    val haptics = rememberHaptics()
    val days = state.weekDays
    val rank = state.colorRank
    val colors = (0 until WellbeingUiState.TOP_COLORED).map { appColor(it) }
    val other = MaterialTheme.colorScheme.outlineVariant
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val line = MaterialTheme.colorScheme.onSurfaceVariant
    val max = days.maxOf { state.screenOn(it) }.coerceAtLeast(60_000)
    val avg = state.weekAverage
    IndexCard(TaskifyModule.Wellbeing, tinted = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Text("7 HARI · ${Dates.format(days.first(), "d MMM")} – ${Dates.format(days.last(), "d MMM")}".uppercase(), style = TaskifyTextStyles.indexLabel, color = TaskifyTheme.modules.accent(TaskifyModule.Wellbeing))
        Text(avg?.let { formatDuration(it) } ?: "—", style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
        Text("rata-rata per hari · total ${formatDuration(state.screenMs)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Canvas(
            Modifier.fillMaxWidth().height(140.dp)
                .semantics { contentDescription = "Waktu layar per hari: " + days.joinToString { Dates.format(it, "EEEE") + " " + formatDuration(state.screenOn(it)) } }
                .pointerInput(days) {
                    detectTapGestures { o ->
                        val i = (o.x / size.width * 7).toInt().coerceIn(0, 6)
                        haptics.tick()
                        actions.selectDay(days[i])
                        actions.setRange(WbRange.Day)
                    }
                },
        ) {
            val slot = size.width / 7f
            val w = slot * 0.56f
            days.forEachIndexed { i, d ->
                val x = i * slot + (slot - w) / 2
                drawRoundRect(track, Offset(x, 0f), Size(w, size.height), CornerRadius(6.dp.toPx()))
                var y = size.height
                val apps = state.appsOn(d)
                val segs = apps.filter { it.pkg in rank }.map { colors[rank[it.pkg]!!] to it.foregroundMs } +
                    listOf(other to apps.filter { it.pkg !in rank }.sumOf { it.foregroundMs })
                segs.filter { it.second > 0 }.forEach { (c, ms) ->
                    val h = size.height * ms / max
                    drawRect(c, Offset(x, y - h), Size(w, h))
                    y -= h
                }
            }
            avg?.let {
                val y = size.height - size.height * it / max
                drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            days.forEach { d ->
                Text(
                    Dates.format(d, "EEE"),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (d == state.day) TaskifyTheme.modules.accent(TaskifyModule.Wellbeing) else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Text("Ketuk batang untuk membuka harinya. Garis putus-putus = rata-rata.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun WeekTrends(state: WellbeingUiState) {
    val week = state.history.filter { it.day in state.weekDays }
    val unlockAvg = week.takeIf { it.isNotEmpty() }?.let { w -> w.sumOf { it.unlocks } / w.size }
    val pickup = UsageMath.median(week.mapNotNull { it.firstPickup }.map(UsageMath::minuteOfDay))
    val notif = state.notifCount / 7
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Tile("Buka kunci/hari", unlockAvg?.let { "$it×" } ?: "—", "rata-rata 7 hari", Modifier.weight(1f))
        Tile("Pertama dipegang", pickup?.let { "%02d.%02d".format(it / 60, it % 60) } ?: "—", "median 7 hari", Modifier.weight(1f))
        Tile("Notifikasi/hari", "$notif", "rata-rata 7 hari", Modifier.weight(1f))
    }
}

// ============================================================ aplikasi

private fun LazyListScope.appList(state: WellbeingUiState, onOpen: (AppUsage) -> Unit) {
    val apps = state.apps
    item(key = "apps-title") {
        Text(
            if (state.range == WbRange.Day) "APLIKASI" else "APLIKASI · 7 HARI",
            style = TaskifyTextStyles.indexLabel,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp).semantics { heading() },
        )
    }
    if (apps.isEmpty()) {
        item(key = "apps-empty") { Text("Belum ada pemakaian aplikasi.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp)) }
        return
    }
    val max = apps.first().foregroundMs.coerceAtLeast(1)
    items(apps, key = { "app-" + it.pkg }) { a ->
        val rank = state.colorRank[a.pkg]
        val color = rank?.let { appColor(it) } ?: MaterialTheme.colorScheme.outline
        val track = MaterialTheme.colorScheme.surfaceContainerHighest
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = "Detail ${a.label}") { onOpen(a) }.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(a.pkg, a.label)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(a.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatDuration(a.foregroundMs), style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(6.dp).clip(MaterialTheme.shapes.small)) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawRect(track)
                            drawRect(color, size = Size(size.width * a.foregroundMs / max, size.height))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("${a.opens}× dibuka", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
