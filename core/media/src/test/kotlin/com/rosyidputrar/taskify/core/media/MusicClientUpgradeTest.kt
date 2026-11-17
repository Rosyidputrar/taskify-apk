package com.rosyidputrar.taskify.core.media

import com.rosyidputrar.taskify.core.domain.media.CollectionKind
import com.rosyidputrar.taskify.core.domain.media.DiscoverySection
import com.rosyidputrar.taskify.core.domain.media.MusicQuality
import com.rosyidputrar.taskify.core.domain.media.QueueSnapshot
import com.rosyidputrar.taskify.core.domain.media.RadioSource
import com.rosyidputrar.taskify.core.domain.media.RepeatMode
import com.rosyidputrar.taskify.core.domain.media.SearchFilter
import com.rosyidputrar.taskify.core.domain.media.Song
import com.rosyidputrar.taskify.core.domain.media.Thumb
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Endpoint baru Worker music-taskify + perbaikan Fase 1 (401 refresh, poll basi, expiry, kode galat). */
class MusicClientUpgradeTest {
    private val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    private var clock = 1_000_000L

    private fun client(token: () -> String? = { "token" }, refresh: suspend () -> Unit = {}, handler: MockRequestHandler) =
        MusicClient("https://music.test", { token() }, HttpClient(MockEngine) {
            expectSuccess = false
            engine { addHandler(handler) }
        }, refresh = refresh, now = { clock })

    private fun body(r: HttpRequestData): JsonObject = Json.parseToJsonElement((r.body as TextContent).text).jsonObject
    private fun err(code: String, status: Int = 400, retryable: Boolean = false) =
        """{"error":{"message":"pesan server","code":"$code","retryable":$retryable}}""" to HttpStatusCode.fromValue(status)

    @Test fun unauthorizedRefreshesSessionOnceThenRetries() = runTest {
        var tok = "lama"
        var refreshes = 0
        val seen = mutableListOf<String?>()
        val c = client(token = { tok }, refresh = { refreshes++; tok = "baru" }) { r ->
            seen += r.headers[HttpHeaders.Authorization]
            if (r.headers[HttpHeaders.Authorization] == "Bearer lama") respond("""{"error":{"message":"Token kedaluwarsa","code":"UNAUTHORIZED","retryable":false}}""", HttpStatusCode.Unauthorized, json)
            else respond("""{"suggestions":[{"text":"tulus teh hijau","type":"query"}]}""", HttpStatusCode.OK, json)
        }
        assertEquals("tulus teh hijau", c.suggest("tulus").single().text)
        assertEquals(1, refreshes)
        assertEquals(listOf<String?>("Bearer lama", "Bearer baru"), seen.toList())
    }

    @Test fun persistentUnauthorizedAsksToSignInAgain() = runTest {
        var refreshes = 0
        val c = client(refresh = { refreshes++ }) { respond("""{"error":{"message":"x","code":"UNAUTHORIZED","retryable":false}}""", HttpStatusCode.Unauthorized, json) }
        val e = assertFailsWith<MusicException> { c.suggest("x") }
        assertTrue(e.sessionExpired)
        assertEquals(MusicClient.MSG_SESSION, e.message)
        assertEquals(1, refreshes, "refresh hanya sekali, tidak berputar")
        // Token kosong → coba refresh dulu sebelum menyerah.
        var tok: String? = null
        val c2 = client(token = { tok }, refresh = { tok = "segar" }) { respond("""{"suggestions":[]}""", HttpStatusCode.OK, json) }
        assertTrue(c2.suggest("x").isEmpty())
    }

    @Test fun errorCodesMapToFriendlyMessages() = runTest {
        for ((code, expected) in listOf(
            "FORBIDDEN" to "Akun ini belum diizinkan memakai pemutar musik.",
            "UPSTREAM_UNAVAILABLE" to "YouTube Music sedang tidak bisa dihubungi. Coba lagi sebentar.",
            "INVALID_CURSOR" to "Halaman lanjutan sudah kedaluwarsa. Ulangi pencarian.",
            "VALIDATION" to "pesan server",
        )) {
            val (b, status) = err(code, if (code == "FORBIDDEN") 403 else if (code == "UPSTREAM_UNAVAILABLE") 502 else 400)
            val c = client { respond(b, status, json) }
            assertEquals(expected, assertFailsWith<MusicException> { c.search("x", SearchFilter.All) }.message, code)
        }
        assertEquals("Terlalu banyak permintaan. Coba lagi dalam 12 detik.", MusicErrors.message(429, "RATE_LIMITED", null, 12))
    }

    @Test fun staleJobIsReInitialisedWithForce() = runTest {
        val inits = mutableListOf<JsonObject>()
        var polls = 0
        val c = client { r ->
            when (r.url.encodedPath) {
                "/v1/stream/init" -> { inits += body(r); respond(if (inits.size == 1) """{"job":"j1","source":"converter"}""" else """{"job":"j2","source":"converter"}""", HttpStatusCode.OK, json) }
                "/v1/stream/poll" -> {
                    polls++
                    val job = body(r)["job"].toString().trim('"')
                    if (job == "j1") respond("""{"error":{"message":"Job tidak sah","code":"INVALID_JOB","retryable":false}}""", HttpStatusCode.BadRequest, json)
                    else respond("""{"progress":100,"url":"https://conv/x.mp3","source":"converter"}""", HttpStatusCode.OK, json)
                }
                else -> respond("{}", HttpStatusCode.NotFound, json)
            }
        }
        val info = c.resolveStreamInfo("vid12345678", StreamOptions(quality = MusicQuality.Low))
        assertEquals("https://conv/x.mp3", info.url)
        assertEquals("j2", info.job, "job disimpan untuk arsip R2")
        assertEquals(2, inits.size)
        assertEquals("true", inits[1]["force"].toString(), "init ulang melewati cache 5 menit")
        assertEquals("\"low\"", inits[0]["quality"].toString())
        assertNull(inits[0]["force"])
        assertEquals(2, polls)
    }

    @Test fun directInitTracksExpiryAndLoudness() = runTest {
        val c = client { respond("""{"url":"https://rr/x","source":"direct","mimeType":"audio/webm; codecs=\"opus\"","itag":251,"expiresInSeconds":21540,"loudnessDb":3.78}""", HttpStatusCode.OK, json) }
        val info = c.resolveStreamInfo("vid12345678", StreamOptions())
        assertEquals(clock + 21_540_000, info.expiresAt)
        assertEquals(3.78, info.loudnessDb)
        assertTrue(info.toSource().seekable)
        assertTrue(info.isOpus)
    }

    @Test fun nowPlayingReturnsTrackLyricsAndStream() = runTest {
        var path = ""
        val c = client { r ->
            path = r.url.encodedPath
            assertEquals("true", body(r)["includeLyrics"].toString())
            respond("""{"track":{"videoId":"1RrF6Ee_io0","title":"Monokrom","artist":"Tulus","duration":"3.35","durationSeconds":215,"lyricsId":"MPLY1","loudnessDb":3.78,
                "thumbnails":[{"url":"https://i/1","width":60,"height":60}],
                "lyrics":{"synced":[{"t":35.66,"text":"Look at the stars"}],"plain":"Look at the stars","source":"lrclib","duration":215},
                "upNext":[{"videoId":"5F28ye50-Kc","title":"Teh Hijau","artist":"Tulus","duration":"3.33","durationSeconds":213,"thumbnails":[],"url":""}]},
                "stream":{"url":"https://rr/y","source":"direct","mimeType":"audio/mp4","itag":140,"expiresInSeconds":60}}""", HttpStatusCode.OK, json)
        }
        val pb = c.playback("1RrF6Ee_io0", StreamOptions(), withTrack = true)
        assertEquals("/v1/now-playing", path)
        val t = assertNotNull(pb.track)
        assertEquals(215, t.durationSeconds)
        assertEquals("Monokrom", t.song!!.title)
        assertTrue(t.lyricsChecked)
        assertEquals(35.66, t.lyrics!!.synced!!.single().t)
        assertEquals(213, t.upNext.single().durationSeconds)
        assertEquals("https://rr/y", pb.info.url)
    }

    @Test fun nowPlayingStreamErrorFallsBackToInit() = runTest {
        val paths = mutableListOf<String>()
        val c = client { r ->
            paths += r.url.encodedPath
            if (r.url.encodedPath == "/v1/now-playing") respond("""{"track":{"videoId":"vid12345678","title":"x","lyricsId":"","lyrics":null,"upNext":[]},
                "stream":{"error":{"message":"Konverter mati","code":"CONVERTER_ERROR","retryable":true}}}""", HttpStatusCode.OK, json)
            else respond("""{"url":"https://rr/z","source":"direct","mimeType":"audio/mp4"}""", HttpStatusCode.OK, json)
        }
        val pb = c.playback("vid12345678", StreamOptions(), withTrack = true)
        assertEquals(listOf("/v1/now-playing", "/v1/stream/init"), paths)
        assertTrue(pb.track!!.lyricsChecked, "lyrics:null = sudah dicari, tidak ada")
        assertNull(pb.track!!.lyrics)
        assertEquals("https://rr/z", pb.info.url)
    }

    @Test fun trackWithoutLyricsRequestIsNotChecked() = runTest {
        val c = client { respond("""{"videoId":"vid12345678","title":"x","lyricsId":"MPLY","lyrics":"","upNext":[]}""", HttpStatusCode.OK, json) }
        val t = c.track("vid12345678")
        assertFalse(t.lyricsChecked, "\"\" = tidak diminta, bukan \"tidak ada lirik\"")
        assertEquals("MPLY", t.lyricsId)
    }

    @Test fun searchParsesEveryKindAndCursor() = runTest {
        val c = client { r ->
            assertEquals(null, body(r)["filter"], "Semua = tanpa filter")
            respond("""{"query":"tulus","topResult":{"title":"Monokrom","videoId":"QqJ-Vp8mvbk","subtitles":["Video","Tulus","3.39"],"thumbnails":[]},
                "songs":[{"videoId":"v-sr2H5UXHU","title":"Monokrom","artist":"Tulus","duration":"6.38","durationSeconds":398,"thumbnails":[],"url":""}],
                "albums":[{"browseId":"MPREb_1","title":"Monokrom","artist":"Tulus","year":"2016","thumbnails":[]}],
                "playlists":[{"browseId":"VLPL1","playlistId":"PL1","title":"Tulus","author":"Fransisca","thumbnails":[]}],
                "artists":[{"browseId":"UC1","name":"Tulus","subscribers":"55,6 jt","thumbnails":[]}],
                "podcasts":[{"browseId":"MPSP1","title":"Pod","author":"farah","thumbnails":[]}],
                "episodes":[{"videoId":"6QyRGE6Cg8E","browseId":"MPED1","title":"Ep","show":"Podcast Raditya Dika","publishedAt":"1 h lalu","duration":"","durationSeconds":null,"thumbnails":[]}],
                "cursor":"gabungan"}""", HttpStatusCode.OK, json)
        }
        val r = c.search("tulus", SearchFilter.All)
        assertEquals("Monokrom", r.top!!.title)
        assertEquals(398, r.songs.single().durationSeconds)
        assertEquals("MPREb_1", r.albums.single().browseId)
        assertEquals("PL1", r.playlists.single().playlistId)
        assertEquals("UC1", r.artists.single().browseId)
        assertEquals("MPSP1", r.podcasts.single().browseId)
        assertEquals("Podcast Raditya Dika · 1 h lalu", r.episodes.single().artist, "episode diputar sebagai lagu, artis = nama acara")
        assertNull(r.episodes.single().durationSeconds)
        assertEquals("gabungan", r.cursor)
    }

    @Test fun collectionsUseAlbumArtAndRouteCursorToSameEndpoint() = runTest {
        val paths = mutableListOf<Pair<String, JsonObject>>()
        val c = client { r ->
            paths += r.url.encodedPath to body(r)
            when (r.url.encodedPath) {
                "/v1/album" -> respond("""{"id":"MPREb_1","title":"Monokrom","by":"Tulus","year":"2016","trackCount":2,"thumbnails":[{"url":"https://i/544","width":544,"height":544}],
                    "tracks":[{"videoId":"f-fEeF12FH4","title":"Manusia Kuat","artist":"Tulus","duration":"3.05","durationSeconds":185,"thumbnails":[]}]}""", HttpStatusCode.OK, json)
                "/v1/playlist" -> respond("""{"id":"VLPL1","title":"Galau","by":"MUSIK ABC","trackCount":134,"thumbnails":[],"tracks":[],"cursor":"p2"}""", HttpStatusCode.OK, json)
                "/v1/podcast" -> respond("""{"browseId":"MPSP1","title":"Pod","author":"farah","description":"desk","thumbnails":[],
                    "episodes":[{"videoId":"6QyRGE6Cg8E","browseId":"","title":"Ep 1","show":"Pod","publishedAt":"3 Apr","duration":"42 mnt","durationSeconds":2520,"thumbnails":[]}],"cursor":"e2"}""", HttpStatusCode.OK, json)
                else -> respond("{}", HttpStatusCode.NotFound, json)
            }
        }
        val album = c.album("MPREb_1")
        assertEquals("https://i/544", album.tracks.single().cover, "trek album tanpa gambar memakai gambar album")
        assertEquals("Tulus · 2016", album.subtitle)
        val pl = c.playlist("PL1")
        assertEquals("\"VLPL1\"", paths.last().second["browseId"].toString(), "playlistId PL… diberi awalan VL")
        assertEquals("p2", pl.cursor)
        c.collectionMore(CollectionKind.Playlist, "p2", pl.id)
        assertEquals("/v1/playlist" to "{\"cursor\":\"p2\"}", paths.last().first to paths.last().second.toString())
        val pod = c.podcast("MPSP1")
        assertEquals(2520, pod.tracks.single().durationSeconds)
        assertEquals("desk", pod.description)
        assertEquals(CollectionKind.Podcast, pod.kind)
    }

    @Test fun artistDiscoveryMoodAndRadioParse() = runTest {
        val c = client { r ->
            when (r.url.encodedPath) {
                "/v1/artist" -> respond("""{"browseId":"UC1","name":"Tulus","subscribers":"2,39 jt","monthlyListeners":"54,5 jt audiens bulanan","description":"d","thumbnails":[],
                    "topSongs":[{"videoId":"v-sr2H5UXHU","title":"Monokrom","artist":"Tulus","duration":"","durationSeconds":null,"thumbnails":[]}],"topSongsPlaylistId":"VLOLAK1",
                    "albums":[{"browseId":"MPREb_1","title":"Monokrom","artist":"","year":"2016","thumbnails":[]}],"singles":[],"relatedArtists":[{"browseId":"UC2","name":"Raisa","subscribers":"","thumbnails":[]}]}""", HttpStatusCode.OK, json)
                "/v1/home" -> respond("""{"sections":[{"title":"Pilihan cepat","kind":"songs","items":[{"videoId":"BLQPNaBklGA","title":"Negoro Angin","artist":"Denny Caknan","duration":"","durationSeconds":null,"thumbnails":[]}]},
                    {"title":"Throwbacks","kind":"playlists","items":[{"browseId":"VLRDCLAK1","playlistId":"RDCLAK1","title":"Mix","author":"","thumbnails":[]}]},{"title":"Aneh","kind":"carousel","items":[]}]}""", HttpStatusCode.OK, json)
                "/v1/mood" -> if (body(r).isEmpty()) respond("""{"sections":[{"title":"Mood & momen","items":[{"title":"Fokus","params":"ggMPOg1u"}]}]}""", HttpStatusCode.OK, json)
                    else respond("""{"mood":"","sections":[{"title":"Flow state","kind":"playlists","items":[{"browseId":"VLPL9","playlistId":"PL9","title":"Deep Focus","author":"YouTube Music","thumbnails":[]}]}]}""", HttpStatusCode.OK, json)
                "/v1/radio" -> respond("""{"songs":[{"videoId":"5F28ye50-Kc","title":"Teh Hijau","artist":"Tulus","duration":"3.33","durationSeconds":213,"thumbnails":[]}],"playlistId":"RDAMVM1","seedVideoId":"1RrF6Ee_io0","cursor":"r2"}""", HttpStatusCode.OK, json)
                else -> respond("{}", HttpStatusCode.NotFound, json)
            }
        }
        val a = c.artist("UC1")
        assertEquals("VLOLAK1", a.topSongsPlaylistId)
        assertEquals("Raisa", a.related.single().name)
        val home = c.home()
        assertEquals(2, home.size, "rak yang tidak dikenal dilewati")
        assertTrue(home[1] is DiscoverySection.Playlists)
        assertEquals("Fokus", c.moods().single().items.single().title)
        val mood = c.mood("ggMPOg1u", "Fokus")
        assertEquals("Fokus", mood.title, "mood kosong (dipanggil dengan params) → pakai judul kategori")
        val radio = c.radio(videoId = "1RrF6Ee_io0")
        assertEquals("r2", radio.cursor)
        assertEquals("RDAMVM1", radio.playlistId)
    }

    @Test fun stationsAndSponsorBlockUseGetWithAuth() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val c = client { r ->
            requests += r
            when (r.url.encodedPath) {
                "/v1/radio/stations" -> respond("""[{"id":"soma-dronezone","name":"Drone Zone","url":"https://ice1.somafm.com/dronezone-128-mp3","mood":"deep","description":"Ambient","provider":"SomaFM","homepage":"","vocals":false}]""", HttpStatusCode.OK, json)
                "/v1/sponsorblock" -> respond("""{"videoId":"9bZkp7q19f0","categories":["sponsor"],"segments":[{"category":"intro","start":5.0,"end":9.5,"actionType":"skip"},{"category":"music_offtopic","start":0,"end":4.013}]}""", HttpStatusCode.OK, json)
                else -> respond("{}", HttpStatusCode.NotFound, json)
            }
        }
        val st = c.stations().single()
        assertEquals("Kerja dalam", st.moodLabel)
        assertTrue(st.toSong().live)
        val segs = c.sponsorSegments("9bZkp7q19f0")
        assertEquals(listOf(0.0, 5.0), segs.map { it.start }, "diurutkan menurut start")
        assertTrue(requests.all { it.method == HttpMethod.Get && it.headers[HttpHeaders.Authorization] == "Bearer token" })
        assertEquals("9bZkp7q19f0", requests.last().url.parameters["videoId"])
        // Daftar kosong → daftar lokal.
        val empty = client { respond("[]", HttpStatusCode.OK, json) }
        assertEquals(RadioStations.FALLBACK, empty.stations())
    }

    @Test fun archiveRetriesNotReadyAndDisablesOn503() = runTest {
        var calls = 0
        val c = client { r ->
            calls++
            assertEquals("j1", body(r)["job"].toString().trim('"'))
            if (calls == 1) respond("""{"error":{"message":"Belum siap","code":"NOT_READY","retryable":true}}""", HttpStatusCode.Conflict, json)
            else respond("""{"url":"https://music.test/v1/audio/vid12345678/mp3?token=t","source":"r2","mimeType":"audio/mpeg","sizeBytes":8220000,"cached":false}""", HttpStatusCode.OK, json)
        }
        val r2 = assertNotNull(c.archive("vid12345678", "j1"))
        assertTrue(r2.toSource().seekable)
        assertEquals(2, calls)
        val off = client { respond("""{"error":{"message":"R2 belum diikat","code":"R2_NOT_CONFIGURED","retryable":false}}""", HttpStatusCode.ServiceUnavailable, json) }
        assertNull(off.archive("vid12345678", "j1"))
        assertFalse(off.archiveAvailable, "503 → arsip mati untuk sesi ini, tidak dicoba lagi")
    }

    @Test fun resolveUrlAndSyncedLyricsParse() = runTest {
        val c = client { r ->
            when (r.url.encodedPath) {
                "/v1/resolve-url" -> respond("""{"kind":"album","browseId":"VLOLAK5uy_x","playlistId":"OLAK5uy_x","url":"https://music.youtube.com/playlist?list=OLAK5uy_x"}""", HttpStatusCode.OK, json)
                "/v1/lyrics/synced" -> respond("""{"synced":[],"plain":"","source":"lrclib"}""", HttpStatusCode.OK, json)
                else -> respond("{}", HttpStatusCode.NotFound, json)
            }
        }
        val link = c.resolveUrl("youtube.com/playlist?list=OLAK5uy_x")
        assertEquals("VLOLAK5uy_x", link.browseId)
        assertTrue(c.syncedLyrics("vid12345678").instrumental)
    }

    @Test fun queueCodecRoundTripsWithoutUrls() {
        val songs = (1..3).map { Song("vid$it".padEnd(11, 'x'), "Lagu $it", "Artis", "3.05", listOf(Thumb("s", 60, 60), Thumb("m", 226, 226), Thumb("l", 544, 544)), "https://music.youtube.com/watch?v=$it", 185) }
        val live = Song("live:soma", "Drone Zone", "SomaFM", "", emptyList(), "https://ice1/dz", null, live = true)
        val snap = QueueSnapshot(songs + live, listOf(2, 0, 1, 3), 1, 61_000, RepeatMode.One, true, false, RadioSource("RDAMVM1", "cur"))
        val back = assertNotNull(QueueCodec.decode(QueueCodec.encode(snap)))
        assertEquals(listOf(songs[2], songs[0], songs[1], live).map { it.videoId }, back.queue.map { it.videoId }, "urutan acak dibekukan")
        assertEquals(1, back.position)
        assertEquals(songs[0].videoId, back.queue[back.position].videoId)
        assertEquals(61_000, back.positionMs)
        assertEquals(RepeatMode.One, back.repeat)
        assertTrue(back.shuffle)
        assertFalse(back.autoContinue)
        assertEquals("cur", back.radio!!.cursor)
        assertEquals(listOf("s", "l"), back.queue[0].thumbnails.map { it.url }, "cukup gambar kecil & besar")
        assertEquals("", back.queue[0].url, "link lagu biasa tidak disimpan")
        assertEquals("https://ice1/dz", back.queue[3].url, "URL siaran langsung disimpan (bukan URL sekali pakai)")
        assertNull(QueueCodec.decode("""{"queue":[]}"""))
    }
}
