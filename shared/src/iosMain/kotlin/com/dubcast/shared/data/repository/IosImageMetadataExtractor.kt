package com.dubcast.shared.data.repository

import com.dubcast.shared.domain.model.ImageInfo
import com.dubcast.shared.domain.usecase.input.ImageMetadataExtractor
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Foundation.NSURL
import platform.UIKit.UIImage

class IosImageMetadataExtractor : ImageMetadataExtractor {

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun extract(uri: String): ImageInfo? {
        val url = NSURL.URLWithString(uri) ?: NSURL.fileURLWithPath(uri)
        val path = url.path ?: return null
        val image = UIImage.imageWithContentsOfFile(path) ?: return null
        val (width, height) = image.size.useContents { Pair(width.toInt(), height.toInt()) }
        if (width <= 0 || height <= 0) return null
        return ImageInfo(uri = uri, width = width, height = height)
    }
}
