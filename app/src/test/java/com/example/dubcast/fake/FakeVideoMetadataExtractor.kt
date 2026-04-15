package com.example.dubcast.fake

import com.example.dubcast.domain.model.VideoInfo
import com.example.dubcast.domain.usecase.input.VideoMetadataExtractor

class FakeVideoMetadataExtractor : VideoMetadataExtractor {
    var result: VideoInfo? = null

    override suspend fun extract(uri: String): VideoInfo? = result
}
