package com.rosyidputrar.taskify.ui.room

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.designsystem.component.IndexCard
import com.rosyidputrar.taskify.core.designsystem.component.RuledLine
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme

/** Modul yang sudah selalu ada di dok, jadi tidak perlu disematkan. */
private val alwaysInDock = setOf(TaskifyModule.Home, TaskifyModule.Calendar)

/**
 * Ruang: peluncur semua modul berupa grid kartu indeks.
 * Tahan kartu untuk menyematkan modul itu ke dok.
 */
@Composable
fun RoomScreen(
    contentPadding: PaddingValues,
    pinnedModule: TaskifyModule?,
    onOpenModule: (TaskifyModule) -> Unit,
    onPinnedModuleChange: (TaskifyModule?) -> Unit,
) {
    var pinCandidate by rememberSaveable { mutableStateOf<TaskifyModule?>(null) }
    val layoutDirection = LocalLayoutDirection.current
    val gridPadding = PaddingValues(
        start = contentPadding.calculateStartPadding(layoutDirection) + 20.dp,
        end = contentPadding.calculateEndPadding(layoutDirection) + 20.dp,
        top = contentPadding.calculateTopPadding(),
        bottom = contentPadding.calculateBottomPadding(),
    )

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 156.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = gridPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(top = 16.dp, bottom = 4.dp)) {
                Text("SEMUA MODUL", style = TaskifyTextStyles.indexLabel, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text("Ruang", style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
                Text(
                    "Tahan sebuah kartu untuk menyematkannya ke dok.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                RuledLine()
            }
        }
        items(TaskifyModule.main, key = { it.name }) { module ->
            ModuleCard(module, pinned = module == pinnedModule, onOpen = { onOpenModule(module) }, onLongPress = {
                if (module !in alwaysInDock) pinCandidate = module
            })
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Lintas modul",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 12.dp).semantics { heading() },
            )
        }
        items(TaskifyModule.extras, key = { it.name }) { module ->
            ModuleCard(module, pinned = module == pinnedModule, onOpen = { onOpenModule(module) }, onLongPress = {
                pinCandidate = module
            })
        }
    }

    pinCandidate?.let { module ->
        val isPinned = module == pinnedModule
        AlertDialog(
            onDismissRequest = { pinCandidate = null },
            title = { Text(if (isPinned) "Lepas dari dok?" else "Sematkan ke dok?") },
            text = {
                Text(
                    if (isPinned) {
                        "${module.label} tidak lagi tampil di dok. Tetap bisa dibuka dari Ruang."
                    } else {
                        "${module.label} tampil di dok di samping Hari Ini" +
                            (pinnedModule?.let { ", menggantikan ${it.label}." } ?: ".")
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onPinnedModuleChange(if (isPinned) null else module)
                    pinCandidate = null
                }) { Text(if (isPinned) "Lepas" else "Sematkan") }
            },
            dismissButton = { TextButton(onClick = { pinCandidate = null }) { Text("Batal") } },
        )
    }
}

@Composable
private fun ModuleCard(module: TaskifyModule, pinned: Boolean, onOpen: () -> Unit, onLongPress: () -> Unit) {
    IndexCard(
        module,
        onClick = onOpen,
        onLongClick = onLongPress,
        onClickLabel = "Buka ${module.label}",
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 14.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(module.icon, contentDescription = null, tint = TaskifyTheme.modules.accent(module), modifier = Modifier.size(26.dp))
            Spacer(Modifier.weight(1f))
            if (pinned) {
                Icon(
                    Icons.Filled.PushPin,
                    contentDescription = "Disematkan di dok",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(module.label, style = MaterialTheme.typography.titleMedium)
        Text(
            module.tagline,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            minLines = 2,
            maxLines = 2,
        )
    }
}
