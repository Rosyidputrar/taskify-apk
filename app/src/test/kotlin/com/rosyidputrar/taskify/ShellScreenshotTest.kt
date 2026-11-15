package com.rosyidputrar.taskify

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.designsystem.theme.ThemeMode
import com.rosyidputrar.taskify.data.AppPreferences
import com.rosyidputrar.taskify.ui.shell.AppEvent
import com.rosyidputrar.taskify.ui.shell.AppEvents
import com.rosyidputrar.taskify.ui.shell.TaskifyApp
import com.rosyidputrar.taskify.ui.home.HomeContent
import org.junit.Rule
import androidx.compose.ui.unit.dp
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tangkapan layar shell aplikasi (terang & gelap, HP & tablet) untuk meninjau desain tanpa emulator.
 * Rekam: ./gradlew :app:recordRoborazziDebug · Bandingkan: ./gradlew :app:verifyRoborazziDebug
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class ShellScreenshotTest {

    @get:Rule val compose = createComposeRule()

    private fun setApp(dark: Boolean, pinned: TaskifyModule? = null) {
        compose.setContent {
            TaskifyTheme(darkTheme = dark) {
                TaskifyApp(
                    preferences = AppPreferences.Snapshot(if (dark) ThemeMode.Dark else ThemeMode.Light, pinned),
                    onThemeModeChange = {},
                    onPinnedModuleChange = {},
                    displayName = "Rosyid",
                    home = { padding, name, openModule, openTask ->
                        HomeContent(SampleData.homeState(), name, padding, openModule, openTask, {}, {})
                    },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun capture(name: String) = compose.onRoot().captureRoboImage("screenshots/$name.png")

    @Test fun homeLight() {
        setApp(dark = false)
        capture("home_light")
    }

    @Test fun homeDark() {
        setApp(dark = true, pinned = TaskifyModule.Finance)
        capture("home_dark_pinned")
    }

    @Test fun roomLight() {
        setApp(dark = false)
        compose.onNodeWithContentDescription("Ruang").performClick()
        compose.waitForIdle()
        capture("room_light")
    }

    @Test fun roomDark() {
        setApp(dark = true)
        compose.onNodeWithContentDescription("Ruang").performClick()
        compose.waitForIdle()
        capture("room_dark")
    }

    @Test fun spotlight() {
        setApp(dark = false)
        AppEvents.send(AppEvent.OpenSpotlight)
        compose.waitForIdle()
        capture("spotlight_light")
    }

    @Test fun moduleDark() {
        setApp(dark = true)
        compose.onNodeWithContentDescription("Ruang").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Jurnal").performClick()
        compose.waitForIdle()
        capture("module_placeholder_dark")
    }

    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    @Test fun tabletLight() {
        setApp(dark = false)
        capture("home_tablet_light")
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class TasksScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun shot(name: String, dark: Boolean) {
        compose.setContent {
            TaskifyTheme(darkTheme = dark) {
                androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                    com.rosyidputrar.taskify.feature.tasks.TasksContent(
                        SampleData.tasksState(),
                        SampleData.NoActions,
                        androidx.compose.foundation.layout.PaddingValues(top = 24.dp, bottom = 24.dp),
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test fun tasksLight() = shot("tasks_light", dark = false)

    @Test fun tasksDark() = shot("tasks_dark", dark = true)
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class CalendarScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun shot(name: String, dark: Boolean) {
        compose.setContent {
            TaskifyTheme(darkTheme = dark) {
                androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                    com.rosyidputrar.taskify.feature.calendar.CalendarContent(
                        SampleData.calendarState(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 24.dp, bottom = 24.dp),
                        detailEnabled = false,
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test fun calendarLight() = shot("calendar_light", dark = false)

    @Test fun calendarDark() = shot("calendar_dark", dark = true)

    @Config(qualifiers = "w393dp-h1900dp-xxhdpi")
    @Test fun calendarAgenda() = shot("calendar_agenda_tall_light", dark = false)
}
