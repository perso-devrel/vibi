package com.example.dubcast.domain.usecase.bgm

import com.example.dubcast.domain.model.BgmClip
import com.example.dubcast.domain.repository.BgmClipRepository
import java.util.UUID
import javax.inject.Inject

class AddBgmClipUseCase @Inject constructor(
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
            id = UUID.randomUUID().toString(),
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
