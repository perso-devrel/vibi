package com.example.dubcast.fake

import com.example.dubcast.domain.model.ImageInfo
import com.example.dubcast.domain.usecase.input.ImageMetadataExtractor

class FakeImageMetadataExtractor : ImageMetadataExtractor {
    var result: ImageInfo? = null

    override suspend fun extract(uri: String): ImageInfo? {
        return result ?: ImageInfo(uri = uri, width = 1920, height = 1080)
    }
}
