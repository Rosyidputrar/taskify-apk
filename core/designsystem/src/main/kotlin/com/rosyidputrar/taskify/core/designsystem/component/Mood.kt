package com.rosyidputrar.taskify.core.designsystem.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material.icons.outlined.SentimentNeutral
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.SentimentVeryDissatisfied
import androidx.compose.material.icons.outlined.SentimentVerySatisfied
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme

/** Ikon mood 1–5 (ikon, bukan emoji, agar konsisten di semua perangkat — sama dengan webapp). */
val MoodIcons: List<ImageVector> = listOf(
    Icons.Outlined.SentimentVeryDissatisfied,
    Icons.Outlined.SentimentDissatisfied,
    Icons.Outlined.SentimentNeutral,
    Icons.Outlined.SentimentSatisfied,
    Icons.Outlined.SentimentVerySatisfied,
)

fun moodIcon(mood: Int?): ImageVector? = mood?.let { MoodIcons.getOrNull(it - 1) }

/** Warna tinta per mood 1–5: merah bata → jingga → kunyit → hijau daun → hijau laut. */
private val MoodLight = listOf(Color(0xFFB3412F), Color(0xFFC96A1E), Color(0xFFAE8A12), Color(0xFF4F8A3A), Color(0xFF237A6E))
private val MoodDark = listOf(Color(0xFFE77A66), Color(0xFFF0A260), Color(0xFFE2C154), Color(0xFF8CC57A), Color(0xFF6CC7B8))

@Composable
fun moodColor(mood: Int?): Color {
    val palette = if (TaskifyTheme.isDark) MoodDark else MoodLight
    return mood?.let { palette.getOrNull(it - 1) } ?: MaterialTheme.colorScheme.outlineVariant
}
