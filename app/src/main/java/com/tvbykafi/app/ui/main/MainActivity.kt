package com.tvbykafi.app.ui.main

import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.firebase.firestore.ListenerRegistration
import com.tvbykafi.app.R
import com.tvbykafi.app.data.FirebaseRepository
import com.tvbykafi.app.data.model.AppConfig
import com.tvbykafi.app.data.model.User
import com.tvbykafi.app.ui.login.LoginActivity
import com.tvbykafi.app.util.DeviceUtils
import com.tvbykafi.app.util.NetworkUtil
import com.tvbykafi.app.util.PrefsManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val repo = FirebaseRepository.getInstance()
    private var userListener: ListenerRegistration? = null
    private var configListener: ListenerRegistration? = null

    var currentUser: User? = null
        private set
    var appConfig: AppConfig? = null
        private set

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var tvExpiryTimer: TextView
    private lateinit var tvNotice: TextView
    private lateinit var offlineBanner: LinearLayout
    private var currentFragmentTag: String? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val expiryFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
    private val displayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val userId = PrefsManager.getUserId(this) ?: run {
            goToLogin(); return
        }

        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawerLayout)
        tvExpiryTimer = findViewById(R.id.tvExpiryTimer)
        tvNotice = findViewById(R.id.tvNotice)
        offlineBanner = findViewById(R.id.offlineBanner)

        setupNavigation()
        startListeners(userId)
        startNetworkMonitor()

        if (savedInstanceState == null) {
            showFragment(HomeFragment(), "home")
        }
    }

    private fun setupNavigation() {
        val isTV = DeviceUtils.isTV(this)

        findViewById<View>(R.id.btnMenu).setOnClickListener {
            drawerLayout.openDrawer(findViewById(R.id.navDrawer))
        }

        val navItems = listOf(
            findViewById<View>(R.id.navHome),
            findViewById<View>(R.id.navWishlist),
            findViewById<View>(R.id.navProfile),
            findViewById<View>(R.id.navSupport),
            findViewById<View>(R.id.navLogout)
        )

        findViewById<View>(R.id.navHome).setOnClickListener {
            showFragment(HomeFragment(), "home"); closeDrawer()
        }
        findViewById<View>(R.id.navWishlist).setOnClickListener {
            showFragment(WishlistFragment(), "wishlist"); closeDrawer()
        }
        findViewById<View>(R.id.navProfile).setOnClickListener {
            showFragment(ProfileFragment(), "profile"); closeDrawer()
        }
        findViewById<View>(R.id.navSupport).setOnClickListener {
            showFragment(SupportFragment(), "support"); closeDrawer()
        }
        findViewById<View>(R.id.navLogout).setOnClickListener {
            logout()
        }

        if (isTV) {
            navItems.forEach { item ->
                item.isFocusable = true
                item.isFocusableInTouchMode = false
            }
        }

        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerClosed(drawerView: View) {
                findViewById<View>(R.id.fragmentContainer).requestFocus()
            }
        })
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val navDrawer = findViewById<View>(R.id.navDrawer)
        when (keyCode) {
            KeyEvent.KEYCODE_MENU -> {
                if (drawerLayout.isDrawerOpen(navDrawer)) {
                    closeDrawer()
                } else {
                    drawerLayout.openDrawer(navDrawer)
                    findViewById<View>(R.id.navHome).requestFocus()
                }
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (drawerLayout.isDrawerOpen(navDrawer)) {
                    closeDrawer()
                    return true
                }
                if (currentFragmentTag != "home") {
                    showFragment(HomeFragment(), "home")
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun startListeners(userId: String) {
        userListener = repo.observeUser(
            userId,
            onUpdate = { user ->
                currentUser = user
                if (!isFinishing && !isDestroyed) {
                    runOnUiThread { updateUI(user) }

                    val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
                    if (currentFragment is UserUpdateListener && currentFragment.isAdded) {
                        runOnUiThread { currentFragment.onUserUpdated(user) }
                    }
                }
            },
            onDeleted = {
                if (!isFinishing && !isDestroyed) {
                    runOnUiThread { logout() }
                }
            }
        )

        configListener = repo.observeAppConfig { config ->
            appConfig = config
            if (!isFinishing && !isDestroyed) {
                runOnUiThread { updateConfigUI(config) }

                val currentFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
                if (currentFragment is ConfigUpdateListener && currentFragment.isAdded) {
                    runOnUiThread { currentFragment.onConfigUpdated(config) }
                }
            }
        }
    }

    private fun updateUI(user: User) {
        if (user.expiry.isNotEmpty()) {
            try {
                val end = parseExpiry(user.expiry)
                val displayDate = if (user.expiry.contains("T")) {
                    user.expiry.split("T")[0]
                } else {
                    user.expiry
                }
                tvExpiryTimer.text = displayDate

                val isExpired = end != null && Date().after(end)
                tvExpiryTimer.setTextColor(
                    getColor(if (isExpired) R.color.status_expired else R.color.status_active)
                )
            } catch (_: Exception) {
                tvExpiryTimer.text = user.expiry
            }
        } else {
            tvExpiryTimer.text = getString(R.string.no_limit)
        }
    }

    private fun updateConfigUI(config: AppConfig) {
        if (config.live_notice.isNotEmpty()) {
            tvNotice.visibility = View.VISIBLE
            tvNotice.text = config.live_notice
            tvNotice.isSelected = true
        } else {
            tvNotice.visibility = View.GONE
        }
    }

    fun showFragment(fragment: Fragment, tag: String = "") {
        currentFragmentTag = tag
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    fun navigateToProfile() {
        showFragment(ProfileFragment(), "profile")
    }

    private fun closeDrawer() {
        drawerLayout.closeDrawers()
    }

    private fun logout() {
        PrefsManager.clearUser(this)
        goToLogin()
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    fun isSubscriptionExpired(): Boolean {
        val user = currentUser ?: return false
        if (user.expiry.isEmpty()) return false
        return try {
            val end = parseExpiry(user.expiry)
            end != null && Date().after(end)
        } catch (_: Exception) {
            false
        }
    }

    private fun parseExpiry(expiry: String): Date? {
        return try {
            if (expiry.contains("T")) {
                expiryFormat.parse(expiry)
            } else {
                displayFormat.parse(expiry)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun startNetworkMonitor() {
        if (!NetworkUtil.isOnline(this)) {
            offlineBanner.visibility = View.VISIBLE
        }
        networkCallback = NetworkUtil.registerCallback(
            this,
            onAvailable = {
                runOnUiThread { offlineBanner.visibility = View.GONE }
            },
            onLost = {
                runOnUiThread { offlineBanner.visibility = View.VISIBLE }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        userListener?.remove()
        configListener?.remove()
        networkCallback?.let { NetworkUtil.unregisterCallback(this, it) }
    }

    interface UserUpdateListener {
        fun onUserUpdated(user: User)
    }

    interface ConfigUpdateListener {
        fun onConfigUpdated(config: AppConfig)
    }
}
