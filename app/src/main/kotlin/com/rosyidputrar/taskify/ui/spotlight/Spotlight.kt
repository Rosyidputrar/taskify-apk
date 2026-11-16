package com.rosyidputrar.taskify.ui.spotlight

import com.rosyidputrar.taskify.core.designsystem.component.rememberHaptics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
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
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.rosyidputrar.taskify.core.designsystem.theme.namedColor
import com.rosyidputrar.taskify.core.domain.files.EntityType
import com.rosyidputrar.taskify.core.domain.search.EntityRow
import com.rosyidputrar.taskify.core.domain.search.Search
import com.rosyidputrar.taskify.core.domain.search.TagHit
import com.rosyidputrar.taskify.core.domain.search.label
import com.rosyidputrar.taskify.core.store.search.SearchRepository
import com.rosyidputrar.taskify.core.ui.describe
import com.rosyidputrar.taskify.core.ui.icon
import com.rosyidputrar.taskify.core.ui.module
import kotlinx.coroutines.delay
import org.koin.core.context.GlobalContext
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

    data class Entity(val row: EntityRow) : SpotlightResult {
        override val title = row.title.ifBlank { "(tanpa judul)" }
        override val subtitle = row.describe()
    }

    data class Tag(val hit: TagHit) : SpotlightResult {
        override val title = "#${hit.name}"
        override val subtitle = "${hit.count} item bertag"
    }

    data class Theme(val mode: ThemeMode) : SpotlightResult {
        override val title = "Tema: ${mode.label}"
        override val subtitle = "Ubah tampilan aplikasi"
    }
}

/**
 * Spotlight = pencarian global & command palette. Dibuka dengan menarik dok ke atas (panel
 * mengikuti jari), Ctrl/⌘ + K, atau shortcut launcher "Cari". Ditutup dengan menarik panel ke
 * bawah, ketuk latar, atau tombol kembali.
 */
@Composable
fun Spotlight(
    state: SpotlightSheetState,
    onOpenModule: (TaskifyModule) -> Unit,
    onCapture: (String) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenEntity: (EntityType, String) -> Unit = { _, _ -> },
    onOpenTag: (String) -> Unit = {},
) {
    val haptics = rememberHaptics()
    var screenHeight by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier.fillMaxSize().onSizeChanged {
            screenHeight = it.height.toFloat()
            // Separuh tinggi layar = terbuka penuh: tarikan terasa 1:1 dengan jari.
            state.travelPx = it.height * 0.5f
        },
    )
    if (!state.visible) return
    BackHandler(onBack = state::close)
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = state.progress }
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = state::close),
    )
    // Seret ke bawah dari daftar yang sudah di paling atas = menutup (seperti bottom sheet).
    val nested = remember(state) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Panel belum penuh & jari naik: naikkan panel dulu sebelum daftar bergulir.
                if (source == NestedScrollSource.UserInput && available.y < 0 && state.progress < 1f) {
                    state.dragBy(available.y)
                    return available
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0) {
                    state.dragBy(available.y)
                    return available
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!state.dragging) return Velocity.Zero
                state.release(available.y)
                return available
            }
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .graphicsLayer {
                val p = state.progress
                // Naik dari bawah mengikuti jari; muncul cepat di awal tarikan.
                translationY = (1f - p) * screenHeight * 0.6f
                alpha = (p * 2.5f).coerceAtMost(1f)
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        SpotlightPanel(
            state = state,
            onDismiss = state::close,
            onOpenModule = onOpenModule,
            onCapture = onCapture,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            onOpenEntity = onOpenEntity,
            onOpenTag = onOpenTag,
            modifier = Modifier
                .nestedScroll(nested)
                .draggable(
                    rememberDraggableState { delta -> if (state.dragBy(delta)) haptics.threshold() },
                    Orientation.Vertical,
                    onDragStopped = { velocity -> state.release(velocity) },
                ),
        )
    }
}

@Composable
private fun SpotlightPanel(
    state: SpotlightSheetState,
    onDismiss: () -> Unit,
    onOpenModule: (TaskifyModule) -> Unit,
    onCapture: (String) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenEntity: (EntityType, String) -> Unit,
    onOpenTag: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    // Pencarian isi (FTS5 lokal) — debounce 120 ms seperti webapp; tanpa repository = modul saja.
    val search = remember { runCatching { GlobalContext.getOrNull()?.getOrNull<SearchRepository>() }.getOrNull() }
    var found by remember { mutableStateOf<Pair<List<EntityRow>, List<TagHit>>>(emptyList<EntityRow>() to emptyList()) }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isEmpty() || search == null) { found = emptyList<EntityRow>() to emptyList(); return@LaunchedEffect }
        delay(120)
        found = runCatching { search.search(q) to search.searchTags(q) }.getOrDefault(emptyList<EntityRow>() to emptyList())
    }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Keyboard baru muncul setelah panel terbuka penuh & diam — tidak melompat di tengah tarikan.
    LaunchedEffect(state.settledOpen) {
        if (state.settledOpen) runCatching { focusRequester.requestFocus() } else focusManager.clearFocus()
    }

    val results = remember(query, themeMode, found) { buildResults(query.trim(), themeMode, found.first, found.second) }
    val run: (SpotlightResult) -> Unit = { result ->
        when (result) {
            is SpotlightResult.Module -> onOpenModule(result.module)
            is SpotlightResult.Capture -> onCapture(result.text)
            is SpotlightResult.Theme -> {
                onThemeModeChange(result.mode)
                onDismiss()
            }
            is SpotlightResult.Entity -> onOpenEntity(result.row.type, result.row.id)
            is SpotlightResult.Tag -> onOpenTag(result.hit.id)
        }
        query = ""
    }

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 16.dp,
        modifier = modifier
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
                items(results, key = { r -> if (r is SpotlightResult.Entity) r.row.type.value + r.row.id else r.title }) { result ->
                    ResultRow(result, onClick = { run(result) })
                }
                if (query.isBlank()) item {
                    Text(
                        "Ketik untuk mencari isi tugas, jurnal, catatan, transaksi, dan Vault — juga saat offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

private fun buildResults(query: String, themeMode: ThemeMode, hits: List<EntityRow>, tags: List<TagHit>): List<SpotlightResult> {
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
    val content = hits.map { SpotlightResult.Entity(it) } + tags.map { SpotlightResult.Tag(it) }
    return if (modules.isEmpty()) content + capture + themes else modules + content + capture + themes
}

@Composable
private fun ResultRow(result: SpotlightResult, onClick: () -> Unit) {
    val (icon: ImageVector, tint: Color) = when (result) {
        is SpotlightResult.Module -> result.module.icon to TaskifyTheme.modules.accent(result.module)
        is SpotlightResult.Capture -> Icons.Outlined.Bolt to MaterialTheme.colorScheme.primary
        is SpotlightResult.Theme -> Icons.Outlined.Contrast to MaterialTheme.colorScheme.onSurfaceVariant
        is SpotlightResult.Entity -> result.row.type.icon to TaskifyTheme.modules.accent(result.row.type.module)
        is SpotlightResult.Tag -> Icons.Outlined.Tag to namedColor(result.hit.color)
    }
    val kind = when (result) {
        is SpotlightResult.Module -> "MODUL"
        is SpotlightResult.Capture -> "TANGKAP"
        is SpotlightResult.Theme -> "PERINTAH"
        is SpotlightResult.Entity -> result.row.type.label.uppercase()
        is SpotlightResult.Tag -> "TAG"
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(result.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            val snippet = (result as? SpotlightResult.Entity)?.row?.snippet?.takeIf { it.contains(Search.MARK_OPEN) }
            if (snippet != null) {
                val hl = MaterialTheme.colorScheme.primary
                Text(
                    buildAnnotatedString {
                        Search.splitSnippet(snippet.replace(Regex("\\s+"), " ")).forEach { p ->
                            if (p.hit) withStyle(SpanStyle(color = hl, fontWeight = FontWeight.SemiBold)) { append(p.text) } else append(p.text)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                )
            } else {
                Text(result.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        Text(kind, style = TaskifyTextStyles.indexLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
