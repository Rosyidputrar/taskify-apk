package com.rosyidputrar.taskify

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.editor.SaveState
import com.rosyidputrar.taskify.core.domain.journal.JournalEntry
import com.rosyidputrar.taskify.core.domain.journal.MoodPoint
import com.rosyidputrar.taskify.core.domain.journal.moodStats
import com.rosyidputrar.taskify.core.domain.notes.Note
import com.rosyidputrar.taskify.core.domain.notes.groupLabel
import com.rosyidputrar.taskify.core.domain.notes.groupNotes
import com.rosyidputrar.taskify.core.domain.time.Dates
import com.rosyidputrar.taskify.core.store.notes.NoteCounts
import com.rosyidputrar.taskify.core.store.tasks.TagRef
import com.rosyidputrar.taskify.feature.journal.JournalActions
import com.rosyidputrar.taskify.feature.journal.JournalContent
import com.rosyidputrar.taskify.feature.journal.JournalDraftUi
import com.rosyidputrar.taskify.feature.journal.JournalEditorActions
import com.rosyidputrar.taskify.feature.journal.JournalEditorContent
import com.rosyidputrar.taskify.feature.journal.JournalInsight
import com.rosyidputrar.taskify.feature.journal.JournalTab
import com.rosyidputrar.taskify.feature.journal.JournalUiState
import com.rosyidputrar.taskify.feature.journal.groupByMonth
import com.rosyidputrar.taskify.feature.notes.NoteDraftUi
import com.rosyidputrar.taskify.feature.notes.NoteEditorActions
import com.rosyidputrar.taskify.feature.notes.NoteEditorContent
import com.rosyidputrar.taskify.feature.notes.NoteGroup
import com.rosyidputrar.taskify.feature.notes.NotesActions
import com.rosyidputrar.taskify.feature.notes.NotesContent
import com.rosyidputrar.taskify.feature.notes.NotesUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.rosyidputrar.taskify.SampleData.TODAY

/** Data contoh Jurnal & Catatan (tanggal tetap agar gambar stabil). */
object SampleJournal {
    private fun entry(id: String, date: String, title: String?, content: String, mood: Int?, pinned: Boolean = false) =
        JournalEntry(id, date, title, content, mood, pinned, "${date}T00:00:00.000Z", "")

    val entries = listOf(
        entry("j0", "2026-09-28", "Refleksi minggu 21 Sep – 27 Sep 2026", "### Yang berjalan baik\n- Rilis fitur kalender\n### Fokus minggu depan\n- [ ] Jurnal & catatan", 4, pinned = true),
        entry("j1", TODAY, "Pagi yang tenang", "Sempat jalan kaki **30 menit** sebelum kerja. Kopi di teras, lalu menulis rencana hari ini.", 4),
        entry("j2", TODAY, null, "Rapat sprint lebih panjang dari rencana, tapi keputusan soal sinkronisasi akhirnya jelas.", 3),
        entry("j3", "2026-10-06", "Hari yang ringan", "Semua tugas beres sebelum jam 5. Malamnya nonton film bareng.", 5),
        entry("j4", "2026-10-04", "Capek", "Kurang tidur. Besok harus lebih awal.", 2),
        entry("j5", "2026-09-30", null, "Akhir bulan: tagihan sudah dibayar semua, sisa anggaran makan masih aman.", 4),
    )

    fun series(days: Int): List<MoodPoint> {
        val moods = listOf(3.0, null, 4.0, 4.0, 2.0, null, 3.0, 5.0, 4.0, null, 3.0, 3.0, 4.0, 2.0, 1.0, 3.0, null, 4.0, 5.0, 4.0, 4.0, null, 3.0, 4.0, 2.0, null, 4.0, 5.0, 3.5, 4.0)
        return (0 until days).map { i ->
            val day = Dates.addDays(TODAY, -(days - 1 - i).toLong())
            val m = moods[i % moods.size]
            MoodPoint(day, m, if (m == null) 0 else 1)
        }
    }

    fun state(tab: JournalTab = JournalTab.Shelf): JournalUiState {
        val s = series(30)
        return JournalUiState(
            today = TODAY,
            tab = tab,
            pinned = entries.filter { it.pinned },
            groups = groupByMonth(entries.filterNot { it.pinned }),
            total = entries.size,
            streak = 4,
            todayCount = 2,
            insight = JournalInsight(30, s, moodStats(s), listOf(entry("old", "2025-10-07", "Hari pertama kerja", "Gugup tapi senang.", 4))),
            loaded = true,
        )
    }

    private fun note(id: String, date: String?, title: String?, content: String, pinned: Boolean = false) =
        Note(id, date, title, content, pinned, "2026-10-01T00:00:00.000Z", "")

    val notes = listOf(
        note("n1", null, "Ide fitur", "- Ekspor jurnal ke PDF\n- Widget mood di layar utama\n- Pencarian lintas modul", pinned = true),
        note("n2", TODAY, "Notulen rapat sprint", "- [x] Tetapkan cakupan Tahap 4\n- [x] Bagi tugas editor\n- [ ] Uji simpan otomatis offline\n- [ ] Tinjau desain kartu"),
        note("n3", "2026-10-06", null, "Nomor servis motor: bengkel dekat pasar, buka jam 8."),
        note("n4", "2026-10-02", "Resep sambal matah", "Bawang merah, serai, cabai rawit, daun jeruk, minyak kelapa panas, perasan jeruk limau. Aduk, jangan diulek."),
        note("n5", null, "Kutipan", "> Yang penting bukan seberapa cepat, tapi seberapa konsisten."),
        note("n6", null, null, "Daftar buku: Atomic Habits, Deep Work, Laskar Pelangi"),
    )

    fun notesState(): NotesUiState {
        val g = groupNotes(notes, trash = false)
        return NotesUiState(
            today = TODAY,
            pinned = g.pinned,
            groups = g.groups.map { (k, v) -> NoteGroup(k, groupLabel(k), v) },
            counts = NoteCounts(total = 6, dated = 3, pinned = 1),
            loaded = true,
        )
    }

    object Actions : JournalActions, NotesActions, JournalEditorActions {
        override fun setTab(tab: JournalTab) = Unit
        override fun setSearch(text: String) = Unit
        override fun setMood(mood: Int?) = Unit
        override fun toggleTrash() = Unit
        override fun setRange(days: Int) = Unit
        override fun restore(entry: JournalEntry) = Unit
        override fun setScope(scope: com.rosyidputrar.taskify.core.domain.notes.NoteScope) = Unit
        override fun togglePin(note: Note) = Unit
        override fun restore(note: Note) = Unit
        override fun back() = Unit
        override fun retry() = Unit
        override fun title(value: String) = Unit
        override fun body(value: TextFieldValue) = Unit
        override fun mood(value: Int?) = Unit
        override fun date(value: String) = Unit
        override fun togglePin() = Unit
        override fun remove() = Unit
        override fun restore() = Unit
        override fun addTag(name: String) = Unit
        override fun removeTag(tag: TagRef) = Unit
    }

    object NoteActions : NoteEditorActions {
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
    }

    const val BODY = "## Rencana hari ini\nSempat jalan kaki **30 menit** sebelum kerja, lalu _menulis_ rencana.\n\n- [x] Rapat sprint\n- [ ] Bayar listrik\n- [ ] Telepon ibu\n\n> Pelan-pelan saja, yang penting jalan.\n\nCatatan lengkap di [taskify](https://taskify.id)."
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class JournalNotesScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val padding = PaddingValues(top = 24.dp, bottom = 24.dp)

    private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent {
            TaskifyTheme(darkTheme = dark) {
                Surface(color = MaterialTheme.colorScheme.background) { content() }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test fun journalShelfLight() = shot("journal_shelf_light", false) {
        JournalContent(SampleJournal.state(), SampleJournal.Actions, padding, {}, {})
    }

    @Test fun journalShelfDark() = shot("journal_shelf_dark", true) {
        JournalContent(SampleJournal.state(), SampleJournal.Actions, padding, {}, {})
    }

    @Config(qualifiers = "w393dp-h1400dp-xxhdpi")
    @Test fun journalInsight() = shot("journal_insight_light", false) {
        JournalContent(SampleJournal.state(JournalTab.Insight), SampleJournal.Actions, padding, {}, {})
    }

    @Test fun journalInsightDark() = shot("journal_insight_dark", true) {
        JournalContent(SampleJournal.state(JournalTab.Insight), SampleJournal.Actions, padding, {}, {})
    }

    private val draft = JournalDraftUi(
        loaded = true, id = "j1", date = TODAY, title = "Pagi yang tenang",
        body = TextFieldValue(SampleJournal.BODY, TextRange(0)), mood = 4, prompt = "Apa yang paling kamu syukuri hari ini?",
    )

    @Test fun journalEditor() = shot("journal_editor_light", false) {
        JournalEditorContent(draft, SaveState.Saved, listOf(TagRef("t1", "refleksi", "violet")), emptyList(), SampleJournal.Actions, today = TODAY)
    }

    @Test fun journalEditorPreviewDark() = shot("journal_editor_preview_dark", true) {
        JournalEditorContent(draft, SaveState.Pending, emptyList(), emptyList(), SampleJournal.Actions, initialPreview = true, today = TODAY)
    }

    @Test fun journalEditorEmpty() = shot("journal_editor_new_light", false) {
        JournalEditorContent(JournalDraftUi(loaded = true, date = TODAY, prompt = "Apa pelajaran dari hari ini?"), SaveState.Idle, emptyList(), emptyList(), SampleJournal.Actions, today = TODAY)
    }

    @Test fun notesLight() = shot("notes_light", false) {
        NotesContent(SampleJournal.notesState(), SampleJournal.Actions, padding, {}, {})
    }

    @Test fun notesDark() = shot("notes_dark", true) {
        NotesContent(SampleJournal.notesState(), SampleJournal.Actions, padding, {}, {})
    }

    @Test fun noteEditor() = shot("note_editor_light", false) {
        NoteEditorContent(
            NoteDraftUi(loaded = true, id = "n2", date = TODAY, title = "Notulen rapat sprint", body = TextFieldValue(SampleJournal.notes[1].content)),
            SaveState.Saved, emptyList(), emptyList(), SampleJournal.NoteActions, today = TODAY,
        )
    }

    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    @Test fun notesTablet() = shot("notes_tablet_light", false) {
        NotesContent(SampleJournal.notesState(), SampleJournal.Actions, padding, {}, {})
    }
}
