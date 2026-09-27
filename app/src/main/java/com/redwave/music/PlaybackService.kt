package com.redwave.music

import android.content.Intent
import android.os.IBinder
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import org.json.JSONArray

class PlaybackService : MediaSessionService() {
    companion object {
        const val ACTION_PLAY_QUEUE = "com.redwave.music.PLAY_QUEUE"
        const val ACTION_PAUSE = "com.redwave.music.PAUSE"
        const val ACTION_RESUME = "com.redwave.music.RESUME"
        const val ACTION_NEXT = "com.redwave.music.NEXT"
        const val ACTION_PREVIOUS = "com.redwave.music.PREVIOUS"
        const val ACTION_SET_REPEAT = "com.redwave.music.SET_REPEAT"
        const val ACTION_SET_SHUFFLE = "com.redwave.music.SET_SHUFFLE"
        const val EXTRA_URIS = "uris"
        const val EXTRA_INDEX = "index"
        const val EXTRA_VALUE = "value"
    }

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
            .build()
        session = MediaSession.Builder(this, player).setCallback(object : MediaSession.Callback {}).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_QUEUE -> {
                val urisJson = intent.getStringExtra(EXTRA_URIS)
                val startIndex = intent.getIntExtra(EXTRA_INDEX, 0)
                if (urisJson != null) {
                    val arr = JSONArray(urisJson)
                    val items = (0 until arr.length()).map { MediaItem.fromUri(arr.getString(it)) }
                    if (items.isNotEmpty()) {
                        val safeIndex = startIndex.coerceIn(0, items.size - 1)
                        player.setMediaItems(items, safeIndex, 0L)
                        player.prepare()
                        player.play()
                    }
                }
            }
            ACTION_PAUSE -> player.pause()
            ACTION_RESUME -> player.play()
            ACTION_NEXT -> if (player.hasNextMediaItem()) player.seekToNextMediaItem()
            ACTION_PREVIOUS -> if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() else player.seekTo(0)
            ACTION_SET_REPEAT -> player.repeatMode = if (intent.getBooleanExtra(EXTRA_VALUE, false)) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
            ACTION_SET_SHUFFLE -> player.shuffleModeEnabled = intent.getBooleanExtra(EXTRA_VALUE, false)
        }
        return START_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onDestroy() { session?.release(); player.release(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = super.onBind(intent)
}
