package com.rosyidputrar.taskify.ui.spotlight

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.designsystem.theme.ThemeMode

private sealed interface SpotlightResult {
    val title: String
    val subtitle: String

    data class Module(val module: TaskifyModule) : SpotlightResult {
        override val title = module.label
        override val subtitle = module.tagline
    }

    data class Capture(val text: String) : SpotlightResult {
        override val title = "Tangkap \"$text\""
        override val subtitle = "Jadikan tugas, catatan, jurnal, atau transaksi"
    }

    data class Theme(val mode: ThemeMode) : SpotlightResult {
        override val title = "Tema: ${mode.label}"
        override val subtitle = "Ubah tampilan aplikasi"
    }
}

/**
 * Spotlight = pencarian global & command palette. Dibuka dengan geser dok ke atas,
 * Ctrl/⌘ + K, atau shortcut launcher "Cari".
 */
@Composable
fun Spotlight(
    visible: Boolean,
    onDismiss: () -> Unit,
    onOpenModule: (TaskifyModule) -> Unit,
    onCapture: (String) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    BackHandler(enabled = visible, onBack = onDismiss)
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { -it / 6 },
        exit = fadeOut() + slideOutVertically { -it / 6 },
    ) {
        Box(Modifier.fillMaxSize().statusBarsPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
            SpotlightPanel(
                onDismiss = onDismiss,
                onOpenModule = onOpenModule,
                onCapture = onCapture,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
            )
        }
    }
}

@Composable
private fun SpotlightPanel(
    onDismiss: () -> Unit,
    onOpenModule: (TaskifyModule) -> Unit,
    onCapture: (String) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val results = remember(query, themeMode) { buildResults(query.trim(), themeMode) }
    val run: (SpotlightResult) -> Unit = { result ->
        when (result) {
            is SpotlightResult.Module -> onOpenModule(result.module)
            is SpotlightResult.Capture -> onCapture(result.text)
            is SpotlightResult.Theme -> {
                onThemeModeChange(result.mode)
                onDismiss()
            }
        }
        query = ""
    }

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 16.dp,
        modifier = Modifier
            .padding(16.dp)
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            // Klik di dalam panel tidak boleh menutup Spotlight.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Column {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Cari atau ketik untuk menangkap…") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { results.firstOrNull()?.let(run) }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                textStyle = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().padding(4.dp).focusRequester(focusRequester),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LazyColumn(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                items(results, key = { it.title }) { result ->
                    ResultRow(result, onClick = { run(result) })
                }
                item {
                    Text(
                        "Pencarian isi tugas, catatan, jurnal, dan transaksi aktif setelah data tersinkron.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

private fun buildResults(query: String, themeMode: ThemeMode): List<SpotlightResult> {
    val all = TaskifyModule.main + TaskifyModule.extras
    val modules = if (query.isEmpty()) {
        all
    } else {
        all.filter { it.label.contains(query, ignoreCase = true) || it.tagline.contains(query, ignoreCase = true) }
    }.map { SpotlightResult.Module(it) }
    val themes = ThemeMode.entries
        .filter { it != themeMode }
        .filter { query.isEmpty() || "tema ${it.label}".contains(query, ignoreCase = true) }
        .map { SpotlightResult.Theme(it) }
    val capture = if (query.isNotEmpty()) listOf(SpotlightResult.Capture(query)) else emptyList()
    // Teks yang tidak cocok modul mana pun paling mungkin catat cepat → taruh di atas.
    return if (modules.isEmpty()) capture + themes else modules + capture + themes
}

@Composable
private fun ResultRow(result: SpotlightResult, onClick: () -> Unit) {
    val (icon: ImageVector, tint: Color) = when (result) {
        is SpotlightResult.Module -> result.module.icon to TaskifyTheme.modules.accent(result.module)
        is SpotlightResult.Capture -> Icons.Outlined.Bolt to MaterialTheme.colorScheme.primary
        is SpotlightResult.Theme -> Icons.Outlined.Contrast to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val kind = when (result) {
        is SpotlightResult.Module -> "MODUL"
        is SpotlightResult.Capture -> "TANGKAP"
        is SpotlightResult.Theme -> "PERINTAH"
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(result.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(result.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Text(kind, style = TaskifyTextStyles.indexLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
