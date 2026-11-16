package com.rosyidputrar.taskify.feature.finance.tab

import com.rosyidputrar.taskify.core.designsystem.component.FitText
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallMade
import androidx.compose.material.icons.automirrored.outlined.CallReceived
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.common.Rupiah
import com.rosyidputrar.taskify.core.designsystem.component.EmptyState
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.designsystem.theme.namedColor
import com.rosyidputrar.taskify.core.domain.finance.BudgetLevel
import com.rosyidputrar.taskify.core.domain.finance.CategoryKind
import com.rosyidputrar.taskify.core.domain.finance.Recurring
import com.rosyidputrar.taskify.core.domain.finance.TxKind
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.feature.finance.FinanceActions
import com.rosyidputrar.taskify.feature.finance.FinanceTab
import com.rosyidputrar.taskify.feature.finance.FinanceUiState
import com.rosyidputrar.taskify.feature.finance.component.CategoryBadge
import com.rosyidputrar.taskify.feature.finance.component.CategoryDonut
import com.rosyidputrar.taskify.feature.finance.component.DailyBars
import com.rosyidputrar.taskify.feature.finance.component.SectionCard
import com.rosyidputrar.taskify.feature.finance.component.TxRow
import com.rosyidputrar.taskify.feature.finance.component.compactSlices
import com.rosyidputrar.taskify.feature.finance.component.expenseColor
import com.rosyidputrar.taskify.feature.finance.component.incomeColor

fun LazyListScope.overviewTab(state: FinanceUiState, actions: FinanceActions, onOpenTx: (String) -> Unit) {
    val totals = state.monthData.totals
    item(key = "ov-stats") {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Pemasukan", totals.income, Icons.AutoMirrored.Outlined.CallReceived, incomeColor(), Modifier.weight(1f))
            StatTile("Pengeluaran", totals.expense, Icons.AutoMirrored.Outlined.CallMade, expenseColor(), Modifier.weight(1f))
            StatTile("Selisih", totals.net, Icons.Outlined.Balance, if (totals.net >= 0) incomeColor() else expenseColor(), Modifier.weight(1f), signed = true)
        }
    }
    if (state.due.isNotEmpty()) item(key = "ov-due") { DueCard(state.due, state.today, actions) }
    item(key = "ov-donut") {
        val expense = state.sliceKind == CategoryKind.Expense
        SectionCard(
            if (expense) "Pengeluaran per kategori" else "Pemasukan per kategori",
            subtitle = Dates.format(state.month, "MMMM yyyy"),
            action = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = expense, onClick = { actions.setSliceKind(CategoryKind.Expense) }, label = { Text("Keluar") })
                    FilterChip(selected = !expense, onClick = { actions.setSliceKind(CategoryKind.Income) }, label = { Text("Masuk") })
                }
            },
        ) {
            val slices = compactSlices(state.slices)
            if (slices.isEmpty()) {
                Text("Belum ada ${if (expense) "pengeluaran" else "pemasukan"} bulan ini.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val total = slices.sumOf { it.total }.coerceAtLeast(1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CategoryDonut(slices, if (expense) "Keluar" else "Masuk", size = 140.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        slices.forEach { s ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val c = namedColor(s.color)
                                Canvas(Modifier.size(10.dp)) { drawCircle(c) }
                                Spacer(Modifier.width(8.dp))
                                Text(s.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text("${s.total * 100 / total}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
    item(key = "ov-daily") {
        SectionCard("Arus harian", subtitle = "Rata-rata keluar ${Rupiah.format(dailyAverage(state))} per hari") {
            DailyBars(state.month, state.monthData.daily, state.today)
        }
    }
    val budgets = state.monthData.budgets
    if (budgets.isNotEmpty()) {
        item(key = "ov-budgets") {
            SectionCard("Anggaran", action = { TextButton(onClick = { actions.setTab(FinanceTab.Budget) }) { Text("Semua") } }) {
                budgets.sortedByDescending { it.fraction }.take(3).forEach { b ->
                    val color = levelColor(b.level)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row {
                            Text(b.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text("${Rupiah.compact(b.spent)} / ${Rupiah.compact(b.amount)}", style = MaterialTheme.typography.labelMedium, color = color)
                        }
                        LinearProgressIndicator(
                            progress = { b.fraction.coerceIn(0f, 1f) },
                            color = color,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            drawStopIndicator = {},
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(3.dp)),
                        )
                    }
                }
            }
        }
    }
    item(key = "ov-recent") { Column {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("TRANSAKSI TERAKHIR", style = TaskifyTextStyles.indexLabel, color = TaskifyTheme.modules.accent(TaskifyModule.Finance), modifier = Modifier.weight(1f).semantics { heading() })
            TextButton(onClick = { actions.setTab(FinanceTab.Transactions) }) { Text("Lihat semua") }
        }
        if (state.loaded && state.monthData.recent.isEmpty()) {
            EmptyState(TaskifyModule.Finance, "Belum ada transaksi bulan ini", "Ketuk + untuk mencatat, atau ketik \"kopi 25rb\" di Tangkap.")
        }
    } }
    state.monthData.recent.forEach { tx ->
        item(key = "ov-r-${tx.id}") { TxRow(tx, onOpen = { onOpenTx(tx.id) }, onDelete = null) }
    }
}

private fun dailyAverage(state: FinanceUiState): Long {
    val days = if (state.month == Dates.monthStart(state.today)) Dates.parse(state.today).dayOfMonth else Dates.parse(state.month).lengthOfMonth()
    return if (days <= 0) 0 else state.monthData.totals.expense / days
}

@Composable
fun levelColor(level: BudgetLevel) = when (level) {
    BudgetLevel.Ok -> incomeColor()
    BudgetLevel.Warn -> namedColor("amber")
    BudgetLevel.Over -> expenseColor()
}

@Composable
private fun StatTile(label: String, value: Long, icon: ImageVector, tint: androidx.compose.ui.graphics.Color, modifier: Modifier, signed: Boolean = false) {
    val text = if (signed && value > 0) "+" + Rupiah.compact(value) else Rupiah.compact(value)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$label ${Rupiah.format(value)}" }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            FitText(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FitText(if (value == 0L) "Rp 0" else "Rp $text", style = MaterialTheme.typography.titleMedium, color = if (signed) tint else MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Tagihan berulang manual yang sudah jatuh tempo: Catat / Lewati. */
@Composable
fun DueCard(items: List<Recurring>, today: String, actions: FinanceActions) {
    SectionCard(
        "Jatuh tempo",
        subtitle = "${items.size} tagihan menunggu konfirmasi",
        container = namedColor("amber").copy(alpha = 0.12f),
        action = { Icon(Icons.Outlined.EventRepeat, null, tint = namedColor("amber")) },
    ) {
        items.forEach { r ->
            val next = r.nextRunOn.orEmpty()
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(r.categoryIcon, r.categoryColor, size = 36.dp, transfer = r.kind == TxKind.Transfer)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            Dates.relativeDayLabel(next, today),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (next < today) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(Rupiah.format(r.amount), style = MaterialTheme.typography.titleSmall)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { actions.skipOccurrence(r) }) { Text("Lewati") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { actions.postOccurrence(r) }) { Text("Catat") }
                }
            }
        }
    }
}
