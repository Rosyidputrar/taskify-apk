package com.rosyidputrar.taskify.ui.tools

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rosyidputrar.taskify.core.designsystem.component.IndexCard
import com.rosyidputrar.taskify.core.designsystem.component.ModuleHeader
import com.rosyidputrar.taskify.core.designsystem.component.rememberHaptics
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTextStyles
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.designsystem.theme.namedColor
import com.rosyidputrar.taskify.core.domain.tools.CharDiff
import com.rosyidputrar.taskify.core.domain.tools.CharState
import com.rosyidputrar.taskify.core.domain.tools.ChatStory
import com.rosyidputrar.taskify.core.domain.tools.ChatVisualTheme
import com.rosyidputrar.taskify.core.domain.tools.SessionSummary
import com.rosyidputrar.taskify.core.domain.tools.TypingDifficulty
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt

@Composable
fun ToolsScreen(
    initialStoryId: String? = null,
    contentPadding: PaddingValues,
    backButton: @Composable (() -> Unit) -> Unit = {},
    onBack: (() -> Unit)? = null,
    onOpenAiSettings: () -> Unit = {},
    viewModel: ChatTypeViewModel = koinViewModel { parametersOf(initialStoryId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.mode == ChatTypeMode.Arena) {
        viewModel.backToLobby()
    }
    ToolsContent(
        state = state,
        actions = viewModel,
        contentPadding = contentPadding,
        backButton = backButton,
        onBack = onBack,
        onOpenAiSettings = onOpenAiSettings,
    )
}

@Composable
fun ToolsContent(
    state: ChatTypeUiState,
    actions: ChatTypeActions,
    contentPadding: PaddingValues,
    backButton: @Composable (() -> Unit) -> Unit = {},
    onBack: (() -> Unit)? = null,
    onOpenAiSettings: () -> Unit = {},
) {
    when (state.mode) {
        ChatTypeMode.Lobby -> ChatTypeLobby(state, actions, contentPadding, backButton, onBack)
        ChatTypeMode.Arena -> ChatTypeArena(state, actions, contentPadding)
    }
    if (state.showGeneratorSheet) {
        AiStoryGeneratorSheet(
            state = state,
            actions = actions,
            onOpenAiSettings = onOpenAiSettings,
        )
    }
}

// ===================================================================== Lobby Tools & Daftar Cerita

@Composable
private fun ChatTypeLobby(
    state: ChatTypeUiState,
    actions: ChatTypeActions,
    contentPadding: PaddingValues,
    backButton: @Composable (() -> Unit) -> Unit,
    onBack: (() -> Unit)?,
) {
    val haptics = rememberHaptics()
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Tools)
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = contentPadding,
    ) {
        item(key = "header") {
            ModuleHeader(
                module = TaskifyModule.Tools,
                title = "Tools · ChatType",
                subtitle = "Latihan mengetik cepat & akurasi bergaya chatting interaktif",
                navigation = onBack?.let { { backButton(it) } },
                actions = {
                    FilledTonalButton(onClick = { haptics.tap(); actions.openGeneratorSheet() }) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Cerita AI")
                    }
                },
            )
        }

        item(key = "hero-stats") {
            IndexCard(
                module = TaskifyModule.Tools,
                tinted = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                contentPadding = 14.dp,
            ) {
                Text("REKOR & GAYA ARENA", style = TaskifyTextStyles.indexLabel, color = accent)
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatColumn("REKOR WPM", if (state.highestWpmEver > 0) "${state.highestWpmEver}" else "—", "kata/menit", accent, Modifier.weight(1f))
                    StatColumn("AKURASI TERBAIK", if (state.bestAccuracy.isNotEmpty()) "${state.averageBestAccuracy}%" else "100%", "ketukan bersih", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                    StatColumn("EPISODE", "${state.completedSessions}", "tuntas", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Text("TEMA VISUAL CHAT", style = TaskifyTextStyles.indexLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChatVisualTheme.entries.forEach { t ->
                        FilterChip(
                            selected = state.theme == t,
                            onClick = { haptics.tick(); actions.setTheme(t) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        }

        item(key = "ai-banner") {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Generator Alur Cerita AI", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Buat skenario chat apa saja — wawancara kerja, debat produk, bahasa Inggris, atau misi hacker.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { haptics.tap(); actions.openGeneratorSheet() }) {
                        Text("Buat")
                    }
                }
            }
        }

        item(key = "stories-heading") {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Pilih Alur Percakapan (${state.stories.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
            }
        }

        items(state.stories, key = { it.id }) { story ->
            StoryCard(
                story = story,
                bestWpm = state.bestWpm[story.id],
                bestAcc = state.bestAccuracy[story.id],
                onPlay = { haptics.tap(); actions.openStory(story) },
                onDelete = if (story.isAiGenerated) ({ haptics.tap(); actions.deleteCustomStory(story.id) }) else null,
            )
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String, unit: String, valueColor: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = TaskifyTextStyles.indexLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, color = valueColor, fontWeight = FontWeight.Bold)
        Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StoryCard(
    story: ChatStory,
    bestWpm: Int?,
    bestAcc: Int?,
    onPlay: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val accent = TaskifyTheme.modules.accent(TaskifyModule.Tools)
    IndexCard(
        module = TaskifyModule.Tools,
        onClick = onPlay,
        onClickLabel = "Mainkan ${story.title}",
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        contentPadding = 14.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${story.category.uppercase()} · ${story.difficulty.label.uppercase()} · ${story.turns.size} BALASAN",
                style = TaskifyTextStyles.indexLabel,
                color = accent,
                modifier = Modifier.weight(1f),
            )
            if (bestWpm != null) {
                Surface(
                    color = accent.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        "🏆 $bestWpm WPM · ${bestAcc ?: 100}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(story.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Lawan bicara: ${story.partnerName} (${story.partnerRole}) · Tema ${story.suggestedTheme.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Hapus cerita", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            FilledTonalButton(onClick = onPlay) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Main")
            }
        }
        story.turns.firstOrNull()?.let { first ->
            Spacer(Modifier.height(6.dp))
            Text(
                "\"${first.partnerMessage}\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ===================================================================== Arena ChatType (Simulasi Chatting & Ketik)

@Composable
private fun ChatTypeArena(
    state: ChatTypeUiState,
    actions: ChatTypeActions,
    contentPadding: PaddingValues,
) {
    val haptics = rememberHaptics()
    val theme = state.theme
    val isTerminal = theme == ChatVisualTheme.Terminal

    val arenaBg = when (theme) {
        ChatVisualTheme.Paper -> MaterialTheme.colorScheme.background
        ChatVisualTheme.Messenger -> MaterialTheme.colorScheme.surfaceContainerLowest
        ChatVisualTheme.Terminal -> Color(0xFF0D1117)
    }
    val arenaText = if (isTerminal) Color(0xFFE6EDF3) else MaterialTheme.colorScheme.onSurface
    val arenaMuted = if (isTerminal) Color(0xFF8B949E) else MaterialTheme.colorScheme.onSurfaceVariant
    val arenaAccent = when (theme) {
        ChatVisualTheme.Paper -> TaskifyTheme.modules.accent(TaskifyModule.Tools)
        ChatVisualTheme.Messenger -> Color(0xFF006D5B)
        ChatVisualTheme.Terminal -> Color(0xFF39D353)
    }

    // Animasi guncang horizontal (Shake) + Getar HP saat pengguna salah ketik (typo).
    val shakeOffset = remember { Animatable(0f) }
    var typoFlash by remember { mutableStateOf(false) }
    LaunchedEffect(state.typoTick) {
        if (state.typoTick > 0) {
            haptics.error()
            typoFlash = true
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 240
                    (-11f) at 35
                    11f at 75
                    (-8f) at 120
                    8f at 165
                    (-4f) at 205
                    0f at 240
                },
            )
            typoFlash = false
        }
    }

    // Getar & animasi saat menembus milestone combo (25x, 50x, ...).
    LaunchedEffect(state.milestoneTick) {
        if (state.milestoneTick > 0) haptics.tick()
    }
    // Getar sukses saat satu balasan selesai terkirim.
    LaunchedEffect(state.sendTick) {
        if (state.sendTick > 0) haptics.success()
    }
    // Getar halus saat bubble lawan bicara masuk.
    LaunchedEffect(state.partnerIncomingTick) {
        if (state.partnerIncomingTick > 1) haptics.tap()
    }

    val listState = rememberLazyListState()
    LaunchedEffect(state.bubbles.size, state.partnerTyping, state.summary != null) {
        val totalItems = state.bubbles.size + (if (state.partnerTyping) 1 else 0) + (if (state.summary != null) 1 else 0)
        if (totalItems > 0) {
            runCatching { listState.animateScrollToItem(totalItems - 1) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(arenaBg)
            .padding(top = contentPadding.calculateTopPadding())
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // 1. Top Bar Kompak + Live HUD (WPM · Akurasi · Combo)
        ArenaTopBar(
            state = state,
            accent = arenaAccent,
            textColor = arenaText,
            mutedColor = arenaMuted,
            isTerminal = isTerminal,
            onBack = { haptics.tap(); actions.backToLobby() },
            onRestart = { haptics.tap(); actions.restartStory() },
            onThemeChange = { haptics.tick(); actions.setTheme(it) },
        )

        HorizontalDivider(color = if (isTerminal) Color(0xFF21262D) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // 2. Daftar Bubble Chat
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.bubbles, key = { it.id }) { bubble ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(180)) + slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it / 3 } + scaleIn(tween(200), initialScale = 0.94f),
                ) {
                    ChatBubbleRow(
                        bubble = bubble,
                        theme = theme,
                        accent = arenaAccent,
                        textColor = arenaText,
                        mutedColor = arenaMuted,
                    )
                }
            }

            if (state.partnerTyping) {
                item(key = "partner-typing") {
                    TypingIndicatorBubble(
                        partnerName = state.activeStory.partnerName,
                        theme = theme,
                        accent = arenaAccent,
                        mutedColor = arenaMuted,
                    )
                }
            }

            if (state.summary != null) {
                item(key = "session-summary") {
                    SessionSummaryCard(
                        summary = state.summary,
                        story = state.activeStory,
                        accent = arenaAccent,
                        isTerminal = isTerminal,
                        onReplay = { haptics.tap(); actions.restartStory() },
                        onNewAiStory = { haptics.tap(); actions.openGeneratorSheet() },
                        onBackToLobby = { haptics.tap(); actions.backToLobby() },
                    )
                }
            }
        }

        // 3. Ghost Prompt & Kotak Ketik Cepat di bawah
        if (state.summary == null) {
            GhostComposerPanel(
                state = state,
                theme = theme,
                accent = arenaAccent,
                textColor = arenaText,
                mutedColor = arenaMuted,
                shakePx = shakeOffset.value,
                typoFlash = typoFlash,
                onInputChanged = actions::onInputChanged,
            )
        }
    }
}

@Composable
private fun ArenaTopBar(
    state: ChatTypeUiState,
    accent: Color,
    textColor: Color,
    mutedColor: Color,
    isTerminal: Boolean,
    onBack: () -> Unit,
    onRestart: () -> Unit,
    onThemeChange: (ChatVisualTheme) -> Unit,
) {
    val story = state.activeStory
    val comboScale by animateFloatAsState(
        targetValue = if (state.combo >= 10) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "combo-scale",
    )
    val accColor = when {
        state.liveAccuracy >= 96 -> accent
        state.liveAccuracy >= 90 -> namedColor("amber")
        else -> MaterialTheme.colorScheme.error
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Kembali", tint = textColor)
            }
            // Avatar inisial lawan bicara
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    story.partnerName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    story.partnerName,
                    style = MaterialTheme.typography.titleSmall,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val statusText = when {
                    state.partnerTyping -> "sedang mengetik…"
                    state.summary != null -> "percakapan selesai"
                    else -> "${story.title} · balasan ${(state.turnIndex + 1).coerceAtMost(story.turns.size)}/${story.turns.size}"
                }
                Text(
                    statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.partnerTyping) accent else mutedColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onRestart) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Ulangi cerita", tint = mutedColor)
            }
        }

        // Baris HUD Metrik Real-Time + Pilihan Tema
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            HudPill(label = "WPM", value = "${state.liveWpm}", valueColor = accent, isTerminal = isTerminal)
            HudPill(label = "AKURASI", value = "${state.liveAccuracy}%", valueColor = accColor, isTerminal = isTerminal)
            HudPill(
                label = "COMBO",
                value = "${state.combo}×",
                valueColor = if (state.combo >= 25) namedColor("orange") else textColor,
                isTerminal = isTerminal,
                modifier = Modifier.scale(comboScale),
            )
            Spacer(Modifier.weight(1f))
            // Siklus tema cepat 1 ketukan (satu baris utuh, tidak terpotong di tengah kata)
            Surface(
                onClick = {
                    val next = ChatVisualTheme.entries[(state.theme.ordinal + 1) % ChatVisualTheme.entries.size]
                    onThemeChange(next)
                },
                color = if (isTerminal) Color(0xFF161B22) else MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    state.theme.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun HudPill(
    label: String,
    value: String,
    valueColor: Color,
    isTerminal: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (isTerminal) Color(0xFF161B22) else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (isTerminal) Color(0xFF30363D) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, style = TaskifyTextStyles.indexLabel, color = if (isTerminal) Color(0xFF8B949E) else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
            Text(
                value,
                style = MaterialTheme.typography.labelLarge,
                color = valueColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                fontFamily = if (isTerminal) FontFamily.Monospace else FontFamily.Default,
            )
        }
    }
}

@Composable
private fun ChatBubbleRow(
    bubble: ChatBubbleItem,
    theme: ChatVisualTheme,
    accent: Color,
    textColor: Color,
    mutedColor: Color,
) {
    val isPartner = bubble.fromPartner
    val isTerminal = theme == ChatVisualTheme.Terminal
    val shape = when (theme) {
        ChatVisualTheme.Paper -> RoundedCornerShape(14.dp)
        ChatVisualTheme.Messenger -> if (isPartner) {
            RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp)
        } else {
            RoundedCornerShape(topStart = 20.dp, topEnd = 4.dp, bottomEnd = 20.dp, bottomStart = 20.dp)
        }
        ChatVisualTheme.Terminal -> RoundedCornerShape(6.dp)
    }
    val bubbleBg = when (theme) {
        ChatVisualTheme.Paper -> if (isPartner) MaterialTheme.colorScheme.surfaceContainerLow else accent.copy(alpha = 0.14f)
        ChatVisualTheme.Messenger -> if (isPartner) MaterialTheme.colorScheme.surfaceContainerHigh else accent
        ChatVisualTheme.Terminal -> if (isPartner) Color(0xFF161B22) else Color(0xFF0F291E)
    }
    val bubbleText = when {
        theme == ChatVisualTheme.Messenger && !isPartner -> Color.White
        isTerminal && !isPartner -> Color(0xFF7EE787)
        else -> textColor
    }
    val border = when (theme) {
        ChatVisualTheme.Paper -> BorderStroke(1.dp, if (isPartner) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f) else accent.copy(alpha = 0.35f))
        ChatVisualTheme.Messenger -> null
        ChatVisualTheme.Terminal -> BorderStroke(1.dp, if (isPartner) Color(0xFF30363D) else Color(0xFF238636))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isPartner) Alignment.Start else Alignment.End,
    ) {
        Surface(
            color = bubbleBg,
            shape = shape,
            border = border,
            modifier = Modifier.widthIn(max = 330.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (isTerminal) {
                    Text(
                        if (isPartner) "[${bubble.senderName.lowercase().replace(' ', '_')}@node]$" else "[kamu@taskify]>",
                        style = TaskifyTextStyles.code,
                        color = if (isPartner) Color(0xFF58A6FF) else accent,
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    text = bubble.text,
                    style = if (isTerminal) TaskifyTextStyles.code else MaterialTheme.typography.bodyLarge,
                    color = bubbleText,
                )
                bubble.metrics?.let { m ->
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val badgeColor = if (theme == ChatVisualTheme.Messenger && !isPartner) Color.White.copy(alpha = 0.85f) else mutedColor
                        if (theme == ChatVisualTheme.Messenger) {
                            Icon(Icons.Rounded.DoneAll, contentDescription = null, tint = badgeColor, modifier = Modifier.size(14.dp))
                        }
                        Text(
                            "${m.wpm} WPM · ${m.accuracyPct}% · ${"%.1f".format(m.elapsedMs / 1000.0)}s",
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor,
                            fontFamily = if (isTerminal) FontFamily.Monospace else FontFamily.Default,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TypingIndicatorBubble(
    partnerName: String,
    theme: ChatVisualTheme,
    accent: Color,
    mutedColor: Color,
) {
    val transition = rememberInfiniteTransition(label = "typing-dots")
    val d1 by transition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "d1")
    val d2 by transition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(450, delayMillis = 150), RepeatMode.Reverse), label = "d2")
    val d3 by transition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(450, delayMillis = 300), RepeatMode.Reverse), label = "d3")
    val isTerminal = theme == ChatVisualTheme.Terminal

    Surface(
        color = if (isTerminal) Color(0xFF161B22) else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (isTerminal) Color(0xFF30363D) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "$partnerName mengetik",
                style = MaterialTheme.typography.labelMedium,
                color = mutedColor,
                fontFamily = if (isTerminal) FontFamily.Monospace else FontFamily.Default,
            )
            listOf(d1, d2, d3).forEach { alpha ->
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = alpha)),
                )
            }
        }
    }
}

@Composable
private fun GhostComposerPanel(
    state: ChatTypeUiState,
    theme: ChatVisualTheme,
    accent: Color,
    textColor: Color,
    mutedColor: Color,
    shakePx: Float,
    typoFlash: Boolean,
    onInputChanged: (String) -> Unit,
) {
    val isTerminal = theme == ChatVisualTheme.Terminal
    val hasTypoNow = state.charDiffs.any { it.state == CharState.Wrong }
    val borderColor by animateColorAsState(
        targetValue = when {
            typoFlash || hasTypoNow -> MaterialTheme.colorScheme.error
            else -> accent.copy(alpha = 0.55f)
        },
        animationSpec = tween(120),
        label = "composer-border",
    )
    val panelBg = if (isTerminal) Color(0xFF161B22) else MaterialTheme.colorScheme.surfaceContainerLow

    Surface(
        color = panelBg,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, if (isTerminal) Color(0xFF30363D) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .offset { IntOffset(shakePx.roundToInt(), 0) },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Keyboard,
                    contentDescription = null,
                    tint = if (hasTypoNow) MaterialTheme.colorScheme.error else accent,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    when {
                        state.partnerTyping -> "TUNGGU PESAN MASUK…"
                        hasTypoNow -> "SALAH KETIK! PERBAIKI HURUF MERAH"
                        state.typedInput.isEmpty() -> "KETIK BALASAN INI (TIMER MULAI SAAT KETUKAN PERTAMA)"
                        else -> "TERUSKAN SAMPAI SELESAI (${state.typedInput.length}/${state.currentTarget.length})"
                    },
                    style = TaskifyTextStyles.indexLabel,
                    color = if (hasTypoNow) MaterialTheme.colorScheme.error else accent,
                )
            }

            // Kartu Ghost Prompt dengan pewarnaan per karakter
            Surface(
                color = if (isTerminal) Color(0xFF0D1117) else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.5.dp, borderColor),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val annotated = remember(state.charDiffs, state.currentTarget, isTerminal, accent, textColor, mutedColor) {
                    buildGhostAnnotatedString(
                        diffs = state.charDiffs,
                        fallbackTarget = state.currentTarget,
                        correctColor = if (isTerminal) Color(0xFF7EE787) else textColor,
                        pendingColor = mutedColor.copy(alpha = 0.52f),
                        cursorColor = accent,
                        errorColor = Color(0xFFD93025),
                        errorBg = Color(0xFFFCE8E6),
                    )
                }
                Text(
                    text = annotated,
                    style = if (isTerminal) TaskifyTextStyles.code else MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }

            OutlinedTextField(
                value = state.typedInput,
                onValueChange = onInputChanged,
                enabled = !state.partnerTyping && state.currentTarget.isNotEmpty(),
                placeholder = {
                    Text(
                        if (state.partnerTyping) "Lawan bicara sedang mengetik…"
                        else "Ketik balasanmu di sini…",
                        color = mutedColor,
                    )
                },
                singleLine = false,
                maxLines = 3,
                isError = hasTypoNow,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    autoCorrectEnabled = false,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun buildGhostAnnotatedString(
    diffs: List<CharDiff>,
    fallbackTarget: String,
    correctColor: Color,
    pendingColor: Color,
    cursorColor: Color,
    errorColor: Color,
    errorBg: Color,
) = buildAnnotatedString {
    if (diffs.isEmpty()) {
        withStyle(SpanStyle(color = pendingColor)) { append(fallbackTarget) }
        return@buildAnnotatedString
    }
    for (d in diffs) {
        when (d.state) {
            CharState.Correct -> withStyle(SpanStyle(color = correctColor, fontWeight = FontWeight.SemiBold)) {
                append(d.char)
            }
            CharState.Wrong -> withStyle(
                SpanStyle(
                    color = errorColor,
                    background = errorBg,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                ),
            ) {
                append(d.char)
            }
            CharState.Cursor -> withStyle(
                SpanStyle(
                    color = cursorColor,
                    background = cursorColor.copy(alpha = 0.16f),
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                ),
            ) {
                append(d.char)
            }
            CharState.Pending -> withStyle(SpanStyle(color = pendingColor)) {
                append(d.char)
            }
        }
    }
}

// ===================================================================== Kartu Evaluasi Akhir Episode

@Composable
private fun SessionSummaryCard(
    summary: SessionSummary,
    story: ChatStory,
    accent: Color,
    isTerminal: Boolean,
    onReplay: () -> Unit,
    onNewAiStory: () -> Unit,
    onBackToLobby: () -> Unit,
) {
    Surface(
        color = if (isTerminal) Color(0xFF161B22) else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, accent),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (summary.isNewRecord) "PERCAKAPAN TUNTAS · REKOR BARU!" else "PERCAKAPAN TUNTAS",
                    style = TaskifyTextStyles.indexLabel,
                    color = accent,
                )
            }
            Text(
                story.title,
                style = MaterialTheme.typography.titleLarge,
                color = if (isTerminal) Color(0xFFE6EDF3) else MaterialTheme.colorScheme.onSurface,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryMetricTile("RATA-RATA", "${summary.averageWpm} WPM", "${summary.averageCpm} CPM", accent, isTerminal, Modifier.weight(1f))
                SummaryMetricTile("PUNCAK", "${summary.peakWpm} WPM", "combo ${summary.maxCombo}×", accent, isTerminal, Modifier.weight(1f))
                SummaryMetricTile("AKURASI", "${summary.accuracyPct}%", "${summary.totalErrors} typo", accent, isTerminal, Modifier.weight(1f))
            }
            if (summary.topMissedLabels.isNotEmpty()) {
                Text(
                    "Karakter yang sering meleset: ${summary.topMissedLabels.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isTerminal) Color(0xFF8B949E) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "Sempurna! Tidak ada satu pun salah ketik sepanjang percakapan ini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = accent,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val btnPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                OutlinedButton(
                    onClick = onBackToLobby,
                    border = BorderStroke(1.dp, if (isTerminal) Color(0xFF8B949E) else MaterialTheme.colorScheme.outline),
                    contentPadding = btnPadding,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        "Cerita",
                        maxLines = 1,
                        softWrap = false,
                        color = if (isTerminal) Color(0xFFE6EDF3) else MaterialTheme.colorScheme.primary,
                    )
                }
                FilledTonalButton(
                    onClick = onNewAiStory,
                    contentPadding = btnPadding,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Buat AI", maxLines = 1, softWrap = false)
                }
                Button(
                    onClick = onReplay,
                    contentPadding = btnPadding,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Ulangi", maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricTile(
    label: String,
    primary: String,
    secondary: String,
    accent: Color,
    isTerminal: Boolean,
    modifier: Modifier,
) {
    Surface(
        color = if (isTerminal) Color(0xFF0D1117) else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = TaskifyTextStyles.indexLabel, color = if (isTerminal) Color(0xFF8B949E) else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(primary, style = MaterialTheme.typography.titleMedium, color = accent, fontWeight = FontWeight.Bold)
            Text(secondary, style = MaterialTheme.typography.labelSmall, color = if (isTerminal) Color(0xFF8B949E) else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ===================================================================== Sheet Generator Cerita AI

private val TURN_COUNT_CHOICES = listOf(4, 6, 8)
private val TOPIC_PRESETS = listOf(
    "Wawancara kerja Android Engineer" to "Tech Lead Perekrut",
    "Debat revisi desain aplikasi malam Minggu" to "Product Manager",
    "Pesan makanan & tanya jalan di Tokyo (English)" to "Local Guide",
    "Misi penyelamatan data server dari serangan siber" to "Agen Keamanan",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiStoryGeneratorSheet(
    state: ChatTypeUiState,
    actions: ChatTypeActions,
    onOpenAiSettings: () -> Unit,
) {
    val haptics = rememberHaptics()
    var topic by rememberSaveable { mutableStateOf("") }
    var partnerRole by rememberSaveable { mutableStateOf("") }
    var difficulty by rememberSaveable { mutableStateOf(TypingDifficulty.Standard) }
    var turnCount by rememberSaveable { mutableIntStateOf(5) }

    ModalBottomSheet(
        onDismissRequest = actions::closeGeneratorSheet,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("GENERATOR ALUR CERITA", style = TaskifyTextStyles.indexLabel, color = TaskifyTheme.modules.accent(TaskifyModule.Tools))
            Text("Buat Skenario Percakapan Baru", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(
                if (state.aiReady) "Terhubung ke ${state.aiProviderLabel}. AI akan membuatkan dialog bolak-balik sesuai topikmu."
                else "Kunci API AI belum diatur — kamu tetap bisa meracik skenario kustom instan secara offline atau mengatur AI dulu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("IDE CEPAT", style = TaskifyTextStyles.indexLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TOPIC_PRESETS.forEach { (presetTopic, presetRole) ->
                    FilterChip(
                        selected = topic == presetTopic,
                        onClick = {
                            haptics.tick()
                            topic = presetTopic
                            partnerRole = presetRole
                        },
                        label = { Text(presetTopic, maxLines = 1) },
                    )
                }
            }

            OutlinedTextField(
                value = topic,
                onValueChange = { topic = it },
                label = { Text("Topik / alur cerita percakapan") },
                placeholder = { Text("mis. Tawar-menawar harga kamera bekas di kafe") },
                singleLine = false,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = partnerRole,
                onValueChange = { partnerRole = it },
                label = { Text("Peran lawan bicara (opsional)") },
                placeholder = { Text("mis. Penjual ramah, HRD, Detektif") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("TINGKAT KESULITAN KETIK", style = TaskifyTextStyles.indexLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypingDifficulty.entries.forEach { d ->
                    FilterChip(
                        selected = difficulty == d,
                        onClick = { haptics.tick(); difficulty = d },
                        label = { Text(d.label) },
                    )
                }
            }
            Text(difficulty.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text("PANJANG PERCAKAPAN", style = TaskifyTextStyles.indexLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TURN_COUNT_CHOICES.forEach { count ->
                    FilterChip(
                        selected = turnCount == count,
                        onClick = { haptics.tick(); turnCount = count },
                        label = { Text("$count balasan") },
                    )
                }
            }

            state.aiError?.let { err ->
                Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!state.aiReady) {
                    TextButton(onClick = { actions.closeGeneratorSheet(); onOpenAiSettings() }) {
                        Text("Pengaturan AI")
                    }
                }
                OutlinedButton(
                    onClick = {
                        haptics.success()
                        actions.generateStoryOffline(topic.ifBlank { "Diskusi Proyek Cepat" }, partnerRole, difficulty, turnCount)
                    },
                    enabled = !state.aiGenerating,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Racik Instan")
                }
                if (state.aiReady) {
                    Button(
                        onClick = {
                            haptics.tap()
                            actions.generateStoryWithAi(topic, partnerRole, difficulty, turnCount)
                        },
                        enabled = !state.aiGenerating && topic.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.aiGenerating) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(6.dp))
                            Text("Merangkai…")
                        } else {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Generate AI")
                        }
                    }
                }
            }
        }
    }
}
