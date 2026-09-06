package com.tvbykafi.app.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tvbykafi.app.R
import com.tvbykafi.app.data.model.Channel

class PlaylistAdapter(
    private val onChannelClick: (Channel, Int) -> Unit
) : ListAdapter<Channel, PlaylistAdapter.VH>(DIFF) {

    private var currentPlayingChno: Int = -1

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvChno: TextView = view.findViewById(R.id.tvChno)
        val ivLogo: ImageView = view.findViewById(R.id.ivLogo)
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvCategory: TextView = view.findViewById(R.id.tvCategory)
        val playingIndicator: View = view.findViewById(R.id.playingIndicator)
    }

    fun setCurrentPlaying(chno: Int) {
        val oldChno = currentPlayingChno
        currentPlayingChno = chno
        currentList.forEachIndexed { index, channel ->
            if (channel.chno == oldChno || channel.chno == chno) {
                notifyItemChanged(index)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_playlist_channel, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val ch = getItem(position)
        val isPlaying = ch.chno == currentPlayingChno

        holder.tvChno.text = ch.chno.toString()
        holder.tvName.text = ch.name
        holder.tvCategory.text = ch.category.uppercase()

        holder.playingIndicator.visibility = if (isPlaying) View.VISIBLE else View.GONE

        if (isPlaying) {
            holder.tvName.setTextColor(holder.itemView.context.getColor(R.color.primary))
            holder.tvChno.setTextColor(holder.itemView.context.getColor(R.color.primary))
        } else {
            holder.tvName.setTextColor(holder.itemView.context.getColor(R.color.text_primary))
            holder.tvChno.setTextColor(holder.itemView.context.getColor(R.color.text_secondary))
        }

        Glide.with(holder.ivLogo.context)
            .load(ch.logo)
            .placeholder(R.drawable.ic_tv_default)
            .error(R.drawable.ic_tv_default)
            .circleCrop()
            .override(40)
            .into(holder.ivLogo)

        holder.itemView.setOnClickListener { onChannelClick(ch, position) }

        holder.itemView.isFocusable = true
        holder.itemView.isFocusableInTouchMode = false

        holder.itemView.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                v.setBackgroundColor(v.context.getColor(R.color.bg_card_elevated))
                v.animate().scaleX(1.02f).scaleY(1.02f).setDuration(150).start()
            } else {
                v.setBackgroundColor(0x00000000)
                v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
            }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Channel>() {
            override fun areItemsTheSame(a: Channel, b: Channel) = a.chno == b.chno
            override fun areContentsTheSame(a: Channel, b: Channel) = a == b
        }
    }
}
