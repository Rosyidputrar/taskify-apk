package com.rosyidputrar.taskify

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.focus.FocusRun
import com.rosyidputrar.taskify.core.domain.focus.MINUTE
import com.rosyidputrar.taskify.core.domain.tasks.Task
import com.rosyidputrar.taskify.core.domain.tasks.TaskStatus
import com.rosyidputrar.taskify.core.store.focus.FocusStats
import com.rosyidputrar.taskify.ui.focus.FocusActions
import com.rosyidputrar.taskify.ui.focus.FocusContent
import com.rosyidputrar.taskify.ui.focus.FocusUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/** I-4: Mode Fokus — pilih tugas & durasi, hitung mundur, checklist langkah. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class FocusScreenTest {
    @get:Rule val compose = createComposeRule()
    private val padding = PaddingValues(top = 24.dp, bottom = 24.dp)
    private val t0 = 1_763_280_000_000L // 15.00 WIB

    private val report = SampleData.tasks[0]
    private val subtasks = listOf(
        report.copy(id = "s1", parentId = report.id, title = "Kumpulkan data penjualan", status = TaskStatus.Done),
        report.copy(id = "s2", parentId = report.id, title = "Tulis ringkasan", status = TaskStatus.Todo),
        report.copy(id = "s3", parentId = report.id, title = "Kirim ke tim", status = TaskStatus.Todo),
    )

    private fun show(dark: Boolean = false, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { TaskifyTheme(darkTheme = dark) { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
        compose.mainClock.advanceTimeBy(800)
    }

    private class Recorder : FocusActions {
        val calls = mutableListOf<String>()
        override fun select(taskId: String?) { calls += "select:$taskId" }
        override fun start() { calls += "start" }
        override fun pause() { calls += "pause" }
        override fun resume() { calls += "resume" }
        override fun skip() { calls += "skip" }
        override fun stop() { calls += "stop" }
        override fun toggle(task: Task) { calls += "toggle:${task.id}" }
        override fun completeTask() { calls += "complete" }
        override fun setFocusMinutes(minutes: Int) { calls += "minutes:$minutes" }
    }

    @Test fun setupScreenshotAndChoices() {
        val actions = Recorder()
        val state = FocusUiState(
            candidates = SampleData.tasks.take(3),
            selectedTaskId = report.id,
            task = report,
            today = FocusStats(minutes = 50, sessions = 2),
            loaded = true,
        )
        show { FocusContent(state, actions, padding, now = t0) }
        compose.onNodeWithText("Mau fokus ke apa?").assertExists()
        compose.onNodeWithText("Hari ini: 50 mnt fokus · 2 sesi").assertExists()
        compose.onRoot().captureRoboImage("screenshots/focus_setup_light.png")
        compose.onNodeWithText("45 mnt").performClick()
        compose.onNodeWithText("Fokus bebas").performClick()
        compose.onNodeWithText("Mulai fokus 25 menit").performClick()
        assertEquals(listOf("minutes:45", "select:null", "start"), actions.calls)
    }

    @Test fun runningScreenshotAndControls() {
        val actions = Recorder()
        val run = FocusRun.start(report.id, report.title, t0).copy(round = 2)
        val state = FocusUiState(run = run, task = report, subtasks = subtasks, loaded = true, today = FocusStats(25, 1))
        show(dark = true) { FocusContent(state, actions, padding, now = t0 + 7 * MINUTE + 30_000) }
        compose.onNodeWithText("17:30", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("selesai 15.25", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Fokus, sisa 18 menit").assertExists()
        compose.onNodeWithText("LANGKAH · 1/3").assertExists()
        compose.onNodeWithContentDescription("1 dari 4 putaran sebelum istirahat panjang").assertExists()
        compose.onRoot().captureRoboImage("screenshots/focus_running_dark.png")
        compose.onNodeWithContentDescription("Jeda").performClick()
        compose.onNodeWithContentDescription("Lewati ke istirahat").performClick()
        compose.onNodeWithText("Tulis ringkasan").performClick()
        compose.onNodeWithText("Tandai tugas selesai").performClick()
        assertEquals(listOf("pause", "skip", "toggle:s2", "complete"), actions.calls)
    }

    @Test fun waitingForNextRoundOffersStart() {
        val actions = Recorder()
        val run = FocusRun.start(null, null, t0).next(t0 + 25 * MINUTE).next(t0 + 30 * MINUTE)
        show { FocusContent(FocusUiState(run = run, loaded = true), actions, padding, now = t0 + 40 * MINUTE) }
        compose.onNodeWithText("25:00", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Fokus, sisa 25 menit, dijeda").assertExists()
        compose.onNodeWithText("Mulai putaran 2").performClick()
        compose.onNodeWithText("Cukup untuk sekarang").performClick()
        assertEquals(listOf("resume", "stop"), actions.calls)
    }

    @Test fun breakShowsRestMessage() {
        val run = FocusRun.start(null, null, t0).next(t0 + 25 * MINUTE)
        show { FocusContent(FocusUiState(run = run, loaded = true), FocusActions.None, padding, now = t0 + 26 * MINUTE) }
        compose.onNodeWithText("ISTIRAHAT").assertExists()
        compose.onNodeWithText("04:00", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Lewati istirahat").assertExists()
    }
}
