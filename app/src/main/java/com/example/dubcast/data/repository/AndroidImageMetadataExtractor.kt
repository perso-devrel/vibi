package com.example.dubcast.data.repository

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.dubcast.domain.model.ImageInfo
import com.example.dubcast.domain.usecase.input.ImageMetadataExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidImageMetadataExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) : ImageMetadataExtractor {

    override suspend fun extract(uri: String): ImageInfo? {
        val contentUri = Uri.parse(uri)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        return try {
            context.contentResolver.openInputStream(contentUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return null

            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            ImageInfo(
                uri = uri,
                width = options.outWidth,
                height = options.outHeight
            )
        } catch (_: Exception) {
            null
        }
    }
}
