package com.rosyidputrar.taskify.core.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.rosyidputrar.taskify.core.domain.media.MusicQueue
import com.rosyidputrar.taskify.core.domain.media.Song
import com.rosyidputrar.taskify.core.domain.media.TrackDetails
import com.rosyidputrar.taskify.core.domain.media.YtEvent
import com.rosyidputrar.taskify.core.domain.media.YtState
import com.rosyidputrar.taskify.core.domain.media.YtStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Posisi putar (ms) untuk bilah progres. */
data class PlaybackPosition(val positionMs: Long = 0, val durationMs: Long = 0)

/**
 * Pemutar musik tunggal untuk seluruh aplikasi (padanan `useMusicPlayer` + `music-store`).
 * Keadaan antrean = [MusicQueue.reduce]; efek samping (konversi audio, prefetch lagu berikut,
 * lirik & antrean lanjutan) dijalankan di sini. ExoPlayer hidup di thread utama dan dibungkus
 * [MusicService] (MediaSession) supaya musik tetap berbunyi di latar & punya kontrol notifikasi.
 */
class MusicPlayer(private val context: Context, private val client: MusicClient) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(YtState())
    val state: StateFlow<YtState> = _state.asStateFlow()
    private val _position = MutableStateFlow(PlaybackPosition())
    val position: StateFlow<PlaybackPosition> = _position.asStateFlow()

    val configured: Boolean get() = client.configured

    private var resolveJob: Job? = null
    private var tickJob: Job? = null
    private var loadedId: String? = null

    val player: ExoPlayer by lazy {
        ExoPlayer.Builder(context)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .build().also { it.addListener(listener) }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val s = _state.value
            if (isPlaying) dispatch(YtEvent.Playing) else if (s.status == YtStatus.Playing && player.playbackState != Player.STATE_ENDED) dispatch(YtEvent.Paused)
            if (isPlaying) startTicker()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) dispatch(YtEvent.Ended)
        }

        override fun onPlayerError(error: PlaybackException) {
            // URL konversi bisa kedaluwarsa: lupakan supaya percobaan berikut meminta yang baru.
            _state.value.current?.let { song -> _state.value = _state.value.copy(urls = _state.value.urls - song.videoId) }
            loadedId = null
            dispatch(YtEvent.Error("Audio gagal diputar. Coba lagi."))
        }
    }

    fun dispatch(event: YtEvent) {
        _state.value = MusicQueue.settle(MusicQueue.reduce(_state.value, event))
        effects()
    }

    // ------------------------------------------------------------------ perintah UI

    fun playQueue(songs: List<Song>, startAt: Int) { loadedId = null; dispatch(YtEvent.SetQueue(songs, startAt)) }
    fun next() = dispatch(YtEvent.Next)
    fun prev() {
        // Lebih dari 3 detik berjalan → ulang dari awal (perilaku umum pemutar).
        if (player.currentPosition > 3_000) player.seekTo(0) else dispatch(YtEvent.Prev)
    }
    fun jump(position: Int) = dispatch(YtEvent.Jump(position))
    fun toggleRepeat() = dispatch(YtEvent.ToggleRepeat)
    fun toggleShuffle() = dispatch(YtEvent.ToggleShuffle())
    fun toggleAutoContinue() = dispatch(YtEvent.ToggleAutoContinue)
    fun seekTo(ms: Long) { player.seekTo(ms); _position.value = _position.value.copy(positionMs = ms) }

    fun togglePlay() {
        val s = _state.value
        when (s.status) {
            YtStatus.Playing -> player.pause()
            YtStatus.Paused -> if (loadedId == s.current?.videoId && player.playbackState != Player.STATE_ENDED) player.play() else dispatch(YtEvent.Preparing)
            YtStatus.Error -> dispatch(YtEvent.Preparing)
            else -> Unit
        }
    }

    fun stop() {
        resolveJob?.cancel()
        player.stop()
        player.clearMediaItems()
        loadedId = null
        dispatch(YtEvent.Clear)
        context.stopService(Intent(context, MusicService::class.java))
    }

    suspend fun lyrics(lyricsId: String): String = client.lyrics(lyricsId)

    // ------------------------------------------------------------------ efek

    private fun effects() {
        val s = _state.value
        val song = s.current
        if (s.status == YtStatus.Preparing && song != null && !s.awaitingMore) {
            val url = s.urls[song.videoId]
            if (url != null) load(song, url) else resolveCurrent(song)
        }
        if (s.status == YtStatus.Playing && song != null && loadedId != song.videoId) s.urls[song.videoId]?.let { load(song, it) }
        MusicQueue.prefetchTarget(s)?.let(::prefetch)
        MusicQueue.detailsTarget(s)?.let(::details)
    }

    private fun resolveCurrent(song: Song) {
        if (resolveJob?.isActive == true && resolvingId == song.videoId) return
        resolveJob?.cancel()
        resolvingId = song.videoId
        resolveJob = scope.launch {
            try {
                val url = client.resolveStream(song.videoId) { p, label -> dispatch(YtEvent.Progress(p, label)) }
                if (_state.value.current?.videoId != song.videoId) return@launch
                dispatch(YtEvent.Ready(song.videoId, url))
                load(song, url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_state.value.current?.videoId == song.videoId) dispatch(YtEvent.Error(e.message ?: "Gagal memuat audio."))
            }
        }
    }
    private var resolvingId: String? = null

    private fun prefetch(videoId: String) {
        dispatch(YtEvent.PrefetchStart(videoId))
        scope.launch {
            runCatching { client.resolveStream(videoId) }
                .onSuccess { dispatch(YtEvent.PrefetchDone(videoId, it)) }
                .onFailure { if (it !is CancellationException) dispatch(YtEvent.PrefetchFailed(videoId)) }
        }
    }

    private fun details(videoId: String) {
        dispatch(YtEvent.DetailsStart(videoId))
        scope.launch {
            runCatching { client.track(videoId) }
                .onSuccess { dispatch(YtEvent.DetailsDone(videoId, TrackDetails(it.lyricsId, it.upNext))) }
                .onFailure { if (it !is CancellationException) dispatch(YtEvent.DetailsFailed(videoId)) }
        }
    }

    private fun load(song: Song, url: String) {
        if (loadedId == song.videoId && player.playbackState != Player.STATE_IDLE) {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
            return
        }
        loadedId = song.videoId
        val meta = MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist)
            .apply { song.cover.takeIf { it.isNotEmpty() }?.let { setArtworkUri(Uri.parse(it)) } }.build()
        player.setMediaItem(MediaItem.Builder().setMediaId(song.videoId).setUri(url).setMediaMetadata(meta).build())
        player.prepare()
        player.play()
        // Layanan MediaSession: musik tetap jalan di latar + kontrol di notifikasi & layar kunci.
        runCatching { context.startService(Intent(context, MusicService::class.java)) }
    }

    private fun startTicker() {
        if (tickJob?.isActive == true) return
        tickJob = scope.launch {
            while (true) {
                _position.value = PlaybackPosition(player.currentPosition.coerceAtLeast(0), player.duration.takeIf { it > 0 } ?: 0)
                if (!player.isPlaying) break
                delay(500)
            }
        }
    }

    /** Logout: hentikan musik & lupakan antrean. */
    fun wipe() { if (_state.value.status != YtStatus.Idle || loadedId != null) stop() }
}
