package com.vibi.shared.domain.usecase.timeline

import com.vibi.shared.platform.generateId

import com.vibi.shared.domain.model.Segment
import com.vibi.shared.domain.model.cloneForSegment
import com.vibi.shared.domain.repository.SegmentRepository
import com.vibi.shared.domain.repository.SeparationDirectiveRepository

class DuplicateSegmentRangeUseCase constructor(
    private val segmentRepository: SegmentRepository,
    private val splitSegmentUseCase: SplitSegmentUseCase,
    private val separationDirectiveRepository: SeparationDirectiveRepository,
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

        // 분리(directive) 따라가기 — middle 에 앵커된 directive 를 복제본(duplicate)에도 동일 local 좌표로
        // 복제. middle 과 duplicate 는 trim/sourceUri 가 같아 local 좌표 그대로 유효, segmentId 만 교체.
        // jobId 는 null(수동 piece) — 원본과 dedup 충돌 방지. 글로벌 range 캐시는 호출자(VM)의 reanchor 가
        // duplicate 의 새 위치로 다시 계산한다.
        val middleDirectives = separationDirectiveRepository.getByProject(middle.projectId)
            .filter { it.segmentId == middle.id }
        if (middleDirectives.isNotEmpty()) {
            separationDirectiveRepository.addAll(
                middleDirectives.map { it.cloneForSegment(duplicate.id) }
            )
        }
        return duplicate
    }
}
