package com.dubcast.shared.data.repository

import com.dubcast.shared.domain.usecase.input.AudioInfo
import com.dubcast.shared.domain.usecase.input.AudioMetadataExtractor
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.AVURLAssetPreferPreciseDurationAndTimingKey
import platform.AVFoundation.duration
import platform.AVFoundation.loadValuesAsynchronouslyForKeys
import platform.CoreMedia.CMTimeGetSeconds
import platform.Foundation.NSURL

class IosAudioMetadataExtractor : AudioMetadataExtractor {

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun extract(uri: String): AudioInfo? {
        // shared/CLAUDE.md:78 known iOS bug — URLWithString(absolutePath) 은 nil 대신 invalid
        // URL 을 만들어 fileURLWithPath fallback 이 발동 안 함. file:// 접두사 유무로 분기.
        val url = if (uri.startsWith("file://")) {
            NSURL.URLWithString(uri) ?: NSURL.fileURLWithPath(uri.removePrefix("file://"))
        } else {
            NSURL.fileURLWithPath(uri)
        }
        val asset = AVURLAsset(
            uRL = url,
            options = mapOf<Any?, Any>(AVURLAssetPreferPreciseDurationAndTimingKey to true),
        )
        // shared/CLAUDE.md:97 — duration 은 lazy. async load 후 읽기.
        suspendCancellableCoroutine<Unit> { cont ->
            asset.loadValuesAsynchronouslyForKeys(listOf("duration")) {
                if (cont.isActive) cont.resume(Unit) {}
            }
        }
        val durationSec = CMTimeGetSeconds(asset.duration)
        if (durationSec.isNaN() || durationSec <= 0.0) return null

        return AudioInfo(
            uri = uri,
            durationMs = (durationSec * 1000.0).toLong(),
            mimeType = "audio/mpeg",
        )
    }
}
