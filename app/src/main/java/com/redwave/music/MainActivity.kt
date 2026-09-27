package com.redwave.music

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private var controller: MediaController? = null
    private val progressHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val progressTick = object : Runnable {
        override fun run() {
            pushPlaybackState()
            progressHandler.postDelayed(this, 500)
        }
    }
    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val clip = data.clipData
        val uris = if (clip != null) (0 until clip.itemCount).map { clip.getItemAt(it).uri } else listOfNotNull(data.data)
        val arr = JSONArray()
        uris.forEach { uri ->
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            arr.put(songFromUri(uri))
        }
        web.post { web.evaluateJavascript("window.redwaveReceiveSongs(${JSONObject.quote(arr.toString())})", null) }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { scanMusic() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.webViewClient = WebViewClient()
        web.addJavascriptInterface(Bridge(), "RedwaveAndroid")
        web.loadUrl("file:///android_asset/index.html")
        setContentView(web)
        connectController()
        if (intent?.action == Intent.ACTION_VIEW) intent.data?.let { playUri(it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) intent.data?.let { playUri(it) }
    }

    private fun connectController() {
        val token = SessionToken(this, android.content.ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener({
            try {
                controller = future.get()
                controller?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        pushPlaybackState()
                        if (isPlaying) progressHandler.post(progressTick) else progressHandler.removeCallbacks(progressTick)
                    }
                })
            } catch (_: Exception) {}
        }, MoreExecutors.directExecutor())
    }

    private fun pushPlaybackState() {
        val c = controller ?: return
        val state = JSONObject().apply {
            put("isPlaying", c.isPlaying)
            put("position", c.currentPosition)
            put("duration", if (c.duration > 0) c.duration else 0)
        }
        web.post { web.evaluateJavascript("window.redwavePlaybackState(${JSONObject.quote(state.toString())})", null) }
    }

    override fun onDestroy() {
        progressHandler.removeCallbacks(progressTick)
        controller?.release()
        super.onDestroy()
    }

    inner class Bridge {
        @JavascriptInterface fun requestAudioPermission() {
            val permissions = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            val needed = permissions.filter { ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED }
            if (needed.isEmpty()) scanMusic() else runOnUiThread { permissionLauncher.launch(needed.toTypedArray()) }
        }
        @JavascriptInterface fun scanMusic() { runOnUiThread { this@MainActivity.scanMusic() } }
        @JavascriptInterface fun openPicker() { runOnUiThread { this@MainActivity.openPicker() } }
        @JavascriptInterface fun play(uri: String) { playUri(Uri.parse(uri)) }
        @JavascriptInterface fun pause() { startServiceCompat(Intent(this@MainActivity, PlaybackService::class.java).setAction(PlaybackService.ACTION_PAUSE)) }
        @JavascriptInterface fun resume() { startServiceCompat(Intent(this@MainActivity, PlaybackService::class.java).setAction(PlaybackService.ACTION_RESUME)) }
        @JavascriptInterface fun next() { startServiceCompat(Intent(this@MainActivity, PlaybackService::class.java).setAction(PlaybackService.ACTION_NEXT)) }
        @JavascriptInterface fun previous() { startServiceCompat(Intent(this@MainActivity, PlaybackService::class.java).setAction(PlaybackService.ACTION_PREVIOUS)) }
        @JavascriptInterface fun setShuffle(value: String) {}
        @JavascriptInterface fun setRepeat(value: String) {}
    }

    private fun openPicker() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "audio/*"; putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        picker.launch(i)
    }

    private fun playUri(uri: Uri) {
        startServiceCompat(Intent(this, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_PLAY; data = uri
        })
    }

    private fun startServiceCompat(intent: Intent) {
        ContextCompat.startForegroundService(this, intent)
    }

    private fun songFromUri(uri: Uri): JSONObject {
        val o = JSONObject().apply { put("id", uri.toString()); put("uri", uri.toString()); put("title", uri.lastPathSegment?.substringAfterLast('/') ?: "Canción"); put("artist", "Desconocido"); put("album", "Sin álbum") }
        contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) o.put("name", c.getString(0)) }
        return o
    }

    private fun scanMusic() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        if (Build.VERSION.SDK_INT < 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) return
        val arr = JSONArray()
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.RELATIVE_PATH)
        contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            while (c.moveToNext()) {
                val uri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, c.getLong(id).toString())
                arr.put(JSONObject().apply {
                    put("id", c.getLong(id).toString()); put("uri", uri.toString()); put("name", c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)))
                    put("title", c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE))); put("artist", c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST))); put("album", c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)))
                    put("duration", c.getLong(c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION))); put("relativePath", c.getString(c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)))
                })
            }
        }
        web.post { web.evaluateJavascript("window.redwaveReceiveSongs(${JSONObject.quote(arr.toString())})", null) }
    }
}
