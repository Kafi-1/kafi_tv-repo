package com.tvbykafi.app.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tvbykafi.app.R
import com.tvbykafi.app.data.ChannelHolder
import com.tvbykafi.app.data.FirebaseRepository
import com.tvbykafi.app.data.model.Channel
import com.tvbykafi.app.data.model.User
import com.tvbykafi.app.ui.adapter.ChannelAdapter
import com.tvbykafi.app.ui.player.PlayerActivity
import com.tvbykafi.app.util.DeviceUtils

class WishlistFragment : Fragment(), MainActivity.UserUpdateListener {

    private val repo = FirebaseRepository.getInstance()

    private var allChannels = listOf<Channel>()
    private var favChannels = listOf<Channel>()
    private var favorites = listOf<String>()
    private var channelsLoaded = false

    private lateinit var channelAdapter: ChannelAdapter
    private lateinit var rvWishlist: RecyclerView
    private lateinit var tvEmpty: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_wishlist, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvWishlist = view.findViewById(R.id.rvWishlist)
        tvEmpty = view.findViewById(R.id.tvEmptyWishlist)

        val mainActivity = activity as? MainActivity ?: return
        favorites = mainActivity.currentUser?.favorites ?: emptyList()

        val spanCount = DeviceUtils.getOptimalSpanCount(requireContext())

        channelAdapter = ChannelAdapter(
            onChannelClick = { channel -> playChannel(channel) },
            onFavClick = { channel ->
                val userId = mainActivity.currentUser?.id ?: return@ChannelAdapter
                repo.removeFavorite(userId, channel.id)
            },
            isFavorite = { true }
        )

        rvWishlist.layoutManager = GridLayoutManager(context, spanCount)
        rvWishlist.adapter = channelAdapter

        // One-time fetch instead of realtime listener
        repo.fetchChannels(
            onSuccess = { channels ->
                allChannels = channels
                channelsLoaded = true
                if (isAdded) {
                    activity?.runOnUiThread { updateWishlist() }
                }
            }
        )
    }

    private fun playChannel(channel: Channel) {
        val index = favChannels.indexOfFirst { it.id == channel.id }
        ChannelHolder.channels = favChannels
        val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra("channel_name", channel.name)
            putExtra("channel_url", channel.url)
            putExtra("channel_drm_url", channel.drmLicenseUrl)
            putExtra("channel_index", if (index >= 0) index else 0)
        }
        startActivity(intent)
    }

    private fun updateWishlist() {
        if (!channelsLoaded) return
        favChannels = allChannels.filter { favorites.contains(it.id) }
        channelAdapter.submitList(favChannels)

        if (favChannels.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            rvWishlist.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            rvWishlist.visibility = View.VISIBLE
        }
    }

    override fun onUserUpdated(user: User) {
        favorites = user.favorites
        updateWishlist()
    }
}
