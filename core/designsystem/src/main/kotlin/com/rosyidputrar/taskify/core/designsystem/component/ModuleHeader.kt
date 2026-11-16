package com.rosyidputrar.taskify.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme

/** Tab indeks kecil: ikon + nama modul kapital di atas tinta warna modul. */
@Composable
fun IndexTab(module: TaskifyModule, modifier: Modifier = Modifier, text: String = module.label) {
    Row(
        modifier
            .background(TaskifyTheme.modules.accent(module), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomEnd = 8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(module.icon, contentDescription = null, tint = TaskifyTheme.modules.onAccent(module), modifier = Modifier.size(14.dp))
        Text(text.uppercase(), style = TaskifyTextStyles.indexLabel, color = TaskifyTheme.modules.onAccent(module))
    }
}

/**
 * Kepala layar modul: tab indeks, judul serif besar, dan subjudul.
 * [navigation] (mis. tombol kembali) tampil di kiri tab, [actions] di kanan.
 */
@Composable
fun ModuleHeader(
    module: TaskifyModule,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (navigation != null) {
                navigation()
                Spacer(Modifier.width(4.dp))
            }
            IndexTab(module)
            Spacer(Modifier.weight(1f))
            actions()
        }
        Spacer(Modifier.size(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.size(12.dp))
        RuledLine()
    }
}

/** Garis bergaris ala kertas bergaris, pemisah antar bagian. */
@Composable
fun RuledLine(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.width(28.dp).size(28.dp, 2.dp).background(MaterialTheme.colorScheme.onBackground))
        Box(Modifier.weight(1f).size(1.dp, 2.dp).background(MaterialTheme.colorScheme.outlineVariant))
    }
}
