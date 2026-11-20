package com.rosyidputrar.taskify.core.designsystem.component

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tepi kiri/kanan memudar HANYA ke arah yang masih bisa digulir — isyarat bahwa baris bisa
 * digeser, bukan teks yang terpotong di tengah kata. Tepi yang sudah mentok tetap tajam.
 */
fun Modifier.fadingEdges(canScrollStart: () -> Boolean, canScrollEnd: () -> Boolean, width: Dp = 28.dp): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val w = width.toPx().coerceAtMost(size.width / 3)
        if (canScrollStart()) {
            drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = w),
                topLeft = Offset.Zero, size = size.copy(width = w), blendMode = BlendMode.DstIn,
            )
        }
        if (canScrollEnd()) {
            drawRect(
                Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - w, endX = size.width),
                topLeft = Offset(size.width - w, 0f), size = size.copy(width = w), blendMode = BlendMode.DstIn,
            )
        }
    }

/** [fadingEdges] untuk Row/TabRow dengan [ScrollState]. */
fun Modifier.fadingEdges(state: ScrollState, width: Dp = 28.dp): Modifier =
    fadingEdges({ state.value > 0 }, { state.value < state.maxValue }, width)

/** [fadingEdges] untuk LazyRow. */
fun Modifier.fadingEdges(state: LazyListState, width: Dp = 28.dp): Modifier =
    fadingEdges({ state.canScrollBackward }, { state.canScrollForward }, width)
