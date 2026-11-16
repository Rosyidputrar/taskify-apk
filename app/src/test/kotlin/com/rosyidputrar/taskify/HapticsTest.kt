package com.rosyidputrar.taskify

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.designsystem.component.LocalHapticsEnabled
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.feature.auth.LockContent
import com.rosyidputrar.taskify.feature.auth.LockUiState
import com.rosyidputrar.taskify.feature.auth.component.PinPad
import com.rosyidputrar.taskify.feature.sandi.SandiContent
import com.rosyidputrar.taskify.feature.tasks.component.TaskCheck
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Getar mengikuti makna aksi, dan saklar "Getaran" mematikan semuanya. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class HapticsTest {
    @get:Rule val compose = createComposeRule()

    private class Recorder : HapticFeedback {
        val played = mutableListOf<HapticFeedbackType>()
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { played += hapticFeedbackType }
    }

    private val rec = Recorder()

    private fun show(enabled: Boolean = true, content: @Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalHapticFeedback provides rec, LocalHapticsEnabled provides enabled) { TaskifyTheme { content() } }
    }

    private val todo = SampleData.tasks.first { it.status == com.rosyidputrar.taskify.core.domain.tasks.TaskStatus.Todo }

    @Test fun completingATaskFeelsLikeSuccess() {
        show { TaskCheck(todo, onToggle = {}) }
        compose.onNodeWithContentDescription("Tandai selesai").performClick()
        assertEquals(listOf(HapticFeedbackType.Confirm), rec.played)
    }

    @Test fun reopeningATaskIsAToggleOff() {
        show { TaskCheck(todo.copy(status = com.rosyidputrar.taskify.core.domain.tasks.TaskStatus.Done), onToggle = {}) }
        compose.onNodeWithContentDescription("Tandai belum selesai").performClick()
        assertEquals(listOf(HapticFeedbackType.ToggleOff), rec.played)
    }

    @Test fun pinKeysTickAndWrongPinRejects() {
        var digits = ""
        show { PinPad(onDigit = { digits += it }, onBackspace = {}, enabled = true) }
        compose.onNodeWithText("7").performClick()
        assertEquals("7", digits)
        assertEquals(listOf(HapticFeedbackType.VirtualKey), rec.played)
    }

    @Test fun wrongPinRejects() {
        show { LockContent(4, LockUiState(message = "PIN salah. Sisa 4 percobaan."), {}, {}, {}, {}) }
        compose.waitForIdle()
        assertTrue(HapticFeedbackType.Reject in rec.played)
    }

    @Test fun copyingASecretConfirms() {
        show { SandiContent(SampleSandi.state(), SampleSandi.Actions, androidx.compose.foundation.layout.PaddingValues()) }
        compose.onAllNodesWithContentDescription("Salin sandi")[0].performScrollTo().performClick()
        assertEquals(listOf(HapticFeedbackType.Confirm), rec.played)
    }

    @Test fun switchOffSilencesEverything() {
        show(enabled = false) { TaskCheck(todo, onToggle = {}) }
        compose.onNodeWithContentDescription("Tandai selesai").performClick()
        assertTrue(rec.played.isEmpty())
    }
}
