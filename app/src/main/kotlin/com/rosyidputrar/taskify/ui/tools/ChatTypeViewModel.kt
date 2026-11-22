package com.rosyidputrar.taskify.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rosyidputrar.taskify.core.ai.AiClients
import com.rosyidputrar.taskify.core.ai.AiEvent
import com.rosyidputrar.taskify.core.ai.AiMessage
import com.rosyidputrar.taskify.core.ai.AiRequest
import com.rosyidputrar.taskify.core.ai.AiRole
import com.rosyidputrar.taskify.core.ai.AiSettingsStore
import com.rosyidputrar.taskify.core.designsystem.component.launchSafe
import com.rosyidputrar.taskify.core.domain.tools.CharDiff
import com.rosyidputrar.taskify.core.domain.tools.CharState
import com.rosyidputrar.taskify.core.domain.tools.ChatStory
import com.rosyidputrar.taskify.core.domain.tools.ChatTypeEngine
import com.rosyidputrar.taskify.core.domain.tools.ChatVisualTheme
import com.rosyidputrar.taskify.core.domain.tools.SessionSummary
import com.rosyidputrar.taskify.core.domain.tools.TurnMetrics
import com.rosyidputrar.taskify.core.domain.tools.TypingDifficulty
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ChatTypeMode { Lobby, Arena }

/** Satu bubble pesan yang sudah tampil di layar percakapan. */
data class ChatBubbleItem(
    val id: String,
    val fromPartner: Boolean,
    val senderName: String,
    val text: String,
    val metrics: TurnMetrics? = null,
)

data class ChatTypeUiState(
    val mode: ChatTypeMode = ChatTypeMode.Lobby,
    val stories: List<ChatStory> = ChatTypeEngine.BUILT_IN_STORIES,
    val bestWpm: Map<String, Int> = emptyMap(),
    val bestAccuracy: Map<String, Int> = emptyMap(),
    val completedSessions: Int = 0,
    val activeStory: ChatStory = ChatTypeEngine.BUILT_IN_STORIES.first(),
    val theme: ChatVisualTheme = ChatVisualTheme.Paper,
    val bubbles: List<ChatBubbleItem> = emptyList(),
    val turnIndex: Int = 0,
    val partnerTyping: Boolean = false,
    val currentTarget: String = "",
    val typedInput: String = "",
    val charDiffs: List<CharDiff> = emptyList(),
    val liveWpm: Int = 0,
    val liveAccuracy: Int = 100,
    val combo: Int = 0,
    val maxCombo: Int = 0,
    /** Monoton naik setiap ada huruf salah ketik → memicu getar HP (`haptics.error()`) + animasi shake. */
    val typoTick: Int = 0,
    /** Monoton naik setiap menembus kelipatan 25 combo → memicu `haptics.tick()` + bounce. */
    val milestoneTick: Int = 0,
    /** Monoton naik setiap kalimat balasan tuntas → memicu `haptics.success()`. */
    val sendTick: Int = 0,
    /** Monoton naik setiap bubble lawan bicara muncul → memicu `haptics.tap()`. */
    val partnerIncomingTick: Int = 0,
    val summary: SessionSummary? = null,
    val showGeneratorSheet: Boolean = false,
    val aiReady: Boolean = false,
    val aiProviderLabel: String = "",
    val aiGenerating: Boolean = false,
    val aiError: String? = null,
) {
    val highestWpmEver: Int get() = bestWpm.values.maxOrNull() ?: 0
    val averageBestAccuracy: Int
        get() = if (bestAccuracy.isEmpty()) 100 else bestAccuracy.values.average().toInt()
}

interface ChatTypeActions {
    fun openStory(story: ChatStory) {}
    fun backToLobby() {}
    fun restartStory() {}
    fun setTheme(theme: ChatVisualTheme) {}
    fun onInputChanged(newText: String) {}
    fun openGeneratorSheet() {}
    fun closeGeneratorSheet() {}
    fun generateStoryWithAi(topic: String, partnerRole: String, difficulty: TypingDifficulty, turnCount: Int) {}
    fun generateStoryOffline(topic: String, partnerRole: String, difficulty: TypingDifficulty, turnCount: Int) {}
    fun deleteCustomStory(storyId: String) {}

    companion object {
        val None = object : ChatTypeActions {}
    }
}

class ChatTypeViewModel(
    initialStoryId: String? = null,
    private val store: ChatTypeStore,
    private val aiSettingsStore: AiSettingsStore? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel(), ChatTypeActions {

    private val _state = MutableStateFlow(ChatTypeUiState())
    val state: StateFlow<ChatTypeUiState> = _state.asStateFlow()

    private var turnStartedAtMs: Long? = null
    private var turnKeystrokes: Int = 0
    private var turnErrors: Int = 0
    private var sessionMissedChars: Map<Char, Int> = emptyMap()
    private val completedTurnMetrics = mutableListOf<TurnMetrics>()
    private var partnerTypingJob: Job? = null

    init {
        viewModelScope.launch {
            store.data.collect { saved ->
                val allStories = saved.customStories + ChatTypeEngine.BUILT_IN_STORIES
                _state.update { cur ->
                    val active = allStories.firstOrNull { it.id == cur.activeStory.id } ?: allStories.first()
                    cur.copy(
                        stories = allStories,
                        bestWpm = saved.bestWpm,
                        bestAccuracy = saved.bestAccuracy,
                        completedSessions = saved.completedCount,
                        activeStory = active,
                        theme = if (cur.mode == ChatTypeMode.Lobby) saved.theme else cur.theme,
                    )
                }
            }
        }
        if (aiSettingsStore != null) {
            viewModelScope.launch {
                aiSettingsStore.settings.collect { s ->
                    _state.update { it.copy(aiReady = s.ready, aiProviderLabel = "${s.provider.label} · ${s.model}".trimEnd(' ', '·')) }
                }
            }
        }
        if (initialStoryId != null) {
            val all = store.data.value.customStories + ChatTypeEngine.BUILT_IN_STORIES
            all.firstOrNull { it.id == initialStoryId }?.let { openStory(it) }
        }
    }

    override fun openStory(story: ChatStory) {
        partnerTypingJob?.cancel()
        turnStartedAtMs = null
        turnKeystrokes = 0
        turnErrors = 0
        sessionMissedChars = emptyMap()
        completedTurnMetrics.clear()

        _state.update {
            it.copy(
                mode = ChatTypeMode.Arena,
                activeStory = story,
                theme = story.suggestedTheme,
                bubbles = emptyList(),
                turnIndex = 0,
                partnerTyping = false,
                currentTarget = "",
                typedInput = "",
                charDiffs = emptyList(),
                liveWpm = 0,
                liveAccuracy = 100,
                combo = 0,
                maxCombo = 0,
                summary = null,
            )
        }
        presentTurn(0, animatePartner = true)
    }

    override fun backToLobby() {
        partnerTypingJob?.cancel()
        _state.update { it.copy(mode = ChatTypeMode.Lobby, partnerTyping = false) }
    }

    override fun restartStory() {
        openStory(_state.value.activeStory)
    }

    override fun setTheme(theme: ChatVisualTheme) {
        store.setTheme(theme)
        _state.update { it.copy(theme = theme) }
    }

    private fun presentTurn(index: Int, animatePartner: Boolean) {
        val story = _state.value.activeStory
        val turn = story.turns.getOrNull(index)
        if (turn == null) {
            finishSession()
            return
        }
        turnStartedAtMs = null
        turnKeystrokes = 0
        turnErrors = 0

        partnerTypingJob?.cancel()
        if (animatePartner) {
            _state.update {
                it.copy(
                    turnIndex = index,
                    partnerTyping = true,
                    currentTarget = "",
                    typedInput = "",
                    charDiffs = emptyList(),
                )
            }
            partnerTypingJob = viewModelScope.launch {
                delay(520L)
                deliverPartnerMessage(story, index, turn.partnerMessage, turn.targetReply)
            }
        } else {
            deliverPartnerMessage(story, index, turn.partnerMessage, turn.targetReply)
        }
    }

    private fun deliverPartnerMessage(story: ChatStory, index: Int, partnerMsg: String, targetReply: String) {
        val bubble = ChatBubbleItem(
            id = "p-$index",
            fromPartner = true,
            senderName = story.partnerName,
            text = partnerMsg,
        )
        _state.update { cur ->
            cur.copy(
                turnIndex = index,
                partnerTyping = false,
                bubbles = cur.bubbles + bubble,
                currentTarget = targetReply,
                typedInput = "",
                charDiffs = ChatTypeEngine.diff(targetReply, ""),
                partnerIncomingTick = cur.partnerIncomingTick + 1,
            )
        }
    }

    override fun onInputChanged(newText: String) {
        val cur = _state.value
        val target = cur.currentTarget
        if (cur.partnerTyping || target.isEmpty() || cur.summary != null) return

        val clipped = if (newText.length > target.length + 4) newText.take(target.length + 4) else newText
        val now = clock()
        if (turnStartedAtMs == null && clipped.isNotEmpty()) {
            turnStartedAtMs = now
        }

        val step = ChatTypeEngine.processInputChange(
            target = target,
            previousInput = cur.typedInput,
            newInput = clipped,
            prevKeystrokes = turnKeystrokes,
            prevErrors = turnErrors,
            prevCombo = cur.combo,
            prevMaxCombo = cur.maxCombo,
            prevMissed = sessionMissedChars,
        )
        turnKeystrokes = step.totalKeystrokes
        turnErrors = step.errorKeystrokes
        sessionMissedChars = step.missedChars

        val diffs = ChatTypeEngine.diff(target, clipped)
        val correctChars = diffs.count { it.state == CharState.Correct }
        val elapsed = (turnStartedAtMs?.let { now - it } ?: 0L).coerceAtLeast(0L)
        val (liveWpm, liveAcc) = ChatTypeEngine.computeLiveWpmAndAccuracy(
            correctChars = correctChars,
            elapsedMs = elapsed,
            totalKeystrokes = turnKeystrokes,
            errorKeystrokes = turnErrors,
        )

        if (step.completed) {
            val metrics = ChatTypeEngine.computeTurnMetrics(
                targetLength = target.length,
                elapsedMs = elapsed,
                totalKeystrokes = turnKeystrokes,
                errorKeystrokes = turnErrors,
            )
            completedTurnMetrics += metrics
            val userBubble = ChatBubbleItem(
                id = "u-${cur.turnIndex}",
                fromPartner = false,
                senderName = "Kamu",
                text = target,
                metrics = metrics,
            )
            _state.update {
                it.copy(
                    bubbles = it.bubbles + userBubble,
                    currentTarget = "",
                    typedInput = "",
                    charDiffs = emptyList(),
                    liveWpm = metrics.wpm,
                    liveAccuracy = metrics.accuracyPct,
                    combo = step.combo,
                    maxCombo = step.maxCombo,
                    sendTick = it.sendTick + 1,
                )
            }
            presentTurn(cur.turnIndex + 1, animatePartner = true)
        } else {
            _state.update {
                it.copy(
                    typedInput = clipped,
                    charDiffs = diffs,
                    liveWpm = if (liveWpm > 0) liveWpm else it.liveWpm,
                    liveAccuracy = liveAcc,
                    combo = step.combo,
                    maxCombo = step.maxCombo,
                    typoTick = if (step.typoTriggered) it.typoTick + 1 else it.typoTick,
                    milestoneTick = if (step.comboMilestone) it.milestoneTick + 1 else it.milestoneTick,
                )
            }
        }
    }

    private fun finishSession() {
        val cur = _state.value
        val prevBest = cur.bestWpm[cur.activeStory.id]
        val summary = ChatTypeEngine.summarizeSession(
            turns = completedTurnMetrics.toList(),
            maxCombo = cur.maxCombo,
            missedChars = sessionMissedChars,
            previousBestWpm = prevBest,
        )
        store.recordResult(cur.activeStory.id, summary.averageWpm, summary.accuracyPct)
        _state.update {
            it.copy(
                partnerTyping = false,
                currentTarget = "",
                typedInput = "",
                charDiffs = emptyList(),
                liveWpm = summary.averageWpm,
                liveAccuracy = summary.accuracyPct,
                summary = summary,
            )
        }
    }

    // ------------------------------------------------------------------ AI Story Generator

    override fun openGeneratorSheet() {
        _state.update { it.copy(showGeneratorSheet = true, aiError = null) }
    }

    override fun closeGeneratorSheet() {
        if (!_state.value.aiGenerating) {
            _state.update { it.copy(showGeneratorSheet = false, aiError = null) }
        }
    }

    override fun generateStoryOffline(
        topic: String,
        partnerRole: String,
        difficulty: TypingDifficulty,
        turnCount: Int,
    ) {
        val story = ChatTypeEngine.generateOfflineStory(topic, partnerRole, difficulty, turnCount)
        store.saveCustomStory(story)
        _state.update { it.copy(showGeneratorSheet = false, aiError = null) }
        openStory(story)
    }

    override fun generateStoryWithAi(
        topic: String,
        partnerRole: String,
        difficulty: TypingDifficulty,
        turnCount: Int,
    ) {
        val cleanTopic = topic.trim()
        if (cleanTopic.isEmpty()) {
            _state.update { it.copy(aiError = "Isi topik atau skenario percakapan dulu.") }
            return
        }
        val settingsStore = aiSettingsStore
        val settings = settingsStore?.settings?.value
        val connection = settings?.let { settingsStore.connection(it.provider) }
        if (settings == null || !settings.ready || connection == null) {
            _state.update { it.copy(aiError = "Kunci API atau model AI belum diatur. Kamu bisa memakai tombol \"Racik Skenario Instan\" atau buka Pengaturan AI.") }
            return
        }

        _state.update { it.copy(aiGenerating = true, aiError = null) }
        launchSafe {
            try {
                val client = AiClients.create(settings.provider, connection.baseUrl, connection.apiKey)
                val prompt = ChatTypeEngine.buildAiStoryPrompt(cleanTopic, partnerRole, difficulty, turnCount)
                val req = AiRequest(
                    model = settings.model,
                    system = "Kamu pembuat skenario latihan mengetik interaktif berbentuk percakapan chat. Selalu balas dengan JSON murni yang valid.",
                    messages = listOf(AiMessage(AiRole.User, prompt)),
                    maxTokens = 2048,
                )
                val raw = StringBuilder()
                client.stream(req).collect { ev ->
                    if (ev is AiEvent.Text) raw.append(ev.delta)
                }
                val parsed = ChatTypeEngine.parseAiStoryJson(raw.toString(), difficulty, cleanTopic)
                if (parsed == null) {
                    _state.update {
                        it.copy(
                            aiGenerating = false,
                            aiError = "Format cerita dari AI belum lengkap. Coba lagi atau gunakan tombol \"Racik Skenario Instan\".",
                        )
                    }
                } else {
                    store.saveCustomStory(parsed)
                    _state.update { it.copy(aiGenerating = false, showGeneratorSheet = false, aiError = null) }
                    openStory(parsed)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        aiGenerating = false,
                        aiError = e.message ?: "Gagal menghubungi server AI. Kamu tetap bisa memakai \"Racik Skenario Instan\".",
                    )
                }
            }
        }
    }

    override fun deleteCustomStory(storyId: String) {
        store.deleteCustomStory(storyId)
    }
}
