package com.tvbykafi.app.ui.player

import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.firestore.ListenerRegistration
import com.tvbykafi.app.R
import com.tvbykafi.app.data.ChannelHolder
import com.tvbykafi.app.data.FirebaseRepository
import com.tvbykafi.app.data.PlaylistRepository
import com.tvbykafi.app.data.model.Channel
import com.tvbykafi.app.data.model.User
import com.tvbykafi.app.ui.adapter.CategoryAdapter
import com.tvbykafi.app.ui.adapter.PlaylistAdapter
import com.tvbykafi.app.ui.login.LoginActivity
import com.tvbykafi.app.util.NetworkUtil
import com.tvbykafi.app.util.PrefsManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@UnstableApi
class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView

    // Overlays
    private lateinit var channelInfoOverlay: LinearLayout
    private lateinit var numberInputOverlay: LinearLayout
    private lateinit var fastBrowseOverlay: LinearLayout
    private lateinit var loadingOverlay: LinearLayout
    private lateinit var errorOverlay: LinearLayout
    private lateinit var playlistOverlay: View
    private lateinit var settingsOverlay: LinearLayout
    private lateinit var numpadOverlay: View
    private lateinit var tvNumpadDisplay: TextView
    private lateinit var tvNotFound: TextView
    private lateinit var tvVolumeHint: TextView
    private lateinit var expirySoonNotice: LinearLayout
    private lateinit var tvExpirySoon: TextView

    // Channel Info views
    private lateinit var tvInfoChno: TextView
    private lateinit var tvInfoName: TextView
    private lateinit var tvInfoCategory: TextView
    private lateinit var tvInfoTime: TextView
    private lateinit var ivChannelLogoOverlay: ImageView

    // Number Input views
    private lateinit var tvNumberInput: TextView

    // Fast Browse views
    private lateinit var tvBrowseChno: TextView
    private lateinit var tvBrowseName: TextView

    // Loading/Error views
    private lateinit var tvLoadingChannel: TextView
    private lateinit var tvErrorMsg: TextView

    // Playlist views
    private lateinit var rvPlaylistChannels: RecyclerView
    private lateinit var rvPlaylistCategories: RecyclerView
    private lateinit var etPlaylistSearch: EditText
    private lateinit var tvChannelCount: TextView

    // Settings views
    private lateinit var tvSettingsUserName: TextView
    private lateinit var tvSettingsEmail: TextView
    private lateinit var tvSettingsExpiry: TextView

    // Adapters
    private lateinit var playlistAdapter: PlaylistAdapter
    private lateinit var categoryAdapter: CategoryAdapter

    // State
    private var allChannels = listOf<Channel>()
    private var currentChannel: Channel? = null
    private var currentChannelIndex = 0
    private var isPlaylistVisible = false
    private var isSettingsVisible = false
    private var isNumpadVisible = false
    private var playlistActiveCategory = "ALL"

    // Number input state
    private var numberBuffer = StringBuilder()
    private val numberHandler = Handler(Looper.getMainLooper())
    private val numberTimeout = 1500L

    // On-screen numpad state
    private val numpadBuffer = StringBuilder()
    private val numpadHandler = Handler(Looper.getMainLooper())
    private val numpadTimeout = 2000L

    // Volume-Down long-press (opens numpad)
    private val volumeHoldHandler = Handler(Looper.getMainLooper())
    private val volumeHoldDelay = 1600L
    private var volumeHoldTriggered = false
    private var volumeHoldActive = false

    // Not-found message
    private val notFoundHandler = Handler(Looper.getMainLooper())

    // Expiry-soon notice auto-hide
    private val expiryNoticeHandler = Handler(Looper.getMainLooper())

    // Fast browse state
    private var isFastBrowsing = false
    private var fastBrowseIndex = 0
    private val fastBrowseHandler = Handler(Looper.getMainLooper())
    private val fastBrowseInterval = 200L

    // Player state
    private var retryCount = 0
    private val maxRetries = 5
    private val retryHandler = Handler(Looper.getMainLooper())
    private val infoHandler = Handler(Looper.getMainLooper())
    private var isInitializing = false
    private var useSoftwareDecoder = false

    // Firebase
    private val firebaseRepo = FirebaseRepository.getInstance()
    private val playlistRepo = PlaylistRepository.getInstance()
    private var userListener: ListenerRegistration? = null
    private var currentUser: User? = null

    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.US)
    private val expiryFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // Network monitor
    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)
        hideSystemUI()

        initViews()
        setupPlaylist()
        setupSettings()
        setupNumpad()
        startUserListener()
        startNetworkMonitor()
        loadPlaylist()
    }

    private fun initViews() {
        playerView = findViewById(R.id.playerView)

        channelInfoOverlay = findViewById(R.id.channelInfoOverlay)
        numberInputOverlay = findViewById(R.id.numberInputOverlay)
        fastBrowseOverlay = findViewById(R.id.fastBrowseOverlay)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        errorOverlay = findViewById(R.id.errorOverlay)
        playlistOverlay = findViewById(R.id.playlistOverlay)
        settingsOverlay = findViewById(R.id.settingsOverlay)
        numpadOverlay = findViewById(R.id.numpadOverlay)
        tvNumpadDisplay = findViewById(R.id.tvNumpadDisplay)
        tvNotFound = findViewById(R.id.tvNotFound)
        tvVolumeHint = findViewById(R.id.tvVolumeHint)
        expirySoonNotice = findViewById(R.id.expirySoonNotice)
        tvExpirySoon = findViewById(R.id.tvExpirySoon)

        tvInfoChno = findViewById(R.id.tvInfoChno)
        tvInfoName = findViewById(R.id.tvInfoName)
        tvInfoCategory = findViewById(R.id.tvInfoCategory)
        tvInfoTime = findViewById(R.id.tvInfoTime)
        ivChannelLogoOverlay = findViewById(R.id.ivChannelLogoOverlay)

        tvNumberInput = findViewById(R.id.tvNumberInput)

        tvBrowseChno = findViewById(R.id.tvBrowseChno)
        tvBrowseName = findViewById(R.id.tvBrowseName)

        tvLoadingChannel = findViewById(R.id.tvLoadingChannel)
        tvErrorMsg = findViewById(R.id.tvErrorMsg)

        rvPlaylistChannels = findViewById(R.id.rvPlaylistChannels)
        rvPlaylistCategories = findViewById(R.id.rvPlaylistCategories)
        etPlaylistSearch = findViewById(R.id.etPlaylistSearch)
        tvChannelCount = findViewById(R.id.tvChannelCount)

        tvSettingsUserName = findViewById(R.id.tvSettingsUserName)
        tvSettingsEmail = findViewById(R.id.tvSettingsEmail)
        tvSettingsExpiry = findViewById(R.id.tvSettingsExpiry)
    }

    // =================== PLAYLIST LOADING ===================

    private fun loadPlaylist() {
        loadingOverlay.visibility = View.VISIBLE
        tvLoadingChannel.text = getString(R.string.loading_playlist)

        lifecycleScope.launch {
            try {
                val channels = playlistRepo.fetchPlaylist()
                allChannels = channels
                ChannelHolder.channels = channels

                runOnUiThread {
                    if (channels.isEmpty()) {
                        showError(getString(R.string.error_empty_playlist))
                        return@runOnUiThread
                    }

                    val lastChno = PrefsManager.getLastChannel(this@PlayerActivity)
                    val startIndex = if (lastChno > 0) {
                        channels.indexOfFirst { it.chno == lastChno }.takeIf { it >= 0 } ?: 0
                    } else 0

                    playlistAdapter.submitList(channels)
                    updateCategoryTabs()
                    tvChannelCount.text = "${channels.size} channels"

                    playChannel(startIndex)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (allChannels.isEmpty()) {
                        showError(getString(R.string.error_load_playlist))
                    }
                }
            }
        }
    }

    // =================== PLAYER ===================

    private fun playChannel(index: Int) {
        if (allChannels.isEmpty()) return
        val safeIndex = index.coerceIn(0, allChannels.size - 1)

        currentChannelIndex = safeIndex
        val channel = allChannels[safeIndex]
        currentChannel = channel

        PrefsManager.saveLastChannel(this, channel.chno)
        playlistAdapter.setCurrentPlaying(channel.chno)

        tvLoadingChannel.text = channel.name
        loadingOverlay.visibility = View.VISIBLE
        errorOverlay.visibility = View.GONE

        showChannelInfo(channel)
        initPlayer(channel)
    }

    private fun playByChno(chno: Int) {
        val index = allChannels.indexOfFirst { it.chno == chno }
        if (index >= 0) {
            playChannel(index)
        } else {
            showChannelNotFound(chno)
        }
    }

    private fun showChannelNotFound(chno: Int) {
        if (isFinishing || isDestroyed) return
        tvNotFound.text = getString(R.string.channel_not_found) + "  #" + chno
        tvNotFound.visibility = View.VISIBLE
        notFoundHandler.removeCallbacksAndMessages(null)
        notFoundHandler.postDelayed({
            if (!isFinishing && !isDestroyed) tvNotFound.visibility = View.GONE
        }, 2000)
    }

    private fun switchChannel(direction: Int) {
        if (allChannels.isEmpty()) return
        val newIndex = currentChannelIndex + direction
        if (newIndex < 0 || newIndex >= allChannels.size) return
        playChannel(newIndex)
    }

    private fun initPlayer(channel: Channel) {
        if (isFinishing || isDestroyed) return
        if (isInitializing) return
        isInitializing = true

        releasePlayer()

        try {
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(2000, 15000, 500, 1000)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000)
                .setUserAgent("Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/120.0 Safari/537.36")

            val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

            val renderersFactory = DefaultRenderersFactory(this).apply {
                if (useSoftwareDecoder) {
                    setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                } else {
                    setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
                }
                setEnableDecoderFallback(true)
            }

            val mediaItem = buildMediaItem(channel)

            val newPlayer = ExoPlayer.Builder(this)
                .setRenderersFactory(renderersFactory)
                .setHandleAudioBecomingNoisy(true)
                .setLoadControl(loadControl)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()

            newPlayer.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (isFinishing || isDestroyed) return
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
                    if (isFinishing || isDestroyed) return
                    isInitializing = false

                    val isDecoderError =
                        error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                            error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                            error.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED

                    when {
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

            newPlayer.setMediaItem(mediaItem)
            newPlayer.prepare()
            newPlayer.playWhenReady = true

            playerView.player = newPlayer
            player = newPlayer
            isInitializing = false
        } catch (e: Exception) {
            isInitializing = false
            showError(getString(R.string.stream_source_error))
        }
    }

    private fun buildMediaItem(channel: Channel): MediaItem {
        val builder = MediaItem.Builder().setUri(channel.url)
        val lowerUrl = channel.url.lowercase()
        when {
            lowerUrl.contains(".m3u8") || lowerUrl.contains("/hls") ->
                builder.setMimeType("application/x-mpegURL")
            lowerUrl.contains(".mpd") ->
                builder.setMimeType("application/dash+xml")
            lowerUrl.contains("rtsp://") ->
                builder.setMimeType("application/x-rtsp")
        }
        return builder.build()
    }

    private fun retryWithDelay() {
        retryCount++
        val delay = minOf(1000L * (1 shl (retryCount - 1)), 8000L)
        retryHandler.removeCallbacksAndMessages(null)
        loadingOverlay.visibility = View.VISIBLE
        errorOverlay.visibility = View.GONE
        tvLoadingChannel.text = getString(R.string.retrying)
        retryHandler.postDelayed({
            if (!isFinishing && !isDestroyed) {
                currentChannel?.let { initPlayer(it) }
            }
        }, delay)
    }

    // =================== CHANNEL INFO OVERLAY ===================

    private fun showChannelInfo(channel: Channel) {
        if (isFinishing || isDestroyed) return

        tvInfoChno.text = channel.chno.toString()
        tvInfoName.text = channel.name
        tvInfoCategory.text = channel.category.uppercase()
        tvInfoTime.text = timeFormat.format(Date())

        try {
            Glide.with(applicationContext)
                .load(channel.logo)
                .placeholder(R.drawable.ic_tv_default)
                .error(R.drawable.ic_tv_default)
                .circleCrop()
                .into(ivChannelLogoOverlay)
        } catch (_: Exception) {}

        channelInfoOverlay.visibility = View.VISIBLE
        channelInfoOverlay.alpha = 1f

        infoHandler.removeCallbacksAndMessages(null)
        infoHandler.postDelayed({
            if (!isFinishing && !isDestroyed) {
                channelInfoOverlay.animate()
                    .alpha(0f)
                    .setDuration(400)
                    .withEndAction {
                        channelInfoOverlay.visibility = View.GONE
                        channelInfoOverlay.alpha = 1f
                    }
                    .start()
            }
        }, 4000)
    }

    // =================== DTH NUMBER INPUT ===================

    private fun onNumberKey(digit: Int) {
        if (isPlaylistVisible || isSettingsVisible) return

        numberBuffer.append(digit)
        tvNumberInput.text = numberBuffer.toString() + "_"
        numberInputOverlay.visibility = View.VISIBLE

        numberHandler.removeCallbacksAndMessages(null)
        numberHandler.postDelayed({
            val chno = numberBuffer.toString().toIntOrNull()
            numberBuffer.clear()
            numberInputOverlay.visibility = View.GONE

            if (chno != null) {
                playByChno(chno)
            }
        }, numberTimeout)
    }

    // =================== FAST CHANNEL BROWSE ===================

    private fun startFastBrowse(direction: Int) {
        if (allChannels.isEmpty()) return
        isFastBrowsing = true
        fastBrowseIndex = currentChannelIndex

        val runnable = object : Runnable {
            override fun run() {
                if (!isFastBrowsing) return
                fastBrowseIndex += direction
                fastBrowseIndex = fastBrowseIndex.coerceIn(0, allChannels.size - 1)

                val ch = allChannels[fastBrowseIndex]
                tvBrowseChno.text = ch.chno.toString()
                tvBrowseName.text = ch.name
                fastBrowseOverlay.visibility = View.VISIBLE

                fastBrowseHandler.postDelayed(this, fastBrowseInterval)
            }
        }

        fastBrowseHandler.post(runnable)
    }

    private fun stopFastBrowse() {
        isFastBrowsing = false
        fastBrowseHandler.removeCallbacksAndMessages(null)
        fastBrowseOverlay.visibility = View.GONE
        playChannel(fastBrowseIndex)
    }

    // =================== PLAYLIST OVERLAY ===================

    private fun setupPlaylist() {
        playlistAdapter = PlaylistAdapter { channel, _ ->
            val index = allChannels.indexOfFirst { it.chno == channel.chno }
            if (index >= 0) {
                playChannel(index)
            }
            hidePlaylist()
        }

        rvPlaylistChannels.layoutManager = LinearLayoutManager(this)
        rvPlaylistChannels.adapter = playlistAdapter

        categoryAdapter = CategoryAdapter { category ->
            playlistActiveCategory = category
            filterPlaylist()
        }

        rvPlaylistCategories.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvPlaylistCategories.adapter = categoryAdapter

        etPlaylistSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { filterPlaylist() }
        })
    }

    private fun updateCategoryTabs() {
        val cats = ChannelHolder.getCategories()
        categoryAdapter.setCategories(cats)
    }

    private fun filterPlaylist() {
        val query = etPlaylistSearch.text.toString().trim()
        var filtered = if (playlistActiveCategory == "ALL") allChannels
        else allChannels.filter { it.category == playlistActiveCategory }

        if (query.isNotEmpty()) {
            val q = query.lowercase()
            val asNumber = q.toIntOrNull()
            filtered = filtered.filter {
                it.name.lowercase().contains(q) ||
                    (asNumber != null && it.chno == asNumber) ||
                    it.chno.toString().startsWith(q)
            }
        }

        playlistAdapter.submitList(filtered)
        tvChannelCount.text = "${filtered.size} channels"
    }

    private fun showPlaylist() {
        isPlaylistVisible = true
        playlistOverlay.visibility = View.VISIBLE
        playlistAdapter.setCurrentPlaying(currentChannel?.chno ?: -1)
        filterPlaylist()

        val currentIdx = playlistAdapter.currentList.indexOfFirst {
            it.chno == currentChannel?.chno
        }
        if (currentIdx >= 0) {
            rvPlaylistChannels.scrollToPosition(currentIdx)
        }

        rvPlaylistChannels.post { rvPlaylistChannels.requestFocus() }
    }

    private fun hidePlaylist() {
        isPlaylistVisible = false
        playlistOverlay.visibility = View.GONE
        etPlaylistSearch.text.clear()
    }

    // =================== SETTINGS OVERLAY ===================

    private fun setupSettings() {
        findViewById<View>(R.id.btnSettingsRefresh).setOnClickListener {
            hideSettings()
            playlistRepo.clearCache()
            loadPlaylist()
        }

        findViewById<View>(R.id.btnSettingsPayment).setOnClickListener {
            hideSettings()
        }

        findViewById<View>(R.id.btnSettingsSupport).setOnClickListener {
            hideSettings()
        }

        findViewById<View>(R.id.btnSettingsLogout).setOnClickListener {
            hideSettings()
            logout()
        }
    }

    private fun showSettings() {
        isSettingsVisible = true
        val user = currentUser
        if (user != null) {
            tvSettingsUserName.text = user.name
            tvSettingsEmail.text = user.email
            val expiry = user.expiry.ifEmpty { getString(R.string.no_limit) }
            tvSettingsExpiry.text = if (expiry.contains("T")) expiry.split("T")[0] else expiry

            val isExpired = isSubscriptionExpired()
            tvSettingsExpiry.setTextColor(
                getColor(if (isExpired) R.color.status_expired else R.color.accent_green)
            )
        }
        settingsOverlay.visibility = View.VISIBLE
        findViewById<View>(R.id.btnSettingsRefresh).requestFocus()
    }

    private fun hideSettings() {
        isSettingsVisible = false
        settingsOverlay.visibility = View.GONE
    }

    // =================== ON-SCREEN NUMPAD ===================

    private fun setupNumpad() {
        val digitMap = mapOf(
            R.id.btnNum0 to 0, R.id.btnNum1 to 1, R.id.btnNum2 to 2,
            R.id.btnNum3 to 3, R.id.btnNum4 to 4, R.id.btnNum5 to 5,
            R.id.btnNum6 to 6, R.id.btnNum7 to 7, R.id.btnNum8 to 8,
            R.id.btnNum9 to 9
        )
        for ((id, value) in digitMap) {
            findViewById<Button>(id).setOnClickListener { onNumpadDigit(value) }
        }
        findViewById<Button>(R.id.btnNumDel).setOnClickListener { onNumpadDel() }
        findViewById<Button>(R.id.btnNumGo).setOnClickListener { onNumpadGo() }
    }

    private fun showNumpad() {
        if (isNumpadVisible) return
        if (isPlaylistVisible) hidePlaylist()
        if (isSettingsVisible) hideSettings()
        isNumpadVisible = true
        numpadBuffer.clear()
        updateNumpadDisplay()
        numpadOverlay.visibility = View.VISIBLE
        findViewById<Button>(R.id.btnNum1).requestFocus()
    }

    private fun hideNumpad() {
        isNumpadVisible = false
        numpadOverlay.visibility = View.GONE
        numpadHandler.removeCallbacksAndMessages(null)
        numpadBuffer.clear()
    }

    private fun onNumpadDigit(digit: Int) {
        if (numpadBuffer.length >= 4) return
        numpadBuffer.append(digit)
        updateNumpadDisplay()
        scheduleNumpadJump()
    }

    private fun onNumpadDel() {
        if (numpadBuffer.isNotEmpty()) {
            numpadBuffer.deleteCharAt(numpadBuffer.length - 1)
            updateNumpadDisplay()
        }
        scheduleNumpadJump()
    }

    private fun onNumpadGo() {
        numpadHandler.removeCallbacksAndMessages(null)
        val chno = numpadBuffer.toString().toIntOrNull()
        hideNumpad()
        if (chno != null) playByChno(chno)
    }

    private fun updateNumpadDisplay() {
        tvNumpadDisplay.text = if (numpadBuffer.isEmpty()) "" else numpadBuffer.toString()
    }

    private fun scheduleNumpadJump() {
        numpadHandler.removeCallbacksAndMessages(null)
        if (numpadBuffer.isEmpty()) return
        numpadHandler.postDelayed({
            val chno = numpadBuffer.toString().toIntOrNull()
            hideNumpad()
            if (chno != null) playByChno(chno)
        }, numpadTimeout)
    }

    // Volume-Down long-press → numpad
    private fun startVolumeHold() {
        volumeHoldActive = true
        volumeHoldTriggered = false
        tvVolumeHint.visibility = View.VISIBLE
        volumeHoldHandler.removeCallbacksAndMessages(null)
        volumeHoldHandler.postDelayed({
            volumeHoldTriggered = true
            tvVolumeHint.visibility = View.GONE
            showNumpad()
        }, volumeHoldDelay)
    }

    private fun cancelVolumeHold() {
        volumeHoldHandler.removeCallbacksAndMessages(null)
        tvVolumeHint.visibility = View.GONE
        if (!volumeHoldTriggered) {
            try {
                val am = getSystemService(AUDIO_SERVICE) as AudioManager
                am.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI
                )
            } catch (_: Exception) {}
        }
        volumeHoldTriggered = false
        volumeHoldActive = false
    }

    // =================== FIREBASE USER LISTENER ===================

    private fun startUserListener() {
        val userId = PrefsManager.getUserId(this) ?: return
        userListener = firebaseRepo.observeUser(
            userId,
            onUpdate = { user ->
                currentUser = user
                if (!isFinishing && !isDestroyed) {
                    runOnUiThread {
                        if (isSubscriptionExpired()) {
                            player?.pause()
                            showError(getString(R.string.subscription_expired))
                        } else {
                            updateExpirySoonNotice()
                        }
                    }
                }
            },
            onDeleted = {
                if (!isFinishing && !isDestroyed) {
                    runOnUiThread { logout() }
                }
            }
        )
    }

    private fun isSubscriptionExpired(): Boolean {
        val user = currentUser ?: return false
        if (user.expiry.isEmpty()) return false
        return try {
            val expiry = if (user.expiry.contains("T")) user.expiry.split("T")[0] else user.expiry
            val end = expiryFormat.parse(expiry)
            end != null && Date().after(end)
        } catch (_: Exception) {
            false
        }
    }

    // Show a transparent notice when subscription ends within 5 days
    private fun updateExpirySoonNotice() {
        val user = currentUser ?: return
        if (user.expiry.isEmpty()) {
            expirySoonNotice.visibility = View.GONE
            return
        }
        try {
            val expiry = if (user.expiry.contains("T")) user.expiry.split("T")[0] else user.expiry
            val end = expiryFormat.parse(expiry) ?: return

            val startOfToday = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.time

            val dayMs = 24 * 60 * 60 * 1000L
            val daysLeft = ((end.time - startOfToday.time) / dayMs).toInt()

            if (daysLeft in 0..5) {
                tvExpirySoon.text = if (daysLeft == 0) {
                    getString(R.string.expiry_soon_today)
                } else {
                    getString(R.string.expiry_soon_days, daysLeft)
                }
                expirySoonNotice.visibility = View.VISIBLE
                expiryNoticeHandler.removeCallbacksAndMessages(null)
                expiryNoticeHandler.postDelayed({
                    if (!isFinishing && !isDestroyed) expirySoonNotice.visibility = View.GONE
                }, 10000)
            } else {
                expirySoonNotice.visibility = View.GONE
            }
        } catch (_: Exception) {
            expirySoonNotice.visibility = View.GONE
        }
    }

    private fun logout() {
        PrefsManager.clearUser(this)
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    // =================== NETWORK MONITOR ===================

    private fun startNetworkMonitor() {
        networkCallback = NetworkUtil.registerCallback(
            this,
            onAvailable = {
                runOnUiThread {
                    if (player?.playerError != null || player?.playbackState == Player.STATE_IDLE) {
                        retryCount = 0
                        useSoftwareDecoder = false
                        currentChannel?.let { initPlayer(it) }
                    }
                }
            },
            onLost = {}
        )
    }

    // =================== KEY HANDLING ===================

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Number keys (0-9)
        val digit = when (keyCode) {
            KeyEvent.KEYCODE_0 -> 0
            KeyEvent.KEYCODE_1 -> 1
            KeyEvent.KEYCODE_2 -> 2
            KeyEvent.KEYCODE_3 -> 3
            KeyEvent.KEYCODE_4 -> 4
            KeyEvent.KEYCODE_5 -> 5
            KeyEvent.KEYCODE_6 -> 6
            KeyEvent.KEYCODE_7 -> 7
            KeyEvent.KEYCODE_8 -> 8
            KeyEvent.KEYCODE_9 -> 9
            else -> -1
        }

        // Numpad open → route keys to numpad
        if (isNumpadVisible) {
            if (keyCode == KeyEvent.KEYCODE_BACK) { hideNumpad(); return true }
            if (digit >= 0) { onNumpadDigit(digit); return true }
            return super.onKeyDown(keyCode, event)
        }

        if (digit >= 0 && !isPlaylistVisible && !isSettingsVisible) {
            onNumberKey(digit)
            return true
        }

        // Playlist search gets key events when visible
        if (isPlaylistVisible && etPlaylistSearch.hasFocus()) {
            return super.onKeyDown(keyCode, event)
        }

        return when (keyCode) {
            // OK / Enter → toggle playlist
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                when {
                    isSettingsVisible -> super.onKeyDown(keyCode, event)
                    isPlaylistVisible -> super.onKeyDown(keyCode, event)
                    else -> { showPlaylist(); true }
                }
            }

            // Back → close overlays or exit
            KeyEvent.KEYCODE_BACK -> {
                when {
                    isNumpadVisible -> { hideNumpad(); true }
                    isSettingsVisible -> { hideSettings(); true }
                    isPlaylistVisible -> { hidePlaylist(); true }
                    else -> { finish(); true }
                }
            }

            // Menu → toggle settings
            KeyEvent.KEYCODE_MENU -> {
                when {
                    isPlaylistVisible -> { hidePlaylist(); showSettings(); true }
                    isSettingsVisible -> { hideSettings(); true }
                    else -> { showSettings(); true }
                }
            }

            // Volume Down → hold 1.6s opens numpad; quick tap lowers volume
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (!isPlaylistVisible && !isSettingsVisible) {
                    if (event?.repeatCount == 0) startVolumeHold()
                    true
                } else super.onKeyDown(keyCode, event)
            }

            // Channel +
            KeyEvent.KEYCODE_CHANNEL_UP -> {
                if (!isPlaylistVisible && !isSettingsVisible) {
                    if (event?.repeatCount == 0) {
                        switchChannel(1)
                    } else if (event != null && event.repeatCount > 3 && !isFastBrowsing) {
                        startFastBrowse(1)
                    }
                    true
                } else super.onKeyDown(keyCode, event)
            }

            // Channel -
            KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                if (!isPlaylistVisible && !isSettingsVisible) {
                    if (event?.repeatCount == 0) {
                        switchChannel(-1)
                    } else if (event != null && event.repeatCount > 3 && !isFastBrowsing) {
                        startFastBrowse(-1)
                    }
                    true
                } else super.onKeyDown(keyCode, event)
            }

            // D-pad Up / Right → next channel (when no overlay)
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!isPlaylistVisible && !isSettingsVisible) {
                    switchChannel(1); true
                } else super.onKeyDown(keyCode, event)
            }

            // D-pad Down → prev channel (when no overlay)
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (!isPlaylistVisible && !isSettingsVisible) {
                    switchChannel(-1); true
                } else super.onKeyDown(keyCode, event)
            }

            // D-pad Left → open numpad (when no overlay)
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (!isPlaylistVisible && !isSettingsVisible) {
                    showNumpad(); true
                } else super.onKeyDown(keyCode, event)
            }

            // Play/Pause
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> {
                player?.let { if (it.isPlaying) it.pause() else it.play() }
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> { player?.play(); true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { player?.pause(); true }
            KeyEvent.KEYCODE_MEDIA_STOP -> { player?.stop(); finish(); true }

            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && volumeHoldActive) {
            cancelVolumeHold()
            return true
        }
        if (isFastBrowsing &&
            (keyCode == KeyEvent.KEYCODE_CHANNEL_UP || keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN)) {
            stopFastBrowse()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    // =================== UI HELPERS ===================

    private fun showError(message: String) {
        if (isFinishing || isDestroyed) return
        loadingOverlay.visibility = View.GONE
        tvErrorMsg.text = message
        errorOverlay.visibility = View.VISIBLE
    }

    private fun hideSystemUI() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.let {
                    it.hide(WindowInsets.Type.systemBars())
                    it.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
        } catch (_: Exception) {}
    }

    // =================== LIFECYCLE ===================

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        player?.let {
            if (it.playbackState != Player.STATE_IDLE &&
                it.playbackState != Player.STATE_ENDED &&
                it.playerError == null
            ) {
                it.play()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onDestroy() {
        numberHandler.removeCallbacksAndMessages(null)
        numpadHandler.removeCallbacksAndMessages(null)
        volumeHoldHandler.removeCallbacksAndMessages(null)
        notFoundHandler.removeCallbacksAndMessages(null)
        expiryNoticeHandler.removeCallbacksAndMessages(null)
        fastBrowseHandler.removeCallbacksAndMessages(null)
        retryHandler.removeCallbacksAndMessages(null)
        infoHandler.removeCallbacksAndMessages(null)
        userListener?.remove()
        networkCallback?.let { NetworkUtil.unregisterCallback(this, it) }
        releasePlayer()
        super.onDestroy()
    }

    private fun releasePlayer() {
        player?.let {
            playerView.player = null
            it.stop()
            it.release()
        }
        player = null
    }
}
