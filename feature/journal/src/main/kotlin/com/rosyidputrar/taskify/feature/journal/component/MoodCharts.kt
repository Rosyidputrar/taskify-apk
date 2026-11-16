package com.rosyidputrar.taskify.feature.journal.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.designsystem.component.MoodIcons
import com.rosyidputrar.taskify.core.designsystem.component.moodColor
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.calendar.WEEKDAY_LABELS
import com.rosyidputrar.taskify.core.domain.journal.MOOD_LABELS
import com.rosyidputrar.taskify.core.domain.journal.MoodPoint
import com.rosyidputrar.taskify.core.domain.time.Dates
import kotlin.math.roundToInt

/**
 * Garis mood per hari. Sumbu kiri = ikon mood 1–5; hari tanpa mood memutus garis (tidak
 * diinterpolasi), jadi celah menulis terlihat jujur.
 */
@Composable
fun MoodLineChart(points: List<MoodPoint>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val line = TaskifyTheme.modules.accent(TaskifyModule.Journal)
    val palette = moodPalette()
    val grid = colors.outlineVariant
    val withMood = points.filter { it.mood != null }
    val summary = if (withMood.isEmpty()) "Belum ada mood tercatat" else
        "Grafik mood ${points.size} hari: ${withMood.size} hari tercatat, rata-rata ${"%.1f".format(withMood.sumOf { it.mood!! } / withMood.size)}"
    Column(modifier.clearAndSetSemantics { contentDescription = summary }) {
        Row(Modifier.fillMaxWidth().height(150.dp)) {
            Column(Modifier.fillMaxHeight().padding(end = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
                MoodIcons.reversed().forEachIndexed { i, icon -> Icon(icon, null, tint = palette[4 - i].copy(alpha = 0.8f), modifier = Modifier.size(16.dp)) }
            }
            Canvas(Modifier.weight(1f).fillMaxHeight().padding(vertical = 8.dp)) {
                val n = points.size
                if (n == 0) return@Canvas
                val stepX = if (n > 1) size.width / (n - 1) else 0f
                fun y(m: Double) = (size.height * (1 - (m - 1) / 4.0)).toFloat()
                for (level in 1..5) {
                    val yy = y(level.toDouble())
                    drawLine(grid, Offset(0f, yy), Offset(size.width, yy), strokeWidth = 1.dp.toPx())
                }
                var path: Path? = null
                points.forEachIndexed { i, p ->
                    val m = p.mood
                    if (m == null) {
                        path?.let { drawPath(it, line, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)) }
                        path = null
                    } else {
                        val o = Offset(i * stepX, y(m))
                        path = (path ?: Path().apply { moveTo(o.x, o.y) }).apply { lineTo(o.x, o.y) }
                    }
                }
                path?.let { drawPath(it, line, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)) }
                points.forEachIndexed { i, p ->
                    val m = p.mood ?: return@forEachIndexed
                    val c = palette[(m.roundToInt() - 1).coerceIn(0, 4)]
                    drawCircle(colors.surfaceContainerLow, radius = 5.dp.toPx(), center = Offset(i * stepX, y(m)))
                    drawCircle(c, radius = 3.5.dp.toPx(), center = Offset(i * stepX, y(m)))
                }
            }
        }
        if (points.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, top = 4.dp)) {
                val label = MaterialTheme.typography.labelSmall
                Text(Dates.format(points.first().day, "d MMM"), style = label, color = colors.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(Dates.format(points[points.size / 2].day, "d MMM"), style = label, color = colors.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text("Hari ini", style = label, color = colors.onSurfaceVariant)
            }
        }
    }
}

/** Batang sebaran: berapa hari untuk tiap mood. */
@Composable
fun MoodDistribution(distribution: List<Int>, modifier: Modifier = Modifier) {
    val max = (distribution.maxOrNull() ?: 0).coerceAtLeast(1)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        distribution.forEachIndexed { i, n ->
            val color = moodColor(i + 1)
            Column(
                Modifier.weight(1f).clearAndSetSemantics { contentDescription = "${MOOD_LABELS[i]}: $n hari" },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(Modifier.height(56.dp), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.width(22.dp).height((56 * n / max).coerceAtLeast(4).dp).background(color.copy(alpha = if (n == 0) 0.25f else 1f), RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)))
                }
                Icon(MoodIcons[i], null, tint = color, modifier = Modifier.size(20.dp))
                Text("$n", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Rata-rata mood per hari dalam minggu (Sen–Min). */
@Composable
fun WeekdayMood(byWeekday: List<Double?>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        byWeekday.forEachIndexed { i, avg ->
            val mood = avg?.roundToInt()?.coerceIn(1, 5)
            val desc = WEEKDAY_LABELS[i] + ": " + (avg?.let { "rata-rata ${"%.1f".format(it)}" } ?: "belum ada data")
            Column(Modifier.weight(1f).clearAndSetSemantics { contentDescription = desc }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.height(64.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxWidth(0.7f)
                            .height(if (avg == null) 4.dp else (64 * (avg / 5.0)).dp)
                            .background(if (mood == null) colors.outlineVariant else moodColor(mood), RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)),
                    )
                }
                Text(WEEKDAY_LABELS[i], style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
    }
}
