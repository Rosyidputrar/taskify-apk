package com.rosyidputrar.taskify.core.media

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.domain.media.AccountInfo
import com.rosyidputrar.taskify.core.domain.media.MusicQuality
import com.rosyidputrar.taskify.core.domain.media.RadioPageResult
import com.rosyidputrar.taskify.core.domain.media.RadioSource
import com.rosyidputrar.taskify.core.domain.media.Song
import com.rosyidputrar.taskify.core.domain.media.Track
import com.rosyidputrar.taskify.core.domain.media.YtEvent
import com.rosyidputrar.taskify.core.domain.media.YtStatus
import com.rosyidputrar.taskify.core.innertube.PlaybackAuthState
import com.rosyidputrar.taskify.core.innertube.YouTube
import com.rosyidputrar.taskify.core.media.sponsorblock.SponsorBlockRepository
import com.rosyidputrar.taskify.core.media.youtube.DirectStream
import com.rosyidputrar.taskify.core.media.youtube.DirectStreamResolver
import com.rosyidputrar.taskify.core.media.youtube.MusicLoginEvents
import com.rosyidputrar.taskify.core.media.youtube.StreamFailure
import com.rosyidputrar.taskify.core.media.youtube.StreamFailureException
import com.rosyidputrar.taskify.core.media.youtube.YouTubeAuthStore
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regresi item 5 (lompat ke lagu yang sedang di-prefetch tidak boleh resolve ganda)
 * dan item 9 (poll prefetch dibatalkan saat lagu berubah), kini atas API direct.
 *
 * `YouTube` + `DirectStreamResolver` dipalsukan (subclass, pola lama `FakeMusicClient`):
 * tiap resolve menahan di gate sampai tes melepasnya, jadi jumlah panggilan bisa
 * dihitung persis. `track()`/`resolve()` memakai `withTimeoutOrNull` yang tidak
 * jalan di bawah `runTest` (pelajaran Tahap 2/3) — fake bergate adalah cara
 * deterministik menguji orkestrasi efek pemutar. Integrasi HTTP-nya sendiri
 * sudah diuji di `YouTubeFacadeTest` + `DirectStreamResolverTest` (MockEngine).
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class MusicPlayerTest {
    private fun song(id: String) = Song(id.padEnd(11, 'x'), "Lagu $id", "Artis", "3.05", emptyList())
    private val a = song("a"); private val b = song("b"); private val c = song("c")

    private class FakeYouTube : YouTube() {
        val trackCalls = mutableListOf<String>()
        var trackResult: (String) -> Result<Track> = { Result.success(Track(it, "", emptyList())) }
        /** Gate per lagu: bila ada, `track()` menahan di sini (untuk mengamati state sebelum keterangan datang). */
        val trackGates = mutableMapOf<String, CompletableDeferred<Result<Track>>>()
        fun gateTrack(id: String) = trackGates.getOrPut(id) { CompletableDeferred() }
        var lastAuthState: PlaybackAuthState? = null

        override suspend fun track(videoId: String, authState: PlaybackAuthState): Result<Track> {
            trackCalls += videoId
            lastAuthState = authState
            return trackGates[videoId]?.await() ?: trackResult(videoId)
        }

        val radioCalls = mutableListOf<String>()
        var radioResult: (String) -> Result<RadioPageResult> = { Result.success(RadioPageResult(emptyList(), "", "", null)) }
        override suspend fun radioMore(cursor: String, authState: PlaybackAuthState): Result<RadioPageResult> {
            radioCalls += cursor
            lastAuthState = authState
            return radioResult(cursor)
        }

        override suspend fun lyrics(lyricsId: String, authState: PlaybackAuthState): Result<String> = Result.success("")
    }

    private class FakeResolver : DirectStreamResolver() {
        val calls = mutableMapOf<String, Int>()
        val qualities = mutableMapOf<String, MusicQuality>()
        val preferMp4aFlags = mutableMapOf<String, Boolean>()
        val cancelled = mutableSetOf<String>()
        val gates = mutableMapOf<String, CompletableDeferred<DirectStream>>()
        fun gate(id: String) = gates.getOrPut(id) { CompletableDeferred() }
        /** Bila diisi, `resolve()` gagal langsung dengan kegagalan ini (tanpa menunggu gate). */
        var resolveFailure: ((String) -> StreamFailure?)? = null
        var lastAuthState: PlaybackAuthState? = null

        override suspend fun resolve(videoId: String, quality: MusicQuality, authState: PlaybackAuthState, preferMp4a: Boolean): Result<DirectStream> {
            calls[videoId] = (calls[videoId] ?: 0) + 1
            qualities[videoId] = quality
            preferMp4aFlags[videoId] = preferMp4a
            lastAuthState = authState
            resolveFailure?.invoke(videoId)?.let { return Result.failure(StreamFailureException(it)) }
            return try {
                Result.success(gate(videoId).await())
            } catch (e: CancellationException) {
                cancelled += videoId
                throw e
            }
        }

        override suspend fun tryResolve(videoId: String, quality: MusicQuality, authState: PlaybackAuthState, preferMp4a: Boolean): DirectStream? {
            calls[videoId] = (calls[videoId] ?: 0) + 1
            qualities[videoId] = quality
            preferMp4aFlags[videoId] = preferMp4a
            lastAuthState = authState
            return try {
                gate(videoId).await()
            } catch (e: CancellationException) {
                cancelled += videoId
                throw e
            }
        }
    }

    /** SponsorBlock tanpa jaringan: 404 → tanpa segmen (perilaku repository). */
    private fun sponsorEmpty() = SponsorBlockRepository(
        HttpClient(MockEngine { respond("[]", HttpStatusCode.NotFound, headersOf()) }) { expectSuccess = false },
    )

    private fun player(tube: FakeYouTube, fake: FakeResolver, authStore: YouTubeAuthStore? = null, loginEvents: MusicLoginEvents? = null): MusicPlayer {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return MusicPlayer(context, tube, fake, sponsorEmpty(), authStore ?: YouTubeAuthStore(context), loginEvents = loginEvents)
    }

    /** Cookie login dummy + dataSyncId + akun, supaya state terhitung login (PLAN C.6). */
    private fun seedLogin(authStore: YouTubeAuthStore) {
        authStore.saveAuthState(PlaybackAuthState(cookie = "SAPISID=dummy; LOGIN_INFO=dummy", dataSyncId = "delegated||user"))
        authStore.saveAccount(AccountInfo("Tes Akun", "tes@example.com"))
    }

    private fun stream(id: String) = DirectStream(
        url = "https://rr.test/$id", mimeType = "audio/webm; codecs=opus", itag = 251,
        bitrate = 169643, expiresInSeconds = 21540, loudnessDb = 3.77, perceptualLoudnessDb = null,
    )

    @Test fun jumpToPrefetchingSongDoesNotResolveTwice() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val tube = FakeYouTube()
            val fake = FakeResolver()
            val player = player(tube, fake)
            player.playQueue(listOf(a, b), 0)
            advanceUntilIdle()
            assertEquals(1, fake.calls[a.videoId], "resolve lagu pertama jalan sekali")
            assertEquals(null, fake.calls[b.videoId])
            assertEquals(listOf(a.videoId), tube.trackCalls, "keterangan lagu pertama diambil lewat track()")

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
            fake.gate(b.videoId).complete(stream(b.videoId))
            advanceUntilIdle()
            assertEquals("https://rr.test/${b.videoId}", player.state.value.urls[b.videoId])
            assertEquals(1, fake.calls[b.videoId])
            assertTrue(player.state.value.prefetching.isEmpty())
            assertTrue(player.state.value.seekable, "direct bisa digeser")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun prefetchIsCancelledWhenSongChanges() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val tube = FakeYouTube()
            val fake = FakeResolver()
            val player = player(tube, fake)
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

    @Test fun nextFailureStillPlaysFromPlayerMetadata() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val tube = FakeYouTube()
            val fake = FakeResolver()
            val player = player(tube, fake)
            // `next` gagal (403) → track() jatuh ke metadata player: upNext kosong,
            // lagu itu sendiri tetap diketahui (pola Worker `loadTrack` + perbaikan next-403).
            tube.gateTrack(a.videoId)
            player.playSimilar(a)
            advanceUntilIdle()
            assertEquals(a.videoId, player.state.value.similarSeed, "benih serupa menunggu keterangan")
            assertEquals(1, fake.calls[a.videoId], "stream sudah diminta walau keterangan belum datang")

            tube.gateTrack(a.videoId).complete(Result.success(Track(a.videoId, "", emptyList(), song = a)))
            advanceUntilIdle()
            assertNull(player.state.value.similarSeed, "keterangan datang tanpa upNext → benih dilepas, antrean tetap satu lagu")
            assertEquals(listOf(a), player.state.value.ordered)
            assertTrue(player.state.value.details[a.videoId]?.upNext.isNullOrEmpty())

            // Stream selesai → lagu tetap diputar walau `next` gagal.
            fake.gate(a.videoId).complete(stream(a.videoId))
            advanceUntilIdle()
            assertEquals("https://rr.test/${a.videoId}", player.state.value.urls[a.videoId])
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun allClientsFailShowsAClearError() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val tube = FakeYouTube()
            tube.trackResult = { Result.failure(NoSuchElementException("track tidak ditemukan")) }
            val fake = FakeResolver()
            // Semua klien InnerTube menolak: tidak ada metadata, tidak ada stream.
            fake.resolveFailure = { StreamFailure.NoFormat }
            val player = player(tube, fake)
            player.playQueue(listOf(a), 0)
            advanceUntilIdle()
            assertEquals(YtStatus.Error, player.state.value.status)
            assertEquals("Audio tidak bisa dimuat dari YouTube (format tidak tersedia).", player.state.value.error)
            assertNull(player.state.value.urls[a.videoId])
            assertTrue(player.state.value.details[a.videoId]?.upNext.isNullOrEmpty(), "keterangan gagal tetap dicatat supaya tidak diulang terus")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun qualitySettingReachesTheResolverAndPersists() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val tube = FakeYouTube()
            val fake = FakeResolver()
            val player = player(tube, fake)
            player.updateSettings { it.copy(quality = MusicQuality.Low) }
            player.playQueue(listOf(a), 0)
            advanceUntilIdle()
            assertEquals(MusicQuality.Low, fake.qualities[a.videoId])
            // Pengaturan tersimpan untuk pemutar berikutnya (proses baru).
            val reborn = player(tube, fake)
            assertEquals(MusicQuality.Low, reborn.settings.value.quality)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun preferMp4aSettingReachesTheResolverAndPersists() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val tube = FakeYouTube()
            val fake = FakeResolver()
            val player = player(tube, fake)
            player.updateSettings { it.copy(preferMp4a = true) }
            player.playQueue(listOf(a), 0)
            advanceUntilIdle()
            assertEquals(true, fake.preferMp4aFlags[a.videoId], "toggle MP4A diteruskan ke resolver")
            // Pengaturan tersimpan untuk pemutar berikutnya (proses baru).
            val reborn = player(tube, fake)
            assertEquals(true, reborn.settings.value.preferMp4a)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun resolvePassesStoredAuthStateToBothClients() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val authStore = YouTubeAuthStore(context)
            authStore.clear()
            authStore.saveVisitorData("CgthYXV0aC10ZXN0")
            val tube = FakeYouTube()
            val fake = FakeResolver()
            val player = MusicPlayer(context, tube, fake, sponsorEmpty(), authStore)
            player.playQueue(listOf(a), 0)
            advanceUntilIdle()
            // Gap Tahap 5: resolver punya InnerTube sendiri — state auth harus
            // diteruskan eksplisit, bukan mengandalkan state internal transport.
            assertEquals("CgthYXV0aC10ZXN0", tube.lastAuthState?.visitorData, "track() menerima visitorData tersimpan")
            assertEquals("CgthYXV0aC10ZXN0", fake.lastAuthState?.visitorData, "resolve() menerima visitorData tersimpan")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun queueSurvivesProcessDeathPausedAndWipeForgetsIt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val authStore = YouTubeAuthStore(context)
            authStore.clear()
            // Gate login (PLAN C.6): antrean hanya dipulihkan bila login,
            // jadi tes pemulihan ini memakai sesi login dummy.
            seedLogin(authStore)
            val tube = FakeYouTube()
            val fake = FakeResolver()
            val player = player(tube, fake, authStore)
            player.playQueue(listOf(a, b, c), 1)
            advanceUntilIdle()
            val reborn = player(tube, fake, authStore)
            assertEquals(YtStatus.Paused, reborn.state.value.status, "dipulihkan dalam keadaan jeda, tidak langsung berbunyi")
            assertEquals(b, reborn.state.value.current)
            assertEquals(listOf(a, b, c), reborn.state.value.ordered)
            assertTrue(reborn.state.value.urls.isEmpty())

            reborn.wipe()
            advanceUntilIdle()
            val afterLogout = player(tube, fake, authStore)
            assertEquals(YtStatus.Idle, afterLogout.state.value.status, "logout menghapus antrean tersimpan")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun queueIsNotRestoredWithoutLogin() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val tube = FakeYouTube()
            val fake = FakeResolver()
            val player = player(tube, fake)
            player.playQueue(listOf(a, b, c), 1)
            advanceUntilIdle()
            // Tanpa sesi login, pemutar baru mulai kosong walau antrean tersimpan.
            val reborn = player(tube, fake)
            assertEquals(YtStatus.Idle, reborn.state.value.status, "tanpa login antrean tidak dipulihkan")
            assertTrue(reborn.state.value.ordered.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun loginRequiredWhileLoggedInExpiresSession() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val authStore = YouTubeAuthStore(context)
            authStore.clear()
            seedLogin(authStore)
            var expiredCalls = 0
            val tube = FakeYouTube()
            val fake = FakeResolver()
            fake.resolveFailure = { StreamFailure.LoginRequired() }
            val player = player(tube, fake, authStore, loginEvents = MusicLoginEvents { expiredCalls++ })
            player.playQueue(listOf(a), 0)
            advanceUntilIdle()
            assertEquals(YtStatus.Error, player.state.value.status)
            assertEquals("Sesi login YouTube berakhir. Masuk lagi.", player.state.value.error)
            assertEquals(1, expiredCalls, "hook onSessionExpired dipanggil sekali")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun loginRequiredWithoutLoginKeepsOldMessage() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            var expiredCalls = 0
            val tube = FakeYouTube()
            val fake = FakeResolver()
            fake.resolveFailure = { StreamFailure.LoginRequired() }
            val player = player(tube, fake, loginEvents = MusicLoginEvents { expiredCalls++ })
            player.playQueue(listOf(a), 0)
            advanceUntilIdle()
            assertEquals(YtStatus.Error, player.state.value.status)
            assertEquals("Audio tidak bisa dimuat dari YouTube (login diperlukan).", player.state.value.error)
            assertEquals(0, expiredCalls, "tanpa cookie login hook tidak dipanggil")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun radioQueueFetchesNextPageNearTheEnd() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val d = song("d")
            val cursors = mutableListOf<String>()
            val tube = FakeYouTube()
            tube.radioResult = { cursor ->
                cursors += cursor
                Result.success(RadioPageResult(listOf(c, d), "RDAMVMa", a.videoId, null))
            }
            val fake = FakeResolver() // gate lagu pertama tidak pernah selesai → audio tidak pernah siap
            val player = player(tube, fake)
            player.playQueue(listOf(a, b), 0, RadioSource("RDAMVMa", "cur-1"))
            player.dispatch(YtEvent.Playing)
            advanceUntilIdle()
            assertEquals(listOf("cur-1"), cursors, "dua lagu tersisa → ambil halaman radio berikutnya")
            assertEquals(listOf(a, b, c, d), player.state.value.ordered)
            assertEquals(null, player.state.value.radio!!.cursor)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
