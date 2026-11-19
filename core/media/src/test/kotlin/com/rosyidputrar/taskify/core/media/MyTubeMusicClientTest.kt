package com.rosyidputrar.taskify.core.media

import com.rosyidputrar.taskify.core.domain.media.ArtistPage
import com.rosyidputrar.taskify.core.domain.media.CollectionKind
import com.rosyidputrar.taskify.core.domain.media.DiscoverySection
import com.rosyidputrar.taskify.core.domain.media.Lyrics
import com.rosyidputrar.taskify.core.domain.media.MoodCategory
import com.rosyidputrar.taskify.core.domain.media.MoodGroup
import com.rosyidputrar.taskify.core.domain.media.MoodPage
import com.rosyidputrar.taskify.core.domain.media.MusicCollection
import com.rosyidputrar.taskify.core.domain.media.RadioPageResult
import com.rosyidputrar.taskify.core.domain.media.ResolvedLink
import com.rosyidputrar.taskify.core.domain.media.SearchFilter
import com.rosyidputrar.taskify.core.domain.media.SearchResults
import com.rosyidputrar.taskify.core.domain.media.Song
import com.rosyidputrar.taskify.core.domain.media.Suggestion
import com.rosyidputrar.taskify.core.domain.media.Track
import com.rosyidputrar.taskify.core.innertube.PlaybackAuthState
import com.rosyidputrar.taskify.core.innertube.MyTube
import com.rosyidputrar.taskify.core.media.lyrics.LrcLibLyricsProvider
import com.rosyidputrar.taskify.core.media.lyrics.LyricsRepository
import com.rosyidputrar.taskify.core.media.sponsorblock.SponsorBlockRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fasad direct (Tahap 7) — pengganti `MusicClientTest` + `MusicClientUpgradeTest`.
 * `MyTube` dipalsukan (subclass, semua metode katalog `open` sejak Tahap 7);
 * LRCLib + SponsorBlock dipalsukan MockEngine.
 *
 * Tes lama yang spesifik Worker (retry 429 ikut Retry-After, refresh 401
 * Supabase, init/poll konverter, arsip R2, `resolveStream`) TIDAK dibawa ke
 * sini: konsepnya tidak ada lagi di jalur direct. Yang dipertahankan
 * setara: pemetaan galat ke pesan UI (401/403/429/5xx/jaringan) dan
 * perilaku lirik + SponsorBlock.
 */
class MyTubeMusicClientTest {
    private val song = Song("vid12345678", "Lagu Tes", "Artis", "3.05", emptyList(), durationSeconds = 185)
    private val song2 = Song("vid87654321", "Lagu Dua", "Artis", "2.10", emptyList(), durationSeconds = 130)
    private val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    /** `MyTube` palsu: hasil kalengan, atau gagal dengan [failure] bila diisi. */
    private inner class FakeTube : MyTube() {
        var failure: Throwable? = null
        var lastShowTitle: String? = null
        var lyricsResult: Result<String> = Result.success("teks youtube")
        var transcriptResult: Result<String> = Result.failure(NoSuchElementException("tanpa transkrip"))

        private fun <T> canned(value: T): Result<T> = failure?.let { Result.failure(it) } ?: Result.success(value)

        override suspend fun search(query: String, filter: SearchFilter, authState: PlaybackAuthState): Result<SearchResults> =
            canned(SearchResults(songs = listOf(song)))

        override suspend fun searchMore(cursor: String, authState: PlaybackAuthState): Result<SearchResults> =
            canned(SearchResults(songs = listOf(song2), cursor = "c2"))

        override suspend fun suggest(input: String, authState: PlaybackAuthState): Result<List<Suggestion>> =
            canned(listOf(Suggestion("kopi senja", "query")))

        override suspend fun track(videoId: String, authState: PlaybackAuthState): Result<Track> =
            canned(Track(videoId, "MPLYt_test", listOf(song2), song = song))

        override suspend fun album(browseId: String, authState: PlaybackAuthState): Result<MusicCollection> =
            canned(MusicCollection(CollectionKind.Album, browseId, "Album Tes", "Artis · 2024", emptyList(), listOf(song)))

        override suspend fun playlist(browseId: String, authState: PlaybackAuthState): Result<MusicCollection> =
            canned(MusicCollection(CollectionKind.Playlist, browseId, "Playlist Tes", "Pemilik", emptyList(), listOf(song)))

        override suspend fun podcast(browseId: String, authState: PlaybackAuthState): Result<MusicCollection> =
            canned(MusicCollection(CollectionKind.Podcast, browseId, "Acara Tes", "Host", emptyList(), listOf(song)))

        override suspend fun collectionMore(kind: CollectionKind, cursor: String, id: String, showTitle: String, authState: PlaybackAuthState): Result<MusicCollection> {
            lastShowTitle = showTitle
            return canned(MusicCollection(kind, id, "", "", emptyList(), listOf(song2), cursor = null))
        }

        override suspend fun artist(browseId: String, authState: PlaybackAuthState): Result<ArtistPage> =
            canned(ArtistPage(browseId, "Artis", "1 jt", "2 jt", "", emptyList(), listOf(song), null, emptyList(), emptyList(), emptyList()))

        override suspend fun home(authState: PlaybackAuthState): Result<List<DiscoverySection>> =
            canned(listOf(DiscoverySection.Songs("Pilihan cepat", listOf(song))))

        override suspend fun charts(country: String, authState: PlaybackAuthState): Result<List<DiscoverySection>> =
            canned(listOf(DiscoverySection.Songs("Tangga Indonesia", listOf(song))))

        override suspend fun newReleases(authState: PlaybackAuthState): Result<List<DiscoverySection>> =
            canned(listOf(DiscoverySection.Songs("Rilis baru", listOf(song))))

        override suspend fun moods(authState: PlaybackAuthState): Result<List<MoodGroup>> =
            canned(listOf(MoodGroup("Mood & momen", listOf(MoodCategory("Fokus", "p1")))))

        override suspend fun mood(params: String, title: String, authState: PlaybackAuthState): Result<MoodPage> =
            canned(MoodPage(title, params, listOf(DiscoverySection.Songs(title, listOf(song)))))

        override suspend fun next(videoId: String, playlistId: String?, authState: PlaybackAuthState): Result<Track> =
            canned(Track(videoId, "MPLYt_test", listOf(song2), song = song))

        override suspend fun radio(videoId: String?, playlistId: String?, authState: PlaybackAuthState): Result<RadioPageResult> =
            canned(RadioPageResult(listOf(song, song2), playlistId ?: "RDAMVM$videoId", videoId ?: "", "cur"))

        override suspend fun radioMore(cursor: String, authState: PlaybackAuthState): Result<RadioPageResult> =
            canned(RadioPageResult(listOf(song2), "", "", null))

        override suspend fun lyrics(lyricsId: String, authState: PlaybackAuthState): Result<String> = lyricsResult

        override suspend fun transcript(videoId: String): Result<String> = transcriptResult

        override fun resolveUrl(url: String): ResolvedLink? =
            if (url.contains("watch?v=vid12345678")) ResolvedLink("video", "vid12345678", url = url) else null
    }

    private fun client(
        tube: FakeTube = FakeTube(),
        lrcLib: LrcLibLyricsProvider = LrcLibLyricsProvider(lrclib404()),
        sponsor: SponsorBlockRepository = SponsorBlockRepository(HttpClient(MockEngine) {
            expectSuccess = false
            engine { addHandler { respond("[]", HttpStatusCode.NotFound, json) } }
        }),
    ) = MyTubeMusicClient(tube, LyricsRepository(lrcLib, tube), sponsor)

    /** LRCLib selalu 404 (tanpa lirik) — untuk tes yang tidak menguji LRCLib. */
    private fun lrclib404() = HttpClient(MockEngine) {
        expectSuccess = false
        engine { addHandler { respond("{}", HttpStatusCode.NotFound, json) } }
    }

    /** LRCLib menjawab LRC sinkron untuk lagu tes (durasi 185 cocok). */
    private fun lrclibSynced() = HttpClient(MockEngine) {
        expectSuccess = false
        engine { addHandler { request ->
            if (request.url.encodedPath == "/api/get") {
                respond("""{"id":1,"trackName":"Lagu Tes","artistName":"Artis","duration":185,"syncedLyrics":"[00:01.00]baris satu\n[00:05.00]baris dua","plainLyrics":"baris satu\nbaris dua","instrumental":false}""", HttpStatusCode.OK, json)
            } else respond("[]", HttpStatusCode.NotFound, json)
        } }
    }

    private suspend fun httpFailure(status: HttpStatusCode): ResponseException {
        val http = HttpClient(MockEngine) {
            engine { addHandler { respond("{}", status, json) } }
        }
        return ResponseException(http.get("https://music.youtube.com/test"), "")
    }

    // ------------------------------------------------------------------ delegasi katalog

    @Test fun searchSuggestTrackDelegateToMyTube() = runTest {
        val c = client()
        assertEquals(listOf(song), c.search("lagu", SearchFilter.All).songs)
        assertEquals(listOf(song2), c.searchMore("cur").songs)
        assertEquals("kopi senja", c.suggest("kopi").single().text)
        assertEquals(song, c.track("vid12345678").song)
    }

    @Test fun collectionsArtistAndDiscoveryDelegate() = runTest {
        val c = client()
        assertEquals("Album Tes", c.album("MPREb_1").title)
        assertEquals("Playlist Tes", c.playlist("PL1").title)
        assertEquals("Acara Tes", c.podcast("MPSP1").title)
        assertEquals("Artis", c.artist("UC1").name)
        assertEquals("Pilihan cepat", c.home().single().title)
        assertEquals("Tangga Indonesia", c.charts("ID").single().title)
        assertEquals("Rilis baru", c.newReleases().single().title)
        assertEquals("Fokus", c.moods().single().items.single().title)
        assertEquals("Fokus", c.mood("p1", "Fokus").title)
    }

    @Test fun radioAndRadioMoreDelegate() = runTest {
        val c = client()
        val r = c.radio(videoId = "vid12345678")
        assertEquals(listOf(song, song2), r.songs)
        assertEquals("RDAMVMvid12345678", r.playlistId)
        assertEquals(listOf(song2), c.radioMore("cur").songs)
    }

    @Test fun collectionMoreForwardsShowTitleForPodcast() = runTest {
        val tube = FakeTube()
        val c = client(tube)
        val more = c.collectionMore(CollectionKind.Podcast, "cur", "MPSP1", showTitle = "Acara Tes")
        assertEquals("Acara Tes", tube.lastShowTitle, "judul acara halaman pertama diteruskan ke halaman lanjutan podcast")
        assertEquals(listOf(song2), more.tracks)
    }

    @Test fun resolveUrlReturnsLinkAndThrowsForUnknown() = runTest {
        val c = client()
        assertEquals("vid12345678", c.resolveUrl("https://music.youtube.com/watch?v=vid12345678").videoId)
        val e = assertFailsWith<MyTubeMusicException> { c.resolveUrl("https://example.com/bukan-link") }
        assertEquals("Link ini tidak bisa dibuka di pemutar musik.", e.message)
    }

    @Test fun lyricsDelegatesToMyTube() = runTest {
        assertEquals("teks youtube", client().lyrics("MPLYt_test"))
    }

    // ------------------------------------------------------------------ lirik

    @Test fun syncedLyricsPrefersLrclibSynced() = runTest {
        val c = client(lrcLib = LrcLibLyricsProvider(lrclibSynced()))
        val l = c.syncedLyrics(song, "MPLYt_test")
        assertTrue(l?.hasSynced == true, "LRCLib memberi LRC sinkron")
        assertEquals("lrclib", l?.source)
        assertEquals("baris satu", l?.synced?.firstOrNull()?.text)
    }

    @Test fun syncedLyricsFallsBackToMyTubeText() = runTest {
        val l = client().syncedLyrics(song, "MPLYt_test")
        assertEquals(Lyrics(null, "teks youtube", "youtube"), l)
    }

    @Test fun syncedLyricsReturnsNullWhenAllSourcesFail() = runTest {
        val tube = FakeTube().apply { lyricsResult = Result.failure(IllegalStateException("tanpa lirik")) }
        assertNull(client(tube).syncedLyrics(song, "MPLYt_test"), "semua sumber gagal = tidak ada lirik, bukan galat")
    }

    // ------------------------------------------------------------------ radio lokal + SponsorBlock

    @Test fun stationsReturnsLocalStationsWithoutNetwork() = runTest {
        val stations = client().stations()
        assertEquals(5, stations.size)
        assertEquals("soma-dronezone", stations.first().id)
        assertEquals(LocalRadioStations.FALLBACK, stations)
    }

    @Test fun sponsorSegmentsDelegatesAndMapsDownstreamFailure() = runTest {
        val ok = SponsorBlockRepository(HttpClient(MockEngine) {
            expectSuccess = false
            engine { addHandler { respond("""[{"videoID":"vid12345678","segments":[{"segment":[10.0,20.0],"category":"sponsor","actionType":"skip"}]}]""", HttpStatusCode.OK, json) } }
        })
        val segs = client(sponsor = ok).sponsorSegments("vid12345678")
        assertEquals(1, segs.size)
        assertEquals("sponsor", segs.single().category)

        val down = SponsorBlockRepository(HttpClient(MockEngine) {
            expectSuccess = false
            engine { addHandler { respond("{}", HttpStatusCode.BadGateway, json) } }
        })
        val e = assertFailsWith<MyTubeMusicException> { client(sponsor = down).sponsorSegments("vid12345678") }
        assertEquals("SponsorBlock sedang tidak bisa dihubungi.", e.message)
    }

    // ------------------------------------------------------------------ pemetaan galat

    @Test fun http403MapsToAuthErrorWithoutClaimingSessionExpired() = runTest {
        val httpErr = httpFailure(HttpStatusCode.Forbidden)
        val tube = FakeTube().apply { failure = httpErr }
        val e = assertFailsWith<MyTubeMusicException> { client(tube).search("x", SearchFilter.All) }
        assertTrue(e.isAuth, "403 InnerTube = galat auth (padanan 401/403 PR #8)")
        assertEquals(403, e.status)
        // Anonim: tidak ada sesi yang bisa habis — jangan klaim "Sesi berakhir".
        assertEquals("YouTube menolak permintaan. Coba lagi.", e.message)
    }

    @Test fun http401IsAlsoAuthError() = runTest {
        val httpErr = httpFailure(HttpStatusCode.Unauthorized)
        val tube = FakeTube().apply { failure = httpErr }
        val e = assertFailsWith<MyTubeMusicException> { client(tube).suggest("x") }
        assertTrue(e.isAuth)
        assertEquals("YouTube menolak permintaan. Coba lagi.", e.message)
    }

    @Test fun http429MapsToRateLimitMessage() = runTest {
        val httpErr = httpFailure(HttpStatusCode.TooManyRequests)
        val tube = FakeTube().apply { failure = httpErr }
        val e = assertFailsWith<MyTubeMusicException> { client(tube).search("x", SearchFilter.All) }
        assertFalse(e.isAuth)
        assertEquals("YouTube membatasi permintaan. Coba lagi sebentar lagi.", e.message)
    }

    @Test fun http500MapsToServerMessage() = runTest {
        val httpErr = httpFailure(HttpStatusCode.InternalServerError)
        val tube = FakeTube().apply { failure = httpErr }
        val e = assertFailsWith<MyTubeMusicException> { client(tube).home() }
        assertFalse(e.isAuth)
        assertEquals("YouTube Music sedang tidak bisa dihubungi. Coba lagi sebentar.", e.message)
    }

    @Test fun ioFailureMapsToNetworkMessage() = runTest {
        val tube = FakeTube().apply { failure = IOException("jaringan mati") }
        val e = assertFailsWith<MyTubeMusicException> { client(tube).search("x", SearchFilter.All) }
        assertFalse(e.isAuth)
        assertEquals("Tidak bisa menghubungi YouTube Music. Periksa koneksi.", e.message)
    }
}
