package com.ilya.markedit.utils

import android.net.Uri

data class FileHistoryItem(
    val uri: Uri?,
    val url: String?,
    val displayName: String,
    val content: String? = null
)