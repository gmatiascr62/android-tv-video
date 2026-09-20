package com.gmatiascr62.androidtvvideo

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private var player: ExoPlayer? = null
    private lateinit var root: FrameLayout
    private val handler = Handler(Looper.getMainLooper())
    private var currentVideoUrl: String? = null
    private var retryAttempt = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        root = FrameLayout(this)
        setContentView(root)
    }

    override fun onStart() {
        super.onStart()
        scheduleConfigCheck(delayMs = 0, forceReplay = false)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        currentVideoUrl = null
        retryAttempt = 0
    }

    private fun scheduleConfigCheck(delayMs: Long, forceReplay: Boolean) {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ loadConfig(forceReplay) }, delayMs)
    }

    private fun loadConfig(forceReplay: Boolean) {
        thread {
            try {
                val configUrl = "$CONFIG_URL?ts=${System.currentTimeMillis()}"
                val conn = URL(configUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.useCaches = false
                conn.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
                conn.setRequestProperty("Pragma", "no-cache")
                val json = conn.inputStream.bufferedReader().use { it.readText() }
                val videoUrl = JSONObject(json).getString("url")
                runOnUiThread { onConfigLoaded(videoUrl, forceReplay) }
            } catch (e: Exception) {
                runOnUiThread { onConfigFailed(e) }
            }
        }
    }

    private fun onConfigLoaded(videoUrl: String, forceReplay: Boolean) {
        retryAttempt = 0
        if (videoUrl != currentVideoUrl || forceReplay) {
            currentVideoUrl = videoUrl
            play(videoUrl)
        }
        scheduleConfigCheck(CONFIG_POLL_INTERVAL_MS, forceReplay = false)
    }

    private fun onConfigFailed(e: Exception) {
        if (currentVideoUrl == null) {
            showError("No se pudo cargar el video.\n" + (e.message ?: ""))
        }
        scheduleConfigCheck(nextRetryDelay(), forceReplay = false)
    }

    private fun nextRetryDelay(): Long {
        val delay = (RETRY_BASE_DELAY_MS * (1L shl retryAttempt.coerceAtMost(MAX_RETRY_SHIFT)))
            .coerceAtMost(MAX_RETRY_DELAY_MS)
        retryAttempt++
        return delay
    }

    private fun play(url: String) {
        player?.release()
        root.removeAllViews()
        val view = PlayerView(this)
        view.useController = true
        root.addView(view, FrameLayout.LayoutParams(-1, -1))
        player = ExoPlayer.Builder(this).build().also {
            view.player = it
            it.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    scheduleConfigCheck(nextRetryDelay(), forceReplay = true)
                }
            })
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

    companion object {
        const val CONFIG_URL = "https://raw.githubusercontent.com/gmatiascr62/android-tv-video/main/video.json"
        const val CONFIG_POLL_INTERVAL_MS = 30_000L
        const val RETRY_BASE_DELAY_MS = 3_000L
        const val MAX_RETRY_DELAY_MS = 60_000L
        const val MAX_RETRY_SHIFT = 5
    }
}
