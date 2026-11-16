package com.rosyidputrar.taskify

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.rosyidputrar.taskify.core.designsystem.component.LoadingBlock
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.feature.calendar.CalendarUiState
import com.rosyidputrar.taskify.feature.tasks.TasksUiState
import com.rosyidputrar.taskify.ui.home.HomeContent
import com.rosyidputrar.taskify.ui.home.HomeUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Sebelum data pertama dari database lokal tiba, layar menampilkan kerangka — bukan halaman kosong. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class LoadingStatesTest {
    @get:Rule val compose = createComposeRule()
    private val padding = PaddingValues(top = 24.dp, bottom = 24.dp)

    private fun show(dark: Boolean = false, content: @Composable () -> Unit) {
        // Indikator & kilau skeleton beranimasi tanpa henti: jam dimajukan manual.
        compose.mainClock.autoAdvance = false
        compose.setContent { TaskifyTheme(darkTheme = dark) { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
        compose.mainClock.advanceTimeBy(600)
    }

    @Test fun homeShowsSkeletonUntilLoaded() {
        show { HomeContent(HomeUiState(today = "2026-10-07"), "Rosyid", padding, {}, {}, {}, {}) }
        compose.onNodeWithContentDescription("Memuat ringkasan hari ini…").assertExists()
        compose.onNodeWithText("Lini waktu masih kosong").assertDoesNotExist()
        compose.onRoot().captureRoboImage("screenshots/loading_home_light.png")
    }

    @Test fun homeShowsContentOnceLoaded() {
        show { HomeContent(SampleData.homeState(), "Rosyid", padding, {}, {}, {}, {}) }
        compose.onNodeWithContentDescription("Memuat ringkasan hari ini…").assertDoesNotExist()
    }

    @Test fun tasksShowSkeletonNotEmptyState() {
        show(dark = true) { com.rosyidputrar.taskify.feature.tasks.TasksContent(TasksUiState(today = "2026-10-07"), SampleData.NoActions, padding) }
        compose.onNodeWithContentDescription("Memuat…").assertExists()
        compose.onRoot().captureRoboImage("screenshots/loading_tasks_dark.png")
    }

    @Test fun calendarGridAppearsImmediately() {
        val month = "2026-10-01"
        show {
            com.rosyidputrar.taskify.feature.calendar.CalendarContent(
                CalendarUiState(today = "2026-10-07", month = month, selected = "2026-10-07", grid = com.rosyidputrar.taskify.core.domain.calendar.buildMonthGrid(month)),
                contentPadding = padding,
                detailEnabled = false,
            )
        }
        // Kisi bulan sudah tampil (tanggal 15 terlihat); agenda masih kerangka.
        compose.onNodeWithText("15").assertExists()
        compose.onNodeWithContentDescription("Memuat…").assertExists()
    }

    @Test fun loadingIndicatorBlock() {
        show { LoadingBlock(label = "Membuka brankas…") }
        compose.onNodeWithContentDescription("Membuka brankas…").assertExists()
        compose.onRoot().captureRoboImage("screenshots/loading_block_light.png")
    }
}
