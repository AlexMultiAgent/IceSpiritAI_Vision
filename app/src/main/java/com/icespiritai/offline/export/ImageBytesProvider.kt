package com.icespiritai.offline.export

import android.content.Context
import android.net.Uri

fun interface ImageBytesProvider {
    fun open(uri: Uri): ByteArray

    companion object {
        fun from(context: Context): ImageBytesProvider = ImageBytesProvider { uri ->
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalArgumentException("Cannot open URI: $uri")
        }
    }
}