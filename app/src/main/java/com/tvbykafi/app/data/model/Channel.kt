package com.tvbykafi.app.data.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Channel(
    val id: String = "",
    val name: String = "",
    val logo: String = "",
    val url: String = "",
    val category: String = "General",
    val status: String = "live",
    val drmLicenseUrl: String = ""
) : Parcelable
