package com.tvbykafi.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tvbykafi.app.R
import com.tvbykafi.app.data.model.PaymentRequest

class PaymentAdapter : ListAdapter<PaymentRequest, PaymentAdapter.VH>(DIFF) {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTrxId: TextView = view.findViewById(R.id.tvTrxId)
        val tvNumber: TextView = view.findViewById(R.id.tvPaymentNumber)
        val tvStatus: TextView = view.findViewById(R.id.tvPaymentStatus)
        val tvDate: TextView = view.findViewById(R.id.tvPaymentDate)
        val statusDot: View = view.findViewById(R.id.statusDot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_payment, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.tvTrxId.text = item.trxId
        holder.tvNumber.text = item.number
        holder.tvStatus.text = item.status

        val date = if (item.timestamp.contains("T")) item.timestamp.split("T")[0] else item.timestamp
        holder.tvDate.text = date

        val ctx = holder.itemView.context
        when (item.status.lowercase()) {
            "approved" -> {
                holder.tvStatus.setTextColor(ctx.getColor(R.color.accent_green))
                holder.statusDot.setBackgroundResource(R.drawable.bg_dot_green)
            }
            "rejected" -> {
                holder.tvStatus.setTextColor(ctx.getColor(R.color.text_error))
                holder.statusDot.setBackgroundResource(R.drawable.bg_dot_red)
            }
            else -> {
                holder.tvStatus.setTextColor(ctx.getColor(R.color.accent_orange))
                holder.statusDot.setBackgroundResource(R.drawable.bg_dot_orange)
            }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<PaymentRequest>() {
            override fun areItemsTheSame(a: PaymentRequest, b: PaymentRequest) = a.id == b.id
            override fun areContentsTheSame(a: PaymentRequest, b: PaymentRequest) = a == b
        }
    }
}
