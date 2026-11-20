package com.rosyidputrar.taskify.feature.notifications

import com.rosyidputrar.taskify.core.designsystem.component.fadingEdges
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rosyidputrar.taskify.core.common.Rupiah
import com.rosyidputrar.taskify.core.designsystem.component.EmptyState
import com.rosyidputrar.taskify.core.designsystem.component.IndexCard
import com.rosyidputrar.taskify.core.designsystem.component.ListSkeleton
import com.rosyidputrar.taskify.core.designsystem.component.ModuleHeader
import com.rosyidputrar.taskify.core.designsystem.component.rememberHaptics
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.designsystem.theme.namedColor
import com.rosyidputrar.taskify.core.device.DeviceAccess
import com.rosyidputrar.taskify.core.device.ui.AppIcon
import com.rosyidputrar.taskify.core.domain.device.NotifStorageLevel
import com.rosyidputrar.taskify.core.domain.device.ParcelStage
import com.rosyidputrar.taskify.core.domain.device.formatClockTime
import com.rosyidputrar.taskify.core.domain.finance.TxKind
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.store.device.NotifEntry
import com.rosyidputrar.taskify.core.store.device.Parcel
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun NotificationsScreen(
    contentPadding: PaddingValues,
    app: String? = null,
    openDrafts: Boolean = false,
    onBack: (() -> Unit)? = null,
    backButton: @Composable (() -> Unit) -> Unit = {},
    viewModel: NotificationsViewModel = koinViewModel { parametersOf(app) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Kembali dari Setelan sistem → status izin dibaca ulang.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshAccess()
        onPauseOrDispose { }
    }
    NotificationsContent(state, viewModel, contentPadding, onBack, backButton, openDrafts)
}

/** Baris lini waktu yang sudah disusun (untuk LazyColumn & lompat ke jam). */
internal sealed interface TimelineRow {
    val key: String

    data class Hour(val hour: Int, val count: Int) : TimelineRow {
        override val key = "hour-$hour"
    }

    data class Single(val entry: NotifEntry) : TimelineRow {
        override val key = "n-${entry.id}"
    }

    /** Beberapa pesan beruntun dari percakapan yang sama dalam jam yang sama. */
    data class Group(val entries: List<NotifEntry>) : TimelineRow {
        override val key = "g-${entries.first().id}"
    }
}

internal fun buildTimeline(entries: List<NotifEntry>): List<TimelineRow> {
    val out = mutableListOf<TimelineRow>()
    entries.groupBy { hourOf(it.postedAt) }.toSortedMap(compareByDescending { it }).forEach { (hour, list) ->
        out += TimelineRow.Hour(hour, list.size)
        var i = 0
        while (i < list.size) {
            val first = list[i]
            val thread = first.conversation ?: first.title
            var j = i + 1
            while (j < list.size && list[j].pkg == first.pkg && (list[j].conversation ?: list[j].title) == thread && thread != null) j++
            out += if (j - i > 1) TimelineRow.Group(list.subList(i, j)) else TimelineRow.Single(first)
            i = j
        }
    }
    return out
}

/**
 * Indeks item baris jam [hour] di LazyColumn, atau null bila lompat tidak boleh dilakukan:
 * jam tidak ada di lini waktu, atau tampilan bukan Lini Waktu. Di tampilan Per Aplikasi baris
 * lini waktu tidak dirender, jadi rumus lama (`indeks + totalItemsCount - timeline.size`) bisa
 * menghasilkan indeks negatif dan `scrollToItem` melempar IllegalArgumentException (crash).
 * [listOffset] = jumlah item sebelum daftar (header + banner + kartu + pita + ringkasan +
 * pencarian + saringan + pemilih tampilan).
 */
internal fun hourJumpTarget(hour: Int, view: NotifView, timeline: List<TimelineRow>, listOffset: Int): Int? {
    if (view != NotifView.Timeline) return null
    val index = timeline.indexOfFirst { it is TimelineRow.Hour && it.hour == hour }
    if (index < 0) return null
    return listOffset + index
}

@Composable
fun NotificationsContent(
    state: NotificationsUiState,
    actions: NotificationsActions,
    contentPadding: PaddingValues,
    onBack: (() -> Unit)? = null,
    backButton: @Composable (() -> Unit) -> Unit = {},
    openDrafts: Boolean = false,
) {
    val haptics = rememberHaptics()
    var detail by remember { mutableStateOf<NotifEntry?>(null) }
    var showDrafts by rememberSaveable { mutableStateOf(openDrafts) }
    var showParcels by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(state.query.isNotEmpty()) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val timeline = remember(state.visible) { buildTimeline(state.visible) }
    // Jumlah item sebelum daftar (lini waktu / per aplikasi): header + banner + kartu + pita +
    // ringkasan + (pencarian) + saringan + pemilih tampilan. Dipakai untuk target lompat ke jam.
    val listOffset = 5 +
        (if (!state.access.connected && state.access.disconnectedAt > 0) 1 else 0) +
        (if (state.drafts.isNotEmpty()) 1 else 0) +
        (if (state.parcels.isNotEmpty()) 1 else 0) +
        (if (searching) 1 else 0)
    // Lompat ke jam yang diketuk dari tampilan Per Aplikasi: pindah dulu ke Lini Waktu,
    // lompatnya menyusul setelah daftar lini waktu tersusun.
    var pendingHour by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(pendingHour, state.view) {
        val h = pendingHour ?: return@LaunchedEffect
        if (state.view != NotifView.Timeline) return@LaunchedEffect
        val target = hourJumpTarget(h, state.view, timeline, listOffset)
        if (target != null) listState.animateScrollToItem(target)
        // Bersihkan penanda SETELAH lompat selesai. Membersihkannya sebelum scroll akan
        // mengubah kunci LaunchedEffect ini dan membatalkan animasi scroll yang sedang berjalan.
        pendingHour = null
    }

    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = contentPadding) {
        item(key = "header") {
            ModuleHeader(
                TaskifyModule.Notifications, "Notifikasi",
                subtitle = "Semua yang masuk, tersimpan hanya di HP ini",
                navigation = onBack?.let { { backButton(it) } },
                actions = {
                    if (state.access.granted) {
                        IconButton(onClick = { searching = !searching; if (!searching) actions.setQuery("") }) {
                            Icon(Icons.Outlined.Search, contentDescription = if (searching) "Tutup pencarian" else "Cari notifikasi")
                        }
                    }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Outlined.Settings, contentDescription = "Pengaturan Notifikasi") }
                },
            )
        }
        if (!state.loaded) {
            item(key = "skeleton") { ListSkeleton(TaskifyModule.Notifications, rows = 5, lines = 2) }
            return@LazyColumn
        }
        if (!state.access.granted) {
            item(key = "onboarding") { NotifOnboarding(state, actions) }
            return@LazyColumn
        }
        if (!state.access.connected && state.access.disconnectedAt > 0) item(key = "disconnected") { DisconnectedBanner(state, actions) }
        if (state.drafts.isNotEmpty()) item(key = "drafts") { DraftsCard(state, onOpen = { showDrafts = true }) }
        if (state.parcels.isNotEmpty()) item(key = "parcels") { ParcelsCard(state.parcels, state.today, onOpen = { showParcels = true }) }
        item(key = "days") { DayRibbon(state, actions) }
        item(key = "summary") {
            SummaryCard(state, actions, onHour = { h ->
                if (state.view == NotifView.Timeline) {
                    hourJumpTarget(h, state.view, timeline, listOffset)?.let { target ->
                        scope.launch { listState.animateScrollToItem(target) }
                    }
                } else {
                    // Baris lini waktu tidak dirender di tampilan Per Aplikasi: pindah dulu,
                    // lompatnya dikerjakan LaunchedEffect di atas setelah tampilan berganti.
                    pendingHour = h
                    actions.setView(NotifView.Timeline)
                }
            })
        }
        if (searching) item(key = "search") {
            OutlinedTextField(
                value = state.query,
                onValueChange = actions::setQuery,
                placeholder = { Text("Cari judul, isi, atau aplikasi") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }
        item(key = "filters") { Filters(state, actions) }
        item(key = "view") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                NotifView.entries.forEachIndexed { i, v ->
                    SegmentedButton(
                        selected = state.view == v,
                        onClick = { haptics.tick(); actions.setView(v) },
                        shape = SegmentedButtonDefaults.itemShape(i, NotifView.entries.size),
                        label = { Text(v.label) },
                    )
                }
            }
        }
        if (state.view == NotifView.PerApp) perApp(state, actions)
        else timeline(timeline, state, actions, onOpen = { detail = it })
    }

    detail?.let { e ->
        NotificationDetailSheet(
            entry = state.entries.firstOrNull { it.id == e.id } ?: e,
            actions = actions,
            onOpenDraft = { detail = null; showDrafts = true },
            onDismiss = { detail = null },
        )
    }
    if (showDrafts) DraftsSheet(state, actions, onDismiss = { showDrafts = false })
    if (showParcels) ParcelsSheet(state.parcels, state.today, onDismiss = { showParcels = false })
    if (showSettings) NotifSettingsSheet(state, actions, onDismiss = { showSettings = false })
}

// ============================================================ izin

@Composable
private fun NotifOnboarding(state: NotificationsUiState, actions: NotificationsActions) {
    val context = LocalContext.current
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Notifications)
    IndexCard(TaskifyModule.Notifications, tinted = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("MULAI MENCATAT", style = TaskifyTextStyles.indexLabel, color = accent)
        Spacer(Modifier.height(6.dp))
        Text("Lihat ulang, cari, dan tindak lanjuti notifikasimu", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(6.dp))
        Text(
            "Taskify mencatat notifikasi yang masuk sejak izin diberikan. Tersimpan hanya di HP ini, tidak pernah dikirim ke server, " +
                "kode OTP disensor, dan terkunci di balik PIN aplikasi Taskify.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        var step = 1
        if (state.access.restrictedHint) {
            Step(step++, "Izinkan setelan terbatas", "Khusus APK dari luar Play Store (Android 13+): buka Info aplikasi → ⋮ (kanan atas) → Izinkan setelan terbatas.") {
                OutlinedButton(onClick = { DeviceAccess.open(context, DeviceAccess.appDetails(context)) }) { Text("Buka Info aplikasi") }
            }
        }
        Step(step, "Beri Akses notifikasi", "Nyalakan Taskify di halaman Akses notifikasi, lalu kembali ke sini.") {
            Button(onClick = { DeviceAccess.open(context, DeviceAccess.notificationSettings(context)) }) { Text("Buka Akses notifikasi") }
        }
        Spacer(Modifier.height(14.dp))
        Text("YANG DISIMPAN", style = TaskifyTextStyles.indexLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LevelChoice(state.settings.level, actions::setLevel)
        // D-06: isi yang disimpan terenkripsi di perangkat (AES-256-GCM, kunci Keystore) dan
        // terhapus setelah 7 hari. "Hitungan saja" tidak menyimpan isi sama sekali.
        Text(
            "Isi yang disimpan terenkripsi di perangkat dan terhapus otomatis setelah 7 hari. Pilih \"Hitungan saja\" bila tidak mau ada isi notifikasi yang tersimpan sama sekali.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Step(n: Int, title: String, body: String, action: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text("$n", style = MaterialTheme.typography.titleMedium, color = TaskifyTheme.modules.accent(TaskifyModule.Notifications), modifier = Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            action()
        }
    }
}

@Composable
internal fun LevelChoice(selected: NotifStorageLevel, onSelect: (NotifStorageLevel) -> Unit) {
    Column(Modifier.selectableGroup()) {
        NotifStorageLevel.entries.forEach { level ->
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                    .selectable(selected == level, role = Role.RadioButton) { onSelect(level) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == level, onClick = null)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(level.label, style = MaterialTheme.typography.bodyLarge)
                    Text(level.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DisconnectedBanner(state: NotificationsUiState, actions: NotificationsActions) {
    val amber = namedColor("amber")
    Surface(color = amber.copy(alpha = 0.14f), shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.SyncProblem, contentDescription = null, tint = amber)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Pencatatan berhenti sejak ${formatClockTime(state.access.disconnectedAt)}" +
                        (Dates.today(state.access.disconnectedAt).takeIf { it != state.today }?.let { ", " + Dates.relativeDayLabel(it, state.today).lowercase() } ?: ""),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
            }
            state.access.oem?.let {
                Text(
                    "HP $it sering menghentikan aplikasi di latar. Izinkan Autostart dan matikan optimasi baterai untuk Taskify.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            TextButton(onClick = actions::reconnect) { Text("Sambungkan ulang") }
        }
    }
}

// ============================================================ kartu draf & paket

@Composable
private fun DraftsCard(state: NotificationsUiState, onOpen: () -> Unit) {
    val total = state.drafts.sumOf { d -> d.money?.let { if (it.kind == TxKind.Income) it.amount else -it.amount } ?: 0L }
    IndexCard(
        TaskifyModule.Finance, tinted = true, onClick = onOpen, onClickLabel = "Tinjau draf transaksi",
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Payments, contentDescription = null, tint = TaskifyTheme.modules.accent(TaskifyModule.Finance))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${state.drafts.size} transaksi menunggu konfirmasi", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Dari notifikasi bank & dompet · bersih ${if (total < 0) "−" else "+"}${Rupiah.format(abs(total))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("Tinjau", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ParcelsCard(parcels: List<Parcel>, today: String, onOpen: () -> Unit) {
    IndexCard(
        TaskifyModule.Notifications, onClick = onOpen, onClickLabel = "Lihat semua paket",
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.LocalShipping, contentDescription = null, tint = TaskifyTheme.modules.accent(TaskifyModule.Notifications))
            Spacer(Modifier.width(10.dp))
            Text("PAKET", style = TaskifyTextStyles.indexLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            val arriving = parcels.count { it.stage == ParcelStage.Delivered && Dates.today(it.updatedAt) == today }
            if (arriving > 0) Text("$arriving tiba hari ini", style = MaterialTheme.typography.labelLarge, color = namedColor("emerald"))
        }
        parcels.take(3).forEach { ParcelRow(it, today) }
        if (parcels.size > 3) Text("+${parcels.size - 3} lainnya", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun ParcelRow(p: Parcel, today: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(p.pkg, p.appLabel, size = 28.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(p.title ?: p.appLabel, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(p.appLabel, p.ref.takeIf { it.length in 8..24 && it.any(Char::isDigit) }, Dates.relativeDayLabel(Dates.today(p.updatedAt), today) + " " + formatClockTime(p.updatedAt)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StageChip(p.stage)
    }
}

@Composable
internal fun StageChip(stage: ParcelStage) {
    val color = when (stage) {
        ParcelStage.Delivered -> namedColor("emerald")
        ParcelStage.OutForDelivery -> namedColor("orange")
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = color.copy(alpha = 0.14f), shape = MaterialTheme.shapes.small) {
        Text(stage.label, style = MaterialTheme.typography.labelMedium, color = color, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

// ============================================================ pita hari & ringkasan

@Composable
private fun DayRibbon(state: NotificationsUiState, actions: NotificationsActions) {
    val haptics = rememberHaptics()
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 6.dp),
        reverseLayout = true,
    ) {
        items(state.days.reversed(), key = { it }) { day ->
            val selected = day == state.day
            Surface(
                onClick = { haptics.tick(); actions.selectDay(day) },
                shape = MaterialTheme.shapes.medium,
                color = if (selected) TaskifyTheme.modules.accent(TaskifyModule.Notifications) else MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = if (selected) TaskifyTheme.modules.onAccent(TaskifyModule.Notifications) else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { contentDescription = Dates.format(day, "EEEE, d MMMM") + if (selected) ", dipilih" else "" },
            ) {
                Column(Modifier.width(52.dp).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (day == state.today) "Ini" else Dates.format(day, "EEE"), style = MaterialTheme.typography.labelMedium)
                    Text(Dates.parse(day).dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(state: NotificationsUiState, actions: NotificationsActions, onHour: (Int) -> Unit) {
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Notifications)
    IndexCard(TaskifyModule.Notifications, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${state.total}", style = TaskifyTextStyles.numeric)
            Spacer(Modifier.width(8.dp))
            Text(
                if (state.day == state.today) "notifikasi hari ini" else "notifikasi · " + Dates.relativeDayLabel(state.day, state.today).lowercase(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        deltaText(state.total, state.previousTotal)?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        HourBars(state.hourly, accent, onHour)
        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            // Tiap label menempati 6 jam → tepat di bawah batang jam 00, 06, 12, 18.
            listOf("00", "06", "12", "18").forEach { label ->
                Text(label, style = TaskifyTextStyles.code, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
        }
        if (state.appCounts.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("TERBANYAK", style = TaskifyTextStyles.indexLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Tiga baris ringkas (bukan chip) supaya nama aplikasi & jumlahnya tidak terpotong.
            state.appCounts.take(3).forEach { a ->
                val selected = state.app == a.pkg
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                        .selectable(selected, role = Role.Checkbox) { actions.setApp(if (selected) null else a.pkg) }
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(a.pkg, a.label, size = 22.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        a.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("${a.count}", style = MaterialTheme.typography.labelLarge, color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

internal fun deltaText(now: Int, before: Int?): String? {
    if (before == null || before == 0) return null
    val pct = ((now - before) * 100.0 / before).roundToInt()
    return when {
        pct == 0 -> "sama dengan kemarin"
        pct > 0 -> "↑ $pct% dari kemarin"
        else -> "↓ ${-pct}% dari kemarin"
    }
}

/** 24 batang per jam; ketuk batang → lompat ke jam itu di lini waktu. */
@Composable
private fun HourBars(hourly: IntArray, color: androidx.compose.ui.graphics.Color, onHour: (Int) -> Unit) {
    val max = (hourly.maxOrNull() ?: 0).coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val busiest = hourly.indices.maxByOrNull { hourly[it] }?.takeIf { hourly[it] > 0 }
    Canvas(
        Modifier.fillMaxWidth().height(56.dp)
            .semantics { contentDescription = busiest?.let { "Grafik per jam. Paling ramai jam %02d.00 (%d notifikasi)".format(it, hourly[it]) } ?: "Belum ada notifikasi" }
            .pointerInput(hourly) { detectTapGestures { o -> onHour((o.x / size.width * 24).toInt().coerceIn(0, 23)) } },
    ) {
        val slot = size.width / 24f
        val w = slot * 0.62f
        for (h in 0 until 24) {
            val x = h * slot + (slot - w) / 2
            drawRoundRect(track, Offset(x, size.height - 3.dp.toPx()), Size(w, 3.dp.toPx()), CornerRadius(2.dp.toPx()))
            val v = hourly[h]
            if (v > 0) {
                val bh = (size.height - 2.dp.toPx()) * v / max
                drawRoundRect(color, Offset(x, size.height - bh), Size(w, bh), CornerRadius(2.dp.toPx()))
            }
        }
    }
}

@Composable
private fun Filters(state: NotificationsUiState, actions: NotificationsActions) {
    val haptics = rememberHaptics()
    val filterScroll = rememberLazyListState()
    LazyRow(state = filterScroll, contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp).fadingEdges(filterScroll)) {
        state.app?.let { pkg ->
            item(key = "app") {
                InputChip(
                    selected = true,
                    onClick = { actions.setApp(null) },
                    label = { Text(state.appLabel ?: pkg) },
                    leadingIcon = { AppIcon(pkg, state.appLabel ?: pkg, size = 18.dp) },
                    trailingIcon = { Icon(Icons.Outlined.Close, contentDescription = "Hapus saringan aplikasi", modifier = Modifier.size(18.dp)) },
                )
            }
        }
        items(NotifFilter.entries, key = { it.name }) { f ->
            FilterChip(selected = state.filter == f, onClick = { haptics.tick(); actions.setFilter(f) }, label = { Text(f.label) })
        }
    }
}

// ============================================================ daftar

private fun LazyListScope.timeline(rows: List<TimelineRow>, state: NotificationsUiState, actions: NotificationsActions, onOpen: (NotifEntry) -> Unit) {
    if (rows.isEmpty()) {
        item(key = "empty") {
            EmptyState(
                TaskifyModule.Notifications,
                title = when {
                    state.entries.isEmpty() && state.settings.level == NotifStorageLevel.CountOnly -> "Hanya hitungan yang disimpan"
                    state.entries.isEmpty() -> if (state.day == state.today) "Belum ada notifikasi" else "Tidak ada catatan hari itu"
                    else -> "Tidak ada yang cocok"
                },
                message = when {
                    state.entries.isEmpty() && state.settings.level == NotifStorageLevel.CountOnly -> "Tingkat penyimpanan \"Hitungan saja\" tidak menyimpan isi. Ubah di Pengaturan Notifikasi."
                    state.entries.isEmpty() -> "Notifikasi yang masuk akan tersusun di sini menurut jamnya."
                    else -> "Coba saringan lain atau kata pencarian yang berbeda."
                },
            )
        }
        return
    }
    items(rows, key = { it.key }) { row ->
        when (row) {
            is TimelineRow.Hour -> Text(
                "%02d.00 · %d".format(row.hour, row.count),
                style = TaskifyTextStyles.code,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 2.dp).semantics { heading() },
            )
            is TimelineRow.Single -> NotificationRow(row.entry, actions, onOpen)
            is TimelineRow.Group -> NotificationGroup(row.entries, actions, onOpen)
        }
    }
}

private fun LazyListScope.perApp(state: NotificationsUiState, actions: NotificationsActions) {
    // Pencarian menyaring daftar Per Aplikasi berdasarkan nama/paket aplikasi.
    val q = state.query.trim().lowercase()
    val apps = if (q.isEmpty()) state.appCounts else state.appCounts.filter { it.label.lowercase().contains(q) || it.pkg.lowercase().contains(q) }
    if (apps.isEmpty()) {
        item(key = "empty-apps") {
            EmptyState(
                TaskifyModule.Notifications,
                if (state.appCounts.isEmpty()) "Belum ada notifikasi" else "Tidak ada aplikasi yang cocok",
                if (state.appCounts.isEmpty()) "Jumlah per aplikasi akan muncul di sini." else "Coba kata pencarian yang berbeda.",
            )
        }
        return
    }
    val max = apps.maxOf { it.count }.coerceAtLeast(1)
    val accent = @Composable { TaskifyTheme.modules.accent(TaskifyModule.Notifications) }
    items(apps, key = { "app-" + it.pkg }) { a ->
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = "Lihat notifikasi ${a.label}") { actions.setApp(a.pkg); actions.setView(NotifView.Timeline) }
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(a.pkg, a.label)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row {
                    Text(a.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${a.count}", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(4.dp))
                val c = accent()
                val track = MaterialTheme.colorScheme.surfaceContainerHighest
                Box(Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.small)) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawRect(track)
                        drawRect(c, size = Size(size.width * a.count / max, size.height))
                    }
                }
            }
        }
    }
}
