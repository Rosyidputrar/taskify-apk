package com.rosyidputrar.taskify.core.media

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.domain.media.Song
import com.rosyidputrar.taskify.core.domain.media.YtEvent
import com.rosyidputrar.taskify.core.domain.media.YtStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regresi item 5 (lompat ke lagu yang sedang di-prefetch tidak boleh resolve ganda)
 * dan item 9 (poll prefetch dibatalkan saat lagu berubah).
 *
 * Klien dipalsukan: tiap resolve menahan di gate sampai tes melepasnya, jadi jumlah
 * panggilan `resolveStreamInfo` bisa dihitung persis.
 */
@RunWith(AndroidJUnit4::class)
class MusicPlayerTest {
    private fun song(id: String) = Song(id.padEnd(11, 'x'), "Lagu $id", "Artis", "3.05", emptyList())
    private val a = song("a"); private val b = song("b"); private val c = song("c")

    private class FakeMusicClient : MusicClient(
        "https://music.test", { "t" },
        HttpClient(MockEngine { request ->
            if (request.url.encodedPath == "/v1/track") respond("""{"videoId":"x","lyricsId":"","upNext":[]}""", HttpStatusCode.OK, headersOf())
            else respond("{}", HttpStatusCode.NotFound, headersOf())
        }) { expectSuccess = false },
    ) {
        val calls = mutableMapOf<String, Int>()
        val cancelled = mutableSetOf<String>()
        val gates = mutableMapOf<String, CompletableDeferred<StreamInfo>>()
        fun gate(id: String) = gates.getOrPut(id) { CompletableDeferred() }

        override suspend fun resolveStreamInfo(videoId: String, forceConverter: Boolean, onProgress: (Int, String) -> Unit): StreamInfo {
            calls[videoId] = (calls[videoId] ?: 0) + 1
            try {
                return gate(videoId).await()
            } catch (e: CancellationException) {
                cancelled += videoId
                throw e
            }
        }
    }

    private fun info(id: String) = StreamInfo("https://audio.test/$id.mp3", "converter", "audio/mpeg", null)

    @Test fun jumpToPrefetchingSongDoesNotResolveTwice() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val fake = FakeMusicClient()
            val player = MusicPlayer(ApplicationProvider.getApplicationContext(), fake)
            player.playQueue(listOf(a, b), 0)
            advanceUntilIdle()
            assertEquals(1, fake.calls[a.videoId], "resolve lagu pertama jalan sekali")
            assertEquals(null, fake.calls[b.videoId])

            // Lagu pertama dianggap sedang diputar (tanpa lewat ExoPlayer): prefetch lagu berikut jalan.
            player.dispatch(YtEvent.Playing)
            advanceUntilIdle()
            assertEquals(1, fake.calls[b.videoId], "prefetch lagu berikut jalan sekali")
            assertTrue(b.videoId in player.state.value.prefetching)

            // Lompat ke lagu yang sedang di-prefetch: resolveCurrent TIDAK boleh mulai resolve kedua.
            player.jump(1)
            advanceUntilIdle()
            assertEquals(b, player.state.value.current)
            assertEquals(1, fake.calls[b.videoId], "tidak ada resolve ganda untuk lagu yang sedang di-prefetch")
            assertEquals(YtStatus.Preparing, player.state.value.status)

            // Prefetch selesai → URL-nya dipakai, tanpa resolve tambahan.
            fake.gate(b.videoId).complete(info(b.videoId))
            advanceUntilIdle()
            assertEquals("https://audio.test/${b.videoId}.mp3", player.state.value.urls[b.videoId])
            assertEquals(1, fake.calls[b.videoId])
            assertTrue(player.state.value.prefetching.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun prefetchIsCancelledWhenSongChanges() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val fake = FakeMusicClient()
            val player = MusicPlayer(ApplicationProvider.getApplicationContext(), fake)
            player.playQueue(listOf(a, b, c), 0)
            advanceUntilIdle()
            player.dispatch(YtEvent.Playing)
            advanceUntilIdle()
            assertEquals(1, fake.calls[b.videoId])
            assertTrue(b.videoId in player.state.value.prefetching)

            // Lompat jauh ke lagu ketiga: poll prefetch lagu kedua harus berhenti.
            player.jump(2)
            advanceUntilIdle()
            assertTrue(b.videoId in fake.cancelled, "prefetch lagu lama dibatalkan saat lagu berubah")
            assertEquals(1, fake.calls[b.videoId], "prefetch lama tidak diulang")
            assertEquals(1, fake.calls[c.videoId], "lagu baru di-resolve sekali")
            assertTrue(b.videoId !in player.state.value.prefetching)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
