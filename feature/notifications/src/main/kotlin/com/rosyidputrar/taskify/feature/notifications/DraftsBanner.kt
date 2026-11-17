package com.rosyidputrar.taskify.feature.notifications

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rosyidputrar.taskify.core.designsystem.component.IndexCard
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.store.device.NotificationLogRepository
import kotlinx.coroutines.flow.flowOf

/**
 * Kartu kecil "N transaksi terdeteksi dari notifikasi" untuk layar lain (Keuangan). Tidak tampil
 * bila tidak ada draf atau modul Notifikasi belum terpasang.
 */
@Composable
fun NotificationDraftsBanner(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val flow = remember {
        runCatching { org.koin.core.context.GlobalContext.getOrNull()?.getOrNull<NotificationLogRepository>()?.watchDraftCount() }.getOrNull() ?: flowOf(0)
    }
    val count by flow.collectAsStateWithLifecycle(0)
    if (count == 0) return
    IndexCard(
        TaskifyModule.Notifications, tinted = true, onClick = onOpen, onClickLabel = "Tinjau draf transaksi",
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = TaskifyTheme.modules.accent(TaskifyModule.Notifications))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("$count transaksi terdeteksi dari notifikasi", style = MaterialTheme.typography.titleSmall)
                Text("Tinjau dulu — tidak ada yang tercatat otomatis.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Tinjau", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}
