package com.rosyidputrar.taskify.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.common.LocalDates
import com.rosyidputrar.taskify.core.domain.auth.homeGreeting
import com.rosyidputrar.taskify.core.designsystem.component.EmptyState
import com.rosyidputrar.taskify.core.designsystem.component.IndexCard
import com.rosyidputrar.taskify.core.designsystem.component.IndexTab
import com.rosyidputrar.taskify.core.designsystem.component.RuledLine
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    displayName: String? = null,
    onOpenModule: (TaskifyModule) -> Unit,
    onCapture: () -> Unit,
) {
    val today = remember { LocalDates.today() }
    val hour = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { TodayHeader(today, homeGreeting(greeting(hour), displayName)) }
        item { WeekRibbon(today, onSelect = { onOpenModule(TaskifyModule.Calendar) }) }
        item { FocusCard(onCapture) }
        item { SummaryBento(onOpenModule) }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    "Aktivitas hari ini",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                EmptyState(
                    module = TaskifyModule.Calendar,
                    title = "Lini waktu masih kosong",
                    message = "Tugas, jurnal, catatan, dan transaksi hari ini akan tersusun di sini menurut jamnya.",
                )
            }
        }
    }
}

private fun greeting(hour: Int): String = when (hour) {
    in 4..10 -> "Selamat pagi"
    in 11..14 -> "Selamat siang"
    in 15..17 -> "Selamat sore"
    else -> "Selamat malam"
}

@Composable
private fun TodayHeader(today: LocalDate, greeting: String) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
        IndexTab(TaskifyModule.Home, text = greeting)
        Spacer(Modifier.height(12.dp))
        Text(LocalDates.dayName(today), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "${today.day} ${LocalDates.monthName(today.month.ordinal + 1)}",
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(12.dp))
        RuledLine()
    }
}

/** Strip 7 hari (3 hari lalu → 3 hari depan). Ketuk hari → Kalender. */
@Composable
private fun WeekRibbon(today: LocalDate, onSelect: (LocalDate) -> Unit) {
    val days = remember(today) { (-3..3).map { offset -> if (offset < 0) today.minus(DatePeriod(days = -offset)) else today.plus(DatePeriod(days = offset)) } }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(days, key = { it.toString() }) { day ->
            val isToday = day == today
            Surface(
                onClick = { onSelect(day) },
                shape = MaterialTheme.shapes.medium,
                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            ) {
                Column(
                    Modifier.width(52.dp).padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(LocalDates.dayName(day).take(3), style = MaterialTheme.typography.labelMedium)
                    Text(day.day.toString(), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
private fun FocusCard(onCapture: () -> Unit) {
    IndexCard(
        TaskifyModule.Tasks,
        tinted = true,
        tabWidth = 8.dp,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    ) {
        Text("FOKUS SEKARANG", style = TaskifyTextStyles.indexLabel, color = TaskifyTheme.modules.accent(TaskifyModule.Tasks))
        Spacer(Modifier.height(6.dp))
        Text("Belum ada tugas fokus", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Tugas terpenting hari ini akan muncul di sini, lengkap dengan progres subtask-nya.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(onClick = onCapture) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Tangkap tugas")
        }
    }
}

private data class Tile(val module: TaskifyModule, val label: String, val value: String, val hint: String)

@Composable
private fun SummaryBento(onOpenModule: (TaskifyModule) -> Unit) {
    val tiles = listOf(
        Tile(TaskifyModule.Tasks, "Tugas tersisa", "—", "0 terlambat"),
        Tile(TaskifyModule.Journal, "Mood hari ini", "—", "Belum menulis"),
        Tile(TaskifyModule.Finance, "Keluar hari ini", "Rp 0", "dari anggaran"),
        Tile(TaskifyModule.Review, "Streak jurnal", "0", "hari berturut"),
    )
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { tile ->
                    IndexCard(
                        tile.module,
                        onClick = { onOpenModule(tile.module) },
                        onClickLabel = "Buka ${tile.module.label}",
                        modifier = Modifier.weight(1f),
                        contentPadding = 14.dp,
                    ) {
                        Text(tile.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(tile.value, style = TaskifyTextStyles.numeric, color = MaterialTheme.colorScheme.onSurface)
                        Text(tile.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

