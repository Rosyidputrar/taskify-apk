package com.rosyidputrar.taskify

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.designsystem.component.DiscardChangesDialog
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.files.Attachment
import com.rosyidputrar.taskify.core.domain.files.AttachmentStatus
import com.rosyidputrar.taskify.core.files.UploadProgress
import com.rosyidputrar.taskify.feature.attachments.AttachmentActions
import com.rosyidputrar.taskify.feature.attachments.AttachmentsContent
import com.rosyidputrar.taskify.ui.capture.CaptureDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Ketikan & berkas pengguna tidak hilang karena satu sentuhan yang tidak disengaja. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class DataSafetyTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun captureDraftSurvivesUntilSavedOrCleared() {
        val draft = CaptureDraft(context)
        draft.text = "besok jam 9 rapat #kerja"
        assertEquals("besok jam 9 rapat #kerja", CaptureDraft(context).text, "terbaca lagi setelah sheet/proses dibuat ulang")
        draft.text = "   "
        assertEquals("", draft.text, "draf kosong tidak disimpan")
        draft.text = "catatan"
        draft.clear()
        assertEquals("", CaptureDraft(context).text)
    }

    private class Removals : AttachmentActions {
        val removed = mutableListOf<String>()
        override fun progress(att: Attachment): Flow<UploadProgress?> = flowOf(null)
        override fun hasLocalCopy(att: Attachment) = false
        override fun add(uris: List<Uri>) = Unit
        override fun open(att: Attachment) = Unit
        override fun saveTo(att: Attachment, target: Uri) = Unit
        override fun retry(att: Attachment) = Unit
        override fun cancel(att: Attachment) = Unit
        override fun remove(att: Attachment) { removed += att.id }
    }

    @Test fun deletingAnAttachmentAsksFirst() {
        val actions = Removals()
        val att = Attachment("a1", "transaction", "t1", "u/a1/struk.jpg", "struk.jpg", "image/jpeg", 120_000, "sha", null, null, AttachmentStatus.Uploaded, null, "2026-11-16T03:00:00.000Z")
        compose.setContent { TaskifyTheme { AttachmentsContent(listOf(att), null, actions) } }
        compose.onNodeWithContentDescription("Aksi struk.jpg").performClick()
        compose.onNodeWithText("Hapus").performClick()
        assertTrue(actions.removed.isEmpty(), "satu ketukan tidak langsung menghapus")
        compose.onNodeWithText("Hapus lampiran?").assertExists()
        compose.onNodeWithText("Batal").performClick()
        assertTrue(actions.removed.isEmpty())

        compose.onNodeWithContentDescription("Aksi struk.jpg").performClick()
        compose.onNodeWithText("Hapus").performClick()
        compose.onNodeWithText("Hapus permanen").performClick()
        assertEquals(listOf("a1"), actions.removed)
    }

    @Test fun discardDialogOffersSaveDiscardCancel() {
        var outcome = ""
        compose.setContent {
            TaskifyTheme { DiscardChangesDialog("transaksi ini", onSave = { outcome = "save" }, onDiscard = { outcome = "discard" }, onDismiss = { outcome = "cancel" }) }
        }
        compose.onNodeWithText("Perubahan pada transaksi ini belum disimpan", substring = true).assertExists()
        compose.onAllNodesWithText("Buang")[0].performClick()
        assertEquals("discard", outcome)
    }
}
