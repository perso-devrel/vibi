package com.example.dubcast.domain.usecase.input

import com.example.dubcast.domain.model.ImageInfo

interface ImageMetadataExtractor {
    suspend fun extract(uri: String): ImageInfo?
}
