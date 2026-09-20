package com.gmatiascr62.androidtvvideo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private var player: ExoPlayer? = null
    private lateinit var root: FrameLayout
    private lateinit var contentContainer: FrameLayout
    private lateinit var updateBanner: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val updateHandler = Handler(Looper.getMainLooper())
    private var currentVideoUrl: String? = null
    private var retryAttempt = 0
    private var installPromptedForSha: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        root = FrameLayout(this)
        setContentView(root)

        contentContainer = FrameLayout(this)
        root.addView(contentContainer, FrameLayout.LayoutParams(-1, -1))

        updateBanner = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0x99000000.toInt())
            setPadding(24, 12, 24, 12)
            visibility = View.GONE
        }
        root.addView(
            updateBanner,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.END
            ).apply {
                bottomMargin = 24
                marginEnd = 24
            }
        )
    }

    override fun onStart() {
        super.onStart()
        scheduleConfigCheck(delayMs = 0, forceReplay = false)
        scheduleUpdateCheck(delayMs = 0)
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacksAndMessages(null)
        updateHandler.removeCallbacksAndMessages(null)
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
        contentContainer.removeAllViews()
        val view = PlayerView(this)
        view.useController = true
        contentContainer.addView(view, FrameLayout.LayoutParams(-1, -1))
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
        contentContainer.removeAllViews()
        val text = TextView(this).apply {
            this.text = message
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }
        contentContainer.addView(text, FrameLayout.LayoutParams(-1, -1))
    }

    private fun scheduleUpdateCheck(delayMs: Long) {
        updateHandler.removeCallbacksAndMessages(null)
        updateHandler.postDelayed({ checkForUpdate() }, delayMs)
    }

    private fun checkForUpdate() {
        thread {
            try {
                val conn = URL(LATEST_COMMIT_URL).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                val json = conn.inputStream.bufferedReader().use { it.readText() }
                val latestSha = JSONObject(json).getString("sha")
                val hasUpdate = BuildConfig.BUILD_SHA != "unknown" && latestSha != BuildConfig.BUILD_SHA
                if (hasUpdate && latestSha != installPromptedForSha) {
                    downloadAndInstall(latestSha)
                }
            } catch (e: Exception) {
                // Ignore; the next scheduled check will retry.
            }
            scheduleUpdateCheck(UPDATE_CHECK_INTERVAL_MS)
        }
    }

    private fun downloadAndInstall(latestSha: String) {
        runOnUiThread { showUpdateBanner("Descargando actualización...") }
        try {
            val conn = URL(APK_DOWNLOAD_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.instanceFollowRedirects = true
            val apkFile = File(cacheDir, "update.apk")
            conn.inputStream.use { input ->
                FileOutputStream(apkFile).use { output -> input.copyTo(output) }
            }
            installPromptedForSha = latestSha
            runOnUiThread {
                hideUpdateBanner()
                launchInstaller(apkFile)
            }
        } catch (e: Exception) {
            runOnUiThread { showUpdateBanner("No se pudo descargar la actualización") }
        }
    }

    private fun launchInstaller(apkFile: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun showUpdateBanner(message: String) {
        updateBanner.text = message
        updateBanner.visibility = View.VISIBLE
    }

    private fun hideUpdateBanner() {
        updateBanner.visibility = View.GONE
    }

    companion object {
        const val CONFIG_URL = "https://raw.githubusercontent.com/gmatiascr62/android-tv-video/main/video.json"
        const val LATEST_COMMIT_URL = "https://api.github.com/repos/gmatiascr62/android-tv-video/commits/main"
        const val APK_DOWNLOAD_URL = "https://github.com/gmatiascr62/android-tv-video/releases/download/latest/app-debug.apk"
        const val CONFIG_POLL_INTERVAL_MS = 30_000L
        const val UPDATE_CHECK_INTERVAL_MS = 30 * 60_000L
        const val RETRY_BASE_DELAY_MS = 3_000L
        const val MAX_RETRY_DELAY_MS = 60_000L
        const val MAX_RETRY_SHIFT = 5
    }
}
