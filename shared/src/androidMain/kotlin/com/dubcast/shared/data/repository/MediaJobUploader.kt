package com.dubcast.shared.data.repository

import android.content.Context
import android.net.Uri
import com.dubcast.shared.data.remote.api.BinaryPart
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaJobUploader(private val context: Context) {

    suspend fun loadAsBinaryPart(
        sourceUri: String,
        mediaType: String,
        prefix: String
    ): BinaryPart = withContext(Dispatchers.IO) {
        val (ext, contentType) = resolveMediaType(mediaType)
        val bytes = context.contentResolver.openInputStream(Uri.parse(sourceUri))?.use { it.readBytes() }
            ?: throw IOException("Cannot open source media")
        BinaryPart(
            fieldName = "file",
            filename = "${prefix}.${ext}",
            bytes = bytes,
            contentType = contentType
        )
    }

    private fun resolveMediaType(mediaType: String): Pair<String, String> = when (mediaType) {
        "VIDEO" -> "mp4" to "video/mp4"
        "AUDIO" -> "mp3" to "audio/mpeg"
        else -> throw IllegalArgumentException("Unsupported media type")
    }
}
