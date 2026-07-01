package com.tvbykafi.app.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.tvbykafi.app.R
import com.tvbykafi.app.data.FirebaseRepository
import com.tvbykafi.app.data.model.AppConfig
import com.tvbykafi.app.data.model.User

class ProfileFragment : Fragment(), MainActivity.UserUpdateListener, MainActivity.ConfigUpdateListener {

    private val repo = FirebaseRepository.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mainActivity = activity as? MainActivity ?: return
        val user = mainActivity.currentUser ?: return

        populateProfile(view, user)

        mainActivity.appConfig?.let { populateConfig(view, it) }

        view.findViewById<Button>(R.id.btnSubmitPayment).setOnClickListener {
            submitPayment(view, user)
        }
    }

    private fun populateProfile(view: View, user: User) {
        view.findViewById<TextView>(R.id.tvAvatarLetter).text = user.name.take(1).uppercase()
        view.findViewById<TextView>(R.id.tvProfileName).text = user.name
        view.findViewById<TextView>(R.id.tvProfileEmail).text = user.email
        view.findViewById<TextView>(R.id.tvProfilePin).text = "••••••"
        view.findViewById<TextView>(R.id.tvProfileStart).text = user.start_at.ifEmpty { getString(R.string.label_na) }
        view.findViewById<TextView>(R.id.tvProfileExpiry).text = user.expiry.ifEmpty { getString(R.string.label_na) }
    }

    private fun populateConfig(view: View, config: AppConfig) {
        val priceText = "${getString(R.string.monthly_price_label)} ${config.monthly_price} ${getString(R.string.bdt)}"
        view.findViewById<TextView>(R.id.tvMonthlyPrice).text = priceText
    }

    private fun submitPayment(view: View, user: User) {
        val etNumber = view.findViewById<EditText>(R.id.etPayNumber)
        val etTrx = view.findViewById<EditText>(R.id.etPayTrx)
        val btn = view.findViewById<Button>(R.id.btnSubmitPayment)

        val number = etNumber.text.toString().trim()
        val trxId = etTrx.text.toString().trim()

        if (number.isEmpty() || trxId.isEmpty()) {
            Toast.makeText(context, "Please fill all fields!", Toast.LENGTH_SHORT).show()
            return
        }

        btn.text = getString(R.string.btn_submitting)
        btn.isEnabled = false

        repo.submitPayment(
            userId = user.id,
            userName = user.name,
            number = number,
            trxId = trxId,
            onSuccess = {
                activity?.runOnUiThread {
                    btn.text = getString(R.string.btn_submit_request)
                    btn.isEnabled = true
                    etNumber.text.clear()
                    etTrx.text.clear()
                    Toast.makeText(context, getString(R.string.payment_success), Toast.LENGTH_LONG).show()
                }
            },
            onError = {
                activity?.runOnUiThread {
                    btn.text = getString(R.string.btn_submit_request)
                    btn.isEnabled = true
                    Toast.makeText(context, getString(R.string.payment_error), Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    override fun onUserUpdated(user: User) {
        view?.let { populateProfile(it, user) }
    }

    override fun onConfigUpdated(config: AppConfig) {
        view?.let { populateConfig(it, config) }
    }
}
