package com.rosyidputrar.taskify

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.sandi.ExportKind
import com.rosyidputrar.taskify.core.domain.sandi.GoogleAuthIssue
import com.rosyidputrar.taskify.core.domain.sandi.GoogleAuthPlan
import com.rosyidputrar.taskify.core.domain.sandi.ItemPayload
import com.rosyidputrar.taskify.core.domain.sandi.TotpConfig
import com.rosyidputrar.taskify.core.sandi.DecryptedItems
import com.rosyidputrar.taskify.core.sandi.SandiItem
import com.rosyidputrar.taskify.feature.sandi.ExportConfirmBody
import com.rosyidputrar.taskify.feature.sandi.canStartExport
import com.rosyidputrar.taskify.feature.sandi.ExportHost
import com.rosyidputrar.taskify.feature.sandi.MigrationDialog
import com.rosyidputrar.taskify.feature.sandi.PendingExport
import com.rosyidputrar.taskify.feature.sandi.SandiActions
import com.rosyidputrar.taskify.feature.sandi.SandiContent
import com.rosyidputrar.taskify.feature.sandi.SettingsBody
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Ekspor Sandi: tombol di Pengaturan, ringkasan + master password, QR bertahap, pemilih lokasi sekali. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class SandiExportUiTest {
    @get:Rule val compose = createComposeRule()

    private class Recorder : SandiActions by SampleSandi.Actions {
        val exports = mutableListOf<Pair<ExportKind, String>>()
        var launched = 0
        var closed = 0
        override fun startExport(kind: ExportKind, password: String) { exports += kind to password }
        override fun exportLaunched() { launched++ }
        override fun closeMigration() { closed++ }
    }

    @Test fun settingsOffersThreeExports() {
        val kinds = mutableListOf<ExportKind>()
        compose.setContent { TaskifyTheme { SettingsBody(SampleSandi.state(), SampleSandi.Actions, {}, {}, onExport = { kinds += it }) } }
        compose.onNodeWithText("Sandi ke Pengelola Sandi Google").performScrollTo().performClick()
        compose.onNodeWithText("2FA ke Google Authenticator").performScrollTo().performClick()
        compose.onNodeWithText("2FA ke aplikasi lain").performScrollTo().performClick()
        assertEquals(listOf(ExportKind.GoogleCsv, ExportKind.GoogleAuthenticator, ExportKind.OtpauthList), kinds)
    }

    // Isi dialog diuji tanpa jendela dialog: kolom teks di dalam AlertDialog membuat Robolectric tidak
    // pernah idle (lingkungan tes; ChangePasswordDialog memakai pola yang sama di aplikasi).

    @Test fun csvConfirmSummarisesAndRequiresMasterPassword() {
        val state = SampleSandi.state()
        var password by mutableStateOf("")
        var submitted = 0
        compose.setContent { TaskifyTheme { ExportConfirmBody(ExportKind.GoogleCsv, state, password, { password = it }, { submitted++ }) } }
        // Sampel: 5 item, Cloudflare hanya 2FA, Wi-Fi tanpa alamat situs.
        compose.onNodeWithText("4 sandi akan diekspor ke berkas CSV.").assertExists()
        compose.onNodeWithText("1 item tanpa sandi (mis. hanya kunci 2FA) tidak ikut.").assertExists()
        compose.onNodeWithText("1 sandi tanpa alamat situs", substring = true).assertExists()
        compose.onNodeWithText("Berkas ini berisi semua sandi tanpa enkripsi", substring = true).assertExists()
        assertFalse(canStartExport(ExportKind.GoogleCsv, state, password))
        compose.onNode(hasSetTextAction()).performTextInput("master rahasia")
        assertEquals("master rahasia", password)
        assertTrue(canStartExport(ExportKind.GoogleCsv, state, password))
        assertFalse(canStartExport(ExportKind.GoogleCsv, state.copy(busy = "Memeriksa master password…"), password))
        compose.onNode(hasSetTextAction()).performImeAction()
        assertEquals(1, submitted)
    }

    @Test fun googleConfirmNamesKeysThatCannotMove() {
        val steam = SandiItem("i9", ItemPayload("Steam", username = "gamer", totp = TotpConfig("JBSWY3DPEHPK3PXP", period = 60)), "", "")
        val state = SampleSandi.state().let { it.copy(items = DecryptedItems(it.items.items + steam, emptyList(), true)) }
        compose.setContent { TaskifyTheme { ExportConfirmBody(ExportKind.GoogleAuthenticator, state, "", {}, {}) } }
        compose.onNodeWithText("2 akun 2FA akan ditampilkan sebagai QR untuk dipindai.").assertExists()
        compose.onNodeWithText("1 kunci tidak didukung Google Authenticator dan tidak ikut: Steam (periode bukan 30 dtk)", substring = true).assertExists()
        compose.onNodeWithText("Siapa pun yang melihat QR ini", substring = true).assertExists()
    }

    @Test fun nothingToExportHidesPasswordField() {
        val onlyPasswords = SampleSandi.state().let { s -> s.copy(items = DecryptedItems(s.items.items.filter { it.payload.totp == null }, emptyList(), true)) }
        compose.setContent { TaskifyTheme { ExportConfirmBody(ExportKind.OtpauthList, onlyPasswords, "", {}, {}) } }
        compose.onNodeWithText("Tidak ada yang bisa diekspor.").assertExists()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        assertFalse(canStartExport(ExportKind.OtpauthList, onlyPasswords, "master rahasia"))
    }

    @Test fun undecryptableItemsAreMentioned() {
        val state = SampleSandi.state().let { it.copy(items = it.items.copy(failed = listOf("x1", "x2"))) }
        compose.setContent { TaskifyTheme { ExportConfirmBody(ExportKind.GoogleCsv, state, "", {}, {}) } }
        compose.onNodeWithText("2 item tidak bisa dibuka dengan kunci ini dan tidak ikut.").assertExists()
    }

    @Test fun migrationQrIsShownOnePageAtATime() {
        val actions = Recorder()
        val plan = GoogleAuthPlan(
            listOf("otpauth-migration://offline?data=CgAQARgCIAAoAQ%3D%3D", "otpauth-migration://offline?data=CgAQARgCIAEoAQ%3D%3D"),
            accounts = 10,
            skipped = listOf("Steam" to GoogleAuthIssue.Algorithm),
        )
        compose.setContent { TaskifyTheme { MigrationDialog(plan, actions) } }
        compose.onNodeWithText("QR 1 dari 2").assertExists()
        compose.onNodeWithContentDescription("Kode QR transfer akun 1 dari 2").assertExists()
        compose.onNodeWithText("Tidak ikut (1): Steam — algoritme bukan SHA1").assertExists()
        compose.onNodeWithText("Berikutnya").performClick()
        compose.onNodeWithText("QR 2 dari 2").assertExists()
        compose.onNodeWithContentDescription("Kode QR transfer akun 2 dari 2").assertExists()
        compose.onNodeWithText("Sebelumnya").performClick()
        compose.onNodeWithText("QR 1 dari 2").assertExists()
        compose.onNodeWithText("Berikutnya").performClick()
        compose.onNodeWithText("Selesai").performClick()
        assertEquals(1, actions.closed)
    }

    /**
     * Lewat layar Sandi sungguhan: di sana actions dibungkus HapticSandiActions (`by` delegasi).
     * Memastikan aksi ekspor (yang punya implementasi bawaan di interface) tetap diteruskan.
     */
    @Test fun exportActionsReachViewModelThroughSandiScreen() {
        val actions = Recorder()
        val state = SampleSandi.state().copy(pendingExport = PendingExport(ExportKind.GoogleCsv, "taskify-sandi-2026-11-19.csv", "text/csv", 4))
        compose.setContent { TaskifyTheme { SandiContent(state, actions, PaddingValues()) } }
        compose.waitForIdle()
        assertEquals(1, actions.launched)
    }

    @Test fun filePickerOpensOnlyOncePerExport() {
        val actions = Recorder()
        var pending by mutableStateOf<PendingExport?>(null)
        compose.setContent { TaskifyTheme { ExportHost(SampleSandi.state().copy(pendingExport = pending), actions) } }
        assertEquals(0, actions.launched)
        pending = PendingExport(ExportKind.GoogleCsv, "taskify-sandi-2026-11-19.csv", "text/csv", 4)
        compose.waitForIdle()
        assertEquals(1, actions.launched)
        // ViewModel menandai sudah dibuka → tidak dibuka lagi (mis. setelah rotasi layar).
        pending = pending!!.copy(launched = true)
        compose.waitForIdle()
        assertEquals(1, actions.launched)
    }
}
