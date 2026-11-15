package com.rosyidputrar.taskify.core.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.domain.editor.SaveState

/**
 * Status simpan otomatis, sengaja ringkas agar muat di bilah atas HP:
 * Draf · Mengetik… · Menyimpan… · Tersimpan · Gagal (ketuk = coba lagi).
 */
@Composable
fun SaveIndicator(state: SaveState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    AnimatedContent(state, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "save", modifier = modifier) { s ->
        val (icon, text) = when (s) {
            SaveState.Idle -> Icons.Outlined.EditNote to "Draf"
            SaveState.Pending -> Icons.Outlined.EditNote to "Mengetik…"
            SaveState.Saving -> Icons.Outlined.Sync to "Menyimpan…"
            SaveState.Saved -> Icons.Outlined.CloudDone to "Tersimpan"
            SaveState.Error -> Icons.Outlined.ErrorOutline to "Gagal"
        }
        val tint = if (s == SaveState.Error) colors.error else colors.onSurfaceVariant
        Row(
            Modifier
                .then(if (s == SaveState.Error) Modifier.clickable(onClickLabel = "Coba simpan lagi", onClick = onRetry) else Modifier)
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1)
        }
    }
}
