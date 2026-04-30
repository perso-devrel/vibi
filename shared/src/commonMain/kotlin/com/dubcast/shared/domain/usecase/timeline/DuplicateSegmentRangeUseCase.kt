package com.dubcast.shared.domain.usecase.timeline

import com.dubcast.shared.platform.generateId

import com.dubcast.shared.domain.model.Segment
import com.dubcast.shared.domain.repository.SegmentRepository

class DuplicateSegmentRangeUseCase constructor(
    private val segmentRepository: SegmentRepository,
    private val splitSegmentUseCase: SplitSegmentUseCase
) {
    suspend operator fun invoke(
        segmentId: String,
        rangeStartLocalMs: Long,
        rangeEndLocalMs: Long
    ): Segment {
        val split = splitSegmentUseCase(segmentId, rangeStartLocalMs, rangeEndLocalMs)
        val middle = split.middle

        val following = segmentRepository.getByProjectId(middle.projectId)
            .filter { it.order > middle.order }
            .sortedByDescending { it.order }
        for (s in following) {
            segmentRepository.updateSegment(s.copy(order = s.order + 1))
        }

        val duplicate = middle.copy(
            id = generateId(),
            order = middle.order + 1,
            duplicatedFromId = middle.id
        )
        segmentRepository.addSegment(duplicate)
        return duplicate
    }
}
