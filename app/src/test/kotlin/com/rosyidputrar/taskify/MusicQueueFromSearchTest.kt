package com.rosyidputrar.taskify

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.domain.media.SearchFilter
import com.rosyidputrar.taskify.core.domain.media.SearchResults
import com.rosyidputrar.taskify.core.domain.media.Song
import com.rosyidputrar.taskify.core.domain.media.TopResult
import com.rosyidputrar.taskify.core.domain.media.Track
import com.rosyidputrar.taskify.core.media.MusicClient
import com.rosyidputrar.taskify.core.media.MusicPlayer
import com.rosyidputrar.taskify.core.media.Playback
import com.rosyidputrar.taskify.core.media.StreamOptions
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
    private val similar = listOf(song("s1"), song("s2"))

    private inner class Fake : MusicClient("https://music.test", { "t" }) {
        val pending = CompletableDeferred<Unit>()
        override suspend fun search(query: String, filter: SearchFilter) =
            SearchResults(top = TopResult("Lagu c", results[2].videoId, listOf("Lagu", "Artis", "3.05"), emptyList()), songs = results, episodes = listOf(episode))
        override suspend fun playback(videoId: String, options: StreamOptions, withTrack: Boolean, onProgress: (Int, String) -> Unit): Playback {
            pending.await() // audio tidak pernah siap di tes ini; yang diuji hanya isi antrean
            error("tidak terjangkau")
        }
        override suspend fun track(videoId: String, includeLyrics: Boolean) = Track(videoId, "", similar)
    }

    private fun setUp(): Pair<MusicViewModel, MusicPlayer> {
        val fake = Fake()
        val player = MusicPlayer(ApplicationProvider.getApplicationContext(), fake)
        val vm = MusicViewModel(player, fake, OnlineFlow { flowOf(true) })
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
