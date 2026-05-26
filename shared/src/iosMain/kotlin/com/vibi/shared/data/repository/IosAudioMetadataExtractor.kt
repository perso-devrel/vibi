package com.vibi.shared.data.repository

import com.vibi.shared.domain.usecase.input.AudioInfo
import com.vibi.shared.domain.usecase.input.AudioMetadataExtractor
import com.vibi.shared.platform.resolveStoredUriToFileUrl
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.AVURLAssetPreferPreciseDurationAndTimingKey
import platform.AVFoundation.duration
import platform.AVFoundation.loadValuesAsynchronouslyForKeys
import platform.CoreMedia.CMTimeGetSeconds

class IosAudioMetadataExtractor : AudioMetadataExtractor {

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun extract(uri: String): AudioInfo? {
        // resolver: 상대 / 절대 / file:// / 옛 UUID remap. 모든 분기 fileURLWithPath.
        val url = resolveStoredUriToFileUrl(uri) ?: return null
        val asset = AVURLAsset(
            uRL = url,
            options = mapOf<Any?, Any>(AVURLAssetPreferPreciseDurationAndTimingKey to true),
        )
        // shared/CLAUDE.md:97 — duration 은 lazy. async load 후 읽기.
        suspendCancellableCoroutine<Unit> { cont ->
            asset.loadValuesAsynchronouslyForKeys(listOf("duration")) {
                if (cont.isActive) cont.resume(Unit)
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
