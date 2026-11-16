package com.rosyidputrar.taskify

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.designsystem.component.DockDestination
import com.rosyidputrar.taskify.core.designsystem.component.TaskifyDock
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.designsystem.theme.ThemeMode
import com.rosyidputrar.taskify.ui.spotlight.Spotlight
import com.rosyidputrar.taskify.ui.spotlight.SpotlightSheetState
import com.rosyidputrar.taskify.ui.spotlight.rememberSpotlightSheetState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Menarik dok ke atas: panel Spotlight ikut tertarik mengikuti jari, lalu terbuka/kembali saat dilepas. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class SpotlightPullTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var state: SpotlightSheetState

    private fun setUp() = compose.setContent {
        TaskifyTheme {
            state = rememberSpotlightSheetState()
            Box(Modifier.fillMaxSize()) {
                TaskifyDock(
                    leading = listOf(DockDestination("home", "Hari Ini", Icons.Outlined.Home, Color.Blue)),
                    trailing = emptyList(),
                    selectedKey = "home",
                    onSelect = {},
                    onCapture = {},
                    onCaptureLongPress = {},
                    onPull = { state.dragBy(it) },
                    onPullEnd = { state.release(it) },
                    onOpenSearch = { state.open() },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                Spotlight(state, onOpenModule = {}, onCapture = {}, themeMode = ThemeMode.System, onThemeModeChange = {})
            }
        }
    }

    private val dock get() = compose.onNodeWithContentDescription("Tarik ke atas untuk mencari")

    @Test fun panelFollowsTheFingerBeforeRelease() {
        setUp()
        compose.mainClock.autoAdvance = false
        dock.performTouchInput { down(center); moveBy(androidx.compose.ui.geometry.Offset(0f, -60f)); moveBy(androidx.compose.ui.geometry.Offset(0f, -200f)) }
        compose.mainClock.advanceTimeByFrame()
        // Jari masih menempel: panel sudah tampil sebagian, belum terbuka penuh.
        assertTrue(state.visible)
        assertTrue(state.progress > 0f && state.progress < 1f, "progress=${state.progress}")
        compose.onNodeWithText("Cari atau ketik untuk menangkap…").assertExists()
        dock.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    @Test fun shortPullSpringsBack() {
        setUp()
        dock.performTouchInput { down(center); moveBy(androidx.compose.ui.geometry.Offset(0f, -80f), delayMillis = 400); up() }
        compose.waitForIdle()
        assertFalse(state.visible)
        compose.onNodeWithText("Cari atau ketik untuk menangkap…").assertDoesNotExist()
    }

    @Test fun longPullOrFlingOpens() {
        setUp()
        dock.performTouchInput { swipeUp(startY = centerY, endY = centerY - 900f, durationMillis = 300) }
        compose.waitForIdle()
        assertTrue(state.isOpen)
        assertTrue(state.progress == 1f)
        compose.onNodeWithText("Cari atau ketik untuk menangkap…").assertExists()
    }

    @Test fun accessibilityActionOpensWithoutDragging() {
        setUp()
        val node = compose.onNodeWithContentDescription("Tarik ke atas untuk mencari").fetchSemanticsNode()
        val action = node.config[androidx.compose.ui.semantics.SemanticsActions.CustomActions].single { it.label == "Buka pencarian" }
        compose.runOnIdle { action.action() }
        compose.waitForIdle()
        assertTrue(state.isOpen)
    }
}
