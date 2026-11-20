package com.rosyidputrar.taskify

import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.designsystem.component.UiMessage
import com.rosyidputrar.taskify.core.designsystem.theme.TaskifyTheme
import com.rosyidputrar.taskify.core.designsystem.theme.ThemeMode
import com.rosyidputrar.taskify.core.domain.media.AccountInfo
import com.rosyidputrar.taskify.core.domain.media.MusicQuality
import com.rosyidputrar.taskify.core.domain.media.SearchFilter
import com.rosyidputrar.taskify.core.domain.media.SearchResults
import com.rosyidputrar.taskify.core.domain.media.Song
import com.rosyidputrar.taskify.core.domain.media.Suggestion
import com.rosyidputrar.taskify.core.domain.media.TopResult
import com.rosyidputrar.taskify.core.domain.media.Track
import com.rosyidputrar.taskify.core.domain.media.YtStatus
import com.rosyidputrar.taskify.core.innertube.MyTube
import com.rosyidputrar.taskify.core.innertube.PlaybackAuthState
import com.rosyidputrar.taskify.core.media.MusicPlayer
import com.rosyidputrar.taskify.core.media.MyTubeMusicClient
import com.rosyidputrar.taskify.core.media.mytube.DirectStream
import com.rosyidputrar.taskify.core.media.mytube.DirectStreamResolver
import com.rosyidputrar.taskify.core.media.mytube.MusicLoginRepository
import com.rosyidputrar.taskify.core.media.mytube.MyTubeAuthStore
import com.rosyidputrar.taskify.data.AppPreferences
import com.rosyidputrar.taskify.feature.media.MusicViewModel
import com.rosyidputrar.taskify.feature.media.OnlineFlow
import com.rosyidputrar.taskify.ui.home.HomeContent
import com.rosyidputrar.taskify.ui.shell.TaskifyApp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertTrue

/**
 * A11 — shell asli dengan pemutar aktif: bilah mini seluruhnya di atas dok, dan snackbar
 * ("Urungkan") tidak tertutup bilah mini. Dulu hanya layar Musik yang diuji tanpa shell
 * ([MusicPlayerDockTest.playerAboveDockGolden] merender MusicContent saja).
 *
 * Pemutar memakai resolver palsu yang tidak pernah selesai (status Preparing — tetap
 * "aktif" menurut shell), jadi tidak ada jaringan yang disentuh.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class ShellMiniPlayerTest {
    @get:Rule val compose = createComposeRule()

    private fun song(id: String) = Song(id.padEnd(11, 'x'), "Lagu $id", "Artis", "3.05", emptyList(), durationSeconds = 185)
    private val results = listOf(song("a"), song("b"), song("c"), song("d"))

    private inner class FakeTube : MyTube() {
        val pending = CompletableDeferred<Unit>()

        override suspend fun search(query: String, filter: SearchFilter, authState: PlaybackAuthState): Result<SearchResults> =
            Result.success(SearchResults(top = TopResult("Lagu c", results[2].videoId, listOf("Lagu", "Artis", "3.05"), emptyList()), songs = results, episodes = emptyList()))

        override suspend fun suggest(input: String, authState: PlaybackAuthState): Result<List<Suggestion>> =
            Result.success(emptyList())

        override suspend fun track(videoId: String, authState: PlaybackAuthState): Result<Track> {
            pending.await()
            error("tidak terjangkau")
        }
    }

    private inner class FakeResolver : DirectStreamResolver() {
        override suspend fun resolve(videoId: String, quality: MusicQuality, authState: PlaybackAuthState, preferMp4a: Boolean, pinnedItag: Int?, networkMetered: Boolean): Result<DirectStream> {
            CompletableDeferred<DirectStream>().await()
            error("tidak terjangkau")
        }

        override suspend fun tryResolve(videoId: String, quality: MusicQuality, authState: PlaybackAuthState, preferMp4a: Boolean, pinnedItag: Int?, networkMetered: Boolean): DirectStream? {
            CompletableDeferred<DirectStream>().await()
            error("tidak terjangkau")
        }
    }

    private lateinit var player: MusicPlayer

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val authStore = MyTubeAuthStore(context).also { it.clear() }
        authStore.saveAuthState(PlaybackAuthState(cookie = "SAPISID=dummy; LOGIN_INFO=dummy", dataSyncId = "delegated||user"))
        authStore.saveAccount(AccountInfo("Tes Akun", "tes@example.com"))
        val tube = FakeTube()
        player = MusicPlayer(context, tube, FakeResolver(), authStore = authStore)
        val vm = MusicViewModel(player, MyTubeMusicClient(tube), OnlineFlow { flowOf(true) }, null, MusicLoginRepository(tube, authStore))
        vm.setQuery("lagu")
        vm.search()
        shadowOf(Looper.getMainLooper()).idle()
        vm.play(1)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(player.state.value.status != YtStatus.Idle, "pemutar harus aktif sebelum shell dirender")

        stopKoin()
        startKoin {
            modules(
                module {
                    single { player }
                    single { MyTubeMusicClient(tube) }
                    single<OnlineFlow> { OnlineFlow { flowOf(true) } }
                    single { MusicLoginRepository(tube, authStore) }
                    viewModel { MusicViewModel(get(), get(), get(), getOrNull(), get()) }
                },
            )
        }
    }

    @After fun tearDown() = stopKoin()

    private fun setApp() {
        // Jam dimatikan: animasi masuk dok/bilah/snackbar dikendalikan manual supaya
        // pengukuran batas dilakukan setelah semuanya menetap — tapi sebelum snackbar
        // Short (4 dtk) hilang sendiri.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TaskifyTheme {
                TaskifyApp(
                    preferences = AppPreferences.Snapshot(ThemeMode.Light, null),
                    onThemeModeChange = {},
                    onPinnedModuleChange = {},
                    displayName = "Rosyid",
                    messages = flowOf(UiMessage("Tugas disimpan", actionLabel = "Urungkan", action = {})),
                    home = { padding, name, openModule, openTask ->
                        HomeContent(SampleData.homeState(), name, padding, openModule, openTask, {}, {})
                    },
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(2000)
        compose.mainClock.autoAdvance = true
    }

    @Test fun miniPlayerFullyAboveDockAndSnackbarAboveMiniPlayer() {
        setApp()
        compose.onNodeWithText("Lagu b").assertIsDisplayed()

        val mini = compose.onNodeWithTag("mini-player").fetchSemanticsNode().boundsInRoot
        val dockHandle = compose.onNodeWithContentDescription("Tarik ke atas untuk mencari").fetchSemanticsNode().boundsInRoot
        assertTrue(
            mini.bottom <= dockHandle.top,
            "bilah mini harus seluruhnya di atas dok: bawah mini=${mini.bottom}, atas dok=${dockHandle.top}",
        )

        val snackbar = compose.onNodeWithTag("snackbar").fetchSemanticsNode().boundsInRoot
        assertTrue(
            snackbar.bottom <= mini.top,
            "snackbar harus seluruhnya di atas bilah mini: bawah snackbar=${snackbar.bottom}, atas mini=${mini.top}",
        )
        compose.onNodeWithText("Tugas disimpan").assertIsDisplayed()
        compose.onNodeWithText("Urungkan").assertIsDisplayed()
    }
}
