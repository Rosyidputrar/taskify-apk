package com.rosyidputrar.taskify.feature.finance.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.common.Rupiah
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.designsystem.theme.namedColor
import com.rosyidputrar.taskify.core.domain.finance.CategorySlice
import com.rosyidputrar.taskify.core.domain.finance.DayTotals
import com.rosyidputrar.taskify.core.domain.time.Dates

/** Warna pemasukan/pengeluaran (tinta hijau & bata), bukan merah/hijau neon. */
@Composable fun incomeColor(): Color = namedColor("emerald")
@Composable fun expenseColor(): Color = namedColor("red")

/**
 * Donat per kategori: tiap irisan berwarna kategori, celah tipis antar irisan, total di tengah.
 * Kategori kecil (< 3%) digabung "Lainnya" agar donat tetap terbaca.
 */
@Composable
fun CategoryDonut(slices: List<CategorySlice>, centerLabel: String, modifier: Modifier = Modifier, size: Dp = 168.dp) {
    val total = slices.sumOf { it.total }.coerceAtLeast(1)
    val colors = slices.map { namedColor(it.color) }
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val desc = "Donat $centerLabel ${Rupiah.format(slices.sumOf { it.total })}: " +
        slices.take(5).joinToString(", ") { "${it.name} ${(it.total * 100 / total)} persen" }
    Box(modifier.size(size).clearAndSetSemantics { contentDescription = desc }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 22.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            var start = -90f
            val gap = if (slices.size > 1) 2f else 0f
            slices.forEachIndexed { i, s ->
                val sweep = 360f * s.total / total
                if (sweep > gap) drawArc(colors[i], start + gap / 2, sweep - gap, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Butt))
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Rupiah.compact(slices.sumOf { it.total }), style = TaskifyTextStyles.numeric.copy(fontSize = TaskifyTextStyles.numeric.fontSize * 0.7f))
        }
    }
}

/** Gabungkan irisan kecil ke "Lainnya". */
fun compactSlices(slices: List<CategorySlice>, minShare: Double = 0.03, max: Int = 6): List<CategorySlice> {
    val total = slices.sumOf { it.total }.toDouble()
    if (total <= 0) return slices
    val (big, small) = slices.partition { it.total / total >= minShare }
    val keep = big.take(max)
    val rest = big.drop(max) + small
    return if (rest.isEmpty()) keep else keep + CategorySlice(null, "Lainnya", "slate", "circle-ellipsis", rest.sumOf { it.total }, rest.sumOf { it.count })
}

/**
 * Batang harian sebulan: pengeluaran ke atas (bata), pemasukan sebagai garis tipis hijau di
 * belakangnya. Skala mengikuti pengeluaran harian terbesar supaya pola belanja terlihat.
 */
@Composable
fun DailyBars(month: String, daily: Map<String, DayTotals>, today: String, modifier: Modifier = Modifier) {
    val days = Dates.parse(month).lengthOfMonth()
    val dates = (0 until days).map { Dates.addDays(month, it.toLong()) }
    val maxExpense = dates.maxOf { daily[it]?.expense ?: 0 }.coerceAtLeast(1)
    val expense = expenseColor()
    val income = incomeColor()
    val todayMark = MaterialTheme.colorScheme.onSurface
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val peak = dates.maxByOrNull { daily[it]?.expense ?: 0 }
    val desc = "Pengeluaran harian. Tertinggi ${peak?.let { Dates.format(it, "d MMMM") }}: ${Rupiah.format(daily[peak]?.expense ?: 0)}"
    Column(modifier.clearAndSetSemantics { contentDescription = desc }) {
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val slot = size.width / days
            val bar = (slot * 0.62f).coerceAtLeast(2f)
            dates.forEachIndexed { i, d ->
                val x = i * slot + (slot - bar) / 2
                val t = daily[d]
                drawRoundRect(track, Offset(x, size.height - 3.dp.toPx()), Size(bar, 3.dp.toPx()), CornerRadius(2f))
                val e = t?.expense ?: 0
                if (e > 0) {
                    val h = (size.height - 8.dp.toPx()) * e / maxExpense
                    drawRoundRect(expense, Offset(x, size.height - h), Size(bar, h), CornerRadius(bar / 2))
                }
                if ((t?.income ?: 0) > 0) drawCircle(income, radius = (bar / 2).coerceAtMost(4.dp.toPx()), center = Offset(x + bar / 2, 4.dp.toPx()))
                if (d == today) drawLine(todayMark, Offset(x + bar / 2, size.height + 2.dp.toPx()), Offset(x + bar / 2, size.height + 5.dp.toPx()), strokeWidth = 2.dp.toPx())
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            val style = MaterialTheme.typography.labelSmall
            val c = MaterialTheme.colorScheme.onSurfaceVariant
            Text("1", style = style, color = c)
            Spacer(Modifier.weight(1f))
            Text("15", style = style, color = c, textAlign = TextAlign.Center)
            Spacer(Modifier.weight(1f))
            Text("$days", style = style, color = c)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Legend(expense, "Pengeluaran")
            Legend(income, "Ada pemasukan")
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.size(8.dp)) { drawCircle(color) }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Busur progres anggaran 270° (gaya CircleProgress), warna menurut level. */
@Composable
fun BudgetArc(fraction: Float, color: Color, modifier: Modifier = Modifier, size: Dp = 56.dp, label: String) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 6.dp.toPx()
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            val o = Offset(stroke / 2, stroke / 2)
            drawArc(track, 135f, 270f, false, o, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(color, 135f, 270f * fraction.coerceIn(0f, 1f), false, o, arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}
