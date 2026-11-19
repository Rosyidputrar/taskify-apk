package com.rosyidputrar.taskify

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.domain.media.AlbumRef
import com.rosyidputrar.taskify.core.domain.media.AccountInfo
import com.rosyidputrar.taskify.core.domain.media.ArtistPage
import com.rosyidputrar.taskify.core.domain.media.ArtistRef
import com.rosyidputrar.taskify.core.domain.media.BrowseTarget
import com.rosyidputrar.taskify.core.domain.media.CollectionKind
import com.rosyidputrar.taskify.core.domain.media.DiscoverySection
import com.rosyidputrar.taskify.core.domain.media.LyricLine
import com.rosyidputrar.taskify.core.domain.media.Lyrics
import com.rosyidputrar.taskify.core.domain.media.MoodCategory
import com.rosyidputrar.taskify.core.domain.media.MoodGroup
import com.rosyidputrar.taskify.core.domain.media.MusicCollection
import com.rosyidputrar.taskify.core.domain.media.MusicSettings
import com.rosyidputrar.taskify.core.domain.media.PlaylistRef
import com.rosyidputrar.taskify.core.domain.media.PodcastRef
import com.rosyidputrar.taskify.core.domain.media.SearchFilter
import com.rosyidputrar.taskify.core.domain.media.SearchResults
import com.rosyidputrar.taskify.core.domain.media.SleepTimer
import com.rosyidputrar.taskify.core.domain.media.Song
import com.rosyidputrar.taskify.core.domain.media.StreamSource
import com.rosyidputrar.taskify.core.domain.media.Suggestion
import com.rosyidputrar.taskify.core.domain.media.TopResult
import com.rosyidputrar.taskify.core.domain.media.YtStatus
import com.rosyidputrar.taskify.core.media.PlaybackPosition
import com.rosyidputrar.taskify.core.media.LocalRadioStations
import com.rosyidputrar.taskify.core.media.youtube.MusicLoginError
import com.rosyidputrar.taskify.feature.media.BrowsePage
import com.rosyidputrar.taskify.feature.media.ExploreState
import com.rosyidputrar.taskify.feature.media.FocusMusicContent
import com.rosyidputrar.taskify.feature.media.FocusMusicState
import com.rosyidputrar.taskify.feature.media.FocusMusicActions
import com.rosyidputrar.taskify.feature.media.LyricsState
import com.rosyidputrar.taskify.feature.media.MusicActions
import com.rosyidputrar.taskify.feature.media.MusicContent
import com.rosyidputrar.taskify.feature.media.MusicLoginScreenState
import com.rosyidputrar.taskify.feature.media.MusicLoginStateContent
import com.rosyidputrar.taskify.feature.media.MusicLoginUiState
import com.rosyidputrar.taskify.feature.media.MusicSearch
import com.rosyidputrar.taskify.feature.media.MusicSettingsContent
import com.rosyidputrar.taskify.feature.media.MusicTab
import com.rosyidputrar.taskify.feature.media.MusicUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/** Fase 2–4 Musik: Jelajah, pencarian lengkap, halaman koleksi/artis, lirik bergulir, pengaturan, Musik fokus. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class MusicScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val padding = PaddingValues(top = 24.dp, bottom = 24.dp)
    private val songs = SampleCross.songs.mapIndexed { i, s -> s.copy(durationSeconds = listOf(252, 228, 303, 201, 280)[i]) }
    private val base = SampleCross.music().copy(search = MusicSearch(), player = SampleCross.music().player.copy(queue = songs))

    private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent { TaskifyTheme(darkTheme = dark) { Surface(color = MaterialTheme.colorScheme.background) { content() } } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    private val explore = ExploreState(
        loaded = true,
        moods = listOf(MoodGroup("Mood & momen", listOf("Fokus", "Santai", "Semangat!", "Tidur", "Sedih").map { MoodCategory(it, "p-$it") })),
        stations = LocalRadioStations.FALLBACK,
        home = listOf(
            DiscoverySection.Songs("Pilihan cepat", songs),
            DiscoverySection.Playlists("Throwbacks", listOf(PlaylistRef("VLRDCLAK1", "RDCLAK1", "Pop 2000-an", "YouTube Music", emptyList()), PlaylistRef("VLPL2", "PL2", "Akustik sore", "Kopi Senja", emptyList()))),
        ),
        charts = listOf(DiscoverySection.Artists("Artis teratas", listOf(ArtistRef("UC1", "Kopi Senja", "1,2 jt", emptyList()), ArtistRef("UC2", "Orkes Malam", "800 rb", emptyList())))),
    )

    @Config(qualifiers = "w393dp-h1700dp-xxhdpi")
    @Test fun explore() = shot("music_explore_light", false) {
        MusicContent(base.copy(tab = MusicTab.Explore, explore = explore, player = base.player.copy(status = YtStatus.Idle)), object : MusicActions {}, padding)
    }

    @Config(qualifiers = "w393dp-h1700dp-xxhdpi")
    @Test fun searchAllDark() = shot("music_search_all_dark", true) {
        val results = SearchResults(
            top = TopResult("Hujan di Bulan Juni", songs[0].videoId, listOf("Lagu", "Kopi Senja", "4.12"), emptyList()),
            songs = songs.take(3),
            albums = listOf(AlbumRef("MPREb_1", "Senja Kota", "Kopi Senja", "2024", emptyList()), AlbumRef("MPREb_2", "Hujan", "Kopi Senja", "2021", emptyList())),
            artists = listOf(ArtistRef("UC1", "Kopi Senja", "1,2 jt audiens bulanan", emptyList())),
            podcasts = listOf(PodcastRef("MPSP1", "Obrolan Sore", "Rumah Kaca", emptyList())),
            episodes = listOf(Song("ep000000001", "Musik & produktivitas", "Obrolan Sore · 2 h lalu", "", emptyList())),
            cursor = "gabungan",
        )
        MusicContent(base.copy(tab = MusicTab.Search, player = base.player.copy(status = YtStatus.Idle),
            search = MusicSearch("kopi senja", SearchFilter.All, results, searched = true)), object : MusicActions {}, padding)
    }

    @Test fun suggestionsPickPlaysVideo() {
        val picked = mutableListOf<Suggestion>()
        val sugg = listOf(Suggestion("kopi senja hujan", "query"), Suggestion("Kopi Senja", "artist", "Artis • 1,2 jt", browseId = "UC1"),
            Suggestion("Hujan di Bulan Juni", "video", "Lagu • Kopi Senja", videoId = "s1000000000"))
        compose.setContent {
            TaskifyTheme {
                MusicContent(base.copy(tab = MusicTab.Search, player = base.player.copy(status = YtStatus.Idle), search = MusicSearch("kopi se", suggestions = sugg)),
                    object : MusicActions { override fun pickSuggestion(s: Suggestion) { picked += s } }, padding)
            }
        }
        compose.onRoot().captureRoboImage("screenshots/music_suggest_light.png")
        compose.onNodeWithText("Hujan di Bulan Juni").performClick()
        assertEquals("video", picked.single().type)
    }

    @Config(qualifiers = "w393dp-h1200dp-xxhdpi")
    @Test fun albumPage() = shot("music_album_light", false) {
        val col = MusicCollection(CollectionKind.Album, "MPREb_1", "Senja Kota", "Kopi Senja · 2024", emptyList(), songs, trackCount = 5)
        MusicContent(base.copy(pages = listOf(BrowsePage(BrowseTarget.Album("MPREb_1", "Senja Kota"), false, collection = col))), object : MusicActions {}, padding)
    }

    @Config(qualifiers = "w393dp-h1400dp-xxhdpi")
    @Test fun artistPageDark() = shot("music_artist_dark", true) {
        val artist = ArtistPage("UC1", "Kopi Senja", "250 rb", "1,2 jt audiens bulanan", "Duo folk dari Bandung yang menulis lagu tentang hujan dan kota.", emptyList(),
            songs.take(4).map { it.copy(duration = "", durationSeconds = null) }, "VLOLAK1",
            listOf(AlbumRef("MPREb_1", "Senja Kota", "", "2024", emptyList()), AlbumRef("MPREb_2", "Hujan", "", "2021", emptyList())), emptyList(),
            listOf(ArtistRef("UC2", "Orkes Malam", "", emptyList())))
        MusicContent(base.copy(player = base.player.copy(status = YtStatus.Idle), pages = listOf(BrowsePage(BrowseTarget.Artist("UC1"), false, artist = artist))), object : MusicActions {}, padding)
    }

    @Config(qualifiers = "w393dp-h1500dp-xxhdpi")
    @Test fun syncedLyrics() = shot("music_lyrics_synced_light", false) {
        val lines = listOf("Hujan turun pelan di bulan Juni", "Jalanan basah memantulkan lampu", "", "Kita berteduh di bawah atap toko", "Menunggu reda tanpa banyak kata",
            "Dan kota pun bernyanyi untuk kita").mapIndexed { i, t -> LyricLine(10.0 + i * 4, t) }
        val p = base.player.copy(streams = mapOf(songs[1].videoId to StreamSource(StreamSource.SOURCE_DIRECT, "audio/webm", 251)))
        MusicContent(base.copy(tab = MusicTab.Lyrics, player = p, position = PlaybackPosition(23_000, 228_000),
            lyrics = LyricsState(songs[1].videoId, Lyrics(lines, "", "lrclib"))), object : MusicActions {}, padding)
    }

    @Config(qualifiers = "w393dp-h1400dp-xxhdpi")
    @Test fun queuePlayingDirect() = shot("music_queue_playing_light", false) {
        // Antrean sedang memutar via direct-to-YouTube (opus 251, tanpa
        // konverter/arsip): daftar antrean + bilah mini player dalam konteks.
        val p = base.player.copy(status = YtStatus.Playing,
            streams = mapOf(songs[1].videoId to StreamSource(StreamSource.SOURCE_DIRECT, "audio/webm", 251)))
        MusicContent(base.copy(tab = MusicTab.Queue, player = p, position = PlaybackPosition(45_000, 228_000)), object : MusicActions {}, padding)
    }

    @Config(qualifiers = "w393dp-h1100dp-xxhdpi")
    @Test fun liveCannotSeek() = shot("music_live_light", false) {
        // Pengganti shot konverter (dihapus Tahap 7): siaran langsung tetap
        // tidak bisa digeser, tapi tanpa jalur konverter/arsip.
        val live = Song("live:soma-dronezone", "Drone Zone", "SomaFM", "", emptyList(), "https://ice1.somafm.com/dronezone-128-mp3", null, live = true)
        val p = base.player.copy(queue = listOf(live), status = YtStatus.Playing)
        MusicContent(base.copy(tab = MusicTab.Queue, player = p, sleep = SleepTimer(endOfSong = true)), object : MusicActions {}, padding)
    }

    @Test fun settings() = shot("music_settings_light", false) {
        MusicContentSettings(MusicSettings(sponsorBlock = true, preferMp4a = true))
    }

    // Tahap 6 login (PLAN C.9): prompt gate, seksi AKUN, state layar login
    // (statis — WebView tidak bisa di-screenshot di Robolectric).
    private val sampleAccount = AccountInfo("Kopi Senja", "kopi@contoh.id", "@kopisenja")

    @Test fun loginPromptLight() = shot("music_login_prompt_light", false) {
        MusicContent(base.copy(login = MusicLoginUiState.LoggedOut), object : MusicActions {}, padding)
    }

    @Test fun loginPromptDark() = shot("music_login_prompt_dark", true) {
        MusicContent(base.copy(login = MusicLoginUiState.LoggedOut), object : MusicActions {}, padding)
    }

    @Test fun loginPromptExpired() = shot("music_login_expired_light", false) {
        MusicContent(base.copy(login = MusicLoginUiState.Expired(sampleAccount)), object : MusicActions {}, padding)
    }

    @Test fun settingsAccountLoggedIn() = shot("music_settings_account_in_light", false) {
        MusicSettingsContent(base.copy(login = MusicLoginUiState.LoggedIn(sampleAccount)), object : MusicActions {})
    }

    @Test fun settingsAccountLoggedOut() = shot("music_settings_account_out_light", false) {
        MusicSettingsContent(base.copy(login = MusicLoginUiState.LoggedOut), object : MusicActions {})
    }

    @Test fun loginLoading() = shot("music_login_loading_light", false) {
        MusicLoginStateContent(MusicLoginScreenState.Loading, onRetry = {})
    }

    @Test fun loginError() = shot("music_login_error_light", false) {
        MusicLoginStateContent(MusicLoginScreenState.Error("Tidak bisa menghubungi YouTube. Periksa koneksi."), onRetry = {})
    }

    // Teks mentah error tampil langsung di bawah pesan ramah (perintah sir):
    // golden baru, fixture error lama tidak diubah. Baris "cookie: ..."
    // di akhir rawDetail adalah format baru PLAN §C.3 butir 2 (Agen B):
    // nama cookie sesi penuh, tanpa nilai — satu-satunya perbedaan dari
    // golden sebelumnya, diverifikasi visual sebelum rekam ulang.
    // Tiga baris diagnostik wire di bawahnya adalah format baru PLAN §C
    // (Agen C): cookie terlampir / hash dari / hash origin, nama saja.
    @Test fun loginErrorRaw() = shot("music_login_error_raw_light", false) {
        MusicLoginStateContent(
            MusicLoginScreenState.Error(
                MusicLoginError.UnrecognizedResponse.message,
                rawDetail = "HTTP 200\n{\"accountMenu\":{\"items\":[]},\"responseContext\":{\"serviceTrackingParams\":[]}}\ncookie: HSID, LOGIN_INFO, SAPISID, SID, SSID\ncookie terlampir: ya\nhash dari: SAPISID\nhash origin: https://music.youtube.com",
            ),
            onRetry = {},
        )
    }

    // Error pemutar + error cari/Jelajah tampilkan teks mentah + tombol
    // salin (perintah sir, Agen A/B): golden baru supaya rendernya bisa
    // diperiksa visual sebelum dikirim. Fixture rawDetail mengikuti format
    // asli penyusunnya (PlaybackErrorDetail / musicErrorRawDetail): tanpa
    // URL lengkap, cookie, atau token. Empat baris diagnostik 403
    // (Agen C, webcreator-403-fix): klien, paramUrl (keberadaan NAMA param
    // n/pot/sig/expire/ip — tanpa nilai, jadi `ip` ikut tercakup),
    // jedaResolveKePutarMs, expiresInSeconds.
    @Config(qualifiers = "w393dp-h1700dp-xxhdpi")
    @Test fun playerErrorRaw() = shot("music_player_error_raw_light", false) {
        val p = base.player.copy(
            status = YtStatus.Error,
            error = "Audio gagal diputar. Coba lagi.",
            errorDetail = "videoId: ${songs[0].videoId}\nformat: itag 251 (audio/webm; codecs=\"opus\")\nhost: rr2---sn-a5mekn7z.googlevideo.com\nerrorCodeName: ERROR_CODE_IO_NETWORK_CONNECTION_FAILED\nerrorCode: 2007\nmessage: Gagal tersambung.\nklien: WEB_CREATOR\nparamUrl: n: ada, pot: ada, sig: ada, expire: ada, ip: ada\njedaResolveKePutarMs: 1240\nexpiresInSeconds: 21540",
        )
        MusicContent(base.copy(tab = MusicTab.Queue, player = p), object : MusicActions {}, padding)
    }

    @Config(qualifiers = "w393dp-h1400dp-xxhdpi")
    @Test fun searchErrorRaw() = shot("music_search_error_raw_light", false) {
        MusicContent(
            base.copy(
                tab = MusicTab.Search,
                player = base.player.copy(status = YtStatus.Idle),
                search = MusicSearch(
                    query = "negoro angin",
                    error = "Pencarian gagal. Coba lagi.",
                    errorDetail = "jenis: HttpException\nstatus: 403\npesan: Forbidden",
                    searched = true,
                ),
            ),
            object : MusicActions {},
            padding,
        )
    }

    @Config(qualifiers = "w393dp-h1700dp-xxhdpi")
    @Test fun exploreErrorRaw() = shot("music_explore_error_raw_light", false) {
        MusicContent(
            base.copy(
                tab = MusicTab.Explore,
                player = base.player.copy(status = YtStatus.Idle),
                explore = explore.copy(
                    error = "Jelajah belum bisa dimuat. Coba lagi.",
                    errorDetail = "jenis: HttpException\nstatus: 403\npesan: Forbidden",
                ),
            ),
            object : MusicActions {},
            padding,
        )
    }

    @Config(qualifiers = "w393dp-h1400dp-xxhdpi")
    @Test fun lyricsErrorRaw() = shot("music_lyrics_error_raw_light", false) {
        val p = base.player.copy(streams = mapOf(songs[1].videoId to StreamSource(StreamSource.SOURCE_DIRECT, "audio/webm", 251)))
        MusicContent(
            base.copy(
                tab = MusicTab.Lyrics,
                player = p,
                position = PlaybackPosition(23_000, 228_000),
                lyrics = LyricsState(
                    songs[1].videoId,
                    error = "Lirik gagal dimuat. Coba lagi.",
                    errorDetail = "jenis: HttpException\nstatus: 404\npesan: Not Found",
                ),
            ),
            object : MusicActions {},
            padding,
        )
    }

    @Composable
    private fun MusicContentSettings(s: MusicSettings) = MusicSettingsContent(base.copy(settings = s), object : MusicActions {})

    @Test fun focusMusic() = shot("focus_music_dark", true) {
        FocusMusicContent(FocusMusicState(stations = LocalRadioStations.FALLBACK, playingId = "soma-groovesalad", playing = true), object : FocusMusicActions {}, Modifier.padding(16.dp))
    }

    @Test fun pageSlidesInAndBackPops() {
        val col = MusicCollection(CollectionKind.Album, "MPREb_1", "Senja Kota", "Kopi Senja · 2024", emptyList(), songs, trackCount = 5)
        var st by androidx.compose.runtime.mutableStateOf(base.copy(tab = MusicTab.Queue))
        var backs = 0
        compose.setContent {
            TaskifyTheme {
                MusicContent(st, object : MusicActions {
                    override fun back() { backs++; st = st.copy(pages = st.pages.dropLast(1)) }
                }, padding, backButton = { onClick -> androidx.compose.material3.TextButton(onClick = onClick) { androidx.compose.material3.Text("Kembali") } })
            }
        }
        compose.waitForIdle()
        st = st.copy(pages = listOf(BrowsePage(BrowseTarget.Album("MPREb_1", "Senja Kota"), false, collection = col)))
        compose.waitForIdle()
        compose.onNodeWithText("Senja Kota").assertExists()
        compose.onNodeWithText("Kembali").performClick()
        compose.waitForIdle()
        assertEquals(1, backs)
        compose.onNodeWithText("Senja Kota").assertDoesNotExist()
        compose.onNodeWithText("Lampu Kota", substring = false).assertExists()
    }
}
