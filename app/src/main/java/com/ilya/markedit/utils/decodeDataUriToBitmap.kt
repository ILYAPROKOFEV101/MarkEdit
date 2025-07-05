package com.ilya.markedit.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64

fun decodeDataUriToBitmap(dataUri: String): Bitmap? {
    val base64Data = dataUri.substringAfter("base64,")
    val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}
