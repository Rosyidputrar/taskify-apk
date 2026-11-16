package com.rosyidputrar.taskify.feature.tasks.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Cincin progres kecil (pola CircleProgress): busur terisi = subtask selesai. */
@Composable
fun ProgressRing(
    done: Int,
    total: Int,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    stroke: Dp = 3.dp,
    showLabel: Boolean = false,
) {
    val track = MaterialTheme.colorScheme.outlineVariant
    val fraction = if (total == 0) 0f else done.toFloat() / total
    Box(modifier.size(size).semantics { contentDescription = "$done dari $total subtask selesai" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = Stroke(width = stroke.toPx(), cap = StrokeCap.Round)
            drawArc(track, 0f, 360f, useCenter = false, style = s)
            if (fraction > 0f) drawArc(color, -90f, 360f * fraction, useCenter = false, style = s)
        }
        if (showLabel) Text("$done/$total", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
