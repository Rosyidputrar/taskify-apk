package com.rosyidputrar.taskify

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.sandi.ImportException
import com.rosyidputrar.taskify.feature.sandi.AuthImportContent
import com.rosyidputrar.taskify.feature.sandi.AuthImportUi
import com.rosyidputrar.taskify.feature.sandi.QrImageReader
import com.rosyidputrar.taskify.feature.sandi.SandiActions
import com.rosyidputrar.taskify.feature.sandi.SandiContent
import com.rosyidputrar.taskify.feature.sandi.SettingsBody
import com.rosyidputrar.taskify.feature.sandi.authImportLines
import com.rosyidputrar.taskify.feature.sandi.sandiFeatureModule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.koinApplication
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Impor 2FA dari Google Authenticator: tombol, cara pindai, ringkasan bertahap, sakelar pasang. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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

    /** Galat pemindai / pembacaan gambar tampil di dalam lembar impor (tidak tersembunyi di balik sheet). */
    @Test fun sheetDisplaysScannerOrImageErrorInline() {
        val msg = "Pemindai QR Google tidak bisa dibuka (Play Services)."
        compose.setContent {
            TaskifyTheme { AuthImportContent(AuthImportUi(), SampleSandi.state().copy(error = msg), Recorder(), {}, {}, {}) }
        }
        compose.onNodeWithText(msg).performScrollTo().assertExists()
    }

    /**
     * Regresi: `ContentResolverFiles.readQr` dulu merangkai `?: throw IOException("gambar tidak bisa dibuka")`
     * pada `BitmapFactory.decodeStream(..., inJustDecodeBounds = true)` yang SELALU mengembalikan null.
     */
    @Test fun qrImageReaderDecodesPngViaContentResolverAndRejectsNonImage() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val koin = koinApplication {
            androidContext(ctx)
            modules(sandiFeatureModule)
        }.koin
        val reader = koin.get<QrImageReader>()

        val qrFile = File("screenshots/sandi_export_qr_dark.png").takeIf { it.exists() }
            ?: File("app/screenshots/sandi_export_qr_dark.png")
        assertTrue(qrFile.exists(), "golden QR screenshot tidak ditemukan: ${qrFile.absolutePath}")
        val decoded = reader.readQr(Uri.fromFile(qrFile).toString())
        assertEquals(1, decoded.size)
        assertTrue(decoded.single().startsWith("otpauth-migration://"), "hasil QR: $decoded")

        val bad = File(ctx.cacheDir, "bukan_gambar.txt").apply { writeText("halo bukan gambar") }
        try {
            val err = assertFailsWith<ImportException> { reader.readQr(Uri.fromFile(bad).toString()) }
            assertEquals("Berkas ini bukan gambar.", err.message)
        } finally {
            bad.delete()
        }
    }
}
