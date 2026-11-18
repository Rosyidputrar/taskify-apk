package com.rosyidputrar.taskify.core.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.rosyidputrar.taskify.core.domain.media.Loudness
import com.rosyidputrar.taskify.core.domain.media.MusicQueue
import com.rosyidputrar.taskify.core.domain.media.MusicSettings
import com.rosyidputrar.taskify.core.domain.media.RadioSource
import com.rosyidputrar.taskify.core.domain.media.RadioStation
import com.rosyidputrar.taskify.core.domain.media.SleepTimer
import com.rosyidputrar.taskify.core.domain.media.Song
import com.rosyidputrar.taskify.core.domain.media.SponsorBlock
import com.rosyidputrar.taskify.core.domain.media.SponsorSegment
import com.rosyidputrar.taskify.core.domain.media.StreamSource
import com.rosyidputrar.taskify.core.domain.media.Track
import com.rosyidputrar.taskify.core.domain.media.TrackDetails
import com.rosyidputrar.taskify.core.domain.media.YtEvent
import com.rosyidputrar.taskify.core.domain.media.YtState
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
import com.rosyidputrar.taskify.core.media.youtube.streamUrlParamPresence
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Posisi putar (ms) untuk bilah progres. */
data class PlaybackPosition(val positionMs: Long = 0, val durationMs: Long = 0)

fun Track.toDetails() = TrackDetails(lyricsId, upNext, loudnessDb, lyrics, lyricsChecked)

/**
 * Pemutar musik tunggal untuk seluruh aplikasi (padanan `useMusicPlayer` + `music-store`).
 * Keadaan antrean = [MusicQueue.reduce]; efek samping (resolve audio direct, prefetch
 * lagu berikut, lanjutan radio, SponsorBlock) dijalankan di sini. ExoPlayer hidup di
 * thread utama dan dibungkus [MusicService] (MediaSession) supaya musik tetap berbunyi di latar.
 *
 * Sumber data direct (rewrite, Tahap 6): katalog + `track()` lewat fasad [YouTube],
 * stream audio lewat [DirectStreamResolver], SponsorBlock lewat [SponsorBlockRepository].
 * Tidak ada lagi panggilan ke Worker dari pemutar ini.
 *
 * Auth (gap Tahap 5): [DirectStreamResolver] punya instance `InnerTube` sendiri yang
 * tidak disinkronkan bootstrap — karena itu SETIAP panggilan ke `youTube`/`streamResolver`
 * meneruskan [authState] eksplisit dari [YouTubeAuthStore] (dibaca segar dari
 * SharedPreferences `"youtube_auth"`), bukan mengandalkan state internal transport.
 * Instance tidak disatukan di sini supaya DI Tahap 7 bebas memilih bentuknya;
 * explicit passing tetap benar apa pun bentuk DI-nya.
 *
 * Antrean disimpan ke [MusicPrefs] tiap kali berubah dan dipulihkan (dalam keadaan jeda) saat
 * proses dibuat ulang; URL tidak ikut disimpan.
 */
class MusicPlayer(
    private val context: Context,
    private val youTube: YouTube = YouTube(),
    private val streamResolver: DirectStreamResolver = DirectStreamResolver(),
    private val sponsorBlock: SponsorBlockRepository = SponsorBlockRepository(),
    private val authStore: YouTubeAuthStore = YouTubeAuthStore(context),
    private val prefs: MusicPrefs = MusicPrefs(context),
    private val now: () -> Long = System::currentTimeMillis,
    /** Hook sesi kedaluwarsa (PLAN C.6): repo login; null = tanpa hook (tes lama). */
    private val loginEvents: MusicLoginEvents? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(YtState())
    val state: StateFlow<YtState> = _state.asStateFlow()
    private val _position = MutableStateFlow(PlaybackPosition())
    val position: StateFlow<PlaybackPosition> = _position.asStateFlow()
    private val _settings = MutableStateFlow(prefs.settings)
    val settings: StateFlow<MusicSettings> = _settings.asStateFlow()
    private val _sleep = MutableStateFlow<SleepTimer?>(null)
    val sleep: StateFlow<SleepTimer?> = _sleep.asStateFlow()
    /** Pesan singkat sesaat (mis. "Melewati sponsor"), dikosongkan otomatis. */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private var resolveJob: Job? = null
    private var resolving: String? = null
    private var tickJob: Job? = null
    private var noticeJob: Job? = null
    private var loadedId: String? = null
    private var loadedUrl: String? = null
    /** Waktu (epoch ms) URL yang sedang dimuat mulai diputar — untuk jeda resolve→putar di diagnostik 403. */
    private var loadedAtMs: Long = 0
    /** Lagu yang sudah sekali di-re-resolve karena 403 (sekali per lagu per percobaan putar, lihat [handlePlayerError]). */
    private var retried403For: String? = null
    private var retry403Job: Job? = null
    /** Poll prefetch per videoId — supaya bisa dibatalkan saat lagu berubah (item 9). */
    private val prefetchJobs = mutableMapOf<String, Job>()
    private val sponsor = mutableMapOf<String, List<SponsorSegment>>()
    private val sponsorPending = mutableSetOf<String>()
    /** Posisi lanjut untuk antrean yang dipulihkan (hanya bila audionya bisa di-seek). */
    private var resumeId: String? = null
    private var resumeMs: Long = 0
    private var savedKey: Any? = null
    private var lastPositionSave = 0L
    private var exoCreated = false

    val player: ExoPlayer by lazy {
        exoCreated = true
        ExoPlayer.Builder(context)
            // Header media ala ArchiveTune (lihat MediaRequestHeaders):
            // Origin/Referer/User-Agent web — tanpa cookie. Transport media
            // = OkHttpDataSource di atas OkHttpClient yang SAMA dengan engine
            // Ktor InnerTube (perbaikan 403 ikatan IP, 18 Nov 2026): request
            // `player` dan request media berbagi DNS + connection pool,
            // jadi tidak lagi bisa keluar lewat keluarga IP yang berbeda.
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(MediaRequestHeaders.createDataSourceFactory()),
            )
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .build().also { it.addListener(listener) }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val s = _state.value
            if (isPlaying) dispatch(YtEvent.Playing)
            else if (s.status == YtStatus.Playing && player.playbackState != Player.STATE_ENDED) { dispatch(YtEvent.Paused); savePosition(force = true) }
            if (isPlaying) startTicker()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState != Player.STATE_ENDED) return
            if (_sleep.value?.endOfSong == true) stopAtEndOfSong() else dispatch(YtEvent.Ended)
        }

        override fun onPlayerError(error: PlaybackException) {
            handlePlayerError(error)
        }
    }

    /**
     * Galat ExoPlayer — internal supaya tes bisa memicunya tanpa ExoPlayer
     * sungguhan (listener hanya meneruskan ke sini).
     *
     * HTTP 403 dari googlevideo (`ERROR_CODE_IO_BAD_HTTP_STATUS` + cause
     * 403) mendapat SATU kesempatan pulih: URL lama dibuang, stream
     * di-resolve ulang secara segar, lalu diputar sekali lagi. Hanya sekali
     * per lagu per percobaan putar ([retried403For]) — percobaan kedua yang
     * tetap 403, atau re-resolve yang gagal, langsung jadi galat mentah
     * seperti sebelumnya (jangan disembunyikan, jangan loop). Siaran
     * langsung tidak di-re-resolve (URL-nya bukan dari resolver).
     *
     * Alasan: 403 googlevideo sering flaky per URL — URL segar dari klien
     * yang sama bisa lolos — dan pola yang sama dipakai ArchiveTune
     * (`retryPlaybackAfterStreamFailure`: invalidate + re-resolve + retry
     * terbatas). Ini ketahanan, bukan perbaikan akar 403.
     */
    internal fun handlePlayerError(error: PlaybackException) {
        val song = _state.value.current ?: return
        val stream = _state.value.currentStream
        val url = _state.value.currentUrl
        // Jeda resolve→putar: dari waktu resolve dicatat di StreamSource
        // sampai URL ini mulai dimuat. `null` = tidak dicatat.
        val resolveToPlayMs = stream?.resolvedAtMs?.let { resolved -> loadedAtMs.takeIf { it > 0 }?.minus(resolved) }
        // Detail mentah disusun SEBELUM dispatch: reducer PlaybackFailed
        // membuang URL/stream lagu yang gagal, jadi format/host harus
        // dibaca dari state sekarang (bukan sesudahnya).
        val rawDetail = error.toRawDetail(song.videoId, stream, url, resolveToPlayMs)
        val isHttp403 = error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS &&
            error.httpStatusCode() == 403
        Log.w(TAG, "pemutaran ${song.videoId} gagal: ${error.errorCodeName} klien=${stream?.resolvedClient ?: "?"} itag=${stream?.itag ?: "?"} param=${streamUrlParamPresence(url)} jedaResolveKePutarMs=${resolveToPlayMs ?: "?"} expiresInSeconds=${stream?.expiresInSeconds ?: "?"}")
        loadedId = null
        loadedUrl = null
        if (isHttp403 && !song.live && retried403For != song.videoId) {
            retried403For = song.videoId
            Log.w(TAG, "403 googlevideo ${song.videoId}: re-resolve sekali (percobaan kedua)")
            reResolveAfter403(song, rawDetail)
            return
        }
        // Direct gagal → langsung galat (tidak ada lagi fallback konverter).
        dispatch(YtEvent.PlaybackFailed(song.videoId, if (song.live) "Siaran tidak bisa diputar sekarang." else "Audio gagal diputar. Coba lagi.", rawDetail))
    }

    /** Re-resolve segar sekali setelah 403; gagal apa pun → galat mentah asal (bukan loop). */
    private fun reResolveAfter403(song: Song, rawDetail: String) {
        retry403Job?.cancel()
        retry403Job = scope.launch {
            val stream = try {
                streamResolver.tryResolve(song.videoId, _settings.value.quality, authState(), _settings.value.preferMp4a)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            // Lagu sudah berganti selagi re-resolve jalan: buang hasilnya, jangan putar lagu lama.
            if (_state.value.current?.videoId != song.videoId) return@launch
            if (stream != null) {
                Log.i(TAG, "re-resolve 403 ${song.videoId} OK klien=${stream.resolvedClient ?: "?"} itag=${stream.itag} param=${streamUrlParamPresence(stream.url)}")
                dispatch(YtEvent.StreamInfo(song.videoId, stream.toStreamSource()))
                dispatch(YtEvent.Ready(song.videoId, stream.url))
            } else {
                Log.w(TAG, "re-resolve 403 ${song.videoId} gagal: tampilkan galat mentah asal")
                dispatch(YtEvent.PlaybackFailed(song.videoId, "Audio gagal diputar. Coba lagi.", rawDetail))
            }
        }
    }

    init {
        // Gate login (PLAN C.6): antrean tersimpan hanya dipulihkan bila ada
        // konteks login pemutaran DAN identitas akun; bila tidak, mulai kosong
        // (prefs TIDAK disentuh — antrean lama tetap tersimpan sampai logout
        // membersihkannya lewat wipe(), bukan di sini).
        if (authStore.loadAuthState().hasPlaybackLoginContext && authStore.loadAccount() != null) {
            prefs.loadQueue()?.let { snap ->
                _state.value = MusicQueue.reduce(_state.value, YtEvent.Restore(snap))
                resumeId = _state.value.current?.videoId
                resumeMs = snap.positionMs
                _position.value = PlaybackPosition(snap.positionMs, (_state.value.current?.seconds ?: 0) * 1000L)
                savedKey = persistKey(_state.value)
            }
        }
    }

    fun dispatch(event: YtEvent) {
        _state.value = MusicQueue.settle(MusicQueue.reduce(_state.value, event))
        effects()
    }

    // ------------------------------------------------------------------ perintah UI

    fun playQueue(songs: List<Song>, startAt: Int, radio: RadioSource? = null) {
        loadedId = null
        resumeId = null
        dispatch(YtEvent.SetQueue(songs, startAt, radio = radio))
    }

    /** Putar satu lagu; antreannya diisi lagu serupa begitu keterangan lagu itu datang. */
    fun playSimilar(song: Song) {
        loadedId = null
        resumeId = null
        dispatch(YtEvent.SetQueue(listOf(song), 0, similar = true))
    }

    fun playStation(station: RadioStation) = playQueue(listOf(station.toSong()), 0)

    fun next() = dispatch(YtEvent.Next)
    fun prev() {
        // Lebih dari 3 detik berjalan → ulang dari awal (perilaku umum pemutar).
        if (loadedId != null && player.currentPosition > 3_000 && _state.value.current?.live != true) player.seekTo(0) else dispatch(YtEvent.Prev)
    }
    fun jump(position: Int) = dispatch(YtEvent.Jump(position))
    fun toggleRepeat() = dispatch(YtEvent.ToggleRepeat)
    fun toggleShuffle() = dispatch(YtEvent.ToggleShuffle())
    fun toggleAutoContinue() = dispatch(YtEvent.ToggleAutoContinue)

    /**
     * Seek hanya bila audionya mendukung `Range` (direct). Siaran langsung tidak bisa di-seek.
     */
    fun seekTo(ms: Long): Boolean {
        val s = _state.value
        if (!s.seekable || loadedId == null) return false
        val song = s.current ?: return false
        val url = s.urls[song.videoId]
        if (url != null && url != loadedUrl) {
            val playing = player.isPlaying
            setMedia(song, url, ms)
            if (playing) player.play()
        } else player.seekTo(ms)
        _position.value = _position.value.copy(positionMs = ms)
        return true
    }

    fun togglePlay() {
        val s = _state.value
        when (s.status) {
            YtStatus.Playing -> player.pause()
            YtStatus.Paused -> {
                val expiresAt = s.currentStream?.expiresAt
                if (expiresAt != null && expiresAt <= now() + MusicQueue.EXPIRY_MARGIN_MS) {
                    // URL direct ±6 jam sudah habis selama dijeda → minta baru.
                    loadedId = null
                    _state.value = MusicQueue.reduce(s, YtEvent.Expire(now()))
                    dispatch(YtEvent.Preparing)
                } else if (loadedId == s.current?.videoId && player.playbackState != Player.STATE_ENDED && player.playbackState != Player.STATE_IDLE) player.play()
                else { loadedId = null; dispatch(YtEvent.Preparing) }
            }
            YtStatus.Error -> dispatch(YtEvent.Preparing)
            else -> Unit
        }
    }

    fun stop() {
        resolveJob?.cancel()
        resolving = null
        retry403Job?.cancel()
        retried403For = null
        cancelPrefetchExcept(null)
        if (exoCreated) { player.stop(); player.clearMediaItems() }
        loadedId = null
        loadedUrl = null
        resumeId = null
        _sleep.value = null
        dispatch(YtEvent.Clear)
        context.stopService(Intent(context, MusicService::class.java))
    }

    fun updateSettings(transform: (MusicSettings) -> MusicSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        prefs.settings = next
        if (loadedId != null) applyVolume()
    }

    /** `null` = matikan; menit; atau akhir lagu ini. */
    fun setSleep(minutes: Int?, endOfSong: Boolean = false) {
        _sleep.value = when {
            endOfSong -> SleepTimer(endOfSong = true)
            minutes != null && minutes > 0 -> SleepTimer(endsAt = now() + minutes * 60_000L)
            else -> null
        }
        if (_state.value.status == YtStatus.Playing) startTicker()
    }

    suspend fun lyrics(lyricsId: String): String = youTube.lyrics(lyricsId, authState()).getOrThrow()

    // ------------------------------------------------------------------ efek

    private fun effects() {
        dropExpired()
        val s = _state.value
        val song = s.current
        if (s.status == YtStatus.Preparing && song != null && !s.awaitingMore) {
            val url = s.urls[song.videoId]
            if (url != null) load(song, url) else resolveCurrent(song)
        }
        if (s.status == YtStatus.Playing && song != null && loadedId != song.videoId) s.urls[song.videoId]?.let { load(song, it) }
        // Lagu berubah → poll prefetch lagu lain tidak lagi dibutuhkan, batalkan (item 9).
        // Yang dipertahankan: lagu saat ini (prefetch-nya jadi sumber resolve, item 5) dan
        // lagu berikut — dihitung dari state tanpa daftar prefetching, karena prefetchTarget
        // mengembalikan null untuk lagu yang sudah di-prefetch (dan itu justru yang dijaga).
        val target = MusicQueue.prefetchTarget(s)
        val nextId = MusicQueue.prefetchTarget(s.copy(prefetching = emptyList()))
        cancelPrefetchExcept(setOfNotNull(song?.videoId, nextId))
        target?.let(::prefetch)
        // Dispatch di atas (PrefetchStart, dst.) memanggil effects() bersarang: baca state TERBARU
        // supaya keterangan/radio yang sudah dimulai di panggilan bersarang tidak diambil dua kali.
        MusicQueue.detailsTarget(_state.value)?.takeIf { resolving != it }?.let(::details)
        MusicQueue.radioTarget(_state.value)?.let(::moreRadio)
        if (song != null && s.status == YtStatus.Playing) sponsorFor(song)
        if (loadedId != null) applyVolume()
        persist(_state.value)
    }

    /**
     * URL direct kedaluwarsa (±6 jam) dibuang supaya diminta ulang. Lagu yang sedang termuat dan
     * berbunyi dibiarkan (ExoPlayer sudah memegang koneksinya); URL-nya dibuang saat dijeda lama.
     */
    private fun dropExpired() {
        val s = _state.value
        val limit = now() + MusicQueue.EXPIRY_MARGIN_MS
        val cur = s.current?.videoId
        val keepCurrent = cur != null && loadedId == cur && s.status == YtStatus.Playing
        val expired = s.streams.filter { (k, v) -> (v.expiresAt ?: Long.MAX_VALUE) <= limit && !(keepCurrent && k == cur) }.keys
        if (expired.isNotEmpty()) _state.value = s.copy(urls = s.urls - expired, streams = s.streams - expired)
    }

    /** Batalkan semua poll prefetch kecuali yang videonya ada di [keep] (null = batalkan semua). */
    private fun cancelPrefetchExcept(keep: Set<String>?) {
        val it = prefetchJobs.entries.iterator()
        while (it.hasNext()) {
            val (id, job) = it.next()
            if (keep == null || id !in keep) { job.cancel(); it.remove() }
        }
    }

    /**
     * State auth tersimpan, dibaca segar tiap panggilan (lihat dok kelas: gap Tahap 5).
     * `visitorData` hasil bootstrap ikut di sini; tanpa login/cookie/PoToken di tahap ini.
     */
    private fun authState(): PlaybackAuthState = authStore.loadAuthState()

    /** [DirectStream] → [StreamSource] domain; `expiresInSeconds` jadi epoch ms. */
    private fun DirectStream.toStreamSource(): StreamSource = StreamSource(
        StreamSource.SOURCE_DIRECT, mimeType, itag,
        expiresAt = expiresInSeconds?.let { now() + it * 1000L },
        loudnessDb = loudnessDb,
        resolvedClient = resolvedClient,
        expiresInSeconds = expiresInSeconds,
        resolvedAtMs = now(),
    )

    /** Pesan galat yang jelas untuk tiap kegagalan resolve (PLAN G.4): bukan retry tanpa akhir. */
    private fun streamErrorMessage(failure: StreamFailure): String = when (failure) {
        is StreamFailure.LoginRequired -> "Audio tidak bisa dimuat dari YouTube (login diperlukan)."
        is StreamFailure.Unplayable -> "Audio tidak bisa dimuat dari YouTube."
        StreamFailure.NoFormat -> "Audio tidak bisa dimuat dari YouTube (format tidak tersedia)."
        is StreamFailure.Http -> if (failure.status == 429) "YouTube membatasi permintaan, coba lagi sebentar lagi."
            else "Audio tidak bisa dimuat dari YouTube."
        StreamFailure.Timeout -> "Audio tidak bisa dimuat dari YouTube (waktu habis)."
        is StreamFailure.Network -> "Gagal memuat audio. Periksa koneksi."
        StreamFailure.InvalidResponse -> "Audio tidak bisa dimuat dari YouTube."
    }

    private fun resolveCurrent(song: Song) {
        // Percobaan putar baru (dari antrean/jeda/galat): jatah re-resolve
        // 403 lagu ini kembali segar. Re-resolve 403 sendiri TIDAK lewat
        // sini (jalur `reResolveAfter403` terpisah), jadi reset ini tidak
        // membatalkan batas sekali-per-percobaan.
        retried403For = null
        retry403Job?.cancel()
        if (resolving == song.videoId) return
        resolveJob?.cancel()
        resolving = null
        // Siaran langsung tidak pernah di-resolve: URL-nya sudah ada dari stasiun.
        if (song.live) { dispatch(YtEvent.Error("Siaran tidak bisa diputar sekarang.")); return }
        // Item 5: lagu ini sedang di-prefetch — jangan mulai resolve kedua
        // (2x resolve + 2x `player` > batas, satu kena 429). Tunggu
        // PrefetchDone: dispatch-nya memanggil effects() lagi, lalu URL yang sudah ada di-load.
        if (song.videoId in _state.value.prefetching) return
        cancelPrefetchExcept(setOf(song.videoId))
        val id = song.videoId
        resolving = id
        val s = _state.value
        // Lagu yang belum punya keterangan: satu komposisi track + stream.
        val withTrack = !song.live && !s.details.containsKey(id) && id !in s.detailsPending
        if (withTrack) dispatch(YtEvent.DetailsStart(id))
        val job = scope.launch(start = CoroutineStart.LAZY) {
            var detailsDone = !withTrack
            try {
                val auth = authState()
                // Komposisi direct (pola Worker `loadTrack` + `tryDirectStream`):
                // `track()` (next + loudness player, paralel di dalam) dan
                // `resolveStream()` jalan BERDAMPINGAN — keduanya independen.
                val trackDeferred = async { if (withTrack) youTube.track(id, auth) else null }
                val streamDeferred = async { streamResolver.resolve(id, _settings.value.quality, auth, _settings.value.preferMp4a) }
                if (withTrack) {
                    // Keterangan datang duluan (walau stream masih jalan): upNext
                    // langsung disambung ke antrean serupa (PR #8) tanpa menunggu audio.
                    val track = trackDeferred.await()?.getOrNull()
                    detailsDone = true
                    if (track != null) dispatch(YtEvent.DetailsDone(id, track.toDetails()))
                    else dispatch(YtEvent.DetailsFailed(id))
                }
                val streamResult = streamDeferred.await()
                val stream = streamResult.getOrNull()
                if (stream == null) {
                    val exception = streamResult.exceptionOrNull()
                    val failure = (exception as? StreamFailureException)?.failure
                    // Sesi kedaluwarsa (PLAN C.6): resolve kena LoginRequired
                    // PADAHAL cookie login ada → sesi yang dulu sah sudah habis;
                    // beri tahu repo lewat hook (VM menampilkan prompt login
                    // ulang) dan pakai pesan kedaluwarsa. Bila tidak login,
                    // pesan lama tetap (tidak ada sesi yang bisa habis).
                    // Detail mentah ikut dikirim apa pun pesannya (Agen B):
                    // jenis StreamFailure + status HTTP + pesan exception,
                    // tanpa URL lengkap / cookie / token.
                    val message = if (failure is StreamFailure.LoginRequired && authState().hasLoginCookie) {
                        loginEvents?.onSessionExpired()
                        "Sesi login YouTube berakhir. Masuk lagi."
                    } else failure?.let(::streamErrorMessage) ?: "Gagal memuat audio."
                    val rawDetail = if (failure != null) streamFailureRawDetail(failure, exception.cause)
                        else musicErrorRawDetail(exception)
                    if (_state.value.current?.videoId == id) dispatch(YtEvent.Error(message, rawDetail))
                    return@launch
                }
                dispatch(YtEvent.StreamInfo(id, stream.toStreamSource()))
                // Diagnostik 403 (logcat saja, tanpa URL lengkap/nilai query):
                // klien mana yang memberi URL + keberadaan nama param.
                Log.i(TAG, "stream $id OK klien=${stream.resolvedClient ?: "?"} itag=${stream.itag} expiresInSeconds=${stream.expiresInSeconds ?: "?"} param=${streamUrlParamPresence(stream.url)}")
                if (_state.value.current?.videoId != id) return@launch
                dispatch(YtEvent.Ready(id, stream.url))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Kegagalan komposisi track/stream di luar StreamFailure
                // (mis. galat jaringan saat track()): detail mentah ikut
                // dikirim supaya tampil di layar musik (Agen B).
                if (_state.value.current?.videoId == id) dispatch(YtEvent.Error(e.message ?: "Gagal memuat audio.", musicErrorRawDetail(e)))
            } finally {
                // Keterangan yang belum datang (galat/batal) dilepas supaya bisa diambil lagi nanti.
                if (!detailsDone && id in _state.value.detailsPending) dispatch(YtEvent.DetailsCancelled(id))
                if (resolveJob == coroutineContext[Job]) resolving = null
            }
        }
        resolveJob = job
        job.start()
    }

    private fun prefetch(videoId: String) {
        if (prefetchJobs[videoId]?.isActive == true) return
        dispatch(YtEvent.PrefetchStart(videoId))
        val job = scope.launch(start = CoroutineStart.LAZY) {
            // Satu lagu berikut, resolve direct saja (pola `tryDirectStream`:
            // gagal apa pun → null, bukan galat — lagu itu akan di-resolve
            // ulang saat gilirannya tiba).
            val stream = streamResolver.tryResolve(videoId, _settings.value.quality, authState(), _settings.value.preferMp4a)
            if (stream != null) {
                dispatch(YtEvent.StreamInfo(videoId, stream.toStreamSource()))
                dispatch(YtEvent.PrefetchDone(videoId, stream.url))
            } else {
                dispatch(YtEvent.PrefetchFailed(videoId))
            }
        }
        prefetchJobs[videoId] = job
        job.invokeOnCompletion { cause ->
            if (prefetchJobs[videoId] == job) prefetchJobs.remove(videoId)
            // Poll yang dibatalkan harus keluar dari daftar prefetching, kalau tidak state
            // mengira lagu itu masih disiapkan dan resolve-nya tidak pernah dimulai lagi.
            if (cause is CancellationException) dispatch(YtEvent.PrefetchCancelled(videoId))
        }
        job.start()
    }

    private fun details(videoId: String) {
        dispatch(YtEvent.DetailsStart(videoId))
        scope.launch {
            try {
                youTube.track(videoId, authState())
                    .onSuccess { dispatch(YtEvent.DetailsDone(videoId, it.toDetails())) }
                    .onFailure { dispatch(YtEvent.DetailsFailed(videoId)) }
            } catch (e: CancellationException) {
                dispatch(YtEvent.DetailsCancelled(videoId))
            }
        }
    }

    private fun moreRadio(cursor: String) {
        dispatch(YtEvent.RadioStart)
        scope.launch {
            try {
                youTube.radioMore(cursor, authState())
                    .onSuccess { dispatch(YtEvent.RadioPage(it.songs, it.cursor)) }
                    .onFailure { dispatch(YtEvent.RadioFailed) }
            } catch (_: CancellationException) {
                // Pengambilan radio batal (lagu berganti): radio tetap pending=false
                // lewat RadioStart berikutnya; tidak ada state galat yang perlu dikirim.
            }
        }
    }

    private fun sponsorFor(song: Song) {
        if (!_settings.value.sponsorBlock || song.live) return
        val id = song.videoId
        if (sponsor.containsKey(id) || !sponsorPending.add(id)) return
        scope.launch {
            // Langsung ke sponsor.ajay.app (Tahap 4): gagal apa pun → tanpa segmen.
            val segs = try { sponsorBlock.segments(id) } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
            sponsor[id] = segs
            sponsorPending -= id
        }
    }

    private fun load(song: Song, url: String) {
        if (loadedId == song.videoId && loadedUrl == url && player.playbackState != Player.STATE_IDLE) {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
            return
        }
        val resume = if (resumeId == song.videoId && _state.value.seekable) resumeMs else 0L
        resumeId = null
        setMedia(song, url, resume)
        player.play()
        // Layanan MediaSession: musik tetap jalan di latar + kontrol di notifikasi & layar kunci.
        runCatching { context.startService(Intent(context, MusicService::class.java)) }
    }

    private fun setMedia(song: Song, url: String, startMs: Long) {
        loadedId = song.videoId
        loadedUrl = url
        loadedAtMs = now()
        val meta = MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist)
            .apply { song.cover.takeIf { it.isNotEmpty() }?.let { setArtworkUri(Uri.parse(it)) } }.build()
        player.setMediaItem(MediaItem.Builder().setMediaId(song.videoId).setUri(url).setMediaMetadata(meta).build(), startMs)
        player.prepare()
        applyVolume()
    }

    /** Volume rata (loudnessDb) dikalikan bisu SponsorBlock bila sedang di segmen "mute". */
    private fun applyVolume() {
        val s = _state.value
        val id = s.current?.videoId ?: return
        val db = s.streams[id]?.loudnessDb ?: s.details[id]?.loudnessDb
        val gain = if (_settings.value.normalize) Loudness.gain(db) else 1f
        val muted = _settings.value.sponsorBlock && SponsorBlock.muted(sponsor[id].orEmpty(), _position.value.positionMs / 1000.0)
        val v = if (muted) 0f else gain
        if (player.volume != v) player.volume = v
    }

    private fun startTicker() {
        if (tickJob?.isActive == true) return
        tickJob = scope.launch {
            while (true) {
                val pos = player.currentPosition.coerceAtLeast(0)
                val dur = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0
                _position.value = PlaybackPosition(pos, dur)
                tick(pos, dur)
                if (!player.isPlaying) break
                delay(500)
            }
        }
    }

    private fun tick(posMs: Long, durMs: Long) {
        val s = _state.value
        val song = s.current ?: return
        // SponsorBlock: hanya bila audio bisa di-seek (direct).
        if (_settings.value.sponsorBlock && s.seekable && s.status == YtStatus.Playing) {
            val durSec = (durMs.takeIf { it > 0 }?.div(1000.0)) ?: song.seconds?.toDouble()
            SponsorBlock.skipTarget(sponsor[song.videoId].orEmpty(), posMs / 1000.0, durSec)?.let { seg ->
                val to = (seg.end * 1000).toLong()
                if (durMs <= 0 || to < durMs - 500) player.seekTo(to) else player.seekTo(durMs)
                showNotice("Melewati ${SponsorBlock.label(seg.category)}")
            }
            applyVolume()
        }
        _sleep.value?.endsAt?.let { if (now() >= it) { _sleep.value = null; player.pause(); showNotice("Pewaktu tidur: musik dijeda") } }
        savePosition()
    }

    /** Pewaktu "akhir lagu": geser ke lagu berikut tapi jangan diputar. */
    private fun stopAtEndOfSong() {
        _sleep.value = null
        val s = _state.value
        val next = MusicQueue.nextPosition(s, manual = true)
        _state.value = if (next != null && next != s.position) s.copy(position = next, status = YtStatus.Paused, percent = 0, label = "")
        else s.copy(status = YtStatus.Paused)
        showNotice("Pewaktu tidur: musik dijeda")
        effects()
    }

    private fun showNotice(text: String) {
        _notice.value = text
        noticeJob?.cancel()
        noticeJob = scope.launch { delay(3_000); _notice.value = null }
    }

    // ------------------------------------------------------------------ simpan antrean

    private fun persistKey(s: YtState): Any = listOf(s.queue.map { it.videoId }, s.order, s.position, s.repeat, s.shuffle, s.autoContinue, s.radio?.playlistId, s.radio?.cursor, s.status == YtStatus.Idle)

    private fun persist(s: YtState) {
        val key = persistKey(s)
        if (key == savedKey) return
        savedKey = key
        prefs.saveQueue(MusicQueue.snapshot(s, if (loadedId == s.current?.videoId) _position.value.positionMs else 0))
    }

    private fun savePosition(force: Boolean = false) {
        val t = now()
        if (!force && t - lastPositionSave < 10_000) return
        lastPositionSave = t
        val s = _state.value
        prefs.saveQueue(MusicQueue.snapshot(s, _position.value.positionMs))
    }

    /** Logout: hentikan musik, lupakan antrean & pengaturan. */
    fun wipe() {
        if (_state.value.status != YtStatus.Idle || loadedId != null) stop()
        sponsor.clear()
        prefs.clear()
        savedKey = null
        _settings.value = MusicSettings()
    }

    companion object {
        const val TAG = "MusicPlayer"
    }
}
