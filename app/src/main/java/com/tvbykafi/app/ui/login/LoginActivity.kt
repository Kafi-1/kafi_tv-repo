package com.tvbykafi.app.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.tvbykafi.app.R
import com.tvbykafi.app.data.FirebaseRepository
import com.tvbykafi.app.ui.main.MainActivity
import com.tvbykafi.app.util.DeviceUtils
import com.tvbykafi.app.util.NetworkUtil
import com.tvbykafi.app.util.PrefsManager

class LoginActivity : AppCompatActivity() {

    private val repo = FirebaseRepository.getInstance()

    private lateinit var etUserId: EditText
    private lateinit var etPin: EditText
    private lateinit var btnLogin: Button
    private lateinit var tvError: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (PrefsManager.getUserId(this) != null) {
            goToMain()
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

        if (DeviceUtils.isTV(this)) {
            etUserId.requestFocus()
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
        if (!NetworkUtil.isOnline(this)) {
            showError(getString(R.string.no_internet))
            return
        }

        setLoading(true)
        hideError()

        val deviceId = PrefsManager.getDeviceId(this)

        repo.login(
            email = email,
            pin = pin,
            deviceId = deviceId,
            onSuccess = { docId, _ ->
                runOnUiThread {
                    PrefsManager.saveUserId(this, docId)
                    goToMain()
                }
            },
            onDeviceLimit = {
                runOnUiThread {
                    setLoading(false)
                    showError(getString(R.string.error_device_limit))
                }
            },
            onError = {
                runOnUiThread {
                    setLoading(false)
                    showError(getString(R.string.error_invalid_credentials))
                }
            }
        )
    }

    private fun setLoading(loading: Boolean) {
        btnLogin.isEnabled = !loading
        btnLogin.text = getString(
            if (loading) R.string.btn_authenticating else R.string.btn_start_streaming
        )
        etUserId.isEnabled = !loading
        etPin.isEnabled = !loading
    }

    private fun showError(msg: String) {
        tvError.text = msg
        tvError.visibility = View.VISIBLE
    }

    private fun hideError() {
        tvError.visibility = View.GONE
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (btnLogin.isFocused) {
                attemptLogin()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
