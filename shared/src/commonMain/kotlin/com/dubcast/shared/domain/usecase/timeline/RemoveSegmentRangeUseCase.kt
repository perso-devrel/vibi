package com.dubcast.shared.domain.usecase.timeline

import com.dubcast.shared.domain.repository.SegmentRepository

class RemoveSegmentRangeUseCase constructor(
    private val segmentRepository: SegmentRepository,
    private val splitSegmentUseCase: SplitSegmentUseCase,
    private val removeSegmentUseCase: RemoveSegmentUseCase
) {
    suspend operator fun invoke(
        segmentId: String,
        rangeStartLocalMs: Long,
        rangeEndLocalMs: Long
    ) {
        val split = splitSegmentUseCase(segmentId, rangeStartLocalMs, rangeEndLocalMs)
        val middle = split.middle
        val projectId = middle.projectId
        val total = segmentRepository.getByProjectId(projectId).size
        if (total <= 1) return
        removeSegmentUseCase(middle.id)
    }
}
