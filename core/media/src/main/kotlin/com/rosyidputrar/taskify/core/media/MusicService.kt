package com.rosyidputrar.taskify.core.media

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.rosyidputrar.taskify.core.domain.media.YtStatus
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * MediaSession untuk pemutar musik: notifikasi media, layar kunci, tombol headset, dan Bluetooth.
 * Tombol berikut/sebelumnya diteruskan ke antrean Taskify (ExoPlayer hanya memegang satu lagu).
 */
@OptIn(UnstableApi::class)
class MusicService : MediaSessionService(), KoinComponent {
    private val music: MusicPlayer by inject()
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val forwarding = object : ForwardingPlayer(music.player) {
            override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands().buildUpon()
                .add(Player.COMMAND_SEEK_TO_NEXT).add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS).add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                // URL konverter / siaran langsung tidak mendukung Range: sembunyikan bilah geser notifikasi.
                .apply { if (!music.state.value.seekable) remove(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) }.build()
            override fun isCommandAvailable(command: Int) = when (command) {
                Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM -> music.state.value.seekable && super.isCommandAvailable(command)
                else -> super.isCommandAvailable(command)
            }
            override fun seekTo(positionMs: Long) { music.seekTo(positionMs) }
            override fun seekToNext() = music.next()
            override fun seekToNextMediaItem() = music.next()
            override fun seekToPrevious() = music.prev()
            override fun seekToPreviousMediaItem() = music.prev()
            /**
             * Jeda/putar dari notifikasi & layar kunci lewat `togglePlay`
             * (Agen B, auto-resume): jeda pengguna mematikan niat putar
             * (pemulihan galat tidak boleh lanjut otomatis sesudahnya),
             * putar memakai jalur kedaluwarsa-URL yang sama dengan UI.
             * Penjaga status mencegah perintah ganda membalik putar/jeda.
             */
            override fun play() {
                if (music.state.value.status != YtStatus.Playing) music.togglePlay()
            }
            override fun pause() {
                if (music.state.value.status == YtStatus.Playing) music.togglePlay()
            }
        }
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        session = MediaSession.Builder(this, forwarding).apply { open?.let(::setSessionActivity) }.build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Aplikasi digeser dari Terkini saat tidak memutar → layanan berhenti. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!music.player.playWhenReady) stopSelf()
    }

    override fun onDestroy() {
        session?.release() // ExoPlayer milik MusicPlayer (singleton), tidak dilepas di sini
        session = null
        super.onDestroy()
    }
}
