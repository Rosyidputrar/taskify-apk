package com.rosyidputrar.taskify

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.designsystem.theme.ThemeContrast
import com.rosyidputrar.taskify.core.designsystem.theme.ThemePalette
import com.rosyidputrar.taskify.core.designsystem.theme.ThemeSpec
import com.rosyidputrar.taskify.core.designsystem.theme.ThemeStyle
import com.rosyidputrar.taskify.ui.home.HomeContent
import com.rosyidputrar.taskify.ui.settings.ThemePaletteSettings
import com.rosyidputrar.taskify.ui.settings.ThemeStudioContent
import com.rosyidputrar.taskify.ui.settings.parseHex
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class ThemeScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val padding = PaddingValues(top = 24.dp, bottom = 24.dp)

    private fun shot(name: String, dark: Boolean, spec: ThemeSpec, content: @Composable () -> Unit) {
        compose.setContent { TaskifyTheme(darkTheme = dark, spec = spec) { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    private val home: @Composable () -> Unit = { HomeContent(SampleData.homeState(), "Rosyid", padding, {}, {}, {}, {}) }
    private val tasks: @Composable () -> Unit = { com.rosyidputrar.taskify.feature.tasks.TasksContent(SampleData.tasksState(), SampleData.NoActions, padding) }

    @Test fun homeForest() = shot("theme_home_hutan_dark", true, ThemeSpec(palette = ThemePalette.Hutan), home)
    @Test fun homeSakura() = shot("theme_home_sakura_light", false, ThemeSpec(palette = ThemePalette.Sakura), home)
    @Test fun tasksGraphite() = shot("theme_tasks_grafit_light", false, ThemeSpec(palette = ThemePalette.Grafit), tasks)
    @Test fun tasksLavenderHighContrast() = shot("theme_tasks_lavender_kontras_dark", true, ThemeSpec(palette = ThemePalette.Lavender, contrast = ThemeContrast.High), tasks)
    @Test fun homeCustomMonochrome() = shot("theme_home_kustom_mono_light", false, ThemeSpec(ThemePalette.Kustom, 0xFF5B6470.toInt(), ThemeStyle.Monochrome, paper = false), home)

    @Test fun studio() = shot("theme_studio_light", false, ThemeSpec.Default) {
        ThemeStudioContent(ThemeSpec(ThemePalette.Kustom, 0xFFC8553D.toInt(), ThemeStyle.Vibrant, true), onApply = {}, onDismiss = {})
    }

    @Test fun paletteSettings() = shot("theme_settings_dark", true, ThemeSpec(palette = ThemePalette.Laut)) {
        Column(Modifier.padding(20.dp)) { ThemePaletteSettings(ThemeSpec(palette = ThemePalette.Laut), onThemeChange = {}) }
    }

    @Test fun choosingAPaletteAppliesIt() {
        var chosen: ThemeSpec? = null
        compose.setContent { TaskifyTheme { Column(Modifier.padding(20.dp)) { ThemePaletteSettings(ThemeSpec.Default, onThemeChange = { chosen = it }) } } }
        compose.onNodeWithContentDescription("Palet Hutan").performClick()
        assertEquals(ThemePalette.Hutan, chosen?.palette)
        compose.onNodeWithText("Tinggi").performClick()
        assertEquals(ThemeContrast.High, chosen?.contrast)
    }

    @Test fun studioAppliesCustomSeed() {
        var applied: ThemeSpec? = null
        compose.setContent { TaskifyTheme { ThemeStudioContent(ThemeSpec(palette = ThemePalette.Kustom), onApply = { applied = it }, onDismiss = {}) } }
        compose.onNodeWithContentDescription("Warna #C2457A").performClick()
        compose.onNodeWithText("Ekspresif").performClick()
        compose.onAllNodesWithTextFirst("Terapkan")
        assertEquals(ThemePalette.Kustom, applied?.palette)
        assertEquals(0xC2457A, applied!!.seed and 0xFFFFFF)
        assertEquals(ThemeStyle.Expressive, applied!!.style)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextFirst(text: String) =
        onAllNodes(androidx.compose.ui.test.hasText(text))[0].performClick()

    @Test fun hexParsing() {
        assertEquals(0xFF1D5867.toInt(), parseHex("#1d5867"))
        assertEquals(0xFF1D5867.toInt(), parseHex("1D5867"))
        assertNull(parseHex("#12345"))
        assertNull(parseHex("zzzzzz"))
    }
}
