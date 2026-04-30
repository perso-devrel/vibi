package com.dubcast.shared.domain.usecase.input

import com.dubcast.shared.domain.model.ImageInfo

interface ImageMetadataExtractor {
    suspend fun extract(uri: String): ImageInfo?
}
