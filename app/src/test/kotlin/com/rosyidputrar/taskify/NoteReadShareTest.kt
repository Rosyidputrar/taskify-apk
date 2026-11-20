package com.rosyidputrar.taskify

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.rosyidputrar.taskify.SampleData.TODAY
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.editor.SaveState
import com.rosyidputrar.taskify.core.domain.notes.NoteFormat
import com.rosyidputrar.taskify.core.store.tasks.TagRef
import com.rosyidputrar.taskify.feature.notes.NoteDraftUi
import com.rosyidputrar.taskify.feature.notes.NoteEditorActions
import com.rosyidputrar.taskify.feature.notes.NoteEditorContent
import com.rosyidputrar.taskify.feature.notes.ShareNoteOptions
import com.rosyidputrar.taskify.feature.notes.share.NotePdf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class NoteReadShareTest {
    @get:Rule val compose = createComposeRule()

    private class Recorder : NoteEditorActions {
        val shared = mutableListOf<NoteFormat?>()
        override fun back() = Unit
        override fun retry() = Unit
        override fun title(value: String) = Unit
        override fun body(value: TextFieldValue) = Unit
        override fun date(value: String?) = Unit
        override fun togglePin() = Unit
        override fun remove() = Unit
        override fun restore() = Unit
        override fun addTag(name: String) = Unit
        override fun removeTag(tag: TagRef) = Unit
        override fun share(format: NoteFormat?) { shared += format }
    }

    private val existing = NoteDraftUi(loaded = true, id = "n1", date = TODAY, title = "Notulen rapat", body = TextFieldValue(SampleJournal.BODY))

    private fun editor(ui: NoteDraftUi, actions: Recorder = Recorder(), preview: Boolean? = null) = compose.setContent {
        TaskifyTheme { Surface(color = MaterialTheme.colorScheme.background) { NoteEditorContent(ui, SaveState.Saved, emptyList(), emptyList(), actions, initialPreview = preview, today = TODAY) } }
    }

    @Test fun existingNoteOpensInReadMode() {
        editor(existing)
        compose.onNodeWithText("Baca").assertIsSelected()
        compose.onNodeWithText("Tulis").assertIsNotSelected()
        // Isi tampil sebagai teks terformat, bukan kolom ketik.
        compose.onNodeWithText("Rencana hari ini").assertExists()
        assertTrue(compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().none { it.config.toString().contains("Isi") })
        // Tombol mengambang "Ubah" → mode Tulis.
        compose.onNodeWithText("Ubah", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Tulis").assertIsSelected()
    }

    @Test fun newOrEmptyNoteOpensInWriteMode() {
        editor(NoteDraftUi(loaded = true))
        compose.onNodeWithText("Tulis").assertIsSelected()
        compose.onNodeWithContentDescription("Bagikan").assertExists()
    }

    @Test fun emptyExistingNoteOpensInWriteMode() {
        editor(NoteDraftUi(loaded = true, id = "n2", title = "Ide"))
        compose.onNodeWithText("Tulis").assertIsSelected()
    }

    @Test fun copyTextAndMarkdown() {
        editor(existing)
        val clip = ApplicationProvider.getApplicationContext<Context>().getSystemService(ClipboardManager::class.java)
        compose.onNodeWithContentDescription("Lainnya").performClick()
        compose.onNodeWithText("Salin teks").performClick()
        val text = clip.primaryClip!!.getItemAt(0).text.toString()
        assertTrue(text.startsWith("Notulen rapat\n\nRencana hari ini\n"), text)
        assertTrue(text.contains("☑ Rapat sprint") && !text.contains("**"), text)

        compose.onNodeWithContentDescription("Lainnya").performClick()
        compose.onNodeWithText("Salin sebagai Markdown").performClick()
        val md = clip.primaryClip!!.getItemAt(0).text.toString()
        assertTrue(md.startsWith("# Notulen rapat\n\n## Rencana hari ini"), md)
    }

    @Test fun shareSheetOffersTextAndFiles() {
        val actions = Recorder()
        editor(existing, actions)
        compose.onNodeWithContentDescription("Bagikan").performClick()
        compose.onNodeWithText("Kirim sebagai teks").assertExists()
        compose.onNodeWithText("PDF (.pdf)").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Bagikan").performClick()
        compose.onNodeWithText("Kirim sebagai teks").performClick()
        assertEquals(listOf(NoteFormat.Pdf, null), actions.shared)
    }

    @Test fun pdfPaginatesAndRenders() {
        // PdfDocument tidak berjalan di Robolectric → halaman digambar ke Bitmap dengan kode yang sama.
        val long = SampleJournal.BODY + "\n\n```kotlin\nfun total(xs: List<Long>) = xs.sum()\n```\n\n| Nama | Nominal |\n|---|---|\n| Listrik | 200rb |\n\n---\n\n" +
            (1..40).joinToString("\n\n") { "## Bagian $it\nIsi paragraf ke-$it dengan **tebal**, _miring_, `kode`, dan [tautan](https://taskify.id).\n\n- [x] selesai\n- [ ] belum\n  - anak" }
        val bitmaps = mutableListOf<Bitmap>()
        val count = NotePdf.render("Notulen panjang", long, "Ditempel ke Jumat, 20 November 2026", object : NotePdf.Pages {
            override fun start(number: Int) = Canvas(Bitmap.createBitmap(NotePdf.WIDTH, NotePdf.HEIGHT, Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.WHITE); bitmaps += it })
            override fun finish(number: Int) = Unit
        })
        assertEquals(bitmaps.size, count)
        assertTrue(count in 4..20, "halaman: $count")
        // Tiap halaman benar-benar berisi tinta (bukan halaman kosong).
        bitmaps.forEach { b -> assertTrue((0 until b.height step 4).any { y -> (0 until b.width step 4).any { x -> b.getPixel(x, y) != Color.WHITE } }) }
        // Pratinjau halaman untuk diperiksa manual (di folder build, tidak ikut repo).
        File("build/pdf-preview").apply { mkdirs() }.let { dir ->
            bitmaps.take(2).forEachIndexed { i, b -> File(dir, "page${i + 1}.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        }
    }

    @Test fun emptyNoteStillMakesOnePage() {
        var started = 0
        assertEquals(1, NotePdf.render(null, "", null, object : NotePdf.Pages {
            override fun start(number: Int) = Canvas(Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)).also { started++ }
            override fun finish(number: Int) = Unit
        }))
        assertEquals(1, started)
    }

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { TaskifyTheme(darkTheme = dark) { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test fun noteWriteModeScreenshot() = shot("note_editor_write_light") {
        NoteEditorContent(existing, SaveState.Saved, emptyList(), emptyList(), Recorder(), initialPreview = false, today = TODAY)
    }

    @Test fun shareOptionsScreenshot() = shot("note_share_light") { ShareNoteOptions(onPick = {}) }

    @Test fun shareOptionsDarkScreenshot() = shot("note_share_dark", dark = true) { ShareNoteOptions(onPick = {}) }
}
