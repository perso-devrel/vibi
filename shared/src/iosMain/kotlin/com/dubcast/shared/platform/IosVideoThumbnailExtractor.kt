@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.dubcast.shared.platform

import platform.AVFoundation.AVAsset
import platform.AVFoundation.AVAssetImageGenerator
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.AVURLAssetPreferPreciseDurationAndTimingKey
import platform.CoreGraphics.CGSizeMake
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.writeToFile
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation

/**
 * AVAssetImageGenerator 로 0초 시점 CGImage 추출 → UIImage → JPEG (NSData) → cacheDir/thumbs/<hash>.jpg.
 * 동일 uri 의 두 번째 호출은 file existence check 만으로 path 반환.
 *
 * 주의: K/N 의 NSURL 절대 경로 처리는 known-bug — `URLWithString(absolutePath)` 가 nil 안 던짐.
 * IosVideoMetadataExtractor 와 동일 패턴으로 분기.
 */
class IosVideoThumbnailExtractor : VideoThumbnailExtractor {

    override suspend fun extractThumbnail(uri: String, atMs: Long): String? {
        val cacheDir = "${cacheDirectory()}/thumbs"
        ensureDir(cacheDir)
        val cachePath = "$cacheDir/${uri.hashCode().toUInt()}_${atMs}.jpg"
        if (NSFileManager.defaultManager.fileExistsAtPath(cachePath)) return cachePath

        val url = if (uri.startsWith("file://")) {
            NSURL.URLWithString(uri) ?: NSURL.fileURLWithPath(uri.removePrefix("file://"))
        } else {
            NSURL.fileURLWithPath(uri)
        }
        val asset: AVAsset = AVURLAsset(
            uRL = url,
            options = mapOf(AVURLAssetPreferPreciseDurationAndTimingKey to true)
        )
        val generator = AVAssetImageGenerator(asset).apply {
            appliesPreferredTrackTransform = true
            // 너무 큰 프레임은 메모리 부담 — 적당히 다운샘플 (썸네일 용도).
            maximumSize = CGSizeMake(720.0, 720.0)
        }

        // 1초 = 600 timescale (CMTime convention).
        val time = CMTimeMake(value = atMs * 600L / 1000L, timescale = 600)
        val cgImage = runCatching {
            generator.copyCGImageAtTime(requestedTime = time, actualTime = null, error = null)
        }.getOrNull() ?: return null

        val uiImage = UIImage.imageWithCGImage(cgImage)
        val data = UIImageJPEGRepresentation(uiImage, 0.8) ?: return null
        val ok = data.writeToFile(cachePath, atomically = true)
        return if (ok) cachePath else null
    }

    private fun cacheDirectory(): String {
        val paths = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
        return requireNotNull(paths.firstOrNull() as? String) { "Could not resolve iOS cache dir." }
    }

    private fun ensureDir(path: String) {
        val fm = NSFileManager.defaultManager
        if (!fm.fileExistsAtPath(path)) {
            fm.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
        }
    }
}
