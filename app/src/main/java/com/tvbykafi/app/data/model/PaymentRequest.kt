package com.tvbykafi.app.data.model

data class PaymentRequest(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val number: String = "",
    val trxId: String = "",
    val status: String = "Pending",
    val timestamp: String = ""
)
