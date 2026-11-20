package com.rosyidputrar.taskify.core.media

import com.rosyidputrar.taskify.core.domain.media.ArtistPage
import com.rosyidputrar.taskify.core.domain.media.CollectionKind
import com.rosyidputrar.taskify.core.domain.media.DiscoverySection
import com.rosyidputrar.taskify.core.domain.media.Lyrics
import com.rosyidputrar.taskify.core.domain.media.MoodGroup
import com.rosyidputrar.taskify.core.domain.media.MoodPage
import com.rosyidputrar.taskify.core.domain.media.MusicCollection
import com.rosyidputrar.taskify.core.domain.media.RadioPageResult
import com.rosyidputrar.taskify.core.domain.media.RadioStation
import com.rosyidputrar.taskify.core.domain.media.ResolvedLink
import com.rosyidputrar.taskify.core.domain.media.SearchFilter
import com.rosyidputrar.taskify.core.domain.media.SearchResults
import com.rosyidputrar.taskify.core.domain.media.Song
import com.rosyidputrar.taskify.core.domain.media.SponsorSegment
import com.rosyidputrar.taskify.core.domain.media.Suggestion
import com.rosyidputrar.taskify.core.domain.media.Track
import com.rosyidputrar.taskify.core.innertube.PlaybackAuthState
import com.rosyidputrar.taskify.core.innertube.MyTube
import com.rosyidputrar.taskify.core.media.lyrics.LyricsRepository
import com.rosyidputrar.taskify.core.media.sponsorblock.SponsorBlockApiException
import com.rosyidputrar.taskify.core.media.sponsorblock.SponsorBlockRepository
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.IOException

/**
 * Galat jalur musik direct; pesannya sudah siap tampil (Indonesia).
 * [status] = HTTP status hulu bila ada (0 = bukan HTTP).
 *
 * [isAuth] = padanan 401/403 Supabase PR #8: galat auth InnerTube (login
 * diperlukan / PoToken / `LOGIN_REQUIRED` persisten). PR #8 menampilkan galat
 * ini di saran ketik, bukan menyembunyikannya — tapi pesannya JANGAN
 * "Sesi berakhir. Masuk ulang." karena mode anonim tidak punya sesi yang
 * bisa habis; yang benar adalah "YouTube menolak permintaan. Coba lagi."
 */
class MyTubeMusicException(message: String, val status: Int = 0) : Exception(message) {
    val isAuth: Boolean get() = status == 401 || status == 403
}

/**
 * Fasad tipis atas jalur direct (Tahap 7 rewrite musik, PLAN D.4) — pengganti
 * klien Worker lama untuk `MusicViewModel`/`MusicPlayer`. API publiknya
 * sama bentuknya dengan klien lama supaya pemanggil berubah minimal:
 * katalog lewat fasad `:core:innertube` [MyTube], lirik lewat
 * [LyricsRepository] (LRCLib -> teks YouTube -> transkrip), SponsorBlock
 * lewat [SponsorBlockRepository] langsung ke `sponsor.ajay.app`, stasiun
 * radio dari data lokal [LocalRadioStations] (bukan jaringan).
 *
 * Galat hulu dipetakan ke [MyTubeMusicException] dengan pesan siap tampil.
 * `open` supaya tes bisa memakai subclass palsu. `authState` dibaca segar tiap panggilan (pola Tahap 5/6: transport
 * `InnerTube` di dalam `MyTube` tidak disatukan dengan resolver, jadi state
 * auth diteruskan eksplisit, bukan mengandalkan state internal transport).
 *
 * Stream audio TIDAK lewat fasad ini: `MusicPlayer` memakai
 * `DirectStreamResolver` langsung (komposisi track + stream di sana).
 */
open class MyTubeMusicClient(
    private val myTube: MyTube = MyTube(),
    private val lyrics: LyricsRepository = LyricsRepository(myTube = myTube),
    private val sponsorBlock: SponsorBlockRepository = SponsorBlockRepository(),
    private val authState: () -> PlaybackAuthState = { PlaybackAuthState.EMPTY },
) {
    /** Buka `Result<T>` fasad `:core:innertube` dan petakan galatnya ke pesan UI. */
    private suspend fun <T> call(block: suspend () -> Result<T>): T {
        val result = try {
            // C S-4: anggaran total per panggilan katalog — `withRetry` di
            // InnerTube mengulang 3x dengan timeout 15 dtk per percobaan
            // (~46 dtk tanpa batas ini). 20 dtk total: cukup untuk 1x
            // percobaan lambat + 1x ulang cepat, tapi gagal-cepat bila
            // jaringan menggantung.
            withTimeout(CATALOG_TIMEOUT_MS) { block() }
        } catch (e: TimeoutCancellationException) {
            throw MyTubeMusicException("Permintaan ke YouTube Music kehabisan waktu. Coba lagi.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e.toMusicException()
        }
        return result.getOrElse { failure ->
            if (failure is TimeoutCancellationException) throw MyTubeMusicException("Permintaan ke YouTube Music kehabisan waktu. Coba lagi.")
            if (failure is CancellationException) throw failure
            throw failure.toMusicException()
        }
    }

    // ------------------------------------------------------------------ pencarian & saran

    open suspend fun search(query: String, filter: SearchFilter): SearchResults =
        call { myTube.search(query, filter, authState()) }

    open suspend fun searchMore(cursor: String): SearchResults =
        call { myTube.searchMore(cursor, authState()) }

    open suspend fun suggest(query: String): List<Suggestion> =
        call { myTube.suggest(query, authState()) }

    /** Link YouTube → tujuan buka; murni parsing lokal, tanpa jaringan. */
    open suspend fun resolveUrl(url: String): ResolvedLink =
        myTube.resolveUrl(url) ?: throw MyTubeMusicException("Link ini tidak bisa dibuka di pemutar musik.")

    // ------------------------------------------------------------------ lagu & lirik

    open suspend fun track(videoId: String): Track =
        call { myTube.track(videoId, authState()) }

    open suspend fun lyrics(lyricsId: String): String =
        call { myTube.lyrics(lyricsId, authState()) }

    /**
     * Lirik sinkron (LRCLib -> teks YouTube -> transkrip); `null` = tidak ada
     * lirik yang bisa dipakai, bukan galat (perilaku Worker: kegagalan LRCLib
     * tidak pernah jadi error selama teks tersedia).
     */
    open suspend fun syncedLyrics(song: Song, lyricsId: String? = null): Lyrics? =
        lyrics.getLyrics(song, lyricsId)

    // ------------------------------------------------------------------ koleksi

    open suspend fun album(browseId: String): MusicCollection =
        call { myTube.album(browseId, authState()) }

    open suspend fun playlist(browseId: String): MusicCollection =
        call { myTube.playlist(browseId, authState()) }

    open suspend fun podcast(browseId: String): MusicCollection =
        call { myTube.podcast(browseId, authState()) }

    /**
     * Halaman lanjutan koleksi. Untuk podcast, [showTitle] (judul acara dari
     * halaman pertama) dipakai sebagai nama acara tiap episode — respons
     * lanjutan tidak mengulang judul itu.
     */
    open suspend fun collectionMore(kind: CollectionKind, cursor: String, id: String, showTitle: String = ""): MusicCollection =
        call { myTube.collectionMore(kind, cursor, id, showTitle, authState()) }

    open suspend fun artist(browseId: String): ArtistPage =
        call { myTube.artist(browseId, authState()) }

    // ------------------------------------------------------------------ jelajah

    open suspend fun home(): List<DiscoverySection> =
        call { myTube.home(authState()) }

    open suspend fun charts(country: String = "ID"): List<DiscoverySection> =
        call { myTube.charts(country, authState()) }

    open suspend fun newReleases(): List<DiscoverySection> =
        call { myTube.newReleases(authState()) }

    open suspend fun moods(): List<MoodGroup> =
        call { myTube.moods(authState()) }

    open suspend fun mood(params: String, title: String = ""): MoodPage =
        call { myTube.mood(params, title, authState()) }

    // ------------------------------------------------------------------ radio

    /** Mix radio dari lagu ([videoId]) atau id mix `RDAMVM…`/`RDCLAK…` ([playlistId]). */
    open suspend fun radio(videoId: String? = null, playlistId: String? = null): RadioPageResult =
        call { myTube.radio(videoId, playlistId, authState()) }

    open suspend fun radioMore(cursor: String): RadioPageResult =
        call { myTube.radioMore(cursor, authState()) }

    /** Stasiun radio langsung — data statis lokal, tanpa jaringan. */
    open suspend fun stations(): List<RadioStation> = LocalRadioStations.FALLBACK

    /** Segmen SponsorBlock (kategori bawaan); `[]` = tanpa segmen. 502 = layanan mati → dilempar. */
    open suspend fun sponsorSegments(videoId: String): List<SponsorSegment> =
        try {
            sponsorBlock.segments(videoId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e.toMusicException()
        }

    companion object {
        /** C S-4: anggaran total per panggilan katalog (lihat `call`). */
        const val CATALOG_TIMEOUT_MS = 20_000L

        /** Galat hulu → pesan siap tampil (pola `MusicErrors` Worker, tanpa kode server). */
        internal fun Throwable.toMusicException(): MyTubeMusicException = when (this) {
            is MyTubeMusicException -> this
            is ResponseException -> MyTubeMusicException(messageForStatus(response.status.value), response.status.value)
            is SponsorBlockApiException -> MyTubeMusicException(
                if (statusCode >= 500) "SponsorBlock sedang tidak bisa dihubungi." else "SponsorBlock balas HTTP $statusCode.",
                statusCode,
            )
            is IOException -> MyTubeMusicException("Tidak bisa menghubungi YouTube Music. Periksa koneksi.")
            else -> MyTubeMusicException(message?.takeIf { it.isNotBlank() } ?: "Gagal memuat dari YouTube Music.")
        }

        private fun messageForStatus(status: Int): String = when (status) {
            // Anonim: tidak ada sesi yang bisa habis — jangan klaim "Sesi berakhir".
            401, 403 -> "YouTube menolak permintaan. Coba lagi."
            429 -> "YouTube membatasi permintaan. Coba lagi sebentar lagi."
            in 500..599 -> "YouTube Music sedang tidak bisa dihubungi. Coba lagi sebentar."
            else -> "YouTube Music balas HTTP $status."
        }
    }
}

/**
 * Daftar stasiun radio langsung (SomaFM) — data statis lokal, tanpa jaringan.
 * Salinan lama `RadioStations.FALLBACK` di klien Worker sudah dihapus
 * bersama kliennya (Tahap 8); yang ini yang bertahan (PLAN C.16).
 */
object LocalRadioStations {
    val FALLBACK = listOf(
        RadioStation("soma-dronezone", "Drone Zone", "https://ice1.somafm.com/dronezone-128-mp3", "deep", "Ambient tanpa beat. Paling netral untuk kerja dalam.", "SomaFM", "https://somafm.com/dronezone/"),
        RadioStation("soma-deepspaceone", "Deep Space One", "https://ice1.somafm.com/deepspaceone-128-mp3", "deep", "Ambient luar angkasa, tenang dan panjang.", "SomaFM", "https://somafm.com/deepspaceone/"),
        RadioStation("soma-groovesalad", "Groove Salad", "https://ice1.somafm.com/groovesalad-128-mp3", "light", "Downtempo ringan untuk kerja santai.", "SomaFM", "https://somafm.com/groovesalad/"),
        RadioStation("soma-sonicuniverse", "Sonic Universe", "https://ice1.somafm.com/sonicuniverse-128-mp3", "jazz", "Jazz modern tanpa banyak vokal.", "SomaFM", "https://somafm.com/sonicuniverse/"),
        RadioStation("soma-lush", "Lush", "https://ice1.somafm.com/lush-128-mp3", "chill", "Elektronik lembut dengan vokal.", "SomaFM", "https://somafm.com/lush/", vocals = true),
    )
}
