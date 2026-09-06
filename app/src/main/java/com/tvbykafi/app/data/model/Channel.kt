package com.tvbykafi.app.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Channel(
    val id: String = "",
    val chno: Int = 0,
    val name: String = "",
    val logo: String = "",
    val url: String = "",
    val category: String = "General",
    val group: String = "",
    val status: String = "live",
    val drmLicenseUrl: String = ""
) : Parcelable
