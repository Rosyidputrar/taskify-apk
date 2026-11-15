package com.rosyidputrar.taskify.core.designsystem.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material.icons.outlined.SentimentNeutral
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.SentimentVeryDissatisfied
import androidx.compose.material.icons.outlined.SentimentVerySatisfied
import androidx.compose.ui.graphics.vector.ImageVector

/** Ikon mood 1–5 (ikon, bukan emoji, agar konsisten di semua perangkat — sama dengan webapp). */
val MoodIcons: List<ImageVector> = listOf(
    Icons.Outlined.SentimentVeryDissatisfied,
    Icons.Outlined.SentimentDissatisfied,
    Icons.Outlined.SentimentNeutral,
    Icons.Outlined.SentimentSatisfied,
    Icons.Outlined.SentimentVerySatisfied,
)

fun moodIcon(mood: Int?): ImageVector? = mood?.let { MoodIcons.getOrNull(it - 1) }
