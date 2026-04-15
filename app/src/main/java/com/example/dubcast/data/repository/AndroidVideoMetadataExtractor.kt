package com.example.dubcast.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.dubcast.domain.model.VideoInfo
import com.example.dubcast.domain.usecase.input.VideoMetadataExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidVideoMetadataExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) : VideoMetadataExtractor {

    override suspend fun extract(uri: String): VideoInfo? {
        val contentUri = Uri.parse(uri)
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, contentUri)

            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: return null
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return null
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return null
            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: return null

            val fileName = getFileName(contentUri) ?: "unknown"
            val fileSize = getFileSize(contentUri) ?: 0L

            VideoInfo(
                uri = uri,
                fileName = fileName,
                mimeType = mimeType,
                durationMs = duration,
                width = width,
                height = height,
                sizeBytes = fileSize
            )
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun getFileName(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            if (nameIndex >= 0) cursor.getString(nameIndex) else null
        }
    }

    private fun getFileSize(uri: Uri): Long? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            cursor.moveToFirst()
            if (sizeIndex >= 0) cursor.getLong(sizeIndex) else null
        }
    }
}
