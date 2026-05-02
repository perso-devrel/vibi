package com.dubcast.shared.domain.usecase.bgm

import com.dubcast.shared.domain.model.BgmClip
import com.dubcast.shared.domain.repository.BgmClipRepository

class UpdateBgmClipUseCase constructor(
    private val bgmClipRepository: BgmClipRepository
) {
    suspend operator fun invoke(
        clipId: String,
        startMs: Long? = null,
        volumeScale: Float? = null,
        speedScale: Float? = null,
    ): BgmClip {
        val current = bgmClipRepository.getClip(clipId)
            ?: throw IllegalArgumentException("BGM clip not found: $clipId")
        startMs?.let { require(it >= 0L) { "startMs must be >= 0: $it" } }
        val updated = current.copy(
            startMs = startMs ?: current.startMs,
            volumeScale = volumeScale?.coerceIn(BgmClip.MIN_VOLUME, BgmClip.MAX_VOLUME)
                ?: current.volumeScale,
            speedScale = speedScale?.coerceIn(BgmClip.MIN_SPEED, BgmClip.MAX_SPEED)
                ?: current.speedScale,
        )
        bgmClipRepository.updateClip(updated)
        return updated
    }
}
