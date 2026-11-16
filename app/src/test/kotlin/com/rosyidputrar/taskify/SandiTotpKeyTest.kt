package com.rosyidputrar.taskify

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.feature.sandi.ItemDetail
import com.rosyidputrar.taskify.feature.sandi.SandiActions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** Kunci rahasia 2FA bisa dilihat, disalin, dan dijadikan QR — tersembunyi secara bawaan. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class SandiTotpKeyTest {
    @get:Rule val compose = createComposeRule()

    private class Recorder : SandiActions by SampleSandi.Actions {
        val copies = mutableListOf<Triple<String, String, Boolean>>()
        override fun copy(what: String, text: String, secret: Boolean) { copies += Triple(what, text, secret) }
    }

    private val github = SampleSandi.items[0] // kunci JBSWY3DPEHPK3PXP

    @Test fun keyIsHiddenUntilRevealed() {
        compose.setContent { TaskifyTheme { ItemDetail(github, SampleSandi.NOW, SampleSandi.Actions, onEdit = {}, onDelete = {}) } }
        compose.onNodeWithText("JBSW Y3DP EHPK 3PXP").assertDoesNotExist()
        compose.onNodeWithContentDescription("Tampilkan kunci 2FA").performScrollTo().performClick()
        compose.onNodeWithText("JBSW Y3DP EHPK 3PXP").assertExists()
        compose.onNodeWithContentDescription("Sembunyikan kunci 2FA").performClick()
        compose.onNodeWithText("JBSW Y3DP EHPK 3PXP").assertDoesNotExist()
    }

    @Test fun copyUsesSelfClearingClipboard() {
        val actions = Recorder()
        compose.setContent { TaskifyTheme { ItemDetail(github, SampleSandi.NOW, actions, onEdit = {}, onDelete = {}) } }
        compose.onNodeWithContentDescription("Salin Kunci 2FA").performScrollTo().performClick()
        assertEquals(Triple("Kunci 2FA", "JBSWY3DPEHPK3PXP", true), actions.copies.single())
    }

    @Test fun qrForOtherAuthenticators() {
        compose.setContent { TaskifyTheme { ItemDetail(github, SampleSandi.NOW, SampleSandi.Actions, onEdit = {}, onDelete = {}) } }
        compose.onNodeWithContentDescription("Tampilkan QR kunci 2FA").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Kode QR kunci 2FA GitHub").assertExists()
        compose.onNodeWithText("Tutup").performClick()
        compose.onNodeWithContentDescription("Kode QR kunci 2FA GitHub").assertDoesNotExist()
    }
}
