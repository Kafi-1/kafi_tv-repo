package com.tvbykafi.app.data.model

data class User(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val pin: String = "",
    val phone: String = "",
    val expiry: String = "",
    val start_at: String = "",
    val role: String = "user",
    val device_limit: Int = 2,
    val deviceIds: List<String> = emptyList(),
    val favorites: List<String> = emptyList()
)
