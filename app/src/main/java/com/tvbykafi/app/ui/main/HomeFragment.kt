package com.tvbykafi.app.ui.main

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.firebase.firestore.ListenerRegistration
import com.tvbykafi.app.R
import com.tvbykafi.app.data.FirebaseRepository
import com.tvbykafi.app.data.model.Channel
import com.tvbykafi.app.data.model.User
import com.tvbykafi.app.ui.adapter.CategoryAdapter
import com.tvbykafi.app.ui.adapter.ChannelAdapter
import com.tvbykafi.app.ui.player.PlayerActivity
import com.tvbykafi.app.util.DeviceUtils

class HomeFragment : Fragment(), MainActivity.UserUpdateListener {

    private val repo = FirebaseRepository.getInstance()
    private var channelListener: ListenerRegistration? = null
    private var categoryListener: ListenerRegistration? = null

    private var allChannels = listOf<Channel>()
    private var favorites = listOf<String>()
    private var activeCategory = "ALL"

    private lateinit var channelAdapter: ChannelAdapter
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var rvChannels: RecyclerView
    private lateinit var rvCategories: RecyclerView
    private lateinit var etSearch: EditText
    private lateinit var expiryWarning: LinearLayout
    private lateinit var swipeRefresh: SwipeRefreshLayout

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvChannels = view.findViewById(R.id.rvChannels)
        rvCategories = view.findViewById(R.id.rvCategories)
        etSearch = view.findViewById(R.id.etSearch)
        expiryWarning = view.findViewById(R.id.expiryWarning)
        swipeRefresh = view.findViewById(R.id.swipeRefresh)

        val mainActivity = activity as? MainActivity ?: return
        favorites = mainActivity.currentUser?.favorites ?: emptyList()

        val isTV = DeviceUtils.isTV(requireContext())

        if (isTV) {
            etSearch.isFocusable = false
            etSearch.isFocusableInTouchMode = false
            etSearch.visibility = View.GONE
            swipeRefresh.isEnabled = false
        }

        setupAdapters()
        setupSearch()
        checkExpiry(mainActivity)

        if (!mainActivity.isSubscriptionExpired()) {
            startListeners()
        }

        swipeRefresh.setColorSchemeResources(R.color.primary)
        swipeRefresh.setOnRefreshListener {
            channelListener?.remove()
            categoryListener?.remove()
            startListeners()
            swipeRefresh.isRefreshing = false
        }

        view.findViewById<View>(R.id.btnRenewNow)?.setOnClickListener {
            mainActivity.navigateToProfile()
        }

        if (isTV) {
            rvChannels.post { rvChannels.requestFocus() }
        }
    }

    private fun setupAdapters() {
        val spanCount = DeviceUtils.getOptimalSpanCount(requireContext())

        channelAdapter = ChannelAdapter(
            onChannelClick = { channel -> playChannel(channel) },
            onFavClick = { channel -> toggleFavorite(channel) },
            isFavorite = { id -> favorites.contains(id) }
        )

        rvChannels.layoutManager = GridLayoutManager(context, spanCount)
        rvChannels.adapter = channelAdapter
        rvChannels.setItemViewCacheSize(20)
        rvChannels.recycledViewPool.setMaxRecycledViews(0, 30)

        categoryAdapter = CategoryAdapter { cat ->
            activeCategory = cat
            filterChannels()
        }

        rvCategories.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        rvCategories.adapter = categoryAdapter
    }

    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterChannels()
            }
        })
    }

    private fun startListeners() {
        channelListener = repo.observeChannels { channels ->
            allChannels = channels
            if (isAdded) {
                activity?.runOnUiThread { filterChannels() }
            }
        }

        categoryListener = repo.observeCategories { cats ->
            if (isAdded) {
                activity?.runOnUiThread { categoryAdapter.setCategories(cats) }
            }
        }
    }

    private fun filterChannels() {
        val searchTerm = etSearch.text.toString().lowercase()
        var filtered = allChannels

        if (activeCategory != "ALL") {
            filtered = filtered.filter { it.category == activeCategory }
        }

        if (searchTerm.isNotEmpty()) {
            filtered = filtered.filter { it.name.lowercase().contains(searchTerm) }
        }

        channelAdapter.submitList(filtered)
    }

    private fun playChannel(channel: Channel) {
        val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra("channel_name", channel.name)
            putExtra("channel_url", channel.url)
        }
        startActivity(intent)
    }

    private fun toggleFavorite(channel: Channel) {
        val mainActivity = activity as? MainActivity ?: return
        val userId = mainActivity.currentUser?.id ?: return

        if (favorites.contains(channel.id)) {
            repo.removeFavorite(userId, channel.id)
        } else {
            repo.addFavorite(userId, channel.id)
        }
    }

    private fun checkExpiry(mainActivity: MainActivity) {
        if (mainActivity.isSubscriptionExpired()) {
            expiryWarning.visibility = View.VISIBLE
            etSearch.visibility = View.GONE
            rvCategories.visibility = View.GONE
            rvChannels.visibility = View.GONE

            val config = mainActivity.appConfig
            if (config != null && config.expire_message.isNotEmpty()) {
                view?.findViewById<TextView>(R.id.tvExpiredMsg)?.text = config.expire_message
            }
        }
    }

    override fun onUserUpdated(user: User) {
        favorites = user.favorites
        filterChannels()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        channelListener?.remove()
        categoryListener?.remove()
    }
}
