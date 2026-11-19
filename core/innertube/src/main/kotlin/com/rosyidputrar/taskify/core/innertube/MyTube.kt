package com.rosyidputrar.taskify.core.innertube

import com.rosyidputrar.taskify.core.domain.media.AccountInfo
import com.rosyidputrar.taskify.core.domain.media.ArtistPage
import com.rosyidputrar.taskify.core.domain.media.CollectionKind
import com.rosyidputrar.taskify.core.domain.media.DiscoverySection
import com.rosyidputrar.taskify.core.domain.media.MoodGroup
import com.rosyidputrar.taskify.core.domain.media.MoodPage
import com.rosyidputrar.taskify.core.domain.media.MusicCollection
import com.rosyidputrar.taskify.core.domain.media.RadioPageResult
import com.rosyidputrar.taskify.core.domain.media.ResolvedLink
import com.rosyidputrar.taskify.core.domain.media.SearchResults
import com.rosyidputrar.taskify.core.domain.media.Suggestion
import com.rosyidputrar.taskify.core.domain.media.Track
import com.rosyidputrar.taskify.core.innertube.models.MyTubeClient
import com.rosyidputrar.taskify.core.innertube.models.body.BrowseBody
import com.rosyidputrar.taskify.core.innertube.pages.CollectionContinuation
import com.rosyidputrar.taskify.core.innertube.pages.findMoodCategory
import com.rosyidputrar.taskify.core.innertube.pages.SelectedAccount
import com.rosyidputrar.taskify.core.innertube.pages.parseAccountDataSyncId
import com.rosyidputrar.taskify.core.innertube.pages.parseAccountInfo
import com.rosyidputrar.taskify.core.innertube.pages.parseSelectedAccount
import com.rosyidputrar.taskify.core.innertube.utils.emptyRawDetail
import com.rosyidputrar.taskify.core.innertube.utils.scrubRawDetail
import kotlinx.serialization.SerializationException
import com.rosyidputrar.taskify.core.innertube.pages.parseArtist
import com.rosyidputrar.taskify.core.innertube.pages.parseCharts
import com.rosyidputrar.taskify.core.innertube.pages.parseCollection
import com.rosyidputrar.taskify.core.innertube.pages.parseCollectionMore
import com.rosyidputrar.taskify.core.innertube.pages.parseDiscoverySections
import com.rosyidputrar.taskify.core.innertube.pages.parseHome
import com.rosyidputrar.taskify.core.innertube.pages.parseMoodCategories
import com.rosyidputrar.taskify.core.innertube.pages.parseNewReleases
import com.rosyidputrar.taskify.core.innertube.pages.parseNext
import com.rosyidputrar.taskify.core.innertube.pages.parseLyrics
import com.rosyidputrar.taskify.core.innertube.pages.parsePlayerTrack
import com.rosyidputrar.taskify.core.innertube.pages.parsePodcast
import com.rosyidputrar.taskify.core.innertube.pages.parsePodcastMore
import com.rosyidputrar.taskify.core.innertube.pages.parseRadio
import com.rosyidputrar.taskify.core.innertube.pages.parseRadioMore
import com.rosyidputrar.taskify.core.innertube.pages.parseSearch
import com.rosyidputrar.taskify.core.innertube.pages.parseSearchMore
import com.rosyidputrar.taskify.core.innertube.pages.parseSuggestions
import com.rosyidputrar.taskify.core.innertube.pages.parseTranscript
import com.rosyidputrar.taskify.core.innertube.pages.resolveYoutubeUrl
import com.rosyidputrar.taskify.core.domain.media.SearchFilter as DomainSearchFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Fasad katalog YouTube Music — semua fungsi mengembalikan `Result<T>`
 * (pola ArchiveTune `YouTube.kt`), tapi parsernya ditulis ulang dari
 * `src/ytmusic/parse.ts` (Worker) dan keluarannya model Taskify
 * (`Song`/`SearchResults`/dst), bukan `YTItem` ArchiveTune.
 *
 * Kelas biasa (bukan `object`) supaya `InnerTube` bisa diinjeksi lewat
 * konstruktor — untuk tes (MockEngine) dan DI (Koin) di Tahap 6-7.
 * `open` supaya tes pemutar bisa memakai subclass palsu: `track()`/`resolve()` memakai `withTimeoutOrNull` yang
 * tidak jalan di bawah `runTest` (pelajaran Tahap 2/3), jadi fake bergate
 * adalah satu-satunya cara deterministik menguji orkestrasi efek.
 * `next` memakai locale yang sama dengan browse/search (`hl=id, gl=ID`,
 * mengikuti Worker), bukan US/en ala ArchiveTune — lihat PLAN D.2.2.
 *
 * Yang BELUM ada di tahap ini: `queue` (`music/get_queue`, eksperimen
 * fallback radio bila `next` diblokir — lihat PLAN F.1c).
 * Lirik sinkron/transkrip ditambah di Tahap 4 (`transcript()` di sini).
 */
open class MyTube(private val innerTube: InnerTube = InnerTube()) {

    private fun parse(raw: String) = InnertubeJson.parseToJsonElement(raw)

    private suspend fun <T> resultOf(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    // ------------------------------------------------------------------ auth

    /** State auth yang dipakai pemanggilan tanpa `authState` eksplisit. */
    fun currentAuthState(): PlaybackAuthState = innerTube.currentAuthState()

    /**
     * Teruskan state auth tersimpan (mis. dari `MyTubeAuthStore`, Tahap 5)
     * ke transport, supaya pemanggilan tanpa `authState` eksplisit ikut
     * memakai `visitorData` hasil bootstrap.
     */
    fun applyAuthState(state: PlaybackAuthState) {
        innerTube.applyAuthState(state)
    }

    /**
     * Ambil `visitorData` anonim dari `GET music.youtube.com/sw.js_data`
     * (pola ArchiveTune `YouTube.visitorData()`): badan diawali prefix XSSI
     * `)]}'` (5 karakter), lalu array JSON; `visitorData` adalah string
     * pertama di `[0][2]` yang cocok [VISITOR_DATA_REGEX].
     *
     * Satu percobaan tanpa retry (bootstrap best-effort) — gagal apa pun
     * (jaringan mati, pola tidak ketemu, JSON rusak) → `Result.failure`.
     * `visitorData` BUKAN penawar `LOGIN_REQUIRED` yang flaky (Worker
     * `direct.ts:23-29`): jangan mengulang panggilan `player` dengan atau
     * tanpa-nya bila gagal.
     */
    open suspend fun visitorData(): Result<String> =
        resultOf {
            val body = innerTube.getSwJsData(withRetry = false).substring(XSSI_PREFIX_LENGTH)
            val candidates = parse(body).jsonArray[0].jsonArray[2].jsonArray
            candidates.first { element ->
                (element as? JsonPrimitive)?.contentOrNull?.let { VISITOR_DATA_REGEX.containsMatchIn(it) } ?: false
            }.jsonPrimitive.content
        }

    /**
     * Identitas akun aktif (`account/account_menu`, WEB_REMIX, PLAN C.3) —
     * dipakai repository login untuk verifikasi: cookie boleh lengkap tapi
     * akun tidak terbaca (mis. sesi kedaluwarsa) → gagal, bukan akun kosong.
     *
     * Bila badan bukan JSON atau `parseAccountInfo` null, dilempar
     * [UnrecognizedAccountResponseException] yang membawa cuplikan badan
     * mentah (sudah di-scrub, maks 500 karakter) — badan tidak pernah
     * dibuang diam-diam (PLAN music-login-raw-error). Pesan exception
     * mengandung "info akun tidak ditemukan" supaya classifier repository
     * tetap memetakannya ke `UnrecognizedResponse`.
     */
    open suspend fun accountInfo(
        authState: PlaybackAuthState = innerTube.currentAuthState(),
    ): Result<AccountInfo> =
        resultOf {
            val raw = innerTube.accountMenu(MyTubeClient.WEB_REMIX, authState)
            if (raw.isBlank()) {
                throw UnrecognizedAccountResponseException(emptyRawDetail(HTTP_OK))
            }
            val root = try {
                parse(raw)
            } catch (e: SerializationException) {
                throw UnrecognizedAccountResponseException(scrubRawDetail(raw))
            }
            parseAccountInfo(root)
                ?: throw UnrecognizedAccountResponseException(scrubRawDetail(raw))
        }

    /**
     * Akun terpilih dari `account/accounts_list` (WEB_REMIX, PLAN
     * music-login-raw-error) — fallback verifikasi login bila
     * `accountInfo()` tidak memberi akun: akun `isSelected` +
     * `dataSyncId`-nya. Gagal bila tidak ada akun terpilih.
     */
    open suspend fun selectedAccount(
        authState: PlaybackAuthState = innerTube.currentAuthState(),
    ): Result<SelectedAccount> =
        resultOf {
            val raw = innerTube.accountChannels(MyTubeClient.WEB_REMIX, authState)
            parseSelectedAccount(parse(raw))
                ?: throw IllegalStateException("akun terpilih tidak ditemukan")
        }

    /**
     * `dataSyncId` akun terpilih (`account/accounts_list`, WEB_REMIX,
     * PLAN C.3) — dipakai repository login setelah `accountInfo()` lolos.
     * Gagal bila respons tidak berisi `dataSyncId` apa pun.
     */
    open suspend fun accountDataSyncId(
        authState: PlaybackAuthState = innerTube.currentAuthState(),
    ): Result<String> =
        resultOf {
            val raw = innerTube.accountChannels(MyTubeClient.WEB_REMIX, authState)
            parseAccountDataSyncId(parse(raw))
                ?: throw IllegalStateException("dataSyncId akun tidak ditemukan")
        }

    // ------------------------------------------------------------------ cari

    open suspend fun search(
        query: String,
        filter: DomainSearchFilter = DomainSearchFilter.All,
        authState: PlaybackAuthState = innerTube.currentAuthState(),
    ): Result<SearchResults> =
        resultOf {
            val raw = innerTube.search(MyTubeClient.WEB_REMIX, query, SearchFilter.forDomain(filter)?.value, authState = authState)
            parseSearch(parse(raw), query)
        }

    open suspend fun searchMore(
        cursor: String,
        authState: PlaybackAuthState = innerTube.currentAuthState(),
    ): Result<SearchResults> =
        resultOf {
            val raw = innerTube.search(MyTubeClient.WEB_REMIX, continuation = cursor, authState = authState)
            parseSearchMore(parse(raw))
        }

    open suspend fun suggest(
        input: String,
        authState: PlaybackAuthState = innerTube.currentAuthState(),
    ): Result<List<Suggestion>> =
        resultOf {
            val raw = innerTube.getSearchSuggestions(MyTubeClient.WEB_REMIX, input, authState = authState)
            parseSuggestions(parse(raw))
        }

    // ------------------------------------------------------- album/playlist/podcast

    open suspend fun album(
        browseId: String,
        authState: PlaybackAuthState = innerTube.currentAuthState(),
    ): Result<MusicCollection> =
        resultOf {
            val raw = innerTube.browse(MyTubeClient.WEB_REMIX, browseId = browseId, authState = authState)
            parseCollection(parse(raw), browseId, CollectionKind.Album)
        }

    open suspend fun playlist(
        browseId: String,
        authState: PlaybackAuthState = innerTube.currentAuthState(),
    ): Result<MusicCollection> =
        resultOf {
            val id = if (browseId.startsWith("VL")) browseId else "VL$browseId"
            val raw = innerTube.browse(MyTubeClient.WEB_REMIX, browseId = id, authState = authState)
            parseCollection(parse(raw), browseId, CollectionKind.Playlist)
        }

    /**
     * Halaman lanjutan album/playlist/podcast. Untuk podcast, [showTitle]
     * (judul acara dari halaman pertama) dipakai sebagai nama acara tiap
     * episode — respons lanjutan tidak mengulang judul itu.
     */
    open suspend fun collectionMore(
        kind: CollectionKind,
        cursor: String,
        id: String,
        showTitle: String = "",
        authState: PlaybackAuthState = innerTube.currentAuthState(),
    ): Result<MusicCollection> =
        resultOf {
            val raw = innerTube.browse(MyTubeClient.WEB_REMIX, continuation = cursor, authState = authState)
            val root = parse(raw)
            when (kind) {
                CollectionKind.Podcast -> {
                    val more = parsePodcastMore(root, showTitle)
                    MusicCollection(
                        kind = kind,
                        id = id,
                        title = "",
                        subtitle = "",
                        thumbnails = emptyList(),
                        tracks = more.tracks,
                        cursor = more.token.takeIf { it.isNotEmpty() },
                    )
                }
                else -> {
                    val more = parseCollectionMore(root)
                    MusicCollection(
                        kind = kind,
                        id = id,
                        title = "",
                        subtitle = "",
                        thumbnails = emptyList(),
                        tracks = more.tracks,
                        cursor = more.token.takeIf { it.isNotEmpty() },
                    )
                }
            }
        }

    open suspend fun podcast(
        browseId: String,
        authState: PlaybackAuthState = innerTube.currentAuthState(),
    ): Result<MusicCollection> =
        resultOf {
            val raw = innerTube.browse(MyTubeClient.WEB_REMIX, browseId = browseId, authState = authState)
            parsePodcast(parse(raw), browseId)
                ?: throw NoSuchElementException("podcast $browseId tidak ditemukan")
        }

    // ------------------------------------------------------------------ artis

    open suspend fun artist(
        browseId: String,
        authState: PlaybackAuthState = innerTube.currentAuthState(),
    ): Result<ArtistPage> =
        resultOf {
            val raw = innerTube.browse(MyTubeClient.WEB_REMIX, browseId = browseId, authState = authState)
            parseArtist(parse(raw), browseId)
                ?: throw NoSuchElementException("artis $browseId tidak ditemukan")
        }

    // ------------------------------------------------------------------ jelajah

    open suspend fun home(authState: PlaybackAuthState = innerTube.currentAuthState()): Result<List<DiscoverySection>> =
        resultOf {
            val raw = innerTube.browse(MyTubeClient.WEB_REMIX, browseId = DISCOVERY_HOME, authState = authState)
            parseHome(parse(raw))
        }

    open suspend fun charts(
        country: String = "ID",
        authState: PlaybackAuthState = innerTube.currentAuthState(),
    ): Result<List<DiscoverySection>> =
        resultOf {
            val raw = innerTube.browse(
                MyTubeClient.WEB_REMIX,
                browseId = DISCOVERY_CHARTS,
                formData = BrowseBody.FormData(listOf(country)),
                authState = authState,
            )
            parseCharts(parse(raw))
        }

    open suspend fun newReleases(authState: PlaybackAuthState = innerTube.currentAuthState()): Result<List<DiscoverySection>> =
        resultOf {
            val raw = innerTube.browse(MyTubeClient.WEB_REMIX, browseId = DISCOVERY_NEW_RELEASES, authState = authState)
            parseNewReleases(parse(raw))
        }

    open suspend fun moods(authState: PlaybackAuthState = innerTube.currentAuthState()): Result<List<MoodGroup>> =
        resultOf {
            val raw = innerTube.browse(MyTubeClient.WEB_REMIX, browseId = DISCOVERY_MOODS, authState = authState)
            parseMoodCategories(parse(raw))
        }

    open suspend fun mood(
        params: String,
        title: String = "",
        authState: PlaybackAuthState = innerTube.currentAuthState(),
    ): Result<MoodPage> =
        resultOf {
            val raw = innerTube.browse(
                MyTubeClient.WEB_REMIX,
                browseId = DISCOVERY_MOOD_CATEGORY,
                params = params,
                authState = authState,
            )
            val root = parse(raw)
            // Judul kategori tidak dikembalikan hulu di halaman ini; pemanggil
            // yang tahu judulnya (dari daftar mood) meneruskannya lewat `title`.
            MoodPage(title, params, parseDiscoverySections(root))
        }

    /** Cari kategori mood berdasarkan judul (untuk `mood()` tanpa `params` langsung). */
    fun findMood(moods: List<MoodGroup>, title: String) = findMoodCategory(moods, title)

    // ------------------------------------------------------------- next / track

    open suspend fun next(
        videoId: String,
        playlistId: String? = null,
        authState: PlaybackAuthState = innerTube.currentAuthState(),
    ): Result<Track> =
        resultOf {
            val pid = playlistId ?: "RDAMVM$videoId"
            val raw = innerTube.next(MyTubeClient.WEB_REMIX, videoId = videoId, playlistId = pid, authState = authState)
            parseNext(parse(raw), videoId)
        }

    /**
     * Track + upNext (`next`) dengan loudness dari `player` (paralel,
     * best-effort, timeout 5 detik — gagal = lagu tanpa loudness, bukan
     * error). Bila `next` diblokir (mis. 403), metadata diambil dari
     * `player` (`videoDetails`); upNext kosong, lyricsId kosong.
     * Pola ini sama dengan `loadTrack`/`fetchPlayerTrack` Worker:
     * `next` dan probe `player` jalan PARALEL, dan bila `next` gagal,
     * fallback memakai hasil `player` yang SUDAH dipanggil paralel
     * (tanpa subrequest tambahan). Bila `player` juga gagal, tidak ada
     * sumber metadata tersisa → gagal (fail closed, jangan karang data).
     */
    /**
     * Telemetri putar (Tahap 9, C2-21 — ArchiveTune `YouTube.kt:2198-2225`):
     * GET ke URL `playbackTracking` dengan `cpn` acak 16 karakter (alfabet
     * base64url ala AT). Mencatat putar di riwayat YouTube (bila login).
     * Best-effort: gagal apa pun → `Result.failure`, pemanggil menelan.
     */
    open suspend fun registerPlayback(
        playbackTracking: String,
        playlistId: String? = null,
        authState: PlaybackAuthState = innerTube.currentAuthState(),
    ): Result<Unit> = runCatching {
        val cpn = (1..16)
            .map { CPN_ALPHABET[Random.Default.nextInt(0, CPN_ALPHABET.length)] }
            .joinToString("")
        innerTube.registerPlayback(
            url = playbackTracking,
            cpn = cpn,
            playlistId = playlistId,
            authState = authState,
        )
    }

    open suspend fun track(
        videoId: String,
        authState: PlaybackAuthState = innerTube.currentAuthState(),
    ): Result<Track> =
        resultOf {
            coroutineScope {
                val nextDeferred = async { next(videoId, authState = authState).getOrNull() }
                val playerDeferred = async { fetchPlayerTrack(videoId, authState) }
                val nextTrack = nextDeferred.await()
                val playerTrack = playerDeferred.await()
                if (nextTrack != null) {
                    nextTrack.copy(loudnessDb = playerTrack?.loudnessDb ?: nextTrack.loudnessDb)
                } else {
                    playerTrack?.takeIf { it.song != null }
                        ?: throw NoSuchElementException(
                            "track $videoId tidak ditemukan (next diblokir dan player tidak memberi metadata)",
                        )
                }
            }
        }

    /** Panggilan `player` best-effort untuk loudness/metadata; gagal apa pun → null. */
    private suspend fun fetchPlayerTrack(
        videoId: String,
        authState: PlaybackAuthState = innerTube.currentAuthState(),
    ): Track? =
        withTimeoutOrNull(LOUDNESS_TIMEOUT_MS) {
            runCatching {
                val raw = innerTube.player(MyTubeClient.ANDROID, videoId, authState = authState)
                parsePlayerTrack(parse(raw), videoId)
            }.onFailure { if (it is CancellationException) throw it }.getOrNull()
        }

    // ------------------------------------------------------------------ radio

    open suspend fun radio(
        videoId: String? = null,
        playlistId: String? = null,
        authState: PlaybackAuthState = innerTube.currentAuthState(),
    ): Result<RadioPageResult> =
        resultOf {
            val pid = playlistId ?: videoId?.let { "RDAMVM$it" }
                ?: throw IllegalArgumentException("radio butuh videoId atau playlistId")
            val raw = innerTube.next(MyTubeClient.WEB_REMIX, videoId = videoId, playlistId = pid, authState = authState)
            val mix = parseRadio(parse(raw))
            RadioPageResult(mix.songs, pid, videoId ?: "", mix.token.takeIf { it.isNotEmpty() })
        }

    open suspend fun radioMore(
        cursor: String,
        authState: PlaybackAuthState = innerTube.currentAuthState(),
    ): Result<RadioPageResult> =
        resultOf {
            val raw = innerTube.next(MyTubeClient.WEB_REMIX, continuation = cursor, authState = authState)
            val mix = parseRadioMore(parse(raw))
            RadioPageResult(mix.songs, "", "", mix.token.takeIf { it.isNotEmpty() })
        }
    // ------------------------------------------------------------------ lirik

    open suspend fun lyrics(
        lyricsId: String,
        authState: PlaybackAuthState = innerTube.currentAuthState(),
    ): Result<String> =
        resultOf {
            val raw = innerTube.browse(MyTubeClient.WEB_REMIX, browseId = lyricsId, authState = authState)
            parseLyrics(parse(raw))
        }

    /**
     * Transkrip sinkron (`get_transcript`, Tahap 4) -> teks LRC
     * (`[mm:ss.mmm]teks` per baris). Tanpa PoToken di tahap 1; bila
     * YouTube menolak tanpa token/visitorData, hasilnya gagal (Result)
     * dan pemanggil (LyricsRepository) jatuh ke `null`, bukan error.
     */
    open suspend fun transcript(videoId: String): Result<String> =
        resultOf {
            val raw = innerTube.getTranscript(MyTubeClient.WEB_REMIX, videoId)
            parseTranscript(parse(raw))
        }

    // ------------------------------------------------------------------ link

    /** Link YouTube → tujuan buka; murni parsing lokal, tanpa jaringan. */
    open fun resolveUrl(url: String): ResolvedLink? = resolveYoutubeUrl(url)

    companion object {
        const val DISCOVERY_HOME = "FEmusic_home"
        const val DISCOVERY_CHARTS = "FEmusic_charts"
        const val DISCOVERY_NEW_RELEASES = "FEmusic_new_releases"
        const val DISCOVERY_MOODS = "FEmusic_moods_and_genres"
        const val DISCOVERY_MOOD_CATEGORY = "FEmusic_moods_and_genres_category"

        /** Alfabet `cpn` telemetri putar (Tahap 9, C2-21 — AT `YouTube.registerPlayback`). */
        const val CPN_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"

        /** Batas tunggu probe loudness untuk `track()` — sama dengan Worker. */
        const val LOUDNESS_TIMEOUT_MS = 5_000L

        /** Panjang prefix XSSI `)]}'` di awal badan `sw.js_data`. */
        const val XSSI_PREFIX_LENGTH = 5

        /** Status HTTP sukses — dipakai cuplikan "respons kosong" `accountInfo()`. */
        const val HTTP_OK = 200

        /**
         * Pola `visitorData` di `sw.js_data` (ArchiveTune `YouTube.kt:2736`):
         * diawali `Cgt`/`Cgs`. ArchiveTune menulisnya `^Cg[t|s]` (kelas
         * karakter berisi `|` literal) — di sini ditulis `^Cg[ts]` yang
         * sama maknanya untuk data asli.
         */
        private val VISITOR_DATA_REGEX = Regex("^Cg[ts]")
    }
}

/**
 * `account/account_menu` membalas 200 tapi tidak berisi akun yang bisa
 * dipakai (badan bukan JSON, atau `parseAccountInfo` null) — PLAN
 * music-login-raw-error. [rawSnippet] adalah cuplikan badan mentah yang
 * SUDAH di-scrub (maks 500 karakter, tanpa cookie/token), atau
 * "respons kosong, status 200" bila badan kosong.
 *
 * Pesan HARUS mengandung "info akun tidak ditemukan": classifier
 * `MusicLoginRepository` yang sudah ada memetakan persis frasa itu ke
 * `MusicLoginError.UnrecognizedResponse`.
 */
class UnrecognizedAccountResponseException(
    val rawSnippet: String,
) : IllegalStateException("info akun tidak ditemukan")
