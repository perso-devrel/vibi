package com.example.dubcast.domain.usecase.timeline

import com.example.dubcast.domain.model.SegmentType
import com.example.dubcast.domain.repository.SegmentRepository
import javax.inject.Inject

class UpdateSegmentSpeedUseCase @Inject constructor(
    private val segmentRepository: SegmentRepository
) {
    suspend operator fun invoke(segmentId: String, speedScale: Float) {
        val seg = segmentRepository.getSegment(segmentId) ?: return
        require(seg.type == SegmentType.VIDEO) {
            "Speed is only supported for VIDEO segments"
        }
        val clamped = speedScale.coerceIn(MIN_SPEED, MAX_SPEED)
        if (seg.speedScale == clamped) return
        segmentRepository.updateSegment(seg.copy(speedScale = clamped))
    }

    companion object {
        const val MIN_SPEED = 0.25f
        const val MAX_SPEED = 4f
    }
}
