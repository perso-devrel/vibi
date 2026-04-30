package com.dubcast.shared.data.repository

import com.dubcast.shared.domain.model.VideoInfo
import com.dubcast.shared.domain.usecase.input.VideoMetadataExtractor
import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import platform.AVFoundation.AVAssetTrack
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.AVURLAssetPreferPreciseDurationAndTimingKey
import platform.AVFoundation.duration
import platform.AVFoundation.naturalSize
import platform.AVFoundation.timeRange
import platform.AVFoundation.tracksWithMediaType
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeRangeGetEnd
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL

class IosVideoMetadataExtractor : VideoMetadataExtractor {

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun extract(uri: String): VideoInfo? {
        println("[Extractor] enter uri=$uri")
        // file:// 시작이면 URLWithString, 그 외엔 fileURLWithPath (절대 경로 가정)
        val url = if (uri.startsWith("file://")) {
            NSURL.URLWithString(uri) ?: NSURL.fileURLWithPath(uri.removePrefix("file://"))
        } else {
            NSURL.fileURLWithPath(uri)
        }
        println("[Extractor] url path=${url.path} isFileURL=${url.fileURL}")
        val asset = AVURLAsset(
            uRL = url,
            options = mapOf(AVURLAssetPreferPreciseDurationAndTimingKey to true)
        )
        println("[Extractor] asset created")

        // 안전망: async load 도 시도하되 1초 timeout
        withTimeoutOrNull(2000) {
            suspendCancellableCoroutine<Unit> { cont ->
                asset.loadValuesAsynchronouslyForKeys(
                    keys = listOf("duration", "tracks")
                ) {
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        }

        val tracks = asset.tracksWithMediaType(AVMediaTypeVideo)
        println("[Extractor] tracks=${tracks.size}")
        val videoTrack = tracks.firstOrNull() as? AVAssetTrack ?: run {
            println("[Extractor] no video track")
            return null
        }

        // duration: asset.duration 우선, 0 이면 track timeRange 로 fallback
        var durationSec = CMTimeGetSeconds(asset.duration)
        if (durationSec.isNaN() || durationSec <= 0.0) {
            val end = CMTimeRangeGetEnd(videoTrack.timeRange)
            durationSec = CMTimeGetSeconds(end)
            println("[Extractor] fallback duration from track timeRange=$durationSec")
        }
        if (durationSec.isNaN() || durationSec <= 0.0) {
            println("[Extractor] duration still invalid")
            return null
        }
        val durationMs = (durationSec * 1000.0).toLong()

        val (width, height) = videoTrack.naturalSize.useContents { Pair(width.toInt(), height.toInt()) }
        println("[Extractor] size ${width}x${height} duration=${durationMs}ms")

        val fileName = (url.lastPathComponent ?: "video.mp4")
        val fileSize = runCatching {
            val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(url.path ?: "", null)
            (attrs?.get(platform.Foundation.NSFileSize) as? Number)?.toLong() ?: 0L
        }.getOrDefault(0L)

        return VideoInfo(
            uri = uri,
            fileName = fileName,
            mimeType = "video/mp4",
            durationMs = durationMs,
            width = width,
            height = height,
            sizeBytes = fileSize
        )
    }
}
