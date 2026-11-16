package com.rosyidputrar.taskify

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.files.Attachment
import com.rosyidputrar.taskify.core.domain.files.AttachmentStatus
import com.rosyidputrar.taskify.core.domain.files.ZipEntryInfo
import com.rosyidputrar.taskify.core.domain.files.buildTree
import com.rosyidputrar.taskify.core.domain.files.summarizeZip
import com.rosyidputrar.taskify.core.domain.files.unwrapSingleRoot
import com.rosyidputrar.taskify.core.domain.vault.CodeProject
import com.rosyidputrar.taskify.core.domain.vault.Snippet
import com.rosyidputrar.taskify.core.files.UploadProgress
import com.rosyidputrar.taskify.core.store.files.StorageUsage
import com.rosyidputrar.taskify.core.store.vault.LanguageCount
import com.rosyidputrar.taskify.feature.attachments.AttachmentActions
import com.rosyidputrar.taskify.feature.attachments.AttachmentsContent
import com.rosyidputrar.taskify.feature.vault.EntryPreview
import com.rosyidputrar.taskify.feature.vault.ProjectActions
import com.rosyidputrar.taskify.feature.vault.ProjectContent
import com.rosyidputrar.taskify.feature.vault.ProjectUiState
import com.rosyidputrar.taskify.feature.vault.SnippetDraft
import com.rosyidputrar.taskify.feature.vault.SnippetEditorContent
import com.rosyidputrar.taskify.feature.vault.SnippetFilter
import com.rosyidputrar.taskify.feature.vault.VaultActions
import com.rosyidputrar.taskify.feature.vault.VaultContent
import com.rosyidputrar.taskify.feature.vault.VaultTab
import com.rosyidputrar.taskify.feature.vault.VaultUiState
import com.rosyidputrar.taskify.feature.vault.ZipViewerContent
import com.rosyidputrar.taskify.feature.vault.ZipViewerState
import com.rosyidputrar.taskify.ui.placeholder.BackButton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

object SampleVault {
    val snippets = listOf(
        Snippet("s1", "Docker run dengan port", "bash", "# jalankan container\ndocker run -d \\\n  -p {{port}}:80 \\\n  --name {{nama}} \\\n  {{image}}", "Template container cepat", true, "", ""),
        Snippet("s2", "Debounce hook", "typescript", "export function useDebounce<T>(value: T, ms = 300) {\n  const [v, setV] = useState(value);\n  useEffect(() => {\n    const t = setTimeout(() => setV(value), ms);\n    return () => clearTimeout(t);\n  }, [value, ms]);\n  return v;\n}", null, false, "", ""),
        Snippet("s3", "Hapus duplikat", "sql", "DELETE FROM tags a USING tags b\nWHERE a.id > b.id AND lower(a.name) = lower(b.name); -- jaga yang lama", "Postgres", false, "", ""),
    )
    val projects = listOf(
        CodeProject("p1", "Taskify", "Aplikasi produktivitas semua-dalam-satu", "https://github.com/seseegy/taskify-webapp", true, "", "", 3, 18_400_000, 6_300_000, "v1.3", "2026-10-06T03:00:00.000Z"),
        CodeProject("p2", "Landing page kopi", null, null, false, "", "", 1, 820_000, 820_000, "v1", "2026-09-12T03:00:00.000Z"),
        CodeProject("p3", "Bot Telegram", "Pengingat jadwal ronda", null, false, "", "", 0, 0, null, null, null),
    )

    fun state(tab: VaultTab = VaultTab.Snippets) = VaultUiState(
        tab = tab, filter = SnippetFilter(), snippets = snippets,
        languages = listOf(LanguageCount("bash", 1), LanguageCount("typescript", 1), LanguageCount("sql", 1)),
        projects = projects, usage = StorageUsage(1_620_000_000, 214), loaded = true,
    )

    private fun att(id: String, label: String?, name: String, size: Long, status: AttachmentStatus, note: String? = null, mime: String = "application/zip") =
        Attachment(id, "code_project", "p1", "u/$id/$name", name, mime, size, "sha", label, note, status, null, "2026-10-0${id.last()}T03:00:00.000Z")

    val versions = listOf(
        att("a3", "v1.3", "taskify-main.zip", 6_300_000, AttachmentStatus.Pending, "Jurnal & catatan"),
        att("a2", "v1.2", "taskify-main.zip", 6_100_000, AttachmentStatus.Uploaded, "Kalender pusat"),
        att("a1", "v1.1", "taskify-main.zip", 6_000_000, AttachmentStatus.Failed),
    )

    val receipts = listOf(
        att("r2", null, "struk-indomaret.jpg", 412_000, AttachmentStatus.Uploaded, mime = "image/jpeg"),
        att("r1", null, "invoice-oktober.pdf", 98_000, AttachmentStatus.Pending, mime = "application/pdf"),
    )

    object Actions : VaultActions, ProjectActions, AttachmentActions {
        override fun setTab(tab: VaultTab) = Unit
        override fun setFilter(filter: SnippetFilter) = Unit
        override fun setProjectSearch(text: String) = Unit
        override fun setConfigSearch(text: String) = Unit
        override fun setConfigEnv(env: String?) = Unit
        override fun toggleFavorite(snippet: Snippet) = Unit
        override fun toggleFavorite(project: CodeProject) = Unit
        override fun copied(snippet: Snippet) = Unit
        override fun createProject(name: String, description: String?, repoUrl: String?, onCreated: (String) -> Unit) = Unit
        override fun progress(att: Attachment): Flow<UploadProgress?> = flowOf(if (att.status == AttachmentStatus.Pending) UploadProgress(true, 0.42f, null) else null)
        override fun pick(uri: Uri) = Unit
        override fun discardPending() = Unit
        override fun uploadPending(label: String, note: String) = Unit
        override fun open(att: Attachment) = Unit
        override fun saveTo(att: Attachment, uri: Uri) = Unit
        override fun retry(att: Attachment) = Unit
        override fun remove(att: Attachment) = Unit
        override fun toggleFavorite() = Unit
        override fun edit(name: String, description: String?, repoUrl: String?) = Unit
        override fun removeProject(onDone: () -> Unit) = Unit
        override fun hasLocalCopy(att: Attachment) = false
        override fun add(uris: List<Uri>) = Unit
        override fun cancel(att: Attachment) = Unit
    }

    val zipEntries = listOf(
        ZipEntryInfo("taskify-main/", 0, true),
        ZipEntryInfo("taskify-main/.env", 120, false),
        ZipEntryInfo("taskify-main/README.md", 2_400, false),
        ZipEntryInfo("taskify-main/package.json", 3_100, false),
        ZipEntryInfo("taskify-main/src/main.tsx", 900, false),
        ZipEntryInfo("taskify-main/src/features/tasks/quick-parse.ts", 6_200, false),
        ZipEntryInfo("taskify-main/node_modules/react/index.js", 190_000, false),
        ZipEntryInfo("taskify-main/public/logo.png", 48_000, false),
    )
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class VaultScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val padding = PaddingValues(top = 24.dp, bottom = 24.dp)

    private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent { TaskifyTheme(darkTheme = dark) { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Config(qualifiers = "w393dp-h1400dp-xxhdpi")
    @Test fun snippets() = shot("vault_snippets_light", false) { VaultContent(SampleVault.state(), SampleVault.Actions, padding, {}, {}) }

    @Test fun projectsDark() = shot("vault_projects_dark", true) { VaultContent(SampleVault.state(VaultTab.Projects), SampleVault.Actions, padding, {}, {}) }

    @Test fun snippetEditor() = shot("vault_snippet_editor_light", false) {
        val s = SampleVault.snippets[1]
        SnippetEditorContent(SnippetDraft(true, s.id, s.title, s.language, "Hook React untuk menunda nilai", s.code), {}, {}, {}, {}, initialPreview = true)
    }

    @Test fun project() = shot("vault_project_light", false) {
        ProjectContent(ProjectUiState(true, SampleVault.projects[0], SampleVault.versions), null, false, SampleVault.Actions, padding, {}, {}) { BackButton(it) }
    }

    @Test fun zipFolderDark() = shot("vault_zip_dark", true) {
        val root = buildTree(SampleVault.zipEntries).unwrapSingleRoot()
        ZipViewerContent(ZipViewerState(SampleVault.versions[1], false, null, summarizeZip(SampleVault.zipEntries), root), {}, {}, { _, _ -> })
    }

    @Test fun zipFile() = shot("vault_zip_file_light", false) {
        val code = "export function parseQuickAdd(input: string, today: LocalDate) {\n  // \"besok jam 9 rapat #kerja\"\n  let text = ` \${input.trim()} `;\n  const tags: string[] = [];\n  return { title: text.trim(), tags, priority: 2 };\n}"
        ZipViewerContent(ZipViewerState(SampleVault.versions[1], false, null, summarizeZip(SampleVault.zipEntries), null, preview = EntryPreview.Text("taskify-main/src/features/tasks/quick-parse.ts", code, false)), {}, {}, { _, _ -> })
    }

    @Test fun attachments() = shot("attachments_light", false) {
        Box(Modifier.padding(20.dp)) { AttachmentsContent(SampleVault.receipts, null, SampleVault.Actions, label = "Struk & lampiran") }
    }
}

