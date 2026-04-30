package com.dubcast.shared.domain.usecase.timeline

import com.dubcast.shared.domain.repository.SegmentRepository

class UpdateSegmentVolumeUseCase constructor(
    private val segmentRepository: SegmentRepository
) {
    suspend operator fun invoke(segmentId: String, volumeScale: Float) {
        val seg = segmentRepository.getSegment(segmentId) ?: return
        val clamped = volumeScale.coerceIn(MIN_VOLUME, MAX_VOLUME)
        if (seg.volumeScale == clamped) return
        segmentRepository.updateSegment(seg.copy(volumeScale = clamped))
    }

    companion object {
        const val MIN_VOLUME = 0f
        const val MAX_VOLUME = 2f
    }
}
