package com.tvbykafi.app.ui.player

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.tvbykafi.app.R
import com.tvbykafi.app.util.NetworkUtil

@UnstableApi
class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var loadingOverlay: LinearLayout
    private lateinit var errorOverlay: LinearLayout

    private var channelName = ""
    private var channelUrl = ""
    private var currentResizeMode = 0
    private var retryCount = 0
    private val maxRetries = 3
    private val retryHandler = Handler(Looper.getMainLooper())
    private var isInitializing = false
    private lateinit var tvErrorMsg: TextView

    private val resizeModes = intArrayOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_FILL,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        hideSystemUI()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_player)

        channelName = intent.getStringExtra("channel_name") ?: ""
        channelUrl = intent.getStringExtra("channel_url") ?: ""

        playerView = findViewById(R.id.playerView)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        errorOverlay = findViewById(R.id.errorOverlay)

        findViewById<TextView>(R.id.tvLoadingChannel).text = channelName
        findViewById<TextView>(R.id.tvChannelName)?.text = channelName
        tvErrorMsg = findViewById(R.id.tvErrorMsg)

        findViewById<View>(R.id.btnRetry)?.setOnClickListener {
            if (!NetworkUtil.isOnline(this)) {
                showError(getString(R.string.no_internet))
                return@setOnClickListener
            }
            errorOverlay.visibility = View.GONE
            loadingOverlay.visibility = View.VISIBLE
            retryCount = 0
            initPlayer()
        }

        findViewById<View>(R.id.btnBack)?.setOnClickListener { finish() }

        findViewById<View>(R.id.btnAspect)?.setOnClickListener {
            cycleAspectRatio()
        }

        findViewById<View>(R.id.btnClosePlayer)?.setOnClickListener { finish() }

        if (channelUrl.isBlank()) {
            showError(getString(R.string.error_stream))
            return
        }

        if (!NetworkUtil.isOnline(this)) {
            showError(getString(R.string.no_internet))
            return
        }

        initPlayer()
    }

    private fun initPlayer() {
        if (isInitializing) return
        isInitializing = true

        releasePlayer()

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                500,
                2000
            )
            .build()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setUserAgent("Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")

        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setHandleAudioBecomingNoisy(true)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                playerView.player = this

                setMediaItem(MediaItem.fromUri(channelUrl))
                prepare()
                playWhenReady = true

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_READY -> {
                                loadingOverlay.visibility = View.GONE
                                errorOverlay.visibility = View.GONE
                                retryCount = 0
                                isInitializing = false
                            }
                            Player.STATE_BUFFERING -> {
                                loadingOverlay.visibility = View.VISIBLE
                            }
                            Player.STATE_ENDED -> {
                                loadingOverlay.visibility = View.GONE
                                isInitializing = false
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        loadingOverlay.visibility = View.GONE
                        isInitializing = false

                        val isDecoderError = error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
                            || error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED
                            || error.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED

                        if (isDecoderError) {
                            showError(getString(R.string.stream_codec_error))
                        } else if (!NetworkUtil.isOnline(this@PlayerActivity)) {
                            showError(getString(R.string.no_internet))
                        } else if (retryCount < maxRetries) {
                            retryWithDelay()
                        } else {
                            showError(getString(R.string.stream_source_error))
                            retryCount = 0
                        }
                    }
                })
            }

        isInitializing = false
    }

    private fun retryWithDelay() {
        retryCount++
        retryHandler.postDelayed({
            if (!isFinishing && !isDestroyed) {
                loadingOverlay.visibility = View.VISIBLE
                errorOverlay.visibility = View.GONE
                initPlayer()
            }
        }, 2000L)
    }

    private fun cycleAspectRatio() {
        currentResizeMode = (currentResizeMode + 1) % resizeModes.size
        playerView.resizeMode = resizeModes[currentResizeMode]

        val modeName = when (resizeModes[currentResizeMode]) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Fit"
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Fill"
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"
            else -> "Default"
        }
        Toast.makeText(this, "Size: $modeName", Toast.LENGTH_SHORT).show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (playerView.isControllerFullyVisible) {
            return super.onKeyDown(keyCode, event)
        }

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                playerView.showController()
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                player?.let { it.seekTo(maxOf(0, it.currentPosition - 10000)) }
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                player?.let { it.seekTo(it.currentPosition + 10000) }
                true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                cycleAspectRatio()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> {
                player?.let {
                    if (it.isPlaying) it.pause() else it.play()
                }
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                player?.play()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                player?.pause()
                true
            }
            KeyEvent.KEYCODE_MEDIA_STOP -> {
                player?.stop()
                finish()
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                finish()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && player?.isPlaying == true) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (_: Exception) {}
        }
    }

    override fun onPictureInPictureModeChanged(isInPip: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPip, newConfig)
        playerView.useController = !isInPip
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        player?.play()
    }

    override fun onPause() {
        super.onPause()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) return
        player?.pause()
    }

    override fun onStop() {
        super.onStop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) return
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        retryHandler.removeCallbacksAndMessages(null)
        releasePlayer()
    }

    private fun showError(message: String) {
        loadingOverlay.visibility = View.GONE
        tvErrorMsg.text = message
        errorOverlay.visibility = View.VISIBLE
    }

    private fun releasePlayer() {
        player?.let {
            it.stop()
            it.release()
        }
        player = null
    }
}
