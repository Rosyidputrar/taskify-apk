package com.rosyidputrar.taskify

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyModule
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.vault.CodeProject
import com.rosyidputrar.taskify.core.domain.vault.Snippet
import com.rosyidputrar.taskify.core.domain.vault.SnippetFormat
import com.rosyidputrar.taskify.core.ui.share.ShareOptions
import com.rosyidputrar.taskify.feature.vault.SnippetDraft
import com.rosyidputrar.taskify.feature.vault.SnippetEditorActions
import com.rosyidputrar.taskify.feature.vault.SnippetEditorContent
import com.rosyidputrar.taskify.feature.vault.VaultActions
import com.rosyidputrar.taskify.feature.vault.VaultContent
import com.rosyidputrar.taskify.feature.vault.VaultTab
import com.rosyidputrar.taskify.feature.vault.SnippetFilter
import com.rosyidputrar.taskify.feature.vault.snippetShareSections
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Snippet: buka di mode Lihat, bagikan sebagai berkas kode/Markdown/gambar, salin, peringatan rahasia, geser hapus. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class SnippetShareTest {
    @get:Rule val compose = createComposeRule()
    private val clip get() = ApplicationProvider.getApplicationContext<Context>().getSystemService(ClipboardManager::class.java)

    object NoActions : SnippetEditorActions {
        override fun edit(block: (SnippetDraft) -> SnippetDraft) = Unit
        override fun back() = Unit
        override fun save() = Unit
        override fun remove() = Unit
    }

    private class Recorder : SnippetEditorActions, VaultActions {
        val calls = mutableListOf<String>()
        override fun edit(block: (SnippetDraft) -> SnippetDraft) { calls += "edit" }
        override fun back() = Unit
        override fun save() { calls += "save" }
        override fun remove() { calls += "remove" }
        override fun toggleFavorite() { calls += "fav" }
        override fun share(format: SnippetFormat?) { calls += "share:${format ?: "text"}" }
        override fun copyImage() { calls += "copyImage" }
        override fun setTab(tab: VaultTab) = Unit
        override fun setFilter(filter: SnippetFilter) = Unit
        override fun setProjectSearch(text: String) = Unit
        override fun toggleFavorite(snippet: Snippet) = Unit
        override fun toggleFavorite(project: CodeProject) = Unit
        override fun copied(snippet: Snippet) = Unit
        override fun setConfigSearch(text: String) = Unit
        override fun setConfigEnv(env: String?) = Unit
        override fun createProject(name: String, description: String?, repoUrl: String?, onCreated: (String) -> Unit) = Unit
        override fun removeSnippet(snippet: Snippet) { calls += "remove:${snippet.id}" }
    }

    private val s = SampleVault.snippets[1]
    private val draft = SnippetDraft(true, s.id, s.title, s.language, "Hook React untuk menunda nilai", s.code, favorite = true)
    private val secret = SnippetDraft(true, "s9", "Koneksi", "bash", "", "export API_KEY=sk-123\ncurl -H \"Authorization: \$API_KEY\" x")

    private fun editor(d: SnippetDraft, actions: SnippetEditorActions, preview: Boolean? = null) =
        compose.setContent { TaskifyTheme { Surface(color = MaterialTheme.colorScheme.background) { SnippetEditorContent(d, actions, initialPreview = preview) } } }

    @Test fun existingSnippetOpensInViewMode() {
        val r = Recorder()
        editor(draft, r)
        compose.onNodeWithText("Snippet").assertExists()
        compose.onNodeWithText("8 baris · ${s.code.length} karakter").assertExists()
        compose.onNodeWithText("Simpan perubahan").assertDoesNotExist() // tidak ada perubahan → tanpa bilah simpan
        compose.onNodeWithText("Ubah", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Ubah snippet").assertExists()
        compose.onNodeWithText("Simpan perubahan").assertExists()
    }

    @Test fun newSnippetOpensInWriteMode() {
        editor(SnippetDraft(loaded = true), Recorder())
        compose.onNodeWithText("Snippet baru").assertExists()
        compose.onNodeWithContentDescription("Kode").assertExists()
    }

    @Test fun copyCodeMarkdownAndImage() {
        val r = Recorder()
        editor(draft, r)
        compose.onNodeWithContentDescription("Salin kode").performClick()
        assertEquals(s.code, clip.primaryClip!!.getItemAt(0).text.toString())
        compose.onNodeWithContentDescription("Lainnya").performClick()
        compose.onNodeWithText("Salin sebagai Markdown").performClick()
        assertTrue(clip.primaryClip!!.getItemAt(0).text.toString().startsWith("# Debounce hook\n\nHook React untuk menunda nilai\n\n```typescript\nexport function"))
        compose.onNodeWithContentDescription("Lainnya").performClick()
        compose.onNodeWithText("Salin sebagai gambar").performClick()
        compose.onNodeWithContentDescription("Lainnya").performClick()
        compose.onNodeWithText("Hapus dari favorit").performClick()
        assertEquals(listOf("copyImage", "fav"), r.calls)
    }

    @Test fun shareSheetUsesLanguageExtension() {
        val r = Recorder()
        editor(draft, r)
        compose.onNodeWithContentDescription("Bagikan").performClick()
        compose.onNodeWithText("Markdown (.md)").assertExists()
        compose.onNodeWithText("Gambar kode (.png)").assertExists()
        compose.onNodeWithText("Berkas kode (.ts)").performClick()
        assertEquals(listOf("share:Source"), r.calls)
    }

    @Test fun secretLookingCodeAsksBeforeSharing() {
        val r = Recorder()
        editor(secret, r)
        compose.onNodeWithContentDescription("Bagikan").performClick()
        compose.onNodeWithText("Gambar kode (.png)").performClick()
        compose.onNodeWithText("Kode tampak berisi rahasia").assertExists()
        compose.onNodeWithText("Batal").performClick()
        assertTrue(r.calls.isEmpty())
        compose.onNodeWithContentDescription("Bagikan").performClick()
        compose.onNodeWithText("Kirim sebagai teks").performClick()
        compose.onNodeWithText("Tetap bagikan").performClick()
        assertEquals(listOf("share:text"), r.calls)
    }

    @Test fun copyCodeAndMarkdownAlsoAskWhenSecret() {
        val r = Recorder()
        editor(secret, r)
        // Salin kode (bilah atas) juga lewat konfirmasi rahasia, bukan langsung tersalin.
        compose.onNodeWithContentDescription("Salin kode").performClick()
        compose.onNodeWithText("Kode tampak berisi rahasia").assertExists()
        compose.onNodeWithText("Tetap bagikan").performClick()
        assertEquals(secret.code, clip.primaryClip!!.getItemAt(0).text.toString())
        // Salin sebagai Markdown juga lewat konfirmasi yang sama; Batal = tidak tersalin.
        compose.onNodeWithContentDescription("Lainnya").performClick()
        compose.onNodeWithText("Salin sebagai Markdown").performClick()
        compose.onNodeWithText("Kode tampak berisi rahasia").assertExists()
        compose.onNodeWithText("Batal").performClick()
        assertEquals(secret.code, clip.primaryClip!!.getItemAt(0).text.toString())
    }

    @Test fun missingSnippetShowsNotFound() {
        editor(SnippetDraft(loaded = true, missing = true), Recorder())
        compose.onNodeWithText("Snippet tidak ditemukan").assertExists()
        compose.onNodeWithText("Simpan snippet").assertDoesNotExist()
        compose.onNodeWithText("Simpan perubahan").assertDoesNotExist()
    }

    @Test fun templateSnippetOffersFillAndCopy() {
        val t = SampleVault.snippets[0]
        editor(SnippetDraft(true, t.id, t.title, t.language, "", t.code), Recorder())
        compose.onNodeWithContentDescription("Lainnya").performClick()
        // Dialognya berisi kolom teks (tidak bisa diam di Robolectric) — cukup pastikan pilihannya ada.
        compose.onNodeWithText("Isi variabel & salin").assertExists()
    }

    @Test fun swipeLeftMovesSnippetToTrash() {
        val r = Recorder()
        compose.setContent { TaskifyTheme { Surface { VaultContent(SampleVault.state(), r, PaddingValues(), {}, {}) } } }
        compose.onNodeWithText("Hapus duplikat").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(listOf("remove:s3"), r.calls)
    }

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { TaskifyTheme(darkTheme = dark) { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test fun viewScreenshot() = shot("vault_snippet_view_light") { SnippetEditorContent(draft, NoActions) }

    @Test fun viewDarkScreenshot() = shot("vault_snippet_view_dark", dark = true) { SnippetEditorContent(draft, NoActions) }

    @Test fun shareSheetScreenshot() = shot("vault_snippet_share_light") { ShareOptions("Bagikan snippet", TaskifyModule.Vault, snippetShareSections("typescript"), onPick = {}) }
}
