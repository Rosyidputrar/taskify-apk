package com.rosyidputrar.taskify.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Geser ke KIRI untuk menghapus (biasanya ke Sampah, dengan Urungkan di snackbar). Latar merah
 * muncul bertahap mengikuti jari, ikon membesar saat ambang terlewati + getar. Juga tersedia
 * sebagai aksi aksesibilitas "[label]" agar tidak hanya bisa lewat gestur.
 *
 * [enabled] = false → konten tampil biasa (mis. di tampilan Sampah).
 */
@Composable
fun SwipeToDelete(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.large,
    label: String = "Hapus",
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        Box(modifier) { content() }
        return
    }
    val state = rememberSwipeToDismissBoxState(positionalThreshold = { it * 0.4f })
    val haptics = rememberHaptics()
    LaunchedEffect(state.targetValue) {
        if (state.targetValue == SwipeToDismissBoxValue.EndToStart) haptics.threshold()
    }
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
            state.snapTo(SwipeToDismissBoxValue.Settled)
        }
    }
    val armed = state.targetValue == SwipeToDismissBoxValue.EndToStart
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        modifier = modifier.semantics { customActions = listOf(CustomAccessibilityAction(label) { onDelete(); true }) },
        backgroundContent = {
            val swiping = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
            val bg by animateColorAsState(
                when { armed -> MaterialTheme.colorScheme.error; swiping -> MaterialTheme.colorScheme.errorContainer; else -> Color.Transparent },
                label = "latar hapus",
            )
            val fg = if (armed) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onErrorContainer
            val iconScale by animateFloatAsState(if (armed) 1.2f else 1f, label = "ikon hapus")
            Row(
                Modifier.fillMaxSize().clip(shape).background(bg).padding(horizontal = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (swiping) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = fg, modifier = Modifier.scale(iconScale))
                }
            }
        },
    ) { content() }
}
