package com.example.dubcast.domain.usecase.bgm

import com.example.dubcast.domain.model.BgmClip
import com.example.dubcast.domain.repository.BgmClipRepository
import javax.inject.Inject

class UpdateBgmClipUseCase @Inject constructor(
    private val bgmClipRepository: BgmClipRepository
) {
    suspend operator fun invoke(
        clipId: String,
        startMs: Long? = null,
        volumeScale: Float? = null
    ): BgmClip {
        val current = bgmClipRepository.getClip(clipId)
            ?: throw IllegalArgumentException("BGM clip not found: $clipId")
        startMs?.let { require(it >= 0L) { "startMs must be >= 0: $it" } }
        val updated = current.copy(
            startMs = startMs ?: current.startMs,
            volumeScale = volumeScale?.coerceIn(BgmClip.MIN_VOLUME, BgmClip.MAX_VOLUME)
                ?: current.volumeScale
        )
        bgmClipRepository.updateClip(updated)
        return updated
    }
}
