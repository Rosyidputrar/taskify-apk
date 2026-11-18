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
import com.rosyidputrar.taskify.core.media.youtube.FailedStreamClients
import com.rosyidputrar.taskify.core.media.youtube.MusicLoginEvents
import com.rosyidputrar.taskify.core.media.youtube.StreamFailure
import com.rosyidputrar.taskify.core.media.youtube.StreamFailureException
import com.rosyidputrar.taskify.core.media.youtube.YoutubeiStreamResolver
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
import kotlin.test.assertFalse
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
        val pinnedItags = mutableMapOf<String, Int?>()
        val networkMeteredFlags = mutableMapOf<String, Boolean>()
        val cancelled = mutableSetOf<String>()
        val gates = mutableMapOf<String, CompletableDeferred<DirectStream>>()
        fun gate(id: String) = gates.getOrPut(id) { CompletableDeferred() }
        /** Bila diisi, `resolve()` gagal langsung dengan kegagalan ini (tanpa menunggu gate). */
        var resolveFailure: ((String) -> StreamFailure?)? = null
        /** Bila diisi, `resolve()`/`tryResolve()` mengembalikan stream ini (per panggilan ke-n) tanpa gate. */
        var streamSupplier: ((String, Int) -> DirectStream)? = null
        var lastAuthState: PlaybackAuthState? = null

        override suspend fun resolve(videoId: String, quality: MusicQuality, authState: PlaybackAuthState, preferMp4a: Boolean, pinnedItag: Int?, networkMetered: Boolean): Result<DirectStream> {
            calls[videoId] = (calls[videoId] ?: 0) + 1
            qualities[videoId] = quality
            preferMp4aFlags[videoId] = preferMp4a
            pinnedItags[videoId] = pinnedItag
            networkMeteredFlags[videoId] = networkMetered
            lastAuthState = authState
            resolveFailure?.invoke(videoId)?.let { return Result.failure(StreamFailureException(it)) }
            streamSupplier?.invoke(videoId, calls[videoId] ?: 0)?.let { return Result.success(it) }
            return try {
                Result.success(gate(videoId).await())
            } catch (e: CancellationException) {
                cancelled += videoId
                throw e
            }
        }

        override suspend fun tryResolve(videoId: String, quality: MusicQuality, authState: PlaybackAuthState, preferMp4a: Boolean, pinnedItag: Int?, networkMetered: Boolean): DirectStream? {
            calls[videoId] = (calls[videoId] ?: 0) + 1
            qualities[videoId] = quality
            preferMp4aFlags[videoId] = preferMp4a
            pinnedItags[videoId] = pinnedItag
            networkMeteredFlags[videoId] = networkMetered
            lastAuthState = authState
            streamSupplier?.invoke(videoId, calls[videoId] ?: 0)?.let { return it }
            return try {
                gate(videoId).await()
            } catch (e: CancellationException) {
                cancelled += videoId
                throw e
            }
        }
    }

    /** youtubei.js palsu (Tahap 4): tanpa QuickJS — `resolve` di-override, resolver asli tidak pernah dibuat. */
    private class FakeYoutubei(context: android.content.Context) : YoutubeiStreamResolver(context) {
        val calls = mutableListOf<String>()
        var result: ((String) -> Result<DirectStream>)? = null

        override suspend fun resolve(
            videoId: String,
            quality: MusicQuality,
            authState: PlaybackAuthState,
            preferMp4a: Boolean,
            pinnedItag: Int?,
            networkMetered: Boolean,
        ): Result<DirectStream> {
            calls += videoId
            return result?.invoke(videoId)
                ?: Result.failure(StreamFailureException(StreamFailure.NoFormat))
        }
    }

    /** SponsorBlock tanpa jaringan: 404 → tanpa segmen (perilaku repository). */
    private fun sponsorEmpty() = SponsorBlockRepository(
        HttpClient(MockEngine { respond("[]", HttpStatusCode.NotFound, headersOf()) }) { expectSuccess = false },
    )

    private fun player(
        tube: FakeYouTube,
        fake: FakeResolver,
        authStore: YouTubeAuthStore? = null,
        loginEvents: MusicLoginEvents? = null,
        youtubei: YoutubeiStreamResolver? = null,
        failedStreamClients: FailedStreamClients? = null,
        refreshVisitorData: (suspend () -> String?)? = null,
        isNetworkMetered: () -> Boolean = { false },
        registerPlayback: (suspend (String, String, PlaybackAuthState) -> Boolean)? = null,
    ): MusicPlayer {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return MusicPlayer(
            context, tube, fake, sponsorEmpty(), authStore ?: YouTubeAuthStore(context),
            loginEvents = loginEvents, youtubeiResolver = youtubei,
            failedStreamClients = failedStreamClients ?: FailedStreamClients(),
            refreshVisitorData = refreshVisitorData,
            isNetworkMetered = isNetworkMetered,
            registerPlayback = registerPlayback,
        )
    }

    /** Cookie login dummy + dataSyncId + akun, supaya state terhitung login (PLAN C.6). */
    private fun seedLogin(authStore: YouTubeAuthStore) {
        authStore.saveAuthState(PlaybackAuthState(cookie = "SAPISID=dummy; LOGIN_INFO=dummy", dataSyncId = "delegated||user"))
        authStore.saveAccount(AccountInfo("Tes Akun", "tes@example.com"))
    }

    private fun stream(id: String, url: String = "https://rr.test/$id", client: String? = null, expiresInSeconds: Int = 21540, itag: Int = 251, trackingUrl: String? = null) = DirectStream(
        url = url, mimeType = "audio/webm; codecs=opus", itag = itag,
        bitrate = 169643, expiresInSeconds = expiresInSeconds, loudnessDb = 3.77, perceptualLoudnessDb = null,
        resolvedClient = client, playbackTrackingUrl = trackingUrl,
    )

    /** Galat ExoPlayer HTTP 403 googlevideo, seperti yang dilihat sir di HP (`ERROR_CODE_IO_BAD_HTTP_STATUS`, 2004). */
    private fun http403() = androidx.media3.common.PlaybackException(
        "Response code: 403",
        androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException(
            403, "Forbidden", null, emptyMap(),
            androidx.media3.datasource.DataSpec(android.net.Uri.parse("https://rr.test/x")), ByteArray(0),
        ),
        androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    )

    /** Galat ExoPlayer HTTP 404 — bukan 403, jadi tidak boleh memicu re-resolve. */
    private fun http404() = androidx.media3.common.PlaybackException(
        "Response code: 404",
        androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException(
            404, "Not Found", null, emptyMap(),
            androidx.media3.datasource.DataSpec(android.net.Uri.parse("https://rr.test/x")), ByteArray(0),
        ),
        androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
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

    @Test fun meteredAutoResolvesAsHigh() = runTest {
        // Tahap 8 (C2-25): jaringan metered + kualitas Auto → resolver menerima High.
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val tube = FakeYouTube()
            val fake = FakeResolver()
            val player = player(tube, fake, isNetworkMetered = { true })
            player.playQueue(listOf(a), 0)
            advanceUntilIdle()
            assertEquals(MusicQuality.High, fake.qualities[a.videoId])
            assertEquals(true, fake.networkMeteredFlags[a.videoId])
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun unmeteredAutoResolvesAsAuto() = runTest {
        // Jaringan biasa + kualitas Auto → resolver menerima Auto, metered false.
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val tube = FakeYouTube()
            val fake = FakeResolver()
            val player = player(tube, fake, isNetworkMetered = { false })
            player.playQueue(listOf(a), 0)
            advanceUntilIdle()
            assertEquals(MusicQuality.Auto, fake.qualities[a.videoId])
            assertEquals(false, fake.networkMeteredFlags[a.videoId])
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun meteredHighestStaysHighest() = runTest {
        // Metered hanya menurunkan Auto; Highest tetap Highest.
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val tube = FakeYouTube()
            val fake = FakeResolver()
            val player = player(tube, fake, isNetworkMetered = { true })
            player.updateSettings { it.copy(quality = MusicQuality.Highest) }
            player.playQueue(listOf(a), 0)
            advanceUntilIdle()
            assertEquals(MusicQuality.Highest, fake.qualities[a.videoId])
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun registerPlaybackOncePerSong() = runTest {
        // Tahap 9 (C2-21): URL tracking dari hasil resolve dikirim sekali
        // per lagu; panggilan kedua untuk lagu yang sama tidak mengulang.
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val tube = FakeYouTube()
            val fake = FakeResolver()
            fake.streamSupplier = { id, _ -> stream(id, trackingUrl = "https://tracking.test/$id") }
            val registered = mutableListOf<Pair<String, String>>()
            val player = player(tube, fake, registerPlayback = { videoId, url, _ ->
                registered += videoId to url
                true
            })
            player.playQueue(listOf(a, b), 0)
            advanceUntilIdle()
            player.registerPlaybackOnce(a.videoId)
            advanceUntilIdle()
            assertEquals(listOf(a.videoId to "https://tracking.test/${a.videoId}"), registered)
            // Panggilan kedua untuk lagu yang sama: tidak mengulang.
            player.registerPlaybackOnce(a.videoId)
            advanceUntilIdle()
            assertEquals(1, registered.size)
            // Lagu berikut: dikirim sekali dengan URL-nya sendiri.
            player.registerPlaybackOnce(b.videoId)
            advanceUntilIdle()
            assertEquals(2, registered.size)
            assertEquals(b.videoId to "https://tracking.test/${b.videoId}", registered[1])
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun registerPlaybackFailureDropsUrlSoNextAttemptRetries() = runTest {
        // Gagal = URL dibuang; pemanggilan berikut (setelah resolve ulang
        // memberi URL baru) mencoba lagi, bukan diam selamanya.
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val tube = FakeYouTube()
            val fake = FakeResolver()
            fake.streamSupplier = { id, _ -> stream(id, trackingUrl = "https://tracking.test/$id") }
            var calls = 0
            val player = player(tube, fake, registerPlayback = { _, _, _ ->
                calls++
                false
            })
            player.playQueue(listOf(a), 0)
            advanceUntilIdle()
            player.registerPlaybackOnce(a.videoId)
            advanceUntilIdle()
            assertEquals(1, calls)
            // URL sudah dibuang: panggilan kedua tanpa resolve ulang tidak mencoba lagi.
            player.registerPlaybackOnce(a.videoId)
            advanceUntilIdle()
            assertEquals(1, calls)
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

    @Test fun playback403ReResolvesOnceThenPlaysAgainWithFreshUrl() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val tube = FakeYouTube()
            val fake = FakeResolver()
            val url1 = "https://rr.test/${a.videoId}?expire=1793000000&ip=203.0.113.7&sig=S1"
            val url2 = "https://rr.test/${a.videoId}?expire=1793000000&ip=203.0.113.7&sig=S2"
            fake.streamSupplier = { _, call ->
                stream(a.videoId, if (call <= 1) url1 else url2, "ANDROID")
            }
            val player = player(tube, fake)
            player.playQueue(listOf(a), 0)
            advanceUntilIdle()
            assertEquals(url1, player.state.value.urls[a.videoId])

            // 403 dari ExoPlayer → re-resolve sekali, lagu diputar lagi dengan URL segar.
            player.handlePlayerError(http403())
            advanceUntilIdle()
            assertEquals(2, fake.calls[a.videoId], "resolve awal + satu re-resolve 403")
            assertEquals(url2, player.state.value.urls[a.videoId], "URL segar dipakai setelah 403")
            assertTrue(player.state.value.status != YtStatus.Error, "belum jadi galat setelah re-resolve berhasil")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun secondPlayback403ShowsRawErrorWithoutLoop() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val tube = FakeYouTube()
            val fake = FakeResolver()
            val url1 = "https://rr.test/${a.videoId}?expire=1793000000&ip=203.0.113.7&sig=S1"
            val url2 = "https://rr.test/${a.videoId}?expire=1793000000&ip=203.0.113.7&sig=S2"
            fake.streamSupplier = { _, call ->
                stream(a.videoId, if (call <= 1) url1 else url2, "ANDROID")
            }
            val player = player(tube, fake)
            player.playQueue(listOf(a), 0)
            advanceUntilIdle()
            player.handlePlayerError(http403())
            advanceUntilIdle()
            assertEquals(2, fake.calls[a.videoId])
            assertEquals(url2, player.state.value.urls[a.videoId])

            // Percobaan kedua tetap 403 → galat mentah, TIDAK ada re-resolve ketiga.
            player.handlePlayerError(http403())
            advanceUntilIdle()
            assertEquals(2, fake.calls[a.videoId], "tidak ada loop re-resolve")
            assertEquals(YtStatus.Error, player.state.value.status)
            assertEquals("Audio gagal diputar. Coba lagi.", player.state.value.error)
            val detail = player.state.value.errorDetail ?: ""
            assertTrue(detail.contains("errorCode: 2004"), detail)
            assertTrue(detail.contains("klien: ANDROID"), detail)
            assertTrue(detail.contains("paramUrl: n: tidak ada, pot: tidak ada, sig: ada, expire: ada, ip: ada"), detail)
            assertTrue(detail.contains("expiresInSeconds: 21540"), detail)
            assertFalse(detail.contains("S2"), "nilai signature tidak boleh bocor ke detail")
            assertFalse(detail.contains("203.0.113.7"), "nilai IP tidak boleh bocor ke detail")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun nonRetryablePlaybackErrorDoesNotReResolve() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val tube = FakeYouTube()
            val fake = FakeResolver()
            fake.streamSupplier = { _, _ -> stream(a.videoId, "https://rr.test/${a.videoId}", "ANDROID") }
            val player = player(tube, fake)
            player.playQueue(listOf(a), 0)
            advanceUntilIdle()
            assertEquals(1, fake.calls[a.videoId])

            // Galat jaringan biasa (IOException generik, bukan timeout/koneksi)
            // → langsung galat, tanpa re-resolve.
            player.handlePlayerError(
                androidx.media3.common.PlaybackException(
                    "Gagal tersambung.",
                    java.io.IOException("putus"),
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                ),
            )
            advanceUntilIdle()
            assertEquals(1, fake.calls[a.videoId], "galat non-retryable tidak memicu re-resolve")
            assertEquals(YtStatus.Error, player.state.value.status)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun http404ReResolvesOnceLike403() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val tube = FakeYouTube()
            val fake = FakeResolver()
            fake.streamSupplier = { _, _ -> stream(a.videoId, "https://rr.test/${a.videoId}", "ANDROID") }
            val player = player(tube, fake)
            player.playQueue(listOf(a), 0)
            advanceUntilIdle()
            assertEquals(1, fake.calls[a.videoId])

            // HTTP 404 dari stream (Tahap 7, C2-19 — ArchiveTune
            // RETRYABLE_STREAM_RESPONSE_CODES): URL mati → re-resolve sekali.
            player.handlePlayerError(http404())
            advanceUntilIdle()
            assertEquals(2, fake.calls[a.videoId], "404 memicu satu re-resolve")
            assertTrue(player.state.value.status != YtStatus.Error, "belum jadi galat setelah re-resolve 404")

            // Percobaan kedua tetap 404 → galat, tanpa loop.
            player.handlePlayerError(http404())
            advanceUntilIdle()
            assertEquals(2, fake.calls[a.videoId], "tidak ada loop re-resolve 404")
            assertEquals(YtStatus.Error, player.state.value.status)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun http403BlacklistsFailedClient() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val tube = FakeYouTube()
            val fake = FakeResolver()
            val blacklist = FailedStreamClients()
            val fingerprint = PlaybackAuthState().streamCacheFingerprint
            // URL segar (expire 2030) dengan param c/cver ANDROID.
            fake.streamSupplier = { _, _ ->
                stream(a.videoId, "https://rr.test/${a.videoId}?c=ANDROID&cver=21.26.364&expire=1893000000", "ANDROID")
            }
            val player = player(tube, fake, failedStreamClients = blacklist)
            player.playQueue(listOf(a), 0)
            advanceUntilIdle()
            assertFalse(blacklist.isBlocked(a.videoId, "ANDROID@21.26.364", fingerprint))

            player.handlePlayerError(http403())
            advanceUntilIdle()
            assertEquals(2, fake.calls[a.videoId], "403 memicu re-resolve")
            assertTrue(
                blacklist.isBlocked(a.videoId, "ANDROID@21.26.364", fingerprint),
                "klien ANDROID yang memberi URL 403 di-blacklist 10 menit",
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun http403DoesNotBlacklistNearExpiredUrl() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val tube = FakeYouTube()
            val fake = FakeResolver()
            val blacklist = FailedStreamClients()
            val fingerprint = PlaybackAuthState().streamCacheFingerprint
            // URL sudah kedaluwarsa (expire 2026-10): 403-nya soal umur URL.
            fake.streamSupplier = { _, _ ->
                stream(a.videoId, "https://rr.test/${a.videoId}?c=ANDROID&cver=21.26.364&expire=1793000000", "ANDROID")
            }
            val player = player(tube, fake, failedStreamClients = blacklist)
            player.playQueue(listOf(a), 0)
            advanceUntilIdle()
            player.handlePlayerError(http403())
            advanceUntilIdle()
            assertEquals(2, fake.calls[a.videoId], "403 URL kedaluwarsa tetap re-resolve")
            assertFalse(
                blacklist.isBlocked(a.videoId, "ANDROID@21.26.364", fingerprint),
                "URL hampir kedaluwarsa tidak mem-blacklist kliennya",
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun recoveryKeepsPositionOnNextPlay() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val tube = FakeYouTube()
            val fake = FakeResolver()
            val url1 = "https://rr.test/${a.videoId}?expire=1893000000&sig=S1"
            val url2 = "https://rr.test/${a.videoId}?expire=1893000000&sig=S2"
            fake.streamSupplier = { _, call -> stream(a.videoId, if (call <= 1) url1 else url2, "ANDROID") }
            val player = player(tube, fake)
            player.playQueue(listOf(a), 0)
            advanceUntilIdle()
            player.player.seekTo(45_000)
            advanceUntilIdle()
            assertEquals(45_000L, player.player.currentPosition, "seek sebelum galat tercatat")

            player.handlePlayerError(http403())
            advanceUntilIdle()
            assertEquals(url2, player.state.value.urls[a.videoId], "URL segar di-stage setelah 403")

            // Tekan putar lagi (Preparing) → URL baru dimuat dari posisi tersimpan.
            player.dispatch(YtEvent.Preparing)
            advanceUntilIdle()
            assertEquals(45_000L, player.player.currentPosition, "posisi dipertahankan setelah re-resolve")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun transientNetworkFailureReResolvesOnce() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val tube = FakeYouTube()
            val fake = FakeResolver()
            fake.streamSupplier = { _, _ -> stream(a.videoId, "https://rr.test/${a.videoId}", "ANDROID") }
            val player = player(tube, fake)
            player.playQueue(listOf(a), 0)
            advanceUntilIdle()

            val networkError = androidx.media3.common.PlaybackException(
                "Unable to connect.",
                java.net.ConnectException("gagal tersambung"),
                androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            )
            player.handlePlayerError(networkError)
            advanceUntilIdle()
            assertEquals(2, fake.calls[a.videoId], "koneksi putus memicu satu re-resolve")
            assertTrue(player.state.value.status != YtStatus.Error)

            player.handlePlayerError(networkError)
            advanceUntilIdle()
            assertEquals(2, fake.calls[a.videoId], "tidak ada loop re-resolve jaringan")
            assertEquals(YtStatus.Error, player.state.value.status)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun parserFailureReResolvesOnce() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val tube = FakeYouTube()
            val fake = FakeResolver()
            fake.streamSupplier = { _, _ -> stream(a.videoId, "https://rr.test/${a.videoId}", "ANDROID") }
            val player = player(tube, fake)
            player.playQueue(listOf(a), 0)
            advanceUntilIdle()

            player.handlePlayerError(
                androidx.media3.common.PlaybackException(
                    "Container malformed.",
                    null,
                    androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                ),
            )
            advanceUntilIdle()
            assertEquals(2, fake.calls[a.videoId], "galat parser memicu satu re-resolve")
            assertTrue(player.state.value.status != YtStatus.Error)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun botDetectionReResolvesOnce() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val tube = FakeYouTube()
            val fake = FakeResolver()
            fake.streamSupplier = { _, _ -> stream(a.videoId, "https://rr.test/${a.videoId}", "ANDROID") }
            val player = player(tube, fake)
            player.playQueue(listOf(a), 0)
            advanceUntilIdle()

            player.handlePlayerError(
                androidx.media3.common.PlaybackException(
                    "Please verify you are not a robot.",
                    null,
                    androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                ),
            )
            advanceUntilIdle()
            assertEquals(2, fake.calls[a.videoId], "bot detection memicu satu re-resolve")
            assertTrue(player.state.value.status != YtStatus.Error)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun allClientsFailedRefreshesSessionAndRetriesOnce() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val tube = FakeYouTube()
            val fake = FakeResolver()
            var refreshCalls = 0
            fake.streamSupplier = { _, _ -> stream(a.videoId, "https://rr.test/${a.videoId}", "ANDROID") }
            // Panggilan pertama sukses; re-resolve selalu AllClientsFailed.
            fake.resolveFailure = {
                if ((fake.calls[a.videoId] ?: 0) <= 1) null
                else StreamFailure.AllClientsFailed(setOf("ANDROID@21.26.364"))
            }
            val player = player(tube, fake, refreshVisitorData = { refreshCalls++; "visitor-baru" })
            player.playQueue(listOf(a), 0)
            advanceUntilIdle()
            assertEquals(1, fake.calls[a.videoId])

            player.handlePlayerError(http403())
            advanceUntilIdle()
            assertEquals(3, fake.calls[a.videoId], "re-resolve + satu percobaan sesudah segarkan sesi")
            assertEquals(1, refreshCalls, "sesi disegarkan sekali saat semua klien gagal")
            assertEquals(YtStatus.Error, player.state.value.status, "dua-duanya gagal → galat mentah asal")
            assertEquals("Audio gagal diputar. Coba lagi.", player.state.value.error)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun initialAllClientsFailedShowsTaxonomyMessage() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val tube = FakeYouTube()
            val fake = FakeResolver()
            fake.resolveFailure = { StreamFailure.AllClientsFailed(setOf("ANDROID@21.26.364", "WEB_CREATOR@1.0")) }
            val player = player(tube, fake)
            player.playQueue(listOf(a), 0)
            advanceUntilIdle()
            assertEquals(YtStatus.Error, player.state.value.status)
            assertEquals("Semua klien YouTube gagal memuat audio. Coba lagi sebentar lagi.", player.state.value.error)
            val detail = player.state.value.errorDetail ?: ""
            assertTrue(detail.contains("AllClientsFailed"), detail)
            assertTrue(detail.contains("ANDROID@21.26.364"), detail)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun botDetectionMessageClassifier() {
        assertTrue(isBotDetectionMessage("Please verify you are not a robot"))
        assertTrue(isBotDetectionMessage("Unusual traffic from your computer network"))
        assertTrue(isBotDetectionMessage("Automated requests are blocked"))
        assertTrue(isBotDetectionMessage("Confirm you are not a bot"))
        assertFalse(isBotDetectionMessage("Response code: 403"))
        assertFalse(isBotDetectionMessage("Gagal tersambung."))
        assertFalse(isBotDetectionMessage(""))
    }

    @Test fun playback403RetryAllowanceResetsOnNewPlayAttempt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val tube = FakeYouTube()
            val fake = FakeResolver()
            fake.streamSupplier = { _, call ->
                stream(a.videoId, "https://rr.test/${a.videoId}?expire=1793000000&sig=S$call", "ANDROID")
            }
            val player = player(tube, fake)
            player.playQueue(listOf(a), 0)
            advanceUntilIdle()
            player.handlePlayerError(http403())
            advanceUntilIdle()
            assertEquals(2, fake.calls[a.videoId])
            player.handlePlayerError(http403())
            advanceUntilIdle()
            assertEquals(YtStatus.Error, player.state.value.status, "jatah sekali habis → galat")

            // Pengguna menekan putar lagi = percobaan putar baru → jatah re-resolve kembali segar.
            // Tahap 6: URL hasil re-resolve masuk cache, jadi percobaan baru
            // memakai cache (tanpa panggilan jaringan) dan tetap bukan galat.
            player.togglePlay()
            advanceUntilIdle()
            assertEquals(2, fake.calls[a.videoId], "percobaan baru memakai cache, bukan resolve ulang")
            assertTrue(player.state.value.status != YtStatus.Error, "percobaan baru tidak langsung galat")
            player.handlePlayerError(http403())
            advanceUntilIdle()
            assertEquals(3, fake.calls[a.videoId], "percobaan baru mendapat satu re-resolve lagi")
            assertTrue(player.state.value.status != YtStatus.Error, "re-resolve percobaan baru berhasil")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun youtubeiSuksesDipakaiDirectTidakDipanggil() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val tube = FakeYouTube()
            val fake = FakeResolver()
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val youtubei = FakeYoutubei(context)
            // URL hasil decipher youtubei.js: `n` sudah di-decipher, ada
            // `sig` + `pot` (bentuk yang TIDAK bisa dihasilkan jalur direct
            // untuk format cipher-only).
            youtubei.result = { id ->
                Result.success(
                    stream(id, "https://rr.test/$id?expire=1793000000&n=deciphered&sig=abc&pot=tok123", "MWEB"),
                )
            }
            val player = player(tube, fake, youtubei = youtubei)
            player.playQueue(listOf(a), 0)
            advanceUntilIdle()
            assertEquals(listOf(a.videoId), youtubei.calls, "youtubei dicoba dulu")
            assertEquals(null, fake.calls[a.videoId], "direct tidak dipanggil bila youtubei sukses")
            assertEquals(
                "https://rr.test/${a.videoId}?expire=1793000000&n=deciphered&sig=abc&pot=tok123",
                player.state.value.urls[a.videoId],
            )
            assertTrue(player.state.value.seekable, "direct bisa digeser")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun youtubeiGagalJatuhKeDirect() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val tube = FakeYouTube()
            val fake = FakeResolver()
            fake.streamSupplier = { id, _ -> stream(id, "https://rr.test/$id", "ANDROID") }
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val youtubei = FakeYoutubei(context)
            youtubei.result = { Result.failure(StreamFailureException(StreamFailure.NoFormat)) }
            val player = player(tube, fake, youtubei = youtubei)
            player.playQueue(listOf(a), 0)
            advanceUntilIdle()
            assertEquals(listOf(a.videoId), youtubei.calls, "youtubei dicoba dulu")
            assertEquals(1, fake.calls[a.videoId], "gagal youtubei -> fallback direct sekali")
            assertEquals("https://rr.test/${a.videoId}", player.state.value.urls[a.videoId])
            assertTrue(player.state.value.status != YtStatus.Error, "fallback direct berhasil → bukan galat")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun resolveCacheDipakaiSaatUrlKedaluwarsa() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val tube = FakeYouTube()
            val fake = FakeResolver()
            // URL berumur 400 detik: masuk cache (TTL 100 detik), tapi
            // Expire(now+200 detik) menganggapnya kedaluwarsa (margin 5 menit).
            fake.streamSupplier = { id, _ -> stream(id, "https://rr.test/$id", "ANDROID", expiresInSeconds = 400) }
            val player = player(tube, fake)
            player.playQueue(listOf(a), 0)
            advanceUntilIdle()
            assertEquals(1, fake.calls[a.videoId])
            assertEquals("https://rr.test/${a.videoId}", player.state.value.urls[a.videoId])

            // URL kedaluwarsa di state -> resolve ulang, tapi cache masih segar.
            player.dispatch(YtEvent.Expire(System.currentTimeMillis() + 200_000))
            advanceUntilIdle()
            assertEquals(null, player.state.value.urls[a.videoId], "URL kedaluwarsa dibuang dari state")
            player.dispatch(YtEvent.Preparing)
            advanceUntilIdle()
            assertEquals(1, fake.calls[a.videoId], "resolve ulang memakai cache, bukan jaringan")
            assertEquals("https://rr.test/${a.videoId}", player.state.value.urls[a.videoId])
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun gantiSesiMembuatCacheMissDenganPinnedItag() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val tube = FakeYouTube()
            val fake = FakeResolver()
            fake.streamSupplier = { id, _ -> stream(id, "https://rr.test/$id", "ANDROID") }
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val authStore = YouTubeAuthStore(context)
            val player = player(tube, fake, authStore = authStore)
            player.playQueue(listOf(a), 0)
            advanceUntilIdle()
            assertEquals(1, fake.calls[a.videoId])
            assertEquals(null, fake.pinnedItags[a.videoId], "resolve pertama tanpa pinned")

            // Ganti visitorData -> fingerprint auth berubah -> cache miss.
            authStore.saveAuthState(PlaybackAuthState(visitorData = "visitor-baru"))
            // Umur URL 21540 detik: Expire harus lewat 21540 - margin 300 detik.
            player.dispatch(YtEvent.Expire(System.currentTimeMillis() + 22_000_000))
            advanceUntilIdle()
            assertEquals(null, player.state.value.urls[a.videoId], "URL kedaluwarsa dibuang dari state")
            player.dispatch(YtEvent.Preparing)
            advanceUntilIdle()
            assertEquals(2, fake.calls[a.videoId], "ganti sesi = cache miss, resolve ulang")
            assertEquals(251, fake.pinnedItags[a.videoId], "resolve ulang memakai itag terakhir sebagai pinned")
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test fun playback403ReResolveFailureShowsOriginalRawError() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val tube = FakeYouTube()
            val fake = FakeResolver()
            var calls = 0
            fake.streamSupplier = { _, _ ->
                calls++
                // Resolve awal berhasil, re-resolve gagal: supplier melempar
                // di panggilan kedua — tryResolve di pemutar menangkapnya jadi null.
                if (calls == 1) stream(a.videoId, "https://rr.test/${a.videoId}", "ANDROID")
                else throw java.io.IOException("putus")
            }
            val player = player(tube, fake)
            player.playQueue(listOf(a), 0)
            advanceUntilIdle()
            player.handlePlayerError(http403())
            advanceUntilIdle()
            assertEquals(YtStatus.Error, player.state.value.status, "re-resolve gagal → galat, bukan diam")
            assertEquals("Audio gagal diputar. Coba lagi.", player.state.value.error)
            assertTrue((player.state.value.errorDetail ?: "").contains("errorCode: 2004"), "detail mentah asal tetap tampil")
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * Agen C (INVESTIGASI-C Jalur A): galat pemutar KEDUA untuk item yang sama,
     * setelah PlaybackFailed membuang URL/stream dari state — diagnostik
     * percobaan terakhir (WEB_CREATOR) HARUS bertahan, bukan "tidak diketahui".
     * Reproduksi error kedua di HP sir (18 Nov 19:29 WIB, videoId 7Xqon20TlH4).
     */
    @Test fun galatPutarKeduaSetelahStateDibersihkanTetapMembawaDiagnostik() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val tube = FakeYouTube()
            val fake = FakeResolver()
            var calls = 0
            val url1 = "https://rr.test/${a.videoId}?c=WEB_CREATOR&cver=1.0&n=abc&pot=tok&sig=S1&expire=1893000000&ip=203.0.113.7"
            fake.streamSupplier = { _, _ ->
                calls++
                if (calls == 1) stream(a.videoId, url1, "WEB_CREATOR")
                else throw java.io.IOException("putus")
            }
            val player = player(tube, fake)
            player.playQueue(listOf(a), 0)
            advanceUntilIdle()
            player.handlePlayerError(http403())
            advanceUntilIdle()
            assertEquals(YtStatus.Error, player.state.value.status, "re-resolve gagal → galat")
            assertNull(player.state.value.currentStream, "PlaybackFailed membuang stream lagu gagal")
            assertNull(player.state.value.currentUrl, "PlaybackFailed membuang URL lagu gagal")

            // Galat pemutar kedua untuk item yang sama (state sudah bersih).
            player.handlePlayerError(http403())
            advanceUntilIdle()
            assertEquals(YtStatus.Error, player.state.value.status)
            val detail = player.state.value.errorDetail ?: ""
            assertTrue(detail.contains("klien: WEB_CREATOR"), detail)
            assertTrue(detail.contains("paramUrl: n: ada, pot: ada, sig: ada, expire: ada, ip: ada"), detail)
            assertTrue(detail.contains("expiresInSeconds: 21540"), detail)
            assertTrue(detail.contains("format: itag 251 (audio/webm; codecs=opus)"), detail)
            assertTrue(detail.contains("host: rr.test"), detail)
            assertTrue(detail.contains("klienGagal: WEB_CREATOR@"), detail)
            assertFalse(detail.contains("S1"), "nilai signature tidak boleh bocor ke detail")
            assertFalse(detail.contains("203.0.113.7"), "nilai IP tidak boleh bocor ke detail")
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * Agen C (INVESTIGASI-C Jalur B): pengguna menekan putar lagi setelah
     * galat putar, resolve gagal total (AllClientsFailed) — detail akhir
     * HARUS tetap membawa diagnostik percobaan terakhir (WEB_CREATOR),
     * bukan hanya "jenis: AllClientsFailed".
     */
    @Test fun gagalResolveSetelahGalatPutarTetapMembawaDiagnostik() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val tube = FakeYouTube()
            val fake = FakeResolver()
            val url1 = "https://rr.test/${a.videoId}?c=WEB_CREATOR&cver=1.0&n=abc&pot=tok&sig=S1&expire=1893000000&ip=203.0.113.7"
            fake.streamSupplier = { _, _ -> stream(a.videoId, url1, "WEB_CREATOR") }
            // Panggilan pertama sukses; resolve berikutnya selalu AllClientsFailed.
            fake.resolveFailure = {
                if ((fake.calls[a.videoId] ?: 0) <= 1) null
                else StreamFailure.AllClientsFailed(setOf("WEB_CREATOR@1.0"))
            }
            val player = player(tube, fake)
            player.playQueue(listOf(a), 0)
            advanceUntilIdle()
            player.handlePlayerError(http403())
            advanceUntilIdle()
            assertEquals(YtStatus.Error, player.state.value.status, "re-resolve gagal total → galat")

            // Pengguna menekan putar lagi = percobaan baru → resolve gagal total.
            player.togglePlay()
            advanceUntilIdle()
            assertEquals(YtStatus.Error, player.state.value.status)
            assertEquals("Semua klien YouTube gagal memuat audio. Coba lagi sebentar lagi.", player.state.value.error)
            val detail = player.state.value.errorDetail ?: ""
            assertTrue(detail.contains("AllClientsFailed"), detail)
            assertTrue(detail.contains("klien: WEB_CREATOR"), detail)
            assertTrue(detail.contains("paramUrl: n: ada, pot: ada, sig: ada, expire: ada, ip: ada"), detail)
            assertTrue(detail.contains("expiresInSeconds: 21540"), detail)
            assertTrue(detail.contains("format: itag 251 (audio/webm; codecs=opus)"), detail)
            assertTrue(detail.contains("host: rr.test"), detail)
            assertFalse(detail.contains("S1"), "nilai signature tidak boleh bocor ke detail")
            assertFalse(detail.contains("203.0.113.7"), "nilai IP tidak boleh bocor ke detail")
        } finally {
            Dispatchers.resetMain()
        }
    }
}
