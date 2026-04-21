package com.example.dubcast.domain.usecase.input

interface AudioMetadataExtractor {
    suspend fun extract(uri: String): AudioInfo?
}

data class AudioInfo(
    val uri: String,
    val durationMs: Long,
    val mimeType: String? = null
)
