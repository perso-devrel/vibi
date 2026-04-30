package com.dubcast.shared.domain.usecase.bgm

import com.dubcast.shared.platform.generateId

import com.dubcast.shared.domain.model.BgmClip
import com.dubcast.shared.domain.repository.BgmClipRepository

class AddBgmClipUseCase constructor(
    private val bgmClipRepository: BgmClipRepository
) {
    suspend operator fun invoke(
        projectId: String,
        sourceUri: String,
        sourceDurationMs: Long,
        startMs: Long = 0L,
        volumeScale: Float = 1.0f
    ): BgmClip {
        require(sourceUri.isNotBlank()) { "sourceUri must not be blank" }
        require(sourceDurationMs > 0L) { "sourceDurationMs must be positive: $sourceDurationMs" }
        require(startMs >= 0L) { "startMs must be >= 0: $startMs" }
        val clamped = volumeScale.coerceIn(BgmClip.MIN_VOLUME, BgmClip.MAX_VOLUME)
        val clip = BgmClip(
            id = generateId(),
            projectId = projectId,
            sourceUri = sourceUri,
            sourceDurationMs = sourceDurationMs,
            startMs = startMs,
            volumeScale = clamped
        )
        bgmClipRepository.addClip(clip)
        return clip
    }
}
