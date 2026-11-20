package com.rosyidputrar.taskify

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.calendar.DayActivity
import com.rosyidputrar.taskify.core.domain.home.HomeLayout
import com.rosyidputrar.taskify.core.domain.home.HomeSection
import com.rosyidputrar.taskify.core.domain.home.HomeTile
import com.rosyidputrar.taskify.ui.home.HomeContent
import com.rosyidputrar.taskify.ui.home.HomeLayoutEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** I-7: Beranda bisa disusun (urutan, sembunyikan bagian/ubin) + titik aktivitas di pita minggu. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class HomeArrangeTest {
    @get:Rule val compose = createComposeRule()
    private val padding = PaddingValues(top = 24.dp, bottom = 24.dp)

    private fun show(dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { TaskifyTheme(darkTheme = dark) { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
    }

    @Test fun editorScreenshot() {
        val layout = HomeLayout.Default.moveSection(HomeSection.Summary, -1).toggleSection(HomeSection.Week).toggleTile(HomeTile.Balance)
        show(dark = true) { HomeLayoutEditor(layout, {}, {}) }
        compose.onNodeWithText("Susun Beranda").assertExists()
        compose.onNodeWithContentDescription("Naikkan Pita minggu").assertIsNotEnabled()
        compose.onRoot().captureRoboImage("screenshots/home_arrange_dark.png")
    }

    @Test fun editorTogglesAndMoves() {
        var layout by mutableStateOf(HomeLayout.Default)
        show { HomeLayoutEditor(layout, { layout = it }, {}) }
        compose.onNodeWithText("Kembalikan bawaan").assertIsNotEnabled()
        compose.onNodeWithText("Pita minggu").performClick()
        assertTrue(HomeSection.Week in layout.hiddenSections)
        compose.onNodeWithContentDescription("Turunkan Fokus sekarang").performClick()
        assertEquals(listOf(HomeSection.Week, HomeSection.Shortcuts, HomeSection.Focus, HomeSection.Summary, HomeSection.Activity), layout.sections)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Saldo semua dompet"))
        compose.onNodeWithText("Saldo semua dompet").assertIsOff().performClick().assertIsOn()
        assertTrue(HomeTile.Balance in layout.visibleTiles)
        compose.onNodeWithText("Kembalikan bawaan").performClick()
        assertEquals(HomeLayout.Default, layout)
    }

    @Test fun homeFollowsLayout() {
        val layout = HomeLayout.Default
            .toggleSection(HomeSection.Focus)
            .toggleTile(HomeTile.Mood).toggleTile(HomeTile.Balance).moveTile(HomeTile.Balance, -10)
        val state = SampleData.homeState().copy(layout = layout, summary = SampleData.homeState().summary.copy(balance = 2_400_000))
        show { HomeContent(state, "Rosyid", padding, {}, {}, {}, {}) }
        compose.onNodeWithText("FOKUS SEKARANG").assertDoesNotExist()
        compose.onNodeWithText("Mood hari ini").assertDoesNotExist()
        compose.onNodeWithText("Saldo semua dompet").assertExists()
        compose.onNodeWithText("Rp 2,4 jt", substring = true).assertExists()
    }

    @Test fun allHiddenShowsHint() {
        val layout = HomeSection.entries.fold(HomeLayout.Default) { l, s -> l.toggleSection(s) }
        show { HomeContent(SampleData.homeState().copy(layout = layout), "Rosyid", padding, {}, {}, {}, {}) }
        compose.onNodeWithText("Beranda dikosongkan").assertExists()
        compose.onNodeWithContentDescription("Susun Beranda").assertExists()
    }

    @Test fun weekRibbonDescribesActivity() {
        val today = SampleData.homeState().today
        val week = mapOf(today to DayActivity(tasks = 3, journal = 1, transactions = 2))
        show { HomeContent(SampleData.homeState().copy(week = week), "Rosyid", padding, {}, {}, {}, {}) }
        compose.onNodeWithContentDescription("hari ini, 3 tugas, 1 jurnal, 2 transaksi", substring = true).assertExists()
    }
}
