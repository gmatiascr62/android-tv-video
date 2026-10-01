package com.gmatiascr62.androidtvvideo

import android.app.Activity
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

data class Channel(val name: String, val url: String)

class MainActivity : Activity() {
    private var player: ExoPlayer? = null
    private lateinit var root: FrameLayout
    private lateinit var videoContainer: FrameLayout
    private lateinit var channelListOverlay: FrameLayout
    private lateinit var channelListItems: LinearLayout
    private lateinit var prefs: SharedPreferences

    private var channels: List<Channel> = emptyList()
    private var currentChannelIndex = 0
    private var listSelectedIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        prefs = getSharedPreferences("tv_video_prefs", MODE_PRIVATE)

        root = FrameLayout(this)
        setContentView(root)

        videoContainer = FrameLayout(this)
        root.addView(videoContainer, FrameLayout.LayoutParams(-1, -1))

        channelListOverlay = buildChannelListOverlay()
        channelListOverlay.visibility = View.GONE
        root.addView(channelListOverlay, FrameLayout.LayoutParams(-1, -1))

        loadConfig()
    }

    private fun buildChannelListOverlay(): FrameLayout {
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(0xCC000000.toInt())
        }
        channelListItems = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        overlay.addView(
            channelListItems,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        return overlay
    }

    private fun populateChannelList() {
        channelListItems.removeAllViews()
        channels.forEach { channel ->
            val item = TextView(this).apply {
                text = channel.name
                textSize = 24f
                setPadding(48, 24, 48, 24)
            }
            channelListItems.addView(item)
        }
    }

    private fun updateChannelListHighlight() {
        for (i in 0 until channelListItems.childCount) {
            val item = channelListItems.getChildAt(i) as TextView
            if (i == listSelectedIndex) {
                item.setTextColor(0xFF000000.toInt())
                item.setBackgroundColor(0xFFFFFFFF.toInt())
            } else {
                item.setTextColor(0xFFFFFFFF.toInt())
                item.setBackgroundColor(0x00000000)
            }
        }
    }

    private fun showChannelList() {
        if (channels.isEmpty()) return
        listSelectedIndex = currentChannelIndex
        updateChannelListHighlight()
        channelListOverlay.visibility = View.VISIBLE
    }

    private fun hideChannelList() {
        channelListOverlay.visibility = View.GONE
    }

    private fun isChannelListVisible() = channelListOverlay.visibility == View.VISIBLE

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isChannelListVisible()) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    listSelectedIndex = (listSelectedIndex - 1).coerceAtLeast(0)
                    updateChannelListHighlight()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    listSelectedIndex = (listSelectedIndex + 1).coerceAtMost(channels.size - 1)
                    updateChannelListHighlight()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    selectChannel(listSelectedIndex)
                    return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    hideChannelList()
                    return true
                }
            }
        } else {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    showChannelList()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun selectChannel(index: Int) {
        currentChannelIndex = index
        prefs.edit().putInt("channel_index", currentChannelIndex).apply()
        hideChannelList()
        playChannel(currentChannelIndex)
    }

    private fun loadConfig() {
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
                val channelsJson = JSONObject(json).getJSONArray("channels")
                val parsed = (0 until channelsJson.length()).map {
                    val entry = channelsJson.getJSONObject(it)
                    Channel(entry.getString("name"), entry.getString("url"))
                }
                runOnUiThread { onConfigLoaded(parsed) }
            } catch (e: Exception) {
                runOnUiThread { showError("No se pudo cargar el video.\n" + (e.message ?: "")) }
            }
        }
    }

    private fun onConfigLoaded(parsed: List<Channel>) {
        if (parsed.isEmpty()) {
            showError("No hay canales configurados.")
            return
        }
        channels = parsed
        populateChannelList()
        currentChannelIndex = prefs.getInt("channel_index", 0).coerceIn(0, channels.size - 1)
        playChannel(currentChannelIndex)
    }

    private fun playChannel(index: Int) {
        play(channels[index].url)
    }

    private fun play(url: String) {
        player?.release()
        videoContainer.removeAllViews()
        val view = PlayerView(this)
        view.useController = false
        videoContainer.addView(view, FrameLayout.LayoutParams(-1, -1))
        player = ExoPlayer.Builder(this).build().also {
            view.player = it
            it.setMediaItem(MediaItem.fromUri(url))
            it.prepare()
            it.playWhenReady = true
        }
    }

    private fun showError(message: String) {
        videoContainer.removeAllViews()
        val text = TextView(this).apply {
            this.text = message
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }
        videoContainer.addView(text, FrameLayout.LayoutParams(-1, -1))
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
