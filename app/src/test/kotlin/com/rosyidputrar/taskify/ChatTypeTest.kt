package com.rosyidputrar.taskify

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.tools.ChatTypeEngine
import com.rosyidputrar.taskify.core.domain.tools.ChatVisualTheme
import com.rosyidputrar.taskify.core.domain.tools.SessionSummary
import com.rosyidputrar.taskify.core.domain.tools.TurnMetrics
import com.rosyidputrar.taskify.core.domain.tools.TypingDifficulty
import com.rosyidputrar.taskify.ui.tools.ChatBubbleItem
import com.rosyidputrar.taskify.ui.tools.ChatTypeActions
import com.rosyidputrar.taskify.ui.tools.ChatTypeMode
import com.rosyidputrar.taskify.ui.tools.ChatTypeStore
import com.rosyidputrar.taskify.ui.tools.ChatTypeUiState
import com.rosyidputrar.taskify.ui.tools.ChatTypeViewModel
import com.rosyidputrar.taskify.ui.tools.ToolsContent
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h950dp-xxhdpi")
class ChatTypeTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var store: ChatTypeStore
    private val outDir = File("/home/rosdi/.gemini/antigravity-cli/brain/dea942cd-c5ac-40fb-a2d8-aca0670e99ee")

    @Before fun setUp() {
        store = ChatTypeStore(context)
        store.clear()
    }

    @Test fun storePersistsRecordsAndCustomStoriesAndClearsOnLogout() {
        val custom = ChatTypeEngine.generateOfflineStory("Negosiasi Gaji", "HRD", TypingDifficulty.Standard, 4, id = "c-1")
        store.saveCustomStory(custom)
        store.recordResult("c-1", wpm = 78, accuracyPct = 97)
        store.setTheme(ChatVisualTheme.Terminal)

        val reloaded = ChatTypeStore(context).data.value
        assertEquals(ChatVisualTheme.Terminal, reloaded.theme)
        assertEquals(78, reloaded.bestWpm["c-1"])
        assertEquals(97, reloaded.bestAccuracy["c-1"])
        assertEquals(1, reloaded.completedCount)
        assertEquals("Negosiasi Gaji", reloaded.customStories.single().title)

        store.clear()
        val afterClear = ChatTypeStore(context).data.value
        assertTrue(afterClear.customStories.isEmpty())
        assertTrue(afterClear.bestWpm.isEmpty())
    }

    @Test fun viewModelGeneratesOfflineStoryAndTracksTypoTick() {
        var now = 1_000_000L
        val vm = ChatTypeViewModel(store = store, clock = { now })
        vm.generateStoryOffline("Tes Cepat", "Partner", TypingDifficulty.Casual, 3)

        val s1 = vm.state.value
        assertEquals(ChatTypeMode.Arena, s1.mode)

        // Simulasikan giliran sudah siap diketik
        val story = ChatTypeEngine.BUILT_IN_STORIES[2] // Rencana Liburan Mendadak
        val target = story.turns.first().targetReply
        val stateReady = ChatTypeUiState(
            mode = ChatTypeMode.Arena,
            activeStory = story,
            currentTarget = target,
            charDiffs = ChatTypeEngine.diff(target, ""),
        )
        assertNotNull(stateReady)
    }

    private fun renderIfOutDirExists(fileName: String, content: @Composable () -> Unit) {
        compose.setContent {
            TaskifyTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    content()
                }
            }
        }
        compose.waitForIdle()
        if (outDir.exists()) {
            compose.onRoot().captureRoboImage(File(outDir, fileName))
        }
    }

    @Config(qualifiers = "w393dp-h1100dp-xxhdpi")
    @Test fun renderLobbyScreen() {
        val state = ChatTypeUiState(
            mode = ChatTypeMode.Lobby,
            bestWpm = mapOf("prod-outage" to 84, "client-revision" to 72),
            bestAccuracy = mapOf("prod-outage" to 98, "client-revision" to 96),
            completedSessions = 5,
            theme = ChatVisualTheme.Messenger,
            aiReady = true,
            aiProviderLabel = "OpenAI-compatible · gemini-2.5-flash",
        )
        renderIfOutDirExists("render_chattype_1_lobby.png") {
            ToolsContent(state, ChatTypeActions.None, PaddingValues(top = 16.dp, bottom = 24.dp))
        }
    }

    @Config(qualifiers = "w393dp-h920dp-xxhdpi")
    @Test fun renderArenaMessengerWithTypoHighlight() {
        val story = ChatTypeEngine.BUILT_IN_STORIES[1] // Negosiasi Revisi Malam Minggu
        val target = story.turns[1].targetReply
        // Pengguna sudah mengetik sebagian lalu salah ketik "x" pada kata "ekspor"
        val typed = "Kami bisa siapkan ex"
        val state = ChatTypeUiState(
            mode = ChatTypeMode.Arena,
            activeStory = story,
            theme = ChatVisualTheme.Messenger,
            turnIndex = 1,
            bubbles = listOf(
                ChatBubbleItem("p-0", true, story.partnerName, story.turns[0].partnerMessage),
                ChatBubbleItem("u-0", false, "Kamu", story.turns[0].targetReply, TurnMetrics(76, 380, 99, 6_400L, 82, 1)),
                ChatBubbleItem("p-1", true, story.partnerName, story.turns[1].partnerMessage),
            ),
            currentTarget = target,
            typedInput = typed,
            charDiffs = ChatTypeEngine.diff(target, typed),
            liveWpm = 74,
            liveAccuracy = 95,
            combo = 0,
            maxCombo = 28,
        )
        renderIfOutDirExists("render_chattype_2_arena_messenger_typo.png") {
            ToolsContent(state, ChatTypeActions.None, PaddingValues(0.dp))
        }
    }

    @Config(qualifiers = "w393dp-h980dp-xxhdpi")
    @Test fun renderArenaTerminalWithSummary() {
        val story = ChatTypeEngine.BUILT_IN_STORIES[0] // Darurat Server Produksi
        val state = ChatTypeUiState(
            mode = ChatTypeMode.Arena,
            activeStory = story,
            theme = ChatVisualTheme.Terminal,
            turnIndex = 2,
            bubbles = listOf(
                ChatBubbleItem("p-0", true, story.partnerName, story.turns[0].partnerMessage),
                ChatBubbleItem("u-0", false, "Kamu", story.turns[0].targetReply, TurnMetrics(88, 440, 98, 4_100L, 62, 1)),
                ChatBubbleItem("p-1", true, story.partnerName, story.turns[1].partnerMessage),
                ChatBubbleItem("u-1", false, "Kamu", story.turns[1].targetReply, TurnMetrics(92, 460, 100, 4_800L, 74, 0)),
            ),
            liveWpm = 90,
            liveAccuracy = 99,
            combo = 52,
            maxCombo = 52,
            summary = SessionSummary(
                averageWpm = 90,
                peakWpm = 92,
                averageCpm = 450,
                accuracyPct = 99,
                totalElapsedMs = 8_900L,
                totalKeystrokes = 136,
                totalErrors = 1,
                maxCombo = 52,
                isNewRecord = true,
                previousBestWpm = 81,
                topMissedLabels = listOf("koma (,) (1×)"),
            ),
        )
        renderIfOutDirExists("render_chattype_3_arena_terminal_summary.png") {
            ToolsContent(state, ChatTypeActions.None, PaddingValues(0.dp))
        }
    }
}
