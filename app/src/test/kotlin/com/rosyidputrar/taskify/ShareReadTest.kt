package com.rosyidputrar.taskify

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.rosyidputrar.taskify.SampleData.TODAY
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.editor.SaveState
import com.rosyidputrar.taskify.core.domain.journal.JournalEntry
import com.rosyidputrar.taskify.core.domain.notes.Note
import com.rosyidputrar.taskify.core.domain.notes.NoteScope
import com.rosyidputrar.taskify.core.domain.text.ShareFormat
import com.rosyidputrar.taskify.core.store.tasks.TagRef
import com.rosyidputrar.taskify.core.ui.share.CodeImage
import com.rosyidputrar.taskify.core.ui.share.DocRender
import com.rosyidputrar.taskify.core.ui.share.ShareDoc
import com.rosyidputrar.taskify.core.ui.share.ShareOptions
import com.rosyidputrar.taskify.core.ui.share.docShareSections
import com.rosyidputrar.taskify.feature.journal.JournalActions
import com.rosyidputrar.taskify.feature.journal.JournalContent
import com.rosyidputrar.taskify.feature.journal.JournalDraftUi
import com.rosyidputrar.taskify.feature.journal.JournalEditorActions
import com.rosyidputrar.taskify.feature.journal.JournalEditorContent
import com.rosyidputrar.taskify.feature.journal.JournalTab
import com.rosyidputrar.taskify.feature.journal.toShare
import com.rosyidputrar.taskify.feature.notes.NoteDraftUi
import com.rosyidputrar.taskify.feature.notes.NoteEditorActions
import com.rosyidputrar.taskify.feature.notes.NoteEditorContent
import com.rosyidputrar.taskify.feature.notes.NotesActions
import com.rosyidputrar.taskify.feature.notes.NotesContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Catatan & Jurnal: buka di mode Baca, bagikan (teks/berkas/gambar), salin, geser kiri untuk hapus. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class ShareReadTest {
    @get:Rule val compose = createComposeRule()
    private val clip get() = ApplicationProvider.getApplicationContext<Context>().getSystemService(ClipboardManager::class.java)

    /** Editor & daftar catatan. */
    private class Recorder : NoteEditorActions, NotesActions {
        val calls = mutableListOf<String>()
        override fun back() = Unit
        override fun retry() = Unit
        override fun title(value: String) = Unit
        override fun body(value: TextFieldValue) = Unit
        override fun date(value: String?) = Unit
        override fun togglePin() { calls += "pin" }
        override fun remove() { calls += "remove" }
        override fun restore() = Unit
        override fun addTag(name: String) = Unit
        override fun removeTag(tag: TagRef) = Unit
        override fun share(format: ShareFormat?) { calls += "share:${format ?: "text"}" }
        override fun copyImage() { calls += "copyImage" }
        override fun setScope(scope: NoteScope) = Unit
        override fun setSearch(text: String) = Unit
        override fun toggleTrash() = Unit
        override fun togglePin(note: Note) = Unit
        override fun restore(note: Note) = Unit
        override fun remove(note: Note) { calls += "remove:${note.id}" }
    }

    /** Editor & daftar jurnal. */
    private class JRecorder : JournalEditorActions, JournalActions {
        val calls = mutableListOf<String>()
        override fun back() = Unit
        override fun retry() = Unit
        override fun title(value: String) = Unit
        override fun body(value: TextFieldValue) = Unit
        override fun mood(value: Int?) = Unit
        override fun date(value: String) = Unit
        override fun togglePin() { calls += "pin" }
        override fun remove() { calls += "remove" }
        override fun restore() = Unit
        override fun addTag(name: String) = Unit
        override fun removeTag(tag: TagRef) = Unit
        override fun share(format: ShareFormat?) { calls += "share:${format ?: "text"}" }
        override fun copyImage() { calls += "copyImage" }
        override fun setTab(tab: JournalTab) = Unit
        override fun setSearch(text: String) = Unit
        override fun setMood(mood: Int?) = Unit
        override fun toggleTrash() = Unit
        override fun setRange(days: Int) = Unit
        override fun restore(entry: JournalEntry) = Unit
        override fun remove(entry: JournalEntry) { calls += "remove:${entry.id}" }
    }

    private val note = NoteDraftUi(loaded = true, id = "n1", date = TODAY, title = "Notulen rapat", body = TextFieldValue(SampleJournal.BODY))
    private val entry = JournalDraftUi(loaded = true, id = "j1", date = TODAY, title = "Pagi yang tenang", body = TextFieldValue(SampleJournal.BODY), mood = 4, prompt = "")

    private fun themed(content: @Composable () -> Unit) = compose.setContent { TaskifyTheme { Surface(color = MaterialTheme.colorScheme.background) { content() } } }

    // ------------------------------------------------------------------ Catatan

    @Test fun noteOpensInReadModeAndUbahSwitchesToWrite() {
        themed { NoteEditorContent(note, SaveState.Saved, emptyList(), emptyList(), Recorder(), today = TODAY) }
        compose.onNodeWithText("Baca").assertIsSelected()
        compose.onNodeWithText("Rencana hari ini").assertExists()
        compose.onNodeWithText("Ubah", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Tulis").assertIsSelected()
    }

    @Test fun readModeShowsTagsAsLabelsAndHidesEmptyTagSection() {
        // Tanpa tag: judul "Tag" tidak tampil sendirian di mode Baca.
        themed { NoteEditorContent(note, SaveState.Saved, emptyList(), emptyList(), Recorder(), today = TODAY) }
        compose.onNodeWithText("Tag").assertDoesNotExist()
        compose.onNodeWithText("Ubah", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Tag").assertExists() // mode Tulis: bagian tag tetap ada untuk menambah
    }

    @Test fun readModeTagLabelsAreNotDisabledChips() {
        val tags = listOf(TagRef("t1", "kerja", "blue"))
        themed { NoteEditorContent(note, SaveState.Saved, tags, tags, Recorder(), today = TODAY) }
        compose.onNodeWithText("Tag").assertExists()
        compose.onNodeWithText("#kerja").assertExists().assertHasNoClickAction()
        compose.onNodeWithContentDescription("Lepas tag kerja").assertDoesNotExist()
    }

    @Test fun newOrEmptyNoteOpensInWriteMode() {
        themed { NoteEditorContent(NoteDraftUi(loaded = true, id = "n2", title = "Ide"), SaveState.Saved, emptyList(), emptyList(), Recorder(), today = TODAY) }
        compose.onNodeWithText("Tulis").assertIsSelected()
        compose.onNodeWithText("Baca").assertIsNotSelected()
    }

    @Test fun noteMenuCopiesTextMarkdownAndImage() {
        val r = Recorder()
        themed { NoteEditorContent(note, SaveState.Saved, emptyList(), emptyList(), r, today = TODAY) }
        compose.onNodeWithContentDescription("Lainnya").performClick()
        compose.onNodeWithText("Salin teks").performClick()
        val text = clip.primaryClip!!.getItemAt(0).text.toString()
        assertTrue(text.startsWith("Notulen rapat\nDitempel ke "), text)
        assertTrue(text.contains("☑ Rapat sprint") && !text.contains("**"), text)

        compose.onNodeWithContentDescription("Lainnya").performClick()
        compose.onNodeWithText("Salin sebagai Markdown").performClick()
        assertTrue(clip.primaryClip!!.getItemAt(0).text.toString().startsWith("# Notulen rapat\n\n_Ditempel ke "))

        compose.onNodeWithContentDescription("Lainnya").performClick()
        compose.onNodeWithText("Salin sebagai gambar").performClick()
        compose.onNodeWithContentDescription("Lainnya").performClick()
        compose.onNodeWithText("Sematkan").performClick()
        assertEquals(listOf("copyImage", "pin"), r.calls)
    }

    @Test fun noteShareSheetHasAllFormats() {
        val r = Recorder()
        themed { NoteEditorContent(note, SaveState.Saved, emptyList(), emptyList(), r, today = TODAY) }
        compose.onNodeWithContentDescription("Bagikan").performClick()
        listOf("Kirim sebagai teks", "Markdown (.md)", "Teks biasa (.txt)", "Halaman web (.html)", "PDF (.pdf)").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("Gambar (.png)").performClick()
        assertEquals(listOf("share:Image"), r.calls)
    }

    @Test fun swipeLeftMovesNoteToTrashButNotInTrashView() {
        val r = Recorder()
        themed { NotesContent(SampleJournal.notesState(), r, PaddingValues(), {}, {}) }
        compose.onNodeWithText("Resep sambal matah").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(listOf("remove:n4"), r.calls)
        // Geser ke kanan tidak menghapus.
        compose.onNodeWithText("Ide fitur").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(listOf("remove:n4"), r.calls)
    }

    @Test fun swipeIsOffInTrash() {
        val r = Recorder()
        val trash = SampleJournal.notesState().let { it.copy(filter = it.filter.copy(trash = true)) }
        themed { NotesContent(trash, r, PaddingValues(), {}, {}) }
        compose.onNodeWithText("Resep sambal matah").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertTrue(r.calls.isEmpty())
    }

    // ------------------------------------------------------------------ Jurnal

    @Test fun journalOpensInReadModeWithShareAndCopy() {
        val r = JRecorder()
        themed { JournalEditorContent(entry, SaveState.Saved, emptyList(), emptyList(), r, today = TODAY) }
        compose.onNodeWithText("Baca").assertIsSelected()
        compose.onNodeWithContentDescription("Bagikan").performClick()
        compose.onNodeWithText("PDF (.pdf)").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Lainnya").performClick()
        compose.onNodeWithText("Salin teks").performClick()
        val text = clip.primaryClip!!.getItemAt(0).text.toString()
        assertTrue(text.startsWith("Pagi yang tenang\n") && text.contains("Mood Baik"), text)
        compose.onNodeWithContentDescription("Lainnya").performClick()
        compose.onNodeWithText("Pindahkan ke Sampah").performClick()
        assertEquals(listOf("share:Pdf", "remove"), r.calls)
        compose.onNodeWithText("Ubah", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Tulis").assertIsSelected()
    }

    @Test fun newJournalEntryOpensInWriteMode() {
        themed { JournalEditorContent(JournalDraftUi(loaded = true, date = TODAY, prompt = "Apa kabar?"), SaveState.Idle, emptyList(), emptyList(), JRecorder(), today = TODAY) }
        compose.onNodeWithText("Tulis").assertIsSelected()
    }

    @Test fun journalShareDocCarriesDateAndMood() {
        val d = entry.toShare()
        assertEquals("Pagi yang tenang", d.title)
        assertTrue(d.meta!!.contains("2026") && d.meta!!.endsWith("Mood Baik"), d.meta)
        assertEquals("Jurnal", d.label)
        assertEquals(TaskifyModule.Journal.light.toArgb(), d.accent)
    }

    @Test fun swipeLeftMovesJournalEntryToTrash() {
        val r = JRecorder()
        val state = SampleJournal.state()
        val target = state.groups.first().entries.first()
        themed { JournalContent(state, r, PaddingValues(), {}, {}) }
        compose.onNodeWithText(target.title!!).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(listOf("remove:${target.id}"), r.calls)
    }

    // ------------------------------------------------------------------ render

    private val long = ShareDoc(
        "Notulen panjang", "Ditempel ke Jumat, 20 November 2026",
        SampleJournal.BODY + "\n\n```kotlin\nfun total(xs: List<Long>) = xs.sum()\n```\n\n| Nama | Nominal |\n|---|---|\n| Listrik | 200rb |\n\n---\n\n" +
            (1..40).joinToString("\n\n") { "## Bagian $it\nIsi paragraf ke-$it dengan **tebal**, _miring_, `kode`, dan [tautan](https://taskify.id).\n\n- [x] selesai\n- [ ] belum\n  - anak" },
        "Catatan", TaskifyModule.Notes.light.toArgb(),
    )

    @Test fun pdfPaginatesAndEveryPageHasInk() {
        // PdfDocument tidak berjalan di Robolectric → halaman digambar ke Bitmap dengan kode yang sama.
        val pages = mutableListOf<Bitmap>()
        val count = DocRender.renderPages(long, object : DocRender.Pages {
            override fun start(number: Int) = Canvas(Bitmap.createBitmap(DocRender.PAGE_W, DocRender.PAGE_H, Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.WHITE); pages += it })
            override fun finish(number: Int) = Unit
        })
        assertEquals(pages.size, count)
        assertTrue(count in 4..20, "halaman: $count")
        pages.forEach { assertTrue(hasInk(it, Color.WHITE)) }
        preview("pdf-page1", pages[0])
    }

    @Test fun emptyDocStillMakesOnePage() {
        assertEquals(1, DocRender.renderPages(ShareDoc(null, null, "", "Catatan"), object : DocRender.Pages {
            override fun start(number: Int) = Canvas(Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888))
            override fun finish(number: Int) = Unit
        }))
    }

    @Test fun shortDocImageFitsContent() {
        val img = DocRender.image(ShareDoc("Belanja", "Jumat", "- [ ] telur\n- [x] susu", "Catatan", TaskifyModule.Notes.light.toArgb()))
        assertEquals(1080, img.bitmap.width)
        assertTrue(img.bitmap.height in 300..900, "tinggi ${img.bitmap.height}")
        assertTrue(!img.truncated)
        preview("image-short", img.bitmap)
    }

    @Test fun longDocImageIsTruncatedAtMaxHeight() {
        val img = DocRender.image(long.copy(markdown = long.markdown + "\n\n" + long.markdown))
        assertTrue(img.truncated)
        val maxPx = ((DocRender.IMAGE_MAX_PT + 120) * 1080 / DocRender.PAGE_W).toInt()
        assertTrue(img.bitmap.height in 3000..maxPx, "tinggi ${img.bitmap.height}")
        preview("image-long", img.bitmap)
    }

    @Test fun tableRendersAsGridWithWrappedCells() {
        val table = ShareDoc(
            "Biaya", "Jumat",
            "| Barang | Harga |\n|---|---|\n| Beras 10 kg | Rp 120.000 |\n| Minyak goreng 2 liter dengan nama yang sangat panjang sekali | Rp 85.000 |\n| Gula | Rp 17.500 |",
            "Catatan", TaskifyModule.Notes.light.toArgb(),
        )
        val img = DocRender.image(table)
        assertTrue(!img.truncated)
        // Sel panjang membungkus di dalam kolomnya (bukan satu baris "│" yang melebar): gambar
        // tabel 3 baris + judul lebih tinggi dari sekadar 3 baris teks.
        assertTrue(img.bitmap.height > 420, "tinggi ${img.bitmap.height}")
        preview("table-grid", img.bitmap)
        // PDF: tabel pendek tetap satu halaman dan ada tinta (grid + teks).
        val pages = mutableListOf<Bitmap>()
        val count = DocRender.renderPages(table, object : DocRender.Pages {
            override fun start(number: Int) = Canvas(Bitmap.createBitmap(DocRender.PAGE_W, DocRender.PAGE_H, Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.WHITE); pages += it })
            override fun finish(number: Int) = Unit
        })
        assertEquals(1, count)
        assertTrue(hasInk(pages[0], Color.WHITE))
    }

    @Test fun hugeCodeImageIsDownscaledUnderPixelCap() {
        val huge = (1..400).joinToString("\n") { "val x$it = \"${"a".repeat(it % 160)}\"" }
        val big = CodeImage.render(huge, "kotlin", "Besar", "Kotlin")
        assertTrue(big.truncated, "220 baris pertama saja")
        val pixels = big.bitmap.width.toLong() * big.bitmap.height
        assertTrue(pixels <= CodeImage.MAX_PIXELS, "piksel: $pixels (${big.bitmap.width}x${big.bitmap.height})")
        assertTrue(big.bitmap.height in 1000..8000, "tinggi ${big.bitmap.height}")
    }

    @Test fun codeImageWrapsLongLinesAndTruncatesHugeCode() {
        val s = SampleVault.snippets[1]
        val img = CodeImage.render(s.code, s.language, s.title, "TypeScript")
        assertTrue(!img.truncated && img.bitmap.width in 1000..3200 && img.bitmap.height in 300..1200, "${img.bitmap.width}x${img.bitmap.height}")
        assertTrue(hasInk(img.bitmap, img.bitmap.getPixel(img.bitmap.width / 2, img.bitmap.height / 2)))
        preview("code", img.bitmap)
        val huge = (1..400).joinToString("\n") { "val x$it = \"${"a".repeat(it % 160)}\"" }
        val big = CodeImage.render(huge, "kotlin", "Besar", "Kotlin")
        assertTrue(big.truncated)
        assertTrue(big.bitmap.width <= 3200)
    }

    private fun hasInk(b: Bitmap, bg: Int) = (0 until b.height step 4).any { y -> (0 until b.width step 4).any { x -> b.getPixel(x, y) != bg } }

    /** Pratinjau untuk diperiksa manual (folder build, tidak ikut repo). */
    private fun preview(name: String, b: Bitmap) {
        File("build/share-preview").apply { mkdirs() }.let { dir -> File(dir, "$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }

    // ------------------------------------------------------------------ screenshot

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { TaskifyTheme(darkTheme = dark) { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test fun noteWriteScreenshot() = shot("note_editor_write_light") {
        NoteEditorContent(note, SaveState.Saved, emptyList(), emptyList(), Recorder(), initialPreview = false, today = TODAY)
    }

    @Test fun journalReadScreenshot() = shot("journal_editor_read_light") {
        JournalEditorContent(entry, SaveState.Saved, emptyList(), emptyList(), JRecorder(), today = TODAY)
    }

    @Test fun shareSheetScreenshot() = shot("note_share_light") { ShareOptions("Bagikan catatan", TaskifyModule.Notes, docShareSections(), onPick = {}) }

    @Test fun shareSheetDarkScreenshot() = shot("note_share_dark", dark = true) { ShareOptions("Bagikan catatan", TaskifyModule.Notes, docShareSections(), onPick = {}) }
}
