package com.dubcast.shared.domain.usecase.timeline

import com.dubcast.shared.domain.model.Segment
import com.dubcast.shared.domain.model.SegmentType
import com.dubcast.shared.domain.repository.SegmentRepository

class UpdateSegmentTrimUseCase constructor(
    private val segmentRepository: SegmentRepository
) {
    suspend operator fun invoke(segmentId: String, trimStartMs: Long, trimEndMs: Long) {
        val segment = segmentRepository.getSegment(segmentId)
            ?: throw IllegalArgumentException("Segment not found: $segmentId")
        require(segment.type == SegmentType.VIDEO) {
            "Trim is only supported on VIDEO segments"
        }
        val newStart = trimStartMs.coerceAtLeast(0L)
        val effectiveEnd = if (trimEndMs <= 0L) segment.durationMs else trimEndMs
        require(effectiveEnd > newStart) { "trimEndMs must be greater than trimStartMs" }
        require(effectiveEnd <= segment.durationMs) { "trimEndMs cannot exceed segment duration" }
        segmentRepository.updateSegment(
            segment.copy(trimStartMs = newStart, trimEndMs = trimEndMs)
        )
    }
}
