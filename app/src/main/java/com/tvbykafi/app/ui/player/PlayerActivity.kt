package com.tvbykafi.app.ui.player

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.tvbykafi.app.R
import com.tvbykafi.app.data.model.Channel
import com.tvbykafi.app.util.NetworkUtil
import kotlin.math.abs

@UnstableApi
class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var loadingOverlay: LinearLayout
    private lateinit var errorOverlay: LinearLayout
    private lateinit var channelInfoOverlay: LinearLayout
    private lateinit var tvChannelInfoName: TextView
    private lateinit var tvChannelInfoCategory: TextView
    private lateinit var ivChannelLogoOverlay: ImageView

    private var channelName = ""
    private var channelUrl = ""
    private var channelDrmUrl = ""
    private var currentResizeMode = 0
    private var retryCount = 0
    private val maxRetries = 3
    private val retryHandler = Handler(Looper.getMainLooper())
    private val infoHandler = Handler(Looper.getMainLooper())
    private var isInitializing = false
    private var useSoftwareDecoder = false
    private lateinit var tvErrorMsg: TextView

    // Channel list for swipe navigation
    private var channelList = arrayListOf<Channel>()
    private var currentChannelIndex = 0

    private lateinit var gestureDetector: GestureDetector

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
        channelDrmUrl = intent.getStringExtra("channel_drm_url") ?: ""

        // Get channel list for swipe navigation
        @Suppress("DEPRECATION")
        channelList = intent.getParcelableArrayListExtra("channel_list") ?: arrayListOf()
        currentChannelIndex = intent.getIntExtra("channel_index", 0)

        playerView = findViewById(R.id.playerView)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        errorOverlay = findViewById(R.id.errorOverlay)
        channelInfoOverlay = findViewById(R.id.channelInfoOverlay)
        tvChannelInfoName = findViewById(R.id.tvChannelInfoName)
        tvChannelInfoCategory = findViewById(R.id.tvChannelInfoCategory)
        ivChannelLogoOverlay = findViewById(R.id.ivChannelLogoOverlay)

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
            useSoftwareDecoder = false
            initPlayer()
        }

        findViewById<View>(R.id.btnBack)?.setOnClickListener { finish() }

        findViewById<View>(R.id.btnAspect)?.setOnClickListener {
            cycleAspectRatio()
        }

        findViewById<View>(R.id.btnClosePlayer)?.setOnClickListener { finish() }

        // Setup touch swipe gesture for phones
        setupGestureDetector()

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

    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 100

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false

                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y

                if (abs(diffX) > abs(diffY) &&
                    abs(diffX) > SWIPE_THRESHOLD &&
                    abs(velocityX) > SWIPE_VELOCITY_THRESHOLD
                ) {
                    if (diffX < 0) {
                        // Swipe LEFT → next channel
                        switchChannel(1)
                    } else {
                        // Swipe RIGHT → previous channel
                        switchChannel(-1)
                    }
                    return true
                }
                return false
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (playerView.isControllerFullyVisible) {
                    playerView.hideController()
                } else {
                    playerView.showController()
                }
                return true
            }
        })

        playerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun switchChannel(direction: Int) {
        if (channelList.isEmpty()) return

        val newIndex = currentChannelIndex + direction
        if (newIndex < 0 || newIndex >= channelList.size) {
            val boundary = if (direction > 0) "Last" else "First"
            Toast.makeText(this, "$boundary channel", Toast.LENGTH_SHORT).show()
            return
        }

        currentChannelIndex = newIndex
        val channel = channelList[currentChannelIndex]

        channelName = channel.name
        channelUrl = channel.url
        channelDrmUrl = channel.drmLicenseUrl

        // Update UI
        findViewById<TextView>(R.id.tvLoadingChannel).text = channelName
        findViewById<TextView>(R.id.tvChannelName)?.text = channelName

        // Show channel info overlay
        showChannelInfo(channel)

        // Reset state and play new channel
        retryCount = 0
        useSoftwareDecoder = false
        loadingOverlay.visibility = View.VISIBLE
        errorOverlay.visibility = View.GONE

        initPlayer()
    }

    private fun showChannelInfo(channel: Channel) {
        tvChannelInfoName.text = channel.name
        tvChannelInfoCategory.text = channel.category.uppercase()

        Glide.with(this)
            .load(channel.logo)
            .placeholder(R.drawable.ic_tv_default)
            .error(R.drawable.ic_tv_default)
            .circleCrop()
            .into(ivChannelLogoOverlay)

        channelInfoOverlay.visibility = View.VISIBLE

        infoHandler.removeCallbacksAndMessages(null)
        infoHandler.postDelayed({
            channelInfoOverlay.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    channelInfoOverlay.visibility = View.GONE
                    channelInfoOverlay.alpha = 1f
                }
                .start()
        }, 3000)
    }

    private fun initPlayer() {
        if (isInitializing) return
        isInitializing = true

        releasePlayer()

        try {
            // Optimized buffer — reduces black screen delay
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(2500, 20000, 500, 1000)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000)
                .setUserAgent("Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")

            val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

            // Software decoder fallback — fixes crash on TV boxes and weak phones
            val renderersFactory = DefaultRenderersFactory(this).apply {
                setExtensionRendererMode(
                    if (useSoftwareDecoder) {
                        DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                    } else {
                        DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    }
                )
                setEnableDecoderFallback(true)
            }

            // Build MediaItem — with or without DRM
            val mediaItem = buildMediaItem()

            player = ExoPlayer.Builder(this)
                .setRenderersFactory(renderersFactory)
                .setHandleAudioBecomingNoisy(true)
                .setLoadControl(loadControl)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
                .apply {
                    playerView.player = this

                    setMediaItem(mediaItem)
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

                            val isDecoderError =
                                error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
                                    || error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED
                                    || error.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED

                            when {
                                // Decoder error — retry with software decoder
                                isDecoderError && !useSoftwareDecoder -> {
                                    useSoftwareDecoder = true
                                    retryCount = 0
                                    retryWithDelay()
                                }
                                !NetworkUtil.isOnline(this@PlayerActivity) -> {
                                    showError(getString(R.string.no_internet))
                                }
                                retryCount < maxRetries -> {
                                    retryWithDelay()
                                }
                                else -> {
                                    showError(getString(R.string.stream_source_error))
                                    retryCount = 0
                                }
                            }
                        }
                    })
                }

            isInitializing = false
        } catch (e: Exception) {
            isInitializing = false
            showError(getString(R.string.stream_source_error))
        }
    }

    private fun buildMediaItem(): MediaItem {
        val builder = MediaItem.Builder().setUri(channelUrl)

        // DRM configuration
        if (channelDrmUrl.isNotEmpty()) {
            builder.setDrmConfiguration(
                MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                    .setLicenseUri(channelDrmUrl)
                    .build()
            )
        }

        // Detect stream type from URL for better compatibility
        val lowerUrl = channelUrl.lowercase()
        when {
            lowerUrl.contains(".m3u8") || lowerUrl.contains("/hls") -> {
                builder.setMimeType("application/x-mpegURL")
            }
            lowerUrl.contains(".mpd") -> {
                builder.setMimeType("application/dash+xml")
            }
            lowerUrl.contains("rtsp://") -> {
                builder.setMimeType("application/x-rtsp")
            }
        }

        return builder.build()
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
        // If player controls are visible, let the default handling work
        if (playerView.isControllerFullyVisible) {
            return super.onKeyDown(keyCode, event)
        }

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                playerView.showController()
                true
            }
            // DPAD LEFT/RIGHT — Televizio-style channel switching
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                switchChannel(-1)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                switchChannel(1)
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
            // Channel up/down buttons on some remotes
            KeyEvent.KEYCODE_CHANNEL_UP -> {
                switchChannel(1)
                true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                switchChannel(-1)
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
        // Only resume if player is in a valid state
        player?.let {
            if (it.playbackState != Player.STATE_IDLE && it.playerError == null) {
                it.play()
            }
        }
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
        infoHandler.removeCallbacksAndMessages(null)
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
