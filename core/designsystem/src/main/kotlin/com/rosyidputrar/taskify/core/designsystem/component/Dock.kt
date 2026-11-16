package com.rosyidputrar.taskify.core.designsystem.component

import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

data class DockDestination(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val accent: Color,
)

/**
 * Dok mengambang: tujuan di kiri & kanan, tombol Tangkap di tengah.
 * - Ketuk ✚ = catat cepat, tahan = pilih jenis.
 * - Tarik dok ke atas = Spotlight (pencarian global); panelnya ikut tertarik mengikuti jari
 *   ([onPull] tiap pergeseran px, [onPullEnd] dengan kecepatan saat dilepas).
 * Tujuan aktif melebar menampilkan labelnya (pola ExpandableBottomBar).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskifyDock(
    leading: List<DockDestination>,
    trailing: List<DockDestination>,
    selectedKey: String?,
    onSelect: (DockDestination) -> Unit,
    onCapture: () -> Unit,
    onCaptureLongPress: () -> Unit,
    onPull: (deltaY: Float) -> Unit,
    onPullEnd: (velocityY: Float) -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Column(
        modifier.draggable(
            rememberDraggableState { delta -> onPull(delta) },
            Orientation.Vertical,
            onDragStopped = { velocity -> onPullEnd(velocity) },
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Pegangan kecil: petunjuk bahwa dok bisa ditarik ke atas.
        Box(
            Modifier
                .padding(bottom = 6.dp)
                .size(width = 36.dp, height = 4.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
                .semantics {
                    contentDescription = "Tarik ke atas untuk mencari"
                    // TalkBack tidak bisa menyeret: sediakan aksi langsung.
                    customActions = listOf(CustomAccessibilityAction("Buka pencarian") { onOpenSearch(); true })
                },
        )
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 0.dp,
            modifier = Modifier.shadow(12.dp, RoundedCornerShape(32.dp), clip = false),
        ) {
            Row(
                Modifier.padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                leading.forEach { DockItem(it, it.key == selectedKey) { if (it.key != selectedKey) haptics.tick(); onSelect(it) } }
                CaptureButton(
                    onClick = { haptics.tap(); onCapture() },
                    onLongClick = {
                        haptics.longPress()
                        onCaptureLongPress()
                    },
                )
                trailing.forEach { DockItem(it, it.key == selectedKey) { if (it.key != selectedKey) haptics.tick(); onSelect(it) } }
            }
        }
    }
}

@Composable
private fun DockItem(destination: DockDestination, selected: Boolean, onClick: () -> Unit) {
    val container by animateColorAsState(
        if (selected) destination.accent.copy(alpha = 0.16f) else Color.Transparent,
        label = "dockItemContainer",
    )
    val content by animateColorAsState(
        if (selected) destination.accent else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "dockItemContent",
    )
    Row(
        Modifier
            .clip(RoundedCornerShape(26.dp))
            .background(container)
            .semantics {
                this.selected = selected
                role = Role.Tab
            }
            .combinedClickable(onClick = onClick)
            .height(52.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(destination.icon, contentDescription = if (selected) null else destination.label, tint = content)
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally(),
        ) {
            Row {
                Spacer(Modifier.width(8.dp))
                Text(destination.label, style = MaterialTheme.typography.labelLarge, color = content)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CaptureButton(onClick: () -> Unit, onLongClick: () -> Unit) {
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .size(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .semantics {
                contentDescription = "Tangkap cepat"
                role = Role.Button
                onLongClick(label = "Pilih jenis catatan") { onLongClick(); true }
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(30.dp))
    }
}
