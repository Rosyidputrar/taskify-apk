package com.rosyidputrar.taskify

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
import com.rosyidputrar.taskify.core.domain.files.EntityType
import com.rosyidputrar.taskify.core.domain.media.AccountInfo
import com.rosyidputrar.taskify.core.domain.media.RepeatMode
import com.rosyidputrar.taskify.core.domain.media.SearchFilter
import com.rosyidputrar.taskify.core.domain.media.Song
import com.rosyidputrar.taskify.core.domain.media.TikTokAsset
import com.rosyidputrar.taskify.core.domain.media.TikTokPost
import com.rosyidputrar.taskify.core.domain.media.YtState
import com.rosyidputrar.taskify.core.domain.media.YtStatus
import com.rosyidputrar.taskify.core.domain.search.EntityRow
import com.rosyidputrar.taskify.core.media.PlaybackPosition
import com.rosyidputrar.taskify.core.store.cross.LinkedEntity
import com.rosyidputrar.taskify.core.store.tasks.TagRef
import com.rosyidputrar.taskify.feature.links.LinksActions
import com.rosyidputrar.taskify.feature.links.LinksContent
import com.rosyidputrar.taskify.feature.media.MiniPlayerBar
import com.rosyidputrar.taskify.feature.media.MusicActions
import com.rosyidputrar.taskify.feature.media.MusicContent
import com.rosyidputrar.taskify.feature.media.MusicLoginUiState
import com.rosyidputrar.taskify.feature.media.MusicSearch
import com.rosyidputrar.taskify.feature.media.MusicTab
import com.rosyidputrar.taskify.feature.media.MusicUiState
import com.rosyidputrar.taskify.feature.media.TikTokActions
import com.rosyidputrar.taskify.feature.media.TikTokContent
import com.rosyidputrar.taskify.feature.media.TikTokUiState
import com.rosyidputrar.taskify.feature.organize.TagActions
import com.rosyidputrar.taskify.feature.organize.TagContent
import com.rosyidputrar.taskify.feature.organize.TagUiState
import com.rosyidputrar.taskify.feature.organize.TagsContent
import com.rosyidputrar.taskify.feature.organize.TagsUiState
import com.rosyidputrar.taskify.feature.organize.TrashActions
import com.rosyidputrar.taskify.feature.organize.TrashContent
import com.rosyidputrar.taskify.core.store.cross.SecretTrashRow
import com.rosyidputrar.taskify.core.store.cross.SecretTrashType
import com.rosyidputrar.taskify.feature.organize.TrashUiState
import com.rosyidputrar.taskify.ui.placeholder.BackButton
import com.rosyidputrar.taskify.ui.share.FileTarget
import com.rosyidputrar.taskify.ui.share.ShareActions
import com.rosyidputrar.taskify.ui.share.ShareContent
import com.rosyidputrar.taskify.ui.share.ShareContentView
import com.rosyidputrar.taskify.ui.share.SharedFile
import com.rosyidputrar.taskify.ui.share.ShareUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant

object SampleCross {
    val NOW = Instant.parse("2026-11-16T05:00:00Z").toEpochMilli()
    val tags = listOf(TagRef("t1", "kerja", "sky", 14), TagRef("t2", "rumah", "emerald", 9), TagRef("t3", "ide", "amber", 6),
        TagRef("t4", "taskify-rilis", "violet", 5), TagRef("t5", "keuangan", "rose", 3), TagRef("t6", "bacaan", "teal", 2), TagRef("t7", "kesehatan", "orange", 1))
    val rows = listOf(
        EntityRow(EntityType.Task, "a", "Kirim laporan Q4 ke tim", "2026-11-18"),
        EntityRow(EntityType.Task, "b", "Review PR modul Sandi", null),
        EntityRow(EntityType.Note, "c", "Ide fitur heatmap di Beranda", null),
        EntityRow(EntityType.JournalEntry, "d", "Rapat perencanaan berjalan baik", "2026-11-14"),
        EntityRow(EntityType.Transaction, "e", "Langganan server", "2026-11-12|expense|120000"),
        EntityRow(EntityType.Snippet, "f", "Script deploy", "bash"),
    )
    val trash = listOf(
        EntityRow(EntityType.Note, "n1", "Draf pengumuman lama", null, "2026-11-15 09:00:00Z"),
        EntityRow(EntityType.Task, "t1", "Telepon vendor AC", "2026-11-03", "2026-11-04T02:00:00.000Z"),
        EntityRow(EntityType.Transaction, "x1", "Parkir", "2026-10-28|expense|5000", "2026-10-20T02:00:00.000Z"),
    )
    val secretTrash = listOf(
        SecretTrashRow(SecretTrashType.ConfigFile, "c1", ".env", "production", "2026-11-14T02:00:00.000Z"),
        SecretTrashRow(SecretTrashType.PasswordItem, "p1", "Item Sandi", null, "2026-11-10T02:00:00.000Z"),
    )
    private fun song(id: String, t: String, a: String, d: String) = Song(id.padEnd(11, '0'), t, a, d, emptyList())
    val songs = listOf(song("s1", "Hujan di Bulan Juni", "Kopi Senja", "4.12"), song("s2", "Lampu Kota", "Orkes Malam", "3.48"),
        song("s3", "Pulang", "Rumah Kaca", "5.03"), song("s4", "Jalan Pagi", "Sinar Tujuh", "3.21"), song("s5", "Daun", "Gema Hutan", "4.40"))

    // Akun contoh untuk shot musik: konten musik hanya tampil bila login
    // (gate Tahap 6); shot konten memakai keadaan LoggedIn ini.
    val sampleAccount = AccountInfo("Kopi Senja", "kopi@contoh.id", "@kopisenja")

    fun music(tab: MusicTab = MusicTab.Search) = MusicUiState(
        tab = tab,
        login = MusicLoginUiState.LoggedIn(sampleAccount),
        search = MusicSearch("lagu santai", SearchFilter.Songs, com.rosyidputrar.taskify.core.domain.media.SearchResults(songs = songs, cursor = "c"), searched = true),
        player = YtState(queue = songs, order = listOf(0, 1, 2, 3, 4), position = 1, status = YtStatus.Playing, repeat = RepeatMode.All),
        position = PlaybackPosition(83_000, 228_000),
    )

    val post = TikTokPost("7312345678901234567", "photo", "Jalan-jalan sore di Bandung 🌇 #bandung #senja", 1763000000, "https://www.tiktok.com/@budi/photo/7312345678901234567",
        "budi.jalan", "Budi Jalan-jalan", true, 1_234_567, 98_400, 1_204, 310, null, "suara asli", "Budi", listOf("bandung", "senja"), null,
        null, listOf("p1", "p2", "p3"), "a")

    object Actions : TagActions, TrashActions, ShareActions, MusicActions, TikTokActions, LinksActions {
        override fun open(row: EntityRow) = Unit
        override fun rename(name: String, onDone: (Boolean) -> Unit) = Unit
        override fun setColor(color: String) = Unit
        override fun delete(onDone: () -> Unit) = Unit
        override fun restore(row: EntityRow) = Unit
        override fun destroy(row: EntityRow) = Unit
        override fun emptyAll() = Unit
        override fun saveTask() = Unit
        override fun saveJournal() = Unit
        override fun saveSnippet() = Unit
        override fun quickCapture() = ""
        override fun saveFile(file: SharedFile, target: FileTarget) = Unit
        override fun dismissText() = Unit
        override fun finish() = Unit
        override fun setTab(tab: MusicTab) = Unit
        override fun setQuery(q: String) = Unit
        override fun setFilter(f: SearchFilter) = Unit
        override fun search() = Unit
        override fun loadMore() = Unit
        override fun play(index: Int) = Unit
        override fun togglePlay() = Unit
        override fun next() = Unit
        override fun prev() = Unit
        override fun seek(ms: Long) = Unit
        override fun jump(position: Int) = Unit
        override fun toggleRepeat() = Unit
        override fun toggleShuffle() = Unit
        override fun toggleAutoContinue() = Unit
        override fun stop() = Unit
        override fun setInput(text: String) = Unit
        override fun extract(text: String?) = Unit
        override fun download(asset: TikTokAsset) = Unit
        override fun clear() = Unit
        override fun link(target: EntityRow) = Unit
        override fun unlink(link: LinkedEntity) = Unit
        override suspend fun candidates(query: String) = emptyList<EntityRow>()
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class CrossModuleScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val padding = PaddingValues(top = 24.dp, bottom = 24.dp)

    private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent { TaskifyTheme(darkTheme = dark) { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test fun tags() = shot("tags_light", false) { TagsContent(TagsUiState(SampleCross.tags, true), padding, {}) }

    @Test fun tagDark() = shot("tag_detail_dark", true) {
        TagContent(TagUiState(SampleCross.tags[0], SampleCross.rows, true), SampleCross.Actions, padding, {}) { BackButton(it) }
    }

    @Test fun trash() = shot("trash_light", false) { TrashContent(TrashUiState(SampleCross.trash, true, SampleCross.NOW, SampleCross.secretTrash), SampleCross.Actions, padding) }

    @Test fun share() = shot("share_light", false) {
        val dir = File(System.getProperty("java.io.tmpdir"))
        ShareContentView(
            ShareUiState(ShareContent("", "Baca nanti: cara merapikan keuangan rumah tangga https://contoh.id/artikel", "", listOf(
                SharedFile("k1", File(dir, "k1"), "struk-alfamart.jpg", "image/jpeg", 412_000),
                SharedFile("k2", File(dir, "k2"), "taskify-main.zip", "application/zip", 6_300_000),
            ))),
            SampleCross.Actions, padding, {}, {}, { BackButton(it) },
        )
    }

    @Config(qualifiers = "w393dp-h1500dp-xxhdpi")
    @Test fun music() = shot("music_light", false) { MusicContent(SampleCross.music(), SampleCross.Actions, padding) }

    @Test fun musicQueueDark() = shot("music_queue_dark", true) { MusicContent(SampleCross.music(MusicTab.Queue).copy(player = SampleCross.music().player.copy(status = YtStatus.Preparing, percent = 45, label = "Menyiapkan audio… 45%")), SampleCross.Actions, padding) }

    @Test fun miniPlayer() = shot("music_mini_light", false) { Box(Modifier.padding(16.dp)) { MiniPlayerBar(SampleCross.music(), SampleCross.Actions, {}) } }

    @Config(qualifiers = "w393dp-h1300dp-xxhdpi")
    @Test fun tiktok() = shot("tiktok_light", false) {
        TikTokContent(TikTokUiState(input = "https://vt.tiktok.com/ZSabc123/", post = SampleCross.post, downloading = mapOf("photo:1" to 0.4f)), SampleCross.Actions, padding)
    }

    @Test fun links() = shot("links_light", false) {
        Box(Modifier.padding(20.dp)) {
            LinksContent(listOf(LinkedEntity("l1", SampleCross.rows[2]), LinkedEntity("l2", SampleCross.rows[4]), LinkedEntity("l3", SampleCross.rows[1].copy(deletedAt = "2026-11-15T00:00:00Z"))), SampleCross.Actions)
        }
    }
}
