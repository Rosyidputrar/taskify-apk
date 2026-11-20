package com.rosyidputrar.taskify

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.domain.media.AccountInfo
import com.rosyidputrar.taskify.core.domain.media.SearchFilter
import com.rosyidputrar.taskify.core.domain.media.SearchResults
import com.rosyidputrar.taskify.core.domain.media.Song
import com.rosyidputrar.taskify.core.domain.media.Suggestion
import com.rosyidputrar.taskify.core.domain.media.TopResult
import com.rosyidputrar.taskify.core.domain.media.Track
import com.rosyidputrar.taskify.core.innertube.PlaybackAuthState
import com.rosyidputrar.taskify.core.innertube.MyTube
import com.rosyidputrar.taskify.core.media.MusicPlayer
import com.rosyidputrar.taskify.core.media.MyTubeMusicClient
import com.rosyidputrar.taskify.core.media.mytube.DirectStream
import com.rosyidputrar.taskify.core.media.mytube.DirectStreamResolver
import com.rosyidputrar.taskify.core.media.mytube.MusicLoginRepository
import com.rosyidputrar.taskify.core.media.mytube.MyTubeAuthStore
import com.rosyidputrar.taskify.feature.media.MusicViewModel
import com.rosyidputrar.taskify.feature.media.OnlineFlow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals

/** Hasil pencarian tidak pernah masuk antrean: yang diputar hanya lagu yang diketuk (+ lagu serupa). */
@RunWith(AndroidJUnit4::class)
class MusicQueueFromSearchTest {
    private fun song(id: String) = Song(id.padEnd(11, 'x'), "Lagu $id", "Artis", "3.05", emptyList(), durationSeconds = 185)
    private val results = listOf(song("a"), song("b"), song("c"), song("d"))
    private val episode = song("ep")

    /** Cari + keterangan lewat fasad direct palsu (Tahap 7); pemutar sudah direct. */
    private inner class FakeTube : MyTube() {
        val pending = CompletableDeferred<Unit>()

        override suspend fun search(query: String, filter: SearchFilter, authState: PlaybackAuthState): Result<SearchResults> =
            Result.success(SearchResults(top = TopResult("Lagu c", results[2].videoId, listOf("Lagu", "Artis", "3.05"), emptyList()), songs = results, episodes = listOf(episode)))

        override suspend fun suggest(input: String, authState: PlaybackAuthState): Result<List<Suggestion>> =
            Result.success(emptyList()) // saran tidak diuji di sini; jangan sentuh jaringan

        override suspend fun track(videoId: String, authState: PlaybackAuthState): Result<Track> {
            pending.await() // keterangan tidak pernah datang di tes ini; yang diuji hanya isi antrean
            error("tidak terjangkau")
        }
    }

    private inner class FakeResolver : DirectStreamResolver() {
        override suspend fun resolve(videoId: String, quality: com.rosyidputrar.taskify.core.domain.media.MusicQuality, authState: PlaybackAuthState, preferMp4a: Boolean, pinnedItag: Int?, networkMetered: Boolean): Result<DirectStream> {
            CompletableDeferred<DirectStream>().await() // audio tidak pernah siap di tes ini
            error("tidak terjangkau")
        }

        override suspend fun tryResolve(videoId: String, quality: com.rosyidputrar.taskify.core.domain.media.MusicQuality, authState: PlaybackAuthState, preferMp4a: Boolean, pinnedItag: Int?, networkMetered: Boolean): DirectStream? {
            CompletableDeferred<DirectStream>().await()
            error("tidak terjangkau")
        }
    }

    private fun setUp(): Pair<MusicViewModel, MusicPlayer> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val authStore = MyTubeAuthStore(context).also { it.clear() }
        // Gate login (Tahap 5): cari/putar hanya jalan bila login — tes ini
        // memakai sesi login dummy supaya perilaku antreannya tetap teruji.
        authStore.saveAuthState(PlaybackAuthState(cookie = "SAPISID=dummy; LOGIN_INFO=dummy", dataSyncId = "delegated||user"))
        authStore.saveAccount(AccountInfo("Tes Akun", "tes@example.com"))
        val tube = FakeTube()
        val player = MusicPlayer(context, tube, FakeResolver(), authStore = authStore)
        val vm = MusicViewModel(player, MyTubeMusicClient(tube), OnlineFlow { flowOf(true) }, null, MusicLoginRepository(tube, authStore))
        vm.setQuery("lagu")
        vm.search()
        shadowOf(Looper.getMainLooper()).idle()
        return vm to player
    }

    @Test fun tappingASearchResultQueuesOnlyThatSong() {
        val (vm, player) = setUp()
        vm.play(1)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(results[1]), player.state.value.ordered, "hasil pencarian lain tidak boleh masuk antrean")
        assertEquals(results[1].videoId, player.state.value.similarSeed, "antrean akan diisi lagu serupa")
    }

    @Test fun topResultAndEpisodeAlsoQueueOnlyThemselves() {
        val (vm, player) = setUp()
        vm.playTop(vm.state.value.search.results.top ?: TopResult("Lagu c", results[2].videoId, listOf("Lagu", "Artis", "3.05"), emptyList()))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(results[2].videoId), player.state.value.ordered.map { it.videoId })
        vm.playSong(episode)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(episode), player.state.value.ordered)
    }
}
