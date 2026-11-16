package com.rosyidputrar.taskify

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.ui.capture.CaptureForm
import com.rosyidputrar.taskify.ui.capture.CaptureKind
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/** Menempel teks panjang di Tangkap tidak boleh mendorong Tutup/Simpan keluar layar. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class CaptureFormTest {
    @get:Rule val compose = createComposeRule()

    private val longText = (1..40).joinToString("\n") { "$it. **Catatan rapat** poin panjang yang ditempel dari aplikasi lain untuk diuji." }

    @Test fun actionsStayVisibleWithVeryLongText() {
        compose.setContent {
            TaskifyTheme(darkTheme = true) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    // Ruang sheet saat keyboard terbuka (~setengah layar).
                    Box(Modifier.fillMaxWidth().height(520.dp)) {
                        CaptureForm(
                            text = longText, kind = CaptureKind.Auto, error = null, restored = false, title = "Tulis apa saja",
                            onText = {}, onKind = {}, onDiscardDraft = {}, onDismiss = {}, onSave = {},
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("Simpan").assertIsDisplayed()
        compose.onNodeWithText("Tutup").assertIsDisplayed()
        val save = compose.onNodeWithText("Simpan").getBoundsInRoot()
        assertTrue(save.bottom.value <= 520f, "tombol Simpan di dalam area sheet (bottom=${save.bottom})")
        compose.onRoot().captureRoboImage("screenshots/capture_long_text_dark.png")
    }

    /** Konflik draf: sheet dibuka dengan teks baru sementara draf lama tersimpan. */
    @Test fun draftConflictShowsRestoreOptions() {
        var restored = false
        var discarded = false
        compose.setContent {
            TaskifyTheme(darkTheme = true) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    CaptureForm(
                        text = "teks baru dari bagikan", kind = CaptureKind.Auto, error = null, restored = false, title = "Tulis apa saja",
                        onText = {}, onKind = {}, onDiscardDraft = { discarded = true }, onDismiss = {}, onSave = {},
                        draftConflict = true, onRestoreDraft = { restored = true },
                    )
                }
            }
        }
        compose.onNodeWithText("Ada draf sebelumnya yang belum disimpan.").assertIsDisplayed()
        compose.onNodeWithText("Lihat draf").performClick()
        assertTrue(restored, "Lihat draf memanggil onRestoreDraft")
        compose.onNodeWithText("Buang draf").performClick()
        assertTrue(discarded, "Buang draf memanggil onDiscardDraft")
    }
}
