package com.tvbykafi.app.data

import com.tvbykafi.app.data.model.Channel

object ChannelHolder {
    var channels: List<Channel> = emptyList()

    fun sortedByChno(): List<Channel> = channels.sortedBy { it.chno }

    fun findByChno(chno: Int): Channel? = channels.find { it.chno == chno }

    fun findByIndex(index: Int): Channel? = channels.getOrNull(index)

    fun getCategories(): List<String> {
        return channels.map { it.category }.distinct().sorted()
    }

    fun filterByCategory(category: String): List<Channel> {
        if (category == "ALL") return channels
        return channels.filter { it.category == category }
    }

    fun search(query: String): List<Channel> {
        val q = query.lowercase().trim()
        if (q.isEmpty()) return channels
        val asNumber = q.toIntOrNull()
        return channels.filter { ch ->
            ch.name.lowercase().contains(q) ||
                ch.category.lowercase().contains(q) ||
                (asNumber != null && ch.chno == asNumber) ||
                ch.chno.toString().startsWith(q)
        }
    }

    fun indexOfChno(chno: Int): Int = channels.indexOfFirst { it.chno == chno }
}
