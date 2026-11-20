package com.rosyidputrar.taskify

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rosyidputrar.taskify.core.domain.media.AccountInfo
import com.rosyidputrar.taskify.core.domain.media.BrowseTarget
import com.rosyidputrar.taskify.core.domain.media.CollectionKind
import com.rosyidputrar.taskify.core.domain.media.DiscoverySection
import com.rosyidputrar.taskify.core.domain.media.MusicCollection
import com.rosyidputrar.taskify.core.domain.media.Lyrics
import com.rosyidputrar.taskify.core.domain.media.MoodGroup
import com.rosyidputrar.taskify.core.domain.media.MusicQuality
import com.rosyidputrar.taskify.core.domain.media.SearchFilter
import com.rosyidputrar.taskify.core.domain.media.SearchResults
import com.rosyidputrar.taskify.core.domain.media.Song
import com.rosyidputrar.taskify.core.domain.media.Suggestion
import com.rosyidputrar.taskify.core.domain.media.Track
import com.rosyidputrar.taskify.core.innertube.PlaybackAuthState
import com.rosyidputrar.taskify.core.innertube.MyTube
import com.rosyidputrar.taskify.core.media.MusicPlayer
import com.rosyidputrar.taskify.core.media.MyTubeMusicClient
import com.rosyidputrar.taskify.core.media.MyTubeMusicException
import com.rosyidputrar.taskify.core.media.mytube.DirectStream
import com.rosyidputrar.taskify.core.media.mytube.DirectStreamResolver
import com.rosyidputrar.taskify.core.media.mytube.MusicLoginRepository
import com.rosyidputrar.taskify.core.media.mytube.MyTubeAuthStore
import com.rosyidputrar.taskify.feature.media.MusicLoginUiState
import com.rosyidputrar.taskify.feature.media.MusicUiState
import com.rosyidputrar.taskify.feature.media.MusicViewModel
import com.rosyidputrar.taskify.feature.media.OnlineFlow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Gate login musik (PLAN C.7, Tahap 5): tanpa login, tidak ada panggilan
 * jaringan/pemutar YouTube — aksi UI membuka prompt, aksi latar diam saja.
 * Pola Robolectric mengikuti `MusicQueueFromSearchTest` (main looper +
 * `shadowOf(...).idle()`); `vm.state` dikoleksi supaya `WhileSubscribed`
 * mengalirkan perubahan `local`.
 */
@RunWith(AndroidJUnit4::class)
class MusicViewModelGateTest {
    private fun song(id: String) = Song(id.padEnd(11, 'x'), "Lagu $id", "Artis", "3.05", emptyList())

    private class FakeTube : MyTube() {
        override suspend fun track(videoId: String, authState: PlaybackAuthState): Result<Track> =
            // lyricsChecked=true supaya loadLyrics tidak memanggil jaringan lirik.
            Result.success(Track(videoId, "", emptyList(), lyricsChecked = true))
    }

    private class FakeResolver : DirectStreamResolver() {
        val calls = mutableMapOf<String, Int>()
        val gates = mutableMapOf<String, CompletableDeferred<DirectStream>>()

        override suspend fun resolve(videoId: String, quality: MusicQuality, authState: PlaybackAuthState, preferMp4a: Boolean, pinnedItag: Int?, networkMetered: Boolean): Result<DirectStream> {
            calls[videoId] = (calls[videoId] ?: 0) + 1
            gates.getOrPut(videoId) { CompletableDeferred() }.await() // audio tidak pernah siap
            error("tidak terjangkau")
        }

        override suspend fun tryResolve(videoId: String, quality: MusicQuality, authState: PlaybackAuthState, preferMp4a: Boolean, pinnedItag: Int?, networkMetered: Boolean): DirectStream? {
            calls[videoId] = (calls[videoId] ?: 0) + 1
            gates.getOrPut(videoId) { CompletableDeferred() }.await()
            error("tidak terjangkau")
        }
    }

    // C S-9: `inner` supaya `playlistResult`/`collectionMoreResult` bisa memakai helper `song()` milik kelas luar.
    private inner class FakeClient(tube: MyTube) : MyTubeMusicClient(tube) {
        var searchCalls = 0
        var suggestCalls = 0
        var homeCalls = 0
        var throwAuthOnSearch = false
        /** C S-1: kendali gagal per seksi Jelajah. */
        var failHome = false
        var failCharts = false
        var failReleases = false
        var failMoods = false
        var homeResult: List<DiscoverySection> = emptyList()
        val results = SearchResults(songs = listOf(Song("aaaaaaaaaaa", "Lagu a", "Artis", "3.05", emptyList()), Song("bbbbbbbbbbb", "Lagu b", "Artis", "3.05", emptyList())))

        override suspend fun search(query: String, filter: SearchFilter): SearchResults {
            searchCalls++
            if (throwAuthOnSearch) throw MyTubeMusicException("YouTube menolak permintaan. Coba lagi.", 401)
            return results
        }

        override suspend fun suggest(query: String): List<Suggestion> {
            suggestCalls++
            return emptyList()
        }

        override suspend fun home(): List<DiscoverySection> {
            homeCalls++
            if (failHome) throw RuntimeException("home gagal")
            return homeResult
        }

        override suspend fun charts(country: String): List<DiscoverySection> {
            if (failCharts) throw RuntimeException("charts gagal")
            return emptyList()
        }

        override suspend fun newReleases(): List<DiscoverySection> {
            if (failReleases) throw RuntimeException("releases gagal")
            return emptyList()
        }

        override suspend fun moods(): List<MoodGroup> {
            if (failMoods) throw RuntimeException("moods gagal")
            return emptyList()
        }

        // C S-9: koleksi 2 halaman untuk playPage.
        var collectionMoreCalls = 0
        var playlistResult: MusicCollection = MusicCollection(
            CollectionKind.Playlist, "pl1", "Playlist", "", emptyList(),
            listOf(song("a"), song("b")), cursor = "c2",
        )
        var collectionMoreResult: MusicCollection = MusicCollection(
            CollectionKind.Playlist, "pl1", "Playlist", "", emptyList(),
            listOf(song("c")), cursor = null,
        )

        override suspend fun playlist(browseId: String): MusicCollection = playlistResult

        override suspend fun collectionMore(kind: CollectionKind, cursor: String, id: String, showTitle: String): MusicCollection {
            collectionMoreCalls++
            return collectionMoreResult
        }
        override suspend fun track(videoId: String): Track = Track(videoId, "", emptyList(), lyricsChecked = true)
        override suspend fun syncedLyrics(song: Song, lyricsId: String?): Lyrics? = null
    }

    private inner class Fixture(loggedIn: Boolean, online: Boolean = true) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val authStore = MyTubeAuthStore(context).also { it.clear() }
        val tube = FakeTube()
        val resolver = FakeResolver()
        val client = FakeClient(tube)
        val repo: MusicLoginRepository
        val player: MusicPlayer
        val vm: MusicViewModel
        val states = mutableListOf<MusicUiState>()
        private val scope = CoroutineScope(Dispatchers.Main)

        init {
            if (loggedIn) {
                authStore.saveAuthState(PlaybackAuthState(cookie = "SAPISID=dummy; LOGIN_INFO=dummy", dataSyncId = "delegated||user"))
                authStore.saveAccount(AccountInfo("Tes Akun", "tes@example.com"))
            }
            repo = MusicLoginRepository(tube, authStore)
            // SponsorBlock default: tidak pernah terpanggil di tes ini
            // (resolve tidak pernah selesai → status tidak pernah Playing).
            player = MusicPlayer(context, tube, resolver, authStore = authStore)
            vm = MusicViewModel(player, client, OnlineFlow { flowOf(online) }, null, repo)
            scope.launch { vm.state.collect { states += it } }
            idle()
        }

        fun idle() = shadowOf(Looper.getMainLooper()).idle()
        fun current(): MusicUiState = states.last()
    }

    @Test fun initDoesNotLoadExploreWhenLoggedOut() {
        val f = Fixture(loggedIn = false)
        assertEquals(0, f.client.homeCalls, "init belum login tidak boleh memanggil Jelajah")
        assertEquals(MusicLoginUiState.LoggedOut, f.current().login)
        assertFalse(f.current().showLogin, "init diam saja, tanpa prompt")
    }

    @Test fun searchWhenLoggedOutShowsPromptAndSkipsClient() {
        val f = Fixture(loggedIn = false)
        f.vm.setQuery("negoro")
        f.vm.search()
        f.idle()
        assertEquals(0, f.client.searchCalls, "belum login: client.search tidak dipanggil")
        assertTrue(f.current().showLogin, "aksi UI membuka prompt login")
        assertEquals(MusicLoginUiState.LoggedOut, f.current().login)
    }

    // C S-4: offline gagal-cepat — tanpa menunggu timeout katalog (20 dtk).
    @Test fun searchWhenOfflineFailsFastWithoutCallingClient() {
        val f = Fixture(loggedIn = true, online = false)
        f.vm.setQuery("negoro")
        f.vm.search()
        f.idle()
        assertEquals(0, f.client.searchCalls, "offline: client.search tidak dipanggil")
        assertFalse(f.current().search.loading)
        assertTrue(f.current().search.error?.contains("offline", ignoreCase = true) == true, f.current().search.error)
    }

    // C S-1: gagal SEBAGIAN (2 dari 4 seksi) → partialError, seksi yang
    // berhasil tetap tampil, tanpa blok error total.
    @Test fun explorePartialFailureShowsPartialErrorAndKeepsSections() {
        val f = Fixture(loggedIn = true)
        f.client.homeResult = listOf(DiscoverySection.Songs("Beranda", listOf(song("a"))))
        f.client.failCharts = true
        f.client.failMoods = true
        f.vm.loadExplore(force = true)
        f.idle()
        val e = f.current().explore
        assertNull(e.error, "gagal sebagian bukan error total")
        assertNull(e.errorDetail)
        assertTrue(e.partialError?.contains("Sebagian") == true, e.partialError)
        assertEquals(1, e.home.size, "seksi yang berhasil tetap tampil")
        assertTrue(e.charts.isEmpty() && e.moods.isEmpty())
    }

    // C S-1: gagal TOTAL (4 dari 4 seksi) → error besar, tanpa partialError.
    @Test fun exploreTotalFailureShowsErrorNotPartial() {
        val f = Fixture(loggedIn = true)
        f.client.failHome = true
        f.client.failCharts = true
        f.client.failReleases = true
        f.client.failMoods = true
        f.vm.loadExplore(force = true)
        f.idle()
        val e = f.current().explore
        assertTrue(e.error != null, "gagal total harus punya error")
        assertNull(e.partialError)
    }

    // C S-9: playPage mulai putar dari halaman pertama SEKARANG, halaman 2
    // disambung di latar ke antrean yang sama.
    @Test fun playPageStartsAfterFirstPageThenAppendsNextPage() {
        val f = Fixture(loggedIn = true)
        f.vm.open(BrowseTarget.Playlist("pl1", "Playlist"))
        f.idle()
        assertEquals(2, f.current().page?.collection?.tracks?.size)
        f.vm.playPage(false)
        f.idle()
        assertEquals(
            listOf("axxxxxxxxxx", "bxxxxxxxxxx", "cxxxxxxxxxx"),
            f.player.state.value.ordered.map { it.videoId },
            "halaman 1 langsung antre + halaman 2 tersambung di latar",
        )
        assertEquals(1, f.client.collectionMoreCalls, "halaman 2 diambil sekali di latar")
        assertNull(f.current().busy, "busy dibersihkan setelah semua halaman")
        assertEquals(3, f.current().page?.collection?.tracks?.size, "halaman tetap menyimpan koleksi gabungan")
    }

    @Test fun playSongWhenLoggedOutDoesNotResolve() {
        val f = Fixture(loggedIn = false)
        f.vm.playSong(song("a"))
        f.idle()
        assertNull(f.resolver.calls[song("a").videoId], "belum login: pemutar tidak di-resolve")
        assertTrue(f.player.state.value.ordered.isEmpty(), "antrean tetap kosong")
        assertTrue(f.current().showLogin, "aksi UI membuka prompt login")
    }

    @Test fun suggestSilentlySkippedWhenLoggedOut() {
        val f = Fixture(loggedIn = false)
        f.vm.setQuery("ne")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000))
        assertEquals(0, f.client.suggestCalls, "belum login: saran debounce diam saja")
        assertFalse(f.current().showLogin, "aksi latar tidak membuka prompt")
    }

    @Test fun searchAndPlayWorkWhenLoggedIn() {
        val f = Fixture(loggedIn = true)
        assertEquals(MusicLoginUiState.LoggedIn(AccountInfo("Tes Akun", "tes@example.com")), f.current().login)
        assertEquals(1, f.client.homeCalls, "init saat login memuat Jelajah")
        f.vm.setQuery("negoro")
        f.vm.search()
        f.idle()
        assertEquals(1, f.client.searchCalls, "sudah login: search jalan")
        assertEquals(listOf("aaaaaaaaaaa", "bbbbbbbbbbb"), f.current().search.results.songs.map { it.videoId })
        assertFalse(f.current().showLogin)
        f.vm.playSong(f.client.results.songs[0])
        f.idle()
        assertEquals(1, f.resolver.calls["aaaaaaaaaaa"], "sudah login: lagu di-resolve")
        assertEquals(listOf("aaaaaaaaaaa"), f.player.state.value.ordered.map { it.videoId })
    }

    @Test fun logoutStopsPlayerAndClearsLogin() {
        val f = Fixture(loggedIn = true)
        f.vm.playSong(f.client.results.songs[0])
        f.idle()
        assertEquals(1, f.player.state.value.ordered.size, "sebelum logout antrean terisi")
        f.vm.logout()
        f.idle()
        assertTrue(f.player.state.value.ordered.isEmpty(), "logout menghentikan pemutar + antrean kosong")
        assertNull(f.repo.account.value, "logout membersihkan akun repo")
        assertFalse(f.repo.isLoggedIn)
        assertFalse(f.current().showLogin)
        assertEquals(MusicLoginUiState.LoggedOut, f.current().login)
    }

    @Test fun authFailureWhileLoggedInMarksSessionExpired() {
        val f = Fixture(loggedIn = true)
        f.client.throwAuthOnSearch = true
        f.vm.setQuery("negoro")
        f.vm.search()
        f.idle()
        assertTrue(f.repo.sessionExpired.value, "fasad 401 saat login menandai sesi kedaluwarsa")
        assertEquals(MusicLoginUiState.Expired(AccountInfo("Tes Akun", "tes@example.com")), f.current().login)
        assertEquals("YouTube menolak permintaan. Coba lagi.", f.current().search.error)
    }

    @Test fun expiredLoginGatesActions() {
        val f = Fixture(loggedIn = true)
        f.repo.markSessionExpired()
        f.idle()
        assertEquals(MusicLoginUiState.Expired(AccountInfo("Tes Akun", "tes@example.com")), f.current().login)
        f.vm.setQuery("negoro")
        f.vm.search()
        f.idle()
        assertEquals(0, f.client.searchCalls, "sesi kedaluwarsa: search tidak jalan")
        assertTrue(f.current().showLogin, "sesi kedaluwarsa membuka prompt login ulang")
    }

    @Test fun closeLoginReloadsExploreWhenLoggedIn() {
        val f = Fixture(loggedIn = true)
        assertEquals(1, f.client.homeCalls, "init memuat Jelajah sekali")
        f.vm.openLogin()
        f.idle()
        assertTrue(f.current().showLogin)
        f.vm.closeLogin()
        f.idle()
        assertFalse(f.current().showLogin)
        assertEquals(2, f.client.homeCalls, "tutup layar login saat login memuat ulang Jelajah")
    }

    @Test fun closeLoginWhenLoggedOutDoesNotLoadExplore() {
        val f = Fixture(loggedIn = false)
        f.vm.openLogin()
        f.idle()
        assertTrue(f.current().showLogin)
        f.vm.closeLogin()
        f.idle()
        assertFalse(f.current().showLogin)
        assertEquals(0, f.client.homeCalls, "tutup layar login saat belum login tidak memuat Jelajah")
    }
}
