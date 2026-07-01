package com.tvbykafi.app.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.tvbykafi.app.R
import com.tvbykafi.app.data.FirebaseRepository
import com.tvbykafi.app.ui.main.MainActivity
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

        btnLogin.setOnClickListener { doLogin() }

        etPin.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                doLogin()
                true
            } else false
        }
    }

    private fun doLogin() {
        val email = etUserId.text.toString().trim()
        val pin = etPin.text.toString().trim()

        if (email.isEmpty() || pin.isEmpty()) {
            showError(getString(R.string.error_invalid_credentials))
            return
        }

        if (!NetworkUtil.isOnline(this)) {
            showError(getString(R.string.no_internet))
            return
        }

        tvError.visibility = View.GONE
        btnLogin.isEnabled = false
        btnLogin.text = getString(R.string.btn_logging_in)

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
                    btnLogin.isEnabled = true
                    btnLogin.text = getString(R.string.btn_start_streaming)
                    showError(getString(R.string.error_device_limit))
                }
            },
            onError = {
                runOnUiThread {
                    btnLogin.isEnabled = true
                    btnLogin.text = getString(R.string.btn_start_streaming)
                    showError(getString(R.string.error_invalid_credentials))
                }
            }
        )
    }

    private fun showError(msg: String) {
        tvError.text = msg
        tvError.visibility = View.VISIBLE
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
