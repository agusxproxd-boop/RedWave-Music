package com.redwave.music

import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    companion object { const val ACTION_PLAY="com.redwave.music.PLAY"; const val ACTION_PAUSE="com.redwave.music.PAUSE"; const val ACTION_RESUME="com.redwave.music.RESUME"; const val ACTION_NEXT="com.redwave.music.NEXT"; const val ACTION_PREVIOUS="com.redwave.music.PREVIOUS" }
    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null
    override fun onCreate() {
        super.onCreate()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisyEnabled(true)
            .build()
        session = MediaSession.Builder(this, player).setCallback(object: MediaSession.Callback {}).build()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when(intent?.action) {
            ACTION_PLAY -> intent.data?.let { player.setMediaItem(MediaItem.fromUri(it)); player.prepare(); player.play() }
            ACTION_PAUSE -> player.pause()
            ACTION_RESUME -> player.play()
            ACTION_NEXT -> player.seekToNextMediaItem()
            ACTION_PREVIOUS -> player.seekToPreviousMediaItem()
        }
        return START_STICKY
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onDestroy() { session?.release(); player.release(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)
}
