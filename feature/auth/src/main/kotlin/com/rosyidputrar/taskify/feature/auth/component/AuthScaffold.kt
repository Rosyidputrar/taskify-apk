package com.rosyidputrar.taskify.feature.auth.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.designsystem.component.IndexTab
import com.rosyidputrar.taskify.core.designsystem.component.RuledLine
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule

/**
 * Kerangka layar gerbang (masuk, verifikasi, kunci): tab indeks, judul serif besar, lalu isi di
 * atas "lembar kertas". Tanpa dok & navigasi — aplikasi belum terbuka.
 */
@Composable
fun AuthScaffold(
    tab: String,
    title: String,
    subtitle: String?,
    module: TaskifyModule = TaskifyModule.Home,
    footer: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp)) {
                IndexTab(module, text = tab)
                Spacer(Modifier.height(16.dp))
                Text(title, style = MaterialTheme.typography.displaySmall, modifier = Modifier.semantics { heading() })
                if (subtitle != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(16.dp))
                RuledLine()
                Spacer(Modifier.height(24.dp))
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
                }
                Spacer(Modifier.height(20.dp))
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, content = footer)
            }
        }
    }
}

@Composable
fun ErrorText(message: String?) {
    if (message != null) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
}
