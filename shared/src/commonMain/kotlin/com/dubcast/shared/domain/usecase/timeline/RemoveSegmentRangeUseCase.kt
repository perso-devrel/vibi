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
        // 항상 middle 삭제 — 사용자 의도된 액션. 마지막 segment 까지 삭제하면 빈 프로젝트가 되지만
        // 그 결정도 사용자에게 맡김 (이전엔 total<=1 에서 silent return 으로 사용자 혼란).
        removeSegmentUseCase(middle.id)
    }
}
