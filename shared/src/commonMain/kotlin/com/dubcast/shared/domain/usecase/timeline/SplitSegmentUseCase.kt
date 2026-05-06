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

        // Snap-to-edge: pre/post 조각이 MIN_FRAGMENT_MS 미만이면 그 조각을 만들지 않고
        // middle 이 segment 의 원래 끝/시작까지 흡수. precision drift (round 잔여 1ms 미만 등)
        // 로 ghost segment 가 BFF 로 흘러가 audio render 가 silent fallback 으로 떨어지는
        // 문제 방지. middle 자체의 길이는 위 require(>= MIN_RANGE_MS=100) 가 이미
        // MIN_FRAGMENT_MS=50 보다 엄격하게 보장.
        val preCandidateDur = start - trimStart
        val postCandidateDur = trimEnd - end
        val absorbPre = preCandidateDur in 1 until MIN_FRAGMENT_MS
        val absorbPost = postCandidateDur in 1 until MIN_FRAGMENT_MS

        val effectiveStart = if (absorbPre) trimStart else start
        val effectiveEnd = if (absorbPost) trimEnd else end

        val needsPre = effectiveStart > trimStart
        val needsPost = effectiveEnd < trimEnd
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
        val pre = if (needsPre) original.copy(trimEndMs = effectiveStart) else null

        // middle/post 는 split 으로 새로 만들어진 조각이지 duplicate 결과가 아님.
        // 부모의 duplicatedFromId 를 carry 하면 timeline UI 의 edited 판정에서
        // "사용자가 의도적으로 편집한 segment" 로 잘못 분류돼 전체가 orange 로 칠해짐.
        val middle = if (needsPre) {
            original.copy(
                id = generateId(),
                order = original.order + 1,
                trimStartMs = effectiveStart,
                trimEndMs = effectiveEnd,
                duplicatedFromId = null,
            )
        } else {
            original.copy(trimStartMs = effectiveStart, trimEndMs = effectiveEnd)
        }

        val postOrder = if (needsPre) original.order + 2 else original.order + 1
        val post = if (needsPost) {
            original.copy(
                id = generateId(),
                order = postOrder,
                trimStartMs = effectiveEnd,
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

        /**
         * pre/post 조각 최소 길이 (ms). 이보다 짧은 조각은 만들지 않고 middle 이 segment 의
         * 원래 boundary 까지 흡수 — BFF audio render 의 20ms guard 보다 보수적.
         * round 잔여 1ms 미만 ghost segment 가 audio render 를 silent fallback 으로 떨어뜨려
         * timeline 미리보기와 export 결과가 어긋나는 문제 방지.
         */
        const val MIN_FRAGMENT_MS = 50L
    }
}
