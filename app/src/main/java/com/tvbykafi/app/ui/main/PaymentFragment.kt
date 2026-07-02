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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.ListenerRegistration
import com.tvbykafi.app.R
import com.tvbykafi.app.data.FirebaseRepository
import com.tvbykafi.app.data.model.AppConfig
import com.tvbykafi.app.ui.adapter.PaymentAdapter

class PaymentFragment : Fragment(), MainActivity.ConfigUpdateListener {

    private val repo = FirebaseRepository.getInstance()
    private var paymentListener: ListenerRegistration? = null
    private lateinit var paymentAdapter: PaymentAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_payment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mainActivity = activity as? MainActivity ?: return
        val user = mainActivity.currentUser ?: return

        mainActivity.appConfig?.let { updatePaymentInfo(view, it) }

        paymentAdapter = PaymentAdapter()
        val rvPayments = view.findViewById<RecyclerView>(R.id.rvPayments)
        rvPayments.layoutManager = LinearLayoutManager(context)
        rvPayments.adapter = paymentAdapter

        paymentListener = repo.observeUserPayments(user.id) { payments ->
            if (isAdded) {
                activity?.runOnUiThread {
                    paymentAdapter.submitList(payments)
                    view.findViewById<View>(R.id.tvNoPayments).visibility =
                        if (payments.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }

        view.findViewById<Button>(R.id.btnSubmitPayment).setOnClickListener {
            submitPayment(view, user)
        }
    }

    private fun updatePaymentInfo(view: View, config: AppConfig) {
        if (config.monthly_price.isNotEmpty()) {
            view.findViewById<TextView>(R.id.tvPayPrice).text =
                "${getString(R.string.monthly_price_label)} ${config.monthly_price} ${getString(R.string.bdt)}"
        }
        if (config.bkash_num.isNotEmpty()) {
            view.findViewById<View>(R.id.payLayoutBkash).visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.tvPayBkash).text = config.bkash_num
        }
        if (config.nagad_num.isNotEmpty()) {
            view.findViewById<View>(R.id.payLayoutNagad).visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.tvPayNagad).text = config.nagad_num
        }
    }

    private fun submitPayment(view: View, user: com.tvbykafi.app.data.model.User) {
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

    override fun onConfigUpdated(config: AppConfig) {
        view?.let { updatePaymentInfo(it, config) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        paymentListener?.remove()
    }
}
