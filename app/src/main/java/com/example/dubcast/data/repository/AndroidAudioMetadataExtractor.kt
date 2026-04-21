package com.example.dubcast.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.dubcast.domain.usecase.input.AudioInfo
import com.example.dubcast.domain.usecase.input.AudioMetadataExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidAudioMetadataExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) : AudioMetadataExtractor {

    override suspend fun extract(uri: String): AudioInfo? {
        val contentUri = Uri.parse(uri)
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, contentUri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: return null
            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            AudioInfo(uri = uri, durationMs = duration, mimeType = mimeType)
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }
}
