package com.rosyidputrar.taskify.core.media

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import com.rosyidputrar.taskify.core.innertube.MyTube
import com.rosyidputrar.taskify.core.media.sponsorblock.SponsorBlockRepository
import com.rosyidputrar.taskify.core.media.mytube.DirectStream
import com.rosyidputrar.taskify.core.media.mytube.DirectStreamResolver
import com.rosyidputrar.taskify.core.media.mytube.FailedStreamClients
import com.rosyidputrar.taskify.core.media.mytube.PlaybackStreamRecoveryTracker
import com.rosyidputrar.taskify.core.media.mytube.PoTokenEnsurer
import com.rosyidputrar.taskify.core.media.mytube.ResolveCache
import com.rosyidputrar.taskify.core.media.mytube.ResolveCacheKey
import com.rosyidputrar.taskify.core.media.mytube.YoutubeiStreamResolver
import com.rosyidputrar.taskify.core.media.mytube.effectiveQuality
import com.rosyidputrar.taskify.core.media.mytube.MusicLoginEvents
import com.rosyidputrar.taskify.core.media.mytube.StreamFailure
import com.rosyidputrar.taskify.core.media.mytube.StreamFailureException
import com.rosyidputrar.taskify.core.media.mytube.MyTubeAuthStore
import com.rosyidputrar.taskify.core.media.mytube.streamUrlClientKey
import com.rosyidputrar.taskify.core.media.mytube.streamUrlParamPresence
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
 * Sumber data direct (rewrite, Tahap 6): katalog + `track()` lewat fasad [MyTube],
 * stream audio lewat [DirectStreamResolver], SponsorBlock lewat [SponsorBlockRepository].
 * Tidak ada lagi panggilan ke Worker dari pemutar ini.
 *
 * Auth (gap Tahap 5): [DirectStreamResolver] punya instance `InnerTube` sendiri yang
 * tidak disinkronkan bootstrap — karena itu SETIAP panggilan ke `myTube`/`streamResolver`
 * meneruskan [authState] eksplisit dari [MyTubeAuthStore] (dibaca segar dari
 * SharedPreferences `"youtube_auth"`), bukan mengandalkan state internal transport.
 * Instance tidak disatukan di sini supaya DI Tahap 7 bebas memilih bentuknya;
 * explicit passing tetap benar apa pun bentuk DI-nya.
 *
 * Antrean disimpan ke [MusicPrefs] tiap kali berubah dan dipulihkan (dalam keadaan jeda) saat
 * proses dibuat ulang; URL tidak ikut disimpan.
 */
class MusicPlayer(
    private val context: Context,
    private val myTube: MyTube = MyTube(),
    private val streamResolver: DirectStreamResolver = DirectStreamResolver(),
    private val sponsorBlock: SponsorBlockRepository = SponsorBlockRepository(),
    private val authStore: MyTubeAuthStore = MyTubeAuthStore(context),
    private val prefs: MusicPrefs = MusicPrefs(context),
    private val now: () -> Long = System::currentTimeMillis,
    /** Hook sesi kedaluwarsa (PLAN C.6): repo login; null = tanpa hook (tes lama). */
    private val loginEvents: MusicLoginEvents? = null,
    /**
     * Resolver youtubei.js (Tahap 3); null = jalur direct saja (tes lama).
     * Bila ada: resolve mencoba youtubei dulu, gagal apa pun → jatuh ke
     * jalur direct. Prefetch tetap direct saja sampai preloader Tahap 6.
     */
    private val youtubeiResolver: YoutubeiStreamResolver? = null,
    /** Cache hasil resolve (Tahap 6, C2-16/17); null = tanpa cache (tes lama). */
    private val resolveCache: ResolveCache = ResolveCache(),
    /**
     * Blacklist klien gagal (Tahap 7, C2-19); satu instance per proses (DI)
     * supaya blacklist berlaku di semua jalur resolve.
     */
    private val failedStreamClients: FailedStreamClients = FailedStreamClients(),
    /**
     * Ensurer PoToken (Tahap 7): di-invalidasi bila bot terdeteksi saat
     * putar atau semua klien gagal, supaya re-resolve mint token baru.
     */
    private val poTokenEnsurer: PoTokenEnsurer? = null,
    /**
     * Segarkan `visitorData` secara paksa (Tahap 7, C2-20); null = tanpa
     * segarkan sesi (lapis AllClientsFailed dilewati).
     */
    private val refreshVisitorData: (suspend () -> String?)? = null,
    /**
     * Jaringan sedang metered (Tahap 8, C2-25 — ArchiveTune `LowDataMode`):
     * bawaan memakai `ConnectivityManager.isActiveNetworkMetered`
     * (tanpa izin tambahan). `Auto` di jaringan metered turun ke `High`.
     */
    private val isNetworkMetered: () -> Boolean = {
        (context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager)
            ?.isActiveNetworkMetered == true
    },
    /**
     * Telemetri putar (Tahap 9, C2-21); `null` = tanpa telemetri (tes).
     * Produksi di-wire di `AppModule` ke `MyTube.registerPlayback`.
     * Dipanggil sekali saat lagu mulai berbunyi, dengan URL tracking dari
     * hasil resolve lagu itu; gagal apa pun ditelan (best-effort).
     */
    private val registerPlayback: (suspend (videoId: String, trackingUrl: String, auth: PlaybackAuthState) -> Boolean)? = null,
    /**
     * Apakah perangkat sedang online (Agen B, tunggu-jaringan): bawaan
     * memakai `ConnectivityManager` aktif (tanpa izin tambahan), sama
     * seperti `NetworkMonitor` di `:core:auth`. Dipakai jalan cepat
     * sebelum menunggu [onlineFlow].
     */
    private val isOnline: () -> Boolean = {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = manager?.getNetworkCapabilities(manager.activeNetwork)
        caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    },
    /**
     * Aliran status online (Agen B, tunggu-jaringan): produksi di-wire di
     * `AppModule` ke `NetworkMonitor.online` (observer
     * `ConnectivityManager.NetworkCallback` yang sudah ada — tidak dibuat
     * observer baru di pemutar); `null` = tanpa observer (tes lama) —
     * offline langsung resolve seperti sebelumnya.
     */
    private val onlineFlow: Flow<Boolean>? = null,
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

    /** Niat putar untuk auto-resume setelah gangguan sesaat (Agen B). */
    private val autoResumeTracker = PlaybackAutoResumeTracker()
    /** Penghitung galat beruntun untuk auto-skip maks 5 (Agen B). */
    private val consecutiveErrorTracker = ConsecutivePlaybackErrorTracker()
    private var resolveJob: Job? = null
    private var resolving: String? = null
    private var tickJob: Job? = null
    private var noticeJob: Job? = null
    private var loadedId: String? = null
    private var loadedUrl: String? = null
    /** Waktu (epoch ms) URL yang sedang dimuat mulai diputar — untuk jeda resolve→putar di diagnostik 403. */
    private var loadedAtMs: Long = 0
    /** Sekali-per-lagu: jatah pemulihan galat putar (Tahap 7, C2-19 — ArchiveTune `PlaybackStreamRecoveryTracker`). */
    private val recoveryTracker = PlaybackStreamRecoveryTracker()
    /**
     * Diagnostik stream terakhir yang gagal diputar (Agen C, INVESTIGASI-C):
     * dicatat di [handlePlayerError] selagi stream/URL masih ada di state,
     * dipakai sebagai fallback bila galat berikut (atau galat resolve
     * berikut) datang setelah state dibersihkan. Hanya berisi host, nama
     * param, itag/mime/klien/angka — tanpa URL lengkap, cookie, atau token.
     * Dibersihkan saat logout ([wipe]).
     */
    private var lastFailedStreamAttempt: LastStreamAttempt? = null
    private var recoveryJob: Job? = null
    /** URL telemetri putar per lagu (Tahap 9, C2-21), dari hasil resolve; dibersihkan di `wipe()`. */
    private val playbackTrackingUrls = mutableMapOf<String, String>()
    /** Lagu yang telemetri putarnya sudah dikirim pada pemutaran ini; direset tiap `load()` lagu baru. */
    private var playbackRegisteredFor: String? = null
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
            // identitas (UA/Origin/Referer) diturunkan dari param `c` URL,
            // tanpa cookie, request pertama selalu `Range` berbatas lewat
            // ChunkedDataSource 8 MB di dalam ResolvingDataSource yang
            // mengisi length dari contentLength hasil resolve.
            // Transport media = OkHttpDataSource di atas OkHttpClient yang
            // SAMA dengan engine Ktor InnerTube (perbaikan 403 ikatan IP,
            // 18 Nov 2026): request `player` dan request media berbagi DNS
            // + connection pool, jadi tidak lagi bisa keluar lewat keluarga
            // IP yang berbeda.
            // Cache media (Tahap 6, C2-22): byte audio yang sudah diunduh
            // disimpan di disk (LRU 100 MB) supaya putar ulang / seek
            // mundur tidak mengunduh lagi; kunci = URL (berisi expire/sig
            // baru tiap resolve, jadi URL lama tidak pernah dipakai lagi).
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(
                        // Wiring lengkap ada di MediaRequestHeaders
                        // (dipakai juga wire test, supaya tidak melenceng).
                        MediaRequestHeaders.createPlayerDataSourceFactory(mediaCache),
                    ),
            )
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            // C2-31: audio-focus ditangani media3 lewat `handleAudioFocus=true`
            // di atas (jeda saat fokus hilang permanen, volume diturunkan saat
            // ducking — padanan `audioFocusVolumeFactor` AT) + noisy di bawah.
            .setHandleAudioBecomingNoisy(true)
            .build().also { it.addListener(listener) }
    }

    /**
     * Cache byte media di disk (Tahap 6, C2-22): dibuat malas saat pemutar
     * ExoPlayer pertama dibuat. Satu instance per folder selama proses hidup
     * (SimpleCache melempar bila dua instance memakai folder yang sama, dan
     * pemutar dibuat ulang tiap pergantian sesi/proses).
     */
    private val mediaCache: androidx.media3.datasource.cache.SimpleCache by lazy { sharedMediaCache(context) }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                recoveryTracker.onPlaybackRecovered(_state.value.current?.videoId)
                // Tahap 9 (C2-21): lagu mulai berbunyi → catat putar sekali.
                registerPlaybackOnce(_state.value.current?.videoId)
            }
            val s = _state.value
            if (isPlaying) dispatch(YtEvent.Playing)
            else if (s.status == YtStatus.Playing && player.playbackState != Player.STATE_ENDED) { dispatch(YtEvent.Paused); savePosition(force = true) }
            if (isPlaying) startTicker()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                // Pemutaran pulih (Tahap 7, C2-19 — ArchiveTune):
                // jatah pemulihan sekali-per-lagu kembali segar.
                recoveryTracker.onPlaybackRecovered(_state.value.current?.videoId)
            }
            if (playbackState != Player.STATE_ENDED) return
            if (_sleep.value?.endOfSong == true) stopAtEndOfSong() else dispatch(YtEvent.Ended)
        }

        override fun onPlayerError(error: PlaybackException) {
            handlePlayerError(error)
        }
    }

    /**
     * Telemetri putar sekali per lagu (Tahap 9, C2-21 — ArchiveTune
     * `registerRemotePlaybackHistory`, tanpa pencatatan riwayat lokal):
     * best-effort, tidak pernah memblokir putar atau menampilkan galat.
     * Gagal = URL dibuang supaya pemutaran berikut resolve ulang yang segar
     * (tanpa refresh via metadata ala AT — pemutar ini tidak punya jalur
     * metadata terpisah; resolve berikut memberi URL tracking baru).
     */
    internal fun registerPlaybackOnce(videoId: String?) {
        val id = videoId ?: return
        if (playbackRegisteredFor == id) return
        val url = playbackTrackingUrls[id] ?: return
        playbackRegisteredFor = id
        scope.launch {
            val ok = runCatching { registerPlayback?.invoke(id, url, authState()) }.getOrNull() == true
            if (!ok) playbackTrackingUrls.remove(id)
        }
    }

    /** Simpan URL telemetri putar dari hasil resolve (Tahap 9, C2-21). */
    private fun rememberTracking(videoId: String, stream: DirectStream) {
        stream.playbackTrackingUrl?.let { playbackTrackingUrls[videoId] = it }
    }

    /**
     * Galat ExoPlayer — internal supaya tes bisa memicunya tanpa ExoPlayer
     * sungguhan (listener hanya meneruskan ke sini).
     *
     * Pemulihan berlapis (Tahap 7, C2-19/20/27 — ArchiveTune
     * `MusicService.handlePlaybackError` + `retryPlaybackAfterStreamFailure`
     * + `recoverFromBadStreamPlayerResponse`), sekali per lagu per
     * percobaan putar ([recoveryTracker]):
     * 1. HTTP stream 403/404/410/416 → klien yang memberi URL di-blacklist
     *    10 menit (kecuali URL hampir kedaluwarsa — 403-nya soal umur URL),
     *    lalu re-resolve (pindah klien) dengan posisi dipertahankan.
     * 2. Jaringan transien (timeout/koneksi putus) atau galat parser →
     *    re-resolve sekali tanpa blacklist.
     * 3. Bot terdeteksi saat putar → PoToken di-invalidasi, re-resolve
     *    sekali (mint token baru).
     * 4. Semua klien gagal → segarkan sesi (visitorData + PoToken baru),
     *    lalu re-resolve sekali lagi.
     * Selain itu (termasuk percobaan kedua yang tetap gagal, dan siaran
     * langsung yang URL-nya bukan dari resolver) langsung jadi galat
     * mentah seperti sebelumnya — jangan disembunyikan, jangan loop.
     *
     * Beda dari ArchiveTune (jujur): URL baru hanya DI-STAGE (status
     * Jeda), bukan langsung diputar — pemutaran lanjut saat pengguna
     * menekan putar / Preparing berikut, dan saat itulah posisi yang
     * disimpan dipakai (`load()` + `resumeId`/`resumeMs`). Auto-resume
     * ala `preparePlaybackFromSnapshot` tidak di-porting: di Robolectric
     * URL palsu tidak bisa dimuat ExoPlayer, jadi tidak ada tes yang bisa
     * membuktikan auto-resume tanpa memutus suite.
     */
    internal fun handlePlayerError(error: PlaybackException) {
        val song = _state.value.current ?: return
        val stream = _state.value.currentStream
        val url = _state.value.currentUrl
        // Jeda resolve→putar: dari waktu resolve dicatat di StreamSource
        // sampai URL ini mulai dimuat. `null` = tidak dicatat.
        val resolveToPlayMs = stream?.resolvedAtMs?.let { resolved -> loadedAtMs.takeIf { it > 0 }?.minus(resolved) }
        // Diagnostik percobaan terakhir (Agen C, INVESTIGASI-C): catat
        // stream/URL yang sedang gagal SELAGI masih ada di state — reducer
        // PlaybackFailed membuangnya, jadi galat berikut (atau galat resolve
        // berikut) tidak lagi punya stream untuk dilaporkan. Pemulihan di
        // bawah tidak berubah; ini hanya pengingat diagnostik.
        LastStreamAttempt.from(song.videoId, stream, url, resolveToPlayMs)?.let { lastFailedStreamAttempt = it }
        // Klien yang sudah gagal untuk lagu ini (blacklist Tahap 7):
        // ringkasan singkat untuk baris `klienGagal` di detail mentah.
        val failedClients = failedStreamClients.blockedClientKeys(song.videoId, authState().streamCacheFingerprint)
        // Detail mentah disusun SEBELUM dispatch: reducer PlaybackFailed
        // membuang URL/stream lagu yang gagal, jadi format/host harus
        // dibaca dari state sekarang (bukan sesudahnya). Bila state sudah
        // bersih (galat kedua untuk item yang sama), `lastAttempt` di atas
        // yang mengisi field diagnostik — bukan "tidak diketahui".
        val rawDetail = error.toRawDetail(song.videoId, stream, url, resolveToPlayMs, lastFailedStreamAttempt, failedClients)
        val httpStatus = error.httpStatusCode()
        // Posisi ditangkap SEBELUM loadedId dibersihkan di bawah
        // (`captureRecoveryPositionMs` mengembalikan 0 bila tidak ada
        // lagu yang termuat).
        val positionMs = captureRecoveryPositionMs()
        Log.w(TAG, "pemutaran ${song.videoId} gagal: ${error.errorCodeName} klien=${stream?.resolvedClient ?: "?"} itag=${stream?.itag ?: "?"} param=${streamUrlParamPresence(url)} jedaResolveKePutarMs=${resolveToPlayMs ?: "?"} expiresInSeconds=${stream?.expiresInSeconds ?: "?"}")
        loadedId = null
        loadedUrl = null
        // Siaran langsung tidak di-re-resolve (URL-nya bukan dari resolver).
        if (song.live) {
            dispatch(YtEvent.PlaybackFailed(song.videoId, "Siaran tidak bisa diputar sekarang.", rawDetail))
            return
        }
        val retryableHttp = httpStatus != null &&
            httpStatus in FailedStreamClients.RETRYABLE_STREAM_RESPONSE_CODES
        val transientNetwork = !retryableHttp && error.isTransientNetworkFailure()
        val parserFailure = !retryableHttp && error.isRetryableParserFailure()
        val botDetection = !retryableHttp && isBotDetectionError(error)
        if (!retryableHttp && !transientNetwork && !parserFailure && !botDetection) {
            // Galat biasa → langsung galat (tidak ada lagi fallback konverter).
            dispatch(YtEvent.PlaybackFailed(song.videoId, "Audio gagal diputar. Coba lagi.", rawDetail))
            return
        }
        if (!recoveryTracker.registerRetryAttempt(song.videoId)) {
            Log.w(TAG, "jatah pemulihan ${song.videoId} habis → galat mentah asal")
            dispatch(YtEvent.PlaybackFailed(song.videoId, "Audio gagal diputar. Coba lagi.", rawDetail))
            return
        }
        // Gangguan saat lagu sedang berbunyi (Agen B): catat supaya
        // pemulihan yang berhasil bisa lanjut otomatis; bila pengguna
        // sudah menjeda, pemulihan tetap jalan tanpa lanjut otomatis.
        autoResumeTracker.onInterrupted(song.videoId)
        if (retryableHttp) {
            // Kunci klien dari param `c` URL (paling akurat), jatuh ke
            // nama klien hasil resolve bila URL tanpa `c` (tes/URL aneh).
            val clientKey = streamUrlClientKey(url ?: "")
                ?: stream?.resolvedClient?.let(DirectStreamResolver::clientKeyForName)
            if (clientKey != null && !FailedStreamClients.isExpiredOrNearExpired(url, now())) {
                failedStreamClients.markFailed(song.videoId, clientKey, httpStatus, authState().streamCacheFingerprint)
                Log.w(TAG, "HTTP $httpStatus stream ${song.videoId}: klien $clientKey di-blacklist 10 menit")
            } else {
                Log.w(TAG, "HTTP $httpStatus stream ${song.videoId}: URL hampir kedaluwarsa, klien tidak di-blacklist")
            }
        }
        if (botDetection) {
            // Bot terdeteksi saat putar: PoToken lama dibuang di dalam
            // koroutine pemulihan supaya re-resolve mint yang baru
            // (bukan memakai token yang sama).
            Log.w(TAG, "bot detection saat putar ${song.videoId}: PoToken di-invalidasi, re-resolve sekali")
        }
        Log.w(TAG, "galat putar ${song.videoId} (http=$httpStatus): re-resolve sekali, posisi dipertahankan")
        reResolveAfterFailure(song, rawDetail, positionMs, invalidatePoToken = botDetection)
    }

    /** Posisi putar saat galat (ArchiveTune `capturePlaybackRecoverySnapshot`): posisi ExoPlayer, dijaga 0..durasi. */
    private fun captureRecoveryPositionMs(): Long {
        if (loadedId == null) return 0L
        val duration = player.duration
        val position = player.currentPosition.coerceAtLeast(0L)
        return if (duration > 0L && duration != C.TIME_UNSET) position.coerceAtMost(duration) else position
    }

    /** Jaringan transien (ArchiveTune `isTransientNetworkFailure`): timeout / koneksi putus — layak re-resolve sekali. */
    private fun Throwable.isTransientNetworkFailure(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is SocketTimeoutException ||
                current is ConnectException ||
                current is UnknownHostException
            ) return true
            if (current.message?.contains("Request timeout has expired", ignoreCase = true) == true) return true
            current = current.cause
        }
        return false
    }

    /** Galat parser jarak-jauh yang layak di-retry (ArchiveTune `isRetryableRemoteParserFailure`). */
    private fun PlaybackException.isRetryableParserFailure(): Boolean {
        if (errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
            errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
        ) return true
        var current: Throwable? = cause
        while (current != null) {
            if (current.message?.contains("Skipping atom with length", ignoreCase = true) == true) return true
            if (current is ArrayIndexOutOfBoundsException &&
                current.stackTrace.any { it.className.startsWith("androidx.media3.extractor") }
            ) return true
            current = current.cause
        }
        return false
    }

    /** Bot detection dari pesan galat / rantai penyebab (ArchiveTune `isBotDetectionException`). */
    private fun isBotDetectionError(error: PlaybackException): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is StreamFailureException && current.failure is StreamFailure.BotDetection) return true
            if (isBotDetectionMessage(current.message.orEmpty())) return true
            current = current.cause
        }
        return false
    }

    /** Re-resolve segar sekali setelah galat putar yang bisa pulih; gagal apa pun → galat mentah asal (bukan loop). */
    private fun reResolveAfterFailure(song: Song, rawDetail: String, positionMs: Long, invalidatePoToken: Boolean = false) {
        recoveryJob?.cancel()
        recoveryJob = scope.launch {
            // Offline (Agen B): tunggu jaringan kembali bila ada
            // observer; tanpa observer langsung resolve seperti dulu.
            awaitNetwork()
            if (invalidatePoToken) poTokenEnsurer?.invalidateAll()
            val auth = authState()
            val quality = _settings.value.quality
            val preferMp4a = _settings.value.preferMp4a
            // Tahap 8 (C2-25): kualitas efektif + metered dipakai kunci cache.
            val metered = isNetworkMetered()
            val effective = effectiveQuality(quality, metered)
            val key = ResolveCacheKey(song.videoId, effective, preferMp4a, auth.streamCacheFingerprint, metered)
            // URL lama kena galat: buang dari cache supaya tidak disajikan lagi.
            resolveCache.remove(key)
            var result = resolveForRecovery(song, effective, auth, preferMp4a, metered)
            var stream = result.getOrNull()
            // Semua klien gagal (C2-20/27): segarkan sesi (PoToken baru +
            // visitorData baru → fingerprint baru, jadi blacklist + cache
            // lama tidak berlaku) lalu coba sekali lagi.
            val firstFailure = (result.exceptionOrNull() as? StreamFailureException)?.failure
            if (stream == null && firstFailure is StreamFailure.AllClientsFailed) {
                Log.w(TAG, "semua klien gagal ${song.videoId}: segarkan sesi lalu re-resolve sekali lagi")
                poTokenEnsurer?.invalidateAll()
                runCatching { refreshVisitorData?.invoke() }
                val freshAuth = authState()
                resolveCache.remove(ResolveCacheKey(song.videoId, effective, preferMp4a, freshAuth.streamCacheFingerprint, metered))
                result = resolveForRecovery(song, effective, freshAuth, preferMp4a, metered)
                stream = result.getOrNull()
            }
            // Lagu sudah berganti selagi re-resolve jalan: buang hasilnya, jangan putar lagu lama.
            if (_state.value.current?.videoId != song.videoId) return@launch
            if (stream != null) {
                resolveCache.put(ResolveCacheKey(song.videoId, effective, preferMp4a, authState().streamCacheFingerprint, metered), stream)
                rememberTracking(song.videoId, stream)
                // Posisi dipertahankan (C2-19): load() memakai resume ini
                // saat URL baru dimuat (tekanan putar / Preparing berikut).
                resumeId = song.videoId
                resumeMs = positionMs
                Log.i(TAG, "re-resolve galat putar ${song.videoId} OK klien=${stream.resolvedClient ?: "?"} itag=${stream.itag} lanjutMs=$positionMs param=${streamUrlParamPresence(stream.url)}")
                dispatch(YtEvent.StreamInfo(song.videoId, stream.toStreamSource()))
                dispatch(YtEvent.Ready(song.videoId, stream.url))
                // Pemulihan berhasil setelah gangguan sesaat (Agen B):
                // lanjut otomatis dari posisi terakhir bila lagu ini
                // memang sedang berbunyi saat terputus.
                if (autoResumeTracker.shouldAutoResume(song.videoId)) {
                    autoResumeTracker.onResumed(song.videoId)
                    dispatch(YtEvent.Preparing)
                }
            } else {
                Log.w(TAG, "re-resolve galat putar ${song.videoId} gagal: tampilkan galat mentah asal")
                dispatch(YtEvent.PlaybackFailed(song.videoId, "Audio gagal diputar. Coba lagi.", rawDetail))
            }
        }
    }

    /**
     * Resolve untuk pemulihan: direct SAJA (Tahap 6) — JANGAN youtubei-first
     * di sini, karena cache disk JS bisa mengembalikan URL yang sama yang
     * baru saja gagal. Kegagalan terketik dipertahankan (bukan null) supaya
     * lapis AllClientsFailed bisa menyegarkan sesi.
     */
    private suspend fun resolveForRecovery(
        song: Song,
        quality: com.rosyidputrar.taskify.core.domain.media.MusicQuality,
        auth: com.rosyidputrar.taskify.core.innertube.PlaybackAuthState,
        preferMp4a: Boolean,
        networkMetered: Boolean = false,
    ): Result<com.rosyidputrar.taskify.core.media.mytube.DirectStream> =
        try {
            streamResolver.resolve(song.videoId, quality, auth, preferMp4a, resolveCache.pinnedItag(song.videoId), networkMetered)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
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
        // Lagu berbunyi (juga lewat settle Preparing→Playing): rantai
        // galat putus + niat putar hidup (Agen B).
        if (_state.value.status == YtStatus.Playing) {
            _state.value.current?.videoId?.let { autoResumeTracker.onPlaybackStarted(it) }
            consecutiveErrorTracker.onPlaybackStarted()
        }
        effects()
        // Galat akhir lagu ini (Agen B): lewati ke lagu berikut bila
        // masih di bawah 5 galat beruntun, selain itu biarkan galat
        // tampil seperti sebelumnya.
        if (event is YtEvent.PlaybackFailed || event is YtEvent.Error) maybeSkipAfterError(event)
    }

    /**
     * Auto-skip setelah galat akhir (Agen B): lagu yang gagal dilewati
     * ke lagu berikut supaya antrean tidak macet, maks 5 galat beruntun.
     * Galat ke-5, galat sesi login, lagu terakhir, atau antrean satu
     * lagu TIDAK di-skip — galat tampil seperti sebelumnya (pesan +
     * detail mentah tidak berubah).
     */
    private fun maybeSkipAfterError(event: YtEvent) {
        val s = _state.value
        if (s.status != YtStatus.Error) return
        val skippable = when (event) {
            is YtEvent.PlaybackFailed -> event.videoId == s.current?.videoId
            is YtEvent.Error -> event.skippable
            else -> false
        }
        if (!skippable) return
        if (!consecutiveErrorTracker.onTerminalError()) return
        val next = MusicQueue.nextPosition(s, manual = true)
        if (next == null || next == s.position) return
        Log.i(TAG, "galat beruntun ${consecutiveErrorTracker.count}: lewati ${s.current?.videoId} ke lagu berikut")
        showNotice("Melewati lagu yang gagal diputar")
        dispatch(YtEvent.Next)
    }

    /**
     * Tunggu jaringan bila offline (Agen B, tunggu-jaringan).
     * Kembalikan `true` bila sempat menunggu (status sempat menampilkan
     * "Menunggu jaringan…"), `false` bila langsung lanjut. Bila online,
     * langsung kembali; bila offline dan ada [onlineFlow], tampilkan
     * status menunggu lalu tangguhkan sampai koneksi kembali (bisa
     * dibatalkan: ganti lagu / berhenti). Tanpa observer, langsung
     * kembali — resolve dicoba dan galat jaringan tampil seperti
     * sebelumnya.
     */
    private suspend fun awaitNetwork(): Boolean {
        if (isOnline()) return false
        val flow = onlineFlow ?: return false
        dispatch(YtEvent.Progress(0, WAITING_FOR_NETWORK_LABEL))
        flow.first { it }
        return true
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
            YtStatus.Playing -> {
                // Jeda oleh pengguna (Agen B): auto-resume dimatikan.
                autoResumeTracker.onUserPaused()
                player.pause()
            }
            YtStatus.Paused -> {
                val expiresAt = s.currentStream?.expiresAt
                if (expiresAt != null && expiresAt <= now() + MusicQueue.EXPIRY_MARGIN_MS) {
                    // URL direct ±6 jam sudah habis selama dijeda → minta baru.
                    loadedId = null
                    _state.value = MusicQueue.reduce(s, YtEvent.Expire(now()))
                    dispatch(YtEvent.Preparing)
                } else if (loadedId == s.current?.videoId && player.playbackState != Player.STATE_ENDED && player.playbackState != Player.STATE_IDLE) {
                    // Lanjut langsung dari ExoPlayer (tanpa lewat listener):
                    // niat putar dicatat eksplisit di sini.
                    s.current?.videoId?.let { autoResumeTracker.onPlaybackStarted(it) }
                    player.play()
                }
                else { loadedId = null; dispatch(YtEvent.Preparing) }
            }
            YtStatus.Error -> dispatch(YtEvent.Preparing)
            else -> Unit
        }
    }

    fun stop() {
        resolveJob?.cancel()
        resolving = null
        recoveryJob?.cancel()
        recoveryTracker.reset()
        autoResumeTracker.reset()
        consecutiveErrorTracker.reset()
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

    suspend fun lyrics(lyricsId: String): String = myTube.lyrics(lyricsId, authState()).getOrThrow()

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
        perceptualLoudnessDb = perceptualLoudnessDb,
        resolvedClient = resolvedClient,
        expiresInSeconds = expiresInSeconds,
        resolvedAtMs = now(),
        contentLength = contentLength,
    )

    /**
     * Detail mentah galat resolve + diagnostik percobaan terakhir lagu ini
     * (Agen C, INVESTIGASI-C Jalur B): galat resolve (`AllClientsFailed`
     * dkk.) dibangun tanpa `DirectStream`, jadi detailnya tidak punya baris
     * diagnostik stream — blok percobaan terakhir ditempel supaya error
     * akhir tetap membawa klien/paramUrl/jeda/expires/host/itag percobaan
     * yang gagal diputar. Bila belum pernah ada percobaan putar untuk lagu
     * ini, detail dasar dikembalikan apa adanya.
     */
    private fun resolveFailureRawDetail(base: String?, videoId: String): String? {
        val attempt = lastFailedStreamAttempt?.takeIf { it.videoId == videoId } ?: return base
        val block = formatLastAttemptDiagnostics(attempt) ?: return base
        return if (base.isNullOrBlank()) block else "$base\n$block"
    }

    /** Pesan galat yang jelas untuk tiap kegagalan resolve (PLAN G.4): bukan retry tanpa akhir. */
    private fun streamErrorMessage(failure: StreamFailure): String = when (failure) {
        is StreamFailure.LoginRequired -> "Audio tidak bisa dimuat dari YouTube (login diperlukan)."
        is StreamFailure.InvalidLoginContext -> "Sesi login YouTube tidak lengkap. Masuk lagi."
        is StreamFailure.Unplayable -> "Audio tidak bisa dimuat dari YouTube."
        StreamFailure.NoFormat -> "Audio tidak bisa dimuat dari YouTube (format tidak tersedia)."
        is StreamFailure.Http -> if (failure.status == 429) "YouTube membatasi permintaan, coba lagi sebentar lagi."
            else "Audio tidak bisa dimuat dari YouTube."
        StreamFailure.Timeout -> "Audio tidak bisa dimuat dari YouTube (waktu habis)."
        is StreamFailure.Network -> "Gagal memuat audio. Periksa koneksi."
        StreamFailure.InvalidResponse -> "Audio tidak bisa dimuat dari YouTube."
        is StreamFailure.BotDetection -> "YouTube meminta verifikasi tambahan. Coba lagi sebentar lagi."
        is StreamFailure.AllClientsFailed -> "Semua klien YouTube gagal memuat audio. Coba lagi sebentar lagi."
    }

    /**
     * Resolve stream lagu: cache dulu (Tahap 6), lalu youtubei.js bila
     * tersedia, gagal apa pun → jalur direct (Tahap 3). Kegagalan youtubei
     * TIDAK pernah langsung jadi galat putar selama direct masih bisa —
     * fallback ada di sini, bukan di UI. Itag terakhir video ini dipakai
     * sebagai pinned (format yang sama dipertahankan).
     */
    private suspend fun resolveStream(
        videoId: String,
        quality: com.rosyidputrar.taskify.core.domain.media.MusicQuality,
        auth: com.rosyidputrar.taskify.core.innertube.PlaybackAuthState,
        preferMp4a: Boolean,
    ): Result<com.rosyidputrar.taskify.core.media.mytube.DirectStream> {
        // Tahap 8 (C2-25): kualitas efektif dihitung SEKALI di sini dan
        // dipakai kunci cache + kedua jalur resolve, supaya entri cache
        // tidak tercampur antara metered dan tidak.
        val metered = isNetworkMetered()
        val effective = effectiveQuality(quality, metered)
        val key = ResolveCacheKey(videoId, effective, preferMp4a, auth.streamCacheFingerprint, metered)
        resolveCache.get(key)?.let { rememberTracking(videoId, it); return Result.success(it) }
        val pinned = resolveCache.pinnedItag(videoId)
        val result = if (youtubeiResolver == null) {
            streamResolver.resolve(videoId, effective, auth, preferMp4a, pinned, metered)
        } else {
            youtubeiResolver.resolve(videoId, effective, auth, preferMp4a, pinned, metered).fold(
                onSuccess = { Result.success(it) },
                onFailure = { streamResolver.resolve(videoId, effective, auth, preferMp4a, pinned, metered) },
            )
        }
        result.getOrNull()?.let { resolveCache.put(key, it); rememberTracking(videoId, it) }
        return result
    }

    private fun resolveCurrent(song: Song) {
        // Percobaan putar baru (dari antrean/jeda/galat): jatah pemulihan
        // lagu ini kembali segar (Tahap 7 — lihat `PlaybackStreamRecoveryTracker.reset`).
        // Re-resolve galat putar sendiri TIDAK lewat sini (jalur
        // `reResolveAfterFailure` terpisah), jadi reset ini tidak
        // membatalkan batas sekali-per-percobaan.
        recoveryTracker.reset()
        recoveryJob?.cancel()
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
                // Offline (Agen B): tunggu jaringan kembali bila ada
                // observer; tanpa observer langsung resolve seperti dulu
                // (galat jaringan tampil seperti sebelumnya). Bila sempat
                // menunggu, bersihkan label tunggu sebelum resolve jalan.
                if (awaitNetwork()) dispatch(YtEvent.Progress(0, ""))
                val auth = authState()
                // Komposisi direct (pola Worker `loadTrack` + `tryDirectStream`):
                // `track()` (next + loudness player, paralel di dalam) dan
                // `resolveStream()` jalan BERDAMPINGAN — keduanya independen.
                val trackDeferred = async { if (withTrack) myTube.track(id, auth) else null }
                val streamDeferred = async { resolveStream(id, _settings.value.quality, auth, _settings.value.preferMp4a) }
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
                    // Sesi kedaluwarsa / tidak lengkap (PLAN C.6, C2-12):
                    // resolve kena LoginRequired ATAU InvalidLoginContext
                    // PADAHAL cookie login ada → sesi yang dulu sah sudah
                    // habis / tidak lengkap; beri tahu repo lewat hook (VM
                    // menampilkan prompt login ulang) dan pakai pesan
                    // kedaluwarsa. Bila tidak login, pesan lama tetap
                    // (tidak ada sesi yang bisa habis).
                    // Detail mentah ikut dikirim apa pun pesannya (Agen B):
                    // jenis StreamFailure + status HTTP + pesan exception,
                    // tanpa URL lengkap / cookie / token.
                    // Galat sesi login tidak boleh auto-skip (Agen B):
                    // pengguna harus masuk lagi, bukan melewati lagu.
                    val isLoginFailure = failure is StreamFailure.LoginRequired || failure is StreamFailure.InvalidLoginContext
                    val message = if (isLoginFailure && authState().hasLoginCookie) {
                        loginEvents?.onSessionExpired()
                        if (failure is StreamFailure.InvalidLoginContext) "Sesi login YouTube tidak lengkap. Masuk lagi."
                        else "Sesi login YouTube berakhir. Masuk lagi."
                    } else failure?.let(::streamErrorMessage) ?: "Gagal memuat audio."
                    val rawDetail = resolveFailureRawDetail(
                        if (failure != null) streamFailureRawDetail(failure, exception.cause)
                        else musicErrorRawDetail(exception),
                        id,
                    )
                    if (_state.value.current?.videoId == id) dispatch(YtEvent.Error(message, rawDetail, skippable = !isLoginFailure))
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
                if (_state.value.current?.videoId == id) dispatch(YtEvent.Error(e.message ?: "Gagal memuat audio.", resolveFailureRawDetail(musicErrorRawDetail(e), id)))
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
            // ulang saat gilirannya tiba). Hasil sukses dicatat di cache
            // resolve (Tahap 6) supaya gilirannya nanti tidak resolve ulang.
            //
            // Optimasi Agen A (A1/A2):
            // 1) Cache dulu — lagu yang sama sudah di-resolve dalam sesi ini
            //    (mis. setelah stop() + putar ulang antrean: `urls` state
            //    kosong tapi cache masih segar) tidak perlu resolve jaringan
            //    lagi; PrefetchDone langsung dari cache.
            // 2) Direct dulu (paling murah, tanpa QuickJS), gagal → youtubei
            //    (cerminan `resolveStream` yang youtubei-first — asimetri ini
            //    sengaja: prefetch direct yang sukses menutup sebagian besar
            //    kasus tanpa biaya JS, youtubei hanya menutup sisa saat direct
            //    gagal, supaya pergantian lagu tidak membayar resolve penuh
            //    di saat giliran). Gagal keduanya → PrefetchFailed, lagu itu
            //    di-resolve ulang saat gilirannya tiba seperti sebelumnya.
            val auth = authState()
            val quality = _settings.value.quality
            val preferMp4a = _settings.value.preferMp4a
            // Tahap 8 (C2-25): kualitas efektif + metered dipakai kunci cache
            // supaya prefetch metered tidak disajikan saat tidak metered.
            val metered = isNetworkMetered()
            val effective = effectiveQuality(quality, metered)
            val key = ResolveCacheKey(videoId, effective, preferMp4a, auth.streamCacheFingerprint, metered)
            var stream = resolveCache.get(key)
            if (stream == null) {
                stream = streamResolver.tryResolve(videoId, effective, auth, preferMp4a, resolveCache.pinnedItag(videoId), metered)
                    ?: runCatching {
                        youtubeiResolver?.resolve(videoId, effective, auth, preferMp4a, resolveCache.pinnedItag(videoId), metered)
                    }.getOrNull()?.getOrNull()
            }
            if (stream != null) {
                resolveCache.put(key, stream)
                rememberTracking(videoId, stream)
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
                myTube.track(videoId, authState())
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
                myTube.radioMore(cursor, authState())
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
        // Lagu berganti (Tahap 7, C2-19 — ArchiveTune): jatah pemulihan
        // lagu lama tidak berlaku untuk lagu baru.
        recoveryTracker.onMediaItemChanged(song.videoId)
        // Tahap 9 (C2-21): lagu baru = telemetri putar boleh dikirim lagi.
        if (loadedId != song.videoId) playbackRegisteredFor = null
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
        // Daftarkan contentLength hasil resolve supaya ResolvingDataSource
        // mengisi length DataSpec (request pertama membawa `Range` berbatas,
        // porting ArchiveTune Tahap 1); tanpa ini ExoPlayer GET penuh.
        // Optimasi Agen A (A3): daftarkan juga identitas isi stabil
        // `videoId:itag` supaya SimpleCache dipakai ulang walau URL sudah
        // di-resolve ulang (URL baru tiap resolve); tanpa itag (siaran
        // langsung) kunci cache tetap URL seperti sebelumnya.
        _state.value.streams[song.videoId]?.let { stream ->
            stream.contentLength?.let { ResolvedStreamRegistry.put(url, it) }
            ResolvedStreamRegistry.putIdentity(url, song.videoId, stream.itag)
        }
        val meta = MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist)
            .apply { song.cover.takeIf { it.isNotEmpty() }?.let { setArtworkUri(Uri.parse(it)) } }.build()
        player.setMediaItem(MediaItem.Builder().setMediaId(song.videoId).setUri(url).setMediaMetadata(meta).build(), startMs)
        player.prepare()
        applyVolume()
    }

    /** Volume rata (loudness perseptual dulu, lalu loudnessDb — Tahap 8, C2-26) dikalikan bisu SponsorBlock bila sedang di segmen "mute". */
    private fun applyVolume() {
        val s = _state.value
        val id = s.current?.videoId ?: return
        val stream = s.streams[id]
        val db = stream?.loudnessDb ?: s.details[id]?.loudnessDb
        val gain = if (_settings.value.normalize) Loudness.gain(db, stream?.perceptualLoudnessDb) else 1f
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
        _sleep.value?.endsAt?.let { if (now() >= it) { _sleep.value = null; autoResumeTracker.onUserPaused(); player.pause(); showNotice("Pewaktu tidur: musik dijeda") } }
        savePosition()
    }

    /** Pewaktu "akhir lagu": geser ke lagu berikut tapi jangan diputar. */
    private fun stopAtEndOfSong() {
        _sleep.value = null
        autoResumeTracker.reset()
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
        autoResumeTracker.reset()
        consecutiveErrorTracker.reset()
        sponsor.clear()
        ResolvedStreamRegistry.clear()
        resolveCache.clear()
        playbackTrackingUrls.clear()
        playbackRegisteredFor = null
        lastFailedStreamAttempt = null
        prefs.clear()
        savedKey = null
        _settings.value = MusicSettings()
    }

    companion object {
        const val TAG = "MusicPlayer"

        /** Label status saat antrean putar menunggu jaringan kembali (Agen B). */
        const val WAITING_FOR_NETWORK_LABEL = "Menunggu jaringan…"

        /** Batas cache byte media di disk (Tahap 6, C2-22): 100 MB, LRU. */
        const val MEDIA_CACHE_MAX_BYTES = 100L * 1024L * 1024L

        @Volatile
        private var sharedCache: androidx.media3.datasource.cache.SimpleCache? = null
        @Volatile
        private var sharedCacheDir: String? = null

        /** Satu SimpleCache per folder per proses; folder berubah (mis. tes) -> yang lama dilepas. */
        @Synchronized
        fun sharedMediaCache(context: android.content.Context): androidx.media3.datasource.cache.SimpleCache {
            val dir = java.io.File(context.cacheDir, "media-cache").absolutePath
            val cached = sharedCache
            if (cached != null && sharedCacheDir == dir) return cached
            try { sharedCache?.release() } catch (_: Exception) { /* sudah dilepas */ }
            return androidx.media3.datasource.cache.SimpleCache(
                java.io.File(dir),
                androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor(MEDIA_CACHE_MAX_BYTES),
                androidx.media3.database.StandaloneDatabaseProvider(context),
            ).also { sharedCache = it; sharedCacheDir = dir }
        }
    }
}

/**
 * Klasifikasi pesan bot-detection (ArchiveTune
 * `YTPlayerUtils.isBotDetectionError(reason: String)`, verbatim kecuali
 * nama): "bot" / "unusual traffic" / "automated" / "confirm ... not a" /
 * "not a robot" / "verify ... human".
 */
internal fun isBotDetectionMessage(reason: String): Boolean {
    val lower = reason.lowercase(Locale.US)
    return "bot" in lower ||
        "unusual traffic" in lower ||
        "automated" in lower ||
        "confirm" in lower && "not a" in lower ||
        "not a robot" in lower ||
        "verify" in lower && "human" in lower
}
