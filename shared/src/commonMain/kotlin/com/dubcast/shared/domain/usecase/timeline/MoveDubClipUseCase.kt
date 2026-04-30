package com.dubcast.shared.domain.usecase.timeline

import com.dubcast.shared.domain.model.DubClip
import com.dubcast.shared.domain.repository.DubClipRepository

class MoveDubClipUseCase constructor(
    private val dubClipRepository: DubClipRepository
) {
    suspend operator fun invoke(
        clip: DubClip,
        newStartMs: Long,
        videoDurationMs: Long
    ): DubClip {
        val clampedStart = newStartMs
            .coerceAtLeast(0L)
            .coerceAtMost((videoDurationMs - clip.durationMs).coerceAtLeast(0L))
        val updated = clip.copy(startMs = clampedStart)
        dubClipRepository.updateClip(updated)
        return updated
    }
}
