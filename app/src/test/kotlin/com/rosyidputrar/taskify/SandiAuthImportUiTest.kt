package com.rosyidputrar.taskify

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.feature.sandi.AuthImportContent
import com.rosyidputrar.taskify.feature.sandi.AuthImportUi
import com.rosyidputrar.taskify.feature.sandi.SandiActions
import com.rosyidputrar.taskify.feature.sandi.SandiContent
import com.rosyidputrar.taskify.feature.sandi.SettingsBody
import com.rosyidputrar.taskify.feature.sandi.authImportLines
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** Impor 2FA dari Google Authenticator: tombol, cara pindai, ringkasan bertahap, sakelar pasang. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class SandiAuthImportUiTest {
    @get:Rule val compose = createComposeRule()

    private class Recorder : SandiActions by SampleSandi.Actions {
        val calls = mutableListOf<String>()
        override fun startAuthImport() { calls += "start" }
        override fun setAuthAttach(on: Boolean) { calls += "attach=$on" }
        override fun confirmAuthImport() { calls += "confirm" }
        override fun closeAuthImport() { calls += "close" }
    }

    @Test fun settingsOffersAuthenticatorImport() {
        val actions = Recorder()
        compose.setContent { TaskifyTheme { SettingsBody(SampleSandi.state(), actions, {}, {}, onImportAuth = actions::startAuthImport) } }
        compose.onNodeWithText("2FA dari Google Authenticator").performScrollTo().performClick()
        assertEquals(listOf("start"), actions.calls)
    }

    @Test fun emptySheetExplainsHowAndOffersThreeWays() {
        val sources = mutableListOf<String>()
        compose.setContent {
            TaskifyTheme { AuthImportContent(AuthImportUi(), SampleSandi.state(), Recorder(), { sources += "scan" }, { sources += "photo" }, { sources += "paste" }) }
        }
        compose.onNodeWithText("Transfer akun → Ekspor akun", substring = true).assertExists()
        compose.onNodeWithText("Pindai QR").performClick()
        compose.onNodeWithText("Dari foto").performClick()
        compose.onNodeWithText("Tempel teks").performClick()
        assertEquals(listOf("scan", "photo", "paste"), sources)
        compose.onNodeWithText("Impor").assertIsNotEnabled()
    }

    @Test fun progressShowsMissingQrAndAttachSwitch() {
        val actions = Recorder()
        compose.setContent { TaskifyTheme { AuthImportContent(SampleSandi.authImport, SampleSandi.state(), actions, {}, {}, {}) } }
        authImportLines(SampleSandi.authImport).forEach { compose.onNodeWithText(it).performScrollTo().assertExists() }
        compose.onNodeWithText("Pindai QR berikutnya").assertExists()
        compose.onNode(isToggleable()).performScrollTo().assertIsOn().performClick()
        compose.onNodeWithText("Impor 15").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("Batal").performScrollTo().performClick()
        assertEquals(listOf("attach=false", "confirm", "close"), actions.calls)
    }

    @Test fun busyBlocksEverything() {
        compose.setContent { TaskifyTheme { AuthImportContent(SampleSandi.authImport, SampleSandi.state().copy(busy = "Mengimpor 2FA…"), Recorder(), {}, {}, {}) } }
        compose.onNodeWithText("Pindai QR berikutnya").assertIsNotEnabled()
        compose.onNodeWithText("Impor 15").performScrollTo().assertIsNotEnabled()
    }

    @Test fun lineWording() {
        assertEquals(
            listOf(
                "QR terbaca: 2 dari 3.",
                "Belum dipindai: QR 2. Akun di QR itu tidak ikut kalau dilewati.",
                "13 akun 2FA baru.",
                "2 dipasang ke sandi yang sudah ada: GitHub · rosyid@example.com, Bank Jago · rosyidputra",
                "1 sudah ada di brankas, dilewati.",
                "Tidak didukung (1): Lama (hotp-user) — HOTP (berbasis penghitung) belum didukung",
            ),
            authImportLines(SampleSandi.authImport),
        )
    }

    /** Lewat layar Sandi sungguhan: lembar impor muncul dari state ViewModel. */
    @Test fun sheetAppearsFromScreenState() {
        compose.setContent { TaskifyTheme { SandiContent(SampleSandi.state().copy(authImport = AuthImportUi()), Recorder(), PaddingValues()) } }
        compose.onNodeWithText("Impor 2FA dari Google Authenticator").assertExists()
    }
}
