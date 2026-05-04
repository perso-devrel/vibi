package com.dubcast.shared.domain.usecase.timeline

import com.dubcast.shared.platform.generateId

import com.dubcast.shared.domain.model.Segment
import com.dubcast.shared.domain.model.SegmentType
import com.dubcast.shared.domain.repository.SegmentRepository

class SplitSegmentUseCase constructor(
    private val segmentRepository: SegmentRepository
) {
    data class SplitResult(
        val pre: Segment?,
        val middle: Segment,
        val post: Segment?
    )

    suspend operator fun invoke(
        segmentId: String,
        rangeStartLocalMs: Long,
        rangeEndLocalMs: Long
    ): SplitResult {
        val original = segmentRepository.getSegment(segmentId)
            ?: throw IllegalArgumentException("Segment not found: $segmentId")
        require(original.type == SegmentType.VIDEO) {
            "Split is only supported for VIDEO segments"
        }

        val trimStart = original.trimStartMs
        val trimEnd = original.effectiveTrimEndMs
        val start = rangeStartLocalMs.coerceIn(trimStart, trimEnd)
        val end = rangeEndLocalMs.coerceIn(trimStart, trimEnd)
        require(end - start >= MIN_RANGE_MS) {
            "Range must be at least ${MIN_RANGE_MS}ms"
        }

        val needsPre = start > trimStart
        val needsPost = end < trimEnd
        val newInsertCount = (if (needsPre) 1 else 0) + (if (needsPost) 1 else 0)

        if (newInsertCount > 0) {
            val following = segmentRepository.getByProjectId(original.projectId)
                .filter { it.order > original.order }
                .sortedByDescending { it.order }
            for (s in following) {
                segmentRepository.updateSegment(s.copy(order = s.order + newInsertCount))
            }
        }

        // pre 는 원본 그대로 trim 만 줄임 — duplicatedFromId 가 있었다면 그대로 보존.
        val pre = if (needsPre) original.copy(trimEndMs = start) else null

        // middle/post 는 split 으로 새로 만들어진 조각이지 duplicate 결과가 아님.
        // 부모의 duplicatedFromId 를 carry 하면 timeline UI 의 edited 판정에서
        // "사용자가 의도적으로 편집한 segment" 로 잘못 분류돼 전체가 orange 로 칠해짐.
        val middle = if (needsPre) {
            original.copy(
                id = generateId(),
                order = original.order + 1,
                trimStartMs = start,
                trimEndMs = end,
                duplicatedFromId = null,
            )
        } else {
            original.copy(trimStartMs = start, trimEndMs = end)
        }

        val postOrder = if (needsPre) original.order + 2 else original.order + 1
        val post = if (needsPost) {
            original.copy(
                id = generateId(),
                order = postOrder,
                trimStartMs = end,
                trimEndMs = trimEnd,
                duplicatedFromId = null,
            )
        } else null

        if (needsPre) {
            segmentRepository.updateSegment(pre!!)
            segmentRepository.addSegment(middle)
        } else {
            segmentRepository.updateSegment(middle)
        }
        if (needsPost) {
            segmentRepository.addSegment(post!!)
        }

        return SplitResult(pre = pre, middle = middle, post = post)
    }

    companion object {
        const val MIN_RANGE_MS = 100L
    }
}
