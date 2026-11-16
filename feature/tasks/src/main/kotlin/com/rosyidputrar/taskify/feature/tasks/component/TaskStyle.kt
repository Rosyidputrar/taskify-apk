package com.rosyidputrar.taskify.feature.tasks.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme

/** Warna cincin centang berdasarkan prioritas: tinggi = merah tinta Tugas, sedang = mustard, rendah = biru. */
@Composable
@ReadOnlyComposable
fun priorityColor(priority: Int): Color = when (priority) {
    3 -> TaskifyTheme.modules.accent(TaskifyModule.Tasks)
    2 -> TaskifyTheme.modules.accent(TaskifyModule.Notes)
    1 -> TaskifyTheme.modules.accent(TaskifyModule.Calendar)
    else -> MaterialTheme.colorScheme.outline
}
