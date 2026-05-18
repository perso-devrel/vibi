package com.vibi.shared.domain.usecase.bgm

import com.vibi.shared.platform.generateId

import com.vibi.shared.domain.model.BgmClip
import com.vibi.shared.domain.repository.BgmClipRepository

class AddBgmClipUseCase constructor(
    private val bgmClipRepository: BgmClipRepository
) {
    suspend operator fun invoke(
        projectId: String,
        sourceUri: String,
        sourceDurationMs: Long,
        startMs: Long = 0L,
        volumeScale: Float = 1.0f,
        sourceTrimStartMs: Long = 0L,
        sourceTrimEndMs: Long = 0L,
    ): BgmClip {
        require(sourceUri.isNotBlank()) { "sourceUri must not be blank" }
        require(sourceDurationMs > 0L) { "sourceDurationMs must be positive: $sourceDurationMs" }
        require(startMs >= 0L) { "startMs must be >= 0: $startMs" }
        require(sourceTrimStartMs >= 0L) { "sourceTrimStartMs must be >= 0: $sourceTrimStartMs" }
        // 0 = "끝까지" (backward-compat). 그 외엔 start 보다 커야 함.
        require(sourceTrimEndMs == 0L || sourceTrimEndMs > sourceTrimStartMs) {
            "sourceTrimEndMs ($sourceTrimEndMs) must be 0 or > sourceTrimStartMs ($sourceTrimStartMs)"
        }
        val clamped = volumeScale.coerceIn(BgmClip.MIN_VOLUME, BgmClip.MAX_VOLUME)
        val clip = BgmClip(
            id = generateId(),
            projectId = projectId,
            sourceUri = sourceUri,
            sourceDurationMs = sourceDurationMs,
            startMs = startMs,
            volumeScale = clamped,
            sourceTrimStartMs = sourceTrimStartMs,
            sourceTrimEndMs = sourceTrimEndMs,
        )
        bgmClipRepository.addClip(clip)
        return clip
    }
}
