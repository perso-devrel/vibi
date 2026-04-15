package com.example.dubcast.domain.usecase.input

import com.example.dubcast.domain.model.VideoInfo

interface VideoMetadataExtractor {
    suspend fun extract(uri: String): VideoInfo?
}
