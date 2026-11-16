package com.rosyidputrar.taskify.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.designsystem.theme.namedColor
import com.rosyidputrar.taskify.core.domain.review.HeatDay
import com.rosyidputrar.taskify.core.domain.review.HeatGrid
import com.rosyidputrar.taskify.core.domain.review.Review
import com.rosyidputrar.taskify.core.domain.time.Dates

private val WEEKDAY = listOf("Sen", "", "Rab", "", "Jum", "", "Min")

/** Warna level 0–4: kertas kosong → tinta pekat (satu rona, terbaca di tema terang & gelap). */
@Composable
fun heatColor(level: Int): Color {
    val ink = namedColor("violet")
    return when (level) {
        0 -> MaterialTheme.colorScheme.surfaceContainerHighest
        1 -> ink.copy(alpha = 0.28f)
        2 -> ink.copy(alpha = 0.5f)
        3 -> ink.copy(alpha = 0.75f)
        else -> ink
    }
}

/**
 * Heatmap aktivitas: kolom = minggu, baris = Senin–Minggu. Ketuk hari → [onSelect]. Setiap sel
 * punya label lengkap untuk TalkBack; intensitas warna bukan satu-satunya informasi.
 */
@Composable
fun ActivityHeatmap(grid: HeatGrid, byDay: Map<String, HeatDay>, today: String, modifier: Modifier = Modifier, onSelect: ((String) -> Unit)? = null) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Layout kustom: ukuran sel dihitung dari lebar yang tersedia (tanpa SubcomposeLayout, yang
        // tidak mendukung pengukuran intrinsik kartu induk); label hari sejajar dengan barisnya.
        val weeks = grid.columns.size
        Layout(
            content = {
                WEEKDAY.forEach { d -> Text(d, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                grid.columns.forEach { col ->
                    col.forEach { day ->
                        if (day > today) {
                            Spacer(Modifier)
                        } else {
                            val d = byDay[day]
                            val label = Dates.format(day, "EEEE, d MMMM yyyy") + ": " + (d?.takeIf { it.score > 0 }?.describe() ?: "tidak ada aktivitas")
                            Box(
                                Modifier.background(heatColor(Review.heatLevel(d?.score ?: 0, grid.max)), RoundedCornerShape(3.dp))
                                    .then(if (onSelect != null) Modifier.clickable(role = Role.Button, onClickLabel = "Buka di Kalender") { onSelect(day) } else Modifier)
                                    .semantics { contentDescription = label },
                            )
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { measurables, constraints ->
            val labelW = 28.dp.roundToPx()
            val gap = 2.dp.roundToPx()
            val width = if (constraints.hasBoundedWidth) constraints.maxWidth else (labelW + weeks * 14.dp.roundToPx())
            val cell = ((width - labelW - gap * (weeks - 1)) / weeks).coerceAtLeast(4)
            val labels = measurables.take(7).map { it.measure(Constraints(maxWidth = labelW)) }
            val cells = measurables.drop(7).map { it.measure(Constraints.fixed(cell, cell)) }
            val height = cell * 7 + gap * 6
            layout(width, height) {
                labels.forEachIndexed { r, p -> p.place(0, r * (cell + gap) + (cell - p.height) / 2) }
                cells.forEachIndexed { i, p ->
                    val w = i / 7
                    val r = i % 7
                    p.place(labelW + w * (cell + gap), r * (cell + gap))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${grid.total} aktivitas · ${grid.activeDays} hari aktif", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("Sedikit ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            (0..4).forEach { l -> Box(Modifier.padding(horizontal = 1.dp).size(10.dp).background(heatColor(l), RoundedCornerShape(2.dp))) }
            Text(" Banyak", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

