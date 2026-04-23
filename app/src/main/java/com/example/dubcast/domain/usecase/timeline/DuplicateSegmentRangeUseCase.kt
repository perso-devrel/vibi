package com.example.dubcast.domain.usecase.timeline

import com.example.dubcast.domain.model.Segment
import com.example.dubcast.domain.repository.SegmentRepository
import java.util.UUID
import javax.inject.Inject

class DuplicateSegmentRangeUseCase @Inject constructor(
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
            id = UUID.randomUUID().toString(),
            order = middle.order + 1,
            duplicatedFromId = middle.id
        )
        segmentRepository.addSegment(duplicate)
        return duplicate
    }
}
