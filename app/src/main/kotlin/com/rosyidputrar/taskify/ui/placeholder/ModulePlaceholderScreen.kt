package com.rosyidputrar.taskify.ui.placeholder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.designsystem.component.IndexCard
import com.rosyidputrar.taskify.core.designsystem.component.ModuleHeader
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme

@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali")
    }
}

/** Layar sementara untuk modul yang belum dibangun: menampilkan isi yang akan datang. */
@Composable
fun ModulePlaceholderScreen(module: TaskifyModule, contentPadding: PaddingValues, onBack: (() -> Unit)?) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item {
            ModuleHeader(
                module = module,
                title = module.label,
                subtitle = module.tagline,
                navigation = onBack?.let { { BackButton(it) } },
            )
        }
        item {
            IndexCard(module, tinted = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("SEDANG DIBANGUN", style = TaskifyTextStyles.indexLabel, color = TaskifyTheme.modules.accent(module))
                Spacer(Modifier.height(8.dp))
                Text("Yang akan ada di sini", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                ModuleRoadmap[module].orEmpty().forEach { line ->
                    Row(
                        Modifier.padding(vertical = 5.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier
                                .padding(top = 7.dp)
                                .size(7.dp)
                                .background(TaskifyTheme.modules.accent(module), CircleShape),
                        )
                        Text(line, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
