package com.dubcast.shared.domain.usecase.timeline

import com.dubcast.shared.domain.repository.SegmentRepository

class RemoveSegmentUseCase constructor(
    private val segmentRepository: SegmentRepository
) {
    suspend operator fun invoke(segmentId: String) {
        val target = segmentRepository.getSegment(segmentId) ?: return
        val remaining = segmentRepository
            .getByProjectId(target.projectId)
            .filter { it.id != segmentId }
            .sortedBy { it.order }

        segmentRepository.deleteSegment(segmentId)

        // Compact orders so they stay contiguous starting at 0.
        for ((index, seg) in remaining.withIndex()) {
            if (seg.order != index) {
                segmentRepository.updateSegment(seg.copy(order = index))
            }
        }
    }
}
