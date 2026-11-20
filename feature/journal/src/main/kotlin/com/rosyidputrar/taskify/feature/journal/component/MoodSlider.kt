package com.rosyidputrar.taskify.feature.journal.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.designsystem.component.MoodIcons
import com.rosyidputrar.taskify.core.designsystem.component.moodColor
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.domain.journal.MOOD_LABELS

/**
 * Penggeser mood 1–5: lima ikon di atas rel; "manik" berwarna mood meluncur ke ikon terpilih.
 * Ketuk ikon untuk memilih (ketuk lagi = hapus), atau seret sepanjang rel. Bagi pembaca layar,
 * setiap ikon adalah tombol radio dengan label mood.
 */
@Composable
fun MoodSlider(value: Int?, onChange: (Int?) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    var drag by remember { mutableStateOf<Int?>(null) }
    val shown = drag ?: value
    val colors = MaterialTheme.colorScheme
    val tint by animateColorAsState(moodColor(shown), label = "moodTint")
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PERASAANMU", style = TaskifyTextStyles.indexLabel, color = colors.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(shown?.let { MOOD_LABELS[it - 1] } ?: "Belum dipilih", style = MaterialTheme.typography.labelLarge, color = if (shown != null) tint else colors.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(colors.surfaceContainerHigh, RoundedCornerShape(28.dp))
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    val slot = { x: Float -> (x / (size.width / 5f)).toInt().coerceIn(0, 4) + 1 }
                    detectHorizontalDragGestures(
                        onDragStart = { drag = slot(it.x) },
                        onDragEnd = { drag?.let(onChange); drag = null },
                        onDragCancel = { drag = null },
                        onHorizontalDrag = { change, _ -> drag = slot(change.position.x) },
                    )
                },
        ) {
            val slotWidth = maxWidth / 5
            val target = if (shown == null) 0.dp else slotWidth * (shown - 1) + (slotWidth - 48.dp) / 2
            val x by animateDpAsState(target, spring(dampingRatio = 0.7f, stiffness = 500f), label = "moodThumb")
            if (shown != null) {
                Box(Modifier.offset(x = x).padding(vertical = 4.dp).size(48.dp).background(tint, CircleShape))
            }
            Row(Modifier.fillMaxSize().selectableGroup()) {
                MoodIcons.forEachIndexed { index, icon ->
                    val mood = index + 1
                    val selected = shown == mood
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = { onChange(if (value == mood) null else mood) })
                            .semantics { contentDescription = MOOD_LABELS[index] },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = null, tint = if (selected) colors.surface else colors.onSurfaceVariant, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

/** Untuk kalkulasi di luar komposisi (dipakai grafik). */
@Composable
internal fun moodPalette() = (1..5).map { moodColor(it) }

