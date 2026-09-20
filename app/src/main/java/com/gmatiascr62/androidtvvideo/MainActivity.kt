package com.gmatiascr62.androidtvvideo

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private var player: ExoPlayer? = null
    private lateinit var root: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        root = FrameLayout(this)
        setContentView(root)
        loadConfig()
    }

    private fun loadConfig() {
        thread {
            try {
                val conn = URL(CONFIG_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.setRequestProperty("Cache-Control", "no-cache")
                val json = conn.inputStream.bufferedReader().use { it.readText() }
                val videoUrl = JSONObject(json).getString("url")
                runOnUiThread { play(videoUrl) }
            } catch (e: Exception) {
                runOnUiThread { showError("No se pudo cargar el video.\n" + (e.message ?: "")) }
            }
        }
    }

    private fun play(url: String) {
        player?.release()
        root.removeAllViews()
        val view = PlayerView(this)
        view.useController = true
        root.addView(view, FrameLayout.LayoutParams(-1, -1))
        player = ExoPlayer.Builder(this).build().also {
            view.player = it
            it.setMediaItem(MediaItem.fromUri(url))
            it.prepare()
            it.playWhenReady = true
        }
    }

    private fun showError(message: String) {
        root.removeAllViews()
        val text = TextView(this).apply {
            this.text = message
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = android.view.Gravity.CENTER
        }
        root.addView(text, FrameLayout.LayoutParams(-1, -1))
    }

    override fun onStop() {
        super.onStop()
        player?.release()
        player = null
    }

    companion object {
        const val CONFIG_URL = "https://raw.githubusercontent.com/gmatiascr62/android-tv-video/main/video.json"
    }
}
