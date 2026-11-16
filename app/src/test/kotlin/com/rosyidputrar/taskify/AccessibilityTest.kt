package com.rosyidputrar.taskify

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.feature.finance.FinanceContent
import com.rosyidputrar.taskify.feature.media.MusicContent
import com.rosyidputrar.taskify.feature.organize.TrashContent
import com.rosyidputrar.taskify.feature.organize.TrashUiState
import com.rosyidputrar.taskify.feature.review.ReviewContent
import com.rosyidputrar.taskify.feature.review.ReviewNav
import com.rosyidputrar.taskify.feature.sandi.SandiContent
import com.rosyidputrar.taskify.feature.sandi.SandiTab
import com.rosyidputrar.taskify.ui.home.HomeContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/**
 * Aksesibilitas: (1) setiap elemen yang bisa diketuk punya nama untuk TalkBack (teks atau
 * contentDescription, sendiri atau tergabung), (2) tata letak tetap terbaca pada ukuran huruf
 * 160% (setelan "Huruf sangat besar" Android).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class AccessibilityTest {
    @get:Rule val compose = createComposeRule()
    private val padding = PaddingValues(top = 24.dp, bottom = 24.dp)

    private val screens: Map<String, @Composable () -> Unit> = mapOf(
        "home" to { HomeContent(SampleData.homeState(), "Rosyid", padding, {}, {}, {}, {}) },
        "tasks" to { com.rosyidputrar.taskify.feature.tasks.TasksContent(SampleData.tasksState(), SampleData.NoActions, padding) },
        "finance" to { FinanceContent(SampleFinance.state(), SampleFinance.Actions, padding, {}, {}) },
        "sandi" to { SandiContent(SampleSandi.state(tab = SandiTab.Codes), SampleSandi.Actions, padding) },
        "review" to { ReviewContent(SampleReview.state(), SampleReview.Actions, ReviewNav(), padding) },
        "music" to { MusicContent(SampleCross.music(), SampleCross.Actions, padding) },
        "trash" to { TrashContent(TrashUiState(SampleCross.trash, true, SampleCross.NOW), SampleCross.Actions, padding) },
    )

    private fun set(content: @Composable () -> Unit, fontScale: Float = 1f) {
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                TaskifyTheme(darkTheme = false) { Surface(color = MaterialTheme.colorScheme.background) { content() } }
            }
        }
        compose.waitForIdle()
    }

    private fun SemanticsNode.label(): String {
        val cfg = config
        val texts = cfg.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }.orEmpty()
        val desc = cfg.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
        val state = cfg.getOrNull(SemanticsProperties.StateDescription).orEmpty()
        val editable = cfg.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
        val own = listOf(texts, desc, state, editable).joinToString(" ").trim()
        return own.ifEmpty { children.joinToString(" ") { it.label() }.trim() }
    }

    private fun SemanticsNode.walk(out: MutableList<SemanticsNode>) { out += this; children.forEach { it.walk(out) } }

    @Test fun everyClickableHasAName() {
        val problems = mutableListOf<String>()
        // Satu setContent per tes: layar diganti lewat state.
        val current = androidx.compose.runtime.mutableStateOf(screens.keys.first())
        set({ androidx.compose.runtime.key(current.value) { screens.getValue(current.value)() } })
        screens.forEach { (name, _) ->
            current.value = name
            compose.waitForIdle()
            val nodes = mutableListOf<SemanticsNode>()
            compose.onRoot(useUnmergedTree = false).fetchSemanticsNode().walk(nodes)
            nodes.filter { it.config.contains(SemanticsActions.OnClick) }.forEach { n ->
                if (n.label().isEmpty()) problems += "$name: elemen tanpa label di ${n.boundsInRoot}"
            }
        }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    @Test fun largeFontHome() { set(screens.getValue("home"), 1.6f); compose.onRoot().captureRoboImage("screenshots/a11y_font160_home.png") }

    @Test fun largeFontTasks() { set(screens.getValue("tasks"), 1.6f); compose.onRoot().captureRoboImage("screenshots/a11y_font160_tasks.png") }

    @Test fun largeFontFinance() { set(screens.getValue("finance"), 1.6f); compose.onRoot().captureRoboImage("screenshots/a11y_font160_finance.png") }

    @Test fun largeFontSandi() { set(screens.getValue("sandi"), 1.6f); compose.onRoot().captureRoboImage("screenshots/a11y_font160_sandi.png") }
}
