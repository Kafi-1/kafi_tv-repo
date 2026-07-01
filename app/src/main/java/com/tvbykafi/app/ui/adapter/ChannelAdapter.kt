package com.tvbykafi.app.ui.adapter

import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tvbykafi.app.R
import com.tvbykafi.app.data.model.Channel

class ChannelAdapter(
    private val onChannelClick: (Channel) -> Unit,
    private val onFavClick: (Channel) -> Unit,
    private val isFavorite: (String) -> Boolean
) : ListAdapter<Channel, ChannelAdapter.VH>(DIFF) {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val ivLogo: ImageView = view.findViewById(R.id.ivChannelLogo)
        val tvName: TextView = view.findViewById(R.id.tvChannelName)
        val btnFav: ImageButton = view.findViewById(R.id.btnFav)
        val channelBg: View = view.findViewById(R.id.channelBg)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val ch = getItem(position)
        val isTV = isTelevision(holder.itemView)

        holder.tvName.text = ch.name

        val glideRequest = Glide.with(holder.ivLogo.context)
            .load(ch.logo)
            .placeholder(R.drawable.ic_tv_default)
            .error(R.drawable.ic_tv_default)
            .circleCrop()
            .override(if (isTV) 48 else 60)

        if (isTV) {
            glideRequest.skipMemoryCache(false)
        }

        glideRequest.into(holder.ivLogo)

        val fav = isFavorite(ch.id)
        holder.btnFav.setImageResource(
            if (fav) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off
        )

        holder.itemView.setOnClickListener { onChannelClick(ch) }

        if (isTV) {
            holder.btnFav.visibility = View.GONE
            holder.itemView.setOnLongClickListener {
                onFavClick(ch)
                true
            }
        } else {
            holder.btnFav.visibility = View.VISIBLE
            holder.btnFav.setOnClickListener { onFavClick(ch) }
        }

        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
            holder.channelBg.setBackgroundResource(
                if (hasFocus) R.drawable.bg_channel_focused else R.drawable.bg_channel_circle
            )
            if (hasFocus) {
                v.animate().scaleX(1.15f).scaleY(1.15f).setDuration(200).start()
                holder.tvName.setTextColor(v.context.getColor(R.color.text_primary))
            } else {
                v.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
                holder.tvName.setTextColor(v.context.getColor(R.color.text_secondary))
            }
        }
    }

    private fun isTelevision(view: View): Boolean {
        val uiMode = view.context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        return uiMode == Configuration.UI_MODE_TYPE_TELEVISION
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Channel>() {
            override fun areItemsTheSame(a: Channel, b: Channel) = a.id == b.id
            override fun areContentsTheSame(a: Channel, b: Channel) = a == b
        }
    }
}
