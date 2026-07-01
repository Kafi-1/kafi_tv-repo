package com.tvbykafi.app.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.tvbykafi.app.R
import com.tvbykafi.app.data.model.AppConfig

class SupportFragment : Fragment(), MainActivity.ConfigUpdateListener {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_support, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mainActivity = activity as? MainActivity ?: return
        mainActivity.appConfig?.let { updateSupportInfo(view, it) }

        view.findViewById<View>(R.id.btnWhatsApp).setOnClickListener {
            val num = mainActivity.appConfig?.support_whatsapp ?: return@setOnClickListener
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$num")))
        }

        view.findViewById<View>(R.id.btnTelegram).setOnClickListener {
            val link = mainActivity.appConfig?.support_telegram ?: return@setOnClickListener
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
        }
    }

    private fun updateSupportInfo(view: View, config: AppConfig) {
        view.findViewById<TextView>(R.id.tvSupportEmail).text = config.support_email.ifEmpty { "N/A" }
    }

    override fun onConfigUpdated(config: AppConfig) {
        view?.let { updateSupportInfo(it, config) }
    }
}
