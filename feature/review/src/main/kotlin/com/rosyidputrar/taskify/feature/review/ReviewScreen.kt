package com.rosyidputrar.taskify.feature.review

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rosyidputrar.taskify.core.common.Rupiah
import com.rosyidputrar.taskify.core.designsystem.component.IndexCard
import com.rosyidputrar.taskify.core.designsystem.component.ModuleHeader
import com.rosyidputrar.taskify.core.designsystem.component.moodColor
import com.rosyidputrar.taskify.core.designsystem.component.moodIcon
import com.rosyidputrar.taskify.core.designsystem.theme.SerifFamily
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.designsystem.theme.namedColor
import com.rosyidputrar.taskify.core.domain.journal.moodLabel
import com.rosyidputrar.taskify.core.domain.review.DayPoint
import com.rosyidputrar.taskify.core.domain.review.Review
import com.rosyidputrar.taskify.core.domain.review.ReviewPeriod
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.ui.ActivityHeatmap
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

/** Tujuan navigasi dari Review (diisi app). */
data class ReviewNav(
    val openDay: (String) -> Unit = {},
    val openJournal: (String) -> Unit = {},
    val openFinance: () -> Unit = {},
    val openVault: () -> Unit = {},
    /** Templat refleksi jurnal: (periode, dari, sampai). */
    val reflect: (ReviewPeriod, String, String) -> Unit = { _, _, _ -> },
)

@Composable
fun ReviewScreen(
    contentPadding: PaddingValues,
    nav: ReviewNav,
    onBack: (() -> Unit)? = null,
    backButton: @Composable (() -> Unit) -> Unit = {},
    viewModel: ReviewViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ReviewContent(state, viewModel, nav, contentPadding, onBack, backButton)
}

@Composable
fun ReviewContent(
    state: ReviewUiState,
    actions: ReviewActions,
    nav: ReviewNav,
    contentPadding: PaddingValues,
    onBack: (() -> Unit)? = null,
    backButton: @Composable (() -> Unit) -> Unit = {},
) {
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Review)
    val cur = state.ranges.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item(key = "header") {
            ModuleHeader(
                TaskifyModule.Review, "Review",
                subtitle = "Ringkasan & refleksi semua aktivitasmu",
                navigation = onBack?.let { { backButton(it) } },
                actions = {
                    FilledTonalIconButton(
                        onClick = { nav.reflect(state.period, cur.from, cur.to) },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = accent, contentColor = TaskifyTheme.modules.onAccent(TaskifyModule.Review)),
                    ) { Icon(Icons.Outlined.EditNote, contentDescription = "Tulis refleksi") }
                },
            )
        }
        item(key = "period") {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ReviewPeriod.entries.forEachIndexed { i, p ->
                        SegmentedButton(state.period == p, { actions.setPeriod(p) }, SegmentedButtonDefaults.itemShape(i, 2), label = { Text(p.label) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { actions.shift(-1) }) { Icon(Icons.Rounded.ChevronLeft, contentDescription = "Periode sebelumnya") }
                    TextButton(onClick = actions::goToday, modifier = Modifier.weight(1f)) {
                        Text(state.label, style = MaterialTheme.typography.titleMedium.copy(fontFamily = SerifFamily))
                    }
                    IconButton(onClick = { actions.shift(1) }, enabled = !state.isCurrent) { Icon(Icons.Rounded.ChevronRight, contentDescription = "Periode berikutnya") }
                }
            }
        }
        val now = state.now ?: return@LazyColumn
        item(key = "summary") {
            IndexCard(TaskifyModule.Review, tinted = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text(
                    (if (state.isCurrent) "${state.period.unit} ini sejauh ini" else "ringkasan ${state.period.unit}").uppercase(),
                    style = MaterialTheme.typography.labelMedium, color = accent,
                )
                Spacer(Modifier.height(6.dp))
                Text(Review.summary(now, state.before, state.period, state.elapsed), style = MaterialTheme.typography.bodyLarge.copy(fontFamily = SerifFamily))
            }
        }
        item(key = "stats") {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat(Icons.Outlined.TaskAlt, TaskifyModule.Tasks, "Tugas selesai", "${now.tasksDone}", Review.percentChange(now.tasksDone, state.before.tasksDone), Modifier.weight(1f))
                    Stat(Icons.Outlined.CalendarMonth, TaskifyModule.Calendar, "Tepat jadwal", Review.onTimePercent(now)?.let { "$it%" } ?: "–", null, Modifier.weight(1f),
                        hint = "${now.tasksDueDone}/${now.tasksDue} tugas ber-tenggat")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat(Icons.Outlined.Wallet, TaskifyModule.Finance, "Pengeluaran", Rupiah.compact(now.expense), Review.percentChange(now.expense, state.before.expense), Modifier.weight(1f),
                        lowerIsBetter = true, hint = "Masuk ${Rupiah.compact(now.income)}")
                    Stat(Icons.Outlined.EditNote, TaskifyModule.Journal, "Hari berjurnal", "${now.journalDays}/${state.elapsed}", null, Modifier.weight(1f),
                        hint = now.moodAvg?.let { "mood ${"%.1f".format(it).replace('.', ',')} · ${moodLabel(it)?.lowercase()}" } ?: "belum ada mood")
                }
            }
        }
        item(key = "daily") {
            IndexCard(TaskifyModule.Review, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text("Per hari", style = MaterialTheme.typography.titleMedium.copy(fontFamily = SerifFamily))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Legend(TaskifyTheme.modules.accent(TaskifyModule.Tasks), "tugas"); Spacer(Modifier.width(12.dp))
                    Legend(namedColor("rose"), "pengeluaran"); Spacer(Modifier.width(12.dp))
                    Text("ikon = mood", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(10.dp))
                DailyChart(state.daily, state.today, nav.openDay)
            }
        }
        item(key = "cats") {
            IndexCard(TaskifyModule.Review, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pengeluaran terbesar", style = MaterialTheme.typography.titleMedium.copy(fontFamily = SerifFamily), modifier = Modifier.weight(1f))
                    TextButton(onClick = nav.openFinance) { Text("Keuangan") }
                }
                if (state.categories.isEmpty()) Text("Belum ada pengeluaran.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.categories.forEach { c ->
                    val fraction = if (now.expense > 0) (c.total.toFloat() / now.expense).coerceIn(0f, 1f) else 0f
                    Column(Modifier.padding(vertical = 5.dp)) {
                        Row {
                            Text(c.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Rupiah.format(c.total), style = MaterialTheme.typography.labelLarge)
                        }
                        Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(3.dp))) {
                            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(namedColor(c.color ?: "slate"), RoundedCornerShape(3.dp)))
                        }
                    }
                }
            }
        }
        item(key = "highlights") {
            IndexCard(TaskifyModule.Review, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text("Sorotan", style = MaterialTheme.typography.titleMedium.copy(fontFamily = SerifFamily))
                val best = Review.bestDay(state.daily)
                Highlight(Icons.Outlined.EmojiEvents, TaskifyModule.Tasks, "Hari paling produktif",
                    best?.let { "${Dates.format(it.day, "EEEE, d MMM")} · ${it.tasksDone} tugas" } ?: "Belum ada tugas selesai",
                    best?.let { b -> { nav.openDay(b.day) } })
                Highlight(Icons.Outlined.Wallet, TaskifyModule.Finance, "Pengeluaran terbesar",
                    state.bigExpense?.let { "${it.title} · ${Rupiah.format(it.amount)}" } ?: "Tidak ada",
                    state.bigExpense?.let { b -> { nav.openDay(b.day) } })
                Highlight(Icons.Outlined.EditNote, TaskifyModule.Journal, "Hari terbaik",
                    state.bestMood?.let { (it.title ?: Dates.format(it.day, "EEEE, d MMM")) + " · " + (moodLabel(it.mood) ?: "") } ?: "Belum ada jurnal dengan mood",
                    state.bestMood?.let { m -> { nav.openJournal(m.id) } })
                Highlight(Icons.Outlined.Code, TaskifyModule.Vault, "Vault", "${now.snippetsNew} snippet baru · ${now.versionsNew} versi project", nav.openVault)
            }
        }
        item(key = "heatmap") {
            IndexCard(TaskifyModule.Review, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text("Aktivitas", style = MaterialTheme.typography.titleMedium.copy(fontFamily = SerifFamily))
                Text("26 minggu terakhir · ketuk hari untuk membuka Kalender", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                ActivityHeatmap(state.heatGrid, state.heat, state.today, onSelect = nav.openDay)
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Box(Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
    Spacer(Modifier.width(4.dp))
    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Stat(icon: ImageVector, module: TaskifyModule, label: String, value: String, delta: Int?, modifier: Modifier, lowerIsBetter: Boolean = false, hint: String? = null) {
    val accent = TaskifyTheme.modules.accent(module)
    IndexCard(module, modifier = modifier, contentPadding = 12.dp) {
        Box(Modifier.size(32.dp).background(accent.copy(alpha = 0.16f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = SerifFamily), maxLines = 1)
            if (delta != null && delta != 0) {
                val good = if (lowerIsBetter) delta < 0 else delta > 0
                val color = if (good) namedColor("emerald") else MaterialTheme.colorScheme.error
                Spacer(Modifier.width(6.dp))
                Icon(if (delta > 0) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown, null, tint = color, modifier = Modifier.size(14.dp))
                Text((if (delta > 0) "+" else "") + "$delta%", style = MaterialTheme.typography.labelSmall, color = color)
            }
        }
        Text(label, style = MaterialTheme.typography.labelLarge)
        hint?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
    }
}

/** Batang ganda per hari (tugas & pengeluaran) + ikon mood; ketuk = buka hari di Kalender. */
@Composable
private fun DailyChart(points: List<DayPoint>, today: String, onSelect: (String) -> Unit) {
    val maxTasks = maxOf(1, points.maxOfOrNull { it.tasksDone } ?: 0)
    val maxExp = maxOf(1L, points.maxOfOrNull { it.expense } ?: 0L)
    val dense = points.size > 10
    val taskColor = TaskifyTheme.modules.accent(TaskifyModule.Tasks)
    val expColor = namedColor("rose")
    Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(if (dense) 1.dp else 6.dp)) {
        points.forEach { p ->
            val future = p.day > today
            val dayNum = p.day.takeLast(2).toInt()
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .then(if (!future) Modifier.clickable(role = Role.Button) { onSelect(p.day) } else Modifier)
                    .semantics { contentDescription = "${Dates.format(p.day, "EEEE, d MMM")}: ${p.tasksDone} tugas, ${Rupiah.format(p.expense)}" + (moodLabel(p.mood)?.let { ", mood $it" } ?: "") },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.height(16.dp), contentAlignment = Alignment.Center) {
                    val m = p.mood?.roundToInt()
                    moodIcon(m)?.let { Icon(it, null, tint = moodColor(m), modifier = Modifier.size(if (dense) 9.dp else 14.dp)) }
                }
                Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    Box(Modifier.weight(1f).fillMaxHeight(p.tasksDone.toFloat() / maxTasks).heightIn(min = if (p.tasksDone > 0) 3.dp else 0.dp).background(taskColor, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)))
                    Box(Modifier.weight(1f).fillMaxHeight(p.expense.toFloat() / maxExp).heightIn(min = if (p.expense > 0) 3.dp else 0.dp).background(expColor, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)))
                }
                Text(
                    if (dense) (if (dayNum % 5 == 1 || p.day == today) "$dayNum" else "") else Dates.format(p.day, "EEE").take(3),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (p.day == today) TaskifyTheme.modules.accent(TaskifyModule.Review) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, softWrap = false,
                    // Kolom bulanan sempit: angka tanggal boleh melewati lebar kolomnya
                    modifier = Modifier.wrapContentWidth(unbounded = true),
                )
            }
        }
    }
}

@Composable
private fun Highlight(icon: ImageVector, module: TaskifyModule, label: String, value: String, onClick: (() -> Unit)?) {
    val accent = TaskifyTheme.modules.accent(module)
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).background(accent.copy(alpha = 0.16f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (onClick != null) Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
