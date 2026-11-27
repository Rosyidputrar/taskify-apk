package com.rosyidputrar.taskify

import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.files.ExpiryOption
import com.rosyidputrar.taskify.core.domain.files.TempFile
import com.rosyidputrar.taskify.core.domain.files.TempFileStatus
import com.rosyidputrar.taskify.core.files.PublicFileInfo
import com.rosyidputrar.taskify.core.files.UploadJob
import com.rosyidputrar.taskify.core.files.UploadPhase
import com.rosyidputrar.taskify.feature.files.FileDetail
import com.rosyidputrar.taskify.feature.files.FilesActions
import com.rosyidputrar.taskify.feature.files.FilesContent
import com.rosyidputrar.taskify.feature.files.FilesUiState
import com.rosyidputrar.taskify.feature.files.PublicDownload
import com.rosyidputrar.taskify.feature.files.PublicFileActions
import com.rosyidputrar.taskify.feature.files.PublicFileContent
import com.rosyidputrar.taskify.feature.files.PublicFileState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

object SampleFiles {
    val NOW = Instant.parse("2026-11-16T03:00:00Z").toEpochMilli()
    private const val MB = 1024L * 1024
    private fun f(id: String, name: String, mime: String, size: Long, expires: String?, downloads: Int = 0, public: Boolean = true, status: TempFileStatus = TempFileStatus.Ready) =
        TempFile(id, name, mime, size, status, "Tk9" + id.repeat(40).take(40), public, expires, downloads, if (downloads > 0) "2026-11-15 12:30:00Z" else null,
            "2026-11-14 08:00:00Z", "2026-11-14 08:00:00Z")

    val files = listOf(
        f("a", "proposal-sponsor-2027.pdf", "application/pdf", 4 * MB + 300_000, "2026-11-21 03:00:00Z", downloads = 12),
        f("b", "foto-acara-kantor.zip", "application/zip", 312 * MB, "2026-11-16 08:00:00Z", downloads = 3),
        f("c", "rekap-keuangan-Q3.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 820_000, null, public = false),
        f("d", "rekaman-rapat.m4a", "audio/mp4", 48 * MB, "2026-11-16 03:40:00Z", downloads = 1),
        f("e", "installer-lama.apk", "application/vnd.android.package-archive", 22 * MB, "2026-11-15 00:00:00Z"),
    )

    fun state(online: Boolean = true) = FilesUiState(
        files = files,
        jobs = listOf(UploadJob("j1", "video-liburan.mp4", 180 * MB, 0.42f, UploadPhase.Uploading, fileId = "z")),
        now = NOW, online = online, expiry = ExpiryOption.Week, loaded = true,
    )

    object Actions : FilesActions, PublicFileActions {
        override fun setExpiry(expiry: ExpiryOption) = Unit
        override fun pick(uris: List<Uri>) = Unit
        override fun cancel(key: String) = Unit
        override fun dismiss(key: String) = Unit
        override fun link(file: TempFile) = "https://taskify.example/unduh/${file.shareToken}"
        override fun copied() = Unit
        override fun setPublic(file: TempFile, value: Boolean) = Unit
        override fun regenerate(file: TempFile) = Unit
        override fun applyExpiry(file: TempFile, expiry: ExpiryOption) = Unit
        override fun rename(file: TempFile, name: String) = Unit
        override fun saveSlug(file: TempFile, slug: String, onTaken: (String) -> Unit) = Unit
        override fun applySlug(file: TempFile, slug: String?) = Unit
        override fun slugError(slug: String): String? = null
        override fun delete(file: TempFile, onDone: () -> Unit) = Unit
        override fun download(file: TempFile, target: Uri) = Unit
        override fun retry() = Unit
        override fun save(target: Uri) = Unit
        override fun browserUrl() = "https://p.supabase.co/functions/v1/tempfiles/d/x"
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class FilesScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val padding = PaddingValues(top = 24.dp, bottom = 24.dp)

    private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent { TaskifyTheme(darkTheme = dark) { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Config(qualifiers = "w393dp-h1500dp-xxhdpi")
    @Test fun files() = shot("files_light", false) { FilesContent(SampleFiles.state(), SampleFiles.Actions, padding) }

    @Test fun filesOfflineDark() = shot("files_offline_dark", true) { FilesContent(SampleFiles.state(online = false), SampleFiles.Actions, padding) }

    @Config(qualifiers = "w393dp-h1100dp-xxhdpi")
    @Test fun detail() = shot("files_detail_light", false) { FileDetail(SampleFiles.files[0], SampleFiles.state(), SampleFiles.Actions) {} }

    @Test fun detailExpiredDark() = shot("files_detail_expired_dark", true) { FileDetail(SampleFiles.files[4], SampleFiles.state(), SampleFiles.Actions) {} }

    @Test fun publicReady() = shot("files_public_light", false) {
        PublicFileContent(
            PublicFileState.Ready(PublicFileInfo("installer-lama.apk", "application/vnd.android.package-archive", 22L * 1024 * 1024, "2026-11-19 03:00:00Z", ""), SampleFiles.NOW),
            PublicDownload(), SampleFiles.Actions,
        )
    }

    @Test fun publicMissingDark() = shot("files_public_missing_dark", true) { PublicFileContent(PublicFileState.Missing, PublicDownload(), SampleFiles.Actions) }
}
