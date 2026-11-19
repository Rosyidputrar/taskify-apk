package com.rosyidputrar.taskify

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.feature.sandi.ImportDialog
import com.rosyidputrar.taskify.feature.sandi.ImportDone
import com.rosyidputrar.taskify.feature.sandi.ImportDoneDialog
import com.rosyidputrar.taskify.feature.sandi.SandiActions
import com.rosyidputrar.taskify.feature.sandi.SandiContent
import com.rosyidputrar.taskify.feature.sandi.SandiImporter
import com.rosyidputrar.taskify.feature.sandi.SettingsBody
import com.rosyidputrar.taskify.feature.sandi.importLines
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** Impor CSV: tombol di Pengaturan, ringkasan sebelum impor, hasil + hapus berkas, pemilih sekali. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class SandiImportUiTest {
    @get:Rule val compose = createComposeRule()

    private class Recorder : SandiActions by SampleSandi.Actions {
        val calls = mutableListOf<String>()
        override fun startImport() { calls += "start" }
        override fun importLaunched() { calls += "launched" }
        override fun confirmImport() { calls += "confirm" }
        override fun cancelImport() { calls += "cancel" }
        override fun deleteImportSource() { calls += "delete" }
        override fun dismissImportDone() { calls += "dismiss" }
    }

    @Test fun settingsOffersImport() {
        val actions = Recorder()
        compose.setContent { TaskifyTheme { SettingsBody(SampleSandi.state(), actions, {}, {}, onImport = actions::startImport) } }
        compose.onNodeWithText("Impor dari CSV").performScrollTo().performClick()
        assertEquals(listOf("start"), actions.calls)
    }

    @Test fun previewExplainsEveryNumberBeforeImporting() {
        val actions = Recorder()
        compose.setContent { TaskifyTheme { ImportDialog(SampleSandi.importPreview, SampleSandi.state(), actions) } }
        importLines(SampleSandi.importPreview).forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("Contoh: GitHub, Bank Jago, Tokopedia, Steam, Shopee, …").assertExists()
        compose.onNodeWithText("Impor 42").assertIsEnabled().performClick()
        compose.onNodeWithText("Batal").performClick()
        assertEquals(listOf("confirm", "cancel"), actions.calls)
    }

    @Test fun nothingNewCannotBeImported() {
        val none = SampleSandi.importPreview.copy(add = 0, withTotp = 0, sample = emptyList())
        compose.setContent { TaskifyTheme { ImportDialog(none, SampleSandi.state(), Recorder()) } }
        compose.onNodeWithText("Tidak ada sandi baru untuk ditambahkan.").assertExists()
        compose.onNodeWithText("Impor").assertIsNotEnabled()
    }

    @Test fun busyImportCannotBeCancelledHalfway() {
        compose.setContent { TaskifyTheme { ImportDialog(SampleSandi.importPreview, SampleSandi.state().copy(busy = "Mengimpor…"), Recorder()) } }
        compose.onNodeWithText("Impor 42").assertIsNotEnabled()
        compose.onNodeWithText("Batal").assertIsNotEnabled()
    }

    @Test fun doneOffersToDeleteThePlainTextFile() {
        val actions = Recorder()
        compose.setContent { TaskifyTheme { ImportDoneDialog(ImportDone(41, listOf("Catatan server")), actions) } }
        compose.onNodeWithText("41 sandi diimpor").assertExists()
        compose.onNodeWithText("1 item terlalu besar", substring = true).assertExists()
        compose.onNodeWithText("Hapus berkas CSV").performClick()
        compose.onNodeWithText("Nanti").performClick()
        assertEquals(listOf("delete", "dismiss"), actions.calls)
    }

    @Test fun lineWording() {
        assertEquals(
            listOf(
                "Dari: Pengelola Sandi Google / Chrome",
                "42 sandi baru akan ditambahkan, 3 di antaranya dengan kunci 2FA.",
                "5 sudah ada di brankas (sama persis), dilewati.",
                "2 akun sudah ada dengan sandi berbeda, ditambahkan sebagai item baru. Item lama tidak diubah.",
                "1 baris kembar di berkas, dilewati.",
                "1 baris tanpa sandi, dilewati.",
                "1 kunci 2FA tidak valid. Sandinya tetap diimpor tanpa 2FA.",
            ),
            importLines(SampleSandi.importPreview),
        )
    }

    /** Lewat layar Sandi sungguhan (actions dibungkus haptik): pemilih berkas dibuka sekali. */
    @Test fun pickerOpensThroughSandiScreen() {
        val actions = Recorder()
        compose.setContent { TaskifyTheme { SandiContent(SampleSandi.state().copy(importPicking = SandiImporter.Picking()), actions, PaddingValues()) } }
        compose.waitForIdle()
        assertEquals(listOf("launched"), actions.calls)
    }
}
