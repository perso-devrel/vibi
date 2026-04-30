package com.dubcast.shared.domain.usecase.input

import com.dubcast.shared.domain.model.VideoInfo

interface VideoMetadataExtractor {
    suspend fun extract(uri: String): VideoInfo?
}
