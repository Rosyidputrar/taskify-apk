package com.rosyidputrar.taskify

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.feature.sandi.ItemDetail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** Menghapus item Sandi butuh konfirmasi (dulu satu ketukan langsung menghapus). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class SandiDeleteTest {
    @get:Rule val compose = createComposeRule()

    private var deleted = 0

    private fun setDetail() = compose.setContent {
        TaskifyTheme { ItemDetail(SampleSandi.items[0], SampleSandi.NOW, SampleSandi.Actions, onEdit = {}, onDelete = { deleted++ }) }
    }

    @Test fun deleteAsksForConfirmation() {
        setDetail()
        compose.onNodeWithText("Hapus").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(0, deleted, "satu ketukan tidak langsung menghapus")
        compose.onNodeWithText("Item dipindahkan ke Sampah", substring = true).assertExists()

        compose.onNodeWithText("Batal").performClick()
        compose.waitForIdle()
        assertEquals(0, deleted)

        compose.onNodeWithText("Hapus").performScrollTo().performClick()
        compose.waitForIdle()
        // Tombol konfirmasi di dialog (yang di layar detail sudah tertutup dialog).
        compose.onNodeWithText("Item dipindahkan ke Sampah", substring = true).assertExists()
        compose.onAllNodesWithTextExact("Hapus").last().performClick()
        compose.waitForIdle()
        assertEquals(1, deleted)
    }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextExact(text: String) =
    onAllNodes(androidx.compose.ui.test.hasText(text, substring = false)).fetchSemanticsNodes().indices.map { i ->
        onAllNodes(androidx.compose.ui.test.hasText(text, substring = false))[i]
    }
