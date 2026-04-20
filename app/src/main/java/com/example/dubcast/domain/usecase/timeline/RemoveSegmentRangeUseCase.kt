package com.example.dubcast.domain.usecase.timeline

import com.example.dubcast.domain.repository.SegmentRepository
import javax.inject.Inject

class RemoveSegmentRangeUseCase @Inject constructor(
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
