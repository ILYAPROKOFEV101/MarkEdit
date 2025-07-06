package com.ilya.markedit.utils

import android.graphics.Bitmap
import android.util.LruCache

val imageCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(
    (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
) {
    override fun sizeOf(key: String, value: Bitmap): Int {
        return value.byteCount / 1024
    }
}
