package com.dubcast.shared.data.repository

import com.dubcast.shared.domain.usecase.input.AudioInfo
import com.dubcast.shared.domain.usecase.input.AudioMetadataExtractor
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVAsset
import platform.AVFoundation.duration
import platform.CoreMedia.CMTimeGetSeconds
import platform.Foundation.NSURL

class IosAudioMetadataExtractor : AudioMetadataExtractor {

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun extract(uri: String): AudioInfo? {
        val url = NSURL.URLWithString(uri) ?: NSURL.fileURLWithPath(uri)
        val asset = AVAsset.assetWithURL(url) ?: return null
        val durationSec = CMTimeGetSeconds(asset.duration)
        if (durationSec.isNaN() || durationSec <= 0.0) return null

        return AudioInfo(
            uri = uri,
            durationMs = (durationSec * 1000.0).toLong(),
            mimeType = "audio/mpeg"
        )
    }
}
