package com.tvbykafi.app.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.tvbykafi.app.R
import com.tvbykafi.app.data.FirebaseRepository
import com.tvbykafi.app.ui.player.PlayerActivity
import com.tvbykafi.app.util.PrefsManager

class LoginActivity : AppCompatActivity() {

    private val repo = FirebaseRepository.getInstance()

    private lateinit var etUserId: EditText
    private lateinit var etPin: EditText
    private lateinit var btnLogin: Button
    private lateinit var tvError: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Already logged in → skip login and go straight to the player
        if (PrefsManager.getUserId(this) != null) {
            goToPlayer()
            return
        }

        setContentView(R.layout.activity_login)

        etUserId = findViewById(R.id.etUserId)
        etPin = findViewById(R.id.etPin)
        btnLogin = findViewById(R.id.btnLogin)
        tvError = findViewById(R.id.tvError)

        btnLogin.setOnClickListener { attemptLogin() }

        etPin.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin()
                true
            } else false
        }
    }

    private fun attemptLogin() {
        val email = etUserId.text.toString().trim()
        val pin = etPin.text.toString().trim()

        if (email.isEmpty()) {
            showError(getString(R.string.error_empty_email))
            return
        }
        if (pin.isEmpty()) {
            showError(getString(R.string.error_empty_pin))
            return
        }

        setLoading(true)
        val deviceId = PrefsManager.getDeviceId(this)

        repo.login(
            email = email,
            pin = pin,
            deviceId = deviceId,
            onSuccess = { docId, _ ->
                PrefsManager.saveUserId(this, docId)
                goToPlayer()
            },
            onDeviceLimit = {
                setLoading(false)
                showError(getString(R.string.error_device_limit))
            },
            onError = {
                setLoading(false)
                showError(getString(R.string.error_invalid_credentials))
            }
        )
    }

    private fun setLoading(loading: Boolean) {
        btnLogin.isEnabled = !loading
        btnLogin.text = getString(
            if (loading) R.string.btn_authenticating else R.string.btn_start_streaming
        )
        if (loading) tvError.visibility = View.GONE
    }

    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
    }

    private fun goToPlayer() {
        startActivity(Intent(this, PlayerActivity::class.java))
        finish()
    }
}
